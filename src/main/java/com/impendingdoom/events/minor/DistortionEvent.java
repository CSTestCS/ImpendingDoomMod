package com.impendingdoom.events.minor;

import com.impendingdoom.ImpendingDoom;
import com.impendingdoom.core.DoomEvent;
import com.impendingdoom.core.EventScheduler;
import com.impendingdoom.registry.ModTags;
import com.impendingdoom.util.GearUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Distortion: until the next event, any armor that loses 25% of its max durability gains the Warped enchantment,
 * which makes it unwearable. A worn piece pops off into the inventory (or onto the ground) with a break sound.
 * Warped persists after the event until the item is repaired or disenchanted on a grindstone.
 */
@EventBusSubscriber(modid = ImpendingDoom.MODID)
public class DistortionEvent extends DoomEvent {
    public static final String ID = "distortion";
    private static final String KEY = "impendingdoom_distortion";

    public DistortionEvent() {
        super(ID, false, ChatFormatting.DARK_GREEN);
    }

    private static Holder<Enchantment> warped(Player player) {
        return player.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(ModTags.WARPED);
    }

    public static boolean isWarped(Player player, ItemStack stack) {
        return !stack.isEmpty() && EnchantmentHelper.getItemEnchantmentLevel(warped(player), stack) > 0;
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 10 != 0) return;
        if (EventScheduler.isActive(ID)) trackArmorWear(player);
        if (player.tickCount % 20 == 0) checkRepairs(player);
    }

    /** Accumulates the durability each worn piece loses during the event. */
    private static void trackArmorWear(ServerPlayer player) {
        for (EquipmentSlot slot : GearUtil.ARMOR_SLOTS) {
            ItemStack stack = player.getItemBySlot(slot);
            if (stack.isEmpty() || !stack.isDamageableItem() || isWarped(player, stack)) continue;
            CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getCompoundOrEmpty(KEY);
            int damage = stack.getDamageValue();
            int last = tag.contains("last") ? tag.getIntOr("last", damage) : damage;
            int lost = tag.getIntOr("lost", 0) + Math.max(0, damage - last);
            if (lost >= stack.getMaxDamage() * 0.25) {
                warp(player, slot, stack);
                continue;
            }
            if (last != damage || !tag.contains("last")) {
                int finalLost = lost;
                CustomData.update(DataComponents.CUSTOM_DATA, stack, t -> {
                    CompoundTag d = new CompoundTag();
                    d.putInt("last", damage);
                    d.putInt("lost", finalLost);
                    t.put(KEY, d);
                });
            }
        }
    }

    private static void warp(ServerPlayer player, EquipmentSlot slot, ItemStack stack) {
        ItemStack warpedStack = stack.copy();
        warpedStack.enchant(warped(player), 1);
        int damageAtWarp = warpedStack.getDamageValue();
        CustomData.update(DataComponents.CUSTOM_DATA, warpedStack, t -> {
            t.remove(KEY);
            t.putInt("impendingdoom_warp_damage", damageAtWarp);
        });
        player.setItemSlot(slot, ItemStack.EMPTY);
        if (!player.getInventory().add(warpedStack)) EventUtil.dropNear(player, warpedStack);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 1.0F, 0.8F);
        player.sendSystemMessage(Component.translatable("message.impendingdoom.armor_warped", warpedStack.getHoverName()).withStyle(ChatFormatting.DARK_PURPLE), true);
    }

    /** Warped is removed when the item is repaired (anvil, crafting, mending...). */
    private static void checkRepairs(ServerPlayer player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.isEmpty() || !isWarped(player, stack)) continue;
            CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
            int warpDamage = tag.getIntOr("impendingdoom_warp_damage", 0);
            if (stack.getDamageValue() < warpDamage) {
                Holder<Enchantment> warped = warped(player);
                EnchantmentHelper.updateEnchantments(stack, mutable -> mutable.removeIf(h -> h.equals(warped)));
                CustomData.update(DataComponents.CUSTOM_DATA, stack, t -> t.remove("impendingdoom_warp_damage"));
            }
        }
    }

    /** Warped armor cannot be worn: it is pushed back into the inventory. */
    @SubscribeEvent
    public static void onEquip(LivingEquipmentChangeEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getSlot().getType() != EquipmentSlot.Type.HUMANOID_ARMOR) return;
        ItemStack to = event.getTo();
        if (!isWarped(player, to)) return;
        EquipmentSlot slot = event.getSlot();
        com.impendingdoom.util.EventUtil.defer(() -> {
            ItemStack worn = player.getItemBySlot(slot);
            if (!isWarped(player, worn)) return;
            player.setItemSlot(slot, ItemStack.EMPTY);
            if (!player.getInventory().add(worn)) EventUtil.dropNear(player, worn);
            player.sendSystemMessage(Component.translatable("message.impendingdoom.armor_unwearable").withStyle(ChatFormatting.DARK_PURPLE), true);
        });
    }
}
