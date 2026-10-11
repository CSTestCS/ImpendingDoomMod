package com.impendingdoom.registry;

import com.impendingdoom.ImpendingDoom;
import com.impendingdoom.effect.BloodlustHazeEffect;
import com.impendingdoom.effect.FreezingEffect;
import com.impendingdoom.effect.StunnedEffect;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEffects {
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, ImpendingDoom.MODID);

    public static final DeferredHolder<MobEffect, MobEffect> STUNNED = EFFECTS.register("stunned", StunnedEffect::new);
    public static final DeferredHolder<MobEffect, MobEffect> BLOODLUST_HAZE = EFFECTS.register("bloodlust_haze", BloodlustHazeEffect::new);
    public static final DeferredHolder<MobEffect, MobEffect> FREEZING = EFFECTS.register("freezing", FreezingEffect::new);

    private ModEffects() {}
}
