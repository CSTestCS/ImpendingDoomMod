package com.impendingdoom.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Wool can be worn on the head, but shift-clicking it must not send it to the helmet slot. */
@Mixin(InventoryMenu.class)
public abstract class InventoryMenuMixin {
    @WrapOperation(method = "quickMoveStack", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Player;getEquipmentSlotForItem(Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/entity/EquipmentSlot;"))
    private EquipmentSlot impendingdoom$noWoolQuickEquip(Player player, ItemStack stack, Operation<EquipmentSlot> original) {
        if (stack.is(ItemTags.WOOL)) return EquipmentSlot.MAINHAND;
        return original.call(player, stack);
    }
}
