package com.impendingdoom.events.minor;

import com.impendingdoom.ImpendingDoom;
import com.impendingdoom.core.DoomEvent;
import com.impendingdoom.core.EventScheduler;
import com.impendingdoom.entity.ai.MoveToPosGoal;
import com.impendingdoom.util.EventUtil;
import com.impendingdoom.util.PlayerUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Incoming Raid: every player's respawn location is raided. Waves work like a vanilla raid (same wave table,
 * captain, boss bar, Hero of the Village on victory) but no village or villagers are needed: players are the target.
 */
@EventBusSubscriber(modid = ImpendingDoom.MODID)
public class IncomingRaidEvent extends DoomEvent {
    public static final String ID = "incoming_raid";
    private static final String RAIDER_KEY = "impendingdoom_raid";
    private static final int[] PILLAGERS = {4, 3, 3, 4, 4, 4, 2, 2};
    private static final int[] VINDICATORS = {0, 2, 0, 1, 4, 2, 5, 5};
    private static final int[] EVOKERS = {0, 0, 0, 0, 1, 1, 2, 2};
    private static final int[] WITCHES = {0, 0, 0, 3, 0, 0, 1, 1};
    private static final int[] RAVAGERS = {0, 0, 0, 1, 0, 1, 2, 2};
    private static final Map<Integer, ServerBossEvent> BARS = new HashMap<>();

    public IncomingRaidEvent() {
        super(ID, false, ChatFormatting.DARK_GREEN);
    }

