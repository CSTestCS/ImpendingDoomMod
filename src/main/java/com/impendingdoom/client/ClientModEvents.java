package com.impendingdoom.client;

import com.google.common.reflect.TypeToken;
import com.impendingdoom.ImpendingDoom;
import com.impendingdoom.core.ActiveEvents;
import com.impendingdoom.events.major.FallEvent;
import com.impendingdoom.events.major.SummerEvent;
import com.impendingdoom.network.EntityMarkPayload;
import com.impendingdoom.registry.ModBlocks;
import com.impendingdoom.registry.ModEntities;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

import java.lang.reflect.Field;
import java.util.List;

@EventBusSubscriber(modid = ImpendingDoom.MODID, value = Dist.CLIENT)
public final class ClientModEvents {
    private static final int[] AUTUMN = {0xFFC0392B, 0xFFD35400, 0xFFE67E22, 0xFFF1C40F, 0xFFA93226, 0xFFF39C12};

    private ClientModEvents() {}

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.TORNADO.get(), NoopRenderer::new);
        event.registerEntityRenderer(ModEntities.METEOR.get(), NoopRenderer::new);
        event.registerEntityRenderer(ModEntities.NETHERFLAME_BOTTLE.get(), ThrownItemRenderer::new);
        event.registerEntityRenderer(ModEntities.HEART_WALKER.get(), HeartWalkerRenderer::new);
        event.registerEntityRenderer(ModEntities.HEROBRINE.get(), HerobrineRenderer::new);
    }

    @SubscribeEvent
    public static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(HeartWalkerModel.LAYER, HeartWalkerModel::createBodyLayer);
    }

    /** Adds the refit armor layer to every living entity renderer that does not already draw armor. */
    @SubscribeEvent
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void addLayers(EntityRenderersEvent.AddLayers event) {
        for (EntityType<?> type : event.getEntityTypes()) {
            EntityRenderer<?, ?> renderer = event.getRenderer(type);
            if (renderer instanceof LivingEntityRenderer living && !rendersArmor(living)) {
                living.addLayer(new RefitArmorLayer(living, event.getContext()));
            }
        }
        // Invading ghasts wear happy ghast harnesses (visual only).
        if (event.getRenderer(EntityTypes.GHAST) instanceof LivingEntityRenderer living) {
            living.addLayer(new GhastHarnessLayer(living, event.getContext()));
        }
    }

    private static boolean rendersArmor(LivingEntityRenderer<?, ?, ?> renderer) {
        try {
            Field field = LivingEntityRenderer.class.getDeclaredField("layers");
            field.setAccessible(true);
            for (Object layer : (List<?>) field.get(renderer)) {
                if (layer instanceof HumanoidArmorLayer<?, ?, ?>) return true;
            }
            return false;
        } catch (ReflectiveOperationException e) {
            return renderer instanceof net.minecraft.client.renderer.entity.HumanoidMobRenderer<?, ?, ?>;
        }
    }

    /** Copies worn armor and the shaking flag into render states. */
    @SubscribeEvent
    public static void renderStateModifiers(RegisterRenderStateModifiersEvent event) {
        event.registerEntityModifier(new TypeToken<LivingEntityRenderer<LivingEntity, LivingEntityRenderState, ?>>() {}, (entity, state) -> {
            if (entity instanceof Mob) {
                ItemStack head = entity.getItemBySlot(EquipmentSlot.HEAD);
                ItemStack chest = entity.getItemBySlot(EquipmentSlot.CHEST);
                ItemStack legs = entity.getItemBySlot(EquipmentSlot.LEGS);
                ItemStack feet = entity.getItemBySlot(EquipmentSlot.FEET);
                if (!head.isEmpty() || !chest.isEmpty() || !legs.isEmpty() || !feet.isEmpty()) {
                    state.setRenderData(ArmorRenderData.ARMOR, new ItemStack[]{head, chest, legs, feet});
                } else {
                    state.setRenderData(ArmorRenderData.ARMOR, null);
                }
            }
            if (entity instanceof net.minecraft.world.entity.monster.Ghast) {
                ItemStack body = entity.getItemBySlot(EquipmentSlot.BODY);
                state.setRenderData(ArmorRenderData.HARNESS, body.isEmpty() ? null : body);
            }
            int flags = ClientDoomState.MARKS.getOrDefault(entity.getId(), 0);
            state.setRenderData(ArmorRenderData.SHAKING, (flags & EntityMarkPayload.SHAKING) != 0 ? Boolean.TRUE : null);
        });
    }

    /** Fall leaves: red, orange and yellow. */
    @SubscribeEvent
    public static void blockColors(RegisterColorHandlersEvent.BlockTintSources event) {
        event.register(List.of(new LeafTint(0xFF48B518, true)),
                Blocks.OAK_LEAVES, Blocks.DARK_OAK_LEAVES, Blocks.JUNGLE_LEAVES, Blocks.ACACIA_LEAVES, Blocks.MANGROVE_LEAVES, Blocks.VINE);
        event.register(List.of(new LeafTint(0xFF80A755, false)), Blocks.BIRCH_LEAVES);
        event.register(List.of(new AutumnTint()), ModBlocks.FALLEN_LEAVES.get(), ModBlocks.LEAF_PILE.get());
    }

    /** Biome foliage color normally (or a fixed color), autumn colors during Fall. */
    private record LeafTint(int fallback, boolean biome) implements BlockTintSource {
        @Override
        public int color(BlockState state) {
            return fallback;
        }

        @Override
        public int colorInWorld(BlockState state, BlockAndTintGetter level, BlockPos pos) {
            if (ActiveEvents.clientActive(FallEvent.ID)) return autumn(pos);
            return biome ? BiomeColors.getAverageFoliageColor(level, pos) : fallback;
        }
    }

    private record AutumnTint() implements BlockTintSource {
        @Override
        public int color(BlockState state) {
            return AUTUMN[1];
        }

        @Override
        public int colorInWorld(BlockState state, BlockAndTintGetter level, BlockPos pos) {
            return autumn(pos);
        }
    }

    private static int autumn(BlockPos pos) {
        if (pos == null) return AUTUMN[1];
        long h = pos.asLong() * 0x9E3779B97F4A7C15L;
        return AUTUMN[(int) ((h >>> 33) % AUTUMN.length)];
    }

    @SubscribeEvent
    public static void guiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.FOOD_LEVEL, ImpendingDoom.id("thirst"), ThirstHud::render);
    }

    static boolean summer() {
        return ActiveEvents.clientActive(SummerEvent.ID);
    }
}
