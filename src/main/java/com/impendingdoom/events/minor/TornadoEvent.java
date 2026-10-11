package com.impendingdoom.events.minor;

import com.impendingdoom.core.DoomEvent;
import com.impendingdoom.core.EventScheduler;
import com.impendingdoom.entity.TornadoEntity;
import com.impendingdoom.registry.ModEntities;
import com.impendingdoom.util.EventUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.UUID;

/** Tornado: a tornado approaches, tracking a player. Lasts half to three quarters of a day. */
public class TornadoEvent extends DoomEvent {
    public static final String ID = "tornado";

    public TornadoEvent() {
        super(ID, false, ChatFormatting.DARK_GREEN);
    }

    public static boolean isTornadoActive() {
        return EventScheduler.isActive(ID);
    }

    @Override
    public void onStart(ServerLevel level, CompoundTag data) {
        data.putInt("remaining", 12000 + level.getRandom().nextInt(6000));
        spawn(level, data);
        level.setWeatherParameters(0, 18000, true, false);
    }

    private void spawn(ServerLevel level, CompoundTag data) {
        ServerPlayer target = EventUtil.randomTarget(level);
        if (target == null) return;
        BlockPos pos = EventUtil.surfacePos(level, target.blockPosition(), 64, 80, level.getRandom());
        if (pos == null) return;
        TornadoEntity tornado = ModEntities.TORNADO.get().create(level, net.minecraft.world.entity.EntitySpawnReason.EVENT);
        if (tornado == null) return;
        tornado.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        tornado.setTarget(target);
        level.addFreshEntity(tornado);
        data.putString("tornado", tornado.getUUID().toString());
        target.sendSystemMessage(Component.translatable("message.impendingdoom.tornado_tracking").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }

    @Override
    public void onTick(ServerLevel level, CompoundTag data) {
        data.putInt("remaining", data.getIntOr("remaining", 0) - 1);
        if (level.getGameTime() % 200 != 0) return;
        UUID id = EventUtil.uuid(data, "tornado");
        Entity tornado = id == null ? null : level.getEntity(id);
        if (tornado == null || !tornado.isAlive()) {
            // The tornado was left behind in unloaded chunks: it catches up.
            int missing = data.getIntOr("missing", 0) + 1;
            data.putInt("missing", missing);
            if (missing >= 3) {
                data.putInt("missing", 0);
                spawn(level, data);
            }
        } else {
            data.putInt("missing", 0);
        }
    }

    @Override
    public boolean isComplete(ServerLevel level, CompoundTag data) {
        return data.getIntOr("remaining", 0) <= 0;
    }

    @Override
    public void onEnd(ServerLevel level, CompoundTag data) {
        UUID id = EventUtil.uuid(data, "tornado");
        Entity tornado = id == null ? null : level.getEntity(id);
        if (tornado != null) tornado.discard();
    }
}