    @Override
    public void onStart(ServerLevel level, CompoundTag data) {
        int waves = switch (level.getDifficulty()) {
            case EASY, PEACEFUL -> 3;
            case NORMAL -> 5;
            case HARD -> 7;
        };
        ListTag raids = new ListTag();
        List<BlockPos> centers = new ArrayList<>();
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            BlockPos spawn = PlayerUtil.overworldRespawnPos(player);
            if (centers.stream().anyMatch(c -> c.closerThan(spawn, 64))) continue;
            centers.add(spawn);
        }
        if (centers.isEmpty()) centers.add(PlayerUtil.worldSpawn(level));
        for (int i = 0; i < centers.size(); i++) {
            CompoundTag raid = new CompoundTag();
            raid.putInt("id", i);
            raid.putLong("center", centers.get(i).asLong());
            raid.putInt("wave", 0);
            raid.putInt("waves", waves);
            raid.putInt("cooldown", 300);
            raid.putInt("waveSize", 0);
            raid.putBoolean("done", false);
            raids.add(raid);
        }
        data.put("raids", raids);
        level.getServer().getPlayerList().broadcastSystemMessage(Component.translatable("message.impendingdoom.raid_incoming")
                .withStyle(ChatFormatting.RED), false);
    }

    @Override
    public void onTick(ServerLevel level, CompoundTag data) {
        ListTag raids = data.getListOrEmpty("raids");
        for (int i = 0; i < raids.size(); i++) {
            tickRaid(level, raids.getCompoundOrEmpty(i));
        }
    }

    private void tickRaid(ServerLevel level, CompoundTag raid) {
        if (raid.getBooleanOr("done", false)) return;
        int id = raid.getIntOr("id", 0);
        BlockPos center = BlockPos.of(raid.getLongOr("center", 0L));
        ServerBossEvent bar = BARS.computeIfAbsent(id, k -> new ServerBossEvent(Component.translatable("event.minecraft.raid"),
                BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10));
        List<ServerPlayer> near = level.getPlayers(p -> p.blockPosition().closerThan(center, 96));
        for (ServerPlayer p : level.players()) {
            if (near.contains(p)) bar.addPlayer(p);
            else bar.removePlayer(p);
        }
        if (near.isEmpty()) return; // the raid waits for the players

        String tag = RAIDER_KEY + "_" + id;
        List<Mob> raiders = level.getEntitiesOfClass(Mob.class, new AABB(center).inflate(160), m -> m.isAlive() && m.getTags().contains(tag));
        int wave = raid.getIntOr("wave", 0);
        int waves = raid.getIntOr("waves", 5);
        int waveSize = Math.max(1, raid.getIntOr("waveSize", 1));

        if (raiders.isEmpty()) {
            if (wave >= waves) {
                victory(level, raid, center, near, bar);
                return;
            }
            int cooldown = raid.getIntOr("cooldown", 300) - 1;
            raid.putInt("cooldown", cooldown);
            bar.setName(Component.translatable("event.minecraft.raid"));
            bar.setProgress(1.0F - Math.max(0, cooldown) / 300.0F);
            if (cooldown <= 0) {
                spawnWave(level, raid, center, wave, tag);
                raid.putInt("wave", wave + 1);
                raid.putInt("cooldown", 300);
            }
        } else {
            bar.setName(Component.translatable("event.minecraft.raid").append(" - ")
                    .append(Component.translatable("event.minecraft.raid.raiders_remaining", raiders.size())));
            bar.setProgress(Math.min(1.0F, raiders.size() / (float) waveSize));
            if (level.getGameTime() % 40 == 0) {
                for (Mob m : raiders) {
                    if (m.getTarget() == null) {
                        ServerPlayer target = EventUtil.nearestTarget(level, m, 48);
                        if (target != null) m.setTarget(target);
                    }
                    if (!m.blockPosition().closerThan(center, 140)) m.teleportTo(center.getX(), center.getY() + 1, center.getZ());
                }
            }
        }
    }

    private void spawnWave(ServerLevel level, CompoundTag raid, BlockPos center, int wave, String tag) {
        int w = Math.min(wave, PILLAGERS.length - 1);
        int bonus = level.getDifficulty() == Difficulty.HARD ? 1 : 0;
        BlockPos origin = EventUtil.surfacePos(level, center, 24, 40, level.getRandom());
        if (origin == null) origin = center;
        int count = 0;
        count += spawnN(level, EntityType.PILLAGER, PILLAGERS[w] + bonus, origin, center, tag, wave == 0);
        count += spawnN(level, EntityType.VINDICATOR, VINDICATORS[w] + bonus, origin, center, tag, false);
        count += spawnN(level, EntityType.EVOKER, EVOKERS[w], origin, center, tag, false);
        count += spawnN(level, EntityType.WITCH, WITCHES[w], origin, center, tag, false);
        for (int i = 0; i < RAVAGERS[w]; i++) {
            Mob ravager = raider(level, EntityType.RAVAGER, origin, center, tag);
            if (ravager == null) continue;
            count++;
            EntityType<? extends Mob> riderType = wave >= 5 ? EntityType.EVOKER : (wave >= 3 ? EntityType.VINDICATOR : EntityType.PILLAGER);
            Mob rider = raider(level, riderType, origin, center, tag);
            if (rider != null) {
                rider.startRiding(ravager, true);
                count++;
            }
        }
        raid.putInt("waveSize", count);
        for (ServerPlayer p : level.getPlayers(p -> p.blockPosition().closerThan(center, 96))) {
            p.playNotifySound(SoundEvents.RAID_HORN.value(), SoundSource.NEUTRAL, 64.0F, 1.0F);
        }
    }

    private int spawnN(ServerLevel level, EntityType<? extends Mob> type, int n, BlockPos origin, BlockPos center, String tag, boolean captain) {
        int spawned = 0;
        for (int i = 0; i < n; i++) {
            Mob mob = raider(level, type, origin, center, tag);
            if (mob == null) continue;
            spawned++;
            if (captain && i == 0) {
                mob.setItemSlot(EquipmentSlot.HEAD, Raid.getOminousBannerInstance(level.registryAccess().lookupOrThrow(Registries.BANNER_PATTERN)));
                mob.setDropChance(EquipmentSlot.HEAD, 2.0F);
            }
        }
        return spawned;
    }

    private Mob raider(ServerLevel level, EntityType<? extends Mob> type, BlockPos origin, BlockPos center, String tag) {
        BlockPos pos = EventUtil.surfacePos(level, origin, 0, 5, level.getRandom());
        if (pos == null) pos = origin;
        Mob mob = EventUtil.spawn(level, type, pos);
        if (mob == null) return null;
        mob.addTag(tag);
        mob.addTag(RAIDER_KEY);
        mob.getPersistentData().putLong(RAIDER_KEY, center.asLong());
        mob.setPersistenceRequired();
        addGoals(mob);
        return mob;
    }

    private static void addGoals(Mob mob) {
        if (mob instanceof PathfinderMob) {
            BlockPos center = BlockPos.of(mob.getPersistentData().getLongOr(RAIDER_KEY, 0L));
            mob.goalSelector.addGoal(4, new MoveToPosGoal(mob, () -> center, 1.0, 6));
        }
    }

    private void victory(ServerLevel level, CompoundTag raid, BlockPos center, List<ServerPlayer> near, ServerBossEvent bar) {
        raid.putBoolean("done", true);
        bar.setName(Component.translatable("event.minecraft.raid.victory.full"));
        bar.setProgress(1.0F);
        for (ServerPlayer p : near) {
            p.addEffect(new MobEffectInstance(MobEffects.HERO_OF_THE_VILLAGE, 48000, level.getDifficulty() == Difficulty.HARD ? 2 : 0));
            p.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0F, 1.0F);
            p.sendSystemMessage(Component.translatable("message.impendingdoom.raid_victory").withStyle(ChatFormatting.GREEN));
        }
        EventUtil.defer(() -> {
            bar.removeAllPlayers();
            BARS.remove(raid.getIntOr("id", 0));
        });
    }

    @Override
    public boolean isComplete(ServerLevel level, CompoundTag data) {
        ListTag raids = data.getListOrEmpty("raids");
        if (raids.isEmpty()) return true;
        for (int i = 0; i < raids.size(); i++) {
            if (!raids.getCompoundOrEmpty(i).getBooleanOr("done", false)) return false;
        }
        return true;
    }

    @Override
    public void onEnd(ServerLevel level, CompoundTag data) {
        BARS.values().forEach(ServerBossEvent::removeAllPlayers);
        BARS.clear();
    }

    /** Re-attach the march goal to raiders loaded from disk. */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !event.loadedFromDisk()) return;
        if (event.getEntity() instanceof Mob mob && mob.getTags().contains(RAIDER_KEY)) {
            if (!EventScheduler.isActive(ID)) {
                mob.removeTag(RAIDER_KEY);
                return;
            }
            addGoals(mob);
        }
    }
}
