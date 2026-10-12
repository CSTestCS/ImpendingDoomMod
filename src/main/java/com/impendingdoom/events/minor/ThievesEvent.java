package com.impendingdoom.events.minor;

import com.impendingdoom.ImpendingDoom;
import com.impendingdoom.core.DayClock;
import com.impendingdoom.core.DoomEvent;
import com.impendingdoom.core.EventScheduler;
import com.impendingdoom.entity.ai.ThiefGoal;
import com.impendingdoom.util.EventUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.monster.illager.Vindicator;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

/**
 * Thieves: at night thieves spawn and go for the players' chests. See {@link ThiefGoal} for their behaviour.
 * Thieves are illagers, so iron golems attack them on sight.
 */
@EventBusSubscriber(modid = ImpendingDoom.MODID)
public class ThievesEvent extends DoomEvent {
    public static final String ID = "thieves";
    public static final String TAG = "impendingdoom.thief";

    public ThievesEvent() {
        super(ID, false, ChatFormatting.DARK_GREEN);
    }

    @Override
    public void onTick(ServerLevel level, CompoundTag data) {
        if (!DayClock.isNight(level) || level.getGameTime() % 600 != 0) return;
        for (ServerPlayer player : EventUtil.targets(level)) {
            if (level.getRandom().nextInt(3) != 0) continue;
            int existing = level.getEntitiesOfClass(Vindicator.class, player.getBoundingBox().inflate(96), v -> v.entityTags().contains(TAG)).size();
            if (existing >= 4) continue;
            BlockPos pos = EventUtil.surfacePos(level, player.blockPosition(), 32, 48, level.getRandom());
            if (pos == null) continue;
            Vindicator thief = EventUtil.spawn(level, EntityTypes.VINDICATOR, pos);
            if (thief == null) continue;
            makeThief(thief);
        }
    }

    public static void makeThief(Vindicator thief) {
        thief.addTag(TAG);
        thief.setCustomName(Component.translatable("entity.impendingdoom.thief"));
        thief.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        setupGoals(thief);
    }

    private static void setupGoals(Vindicator thief) {
        thief.goalSelector.removeAllGoals(g -> true);
        thief.targetSelector.removeAllGoals(g -> true);
        thief.goalSelector.addGoal(0, new FloatGoal(thief));
        thief.goalSelector.addGoal(1, new MeleeAttackGoal(thief, 1.1, false));
        thief.goalSelector.addGoal(2, new ThiefGoal(thief));
        thief.targetSelector.addGoal(1, new HurtByTargetGoal(thief));
        // Once hidden it fights anything that comes close: players and golems.
        thief.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(thief, Player.class, 10, true, false,
                (target, level) -> ThiefGoal.isHiding(thief) && target.distanceToSqr(thief) < 36));
        thief.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(thief, IronGolem.class, 10, true, false,
                (target, level) -> ThiefGoal.isHiding(thief) && target.distanceToSqr(thief) < 36));
    }

    /** Goals are not saved; restore them when a thief is loaded from disk. */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !event.loadedFromDisk()) return;
        if (event.getEntity() instanceof Vindicator v && v.entityTags().contains(TAG)) setupGoals(v);
    }

    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        if (event.getEntity() instanceof Vindicator v && v.entityTags().contains(TAG)) {
            for (ItemStack stack : ThiefGoal.takeLoot(v)) {
                event.getDrops().add(new net.minecraft.world.entity.item.ItemEntity(v.level(), v.getX(), v.getY(), v.getZ(), stack));
            }
        }
    }

    @Override
    public void onEnd(ServerLevel level, CompoundTag data) {
        // Thieves that have not stolen anything give up and leave.
        for (ServerPlayer player : level.players()) {
            for (Vindicator v : level.getEntitiesOfClass(Vindicator.class, new AABB(player.blockPosition()).inflate(128),
                    v -> v.entityTags().contains(TAG) && !ThiefGoal.hasLoot(v))) {
                v.discard();
            }
        }
    }
}
