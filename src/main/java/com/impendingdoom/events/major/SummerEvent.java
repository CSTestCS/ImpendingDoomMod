package com.impendingdoom.events.major;

import com.impendingdoom.util.EventUtil;

import com.impendingdoom.ImpendingDoom;
import com.impendingdoom.core.DayClock;
import com.impendingdoom.core.DoomEvent;
import com.impendingdoom.core.EventScheduler;
import com.impendingdoom.registry.ModTags;
import com.impendingdoom.util.GearUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Summer (season 1/4): heavy armor can only be worn for a short time during the day before the wearer overheats and
 * tears it off (no limit at night). Players must also manage thirst: drink bottles, milk, honey, soups and juicy
 * fruit, sneak while looking at water with an empty hand, or look up at the rain while sneaking.
 */
@EventBusSubscriber(modid = ImpendingDoom.MODID)
public class SummerEvent extends DoomEvent {
    public static final String ID = "summer";
    public static final int MAX_THIRST = 20;
    private static final int MAX_HEAT = 2400;
    private static final String THIRST = "impendingdoom_thirst";
    private static final String THIRST_EX = "impendingdoom_thirst_ex";
    private static final String HEAT = "impendingdoom_heat";

    public SummerEvent() {
        super(ID, true, ChatFormatting.GOLD);
    }

    @Override
    public boolean isSeason() {
        return true;
    }

    public static int thirst(Player player) {
        CompoundTag tag = player.getPersistentData();
        return tag.contains(THIRST) ? tag.getIntOr(THIRST, MAX_THIRST) : MAX_THIRST;
    }

    public static void setThirst(Player player, int value) {
        player.getPersistentData().putInt(THIRST, Math.max(0, Math.min(MAX_THIRST, value)));
    }

    public static void drink(Player player, int amount) {
        setThirst(player, thirst(player) + amount);
        player.getPersistentData().putFloat(THIRST_EX, 0);
    }

    @Override
    public void onEnd(ServerLevel level, CompoundTag data) {
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            setThirst(player, MAX_THIRST);
            player.getPersistentData().putInt(HEAT, 0);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !EventScheduler.isActive(ID)) return;
        if (player.isCreative() || player.isSpectator()) return;
        ServerLevel level = (ServerLevel) player.level();
        tickHeat(level, player);
        tickThirst(level, player);
    }

    private static int heavyPieces(Player player) {
        int n = 0;
        for (EquipmentSlot slot : GearUtil.ARMOR_SLOTS) if (player.getItemBySlot(slot).is(ModTags.HEAVY_ARMOR)) n++;
        return n;
    }

    private static void tickHeat(ServerLevel level, ServerPlayer player) {
        CompoundTag tag = player.getPersistentData();
        int heat = tag.getIntOr(HEAT, 0);
        int heavy = heavyPieces(player);
        boolean day = DayClock.isDay(level) && level.dimensionType().hasSkyLight();
        if (heavy > 0 && day) {
            heat += heavy + (level.canSeeSky(player.blockPosition().above()) ? 1 : 0);
        } else {
            heat = Math.max(0, heat - 6);
        }
        if (heat >= MAX_HEAT) {
            for (EquipmentSlot slot : GearUtil.ARMOR_SLOTS) {
                ItemStack stack = player.getItemBySlot(slot);
                if (!stack.is(ModTags.HEAVY_ARMOR)) continue;
                player.setItemSlot(slot, ItemStack.EMPTY);
                if (!player.getInventory().add(stack)) EventUtil.dropNear(player, stack);
            }
            player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 200, 1));
            player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 160, 0));
            level.playSound(null, player.blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.8F, 0.6F);
            heat = MAX_HEAT / 2;
        }
        tag.putInt(HEAT, heat);
    }

    private static void tickThirst(ServerLevel level, ServerPlayer player) {
        CompoundTag tag = player.getPersistentData();
        float ex = tag.getFloatOr(THIRST_EX, 0);
        ex += 0.0035F;
        if (player.isSprinting()) ex += 0.03F;
        if (DayClock.isDay(level) && level.canSeeSky(player.blockPosition().above())) ex += 0.004F;
        if (level.getBiome(player.blockPosition()).value().getBaseTemperature() > 1.0F) ex += 0.004F;
        int thirst = thirst(player);
        if (ex >= 4.0F) {
            ex -= 4.0F;
            thirst = Math.max(0, thirst - 1);
            setThirst(player, thirst);
        }
        tag.putFloat(THIRST_EX, ex);

        if (thirst <= 0 && player.tickCount % 80 == 0) {
            player.hurtServer(level, level.damageSources().dryOut(), 1.0F);
        }
        if (thirst <= 4 && player.tickCount % 40 == 0) {
            player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 0, true, false));
        }
        // Drink from water or rain with an empty hand while sneaking.
        if (player.isShiftKeyDown() && player.getMainHandItem().isEmpty() && player.tickCount % 20 == 0 && thirst < MAX_THIRST) {
            HitResult hit = player.pick(4.5, 1.0F, true);
            boolean water = hit instanceof BlockHitResult bhr && hit.getType() == HitResult.Type.BLOCK
                    && level.getFluidState(bhr.getBlockPos()).is(FluidTags.WATER);
            BlockPos head = player.blockPosition().above();
            boolean rain = player.getXRot() < -60 && level.isRainingAt(head.above());
            if (water || (rain && player.tickCount % 40 == 0)) {
                drink(player, 1);
                level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GENERIC_DRINK, SoundSource.PLAYERS, 0.5F, 1.0F);
            }
        }
    }

    @SubscribeEvent
    public static void onConsume(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide() || !EventScheduler.isActive(ID)) return;
        ItemStack stack = event.getItem();
        int amount = 0;
        if (stack.is(Items.POTION)) amount = 7;
        else if (stack.is(Items.MILK_BUCKET)) amount = 5;
        else if (stack.is(Items.HONEY_BOTTLE)) amount = 3;
        else if (stack.is(Items.MUSHROOM_STEW) || stack.is(Items.BEETROOT_SOUP) || stack.is(Items.RABBIT_STEW) || stack.is(Items.SUSPICIOUS_STEW)) amount = 4;
        else if (stack.is(Items.MELON_SLICE)) amount = 3;
        else if (stack.is(Items.SWEET_BERRIES) || stack.is(Items.GLOW_BERRIES) || stack.is(Items.APPLE) || stack.is(Items.CHORUS_FRUIT)) amount = 1;
        if (amount > 0) drink(player, amount);
    }
}
