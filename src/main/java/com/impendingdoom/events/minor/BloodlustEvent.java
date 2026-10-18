package com.impendingdoom.events.minor;

import com.impendingdoom.ImpendingDoom;
import com.impendingdoom.core.DoomEvent;
import com.impendingdoom.core.EventScheduler;
import com.impendingdoom.entity.ai.ForcedMeleeGoal;
import com.impendingdoom.network.EntityMarkPayload;
import com.impendingdoom.registry.ModEffects;
import com.impendingdoom.util.EntityMarks;
import com.impendingdoom.util.EventUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * Mortis's Bloodlust: a few times a day, a random 30% of mobs get the Bloodlust Haze (red haze, shaking) - 2 minutes
 * for hostile mobs, 5 for the rest. When the event ends, passive and neutral mobs recover. If the haze runs out naturally the mob becomes bloodlusted: it stops shaking, blood drips from it, its eyes
 * glow red, it attacks any mob it can find, and it grows bigger and stronger with every kill.
 */
@EventBusSubscriber(modid = ImpendingDoom.MODID)
public class BloodlustEvent extends DoomEvent {
    public static final String ID = "bloodlust";
    public static final String TAG = "impendingdoom.bloodlusted";
    private static final int HOSTILE_DURATION = 2400;
    private static final int OTHER_DURATION = 6000;
    /** Share of mobs each strike of the haze affects. */
    private static final float CHANCE = 0.3F;
    private static final DustParticleOptions EYE = new DustParticleOptions(0xFF0000, 0.6F);
    private static final BlockParticleOption BLOOD = new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.REDSTONE_BLOCK.defaultBlockState());

    public BloodlustEvent() {
        super(ID, false, ChatFormatting.DARK_GREEN);
    }

    private static void haze(Mob mob) {
        if (mob.entityTags().contains(TAG) || mob.hasEffect(ModEffects.BLOODLUST_HAZE)) return;
        mob.addEffect(new MobEffectInstance(ModEffects.BLOODLUST_HAZE, mob instanceof Enemy ? HOSTILE_DURATION : OTHER_DURATION, 0));
        EntityMarks.update(mob);
    }

    @Override
    public void onStart(ServerLevel level, CompoundTag data) {
        strike(level);
        scheduleNext(level, data);
    }

    /** A few times a day the haze falls on a random 30% of the mobs around. */
    @Override
    public void onTick(ServerLevel level, CompoundTag data) {
        if (level.getGameTime() < data.getLongOr("next", 0L)) return;
        strike(level);
        scheduleNext(level, data);
    }

    private static void scheduleNext(ServerLevel level, CompoundTag data) {
        // Three or four times per Minecraft day, at random.
        data.putLong("next", level.getGameTime() + 5000 + level.getRandom().nextInt(4000));
    }

    private static void strike(ServerLevel level) {
        for (Mob mob : level.getEntities(EntityTypeTest.forClass(Mob.class), m -> m.isAlive() && !(m instanceof ArmorStand))) {
            if (level.getRandom().nextFloat() < CHANCE) haze(mob);
        }
    }

    /** When the event ends, passive and neutral mobs come back to their senses. Hostile mobs stay bloodlusted. */
    @Override
    public void onEnd(ServerLevel level, CompoundTag data) {
        for (Mob mob : level.getEntities(EntityTypeTest.forClass(Mob.class), BloodlustEvent::recovers)) cure(mob);
    }

    private static boolean recovers(Mob mob) {
        return !(mob instanceof Enemy) || mob instanceof NeutralMob;
    }

    private static void cure(Mob mob) {
        if (!mob.entityTags().contains(TAG) && !mob.hasEffect(ModEffects.BLOODLUST_HAZE)) return;
        mob.removeEffect(ModEffects.BLOODLUST_HAZE);
        mob.removeTag(TAG);
        mob.goalSelector.removeAllGoals(g -> g instanceof BloodlustGoal);
        mob.targetSelector.removeAllGoals(g -> g instanceof BloodlustGoal);
        mob.setTarget(null);
        mob.getPersistentData().remove("impendingdoom_kills");
        for (var attr : java.util.List.of(Attributes.SCALE, Attributes.MAX_HEALTH, Attributes.ATTACK_DAMAGE)) {
            var inst = mob.getAttribute(attr);
            if (inst == null) continue;
            inst.removeModifier(ImpendingDoom.id("bloodlust_scale"));
            inst.removeModifier(ImpendingDoom.id("bloodlust_health"));
            inst.removeModifier(ImpendingDoom.id("bloodlust_damage"));
        }
        if (mob.getHealth() > mob.getMaxHealth()) mob.setHealth(mob.getMaxHealth());
        EntityMarks.update(mob);
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel) || !(event.getEntity() instanceof Mob mob)) return;
        if (!mob.entityTags().contains(TAG) && !mob.hasEffect(ModEffects.BLOODLUST_HAZE)) return;
        // Passive and neutral mobs that were unloaded when the event ended recover when they load again.
        if (!EventScheduler.isActive(ID) && recovers(mob)) {
            EventUtil.defer(() -> cure(mob));
        } else if (mob.entityTags().contains(TAG)) {
            addBloodlustGoals(mob);
        }
    }

    @SubscribeEvent
    public static void onExpire(MobEffectEvent.Expired event) {
        MobEffectInstance instance = event.getEffectInstance();
        if (instance == null || !instance.is(ModEffects.BLOODLUST_HAZE)) return;
        if (!(event.getEntity() instanceof Mob mob) || mob.level().isClientSide()) return;
        if (!EventScheduler.isActive(ID) && recovers(mob)) return;
        mob.addTag(TAG);
        mob.setPersistenceRequired();
        addBloodlustGoals(mob);
        EntityMarks.update(mob, EntityMarkPayload.SHAKING);
    }

    @SubscribeEvent
    public static void onRemoved(MobEffectEvent.Remove event) {
        MobEffectInstance instance = event.getEffectInstance();
        if (instance != null && instance.is(ModEffects.BLOODLUST_HAZE)) {
            EntityMarks.update(event.getEntity(), EntityMarkPayload.SHAKING);
        }
    }

    private static void addBloodlustGoals(Mob mob) {
        mob.targetSelector.addGoal(0, new BloodlustTarget(mob));
        if (!(mob instanceof PathfinderMob pathfinder)) return;
        if (EventUtil.hasAttribute(mob, Attributes.ATTACK_DAMAGE) && !(mob instanceof Enemy)) {
            mob.goalSelector.addGoal(1, new BloodlustMelee(pathfinder));
        } else if (!EventUtil.hasAttribute(mob, Attributes.ATTACK_DAMAGE)) {
            mob.goalSelector.addGoal(1, new BloodlustForcedMelee(mob));
        }
    }

    /** Marks the goals bloodlust adds, so they can be taken away again. */
    private interface BloodlustGoal {}

    private static final class BloodlustTarget extends NearestAttackableTargetGoal<LivingEntity> implements BloodlustGoal {
        BloodlustTarget(Mob mob) {
            super(mob, LivingEntity.class, 10, true, false, (target, level) -> target != mob && !(target instanceof ArmorStand)
                    && !(target instanceof ServerPlayer p && (p.isCreative() || p.isSpectator())));
        }
    }

    private static final class BloodlustMelee extends MeleeAttackGoal implements BloodlustGoal {
        BloodlustMelee(PathfinderMob mob) {
            super(mob, 1.2, true);
        }
    }

    private static final class BloodlustForcedMelee extends ForcedMeleeGoal implements BloodlustGoal {
        BloodlustForcedMelee(Mob mob) {
            super(mob, 1.2, 3.0F);
        }
    }

    /** Bloodlusted: blood drips from the body and red eyes glow above the head. */
    @SubscribeEvent
    public static void onTick(EntityTickEvent.Post event) {
        Entity e = event.getEntity();
        if (e.tickCount % 4 != 0 || !(e.level() instanceof ServerLevel level) || !e.entityTags().contains(TAG)) return;
        level.sendParticles(BLOOD, e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), 2,
                e.getBbWidth() * 0.35, e.getBbHeight() * 0.3, e.getBbWidth() * 0.35, 0.0);
        Vec3 look = e.getViewVector(1.0F).multiply(1, 0, 1).normalize();
        Vec3 side = new Vec3(-look.z, 0, look.x).scale(0.15 * Math.max(1, e.getBbWidth()));
        double y = e.getY() + e.getBbHeight() + 0.25;
        level.sendParticles(EYE, e.getX() + side.x, y, e.getZ() + side.z, 1, 0, 0, 0, 0);
        level.sendParticles(EYE, e.getX() - side.x, y, e.getZ() - side.z, 1, 0, 0, 0, 0);
    }

    @SubscribeEvent
    public static void onKill(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof Mob killer) || !killer.entityTags().contains(TAG)) return;
        int kills = killer.getPersistentData().getIntOr("impendingdoom_kills", 0) + 1;
        killer.getPersistentData().putInt("impendingdoom_kills", kills);
        EventUtil.addModifier(killer, Attributes.SCALE, "bloodlust_scale", Math.min(2.0, kills * 0.1), AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        EventUtil.addModifier(killer, Attributes.MAX_HEALTH, "bloodlust_health", kills * 4.0, AttributeModifier.Operation.ADD_VALUE);
        EventUtil.addModifier(killer, Attributes.ATTACK_DAMAGE, "bloodlust_damage", kills * 1.0, AttributeModifier.Operation.ADD_VALUE);
        killer.heal(8.0F);
    }
}
