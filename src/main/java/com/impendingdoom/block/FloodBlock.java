package com.impendingdoom.block;

import com.impendingdoom.events.major.FloodingEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Temporary, layer based flood fluid. The block exposes a non-source flowing fluid state with the level of its
 * layer, so entities swim and drown in it and it renders like water/lava. Nothing ever schedules a fluid tick for
 * it, so it never spreads and never creates sources. It removes itself once the flood falls below it.
 */
public class FloodBlock extends Block {
    public static final IntegerProperty LAYERS = IntegerProperty.create("layers", 1, 8);

    private final FlowingFluid fluid;
    private final boolean lava;

    public FloodBlock(Properties properties, FlowingFluid fluid, boolean lava) {
        super(properties);
        this.fluid = fluid;
        this.lava = lava;
        registerDefaultState(stateDefinition.any().setValue(LAYERS, 8));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LAYERS);
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return fluid.getFlowing(state.getValue(LAYERS), false);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected boolean canBeReplaced(BlockState state, Fluid fluid) {
        // Vanilla fluids must not flow into (and replace) the flood.
        return false;
    }

    @Override
    protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
        return ItemStack.EMPTY;
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state) {
        return !lava;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!FloodingEvent.shouldExist(level, pos, lava)) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        } else {
            getFluidState(state).randomTick(level, pos, random);
        }
    }

    @Override
    protected boolean skipRendering(BlockState state, BlockState adjacent, Direction direction) {
        return adjacent.is(this) || super.skipRendering(state, adjacent, direction);
    }

    public boolean isLava() {
        return lava;
    }
}
