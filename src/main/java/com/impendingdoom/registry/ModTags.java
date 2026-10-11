package com.impendingdoom.registry;

import com.impendingdoom.ImpendingDoom;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.resources.ResourceKey;

public final class ModTags {
    /** Armor that overheats the wearer during Summer days. */
    public static final TagKey<Item> HEAVY_ARMOR = TagKey.create(Registries.ITEM, ImpendingDoom.id("heavy_armor"));
    /** Armor that keeps the wearer warm during Winter. */
    public static final TagKey<Item> WARM_ARMOR = TagKey.create(Registries.ITEM, ImpendingDoom.id("warm_armor"));

    public static final ResourceKey<Enchantment> WARPED = ResourceKey.create(Registries.ENCHANTMENT, ImpendingDoom.id("warped"));

    private ModTags() {}
}
