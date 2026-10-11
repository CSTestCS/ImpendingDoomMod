package com.impendingdoom.registry;

import com.impendingdoom.ImpendingDoom;
import com.impendingdoom.entity.HeartWalker;
import com.impendingdoom.entity.Herobrine;
import com.impendingdoom.entity.MeteorEntity;
import com.impendingdoom.entity.NetherflameBottle;
import com.impendingdoom.entity.TornadoEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, ImpendingDoom.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<TornadoEntity>> TORNADO = ENTITY_TYPES.register("tornado",
            id -> EntityType.Builder.<TornadoEntity>of(TornadoEntity::new, MobCategory.MISC)
                    .sized(6.0F, 24.0F).clientTrackingRange(16).updateInterval(2).fireImmune()
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, id)));

    public static final DeferredHolder<EntityType<?>, EntityType<MeteorEntity>> METEOR = ENTITY_TYPES.register("meteor",
            id -> EntityType.Builder.<MeteorEntity>of(MeteorEntity::new, MobCategory.MISC)
                    .sized(2.0F, 2.0F).clientTrackingRange(20).updateInterval(1).fireImmune()
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, id)));

    public static final DeferredHolder<EntityType<?>, EntityType<NetherflameBottle>> NETHERFLAME_BOTTLE = ENTITY_TYPES.register("netherflame_bottle",
            id -> EntityType.Builder.<NetherflameBottle>of(NetherflameBottle::new, MobCategory.MISC)
                    .sized(0.25F, 0.25F).clientTrackingRange(4).updateInterval(10)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, id)));

    public static final DeferredHolder<EntityType<?>, EntityType<HeartWalker>> HEART_WALKER = ENTITY_TYPES.register("heart_walker",
            id -> EntityType.Builder.<HeartWalker>of(HeartWalker::new, MobCategory.MISC)
                    .sized(0.7F, 1.1F).clientTrackingRange(10)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, id)));

    public static final DeferredHolder<EntityType<?>, EntityType<Herobrine>> HEROBRINE = ENTITY_TYPES.register("herobrine",
            id -> EntityType.Builder.<Herobrine>of(Herobrine::new, MobCategory.MISC)
                    .sized(0.6F, 1.8F).clientTrackingRange(16).fireImmune()
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, id)));

    private ModEntities() {}

    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(HEART_WALKER.get(), HeartWalker.createAttributes().build());
        event.put(HEROBRINE.get(), Herobrine.createAttributes().build());
    }
}
