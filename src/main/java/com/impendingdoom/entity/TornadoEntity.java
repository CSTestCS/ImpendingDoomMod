package com.impendingdoom.entity;

import com.impendingdoom.events.minor.TornadoEvent;
import com.impendingdoom.util.EventUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
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
import net.minecraft.world.entity.LivingEntity;
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
 * A large tornado that slowly tracks one player (so it can be led away from builds). It sucks in entities and blocks,
 * lifts them up the funnel while spinning them around, and throws them out of the top. The funnel itself is drawn
 * client side from wind gusts, campfire smoke, clouds and explosions.
 */
public class TornadoEntity extends Entity {
    private static final double SPEED = 0.12;
    private static final double BASE_RADIUS = 3.0;
    private static final double TOP_RADIUS = 18.0;
    private static final double HEIGHT = 50.0;
    private static final String FLUNG = "impendingdoom_flung";
    private UUID targetId;
    private int age;

    public TornadoEntity(EntityType<? extends TornadoEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public void setTarget(Player player) {
        this.targetId = player.getUUID();
    }

    /** Funnel radius at a height above the base. */
    private static double radiusAt(double h) {
        double t = Mth.clamp(h / HEIGHT, 0.0, 1.0);
        return BASE_RADIUS + (TOP_RADIUS - BASE_RADIUS) * t * t;
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
    public boolean shouldRenderAtSqrDistance(double distance) {
        return true;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            clientParticles();
            return;
        }
        ServerLevel level = (ServerLevel) level();
        if (!TornadoEvent.isTornadoActive()) {
            discard();
            return;
        }
        age++;
        move(level);
        pull(level);
        if (EventUtil.canDestroyBlocks(level)) rip(level);
        if (age % 30 == 0) {
            level.playSound(null, getX(), getY() + 6, getZ(), SoundEvents.ELYTRA_FLYING, SoundSource.WEATHER, 8.0F, 0.4F);
            level.playSound(null, getX(), getY() + 2, getZ(), SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), SoundSource.WEATHER, 4.0F, 0.5F);
        }
    }

    // ---------------------------------------------------------------- visuals (client)

