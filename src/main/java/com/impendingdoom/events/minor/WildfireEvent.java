package com.impendingdoom.events.minor;

import com.impendingdoom.core.DayClock;
import com.impendingdoom.core.DoomEvent;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Wildfire: fire spreads faster, grass and leaves ignite readily, and grass blocks are flammable - burnt grass
 * turns to coarse dirt.
 */
public class WildfireEvent extends DoomEvent {
    public static final String ID = "wildfire";

    public WildfireEvent() {
        super(ID, false, ChatFormatting.DARK_GREEN);
    }

    private static boolean kindling(BlockState state) {
        return state.is(Blocks.GRASS_BLOCK) || state.is(BlockTags.LEAVES) || state.is(Blocks.SHORT_GRASS) || state.is(Blocks.TALL_GRASS)
                || state.is(Blocks.FERN) || state.is(Blocks.LARGE_FERN) || state.is(Blocks.DEAD_BUSH) || state.is(BlockTags.FLOWERS)
                || state.is(Blocks.HAY_BLOCK) || state.is(BlockTags.OVERWORLD_NATURAL_LOGS) || state.is(BlockTags.PLANKS);
    }

    @Override
    public void onTick(ServerLevel level, CompoundTag data) {
        if (level.getGameTime() % 5 != 0) return;
        RandomSource r = level.getRandom();
        for (ServerPlayer player : level.players()) {
            BlockPos center = player.blockPosition();
            for (int i = 0; i < 400; i++) {
                BlockPos pos = center.offset(r.nextInt(49) - 24, r.nextInt(17) - 8, r.nextInt(49) - 24);
                if (level.getBlockState(pos).is(BlockTags.FIRE)) burn(level, pos, r);
            }
            // Dry, sunny days: the odd spontaneous brush fire.
            if (!level.isRaining() && DayClock.isDay(level) && r.nextInt(400) == 0) {
                BlockPos column = center.offset(r.nextInt(97) - 48, 0, r.nextInt(97) - 48);
                if (level.isLoaded(column)) {
                    BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, column);
                    if (kindling(level.getBlockState(top.below())) && top.distSqr(center) > 16 * 16) ignite(level, top);
                }
            }
        }
    }

    private static void burn(ServerLevel level, BlockPos fire, RandomSource r) {
        BlockState below = level.getBlockState(fire.below());
        if ((below.is(Blocks.GRASS_BLOCK) || below.is(Blocks.PODZOL) || below.is(Blocks.MYCELIUM)) && r.nextInt(3) == 0) {
            level.setBlockAndUpdate(fire.below(), Blocks.COARSE_DIRT.defaultBlockState());
        }
        if (level.isRainingAt(fire)) return;
        for (int i = 0; i < 2; i++) {
            BlockPos target = fire.offset(r.nextInt(5) - 2, r.nextInt(4) - 1, r.nextInt(5) - 2);
            BlockState state = level.getBlockState(target);
            if (state.is(Blocks.SHORT_GRASS) || state.is(Blocks.TALL_GRASS) || state.is(Blocks.FERN) || state.is(BlockTags.FLOWERS)) {
                level.setBlockAndUpdate(target, BaseFireBlock.getState(level, target));
            } else if (kindling(state) || state.isFlammable(level, target, Direction.UP)) {
                for (Direction d : Direction.values()) {
                    BlockPos adj = target.relative(d);
                    if (level.getBlockState(adj).isAir()) {
                        ignite(level, adj);
                        break;
                    }
                }
            }
        }
    }

    private static void ignite(ServerLevel level, BlockPos pos) {
        if (level.getBlockState(pos).isAir()) {
            BlockState fire = BaseFireBlock.getState(level, pos);
            if (fire.canSurvive(level, pos) || level.getBlockState(pos.below()).is(Blocks.GRASS_BLOCK)) {
                level.setBlockAndUpdate(pos, fire);
            }
        }
    }
}
