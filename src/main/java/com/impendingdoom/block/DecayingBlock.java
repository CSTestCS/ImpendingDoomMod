package com.impendingdoom.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Supplier;

/**
 * A natural block thrown by a tornado. It looks like the original block and wears away one layer at a time, like
 * melting snow, until it is gone two days later. Mining it drops the original block.
 */
public class DecayingBlock extends SnowLayerBlock {
    /** Two Minecraft days spread over eight layers. */
    public static final int STEP_TICKS = 48000 / 8;
    private final Supplier<Block> original;

    public DecayingBlock(Properties properties, Supplier<Block> original) {
        super(properties);
        this.original = original;
        registerDefaultState(stateDefinition.any().setValue(LAYERS, 8));
    }

    public Block original() {
        return original.get();
    }

    @Override
    public Item asItem() {
        return original.get().asItem();
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide() && !oldState.is(this)) level.scheduleTick(pos, this, STEP_TICKS);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int layers = state.getValue(LAYERS);
        if (layers <= 1) {
            level.removeBlock(pos, false);
        } else {
            level.setBlock(pos, state.setValue(LAYERS, layers - 1), Block.UPDATE_ALL);
            level.scheduleTick(pos, this, STEP_TICKS);
        }
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        // Does not melt in light like snow; it decays on its own schedule.
    }
}