    private void clientParticles() {
        RandomSource r = random;
        Level level = level();
        for (int i = 0; i < 110; i++) {
            double h = Math.pow(r.nextDouble(), 0.85) * HEIGHT;
            double radius = radiusAt(h) * (0.75 + r.nextDouble() * 0.35);
            double a = tickCount * 0.32 + r.nextDouble() * Math.PI * 2;
            double x = getX() + Math.cos(a) * radius;
            double z = getZ() + Math.sin(a) * radius;
            double y = getY() + h;
            // Move along the swirl so particles visibly spin.
            double tx = -Math.sin(a) * 0.45;
            double tz = Math.cos(a) * 0.45;
            ParticleOptions type;
            int roll = r.nextInt(100);
            if (roll < 50) type = ParticleTypes.CAMPFIRE_COSY_SMOKE;
            else if (roll < 68) type = ParticleTypes.CLOUD;
            else if (roll < 84) type = ParticleTypes.SMALL_GUST;
            else if (roll < 92) type = ParticleTypes.GUST;
            else if (roll < 97) type = ParticleTypes.LARGE_SMOKE;
            else type = ParticleTypes.EXPLOSION;
            level.addAlwaysVisibleParticle(type, x, y, z, tx, 0.04 + r.nextDouble() * 0.12, tz);
        }
        // Debris spray around the base
        BlockState ground = level.getBlockState(blockPosition().below());
        if (!ground.isAir()) {
            for (int i = 0; i < 12; i++) {
                double a = r.nextDouble() * Math.PI * 2;
                double d = BASE_RADIUS + r.nextDouble() * 5;
                level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, ground), getX() + Math.cos(a) * d, getY() + 0.3, getZ() + Math.sin(a) * d,
                        -Math.sin(a) * 0.6, 0.4 + r.nextDouble() * 0.5, Math.cos(a) * 0.6);
            }
        }
        if (tickCount % 4 == 0) {
            level.addAlwaysVisibleParticle(ParticleTypes.EXPLOSION,
                    getX() + (r.nextDouble() - 0.5) * 4, getY() + r.nextDouble() * 3, getZ() + (r.nextDouble() - 0.5) * 4, 0, 0, 0);
        }
    }

    // ---------------------------------------------------------------- movement (server)

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
        motion = motion.add(Math.sin(age * 0.05) * 0.05, 0, Math.cos(age * 0.04) * 0.05);
        double nx = getX() + motion.x;
        double nz = getZ() + motion.z;
        BlockPos column = BlockPos.containing(nx, getY(), nz);
        if (!level.isLoaded(column)) return;
        // Always sits on the surface (never in caves), following the terrain exactly.
        double ny = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column).getY();
        setPos(nx, ny, nz);
    }

    // ---------------------------------------------------------------- physics (server)

    private void pull(ServerLevel level) {
        AABB box = new AABB(getX() - TOP_RADIUS - 8, getY() - 3, getZ() - TOP_RADIUS - 8, getX() + TOP_RADIUS + 8, getY() + HEIGHT + 6, getZ() + TOP_RADIUS + 8);
        long now = level.getGameTime();
        RandomSource r = level.getRandom();
        for (Entity e : level.getEntities(this, box, e -> !(e instanceof TornadoEntity) && !e.isSpectator()
                && !(e instanceof Player p && p.getAbilities().flying))) {
            if (now - e.getPersistentData().getLongOr(FLUNG, -1000L) < 40) continue;
            // Only things out in the open: nothing underground or under a roof is touched.
            if (e.getY() < getY() - 3 || !level.canSeeSky(e.blockPosition().above())) continue;
            double h = e.getY() - getY();
            double dx = e.getX() - getX();
            double dz = e.getZ() - getZ();
            double dist = Math.sqrt(dx * dx + dz * dz);
            double radius = radiusAt(Math.max(0, h));
            if (dist > radius + 8 || dist < 0.01) continue;
            Vec3 inward = new Vec3(-dx / dist, 0, -dz / dist);
            Vec3 tangent = new Vec3(-dz / dist, 0, dx / dist);
            Vec3 velocity;
            if (dist > radius * 1.2) {
                // Outer winds: dragged toward the funnel.
                double pull = 0.12 * (1.0 - (dist - radius * 1.2) / 10.0);
                velocity = e.getDeltaMovement().add(inward.scale(pull)).add(tangent.scale(pull * 0.8));
            } else if (h > HEIGHT * 0.82 || (h > HEIGHT * 0.45 && r.nextInt(35) == 0)) {
                // Thrown out of the funnel.
                velocity = inward.scale(-(2.0 + r.nextDouble() * 1.2)).add(tangent.scale(0.8)).add(0, 0.7 + r.nextDouble() * 0.5, 0);
                e.getPersistentData().putLong(FLUNG, now);
            } else {
                // Caught: spun around the funnel and carried upward.
                double toWall = (radius * 0.8 - dist) * 0.08;
                velocity = tangent.scale(0.7).add(inward.scale(-toWall)).add(0, 0.38, 0);
                e.fallDistance = 0;
                if (e instanceof LivingEntity living && now % 20 == 0) {
                    living.hurtServer(level, level.damageSources().flyIntoWall(), 1.0F);
                }
            }
            e.setDeltaMovement(velocity);
            e.needsSync = true;
            if (e instanceof ServerPlayer player) player.connection.send(new ClientboundSetEntityMotionPacket(player));
        }
    }

    private void rip(ServerLevel level) {
        RandomSource random = level.getRandom();
        if (level.getEntitiesOfClass(FallingBlockEntity.class, getBoundingBox().inflate(48)).size() > 150) return;
        for (int i = 0; i < 16; i++) {
            double a = random.nextDouble() * Math.PI * 2;
            double d = random.nextDouble() * (BASE_RADIUS + 5);
            BlockPos column = BlockPos.containing(getX() + Math.cos(a) * d, getY(), getZ() + Math.sin(a) * d);
            if (!level.isLoaded(column)) continue;
            BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, column).below();
            if (Math.abs(top.getY() - getY()) > 6) continue;
            BlockState state = level.getBlockState(top);
            if (!EventUtil.isBreakable(level, top, state, 2.5F) || !state.getFluidState().isEmpty()) continue;
            FallingBlockEntity fb = FallingBlockEntity.fall(level, top, state);
            fb.setDeltaMovement((random.nextDouble() - 0.5) * 0.5, 0.9 + random.nextDouble() * 0.6, (random.nextDouble() - 0.5) * 0.5);
            fb.needsSync = true;
        }
    }
}
