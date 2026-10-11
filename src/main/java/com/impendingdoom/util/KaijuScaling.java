package com.impendingdoom.util;

import com.impendingdoom.ImpendingDoom;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;

import java.lang.reflect.Field;

/** The vanilla scale attribute is capped at 16x; Kaiju are 20x by default, so the cap is raised. */
public final class KaijuScaling {
    private static final double NEW_MAX = 32.0;

    private KaijuScaling() {}

    public static void raiseScaleLimit() {
        try {
            Field field = RangedAttribute.class.getDeclaredField("maxValue");
            field.setAccessible(true);
            if (Attributes.SCALE.value() instanceof RangedAttribute ranged && ranged.getMaxValue() < NEW_MAX) {
                field.setDouble(ranged, NEW_MAX);
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            ImpendingDoom.LOGGER.warn("Could not raise the scale attribute limit, Kaiju will be limited to 16x", e);
        }
    }

    public static double maxScale() {
        return Attributes.SCALE.value() instanceof RangedAttribute ranged ? ranged.getMaxValue() : 16.0;
    }
}
