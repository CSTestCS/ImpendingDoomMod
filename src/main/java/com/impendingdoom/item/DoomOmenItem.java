package com.impendingdoom.item;

import com.impendingdoom.core.DoomEvent;
import com.impendingdoom.core.DoomSavedData;
import com.impendingdoom.core.EventRegistry;
import com.impendingdoom.core.EventScheduler;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

/**
 * Handed to every player when an event begins: names the next event (in its name and lore).
 * Using it reads the prophecy aloud and tells you whether it is still the current one.
 */
public class DoomOmenItem extends Item {
    public DoomOmenItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level instanceof ServerLevel serverLevel) {
            ItemStack stack = player.getItemInHand(hand);
            CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
            DoomEvent foretold = EventRegistry.get(tag.getStringOr("event", ""));
            DoomSavedData d = EventScheduler.data();
            if (foretold == null || d == null) {
                player.sendSystemMessage(Component.translatable("omen.impendingdoom.blank").withStyle(ChatFormatting.GRAY));
            } else if (!foretold.id().equals(d.nextEvent)) {
                player.sendSystemMessage(Component.translatable("omen.impendingdoom.faded", foretold.displayName()).withStyle(ChatFormatting.GRAY));
            } else {
                long arrival = EventScheduler.nextEventDay(serverLevel.getServer().overworld(), d);
                player.sendSystemMessage(Component.translatable("omen.impendingdoom.reading", foretold.displayName(), arrival + 1)
                        .withStyle(ChatFormatting.DARK_PURPLE));
                player.sendSystemMessage(foretold.description().withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }
}
