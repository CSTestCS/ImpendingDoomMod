package com.impendingdoom.util;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/** Respawn point lookups, isolated because this API moves around between Minecraft versions. */
public final class PlayerUtil {
    private PlayerUtil() {}

    public static BlockPos worldSpawn(ServerLevel level) {
        return level.getRespawnData().pos();
    }

    /** The player's bed/anchor if it is in the overworld, otherwise the world spawn. */
    public static BlockPos overworldRespawnPos(ServerPlayer player) {
        ServerLevel overworld = player.level().getServer().overworld();
        ServerPlayer.RespawnConfig config = player.getRespawnConfig();
        if (config != null && config.respawnData().dimension() == Level.OVERWORLD) {
            return config.respawnData().pos();
        }
        return worldSpawn(overworld);
    }
}
