package com.impendingdoom.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;

import java.util.function.Supplier;

public class UnlitLanternBlock extends LanternBlock implements Relightable {
    private final Supplier<Block> lit;

    public UnlitLanternBlock(Properties properties, Supplier<Block> lit) {
        super(properties);
        this.lit = lit;
        registerDefaultState(stateDefinition.any().setValue(HANGING, false).setValue(WATERLOGGED, false).setValue(FLICKER, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FLICKER);
    }

    @Override
    public BlockState litState(BlockState unlit) {
        return lit.get().defaultBlockState().setValue(HANGING, unlit.getValue(HANGING)).setValue(WATERLOGGED, unlit.getValue(WATERLOGGED));
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        return Relightable.tryRelight(this, stack, state, level, pos, player, hand);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        Relightable.flickerBack(this, state, level, pos);
        super.tick(state, level, pos, random);
    }
}
