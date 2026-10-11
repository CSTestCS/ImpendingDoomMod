package com.impendingdoom.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/** Shared logic for light sources that burned out during a Blackout. */
public interface Relightable {
    /** True while the light is only flickering; it comes back on by itself after a few ticks. */
    BooleanProperty FLICKER = BooleanProperty.create("flicker");

    BlockState litState(BlockState unlit);

    static InteractionResult tryRelight(Relightable self, ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand) {
        if (!stack.is(Items.FLINT_AND_STEEL)) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (!level.isClientSide()) {
            level.setBlock(pos, self.litState(state), Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 1.0F, level.getRandom().nextFloat() * 0.4F + 0.8F);
            stack.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
        }
        return InteractionResult.SUCCESS;
    }

    static void flickerBack(Relightable self, BlockState state, ServerLevel level, BlockPos pos) {
        if (state.getValue(FLICKER)) level.setBlock(pos, self.litState(state), Block.UPDATE_ALL);
    }
}
