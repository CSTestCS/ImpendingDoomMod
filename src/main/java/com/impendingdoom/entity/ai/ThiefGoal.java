package com.impendingdoom.entity.ai;

import com.impendingdoom.util.EventUtil;
import com.impendingdoom.util.GearUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * Thief brain:
 * <ol>
 *   <li>SEEK: find the nearest chest/barrel and go to it, climbing walls, tunnelling through dirt/grass/sand and
 *       gliding when it drops from a height.</li>
 *   <li>Steal: grab several stacks, wear any armor and wield the strongest weapon found. It can no longer despawn.</li>
 *   <li>ESCAPE: run to the nearest reachable cave.</li>
 *   <li>HIDE: sit and admire the loot until a player or golem comes to attack it.</li>
 * </ol>
 */
public class ThiefGoal extends Goal {
    private static final String STATE = "impendingdoom_thief_state";
    private static final String LOOT = "impendingdoom_thief_loot";
    private static final String DEST = "impendingdoom_thief_dest";
    private static final int SEEK = 0, ESCAPE = 1, HIDE = 2;

    private final Mob thief;
    private int repath;
    private int stuckTicks;
    private Vec3 lastPos = Vec3.ZERO;

    public ThiefGoal(Mob thief) {
        this.thief = thief;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    public static boolean isHiding(Mob mob) {
        return mob.getPersistentData().getIntOr(STATE, SEEK) == HIDE;
    }

    public static boolean hasLoot(Mob mob) {
        return mob.getPersistentData().getIntOr(STATE, SEEK) != SEEK;
    }

    private int state() {
        return thief.getPersistentData().getIntOr(STATE, SEEK);
    }

    private void setState(int state) {
        thief.getPersistentData().putInt(STATE, state);
    }

    @Override
    public boolean canUse() {
        return thief.getTarget() == null;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (!(thief.level() instanceof ServerLevel level)) return;
        // Arms out, so whatever it carries is visible.
        if (!thief.isAggressive()) thief.setAggressive(true);
        climbAndGlide();
        switch (state()) {
            case SEEK -> seek(level);
            case ESCAPE -> escape(level);
            case HIDE -> admire(level);
            default -> setState(SEEK);
        }
    }

    private void climbAndGlide() {
        if (thief.horizontalCollision && state() != HIDE) {
            Vec3 v = thief.getDeltaMovement();
            thief.setDeltaMovement(v.x, 0.22, v.z);
            thief.fallDistance = 0;
        }
        if (thief.fallDistance > 2.5F && !thief.hasEffect(MobEffects.SLOW_FALLING)) {
            thief.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 60, 0, false, false));
        }
    }

    private BlockPos destination() {
        long packed = thief.getPersistentData().getLongOr(DEST, Long.MIN_VALUE);
        return packed == Long.MIN_VALUE ? null : BlockPos.of(packed);
    }

    private void setDestination(BlockPos pos) {
        if (pos == null) thief.getPersistentData().remove(DEST);
        else thief.getPersistentData().putLong(DEST, pos.asLong());
    }

    private void seek(ServerLevel level) {
        BlockPos chest = destination();
        if (chest == null || !(level.getBlockEntity(chest) instanceof Container) || thief.tickCount % 200 == 0) {
            chest = findContainer(level);
            setDestination(chest);
        }
        if (chest == null) {
            ServerLevelHelper.wander(thief);
            return;
        }
        if (thief.blockPosition().closerThan(chest, 2.5) && canReach(level, chest)) {
            steal(level, chest);
            return;
        }
        travel(level, chest);
    }

    /** The chest must be in plain reach: nothing between the thief's eyes and the chest. */
    private boolean canReach(ServerLevel level, BlockPos chest) {
        Vec3 eye = thief.getEyePosition();
        Vec3 target = Vec3.atCenterOf(chest);
        net.minecraft.world.phys.BlockHitResult hit = level.clip(new net.minecraft.world.level.ClipContext(eye, target,
                net.minecraft.world.level.ClipContext.Block.OUTLINE, net.minecraft.world.level.ClipContext.Fluid.NONE, thief));
        return hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS || hit.getBlockPos().equals(chest);
    }

    private void travel(ServerLevel level, BlockPos dest) {
        if (--repath <= 0) {
            repath = 30;
            thief.getNavigation().moveTo(dest.getX() + 0.5, dest.getY(), dest.getZ() + 0.5, 1.15);
        }
        if (thief.position().distanceToSqr(lastPos) < 0.01) stuckTicks++;
        else stuckTicks = 0;
        lastPos = thief.position();
        if (stuckTicks > 30 && EventUtil.canDestroyBlocks(level)) {
            tunnel(level, dest);
            stuckTicks = 0;
        }
    }

    /** Tunnels through soft ground (dirt, grass, sand, gravel) toward the destination. */
    private void tunnel(ServerLevel level, BlockPos dest) {
        Vec3 dir = Vec3.atCenterOf(dest).subtract(thief.position());
        Direction facing = Direction.getApproximateNearest(dir.x, 0, dir.z);
        BlockPos front = thief.blockPosition().relative(facing);
        BlockPos[] targets = dir.y < -1.5
                ? new BlockPos[]{thief.blockPosition().below(), front}
                : new BlockPos[]{front, front.above()};
        for (BlockPos p : targets) {
            BlockState state = level.getBlockState(p);
            if (state.is(BlockTags.DIRT) || state.is(BlockTags.SAND) || state.is(Blocks.GRAVEL) || state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.CLAY)) {
                level.destroyBlock(p, true, thief);
            }
        }
    }

    private BlockPos findContainer(ServerLevel level) {
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        ChunkPos center = thief.chunkPosition();
        for (int cx = -3; cx <= 3; cx++) {
            for (int cz = -3; cz <= 3; cz++) {
                if (!level.hasChunk(center.x() + cx, center.z() + cz)) continue;
                LevelChunk chunk = level.getChunk(center.x() + cx, center.z() + cz);
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (!(be instanceof Container container) || container.isEmpty()) continue;
                    if (!(be instanceof net.minecraft.world.level.block.entity.ChestBlockEntity)
                            && !(be instanceof net.minecraft.world.level.block.entity.BarrelBlockEntity)) continue;
                    double d = be.getBlockPos().distSqr(thief.blockPosition());
                    if (d < bestDist) {
                        bestDist = d;
                        best = be.getBlockPos();
                    }
                }
            }
        }
        return best;
    }

    private void steal(ServerLevel level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof Container container)) {
            setDestination(null);
            return;
        }
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < container.getContainerSize(); i++) if (!container.getItem(i).isEmpty()) slots.add(i);
        if (slots.isEmpty()) {
            setDestination(null);
            return;
        }
        java.util.Collections.shuffle(slots, new java.util.Random(thief.getRandom().nextLong()));
        List<ItemStack> loot = new ArrayList<>();
        for (int i = 0; i < Math.min(6, slots.size()); i++) {
            loot.add(container.removeItemNoUpdate(slots.get(i)));
        }
        container.setChanged();
        level.playSound(null, pos, SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 0.6F, 1.2F);
        level.playSound(null, thief.blockPosition(), SoundEvents.VINDICATOR_CELEBRATE, SoundSource.HOSTILE, 1.0F, 1.4F);

        List<ItemStack> kept = new ArrayList<>();
        for (ItemStack stack : loot) {
            EquipmentSlot slot = GearUtil.armorSlot(stack);
            if (slot != null && GearUtil.armorValue(stack) > GearUtil.armorValue(thief.getItemBySlot(slot))) {
                ItemStack old = thief.getItemBySlot(slot);
                if (!old.isEmpty()) kept.add(old);
                GearUtil.equip(thief, slot, stack, 2.0F);
            } else if (GearUtil.attackValue(stack) > GearUtil.attackValue(thief.getMainHandItem())) {
                ItemStack old = thief.getMainHandItem();
                if (!old.isEmpty()) kept.add(old);
                GearUtil.equip(thief, EquipmentSlot.MAINHAND, stack, 2.0F);
            } else {
                kept.add(stack);
            }
        }
        // Show off the loot: hold it in the off hand (and the main hand when there is no weapon).
        if (!kept.isEmpty()) GearUtil.equip(thief, EquipmentSlot.OFFHAND, kept.remove(0), 2.0F);
        if (thief.getMainHandItem().isEmpty() && !kept.isEmpty()) GearUtil.equip(thief, EquipmentSlot.MAINHAND, kept.remove(0), 2.0F);
        storeLoot(level, kept);
        thief.setPersistenceRequired();
        setState(ESCAPE);
        setDestination(findCave(level));
    }

    private void storeLoot(ServerLevel level, List<ItemStack> stacks) {
        RegistryOps<Tag> ops = level.registryAccess().createSerializationContext(NbtOps.INSTANCE);
        ListTag list = thief.getPersistentData().getListOrEmpty(LOOT).copy();
        for (ItemStack stack : stacks) {
            ItemStack.CODEC.encodeStart(ops, stack).result().ifPresent(list::add);
        }
        thief.getPersistentData().put(LOOT, list);
    }

    /** Removes and returns the stolen loot (for drops on death). */
    public static List<ItemStack> takeLoot(Mob thief) {
        List<ItemStack> out = new ArrayList<>();
        RegistryOps<Tag> ops = thief.level().registryAccess().createSerializationContext(NbtOps.INSTANCE);
        ListTag list = thief.getPersistentData().getListOrEmpty(LOOT);
        for (Tag t : list) ItemStack.CODEC.parse(ops, t).result().ifPresent(out::add);
        thief.getPersistentData().remove(LOOT);
        return out;
    }

    /** Looks for the nearest dark, roofed air pocket underground. */
    private BlockPos findCave(ServerLevel level) {
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (int i = 0; i < 48; i++) {
            int x = thief.getBlockX() + thief.getRandom().nextInt(97) - 48;
            int z = thief.getBlockZ() + thief.getRandom().nextInt(97) - 48;
            BlockPos column = new BlockPos(x, 0, z);
            if (!level.isLoaded(column)) continue;
            int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
            for (int y = top - 6; y > level.getMinY() + 4; y -= 2) {
                BlockPos p = new BlockPos(x, y, z);
                if (level.getBlockState(p).isAir() && level.getBlockState(p.above()).isAir()
                        && level.getBlockState(p.below()).isFaceSturdy(level, p.below(), Direction.UP) && !level.canSeeSky(p)) {
                    double d = p.distSqr(thief.blockPosition());
                    if (d < bestDist) {
                        bestDist = d;
                        best = p;
                    }
                    break;
                }
            }
        }
        if (best == null) {
            // No cave nearby: run far away from the closest player and dig in.
            BlockPos away = thief.blockPosition().offset(thief.getRandom().nextInt(129) - 64, 0, thief.getRandom().nextInt(129) - 64);
            best = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, away).below(4);
        }
        return best;
    }

    private void escape(ServerLevel level) {
        BlockPos cave = destination();
        if (cave == null) {
            cave = findCave(level);
            setDestination(cave);
        }
        if (thief.blockPosition().closerThan(cave, 2.5)) {
            setState(HIDE);
            thief.getNavigation().stop();
            return;
        }
        travel(level, cave);
    }

    private void admire(ServerLevel level) {
        thief.getNavigation().stop();
        if (thief.tickCount % 40 == 0) {
            thief.getLookControl().setLookAt(thief.getX() + thief.getRandom().nextGaussian(), thief.getEyeY() - 1, thief.getZ() + thief.getRandom().nextGaussian());
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, thief.getX(), thief.getY() + 1.0, thief.getZ(), 1, 0.3, 0.2, 0.3, 0);
        }
        if (thief.tickCount % 200 == 0) level.playSound(null, thief.blockPosition(), SoundEvents.VINDICATOR_AMBIENT, SoundSource.HOSTILE, 0.6F, 1.5F);
    }

    /** Fallback idle movement while no chest is known. */
    static final class ServerLevelHelper {
        static void wander(Mob mob) {
            if (mob.getNavigation().isDone() && mob.getRandom().nextInt(40) == 0) {
                Vec3 p = mob.position().add(mob.getRandom().nextInt(17) - 8, 0, mob.getRandom().nextInt(17) - 8);
                mob.getNavigation().moveTo(p.x, p.y, p.z, 1.0);
            }
        }
    }
}
