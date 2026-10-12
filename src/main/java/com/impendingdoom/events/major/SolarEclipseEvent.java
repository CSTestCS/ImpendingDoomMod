package com.impendingdoom.events.major;

import com.impendingdoom.ImpendingDoom;
import com.impendingdoom.core.DayClock;
import com.impendingdoom.core.DoomEvent;
import com.impendingdoom.core.EventScheduler;
import com.impendingdoom.util.EventUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * Solar Eclipse: the sun is obscured, day is as dark as night (client), monsters spawn by day and do not burn, and
 * wardens and creakings spawn like ordinary monsters (creakings with their walking heart).
 */
@EventBusSubscriber(modid = ImpendingDoom.MODID)
public class SolarEclipseEvent extends DoomEvent {
    public static final String ID = "solar_eclipse";
    private static final EntityType<?>[] SPAWNS = {EntityTypes.ZOMBIE, EntityTypes.ZOMBIE, EntityTypes.SKELETON, EntityTypes.SKELETON,
            EntityTypes.SPIDER, EntityTypes.CREEPER, EntityTypes.ENDERMAN, EntityTypes.WITCH, EntityTypes.CREAKING, EntityTypes.CREAKING,
            EntityTypes.WARDEN};

    public SolarEclipseEvent() {
        super(ID, true, ChatFormatting.DARK_RED);
    }

    @Override
    public void onTick(ServerLevel level, CompoundTag data) {
        if (!DayClock.isDay(level) || level.getGameTime() % 60 != 0) return;
        RandomSource r = level.getRandom();
        for (ServerPlayer player : EventUtil.targets(level)) {
            if (level.getEntitiesOfClass(Monster.class, player.getBoundingBox().inflate(64)).size() > 40) continue;
            BlockPos pos = EventUtil.surfacePos(level, player.blockPosition(), 24, 64, r);
            if (pos == null || level.getBrightness(LightLayer.BLOCK, pos) > 0) continue;
            EntityType<?> type = SPAWNS[r.nextInt(SPAWNS.length)];
            if (type == EntityTypes.WARDEN && (r.nextInt(4) != 0
                    || !level.getEntitiesOfClass(net.minecraft.world.entity.monster.warden.Warden.class, player.getBoundingBox().inflate(96)).isEmpty())) {
                continue;
            }
            EventUtil.spawn(level, type, pos);
        }
    }

    /** No burning in the eclipse. */
    @SubscribeEvent
    public static void onMobTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof Mob mob) || !(mob.level() instanceof ServerLevel level)) return;
        if (!mob.isOnFire() || !mob.getType().builtInRegistryHolder().is(EntityTypeTags.UNDEAD) || !EventScheduler.isActive(ID)) return;
        if (mob.isInLava() || level.getBlockState(mob.blockPosition()).is(Blocks.FIRE) || !level.canSeeSky(mob.blockPosition())) return;
        if (mob.getLastDamageSource() != null && mob.getLastDamageSource().getEntity() != null && mob.tickCount - mob.getLastHurtByMobTimestamp() < 100) return;
        mob.clearFire();
    }
}
