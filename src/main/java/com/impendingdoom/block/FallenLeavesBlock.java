package com.impendingdoom.block;

import com.impendingdoom.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Leaves shed during Fall. Stacks like snow layers; when a pile fills a full block it becomes a
 * {@link LeafPileBlock}, which spawns decaying creepers.
 */
public class FallenLeavesBlock extends SnowLayerBlock {
    public FallenLeavesBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        // Unlike snow, leaves do not melt in light.
    }

    /** Adds one layer at the given position, converting to a leaf pile when the block fills up. */
    public static void addLayer(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.is(ModBlocks.FALLEN_LEAVES.get())) {
            int layers = state.getValue(LAYERS);
            if (layers >= 7) {
                level.setBlockAndUpdate(pos, ModBlocks.LEAF_PILE.get().defaultBlockState());
            } else {
                level.setBlockAndUpdate(pos, state.setValue(LAYERS, layers + 1));
            }
        } else if (state.isAir() || state.canBeReplaced()) {
            BlockState leaves = ModBlocks.FALLEN_LEAVES.get().defaultBlockState();
            if (leaves.canSurvive(level, pos) && state.getFluidState().isEmpty()) level.setBlockAndUpdate(pos, leaves);
        }
    }
}
