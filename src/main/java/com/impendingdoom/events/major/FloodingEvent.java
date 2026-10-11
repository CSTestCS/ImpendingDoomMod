package com.impendingdoom.events.major;

import com.impendingdoom.block.FloodBlock;
import com.impendingdoom.config.DoomConfig;
import com.impendingdoom.core.DayClock;
import com.impendingdoom.core.DoomEvent;
import com.impendingdoom.core.DoomSavedData;
import com.impendingdoom.core.EventScheduler;
import com.impendingdoom.registry.ModBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Flooding / Volcanic Flooding: a temporary, layer based fluid rises from its starting level (sea level for water,
 * deep underground for lava) and evenly fills every air block at its level. It rises a little each day (water rises
 * faster in rain) and rapidly drains away over the final three days before the next major event.
 * Volcanic Flooding only fills underground space.
 */
public class FloodingEvent extends DoomEvent {
    public static final String WATER_ID = "flooding";
    public static final String LAVA_ID = "volcanic_flooding";
    private static final int RADIUS = 48;
    private static final int COLUMNS_PER_TICK = 48;

    private final boolean lava;

    public FloodingEvent(String id, boolean lava) {
        super(id, true, lava ? ChatFormatting.DARK_RED : ChatFormatting.BLUE);
        this.lava = lava;
    }

    private Block block() {
        return lava ? ModBlocks.FLOOD_LAVA.get() : ModBlocks.FLOOD_WATER.get();
    }

    private static int baseLevel(ServerLevel level, boolean lava) {
        return lava ? level.getMinY() + 10 : level.getSeaLevel();
    }

    /** Current surface of the flood, or NaN when inactive. */
    public static double surface(boolean lava) {
        String id = lava ? LAVA_ID : WATER_ID;
        if (!EventScheduler.isActive(id)) return Double.NaN;
        return EventScheduler.eventData(id).getDoubleOr("surface", Double.NaN);
    }

    /** Used by flood blocks to remove themselves once the flood has gone down. */
    public static boolean shouldExist(ServerLevel level, BlockPos pos, boolean lava) {
        double surface = surface(lava);
        return !Double.isNaN(surface) && pos.getY() < Math.ceil(surface);
    }

    @Override
    public void onStart(ServerLevel level, CompoundTag data) {
        double base = baseLevel(level, lava);
        data.putDouble("surface", base);
        data.putDouble("peak", base);
        data.putLong("lastTime", DayClock.dayTime(level));
    }

    @Override
    public void onTick(ServerLevel level, CompoundTag data) {
        DoomSavedData d = EventScheduler.data();
        double base = baseLevel(level, lava);
        double surface = data.getDoubleOr("surface", base);
        long now = DayClock.dayTime(level);
        long elapsed = Math.max(0, now - data.getLongOr("lastTime", now));
        data.putLong("lastTime", now);

        long daysLeft = d == null ? 99 : EventScheduler.daysUntilNextMajor(level, d);
        double dayFraction = 1.0 - DayClock.timeOfDay(level) / (double) DayClock.DAY_LENGTH;
        double remaining = daysLeft - 1 + dayFraction; // days until the next major event
        if (remaining > 3) {
            double perDay = lava ? DoomConfig.LAVA_RISE_PER_DAY.get() : DoomConfig.FLOOD_RISE_PER_DAY.get();
            if (!lava && level.isRaining()) perDay *= level.isThundering() ? 3 : 2;
            surface += perDay * elapsed / DayClock.DAY_LENGTH;
            surface = Math.min(surface, level.getMaxY() - 2);
            data.putDouble("peak", surface);
        } else {
            // Drain rapidly over the last three days.
            double peak = data.getDoubleOr("peak", surface);
            surface = base + (peak - base) * Math.max(0, remaining - 0.25) / 2.75;
        }
        data.putDouble("surface", surface);

        RandomSource r = level.getRandom();
        for (ServerPlayer player : level.players()) {
            for (int i = 0; i < COLUMNS_PER_TICK; i++) {
                int x = player.getBlockX() + r.nextInt(RADIUS * 2 + 1) - RADIUS;
                int z = player.getBlockZ() + r.nextInt(RADIUS * 2 + 1) - RADIUS;
                updateColumn(level, x, z, (int) base, surface);
            }
        }
    }

    private void updateColumn(ServerLevel level, int x, int z, int base, double surface) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, base, z);
        if (!level.isLoaded(pos)) return;
        Block flood = block();
        int full = Mth.floor(surface);
        int topLayers = Mth.clamp((int) Math.round((surface - full) * 8), 0, 8);
        int maxY = full + 3;
        for (int y = base; y <= maxY; y++) {
            pos.setY(y);
            BlockState state = level.getBlockState(pos);
            int wanted = y < full ? 8 : (y == full ? topLayers : 0);
            if (wanted > 0 && lava && level.canSeeSky(pos)) wanted = 0; // volcanic flooding stays underground
            if (state.is(flood)) {
                if (wanted == 0) level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                else if (state.getValue(FloodBlock.LAYERS) != wanted) level.setBlock(pos, state.setValue(FloodBlock.LAYERS, wanted), Block.UPDATE_CLIENTS);
            } else if (wanted > 0 && state.isAir()) {
                level.setBlock(pos, flood.defaultBlockState().setValue(FloodBlock.LAYERS, wanted), Block.UPDATE_CLIENTS);
            }
        }
    }

    @Override
    public void onEnd(ServerLevel level, CompoundTag data) {
        // Remaining flood blocks near players are cleared now; any others remove themselves on their next random tick.
        Block flood = block();
        for (ServerPlayer player : level.players()) {
            BlockPos c = player.blockPosition();
            int base = baseLevel(level, lava);
            int top = (int) Math.ceil(data.getDoubleOr("peak", base)) + 1;
            for (BlockPos p : BlockPos.betweenClosed(c.getX() - RADIUS, base, c.getZ() - RADIUS, c.getX() + RADIUS, top, c.getZ() + RADIUS)) {
                if (level.getBlockState(p).is(flood)) level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
    }
}
