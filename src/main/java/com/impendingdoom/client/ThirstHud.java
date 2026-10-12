package com.impendingdoom.client;

import com.impendingdoom.events.major.SummerEvent;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;

/** Ten water droplets drawn above the hunger bar during Summer. */
public final class ThirstHud {
    private static final int FULL = 0xFF3F76E4;
    private static final int HALF = 0xFF2A4E96;
    private static final int EMPTY = 0xFF1B1B2A;
    private static final int OUTLINE = 0xFF0B0B12;

    private ThirstHud() {}

    public static void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || !ClientModEvents.summer()) return;
        if (player.isCreative() || player.isSpectator()) return;
        int thirst = ClientDoomState.thirst;
        int right = graphics.guiWidth() / 2 + 91;
        int top = graphics.guiHeight() - 49;
        if (player.getAirSupply() < player.getMaxAirSupply() || player.isUnderWater()) top -= 10;
        for (int i = 0; i < SummerEvent.MAX_THIRST / 2; i++) {
            int x = right - i * 8 - 9;
            int value = thirst - i * 2;
            int color = value >= 2 ? FULL : value == 1 ? HALF : EMPTY;
            drawDrop(graphics, x, top, color);
        }
    }

    private static void drawDrop(GuiGraphicsExtractor g, int x, int y, int color) {
        // A small droplet: narrow tip widening into a round base.
        g.fill(x + 3, y, x + 5, y + 2, OUTLINE);
        g.fill(x + 2, y + 2, x + 6, y + 4, OUTLINE);
        g.fill(x + 1, y + 4, x + 7, y + 8, OUTLINE);
        g.fill(x + 2, y + 8, x + 6, y + 9, OUTLINE);
        g.fill(x + 3, y + 1, x + 5, y + 2, color);
        g.fill(x + 3, y + 2, x + 5, y + 4, color);
        g.fill(x + 2, y + 4, x + 6, y + 8, color);
        g.fill(x + 3, y + 5, x + 4, y + 6, 0xFFB4D2FF);
    }
}
