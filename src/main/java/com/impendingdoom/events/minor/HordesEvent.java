package com.impendingdoom.events.minor;

import com.impendingdoom.ImpendingDoom;
import com.impendingdoom.config.DoomConfig;
import com.impendingdoom.core.DayClock;
import com.impendingdoom.core.DoomEvent;
import com.impendingdoom.core.EventScheduler;
import com.impendingdoom.util.EventUtil;
import com.impendingdoom.util.GroupWander;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

import java.util.UUID;

/**
 * Hordes: every night large groups of one random hostile mob spawn around each player and roam like patrols. A horde
 * attacks players it comes across, and while the event lasts its members call reinforcements like zombies do. Horde
 * members are the only mobs from this mod that can call for backup.
 */
@EventBusSubscriber(modid = ImpendingDoom.MODID)
public class HordesEvent extends DoomEvent {
    public static final String ID = "hordes";
    private static final String HORDE = "impendingdoom_horde";
    private static final String TAG = "impendingdoom.horde";

    public HordesEvent() {
        super(ID, false, ChatFormatting.DARK_GREEN);
    }

    @Override
    public void onTick(ServerLevel level, CompoundTag data) {
        // Three waves every night (start of night, middle, late night); each wave brings one or two hordes.
        long day = DayClock.day(level);
        long time = DayClock.timeOfDay(level);
        int wave = time >= 13000 && time < 15500 ? 1 : time >= 15500 && time < 18000 ? 2 : time >= 18000 && time < 21000 ? 3 : 0;
        long key = day * 10 + wave;
        if (wave > 0 && data.getLongOr("lastWave", -1) != key) {
            data.putLong("lastWave", key);
            for (ServerPlayer player : EventUtil.targets(level)) {
                int hordes = 1 + level.getRandom().nextInt(2);
                for (int i = 0; i < hordes; i++) spawnHorde(level, player);
                if (EventScheduler.isActive(com.impendingdoom.events.major.FallEvent.ID)) {
                    com.impendingdoom.events.major.FallEvent.spawnTinySpiderHorde(level, player);
                }
            }
        }
    }

    private static void spawnHorde(ServerLevel level, ServerPlayer player) {
        EntityType<?> type = DoomConfig.randomType(DoomConfig.HORDE_MOBS, level.getRandom());
        int min = DoomConfig.HORDE_MIN_SIZE.get();
        int size = min + level.getRandom().nextInt(Math.max(1, DoomConfig.HORDE_MAX_SIZE.get() - min + 1));
        BlockPos origin = EventUtil.surfacePos(level, player.blockPosition(), 28, 48, level.getRandom());
        if (origin == null) return;
        String hordeId = UUID.randomUUID().toString();
        for (int i = 0; i < size; i++) {
            BlockPos pos = EventUtil.surfacePos(level, origin, 0, 5, level.getRandom());
            joinHorde(EventUtil.spawn(level, type, pos == null ? origin : pos), hordeId);
        }
    }

    private static void joinHorde(Entity entity, String hordeId) {
        if (!(entity instanceof Mob mob)) return;
        mob.addTag(TAG);
        mob.getPersistentData().putString(HORDE, hordeId);
        mob.setPersistenceRequired();
        EventUtil.allowReinforcements(mob);
        GroupWander.join(mob, hordeId);
    }

    /** Outside the event, leftover horde members are ordinary mobs: no more backup. */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.loadedFromDisk() && event.getEntity() instanceof Mob m
                && m.entityTags().contains(TAG) && !EventScheduler.isActive(ID)) {
            EventUtil.noReinforcements(m);
        }
    }

    @Override
    public void onEnd(ServerLevel level, CompoundTag data) {
        for (Mob m : level.getEntities(EntityTypeTest.forClass(Mob.class), m -> m.entityTags().contains(TAG))) EventUtil.noReinforcements(m);
    }

    /** Zombie-style reinforcements: a hurt member may call another of its kind. */
    @SubscribeEvent
    public static void onHurt(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof Mob mob) || !(mob.level() instanceof ServerLevel level) || !mob.entityTags().contains(TAG)) return;
        if (!EventScheduler.isActive(ID) || !(event.getSource().getEntity() instanceof Player) || level.getRandom().nextFloat() > 0.15F) return;
        String hordeId = mob.getPersistentData().getStringOr(HORDE, "");
        long size = level.getEntities(EntityTypeTest.forClass(Mob.class), m -> hordeId.equals(m.getPersistentData().getStringOr(HORDE, ""))).size();
        if (size >= 40) return;
        BlockPos pos = EventUtil.surfacePos(level, mob.blockPosition(), 7, 16, level.getRandom());
        if (pos == null) return;
        EventUtil.defer(() -> {
            Entity e = EventUtil.spawn(level, mob.getType(), pos);
            joinHorde(e, hordeId);
            if (e instanceof Mob m && event.getSource().getEntity() instanceof Player p) m.setTarget(p);
        });
    }
}
