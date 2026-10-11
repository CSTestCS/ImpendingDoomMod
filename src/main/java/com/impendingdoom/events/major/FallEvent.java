package com.impendingdoom.events.major;

import com.impendingdoom.ImpendingDoom;
import com.impendingdoom.block.FallenLeavesBlock;
import com.impendingdoom.core.DoomEvent;
import com.impendingdoom.core.EventScheduler;
import com.impendingdoom.util.EventUtil;
import com.impendingdoom.util.GearUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.SwellGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * Fall (season 2/4): leaves turn red, orange and yellow (client) and shed fallen leaves that stack like snow.
 * A full block of leaves becomes a spawner of decaying creepers that always know where the player is. Tiny spiders
 * roam at any time of day and sometimes swarm, and skeletons are more common and carry any weapon or armor.
 */
@EventBusSubscriber(modid = ImpendingDoom.MODID)
public class FallEvent extends DoomEvent {
    public static final String ID = "fall";
    public static final String DECAYING = "impendingdoom.decaying_creeper";
    public static final String TINY = "impendingdoom.tiny_spider";
    private static final String FUSE = "impendingdoom_fuse";

    public FallEvent() {
        super(ID, true, ChatFormatting.GOLD);
    }

    @Override
    public boolean isSeason() {
        return true;
    }

    @Override
    public void onTick(ServerLevel level, CompoundTag data) {
        long time = level.getGameTime();
        RandomSource r = level.getRandom();
        if (time % 10 == 0) {
            for (ServerPlayer player : level.players()) shedLeaves(level, player.blockPosition(), r);
        }
        if (time % 600 == 0) {
            for (ServerPlayer player : EventUtil.targets(level)) {
                if (r.nextInt(4) != 0) continue;
                boolean swarm = r.nextInt(4) == 0;
                int count = swarm ? 8 + r.nextInt(7) : 2 + r.nextInt(2);
                BlockPos origin = EventUtil.surfacePos(level, player.blockPosition(), 16, 32, r);
                if (origin == null) continue;
                for (int i = 0; i < count; i++) {
                    BlockPos pos = EventUtil.surfacePos(level, origin, 0, 3, r);
                    if (EventUtil.spawn(level, EntityType.SPIDER, pos == null ? origin : pos) instanceof Spider spider) {
                        makeTiny(spider);
                        if (swarm) spider.setTarget(player);
                    }
                }
            }
        }
    }

    private static void shedLeaves(ServerLevel level, BlockPos center, RandomSource r) {
        for (int i = 0; i < 24; i++) {
            BlockPos pos = center.offset(r.nextInt(49) - 24, r.nextInt(25) - 8, r.nextInt(49) - 24);
            if (!level.getBlockState(pos).is(BlockTags.LEAVES) || r.nextInt(3) != 0) continue;
            BlockPos p = pos.below();
            for (int d = 0; d < 32 && p.getY() > level.getMinY(); d++, p = p.below()) {
                BlockState s = level.getBlockState(p);
                if (s.isAir() || s.is(BlockTags.LEAVES) || s.is(BlockTags.LOGS)) continue;
                if (s.is(com.impendingdoom.registry.ModBlocks.FALLEN_LEAVES.get())) {
                    FallenLeavesBlock.addLayer(level, p);
                } else if (level.getBlockState(p.above()).isAir()) {
                    FallenLeavesBlock.addLayer(level, p.above());
                }
                break;
            }
        }
    }

