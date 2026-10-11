package com.impendingdoom.entity;

import com.impendingdoom.util.EventUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;

import java.util.ArrayList;
import java.util.List;

/** A falling end stone meteor. On impact it blasts a crater and leaves an end stone boulder with an ore core. */
public class MeteorEntity extends Entity {
    private int life;

    public MeteorEntity(EntityType<? extends MeteorEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        life = input.getIntOr("Life", 0);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.putInt("Life", life);
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return true;
    }

    @Override
    public void tick() {
        super.tick();
        Vec3 pos = position();
        Vec3 motion = getDeltaMovement();
        Vec3 next = pos.add(motion);
        if (level() instanceof ServerLevel level) {
            if (++life > 800 || !level.isLoaded(BlockPos.containing(next))) {
                discard();
                return;
            }
            level.sendParticles(ParticleTypes.FLAME, getX(), getY() + 1, getZ(), 12, 0.8, 0.8, 0.8, 0.02);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 1, getZ(), 6, 0.6, 0.6, 0.6, 0.01);
            level.sendParticles(ParticleTypes.END_ROD, getX(), getY() + 1, getZ(), 2, 0.5, 0.5, 0.5, 0.05);
            if (life % 20 == 0) level.playSound(null, getX(), getY(), getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.WEATHER, 6.0F, 0.3F);
            BlockHitResult hit = level.clip(new ClipContext(pos, next.add(motion.normalize().scale(1.5)), ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, this));
            if (hit.getType() == HitResult.Type.BLOCK) {
                impact(level, hit.getLocation());
                return;
            }
        }
        setPos(next.x, next.y, next.z);
    }

    private void impact(ServerLevel level, Vec3 at) {
        level.explode(this, at.x, at.y, at.z, 5.0F, true,
                EventUtil.canDestroyBlocks(level) ? Level.ExplosionInteraction.BLOCK : Level.ExplosionInteraction.NONE);
        BlockPos center = BlockPos.containing(at).below(2);
        RandomSource random = level.getRandom();
        List<Block> ores = new ArrayList<>();
        for (Holder<Block> holder : BuiltInRegistries.BLOCK.getTagOrEmpty(Tags.Blocks.ORES)) ores.add(holder.value());
        int radius = 2 + random.nextInt(2);
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-radius, -radius, -radius), center.offset(radius, radius, radius))) {
            double d = Math.sqrt(p.distSqr(center));
            if (d > radius + 0.3) continue;
            BlockState existing = level.getBlockState(p);
            if (existing.getDestroySpeed(level, p) < 0) continue;
            BlockState place;
            if (d <= radius - 1.2 && !ores.isEmpty()) {
                place = random.nextInt(4) == 0 ? Blocks.DIAMOND_ORE.defaultBlockState() : ores.get(random.nextInt(ores.size())).defaultBlockState();
            } else {
                place = random.nextInt(8) == 0 ? Blocks.OBSIDIAN.defaultBlockState() : Blocks.END_STONE.defaultBlockState();
            }
            level.setBlockAndUpdate(p.immutable(), place);
        }
        // There is always at least one valuable ore at the heart of a meteor.
        level.setBlockAndUpdate(center, Blocks.DIAMOND_ORE.defaultBlockState());
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y, at.z, 2, 1, 1, 1, 0);
        discard();
    }
}
