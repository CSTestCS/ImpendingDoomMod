package com.impendingdoom.entity;

import com.impendingdoom.events.minor.TornadoEvent;
import com.impendingdoom.util.EventUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * A tornado that slowly tracks one player (so it can be led away from builds). It pulls entities in, lifts and
 * flings them, and tears up weak blocks. Visuals are server-side particles, so no renderer is needed.
 */
public class TornadoEntity extends Entity {
    private static final double SPEED = 0.11;
    private static final double RADIUS = 7.0;
    private static final double HEIGHT = 26.0;
    private UUID targetId;
    private int age;

    public TornadoEntity(EntityType<? extends TornadoEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public void setTarget(Player player) {
        this.targetId = player.getUUID();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        age = input.getIntOr("Age", 0);
        String id = input.getStringOr("Target", "");
        targetId = id.isEmpty() ? null : UUID.fromString(id);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.putInt("Age", age);
        if (targetId != null) output.putString("Target", targetId.toString());
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) return;
        if (!TornadoEvent.isTornadoActive()) {
            discard();
            return;
        }
        age++;
        move(level);
        swirl(level);
        if (age % 2 == 0) pull(level);
        if (age % 3 == 0 && EventUtil.canDestroyBlocks(level)) rip(level);
        if (age % 40 == 0) level.playSound(null, getX(), getY() + 4, getZ(), SoundEvents.ELYTRA_FLYING, SoundSource.WEATHER, 4.0F, 0.5F);
    }

    private void move(ServerLevel level) {
        ServerPlayer target = targetId == null ? null : (ServerPlayer) level.getPlayerByUUID(targetId);
        if (target == null || target.isSpectator() || !target.isAlive()) {
            target = EventUtil.nearestTarget(level, this, 512);
            if (target != null) targetId = target.getUUID();
        }
        Vec3 motion = Vec3.ZERO;
        if (target != null) {
            Vec3 dir = new Vec3(target.getX() - getX(), 0, target.getZ() - getZ());
            if (dir.lengthSqr() > 1) motion = dir.normalize().scale(SPEED);
        }
        // Wobble so it does not move in a perfect line.
        double wobble = Math.sin(age * 0.05) * 0.04;
        motion = motion.add(wobble, 0, Math.cos(age * 0.04) * 0.04);
        double nx = getX() + motion.x;
        double nz = getZ() + motion.z;
        BlockPos column = BlockPos.containing(nx, getY(), nz);
        double ny = getY();
        if (level.isLoaded(column)) {
            ny = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column).getY();
        }
        setPos(nx, Mth.lerp(0.2, getY(), ny), nz);
    }

    private void swirl(ServerLevel level) {
        RandomSource random = level.getRandom();
        for (int i = 0; i < 24; i++) {
            double h = random.nextDouble() * HEIGHT;
            double r = 0.8 + (h / HEIGHT) * RADIUS;
            double a = age * 0.45 + random.nextDouble() * Math.PI * 2;
            level.sendParticles(i % 3 == 0 ? ParticleTypes.LARGE_SMOKE : ParticleTypes.CLOUD,
                    getX() + Math.cos(a) * r, getY() + h, getZ() + Math.sin(a) * r, 1, 0, 0, 0, 0.02);
        }
        BlockState ground = level.getBlockState(blockPosition().below());
        if (!ground.isAir()) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), getX(), getY() + 0.5, getZ(), 6, 2.0, 0.3, 2.0, 0.2);
        }
    }

    private void pull(ServerLevel level) {
        AABB box = new AABB(getX() - RADIUS * 2, getY() - 2, getZ() - RADIUS * 2, getX() + RADIUS * 2, getY() + HEIGHT, getZ() + RADIUS * 2);
        for (Entity e : level.getEntities(this, box, e -> !(e instanceof TornadoEntity) && !e.isSpectator()
                && !(e instanceof Player p && (p.isCreative() || p.getAbilities().flying)))) {
            Vec3 toCenter = new Vec3(getX() - e.getX(), 0, getZ() - e.getZ());
            double dist = toCenter.length();
            if (dist > RADIUS * 2) continue;
            double strength = 1.0 - dist / (RADIUS * 2);
            Vec3 tangent = new Vec3(-toCenter.z, 0, toCenter.x).normalize();
            Vec3 push = toCenter.normalize().scale(0.08 * strength).add(tangent.scale(0.25 * strength));
            double lift = e.getY() - getY() > HEIGHT * 0.75 ? -0.05 : 0.12 * strength;
            if (e.getY() - getY() > HEIGHT * 0.7 && level.getRandom().nextInt(15) == 0) {
                // Fling out of the top
                push = toCenter.normalize().scale(-1.6);
                lift = 0.6;
            }
            e.setDeltaMovement(e.getDeltaMovement().scale(0.85).add(push.x, lift, push.z));
            e.needsSync = true;
            e.fallDistance = Math.min(e.fallDistance, 3.0F);
        }
    }

    private void rip(ServerLevel level) {
        RandomSource random = level.getRandom();
        long falling = level.getEntitiesOfClass(FallingBlockEntity.class, getBoundingBox().inflate(32)).size();
        if (falling > 40) return;
        for (int i = 0; i < 2; i++) {
            BlockPos pos = BlockPos.containing(getX() + (random.nextDouble() - 0.5) * RADIUS, getY() - 1 + random.nextInt(4),
                    getZ() + (random.nextDouble() - 0.5) * RADIUS);
            BlockState state = level.getBlockState(pos);
            if (!EventUtil.isBreakable(level, pos, state, 2.0F) || !level.getBlockState(pos.above()).isAir()) continue;
            FallingBlockEntity fb = FallingBlockEntity.fall(level, pos, state);
            fb.setDeltaMovement((random.nextDouble() - 0.5) * 0.6, 0.8 + random.nextDouble() * 0.6, (random.nextDouble() - 0.5) * 0.6);
            fb.needsSync = true;
        }
    }
}