    /** Called by leaf piles: they work as spawners of decaying creepers, even in the light. */
    public static void leafPileSpawn(ServerLevel level, BlockPos pos, RandomSource r) {
        if (!EventScheduler.isActive(ID)) return;
        if (level.getNearestPlayer(pos.getX(), pos.getY(), pos.getZ(), 32, p -> !((Player) p).isCreative() && !p.isSpectator()) == null) return;
        int nearby = level.getEntitiesOfClass(Creeper.class, new AABB(pos).inflate(16), c -> c.getTags().contains(DECAYING)).size();
        if (nearby >= 3 || !level.getBlockState(pos.above()).isAir() || !level.getBlockState(pos.above(2)).isAir()) return;
        Creeper creeper = EntityType.CREEPER.create(level, EntitySpawnReason.SPAWNER);
        if (creeper == null) return;
        creeper.setPos(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5);
        creeper.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.SPAWNER, null);
        makeDecaying(creeper);
        level.addFreshEntity(creeper);
        level.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 20, 0.4, 0.4, 0.4, 0.01);
    }

    private static void makeDecaying(Creeper creeper) {
        creeper.addTag(DECAYING);
        creeper.setCustomName(net.minecraft.network.chat.Component.translatable("entity.impendingdoom.decaying_creeper"));
        EventUtil.addModifier(creeper, Attributes.FOLLOW_RANGE, "decaying_range", 128, AttributeModifier.Operation.ADD_VALUE);
        setupDecaying(creeper);
    }

    private static void setupDecaying(Creeper creeper) {
        // Decaying creepers do not swell normally; they rot apart in a weaker blast handled below.
        creeper.goalSelector.removeAllGoals(g -> g instanceof SwellGoal);
    }

    private static void makeTiny(Spider spider) {
        spider.addTag(TINY);
        EventUtil.addModifier(spider, Attributes.SCALE, "tiny_scale", -0.65, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        EventUtil.addModifier(spider, Attributes.MAX_HEALTH, "tiny_health", -0.7, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        EventUtil.addModifier(spider, Attributes.MOVEMENT_SPEED, "tiny_speed", 0.25, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        spider.setHealth(spider.getMaxHealth());
        setupTiny(spider);
    }

    private static void setupTiny(Spider spider) {
        // Daylight does not calm tiny spiders.
        spider.goalSelector.addGoal(2, new MeleeAttackGoal(spider, 1.0, true));
        spider.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(spider, Player.class, true));
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !event.loadedFromDisk()) return;
        if (event.getEntity() instanceof Creeper c && c.getTags().contains(DECAYING)) setupDecaying(c);
        if (event.getEntity() instanceof Spider s && s.getTags().contains(TINY)) setupTiny(s);
    }

    @SubscribeEvent
    public static void onCreeperTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof Creeper creeper) || !(creeper.level() instanceof ServerLevel level) || !creeper.getTags().contains(DECAYING)) return;
        if (creeper.tickCount % 20 == 0) {
            // Always knows where the player is.
            Player target = level.getNearestPlayer(creeper.getX(), creeper.getY(), creeper.getZ(), 128, p -> !((Player) p).isCreative() && !p.isSpectator());
            if (target != null) {
                creeper.setTarget(target);
                if (creeper.distanceToSqr(target) > 16 * 16) creeper.getNavigation().moveTo(target, 1.0);
            }
            level.sendParticles(ParticleTypes.MYCELIUM, creeper.getX(), creeper.getY() + 1, creeper.getZ(), 4, 0.3, 0.5, 0.3, 0);
        }
        int fuse = creeper.getPersistentData().getIntOr(FUSE, 0);
        if (creeper.getTarget() != null && creeper.distanceToSqr(creeper.getTarget()) < 3.0 * 3.0) {
            if (fuse == 0) level.playSound(null, creeper.blockPosition(), SoundEvents.CREEPER_PRIMED, SoundSource.HOSTILE, 1.0F, 0.6F);
            fuse++;
            if (fuse >= 30) {
                level.explode(creeper, creeper.getX(), creeper.getY(), creeper.getZ(), 1.6F, Level.ExplosionInteraction.MOB);
                creeper.discard();
                return;
            }
        } else {
            fuse = Math.max(0, fuse - 1);
        }
        creeper.getPersistentData().putInt(FUSE, fuse);
    }

    @SubscribeEvent
    public static void onFinalize(FinalizeSpawnEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !EventScheduler.isActive(ID)) return;
        if (!(event.getEntity() instanceof AbstractSkeleton skeleton) || event.getSpawnType() != EntitySpawnReason.NATURAL) return;
        RandomSource r = level.getRandom();
        EventUtil.defer(() -> {
            GearUtil.equipArmor(skeleton, r, 0, 5, 0.6F);
            GearUtil.equip(skeleton, EquipmentSlot.MAINHAND, GearUtil.randomWeapon(r), 0.085F);
            skeleton.reassessWeaponGoal();
            // More skeletons
            if (r.nextBoolean()) {
                BlockPos pos = EventUtil.surfacePos(level, skeleton.blockPosition(), 2, 6, r);
                if (pos != null) {
                    Mob extra = EventUtil.spawn(level, EntityType.SKELETON, pos);
                    if (extra instanceof AbstractSkeleton s) {
                        GearUtil.equipArmor(s, r, 0, 5, 0.6F);
                        GearUtil.equip(s, EquipmentSlot.MAINHAND, GearUtil.randomWeapon(r), 0.085F);
                        s.reassessWeaponGoal();
                    }
                }
            }
        });
    }

    @Override
    public void onEnd(ServerLevel level, CompoundTag data) {
        // Fallen leaves stay as decoration; leaf piles stop spawning once Fall ends (see leafPileSpawn).
    }
}
