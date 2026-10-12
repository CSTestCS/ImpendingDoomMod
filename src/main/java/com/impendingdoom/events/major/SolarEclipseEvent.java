package com.impendingdoom.events.major;

import com.impendingdoom.ImpendingDoom;
import com.impendingdoom.core.DayClock;
import com.impendingdoom.core.DoomEvent;
import com.impendingdoom.core.EventScheduler;
import com.impendingdoom.entity.HeartWalker;
import com.impendingdoom.util.CreakingCompanions;
import com.impendingdoom.util.EventUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.creaking.Creaking;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;

/**
 * Solar Eclipse: the sun is obscured and day is as dark as night (EnvironmentAttributeSystemMixin), monsters spawn by
 * day and do not burn, and wardens and creakings spawn like ordinary monsters (creakings with their walking heart).
 * Wardens leave creakings and their hearts alone. Eclipse creakings and hearts vanish when the eclipse ends.
 */
@EventBusSubscriber(modid = ImpendingDoom.MODID)
public class SolarEclipseEvent extends DoomEvent {
    public static final String ID = "solar_eclipse";
    public static final String TAG = "impendingdoom.eclipse";
    private static final EntityType<?>[] SPAWNS = {EntityTypes.ZOMBIE, EntityTypes.ZOMBIE, EntityTypes.SKELETON, EntityTypes.SKELETON,
            EntityTypes.SPIDER, EntityTypes.CREEPER, EntityTypes.ENDERMAN, EntityTypes.WITCH, EntityTypes.CREAKING, EntityTypes.CREAKING,
            EntityTypes.WARDEN};

    public SolarEclipseEvent() {
        super(ID, true, ChatFormatting.DARK_RED);
    }

    @Override
    public void onTick(ServerLevel level, CompoundTag data) {
        if (level.getGameTime() % 20 == 0) calmWardens(level);
        if (!DayClock.isDay(level) || level.getGameTime() % 60 != 0) return;
        RandomSource r = level.getRandom();
        for (ServerPlayer player : EventUtil.targets(level)) {
            if (level.getEntitiesOfClass(Monster.class, player.getBoundingBox().inflate(64)).size() > 40) continue;
            BlockPos pos = EventUtil.surfacePos(level, player.blockPosition(), 24, 64, r);
            if (pos == null || level.getBrightness(LightLayer.BLOCK, pos) > 0) continue;
            EntityType<?> type = SPAWNS[r.nextInt(SPAWNS.length)];
            if (type == EntityTypes.WARDEN && (r.nextInt(4) != 0 || !level.getEntitiesOfClass(Warden.class, player.getBoundingBox().inflate(96)).isEmpty())) {
                continue;
            }
            Entity spawned = EventUtil.spawn(level, type, pos);
            if (spawned instanceof Creaking creaking) {
                creaking.addTag(TAG);
                HeartWalker heart = CreakingCompanions.heartOf(level, creaking);
                if (heart != null) heart.addTag(TAG);
            }
        }
    }

    /** Wardens never hold a grudge against creakings or walking hearts. */
    private static void calmWardens(ServerLevel level) {
        for (Warden warden : level.getEntities(EntityTypeTest.forClass(Warden.class), Entity::isAlive)) {
            for (Mob m : level.getEntitiesOfClass(Mob.class, warden.getBoundingBox().inflate(48), SolarEclipseEvent::ignoredByWardens)) {
                warden.clearAnger(m);
            }
            if (ignoredByWardens(warden.getTarget())) warden.setAttackTarget(null);
        }
    }

    private static boolean ignoredByWardens(Entity e) {
        return e instanceof Creaking || e instanceof HeartWalker;
    }

    @SubscribeEvent
    public static void onTarget(LivingChangeTargetEvent event) {
        LivingEntity target = event.getNewAboutToBeSetTarget();
        if (event.getEntity() instanceof Warden && ignoredByWardens(target)) event.setCanceled(true);
    }

    @Override
    public void onEnd(ServerLevel level, CompoundTag data) {
        for (Mob m : level.getEntities(EntityTypeTest.forClass(Mob.class), m -> m.entityTags().contains(TAG))) m.discard();
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.loadedFromDisk() && event.getEntity().entityTags().contains(TAG)
                && !EventScheduler.isActive(ID)) {
            event.setCanceled(true);
        }
    }
}
