package com.impendingdoom.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;

/**
 * Renders player armor on mobs whose models do not normally render armor (spiders, creepers, illagers, endermen,
 * modded mobs, ...).
 * <p>
 * The armor is the regular player armor model. It is <b>not</b> wrapped onto the mob's own model parts: instead it is
 * inflated with the same proportions it has on a player. A player is 0.6 wide and 1.8 tall, so a mob that is twice as
 * tall as a player gets armor twice as tall, a mob that is twice as wide gets armor twice as wide, and the pieces sit
 * where they would on a player of that size, standing on the mob's feet.
 */
public class RefitArmorLayer<S extends LivingEntityRenderState, M extends net.minecraft.client.model.EntityModel<? super S>> extends RenderLayer<S, M> {
    private static final float PLAYER_WIDTH = 0.6F;
    private static final float PLAYER_HEIGHT = 1.8F;
    /** Keeps very flat or very thin mobs from getting paper-thin armor. */
    private static final float MAX_ASPECT = 2.5F;

    private final ArmorModelSet<HumanoidModel<HumanoidRenderState>> models;
    private final EquipmentLayerRenderer equipmentRenderer;
    private final HumanoidRenderState pose = new HumanoidRenderState();

    public RefitArmorLayer(RenderLayerParent<S, M> parent, EntityRendererProvider.Context context) {
        super(parent);
        this.models = ArmorModelSet.bake(ModelLayers.PLAYER_ARMOR, context.getModelSet(), HumanoidModel::new);
        this.equipmentRenderer = context.getEquipmentRenderer();
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, S state, float yRot, float xRot) {
        ItemStack[] armor = state.getRenderData(ArmorRenderData.ARMOR);
        if (armor == null) return;

        float scale = state.scale <= 0 ? 1.0F : state.scale;
        float width = state.boundingBoxWidth / scale;
        float height = state.boundingBoxHeight / scale;
        float sx = width / PLAYER_WIDTH;
        float sy = height / PLAYER_HEIGHT;
        if (sx > sy * MAX_ASPECT) sx = sy * MAX_ASPECT;
        if (sy > sx * MAX_ASPECT) sy = sx * MAX_ASPECT;

        pose.walkAnimationPos = state.walkAnimationPos;
        pose.walkAnimationSpeed = state.walkAnimationSpeed;
        pose.ageInTicks = state.ageInTicks;
        pose.isCrouching = false;
        pose.outlineColor = state.outlineColor;

        poseStack.pushPose();
        // Entity model space has the feet at y=1.501 (y axis points down). Scale around the feet.
        poseStack.translate(0.0F, 1.501F, 0.0F);
        poseStack.scale(sx, sy, sx);
        poseStack.translate(0.0F, -1.501F, 0.0F);
        renderPiece(poseStack, collector, light, armor[0], EquipmentSlot.HEAD);
        renderPiece(poseStack, collector, light, armor[1], EquipmentSlot.CHEST);
        renderPiece(poseStack, collector, light, armor[2], EquipmentSlot.LEGS);
        renderPiece(poseStack, collector, light, armor[3], EquipmentSlot.FEET);
        poseStack.popPose();
    }

    private void renderPiece(PoseStack poseStack, SubmitNodeCollector collector, int light, ItemStack stack, EquipmentSlot slot) {
        if (stack == null || stack.isEmpty()) return;
        Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
        if (equippable == null || equippable.slot() != slot || equippable.assetId().isEmpty()) return;
        HumanoidModel<HumanoidRenderState> model = models.get(slot);
        EquipmentClientInfo.LayerType layer = slot == EquipmentSlot.LEGS
                ? EquipmentClientInfo.LayerType.HUMANOID_LEGGINGS : EquipmentClientInfo.LayerType.HUMANOID;
        equipmentRenderer.renderLayers(layer, equippable.assetId().get(), model, pose, stack, poseStack, collector, light, pose.outlineColor);
    }
}
