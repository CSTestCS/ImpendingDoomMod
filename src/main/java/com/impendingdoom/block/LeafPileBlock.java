package com.impendingdoom.block;

import com.impendingdoom.events.major.FallEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A full block of fallen leaves. Works as a mob spawner for decaying creepers, even in the light.
 */
public class LeafPileBlock extends Block {
    public LeafPileBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide()) level.scheduleTick(pos, this, 200 + level.getRandom().nextInt(400));
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        FallEvent.leafPileSpawn(level, pos, random);
        level.scheduleTick(pos, this, 300 + random.nextInt(600));
    }
}
