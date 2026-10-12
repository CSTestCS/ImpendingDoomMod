package com.impendingdoom.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.animal.ghast.HappyGhastHarnessModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.HappyGhastRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;

/**
 * Draws a happy ghast harness on a regular ghast. Purely visual: the Nether Invasion puts a harness in the ghast's
 * body slot so piglin riders look saddled. Ghasts and happy ghasts share the same 16px body cube, so the happy ghast
 * harness model fits the ghast without adjustment.
 */
public class GhastHarnessLayer<S extends LivingEntityRenderState, M extends EntityModel<? super S>> extends RenderLayer<S, M> {
    private final HappyGhastHarnessModel model;
    private final EquipmentLayerRenderer equipmentRenderer;
    private final HappyGhastRenderState harnessState = new HappyGhastRenderState();

    public GhastHarnessLayer(RenderLayerParent<S, M> parent, EntityRendererProvider.Context context) {
        super(parent);
        this.model = new HappyGhastHarnessModel(context.bakeLayer(ModelLayers.HAPPY_GHAST_HARNESS));
        this.equipmentRenderer = context.getEquipmentRenderer();
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, S state, float yRot, float xRot) {
        ItemStack harness = state.getRenderData(ArmorRenderData.HARNESS);
        if (harness == null || harness.isEmpty()) return;
        Equippable equippable = harness.get(DataComponents.EQUIPPABLE);
        if (equippable == null || equippable.assetId().isEmpty()) return;
        harnessState.ageInTicks = state.ageInTicks;
        harnessState.outlineColor = state.outlineColor;
        equipmentRenderer.renderLayers(EquipmentClientInfo.LayerType.HAPPY_GHAST_BODY, equippable.assetId().get(), model, harnessState,
                harness, poseStack, collector, light, state.outlineColor);
    }
}
