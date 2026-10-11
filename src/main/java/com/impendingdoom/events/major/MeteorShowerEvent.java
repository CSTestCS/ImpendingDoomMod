package com.impendingdoom.events.major;

import com.impendingdoom.core.DoomEvent;
import com.impendingdoom.entity.MeteorEntity;
import com.impendingdoom.registry.ModEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/** Meteor Shower: end stone meteors rain down, destroying what they hit and leaving ore-filled boulders. */
public class MeteorShowerEvent extends DoomEvent {
    public static final String ID = "meteor_shower";

    public MeteorShowerEvent() {
        super(ID, true, ChatFormatting.DARK_RED);
    }

    @Override
    public void onTick(ServerLevel level, CompoundTag data) {
        RandomSource r = level.getRandom();
        for (ServerPlayer player : level.players()) {
            if (r.nextInt(900) != 0) continue;
            launch(level, player, r);
        }
    }

    private static void launch(ServerLevel level, ServerPlayer player, RandomSource r) {
        BlockPos landColumn = player.blockPosition().offset(r.nextInt(81) - 40, 0, r.nextInt(81) - 40);
        if (!level.isLoaded(landColumn)) return;
        BlockPos land = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, landColumn);
        double angle = r.nextDouble() * Math.PI * 2;
        Vec3 start = new Vec3(land.getX() + Math.cos(angle) * 80, Math.min(level.getMaxY() - 5, land.getY() + 160), land.getZ() + Math.sin(angle) * 80);
        if (!level.isLoaded(BlockPos.containing(start))) start = new Vec3(land.getX(), Math.min(level.getMaxY() - 5, land.getY() + 160), land.getZ());
        MeteorEntity meteor = ModEntities.METEOR.get().create(level, EntitySpawnReason.EVENT);
        if (meteor == null) return;
        meteor.setPos(start.x, start.y, start.z);
        meteor.setDeltaMovement(Vec3.atCenterOf(land).subtract(start).normalize().scale(1.6));
        level.addFreshEntity(meteor);
    }

}
