package com.impendingdoom.config;

import com.impendingdoom.core.NextEventHudSettings;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.Arrays;

/** Client settings, saved in config/impendingdoom-client.toml. Changed in game with /impendingdoom hud. */
public final class DoomClientConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue HUD_VISIBLE;
    public static final ModConfigSpec.ConfigValue<String> HUD_ANCHOR;
    public static final ModConfigSpec.IntValue HUD_X;
    public static final ModConfigSpec.IntValue HUD_Y;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("next_event_hud");
        HUD_VISIBLE = b.comment("Show the next doom event and the time until it arrives").define("visible", true);
        HUD_ANCHOR = b.comment("Corner or edge the HUD is placed against: " + String.join(", ", NextEventHudSettings.ANCHORS))
                .define("anchor", "top_left", o -> o instanceof String s && Arrays.asList(NextEventHudSettings.ANCHORS).contains(s));
        HUD_X = b.comment("Horizontal offset from the anchor, in GUI pixels").defineInRange("x", 4, -10000, 10000);
        HUD_Y = b.comment("Vertical offset from the anchor, in GUI pixels").defineInRange("y", 4, -10000, 10000);
        b.pop();
        SPEC = b.build();
    }

    private DoomClientConfig() {}
}
