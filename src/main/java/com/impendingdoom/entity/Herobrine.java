package com.impendingdoom.entity;

import com.impendingdoom.core.EventScheduler;
import com.impendingdoom.events.major.HerobrineEvent;
import com.impendingdoom.util.EventUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.EvokerFangs;
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball;
import net.minecraft.world.entity.projectile.hurtingprojectile.WitherSkull;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * There is only ever one. He appears at a distance and stares, hides behind blocks ahead of the player and
 * vanishes when spotted, casts random magic, summons monsters, and sometimes breaks the single block layer a player
 * is standing on from behind. He cannot be harmed: hitting him only makes him vanish.
 */
public class Herobrine extends PathfinderMob {
    private enum Mode { VANISHED, STARE, HIDE, BEHIND }

    private Mode mode = Mode.VANISHED;
    private int modeTimer = 200;
    private int attackCooldown = 1800;
    private int watchTimer;
    private ServerPlayer watched;

    public Herobrine(EntityType<? extends Herobrine> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        setInvisible(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 200.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FOLLOW_RANGE, 128.0);
    }

    @Override
    protected void registerGoals() {
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) return super.hurtServer(level, source, amount);
        if (mode != Mode.VANISHED) vanish(level);
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        attackCooldown = input.getIntOr("AttackCooldown", 1800);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("AttackCooldown", attackCooldown);
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (!EventScheduler.isActive(HerobrineEvent.ID) || !HerobrineEvent.isTheOne(this)) {
            discard();
            return;
        }
        if (watched == null || !watched.isAlive() || watched.level() != level || watched.isSpectator() || --watchTimer <= 0) {
            watched = EventUtil.randomTarget(level);
            watchTimer = 6000;
        }
        if (watched == null) {
            if (mode != Mode.VANISHED) vanish(level);
            return;
        }
        modeTimer--;
        switch (mode) {
            case VANISHED -> {
                if (modeTimer <= 0) {
                    if (random.nextInt(10) < 6) appearStaring(level);
                    else hideAhead(level);
                }
            }
            case STARE -> {
                lookAtWatched();
                if (modeTimer <= 0 || distanceTo(watched) < 7.0) vanish(level);
            }
            case HIDE -> {
                lookAtWatched();
                if (modeTimer <= 0 || isSpottedBy(watched)) vanish(level);
            }
            case BEHIND -> {
                lookAtWatched();
                if (modeTimer <= 0) vanish(level);
            }
        }
        if (--attackCooldown <= 0) {
            attackCooldown = 1200 + random.nextInt(2400);
            attack(level);
        }
    }

    private void lookAtWatched() {
        getLookControl().setLookAt(watched, 360.0F, 360.0F);
        double dx = watched.getX() - getX();
        double dz = watched.getZ() - getZ();
        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
        setYRot(yaw);
        setYHeadRot(yaw);
        yBodyRot = yaw;
    }

    private boolean isSpottedBy(ServerPlayer player) {
        Vec3 look = player.getViewVector(1.0F).normalize();
        Vec3 to = new Vec3(getX() - player.getX(), getEyeY() - player.getEyeY(), getZ() - player.getZ());
        double dist = to.length();
        return look.dot(to.normalize()) > 1.0 - 0.025 / Math.max(0.2, dist / 16.0) && player.hasLineOfSight(this);
    }

    private void teleportVisible(ServerLevel level, Vec3 pos) {
        teleportTo(pos.x, pos.y, pos.z);
        setInvisible(false);
        getNavigation().stop();
    }

    private void appearStaring(ServerLevel level) {
        RandomSource r = getRandom();
        for (int i = 0; i < 12; i++) {
            BlockPos pos = EventUtil.surfacePos(level, watched.blockPosition(), 14, 28, r);
            if (pos == null) continue;
            Vec3 at = Vec3.atBottomCenterOf(pos);
            teleportVisible(level, at);
            if (watched.hasLineOfSight(this)) {
                mode = Mode.STARE;
                modeTimer = 200 + r.nextInt(300);
                return;
            }
        }
        hideAhead(level);
    }

    /** Hide behind a block in the direction the player is heading, waiting to be seen. */
    private void hideAhead(ServerLevel level) {
        RandomSource r = getRandom();
        Vec3 dir = watched.getDeltaMovement().multiply(1, 0, 1);
        if (dir.lengthSqr() < 0.001) dir = watched.getViewVector(1.0F).multiply(1, 0, 1);
        dir = dir.normalize();
        Vec3 eye = watched.getEyePosition();
        for (int i = 0; i < 20; i++) {
            double dist = 10 + r.nextInt(12);
            Vec3 side = new Vec3(-dir.z, 0, dir.x).scale((r.nextDouble() - 0.5) * 12);
            Vec3 guess = watched.position().add(dir.scale(dist)).add(side);
            BlockPos column = BlockPos.containing(guess);
            if (!level.isLoaded(column)) continue;
            BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
            Vec3 at = Vec3.atBottomCenterOf(ground);
            // Body hidden, head barely peeking out
            HitResult body = level.clip(new ClipContext(eye, at.add(0, 0.8, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, watched));
            HitResult head = level.clip(new ClipContext(eye, at.add(0, 1.7, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, watched));
            if (body.getType() == HitResult.Type.BLOCK && head.getType() == HitResult.Type.MISS) {
                teleportVisible(level, at);
                mode = Mode.HIDE;
                modeTimer = 900;
                return;
            }
        }
        mode = Mode.VANISHED;
        modeTimer = 200;
    }

    private void vanish(ServerLevel level) {
        if (!isInvisible()) {
            level.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 1, getZ(), 25, 0.3, 0.8, 0.3, 0.02);
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 0.4F, 0.5F);
        }
        setInvisible(true);
        mode = Mode.VANISHED;
        modeTimer = 400 + getRandom().nextInt(1600);
    }

    private void attack(ServerLevel level) {
        if (watched == null || watched.isCreative()) return;
        int roll = getRandom().nextInt(10);
        if (roll < 3 && standsOnSingleLayer(level, watched)) {
            breakFloor(level);
        } else if (roll < 6) {
            summon(level);
        } else {
            magic(level);
        }
    }

    private static boolean standsOnSingleLayer(ServerLevel level, ServerPlayer player) {
        BlockPos below = player.blockPosition().below();
        BlockState floor = level.getBlockState(below);
        BlockState under = level.getBlockState(below.below());
        return player.onGround() && !floor.isAir() && floor.getDestroySpeed(level, below) >= 0 && under.getCollisionShape(level, below.below()).isEmpty();
    }

    private void breakFloor(ServerLevel level) {
        Vec3 back = watched.getViewVector(1.0F).multiply(1, 0, 1).normalize().scale(-1.6);
        teleportVisible(level, watched.position().add(back));
        lookAtWatched();
        swingForAttack(net.minecraft.world.InteractionHand.MAIN_HAND);
        level.destroyBlock(watched.blockPosition().below(), false, this);
        mode = Mode.BEHIND;
        modeTimer = 15;
    }

    private void summon(ServerLevel level) {
        EntityType<?>[] types = {EntityTypes.ZOMBIE, EntityTypes.SKELETON, EntityTypes.SPIDER, EntityTypes.CREEPER, EntityTypes.VEX, EntityTypes.WITCH, EntityTypes.CREAKING};
        int count = 2 + getRandom().nextInt(3);
        for (int i = 0; i < count; i++) {
            BlockPos pos = EventUtil.surfacePos(level, watched.blockPosition(), 4, 10, getRandom());
            if (pos == null) continue;
            var mob = EventUtil.spawn(level, types[getRandom().nextInt(types.length)], pos);
            if (mob instanceof net.minecraft.world.entity.monster.creaking.Creaking creaking
                    && com.impendingdoom.util.CreakingCompanions.heartOf(level, creaking) == null) {
                com.impendingdoom.util.CreakingCompanions.attach(level, creaking);
            }
            if (mob instanceof Mob m) m.setTarget(watched);
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 20, 0.3, 0.6, 0.3, 0.02);
        }
    }

    private void magic(ServerLevel level) {
        Vec3 from = new Vec3(getX(), getEyeY(), getZ());
        Vec3 dir = watched.getEyePosition().subtract(from).normalize();
        switch (getRandom().nextInt(6)) {
            case 0 -> {
                LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.EVENT);
                if (bolt != null) {
                    bolt.setPos(watched.getX(), watched.getY(), watched.getZ());
                    level.addFreshEntity(bolt);
                }
            }
            case 1 -> {
                watched.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 60, 1));
                watched.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 100, 0));
            }
            case 2 -> {
                for (int i = 0; i < 3; i++) {
                    SmallFireball fb = new SmallFireball(level, this, dir.add(getRandom().nextGaussian() * 0.05, 0, getRandom().nextGaussian() * 0.05));
                    fb.setPos(from.x, from.y, from.z);
                    level.addFreshEntity(fb);
                }
            }
            case 3 -> {
                WitherSkull skull = new WitherSkull(level, this, dir);
                skull.setPos(from.x, from.y, from.z);
                level.addFreshEntity(skull);
            }
            case 4 -> {
                for (int i = 0; i < 8; i++) {
                    double a = i * Math.PI / 4;
                    level.addFreshEntity(new EvokerFangs(level, watched.getX() + Math.cos(a) * 1.5, watched.getY(), watched.getZ() + Math.sin(a) * 1.5,
                            (float) a, 5, this));
                }
            }
            default -> {
                watched.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 200, 0));
                watched.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, 400, 1));
                watched.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 200, 1));
            }
        }
        level.playSound(null, watched.getX(), watched.getY(), watched.getZ(), SoundEvents.EVOKER_CAST_SPELL, SoundSource.HOSTILE, 1.0F, 0.5F);
    }
}
