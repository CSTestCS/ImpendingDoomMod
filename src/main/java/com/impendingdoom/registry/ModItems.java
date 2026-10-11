package com.impendingdoom.registry;

import com.impendingdoom.ImpendingDoom;
import com.impendingdoom.item.DoomOmenItem;
import com.impendingdoom.item.NetherflameItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Function;
import java.util.function.Supplier;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, ImpendingDoom.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ImpendingDoom.MODID);

    public static final DeferredHolder<Item, Item> DOOM_OMEN = register("doom_omen", DoomOmenItem::new);
    public static final DeferredHolder<Item, Item> NETHERFLAME_BOTTLE = register("netherflame_bottle", NetherflameItem::new);
    public static final DeferredHolder<Item, Item> FALLEN_LEAVES = block("fallen_leaves", ModBlocks.FALLEN_LEAVES);
    public static final DeferredHolder<Item, Item> LEAF_PILE = block("leaf_pile", ModBlocks.LEAF_PILE);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.impendingdoom"))
            .icon(() -> new ItemStack(DOOM_OMEN.get()))
            .displayItems((params, output) -> {
                output.accept(DOOM_OMEN.get());
                output.accept(NETHERFLAME_BOTTLE.get());
                output.accept(FALLEN_LEAVES.get());
                output.accept(LEAF_PILE.get());
            })
            .build());

    private ModItems() {}

    private static DeferredHolder<Item, Item> register(String name, Function<Item.Properties, ? extends Item> factory) {
        return ITEMS.register(name, id -> factory.apply(new Item.Properties().setId(ResourceKey.create(Registries.ITEM, id))));
    }

    private static DeferredHolder<Item, Item> block(String name, Supplier<? extends Block> block) {
        return ITEMS.register(name, id -> new BlockItem(block.get(),
                new Item.Properties().setId(ResourceKey.create(Registries.ITEM, id)).useBlockDescriptionPrefix()));
    }
}
