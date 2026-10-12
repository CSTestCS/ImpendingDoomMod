package com.impendingdoom;

import com.impendingdoom.config.DoomConfig;
import com.impendingdoom.network.NetworkHandler;
import com.impendingdoom.registry.ModBlocks;
import com.impendingdoom.registry.ModEffects;
import com.impendingdoom.registry.ModEntities;
import com.impendingdoom.registry.ModGameRules;
import com.impendingdoom.registry.ModItems;
import com.impendingdoom.util.KaijuScaling;
import com.mojang.logging.LogUtils;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.equipment.Equippable;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.ModifyDefaultComponentsEvent;
import org.slf4j.Logger;

@Mod(ImpendingDoom.MODID)
public final class ImpendingDoom {
    public static final String MODID = "impendingdoom";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ImpendingDoom(IEventBus modBus, ModContainer container) {
        ModBlocks.BLOCKS.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModItems.TABS.register(modBus);
        ModEntities.ENTITY_TYPES.register(modBus);
        ModEffects.EFFECTS.register(modBus);
        ModGameRules.GAME_RULES.register(modBus);

        container.registerConfig(ModConfig.Type.STARTUP, DoomConfig.SPEC);

        modBus.addListener(NetworkHandler::register);
        modBus.addListener(ModEntities::registerAttributes);
        modBus.addListener(ImpendingDoom::modifyComponents);
        modBus.addListener(ImpendingDoom::commonSetup);
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }

    private static void commonSetup(FMLCommonSetupEvent event) {
        // Kaiju need to be scaled beyond the vanilla 16x limit of the scale attribute.
        event.enqueueWork(KaijuScaling::raiseScaleLimit);
    }

    /** Wool can be worn on the head (Blood Moon lets players sleep with wool over their eyes). */
    private static void modifyComponents(ModifyDefaultComponentsEvent event) {
        event.modifyMatching((item, components) -> {
            Identifier key = BuiltInRegistries.ITEM.getKey(item);
            return key.getNamespace().equals("minecraft") && key.getPath().endsWith("_wool");
        }, (builder, lookup, item) -> builder.set(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.HEAD).build()));
    }
}
