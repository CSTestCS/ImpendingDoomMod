package com.impendingdoom.item;

import com.impendingdoom.entity.NetherflameBottle;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Splash potion of Netherflame: converts the area it shatters on into nether materials. */
public class NetherflameItem extends Item {
    public NetherflameItem(Properties properties) {
        super(properties.stacksTo(16));
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SPLASH_POTION_THROW, SoundSource.PLAYERS, 0.5F, 0.4F);
        if (!level.isClientSide()) {
            NetherflameBottle bottle = new NetherflameBottle(level, player, stack.copyWithCount(1));
            bottle.shootFromRotation(player, player.getXRot(), player.getYRot(), -20.0F, 0.5F, 1.0F);
            level.addFreshEntity(bottle);
        }
        stack.consume(1, player);
        return InteractionResult.SUCCESS;
    }
}
