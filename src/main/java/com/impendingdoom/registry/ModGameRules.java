package com.impendingdoom.registry;

import com.impendingdoom.ImpendingDoom;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.serialization.Codec;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;
import net.minecraft.world.level.gamerules.GameRuleType;
import net.minecraft.world.level.gamerules.GameRuleTypeVisitor;
import net.minecraft.world.level.gamerules.GameRules;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Gamerules controlling the event cadence. Adjust in game with
 * <pre>
 * /gamerule impendingdoom:days_between_events 7
 * /gamerule impendingdoom:minor_events_before_major 3
 * </pre>
 * All gamerule access for the mod goes through this class so that it is the single place to update
 * if the gamerule API changes between Minecraft versions.
 */
public final class ModGameRules {
    public static final DeferredRegister<GameRule<?>> GAME_RULES = DeferredRegister.create(Registries.GAME_RULE, ImpendingDoom.MODID);

    public static final DeferredHolder<GameRule<?>, GameRule<Integer>> DAYS_BETWEEN_EVENTS =
            GAME_RULES.register("days_between_events", () -> intRule(7, 1, 100000));
    public static final DeferredHolder<GameRule<?>, GameRule<Integer>> MINOR_EVENTS_BEFORE_MAJOR =
            GAME_RULES.register("minor_events_before_major", () -> intRule(3, 0, 1000));

    private ModGameRules() {}

    private static GameRule<Integer> intRule(int defaultValue, int min, int max) {
        return new GameRule<>(
                GameRuleCategory.MISC,
                GameRuleType.INT,
                IntegerArgumentType.integer(min, max),
                GameRuleTypeVisitor::visitInteger,
                Codec.intRange(min, max),
                value -> value,
                defaultValue,
                FeatureFlagSet.of());
    }

    public static int daysBetweenEvents(ServerLevel level) {
        return Math.max(1, level.getGameRules().get(DAYS_BETWEEN_EVENTS.get()));
    }

    public static int minorEventsBeforeMajor(ServerLevel level) {
        return Math.max(0, level.getGameRules().get(MINOR_EVENTS_BEFORE_MAJOR.get()));
    }

    public static void setDaysBetweenEvents(ServerLevel level, int value) {
        level.getGameRules().set(DAYS_BETWEEN_EVENTS.get(), value, level.getServer());
    }

    public static void setMinorEventsBeforeMajor(ServerLevel level, int value) {
        level.getGameRules().set(MINOR_EVENTS_BEFORE_MAJOR.get(), value, level.getServer());
    }

    public static boolean mobGriefing(ServerLevel level) {
        return level.getGameRules().get(GameRules.MOB_GRIEFING);
    }
}
