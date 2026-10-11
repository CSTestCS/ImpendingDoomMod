package com.impendingdoom.mixin;

import com.impendingdoom.core.ActiveEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Winter: every biome is cold enough to snow and all precipitation is snow, on both the server (snow layers,
 * no rain) and the client (snow particles instead of rain). World generation threads are unaffected.
 */
@Mixin(Biome.class)
public abstract class BiomeMixin {
    private static final String WINTER = "winter";

    @Inject(method = "getPrecipitationAt", at = @At("HEAD"), cancellable = true)
    private void impendingdoom$winterPrecipitation(BlockPos pos, int seaLevel, CallbackInfoReturnable<Biome.Precipitation> cir) {
        if (ActiveEvents.contextualActive(WINTER)) cir.setReturnValue(Biome.Precipitation.SNOW);
    }

    @Inject(method = "coldEnoughToSnow", at = @At("HEAD"), cancellable = true)
    private void impendingdoom$winterCold(BlockPos pos, int seaLevel, CallbackInfoReturnable<Boolean> cir) {
        if (ActiveEvents.contextualActive(WINTER)) cir.setReturnValue(true);
    }
}
