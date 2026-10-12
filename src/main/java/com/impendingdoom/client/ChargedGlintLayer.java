package com.impendingdoom.client;

import com.impendingdoom.ImpendingDoom;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

/** A light blue, slowly scrolling glint drawn over the whole model of a mob charged during an Electrical Storm. */
public class ChargedGlintLayer<S extends LivingEntityRenderState, M extends EntityModel<? super S>> extends RenderLayer<S, M> {
    private static final Identifier TEXTURE = ImpendingDoom.id("textures/entity/charged_glint.png");

    public ChargedGlintLayer(RenderLayerParent<S, M> parent) {
        super(parent);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, S state, float yRot, float xRot) {
        Boolean charged = state.getRenderData(ArmorRenderData.CHARGED);
        if (charged == null || !charged) return;
        float t = state.ageInTicks * 0.008F;
        collector.submitModel(getParentModel(), state, poseStack, RenderTypes.energySwirl(TEXTURE, t % 1.0F, (t * 0.7F) % 1.0F),
                light, OverlayTexture.NO_OVERLAY, state.outlineColor);
    }
}
