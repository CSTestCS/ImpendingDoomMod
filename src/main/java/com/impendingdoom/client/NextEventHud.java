package com.impendingdoom.client;

import com.impendingdoom.core.DoomEvent;
import com.impendingdoom.core.EventRegistry;
import com.impendingdoom.config.DoomClientConfig;
import com.impendingdoom.core.EventScheduler;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * Shows the next doom event and the day it arrives. Players hide, show and move it with {@code /impendingdoom hud}.
 */
public final class NextEventHud {
    private NextEventHud() {}

    public static void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (!DoomClientConfig.HUD_VISIBLE.get()) return;

        DoomEvent next = EventRegistry.get(ClientDoomState.next);
        Component title;
        Component when = null;
        if (next == null) {
            title = Component.translatable("hud.impendingdoom.none").withStyle(ChatFormatting.GRAY);
        } else {
            title = Component.translatable("hud.impendingdoom.next", next.displayName())
                    .withStyle(next.isMajor() ? ChatFormatting.DARK_RED : ChatFormatting.GOLD);
            // Counts down between syncs (the server corrects it every second).
            long since = mc.level == null ? 0 : Math.max(0, mc.level.getGameTime() - ClientDoomState.syncedAt);
            long ticks = Math.max(0, ClientDoomState.ticksUntilNext - since);
            when = Component.translatable("hud.impendingdoom.when", EventScheduler.formatTicks(ticks))
                    .withStyle(ChatFormatting.GRAY);
        }

        Font font = mc.font;
        int width = Math.max(font.width(title), when == null ? 0 : font.width(when));
        int height = when == null ? font.lineHeight : font.lineHeight * 2 + 1;
        int sw = graphics.guiWidth();
        int sh = graphics.guiHeight();
        int ox = DoomClientConfig.HUD_X.get();
        int oy = DoomClientConfig.HUD_Y.get();
        int x;
        int y;
        switch (DoomClientConfig.HUD_ANCHOR.get()) {
            case "top_center" -> { x = (sw - width) / 2 + ox; y = oy; }
            case "top_right" -> { x = sw - width - ox; y = oy; }
            case "middle_left" -> { x = ox; y = (sh - height) / 2 + oy; }
            case "middle_right" -> { x = sw - width - ox; y = (sh - height) / 2 + oy; }
            case "bottom_left" -> { x = ox; y = sh - height - oy; }
            case "bottom_right" -> { x = sw - width - ox; y = sh - height - oy; }
            default -> { x = ox; y = oy; }
        }
        x = Math.max(0, Math.min(sw - width, x));
        y = Math.max(0, Math.min(sh - height, y));
        graphics.fill(x - 2, y - 2, x + width + 2, y + height + 1, 0x80000000);
        graphics.text(font, title, x, y, 0xFFFFFFFF, true);
        if (when != null) graphics.text(font, when, x, y + font.lineHeight + 1, 0xFFFFFFFF, true);
    }
}
