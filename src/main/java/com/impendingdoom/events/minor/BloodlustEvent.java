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
 * Mortis's Bloodlust: every mob gets the Bloodlust Haze (red haze, shaking) - 2 minutes for hostile mobs, 5 for the
 * rest. If the haze runs out naturally the mob becomes bloodlusted: it stops shaking, blood drips from it, its eyes
 * glow red, it attacks any mob it can find, and it grows bigger and stronger with every kill.
 */
@EventBusSubscriber(modid = ImpendingDoom.MODID)
public class BloodlustEvent extends DoomEvent {
    public static final String ID = "bloodlust";
    public static final String TAG = "impendingdoom.bloodlusted";
    private static final int HOSTILE_DURATION = 2400;
    private static final int OTHER_DURATION = 6000;
    private static final DustParticleOptions EYE = new DustParticleOptions(0xFF0000, 0.6F);
    private static final BlockParticleOption BLOOD = new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.REDSTONE_BLOCK.defaultBlockState());

    public BloodlustEvent() {
        super(ID, false, ChatFormatting.DARK_GREEN);
    }

    private static void haze(Mob mob) {
        if (mob instanceof ArmorStand || mob.entityTags().contains(TAG) || mob.hasEffect(ModEffects.BLOODLUST_HAZE)) return;
        mob.addEffect(new MobEffectInstance(ModEffects.BLOODLUST_HAZE, mob instanceof Enemy ? HOSTILE_DURATION : OTHER_DURATION, 0));
        EntityMarks.update(mob);
    }

    @Override
    public void onStart(ServerLevel level, CompoundTag data) {
        for (Mob mob : level.getEntities(EntityTypeTest.forClass(Mob.class), Entity::isAlive)) haze(mob);
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel) || !(event.getEntity() instanceof Mob mob)) return;
        if (mob.entityTags().contains(TAG)) {
            addBloodlustGoals(mob);
        } else if (!event.loadedFromDisk() && EventScheduler.isActive(ID)) {
            EventUtil.defer(() -> haze(mob));
        }
    }

    @SubscribeEvent
    public static void onExpire(MobEffectEvent.Expired event) {
        MobEffectInstance instance = event.getEffectInstance();
        if (instance == null || !instance.is(ModEffects.BLOODLUST_HAZE)) return;
        if (!(event.getEntity() instanceof Mob mob) || mob.level().isClientSide()) return;
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
        mob.targetSelector.addGoal(0, new NearestAttackableTargetGoal<>(mob, LivingEntity.class, 10, true, false,
                (target, level) -> target != mob && !(target instanceof ArmorStand) && !(target instanceof ServerPlayer p && (p.isCreative() || p.isSpectator()))));
        if (!(mob instanceof PathfinderMob pathfinder)) return;
        if (EventUtil.hasAttribute(mob, Attributes.ATTACK_DAMAGE) && !(mob instanceof Enemy)) {
            mob.goalSelector.addGoal(1, new MeleeAttackGoal(pathfinder, 1.2, true));
        } else if (!EventUtil.hasAttribute(mob, Attributes.ATTACK_DAMAGE)) {
            mob.goalSelector.addGoal(1, new ForcedMeleeGoal(mob, 1.2, 3.0F));
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
