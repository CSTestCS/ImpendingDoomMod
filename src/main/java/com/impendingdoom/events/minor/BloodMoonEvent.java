package com.impendingdoom.events.minor;

import com.impendingdoom.ImpendingDoom;
import com.impendingdoom.core.DayClock;
import com.impendingdoom.core.DoomEvent;
import com.impendingdoom.core.EventScheduler;
import com.impendingdoom.util.EventUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.player.CanPlayerSleepEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.List;

/**
 * Blood Moon: every night the moon is red (client tint) and more hostile mobs spawn with potion effects. Zombies
 * often carry tools and slowly break blocks their tool is good at. Hunting creepers explode as soon as they lose
 * their path to the player. Sleeping requires a block of wool over your head (which blacks out your sight).
 */
@EventBusSubscriber(modid = ImpendingDoom.MODID)
public class BloodMoonEvent extends DoomEvent {
    public static final String ID = "blood_moon";
    private static final String DIG = "impendingdoom_dig";
    private static final List<Holder<MobEffect>> BUFFS = List.of(MobEffects.SPEED, MobEffects.STRENGTH, MobEffects.RESISTANCE,
            MobEffects.FIRE_RESISTANCE, MobEffects.JUMP_BOOST, MobEffects.INVISIBILITY);
    private static final EntityType<?>[] EXTRA = {EntityTypes.ZOMBIE, EntityTypes.ZOMBIE, EntityTypes.SKELETON, EntityTypes.SPIDER,
            EntityTypes.CREEPER, EntityTypes.HUSK, EntityTypes.WITCH};

    public BloodMoonEvent() {
        super(ID, false, ChatFormatting.DARK_GREEN);
    }

    public static boolean bloodNight(ServerLevel level) {
        return EventScheduler.isActive(ID) && DayClock.isNight(level);
    }

    @Override
    public void onTick(ServerLevel level, CompoundTag data) {
        if (!DayClock.isNight(level) || level.getGameTime() % 80 != 0) return;
        for (ServerPlayer player : EventUtil.targets(level)) {
            long nearby = level.getEntitiesOfClass(Monster.class, player.getBoundingBox().inflate(64)).size();
            if (nearby > 45) continue;
            for (int i = 0; i < 1 + level.getRandom().nextInt(3); i++) {
                BlockPos pos = EventUtil.surfacePos(level, player.blockPosition(), 24, 56, level.getRandom());
                if (pos == null || level.getBrightness(LightLayer.BLOCK, pos) > 0) continue;
                Mob mob = (Mob) EventUtil.spawn(level, EXTRA[level.getRandom().nextInt(EXTRA.length)], pos);
                if (mob != null) empower(mob);
            }
        }
    }

    private static void empower(Mob mob) {
        if (mob.entityTags().contains("impendingdoom.blood")) return;
        mob.addTag("impendingdoom.blood");
        Holder<MobEffect> effect = BUFFS.get(mob.getRandom().nextInt(BUFFS.size()));
        mob.addEffect(new MobEffectInstance(effect, 24000, mob.getRandom().nextInt(2)));
        if (mob instanceof Zombie zombie && mob.getRandom().nextFloat() < 0.45F && zombie.getMainHandItem().isEmpty()) {
            ItemStack[] tools = {new ItemStack(Items.IRON_SHOVEL), new ItemStack(Items.IRON_PICKAXE), new ItemStack(Items.IRON_AXE),
                    new ItemStack(Items.STONE_PICKAXE), new ItemStack(Items.STONE_SHOVEL), new ItemStack(Items.STONE_AXE)};
            zombie.setItemSlot(EquipmentSlot.MAINHAND, tools[mob.getRandom().nextInt(tools.length)]);
        }
    }

    @SubscribeEvent
    public static void onFinalize(FinalizeSpawnEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !bloodNight(level)) return;
        if (event.getSpawnType() != EntitySpawnReason.NATURAL) return;
        Mob mob = event.getEntity();
        if (!(mob instanceof Monster)) return;
        empower(mob);
    }

    @SubscribeEvent
    public static void onMobTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof Mob mob) || !(mob.level() instanceof ServerLevel level) || mob.tickCount % 5 != 0) return;
        if (!bloodNight(level)) return;
        LivingEntity target = mob.getTarget();
        if (!(target instanceof Player)) return;
        if (mob instanceof Creeper creeper) {
            if (mob.tickCount % 20 == 0 && creeper.distanceToSqr(target) < 32 * 32) {
                Path path = creeper.getNavigation().createPath(target, 0);
                if (path == null || !path.canReach()) creeper.ignite();
            }
        } else if (mob instanceof Zombie zombie && !zombie.getMainHandItem().isEmpty()) {
            dig(level, zombie, target);
        }
    }

    /** Zombies holding a tool slowly break the blocks that tool is suited for, when blocked from their target. */
    private static void dig(ServerLevel level, Zombie zombie, LivingEntity target) {
        if (!EventUtil.canDestroyBlocks(level) || zombie.distanceToSqr(target) < 2.5) return;
        boolean blocked = zombie.getNavigation().isDone() || zombie.horizontalCollision;
        if (!blocked) {
            zombie.getPersistentData().remove(DIG);
            return;
        }
        Vec3 dir = target.position().subtract(zombie.position());
        Direction facing = Direction.getApproximateNearest(dir.x, 0, dir.z);
        BlockPos base = zombie.blockPosition().relative(facing);
        ItemStack tool = zombie.getMainHandItem();
        for (BlockPos pos : new BlockPos[]{base.above(), base}) {
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || !state.getFluidState().isEmpty()) continue;
            float hardness = state.getDestroySpeed(level, pos);
            if (hardness < 0 || hardness > 20 || tool.getDestroySpeed(state) <= 1.0F) continue;
            CompoundTag dig = zombie.getPersistentData().getCompoundOrEmpty(DIG);
            if (dig.getLongOr("pos", 0L) != pos.asLong()) {
                dig = new CompoundTag();
                dig.putLong("pos", pos.asLong());
            }
            float progress = dig.getFloatOr("progress", 0) + tool.getDestroySpeed(state) / (hardness * 30.0F) * 5.0F;
            dig.putFloat("progress", progress);
            zombie.getPersistentData().put(DIG, dig);
            zombie.swingForAttack(net.minecraft.world.InteractionHand.MAIN_HAND);
            if (progress >= 1.0F) {
                level.destroyBlockProgress(zombie.getId(), pos, -1);
                level.destroyBlock(pos, true, zombie);
                zombie.getPersistentData().remove(DIG);
            } else {
                level.destroyBlockProgress(zombie.getId(), pos, (int) (progress * 10));
            }
            return;
        }
    }

    /** Nobody sleeps through a Blood Moon or an Electrical Storm unless they wear wool over their eyes. */
    @SubscribeEvent
    public static void canSleep(CanPlayerSleepEvent event) {
        if (!EventScheduler.isActive(ID) && !EventScheduler.isActive(ElectricalStormEvent.ID)) return;
        if (!event.getEntity().getItemBySlot(EquipmentSlot.HEAD).is(ItemTags.WOOL)) {
            event.setProblem(Player.BedSleepingProblem.OTHER_PROBLEM);
        }
    }
}
