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
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.particles.ParticleTypes;
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
    private static final String TARGET = "impendingdoom_kaiju_target";
    private static final String COOLDOWN = "impendingdoom_kaiju_cooldown";
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
        List<? extends Mob> kaiju = level.getEntities(EntityTypeTest.forClass(Mob.class), m -> m.entityTags().contains(TAG) && m.isAlive());
        if (level.getGameTime() % 20 == 0) {
            for (Mob k : kaiju) hunt(level, k, kaiju);
            updateBars(level, kaiju);
        }
        for (Mob k : kaiju) drive(level, k);
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
        mob.addTag(com.impendingdoom.util.MobSight.SEEKER);
        mob.setPersistenceRequired();
        EventUtil.addModifier(mob, Attributes.SCALE, "kaiju_scale", scale - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        EventUtil.addModifier(mob, Attributes.MAX_HEALTH, "kaiju_health", 19.0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        EventUtil.addModifier(mob, Attributes.ATTACK_DAMAGE, "kaiju_damage", 4.0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        EventUtil.addModifier(mob, Attributes.KNOCKBACK_RESISTANCE, "kaiju_knockback", 1.0, AttributeModifier.Operation.ADD_VALUE);
        EventUtil.addModifier(mob, Attributes.FOLLOW_RANGE, "kaiju_range", 256.0, AttributeModifier.Operation.ADD_VALUE);
        EventUtil.addModifier(mob, Attributes.STEP_HEIGHT, "kaiju_step", 8.0, AttributeModifier.Operation.ADD_VALUE);
        EventUtil.addModifier(mob, Attributes.ARMOR, "kaiju_armor", 10.0, AttributeModifier.Operation.ADD_VALUE);
        mob.setHealth(mob.getMaxHealth());
        stripAi(mob);
        mob.setCustomName(Component.translatable("entity.impendingdoom.kaiju", type.getDescription()).withStyle(ChatFormatting.DARK_RED));
        level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 16.0F, 0.3F);
    }

    /**
     * Kaiju always know where the players are, but fight each other first. They do not use the pathfinder (path
     * searches for a 20x bounding box are what lagged the server): they stride straight at their target and stomp it.
     */
    private static void hunt(ServerLevel level, Mob kaiju, List<? extends Mob> all) {
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
        kaiju.getPersistentData().putInt(TARGET, target == null ? -1 : target.getId());
    }

    /** Called every tick: walk, look and stomp. */
    private static void drive(ServerLevel level, Mob kaiju) {
        int id = kaiju.getPersistentData().getIntOr(TARGET, -1);
        Entity e = id < 0 ? null : level.getEntity(id);
        kaiju.getNavigation().stop();
        if (!(e instanceof LivingEntity target) || !target.isAlive()) return;
        kaiju.getLookControl().setLookAt(target, 30.0F, 30.0F);
        double reach = kaiju.getBbWidth() * 0.6 + target.getBbWidth() + 2.0;
        double dx = target.getX() - kaiju.getX();
        double dz = target.getZ() - kaiju.getZ();
        double flat = Math.sqrt(dx * dx + dz * dz);
        if (flat > reach * 0.8) {
            kaiju.getMoveControl().setWantedPosition(target.getX(), target.getY(), target.getZ(), 1.0);
        }
        int cooldown = kaiju.getPersistentData().getIntOr(COOLDOWN, 0);
        if (cooldown > 0) {
            kaiju.getPersistentData().putInt(COOLDOWN, cooldown - 1);
        } else if (flat <= reach && Math.abs(target.getY() - kaiju.getY()) < kaiju.getBbHeight()) {
            kaiju.getPersistentData().putInt(COOLDOWN, 30);
            kaiju.swingForAttack(net.minecraft.world.InteractionHand.MAIN_HAND);
            float damage = EventUtil.hasAttribute(kaiju, Attributes.ATTACK_DAMAGE) ? (float) kaiju.getAttributeValue(Attributes.ATTACK_DAMAGE) : 20.0F;
            target.hurtServer(level, kaiju.damageSources().mobAttack(kaiju), Math.max(damage, 15.0F));
            target.push(dx / Math.max(flat, 0.01) * 2.0, 0.8, dz / Math.max(flat, 0.01) * 2.0);
            target.needsSync = true;
            if (target instanceof ServerPlayer sp) sp.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(sp));
            level.sendParticles(ParticleTypes.EXPLOSION, target.getX(), target.getY(), target.getZ(), 3, 1.0, 0.5, 1.0, 0.0);
            level.playSound(null, target.blockPosition(), SoundEvents.RAVAGER_STUNNED, SoundSource.HOSTILE, 4.0F, 0.4F);
        }
    }

    /** Breaks the blocks in a slab just in front of the kaiju, in the direction it is moving. */
    private static void smash(ServerLevel level, Mob kaiju) {
        Vec3 dir = kaiju.getDeltaMovement().multiply(1, 0, 1);
        if (dir.lengthSqr() < 1.0E-4) return;
        dir = dir.normalize();
        double half = kaiju.getBbWidth() / 2.0;
        Vec3 front = kaiju.position().add(dir.scale(half + 1.0));
        Vec3 side = new Vec3(-dir.z, 0, dir.x);
        int broken = 0;
        for (double w = -half; w <= half; w += 1.0) {
            for (double y = 1; y < kaiju.getBbHeight(); y += 1.0) {
                BlockPos pos = BlockPos.containing(front.x + side.x * w, kaiju.getY() + y, front.z + side.z * w);
                BlockState state = level.getBlockState(pos);
                if (EventUtil.isBreakable(level, pos, state, 5.0F)) {
                    level.destroyBlock(pos, level.getRandom().nextInt(10) == 0, kaiju);
                    if (++broken > 64) return;
                }
            }
        }
    }

    /** Kaiju AI is replaced entirely (also after loading). */
    private static void stripAi(Mob kaiju) {
        kaiju.goalSelector.removeAllGoals(g -> true);
        kaiju.targetSelector.removeAllGoals(g -> true);
        kaiju.setTarget(null);
    }

    @SubscribeEvent
    public static void onJoin(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof Mob m && m.entityTags().contains(TAG)) stripAi(m);
    }

    /** Kaiju never acquire targets through vanilla AI (that would bring the pathfinder back). */
    @SubscribeEvent
    public static void onTarget(net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent event) {
        if (event.getEntity().entityTags().contains(TAG) && event.getNewAboutToBeSetTarget() != null) event.setCanceled(true);
    }

    private static void updateBars(ServerLevel level, List<? extends Mob> kaiju) {
        BARS.keySet().removeIf(id -> {
            boolean gone = kaiju.stream().noneMatch(k -> k.getUUID().equals(id));
            if (gone) BARS.get(id).removeAllPlayers();
            return gone;
        });
        for (Mob k : kaiju) {
            ServerBossEvent bar = BARS.computeIfAbsent(k.getUUID(), id -> new ServerBossEvent(java.util.UUID.randomUUID(), k.getDisplayName(), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS));
            bar.setProgress(k.getHealth() / k.getMaxHealth());
            for (ServerPlayer p : level.players()) {
                if (p.distanceToSqr(k) < 160 * 160) bar.addPlayer(p);
                else bar.removePlayer(p);
            }
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity().entityTags().contains(TAG) && event.getEntity().level() instanceof ServerLevel level) {
            level.playSound(null, event.getEntity().blockPosition(), SoundEvents.WITHER_DEATH, SoundSource.HOSTILE, 4.0F, 0.5F);
        }
    }

    @Override
    public void onEnd(ServerLevel level, CompoundTag data) {
        for (Mob k : level.getEntities(EntityTypeTest.forClass(Mob.class), m -> m.entityTags().contains(TAG))) k.discard();
        BARS.values().forEach(ServerBossEvent::removeAllPlayers);
        BARS.clear();
    }

    public static boolean active() {
        return EventScheduler.isActive(ID);
    }
}
