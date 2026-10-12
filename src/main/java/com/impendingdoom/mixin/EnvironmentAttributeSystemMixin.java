package com.impendingdoom.mixin;

import com.impendingdoom.core.ActiveEvents;
import net.minecraft.world.attribute.EnvironmentAttribute;
import net.minecraft.world.attribute.EnvironmentAttributeSystem;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.attribute.SpatialAttributeInterpolator;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Solar Eclipse: the sky goes black and daylight is as dark as night, on both the server (sky light level for mob
 * spawning, no sun burning) and the client (sky, fog, lighting).
 */
@Mixin(EnvironmentAttributeSystem.class)
public abstract class EnvironmentAttributeSystemMixin {
    private static final String ECLIPSE = "solar_eclipse";
    private static final Vector3f BLACK = new Vector3f(0.0F, 0.0F, 0.0F);
    private static final Vector3f DIM = new Vector3f(0.02F, 0.02F, 0.035F);
    private static final Vector4f NONE = new Vector4f(0.0F, 0.0F, 0.0F, 0.0F);
    private static final Vector4f DARK_CLOUDS = new Vector4f(0.05F, 0.05F, 0.06F, 0.8F);

    @Inject(method = "getValue(Lnet/minecraft/world/attribute/EnvironmentAttribute;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/attribute/SpatialAttributeInterpolator;)Ljava/lang/Object;",
            at = @At("RETURN"), cancellable = true)
    private void impendingdoom$eclipse(EnvironmentAttribute<?> attribute, Vec3 pos, SpatialAttributeInterpolator interpolator, CallbackInfoReturnable<Object> cir) {
        if (!ActiveEvents.contextualActiveOverworld(ECLIPSE)) return;
        if (attribute == EnvironmentAttributes.SKY_LIGHT_FACTOR) {
            cir.setReturnValue(Math.min((Float) cir.getReturnValue(), 0.0F));
        } else if (attribute == EnvironmentAttributes.SKY_LIGHT_LEVEL) {
            cir.setReturnValue(Math.min((Float) cir.getReturnValue(), 0.0F));
        } else if (attribute == EnvironmentAttributes.SKY_COLOR || attribute == EnvironmentAttributes.SKY_LIGHT_COLOR) {
            cir.setReturnValue(BLACK);
        } else if (attribute == EnvironmentAttributes.FOG_COLOR || attribute == EnvironmentAttributes.AMBIENT_LIGHT_COLOR) {
            cir.setReturnValue(DIM);
        } else if (attribute == EnvironmentAttributes.SUNRISE_SUNSET_COLOR) {
            cir.setReturnValue(NONE);
        } else if (attribute == EnvironmentAttributes.CLOUD_COLOR) {
            cir.setReturnValue(DARK_CLOUDS);
        } else if (attribute == EnvironmentAttributes.STAR_BRIGHTNESS) {
            cir.setReturnValue(0.6F);
        } else if (attribute == EnvironmentAttributes.MONSTERS_BURN) {
            cir.setReturnValue(Boolean.FALSE);
        }
    }
}
