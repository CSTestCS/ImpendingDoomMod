package com.impendingdoom.client;

import com.impendingdoom.ImpendingDoom;
import com.impendingdoom.entity.HeartWalker;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;

public class HeartWalkerRenderer extends MobRenderer<HeartWalker, LivingEntityRenderState, HeartWalkerModel> {
    private static final Identifier TEXTURE = ImpendingDoom.id("textures/entity/heart_walker.png");

    public HeartWalkerRenderer(EntityRendererProvider.Context context) {
        super(context, new HeartWalkerModel(context.bakeLayer(HeartWalkerModel.LAYER)), 0.4F);
    }

    @Override
    public LivingEntityRenderState createRenderState() {
        return new LivingEntityRenderState();
    }

    @Override
    public Identifier getTextureLocation(LivingEntityRenderState state) {
        return TEXTURE;
    }
}
