package com.impendingdoom.client;

import com.impendingdoom.ImpendingDoom;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.item.ItemStack;

/** Extra render state data attached to every living entity render state. */
public final class ArmorRenderData {
    /** Armor pieces worn (head, chest, legs, feet) by a mob whose model does not render armor itself. */
    public static final ContextKey<ItemStack[]> ARMOR = new ContextKey<>(ImpendingDoom.id("refit_armor"));
    /** Harness worn by a regular ghast (purely visual, Nether Invasion). */
    public static final ContextKey<ItemStack> HARNESS = new ContextKey<>(ImpendingDoom.id("ghast_harness"));
    /** True while the entity should shake (bloodlust haze). */
    public static final ContextKey<Boolean> SHAKING = new ContextKey<>(ImpendingDoom.id("shaking"));

    private ArmorRenderData() {}
}
