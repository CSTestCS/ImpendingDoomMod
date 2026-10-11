package com.impendingdoom.effect;

import com.impendingdoom.ImpendingDoom;
import com.impendingdoom.registry.ModEffects;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * Stunned: lasts one second and disables movement, attacking and interaction.
 * Inflicted by the attacks of mobs charged during an Electrical Storm.
 */
@EventBusSubscriber(modid = ImpendingDoom.MODID)
public class StunnedEffect extends MobEffect {
    public StunnedEffect() {
        super(MobEffectCategory.HARMFUL, 0xF4F06B);
        addAttributeModifier(Attributes.MOVEMENT_SPEED, ImpendingDoom.id("stunned_speed"), -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        addAttributeModifier(Attributes.JUMP_STRENGTH, ImpendingDoom.id("stunned_jump"), -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        addAttributeModifier(Attributes.FLYING_SPEED, ImpendingDoom.id("stunned_fly"), -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return true;
    }

    @Override
    public boolean applyEffectTick(ServerLevel level, LivingEntity entity, int amplifier) {
        Vec3 v = entity.getDeltaMovement();
        entity.setDeltaMovement(0, Math.min(v.y, 0), 0);
        if (entity instanceof Mob mob) mob.getNavigation().stop();
        if (level.getGameTime() % 4 == 0) {
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK,
                    entity.getX(), entity.getEyeY() + 0.3, entity.getZ(), 2, 0.25, 0.1, 0.25, 0.0);
        }
        return true;
    }

    public static boolean isStunned(Entity entity) {
        return entity instanceof LivingEntity living && living.hasEffect(ModEffects.STUNNED);
    }

    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        if (isStunned(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onMobAttack(LivingIncomingDamageEvent event) {
        Entity attacker = event.getSource().getEntity();
        if (attacker != null && attacker == event.getSource().getDirectEntity() && isStunned(attacker)) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (isStunned(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (isStunned(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (isStunned(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (isStunned(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (isStunned(event.getPlayer())) event.setCanceled(true);
    }
}
