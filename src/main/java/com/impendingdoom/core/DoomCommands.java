package com.impendingdoom.core;

import com.impendingdoom.ImpendingDoom;
import com.impendingdoom.registry.ModGameRules;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * /impendingdoom status                   - show current/next events
 * /impendingdoom list                     - list all events
 * /impendingdoom trigger [event]          - start the next (or given) event right now
 * /impendingdoom next &lt;event&gt;       - choose the next event (re-issues omens); "foretell" is an alias
 * /impendingdoom disable|enable &lt;event&gt; - stop an event from ever being chosen (or allow it again)
 * /impendingdoom disabled                 - list disabled events
 * /impendingdoom end minor|major          - end the running event
 * /impendingdoom days &lt;n&gt;           - same as /gamerule impendingdoom:days_between_events
 * /impendingdoom minors &lt;n&gt;         - same as /gamerule impendingdoom:minor_events_before_major
 */
@EventBusSubscriber(modid = ImpendingDoom.MODID)
public final class DoomCommands {
    private static final SimpleCommandExceptionType NOT_READY = new SimpleCommandExceptionType(Component.literal("Impending Doom is not running"));
    private static final SimpleCommandExceptionType UNKNOWN = new SimpleCommandExceptionType(Component.literal("Unknown event"));

    private DoomCommands() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("impendingdoom")
                .then(Commands.literal("status").executes(DoomCommands::status))
                .then(Commands.literal("list").executes(DoomCommands::list))
                .then(Commands.literal("trigger").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(ctx -> trigger(ctx, null))
                        .then(Commands.argument("event", StringArgumentType.word())
                                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(EventRegistry.ids(), b))
                                .executes(ctx -> trigger(ctx, StringArgumentType.getString(ctx, "event")))))
                .then(Commands.literal("next").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("event", StringArgumentType.word())
                                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(EventRegistry.ids(), b))
                                .executes(DoomCommands::foretell)))
                .then(Commands.literal("disable").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("event", StringArgumentType.word())
                                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(EventRegistry.ids(), b))
                                .executes(ctx -> setEnabled(ctx, false))))
                .then(Commands.literal("enable").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("event", StringArgumentType.word())
                                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(EventRegistry.ids(), b))
                                .executes(ctx -> setEnabled(ctx, true))))
                .then(Commands.literal("disabled").executes(DoomCommands::listDisabled))
                .then(Commands.literal("foretell").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("event", StringArgumentType.word())
                                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(EventRegistry.ids(), b))
                                .executes(DoomCommands::foretell)))
                .then(Commands.literal("end").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.literal("minor").executes(ctx -> end(ctx, false)))
                        .then(Commands.literal("major").executes(ctx -> end(ctx, true))))
                .then(Commands.literal("days").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("value", IntegerArgumentType.integer(1)).executes(ctx -> {
                            ModGameRules.setDaysBetweenEvents(ctx.getSource().getServer().overworld(), IntegerArgumentType.getInteger(ctx, "value"));
                            return status(ctx);
                        })))
                .then(Commands.literal("minors").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("value", IntegerArgumentType.integer(0)).executes(ctx -> {
                            ModGameRules.setMinorEventsBeforeMajor(ctx.getSource().getServer().overworld(), IntegerArgumentType.getInteger(ctx, "value"));
                            return status(ctx);
                        }))));
    }

    private static DoomSavedData data() throws CommandSyntaxException {
        DoomSavedData d = EventScheduler.data();
        if (d == null) throw NOT_READY.create();
        return d;
    }

    private static Component name(String id) {
        DoomEvent e = EventRegistry.get(id);
        return e == null ? Component.literal("none").withStyle(ChatFormatting.GRAY) : e.displayName();
    }

    private static int status(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        DoomSavedData d = data();
        ServerLevel level = ctx.getSource().getServer().overworld();
        long day = DayClock.day(level);
        long next = EventScheduler.nextEventDay(level, d);
        ctx.getSource().sendSuccess(() -> Component.literal("Day " + (day + 1) + " | events every "
                + ModGameRules.daysBetweenEvents(level) + " days, " + ModGameRules.minorEventsBeforeMajor(level)
                + " minor events before a major event"), false);
        ctx.getSource().sendSuccess(() -> Component.literal("Minor event: ").append(name(d.currentMinor)), false);
        ctx.getSource().sendSuccess(() -> Component.literal("Major event: ").append(name(d.currentMajor)), false);
        ctx.getSource().sendSuccess(() -> Component.literal("Season: ").append(name(d.currentSeason)), false);
        ctx.getSource().sendSuccess(() -> Component.literal("Next: ").append(name(d.nextEvent))
                .append(Component.literal(" on day " + (next + 1) + " (in " + Math.max(0, next - day) + " days)")), false);
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        for (DoomEvent e : EventRegistry.all()) {
            ctx.getSource().sendSuccess(() -> Component.literal(e.isMajor() ? "[Major] " : "[Minor] ")
                    .append(e.displayName()).append(Component.literal(" (" + e.id() + ")").withStyle(ChatFormatting.DARK_GRAY)), false);
        }
        return EventRegistry.all().size();
    }

    private static int trigger(CommandContext<CommandSourceStack> ctx, String id) throws CommandSyntaxException {
        DoomSavedData d = data();
        DoomEvent forced = null;
        if (id != null) {
            forced = EventRegistry.get(id);
            if (forced == null) throw UNKNOWN.create();
        }
        ServerLevel level = ctx.getSource().getServer().overworld();
        EventScheduler.triggerNext(level, d, DayClock.day(level), forced);
        return 1;
    }

    private static int foretell(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        DoomSavedData d = data();
        DoomEvent e = EventRegistry.get(StringArgumentType.getString(ctx, "event"));
        if (e == null) throw UNKNOWN.create();
        EventScheduler.setNext(ctx.getSource().getServer().overworld(), d, e);
        // The chosen event starts next regardless of whether a minor or major event was due.
        d.nextForced = true;
        d.disabled.remove(e.id());
        d.setDirty();
        ctx.getSource().sendSuccess(() -> Component.literal("Next event: ").append(e.displayName()), true);
        return 1;
    }

    /** Disabled events are never chosen. Disabling a running event ends it; disabling the foretold one re-rolls it. */
    private static int setEnabled(CommandContext<CommandSourceStack> ctx, boolean enabled) throws CommandSyntaxException {
        DoomSavedData d = data();
        DoomEvent e = EventRegistry.get(StringArgumentType.getString(ctx, "event"));
        if (e == null) throw UNKNOWN.create();
        ServerLevel level = ctx.getSource().getServer().overworld();
        if (enabled) {
            d.disabled.remove(e.id());
        } else {
            d.disabled.add(e.id());
            if (e.id().equals(d.currentMinor)) EventScheduler.endMinor(level, d);
            if (e.id().equals(d.currentSeason)) EventScheduler.endSeason(level, d);
            if (e.id().equals(d.currentMajor)) EventScheduler.endMajor(level, d);
            if (e.id().equals(d.nextEvent)) EventScheduler.chooseNext(level, d);
            EventScheduler.syncAll(level, d);
        }
        d.setDirty();
        ctx.getSource().sendSuccess(() -> e.displayName().append(Component.literal(enabled ? " enabled" : " disabled")), true);
        return 1;
    }

    private static int listDisabled(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        DoomSavedData d = data();
        if (d.disabled.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("No events are disabled"), false);
        }
        for (String id : d.disabled) ctx.getSource().sendSuccess(() -> name(id), false);
        return d.disabled.size();
    }

    private static int end(CommandContext<CommandSourceStack> ctx, boolean major) throws CommandSyntaxException {
        DoomSavedData d = data();
        ServerLevel level = ctx.getSource().getServer().overworld();
        if (major) {
            EventScheduler.endMajor(level, d);
            EventScheduler.syncAll(level, d);
        } else {
            EventScheduler.endMinor(level, d);
        }
        return 1;
    }
}
