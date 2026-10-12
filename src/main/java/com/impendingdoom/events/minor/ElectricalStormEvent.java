package com.impendingdoom.events.minor;

import com.impendingdoom.ImpendingDoom;
import com.impendingdoom.core.DoomEvent;
import com.impendingdoom.core.EventScheduler;
import com.impendingdoom.registry.ModEffects;
import com.impendingdoom.util.EventUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityStruckByLightningEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import java.util.List;

/**
 * Electrical Storm: a long, intense thunderstorm. Any monster can be charged by lightning, and fire-immune charged
 * breezes may spawn from strikes. Charged mobs' attacks inflict Stunned.
 */
@EventBusSubscriber(modid = ImpendingDoom.MODID)
public class ElectricalStormEvent extends DoomEvent {
    public static final String ID = "electrical_storm";
    public static final String CHARGED = "impendingdoom.charged";

    public ElectricalStormEvent() {
        super(ID, false, ChatFormatting.DARK_GREEN);
    }

    @Override
    public void onStart(ServerLevel level, CompoundTag data) {
        EventUtil.setWeather(level, "thunder", 36000);
    }

    @Override
    public void onTick(ServerLevel level, CompoundTag data) {
        int age = data.getIntOr("age", 0) + 1;
        data.putInt("age", age);
        if (!level.isThundering()) return;
        for (ServerPlayer player : level.players()) {
            if (level.getRandom().nextInt(90) != 0) continue;
            BlockPos strike;
            List<Mob> monsters = level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(40),
                    m -> m instanceof Enemy && !m.entityTags().contains(CHARGED) && level.canSeeSky(m.blockPosition()));
            if (!monsters.isEmpty() && level.getRandom().nextInt(3) == 0) {
                strike = monsters.get(level.getRandom().nextInt(monsters.size())).blockPosition();
            } else {
                BlockPos around = player.blockPosition().offset(level.getRandom().nextInt(80) - 40, 0, level.getRandom().nextInt(80) - 40);
                if (!level.isLoaded(around)) continue;
                strike = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, around);
            }
            LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.EVENT);
            if (bolt != null) {
                bolt.setPos(strike.getX() + 0.5, strike.getY(), strike.getZ() + 0.5);
                level.addFreshEntity(bolt);
            }
        }
    }

    @Override
    public boolean isComplete(ServerLevel level, CompoundTag data) {
        return data.getIntOr("age", 0) > 200 && !level.isThundering();
    }

    public static void charge(Mob mob) {
        mob.addTag(CHARGED);
        com.impendingdoom.util.EntityMarks.update(mob);
        EventUtil.addModifier(mob, Attributes.ATTACK_DAMAGE, "charged_damage", 0.25, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        EventUtil.addModifier(mob, Attributes.MOVEMENT_SPEED, "charged_speed", 0.15, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
    }

    @SubscribeEvent
    public static void onLightning(EntityStruckByLightningEvent event) {
        if (!EventScheduler.isActive(ID)) return;
        Entity entity = event.getEntity();
        if (entity instanceof Mob mob && entity instanceof Enemy && !(entity instanceof Creeper)) {
            event.setCanceled(true); // charged instead of burned
            if (!mob.entityTags().contains(CHARGED)) charge(mob);
        }
    }

    @SubscribeEvent
    public static void onBoltSpawn(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || event.loadedFromDisk() || !(event.getEntity() instanceof LightningBolt bolt)) return;
        if (!EventScheduler.isActive(ID) || level.getRandom().nextInt(5) != 0) return;
        BlockPos pos = bolt.blockPosition();
        EventUtil.defer(() -> {
            Entity e = EventUtil.spawn(level, EntityTypes.BREEZE, pos);
            if (e instanceof Mob breeze) {
                charge(breeze);
                breeze.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, MobEffectInstance.INFINITE_DURATION, 0, false, false));
            }
        });
    }


    @SubscribeEvent
    public static void onHit(LivingDamageEvent.Post event) {
        Entity attacker = event.getSource().getEntity();
        if (attacker == null || event.getEntity() == attacker) return;
        boolean charged = attacker.entityTags().contains(CHARGED) || (attacker instanceof Creeper creeper && creeper.isPowered());
        if (charged) {
            LivingEntity victim = event.getEntity();
            victim.addEffect(new MobEffectInstance(ModEffects.STUNNED, 20, 0));
        }
    }
}
