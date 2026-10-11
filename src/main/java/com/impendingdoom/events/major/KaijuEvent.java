package com.impendingdoom.events.major;

import com.impendingdoom.ImpendingDoom;
import com.impendingdoom.config.DoomConfig;
import com.impendingdoom.core.DayClock;
import com.impendingdoom.core.DoomEvent;
import com.impendingdoom.core.EventScheduler;
import com.impendingdoom.util.EventUtil;
import com.impendingdoom.util.KaijuScaling;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Kaiju: every night one monster spawns at 20x scale with greatly increased stats and full awareness of players.
 * It smashes through terrain toward them. Multiple Kaiju fight each other.
 */
@EventBusSubscriber(modid = ImpendingDoom.MODID)
public class KaijuEvent extends DoomEvent {
    public static final String ID = "kaiju";
    public static final String TAG = "impendingdoom.kaiju";
    private static final Map<UUID, ServerBossEvent> BARS = new HashMap<>();

    public KaijuEvent() {
        super(ID, true, ChatFormatting.DARK_RED);
    }

    @Override
    public void onTick(ServerLevel level, CompoundTag data) {
        long day = DayClock.day(level);
        if (DayClock.isNight(level) && data.getLongOr("lastNight", -1) != day) {
            data.putLong("lastNight", day);
            spawnKaiju(level);
        }
        List<Mob> kaiju = level.getEntities(EntityTypeTest.forClass(Mob.class), m -> m.getTags().contains(TAG) && m.isAlive());
        if (level.getGameTime() % 20 == 0) {
            for (Mob k : kaiju) hunt(level, k, kaiju);
            updateBars(level, kaiju);
        }
        if (level.getGameTime() % 4 == 0 && EventUtil.canDestroyBlocks(level)) {
            for (Mob k : kaiju) smash(level, k);
        }
    }

    private static void spawnKaiju(ServerLevel level) {
        ServerPlayer player = EventUtil.randomTarget(level);
        if (player == null) return;
        BlockPos pos = EventUtil.surfacePos(level, player.blockPosition(), 64, 96, level.getRandom());
        if (pos == null) return;
        EntityType<?> type = DoomConfig.randomType(DoomConfig.KAIJU_MOBS, level.getRandom());
        Entity e = EventUtil.spawn(level, type, pos);
        if (!(e instanceof Mob mob)) return;
        double scale = Math.min(DoomConfig.KAIJU_SCALE.get(), KaijuScaling.maxScale());
        mob.addTag(TAG);
        mob.setPersistenceRequired();
        EventUtil.addModifier(mob, Attributes.SCALE, "kaiju_scale", scale - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        EventUtil.addModifier(mob, Attributes.MAX_HEALTH, "kaiju_health", 19.0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        EventUtil.addModifier(mob, Attributes.ATTACK_DAMAGE, "kaiju_damage", 4.0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        EventUtil.addModifier(mob, Attributes.KNOCKBACK_RESISTANCE, "kaiju_knockback", 1.0, AttributeModifier.Operation.ADD_VALUE);
        EventUtil.addModifier(mob, Attributes.FOLLOW_RANGE, "kaiju_range", 256.0, AttributeModifier.Operation.ADD_VALUE);
        EventUtil.addModifier(mob, Attributes.STEP_HEIGHT, "kaiju_step", 8.0, AttributeModifier.Operation.ADD_VALUE);
        EventUtil.addModifier(mob, Attributes.ARMOR, "kaiju_armor", 10.0, AttributeModifier.Operation.ADD_VALUE);
        mob.setHealth(mob.getMaxHealth());
        mob.setCustomName(Component.translatable("entity.impendingdoom.kaiju", type.getDescription()).withStyle(ChatFormatting.DARK_RED));
        for (ServerPlayer p : level.players()) {
            p.playNotifySound(SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 4.0F, 0.3F);
            p.sendSystemMessage(Component.translatable("message.impendingdoom.kaiju").withStyle(ChatFormatting.DARK_RED));
        }
    }

    /** Kaiju always know where the players are, but fight each other first. */
    private static void hunt(ServerLevel level, Mob kaiju, List<Mob> all) {
        LivingEntity target = null;
        double best = Double.MAX_VALUE;
        for (Mob other : all) {
            if (other == kaiju) continue;
            double d = other.distanceToSqr(kaiju);
            if (d < best) {
                best = d;
                target = other;
            }
        }
        if (target == null || best > 192 * 192) {
            target = null;
            best = Double.MAX_VALUE;
            for (ServerPlayer p : EventUtil.targets(level)) {
                double d = p.distanceToSqr(kaiju);
                if (d < best) {
                    best = d;
                    target = p;
                }
            }
        }
        if (target == null) return;
        kaiju.setTarget(target);
        if (best > 40 * 40) {
            // Too far for the pathfinder: stride straight at it.
            kaiju.getMoveControl().setWantedPosition(target.getX(), target.getY(), target.getZ(), 1.0);
        }
    }

    private static void smash(ServerLevel level, Mob kaiju) {
        AABB box = kaiju.getBoundingBox().inflate(0.5, 0, 0.5).move(kaiju.getDeltaMovement().scale(4));
        int broken = 0;
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY + 1, box.minZ), BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
            if (broken > 48) break;
            BlockState state = level.getBlockState(pos);
            if (EventUtil.isBreakable(level, pos, state, 5.0F)) {
                level.destroyBlock(pos, level.getRandom().nextInt(10) == 0, kaiju);
                broken++;
            }
        }
    }

    private static void updateBars(ServerLevel level, List<Mob> kaiju) {
        BARS.keySet().removeIf(id -> {
            boolean gone = kaiju.stream().noneMatch(k -> k.getUUID().equals(id));
            if (gone) BARS.get(id).removeAllPlayers();
            return gone;
        });
        for (Mob k : kaiju) {
            ServerBossEvent bar = BARS.computeIfAbsent(k.getUUID(), id -> new ServerBossEvent(k.getDisplayName(), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS));
            bar.setProgress(k.getHealth() / k.getMaxHealth());
            for (ServerPlayer p : level.players()) {
                if (p.distanceToSqr(k) < 160 * 160) bar.addPlayer(p);
                else bar.removePlayer(p);
            }
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity().getTags().contains(TAG) && event.getEntity().level() instanceof ServerLevel level) {
            level.playSound(null, event.getEntity().blockPosition(), SoundEvents.WITHER_DEATH, SoundSource.HOSTILE, 4.0F, 0.5F);
        }
    }

    @Override
    public void onEnd(ServerLevel level, CompoundTag data) {
        for (Mob k : level.getEntities(EntityTypeTest.forClass(Mob.class), m -> m.getTags().contains(TAG))) k.discard();
        BARS.values().forEach(ServerBossEvent::removeAllPlayers);
        BARS.clear();
    }

    public static boolean active() {
        return EventScheduler.isActive(ID);
    }
}
