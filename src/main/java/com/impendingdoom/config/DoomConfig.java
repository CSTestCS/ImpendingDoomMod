package com.impendingdoom.config;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

/**
 * Detailed tuning. The event cadence itself is controlled by gamerules (see ModGameRules).
 */
public final class DoomConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.ConfigValue<List<? extends String>> MUTATION_MOBS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> HORDE_MOBS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> KAIJU_MOBS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> HUNTER_BLACKLIST;
    public static final ModConfigSpec.IntValue HORDE_MIN_SIZE;
    public static final ModConfigSpec.IntValue HORDE_MAX_SIZE;
    public static final ModConfigSpec.DoubleValue KAIJU_SCALE;
    public static final ModConfigSpec.DoubleValue FLOOD_RISE_PER_DAY;
    public static final ModConfigSpec.DoubleValue LAVA_RISE_PER_DAY;
    public static final ModConfigSpec.BooleanValue EVENTS_DESTROY_BLOCKS;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.push("minor_events");
        MUTATION_MOBS = b.comment("Mobs that can mutate during Monster Mutations (valid mutations list)")
                .defineListAllowEmpty("mutationMobs", List.of(
                        "minecraft:zombie", "minecraft:husk", "minecraft:drowned", "minecraft:zombie_villager",
                        "minecraft:skeleton", "minecraft:stray", "minecraft:bogged", "minecraft:spider",
                        "minecraft:cave_spider", "minecraft:creeper", "minecraft:slime", "minecraft:witch",
                        "minecraft:enderman", "minecraft:silverfish", "minecraft:pillager", "minecraft:vindicator",
                        "minecraft:phantom"), () -> "minecraft:zombie", DoomConfig::isString);
        HORDE_MOBS = b.comment("Hostile mobs that a nightly horde may be made of")
                .defineListAllowEmpty("hordeMobs", List.of(
                        "minecraft:zombie", "minecraft:husk", "minecraft:skeleton", "minecraft:stray",
                        "minecraft:spider", "minecraft:creeper", "minecraft:zombie_villager", "minecraft:pillager",
                        "minecraft:vindicator", "minecraft:witch", "minecraft:bogged", "minecraft:cave_spider",
                        "minecraft:silverfish", "minecraft:slime"), () -> "minecraft:zombie", DoomConfig::isString);
        HORDE_MIN_SIZE = b.defineInRange("hordeMinSize", 12, 1, 200);
        HORDE_MAX_SIZE = b.defineInRange("hordeMaxSize", 20, 1, 200);
        HUNTER_BLACKLIST = b.comment("Mobs that can never be chosen as Hunters")
                .defineListAllowEmpty("hunterBlacklist", List.of(
                        "minecraft:ender_dragon", "minecraft:wither", "minecraft:giant", "minecraft:shulker",
                        "minecraft:elder_guardian", "minecraft:armor_stand"), () -> "minecraft:pig", DoomConfig::isString);
        b.pop();

        b.push("major_events");
        KAIJU_MOBS = b.comment("Monsters that can become a Kaiju")
                .defineListAllowEmpty("kaijuMobs", List.of(
                        "minecraft:zombie", "minecraft:husk", "minecraft:skeleton", "minecraft:spider",
                        "minecraft:creeper", "minecraft:enderman", "minecraft:witch", "minecraft:ravager",
                        "minecraft:vindicator", "minecraft:drowned", "minecraft:stray", "minecraft:wither_skeleton"),
                        () -> "minecraft:zombie", DoomConfig::isString);
        KAIJU_SCALE = b.comment("Kaiju size multiplier").defineInRange("kaijuScale", 20.0, 1.0, 32.0);
        FLOOD_RISE_PER_DAY = b.comment("Blocks the Flooding water rises per Minecraft day (rain doubles it)")
                .defineInRange("floodRisePerDay", 2.0, 0.1, 64.0);
        LAVA_RISE_PER_DAY = b.comment("Blocks the Volcanic Flooding lava rises per Minecraft day")
                .defineInRange("lavaRisePerDay", 3.0, 0.1, 64.0);
        b.pop();

        b.push("general");
        EVENTS_DESTROY_BLOCKS = b.comment("Allow events (tornadoes, meteors, kaiju, earthquakes, ...) to destroy blocks. The mobGriefing gamerule is also respected.")
                .define("eventsDestroyBlocks", true);
        b.pop();

        SPEC = b.build();
    }

    private DoomConfig() {}

    private static boolean isString(Object o) {
        return o instanceof String;
    }

    public static boolean listContains(ModConfigSpec.ConfigValue<List<? extends String>> list, EntityType<?> type) {
        Identifier key = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        return list.get().contains(key.toString());
    }

    public static EntityType<?> randomType(ModConfigSpec.ConfigValue<List<? extends String>> list, net.minecraft.util.RandomSource random) {
        List<? extends String> ids = list.get();
        if (ids.isEmpty()) return EntityTypes.ZOMBIE;
        for (int attempt = 0; attempt < 8; attempt++) {
            Identifier id = Identifier.tryParse(ids.get(random.nextInt(ids.size())));
            if (id == null) continue;
            var type = BuiltInRegistries.ENTITY_TYPE.getOptional(id);
            if (type.isPresent()) return type.get();
        }
        return EntityTypes.ZOMBIE;
    }
}
