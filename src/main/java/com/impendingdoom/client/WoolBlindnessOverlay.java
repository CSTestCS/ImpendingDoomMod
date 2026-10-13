package com.impendingdoom.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;

/** Wool over your eyes: the world is completely black, only the interface stays visible. */
public final class WoolBlindnessOverlay {
    private WoolBlindnessOverlay() {}

    public static boolean active() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && !mc.player.isSpectator() && mc.player.getItemBySlot(EquipmentSlot.HEAD).is(ItemTags.WOOL);
    }

    public static void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        if (active()) graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), 0xFF000000);
    }
}
