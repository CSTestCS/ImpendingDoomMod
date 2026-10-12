package com.impendingdoom.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Handed to every player when an event begins. Its name says which event comes next and its lore when it arrives.
 */
public class DoomOmenItem extends Item {
    public DoomOmenItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }
}
