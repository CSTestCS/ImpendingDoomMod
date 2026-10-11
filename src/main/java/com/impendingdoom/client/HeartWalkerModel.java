package com.impendingdoom.client;

import com.impendingdoom.ImpendingDoom;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;

/** A creaking heart block on two stubby legs. */
public class HeartWalkerModel extends EntityModel<LivingEntityRenderState> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(ImpendingDoom.id("heart_walker"), "main");
    private final ModelPart heart;
    private final ModelPart rightLeg;
    private final ModelPart leftLeg;

    public HeartWalkerModel(ModelPart root) {
        super(root);
        this.heart = root.getChild("heart");
        this.rightLeg = root.getChild("right_leg");
        this.leftLeg = root.getChild("left_leg");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("heart", CubeListBuilder.create().texOffs(0, 0).addBox(-5.0F, -10.0F, -5.0F, 10, 10, 10), PartPose.offset(0.0F, 16.0F, 0.0F));
        root.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(0, 20).addBox(-1.5F, 0.0F, -1.5F, 3, 8, 3), PartPose.offset(-2.5F, 16.0F, 0.0F));
        root.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(12, 20).addBox(-1.5F, 0.0F, -1.5F, 3, 8, 3), PartPose.offset(2.5F, 16.0F, 0.0F));
        return LayerDefinition.create(mesh, 64, 32);
    }

    @Override
    public void setupAnim(LivingEntityRenderState state) {
        super.setupAnim(state);
        float pos = state.walkAnimationPos;
        float speed = state.walkAnimationSpeed;
        rightLeg.xRot = Mth.cos(pos * 1.2F) * 1.6F * speed;
        leftLeg.xRot = Mth.cos(pos * 1.2F + Mth.PI) * 1.6F * speed;
        // Nervous bob
        heart.y = 16.0F + Math.abs(Mth.cos(pos * 1.2F)) * -1.0F * speed + Mth.sin(state.ageInTicks * 0.3F) * 0.3F;
        heart.zRot = Mth.cos(pos * 0.6F) * 0.08F * speed;
    }
}
