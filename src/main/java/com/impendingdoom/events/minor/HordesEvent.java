package com.impendingdoom.events.minor;

import com.impendingdoom.ImpendingDoom;
import com.impendingdoom.config.DoomConfig;
import com.impendingdoom.core.DayClock;
import com.impendingdoom.core.DoomEvent;
import com.impendingdoom.core.EventScheduler;
import com.impendingdoom.entity.ai.MoveToPosGoal;
import com.impendingdoom.util.EventUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
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
 * Hordes: every night a large group of one random hostile mob spawns near each player and migrates toward them.
 * While any member lives, the horde calls reinforcements like zombies do.
 */
@EventBusSubscriber(modid = ImpendingDoom.MODID)
public class HordesEvent extends DoomEvent {
    public static final String ID = "hordes";
    private static final String HORDE = "impendingdoom_horde";
    private static final String PREY = "impendingdoom_horde_prey";
    private static final String TAG = "impendingdoom.horde";

    public HordesEvent() {
        super(ID, false, ChatFormatting.DARK_GREEN);
    }

    @Override
    public void onTick(ServerLevel level, CompoundTag data) {
        long day = DayClock.day(level);
        if (DayClock.isNight(level) && data.getLongOr("lastNight", -1) != day) {
            data.putLong("lastNight", day);
            for (ServerPlayer player : EventUtil.targets(level)) spawnHorde(level, player);
        }
        if (level.getGameTime() % 100 == 0) {
            for (Mob m : level.getEntities(EntityTypeTest.forClass(Mob.class), m -> m.entityTags().contains(TAG) && m.getTarget() == null)) {
                UUID prey = EventUtil.uuid(m.getPersistentData(), PREY);
                Player p = prey == null ? null : level.getPlayerByUUID(prey);
                if (p != null && !p.isCreative() && m.distanceToSqr(p) < 24 * 24) m.setTarget(p);
            }
        }
    }

    private static void spawnHorde(ServerLevel level, ServerPlayer player) {
        EntityType<?> type = DoomConfig.randomType(DoomConfig.HORDE_MOBS, level.getRandom());
        int min = DoomConfig.HORDE_MIN_SIZE.get();
        int size = min + level.getRandom().nextInt(Math.max(1, DoomConfig.HORDE_MAX_SIZE.get() - min + 1));
        BlockPos origin = EventUtil.surfacePos(level, player.blockPosition(), 40, 56, level.getRandom());
        if (origin == null) return;
        String hordeId = UUID.randomUUID().toString();
        for (int i = 0; i < size; i++) {
            BlockPos pos = EventUtil.surfacePos(level, origin, 0, 5, level.getRandom());
            joinHorde(level, EventUtil.spawn(level, type, pos == null ? origin : pos), hordeId, player.getUUID());
        }
        player.sendSystemMessage(Component.translatable("message.impendingdoom.horde", type.getDescription()).withStyle(ChatFormatting.RED), true);
    }

    private static void joinHorde(ServerLevel level, Entity entity, String hordeId, UUID prey) {
        if (!(entity instanceof Mob mob)) return;
        mob.addTag(TAG);
        mob.getPersistentData().putString(HORDE, hordeId);
        mob.getPersistentData().putString(PREY, prey.toString());
        mob.setPersistenceRequired();
        addMigration(mob);
    }

    private static void addMigration(Mob mob) {
        mob.goalSelector.addGoal(4, new MoveToPosGoal(mob, () -> {
            UUID prey = EventUtil.uuid(mob.getPersistentData(), PREY);
            Player p = prey == null ? null : mob.level().getPlayerByUUID(prey);
            return p == null ? null : p.blockPosition();
        }, 1.0, 8));
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.loadedFromDisk() && event.getEntity() instanceof Mob m && m.entityTags().contains(TAG)) addMigration(m);
    }

    /** Zombie-style reinforcements: a hurt member may call another of its kind. */
    @SubscribeEvent
    public static void onHurt(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof Mob mob) || !(mob.level() instanceof ServerLevel level) || !mob.entityTags().contains(TAG)) return;
        if (!EventScheduler.isActive(ID) || !(event.getSource().getEntity() instanceof Player) || level.getRandom().nextFloat() > 0.15F) return;
        String hordeId = mob.getPersistentData().getStringOr(HORDE, "");
        long size = level.getEntities(EntityTypeTest.forClass(Mob.class), m -> hordeId.equals(m.getPersistentData().getStringOr(HORDE, ""))).size();
        if (size >= 40) return;
        UUID prey = EventUtil.uuid(mob.getPersistentData(), PREY);
        BlockPos pos = EventUtil.surfacePos(level, mob.blockPosition(), 7, 16, level.getRandom());
        if (pos == null || prey == null) return;
        EventUtil.defer(() -> {
            Entity e = EventUtil.spawn(level, mob.getType(), pos);
            joinHorde(level, e, hordeId, prey);
            if (e instanceof Mob m && event.getSource().getEntity() instanceof Player p) m.setTarget(p);
        });
    }
}
