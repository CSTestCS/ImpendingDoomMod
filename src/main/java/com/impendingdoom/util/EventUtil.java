package com.impendingdoom.util;

import com.impendingdoom.config.DoomConfig;
import com.impendingdoom.registry.ModGameRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.monster.creaking.Creaking;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;

/** Small helpers shared by the events. */
public final class EventUtil {
    private static final Deque<Runnable> DEFERRED = new ArrayDeque<>();

    private EventUtil() {}

    /** Runs a task at the end of the current server tick (safe place to add entities from event callbacks). */
    public static void defer(Runnable task) {
        synchronized (DEFERRED) {
            DEFERRED.add(task);
        }
    }

    public static void runDeferred() {
        List<Runnable> tasks;
        synchronized (DEFERRED) {
            if (DEFERRED.isEmpty()) return;
            tasks = new ArrayList<>(DEFERRED);
            DEFERRED.clear();
        }
        for (Runnable task : tasks) task.run();
    }

    /** Survival / adventure players in the given level: the ones events target. */
    public static List<ServerPlayer> targets(ServerLevel level) {
        List<ServerPlayer> list = new ArrayList<>();
        for (ServerPlayer p : level.players()) {
            if (!p.isSpectator() && p.isAlive()) list.add(p);
        }
        return list;
    }

    public static ServerPlayer randomTarget(ServerLevel level) {
        List<ServerPlayer> list = targets(level);
        return list.isEmpty() ? null : list.get(level.getRandom().nextInt(list.size()));
    }

    public static ServerPlayer nearestTarget(ServerLevel level, Entity from, double maxDist) {
        ServerPlayer best = null;
        double bestDist = maxDist * maxDist;
        for (ServerPlayer p : targets(level)) {
            double d = p.distanceToSqr(from);
            if (d < bestDist) {
                bestDist = d;
                best = p;
            }
        }
        return best;
    }

    public static boolean canDestroyBlocks(ServerLevel level) {
        return DoomConfig.EVENTS_DESTROY_BLOCKS.get() && ModGameRules.mobGriefing(level);
    }

    /** A destroyable, non-container block an event may break. */
    public static boolean isBreakable(ServerLevel level, BlockPos pos, BlockState state, float maxHardness) {
        if (state.isAir()) return false;
        float hardness = state.getDestroySpeed(level, pos);
        return hardness >= 0 && hardness <= maxHardness && !state.hasBlockEntity();
    }

    /** Random surface position between min and max blocks away from center, or null if it is not loaded. */
    public static BlockPos surfacePos(ServerLevel level, BlockPos center, int min, int max, RandomSource random) {
        for (int attempt = 0; attempt < 10; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double dist = min + random.nextDouble() * Math.max(1, max - min);
            int x = center.getX() + Mth.floor(Math.cos(angle) * dist);
            int z = center.getZ() + Mth.floor(Math.sin(angle) * dist);
            BlockPos column = new BlockPos(x, center.getY(), z);
            if (!level.isLoaded(column)) continue;
            BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
            if (level.getBlockState(top.below()).getFluidState().isEmpty()) return top;
        }
        return null;
    }

    /**
     * Spawns an entity for an event. Mobs are finalized normally. Every creaking spawned by the mod gets a
     * {@link com.impendingdoom.entity.HeartWalker} companion.
     */
    public static <T extends Entity> T spawn(ServerLevel level, EntityType<T> type, BlockPos pos) {
        T entity = type.create(level, EntitySpawnReason.EVENT);
        if (entity == null) return null;
        entity.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        entity.setYRot(level.getRandom().nextFloat() * 360.0F);
        if (entity instanceof Mob mob) {
            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.EVENT, null);
        }
        level.addFreshEntityWithPassengers(entity);
        if (entity instanceof Creaking creaking) CreakingCompanions.attach(level, creaking);
        return entity;
    }

    public static void addModifier(LivingEntity entity, Holder<Attribute> attribute, String name, double amount, AttributeModifier.Operation op) {
        AttributeInstance inst = entity.getAttribute(attribute);
        if (inst == null) return;
        Identifier id = com.impendingdoom.ImpendingDoom.id(name);
        inst.removeModifier(id);
        inst.addPermanentModifier(new AttributeModifier(id, amount, op));
    }

    public static double modifierAmount(LivingEntity entity, Holder<Attribute> attribute, String name) {
        AttributeInstance inst = entity.getAttribute(attribute);
        if (inst == null) return 0;
        AttributeModifier mod = inst.getModifier(com.impendingdoom.ImpendingDoom.id(name));
        return mod == null ? 0 : mod.amount();
    }

    public static boolean hasAttribute(LivingEntity entity, Holder<Attribute> attribute) {
        return entity.getAttribute(attribute) != null;
    }

    /** Plays a sound that only the given player hears. */
    public static void privateSound(ServerPlayer player, SoundEvent sound, double x, double y, double z, float volume, float pitch) {
        Holder<SoundEvent> holder = BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound);
        player.connection.send(new ClientboundSoundPacket(holder, SoundSource.HOSTILE, x, y, z, volume, pitch, player.getRandom().nextLong()));
    }

    /** Sets the weather with the vanilla /weather command. Kind is clear, rain or thunder. */
    public static void setWeather(ServerLevel level, String kind, int ticks) {
        var server = level.getServer();
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(),
                "weather " + kind + " " + Math.max(1, ticks) + "t");
    }

    /** Drops an item at an entity's feet. */
    public static void dropNear(net.minecraft.world.entity.LivingEntity entity, net.minecraft.world.item.ItemStack stack) {
        net.minecraft.world.entity.item.ItemEntity item = entity.createItemStackToDrop(stack, true, false);
        if (item != null) entity.level().addFreshEntity(item);
    }

    /** Plays a sound at the player that only they hear. */
    public static void notifySound(ServerPlayer player, SoundEvent sound, SoundSource source, float volume, float pitch) {
        Holder<SoundEvent> holder = BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound);
        player.connection.send(new ClientboundSoundPacket(holder, source, player.getX(), player.getY(), player.getZ(), volume, pitch, player.getRandom().nextLong()));
    }

    public static UUID uuid(CompoundTag tag, String key) {
        String s = tag.getStringOr(key, "");
        if (s.isEmpty()) return null;
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static boolean hasTag(Entity entity, String tag) {
        return entity.entityTags().contains(tag);
    }
}
