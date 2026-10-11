package com.impendingdoom.events.minor;

import com.impendingdoom.ImpendingDoom;
import com.impendingdoom.core.DoomEvent;
import com.impendingdoom.core.EventScheduler;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BonemealableBlock;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.block.CropGrowEvent;

import java.util.Iterator;

/** Famine: food feeds 50% less, crops cannot grow, animals drop only one food item. */
@EventBusSubscriber(modid = ImpendingDoom.MODID)
public class FamineEvent extends DoomEvent {
    public static final String ID = "famine";

    public FamineEvent() {
        super(ID, false, ChatFormatting.DARK_GREEN);
    }

    @SubscribeEvent
    public static void onEat(LivingEntityUseItemEvent.Finish event) {
        if (!EventScheduler.isActive(ID) || !(event.getEntity() instanceof Player player) || player.level().isClientSide()) return;
        FoodProperties food = event.getItem().get(DataComponents.FOOD);
        if (food == null) return;
        FoodData data = player.getFoodData();
        data.setFoodLevel(Math.max(0, data.getFoodLevel() - food.nutrition() / 2));
        data.setSaturation(Math.max(0.0F, data.getSaturationLevel() - food.saturation() / 2.0F));
    }

    @SubscribeEvent
    public static void onCropGrow(CropGrowEvent.Pre event) {
        if (EventScheduler.isActive(ID)) event.setResult(CropGrowEvent.Pre.Result.DO_NOT_GROW);
    }

    @SubscribeEvent
    public static void onBonemeal(PlayerInteractEvent.RightClickBlock event) {
        if (!EventScheduler.isActive(ID)) return;
        ItemStack stack = event.getItemStack();
        if (stack.is(Items.BONE_MEAL) && event.getLevel().getBlockState(event.getPos()).getBlock() instanceof BonemealableBlock) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        if (!EventScheduler.isActive(ID) || !(event.getEntity() instanceof Animal)) return;
        boolean keptFood = false;
        Iterator<ItemEntity> it = event.getDrops().iterator();
        while (it.hasNext()) {
            ItemEntity drop = it.next();
            if (!drop.getItem().has(DataComponents.FOOD)) continue;
            if (keptFood) {
                it.remove();
            } else {
                drop.setItem(drop.getItem().copyWithCount(1));
                keptFood = true;
            }
        }
    }
}
