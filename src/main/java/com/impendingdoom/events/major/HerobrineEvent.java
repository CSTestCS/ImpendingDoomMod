package com.impendingdoom.events.major;

import com.impendingdoom.core.DoomEvent;
import com.impendingdoom.core.EventScheduler;
import com.impendingdoom.entity.Herobrine;
import com.impendingdoom.registry.ModEntities;
import com.impendingdoom.util.EventUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;

import java.util.UUID;

/** Herobrine: dense fog (client) and the one and only Herobrine (see {@link Herobrine}). */
public class HerobrineEvent extends DoomEvent {
    public static final String ID = "herobrine";

    public HerobrineEvent() {
        super(ID, true, ChatFormatting.DARK_RED);
    }

    public static boolean isTheOne(Herobrine herobrine) {
        UUID id = EventUtil.uuid(EventScheduler.eventData(ID), "herobrine");
        return herobrine.getUUID().equals(id);
    }

    @Override
    public void onTick(ServerLevel level, CompoundTag data) {
        if (level.getGameTime() % 200 != 0) return;
        UUID id = EventUtil.uuid(data, "herobrine");
        Entity existing = id == null ? null : level.getEntity(id);
        if (existing != null && existing.isAlive()) return;
        // He was left behind in unloaded chunks (or never existed): he finds the players again.
        ServerPlayer player = EventUtil.randomTarget(level);
        if (player == null) return;
        BlockPos pos = EventUtil.surfacePos(level, player.blockPosition(), 30, 40, level.getRandom());
        if (pos == null) return;
        Herobrine herobrine = ModEntities.HEROBRINE.get().create(level, EntitySpawnReason.EVENT);
        if (herobrine == null) return;
        herobrine.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        data.putString("herobrine", herobrine.getUUID().toString());
        level.addFreshEntity(herobrine);
    }

    @Override
    public void onEnd(ServerLevel level, CompoundTag data) {
        UUID id = EventUtil.uuid(data, "herobrine");
        Entity existing = id == null ? null : level.getEntity(id);
        if (existing != null) existing.discard();
    }
}
