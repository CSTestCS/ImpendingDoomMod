package com.impendingdoom.util;

import net.minecraft.core.component.DataComponents;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.equipment.Equippable;

/** Equipment helpers. Any mob can be given armor; it is rendered by the client "refit" armor layer. */
public final class GearUtil {
    public static final EquipmentSlot[] ARMOR_SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private static final Item[][] ARMOR_SETS = {
            {Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS},
            {Items.CHAINMAIL_HELMET, Items.CHAINMAIL_CHESTPLATE, Items.CHAINMAIL_LEGGINGS, Items.CHAINMAIL_BOOTS},
            {Items.GOLDEN_HELMET, Items.GOLDEN_CHESTPLATE, Items.GOLDEN_LEGGINGS, Items.GOLDEN_BOOTS},
            {Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS},
            {Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS},
            {Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS},
    };
    public static final Item[][] PIGLIN_SETS = {ARMOR_SETS[2], ARMOR_SETS[5]};

    private static final Item[] WEAPONS = {
            Items.STONE_SWORD, Items.IRON_SWORD, Items.DIAMOND_SWORD, Items.NETHERITE_SWORD,
            Items.STONE_AXE, Items.IRON_AXE, Items.DIAMOND_AXE, Items.NETHERITE_AXE,
            Items.GOLDEN_SWORD, Items.GOLDEN_AXE, Items.TRIDENT, Items.MACE, Items.BOW, Items.CROSSBOW,
            Items.IRON_SPEAR, Items.DIAMOND_SPEAR
    };

    private GearUtil() {}

    /** Gives a random armor set. Tier is 0 (leather) .. 5 (netherite); pieces are mixed around it. */
    public static void equipArmor(Mob mob, RandomSource random, int minTier, int maxTier, float pieceChance) {
        for (int i = 0; i < 4; i++) {
            if (random.nextFloat() > pieceChance) continue;
            int tier = minTier + random.nextInt(Math.max(1, maxTier - minTier + 1));
            equip(mob, ARMOR_SLOTS[i], new ItemStack(ARMOR_SETS[Math.min(tier, 5)][i]), 0.085F);
        }
    }

    public static void equipArmorFrom(Mob mob, RandomSource random, Item[][] sets, float pieceChance) {
        for (int i = 0; i < 4; i++) {
            if (random.nextFloat() > pieceChance) continue;
            equip(mob, ARMOR_SLOTS[i], new ItemStack(sets[random.nextInt(sets.length)][i]), 0.085F);
        }
    }

    public static ItemStack randomWeapon(RandomSource random) {
        return new ItemStack(WEAPONS[random.nextInt(WEAPONS.length)]);
    }

    public static ItemStack randomMeleeWeapon(RandomSource random) {
        Item[] melee = {Items.IRON_SWORD, Items.DIAMOND_SWORD, Items.IRON_AXE, Items.DIAMOND_AXE, Items.STONE_SWORD, Items.NETHERITE_SWORD};
        return new ItemStack(melee[random.nextInt(melee.length)]);
    }

    public static void equip(Mob mob, EquipmentSlot slot, ItemStack stack, float dropChance) {
        mob.setItemSlot(slot, stack);
        mob.setDropChance(slot, dropChance);
    }

    public static ItemStack unbreakable(ItemStack stack) {
        stack.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
        return stack;
    }

    public static EquipmentSlot armorSlot(ItemStack stack) {
        Equippable eq = stack.get(DataComponents.EQUIPPABLE);
        if (eq == null) return null;
        EquipmentSlot slot = eq.slot();
        return slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR ? slot : null;
    }

    /** Sum of the armor value an item grants. */
    public static double armorValue(ItemStack stack) {
        return attributeSum(stack, Attributes.ARMOR.value()) + attributeSum(stack, Attributes.ARMOR_TOUGHNESS.value()) * 0.5;
    }

    /** Sum of flat attack damage an item grants, used to find the strongest weapon. */
    public static double attackValue(ItemStack stack) {
        return attributeSum(stack, Attributes.ATTACK_DAMAGE.value());
    }

    private static double attributeSum(ItemStack stack, net.minecraft.world.entity.ai.attributes.Attribute attribute) {
        ItemAttributeModifiers mods = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        double sum = 0;
        for (ItemAttributeModifiers.Entry entry : mods.modifiers()) {
            if (entry.attribute().value() == attribute
                    && entry.modifier().operation() == net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE) {
                sum += entry.modifier().amount();
            }
        }
        return sum;
    }
}
