package com.impendingdoom.events.minor;

import com.impendingdoom.ImpendingDoom;
import com.impendingdoom.core.DoomEvent;
import com.impendingdoom.core.EventScheduler;
import com.impendingdoom.entity.ai.ForcedMeleeGoal;
import com.impendingdoom.network.EntityMarkPayload;
import com.impendingdoom.registry.ModEffects;
import com.impendingdoom.util.EventUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
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
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Mortis's Bloodlust: every mob able to fight gets a 5 minute haze (red particles, shaking). If the haze runs out
 * naturally the mob becomes bloodlusted: glowing red eyes, attacks any mob it can find, and grows bigger and
 * stronger with every kill.
 */
@EventBusSubscriber(modid = ImpendingDoom.MODID)
public class BloodlustEvent extends DoomEvent {
    public static final String ID = "bloodlust";
    public static final String TAG = "impendingdoom.bloodlusted";
    private static final int DURATION = 6000;
    private static final DustParticleOptions EYE = new DustParticleOptions(0xFF0000, 0.6F);

    public BloodlustEvent() {
        super(ID, false, ChatFormatting.DARK_GREEN);
    }

    private static boolean canFight(Mob mob) {
        return mob instanceof Enemy || EventUtil.hasAttribute(mob, Attributes.ATTACK_DAMAGE);
    }

    private static void haze(Mob mob) {
        if (mob.getTags().contains(TAG) || mob.hasEffect(ModEffects.BLOODLUST_HAZE)) return;
        mob.addEffect(new MobEffectInstance(ModEffects.BLOODLUST_HAZE, DURATION, 0));
        PacketDistributor.sendToPlayersTrackingEntity(mob, new EntityMarkPayload(mob.getId(), EntityMarkPayload.SHAKING));
    }

    @Override
    public void onStart(ServerLevel level, CompoundTag data) {
        for (Mob mob : level.getEntities(EntityTypeTest.forClass(Mob.class), m -> m.isAlive() && canFight(m))) haze(mob);
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel) || !(event.getEntity() instanceof Mob mob)) return;
        if (mob.getTags().contains(TAG)) {
            addBloodlustGoals(mob);
        } else if (!event.loadedFromDisk() && EventScheduler.isActive(ID) && canFight(mob)) {
            EventUtil.defer(() -> haze(mob));
        }
    }

    @SubscribeEvent
    public static void onExpire(MobEffectEvent.Expired event) {
        MobEffectInstance instance = event.getEffectInstance();
        if (instance == null || !instance.getEffect().is(ModEffects.BLOODLUST_HAZE.getKey())) return;
        if (!(event.getEntity() instanceof Mob mob) || mob.level().isClientSide()) return;
        PacketDistributor.sendToPlayersTrackingEntity(mob, new EntityMarkPayload(mob.getId(), 0));
        mob.addTag(TAG);
        mob.setPersistenceRequired();
        addBloodlustGoals(mob);
    }

    @SubscribeEvent
    public static void onRemoved(MobEffectEvent.Remove event) {
        MobEffectInstance instance = event.getEffectInstance();
        if (instance != null && instance.getEffect().is(ModEffects.BLOODLUST_HAZE.getKey()) && !event.getEntity().level().isClientSide()) {
            PacketDistributor.sendToPlayersTrackingEntity(event.getEntity(), new EntityMarkPayload(event.getEntity().getId(), 0));
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

    /** Glowing red eyes hovering over bloodlusted mobs. */
    @SubscribeEvent
    public static void onTick(EntityTickEvent.Post event) {
        Entity e = event.getEntity();
        if (e.tickCount % 4 != 0 || !(e.level() instanceof ServerLevel level) || !e.getTags().contains(TAG)) return;
        Vec3 look = e.getViewVector(1.0F).multiply(1, 0, 1).normalize();
        Vec3 side = new Vec3(-look.z, 0, look.x).scale(0.15 * Math.max(1, e.getBbWidth()));
        double y = e.getY() + e.getBbHeight() + 0.25;
        level.sendParticles(EYE, e.getX() + side.x, y, e.getZ() + side.z, 1, 0, 0, 0, 0);
        level.sendParticles(EYE, e.getX() - side.x, y, e.getZ() - side.z, 1, 0, 0, 0, 0);
    }

    @SubscribeEvent
    public static void onKill(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof Mob killer) || !killer.getTags().contains(TAG)) return;
        int kills = killer.getPersistentData().getIntOr("impendingdoom_kills", 0) + 1;
        killer.getPersistentData().putInt("impendingdoom_kills", kills);
        EventUtil.addModifier(killer, Attributes.SCALE, "bloodlust_scale", Math.min(2.0, kills * 0.1), AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        EventUtil.addModifier(killer, Attributes.MAX_HEALTH, "bloodlust_health", kills * 4.0, AttributeModifier.Operation.ADD_VALUE);
        EventUtil.addModifier(killer, Attributes.ATTACK_DAMAGE, "bloodlust_damage", kills * 1.0, AttributeModifier.Operation.ADD_VALUE);
        killer.heal(8.0F);
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof LivingEntity living && living.hasEffect(ModEffects.BLOODLUST_HAZE)
                && event.getEntity() instanceof ServerPlayer player) {
            PacketDistributor.sendToPlayer(player, new EntityMarkPayload(living.getId(), EntityMarkPayload.SHAKING));
        }
    }
}
