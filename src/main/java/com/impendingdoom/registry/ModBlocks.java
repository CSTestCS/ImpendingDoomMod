package com.impendingdoom.registry;

import com.impendingdoom.ImpendingDoom;
import com.impendingdoom.block.FallenLeavesBlock;
import com.impendingdoom.block.FloodBlock;
import com.impendingdoom.block.LeafPileBlock;
import com.impendingdoom.block.UnlitLanternBlock;
import com.impendingdoom.block.UnlitTorchBlock;
import com.impendingdoom.block.UnlitWallTorchBlock;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Function;
import java.util.function.UnaryOperator;

public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, ImpendingDoom.MODID);

    public static final DeferredHolder<Block, Block> FALLEN_LEAVES = register("fallen_leaves", FallenLeavesBlock::new,
            p -> p.mapColor(MapColor.COLOR_ORANGE).strength(0.1F).sound(SoundType.AZALEA_LEAVES).noOcclusion()
                    .replaceable().randomTicks());
    public static final DeferredHolder<Block, Block> LEAF_PILE = register("leaf_pile", LeafPileBlock::new,
            p -> p.mapColor(MapColor.COLOR_ORANGE).strength(0.3F).sound(SoundType.AZALEA_LEAVES).noOcclusion());

    public static final DeferredHolder<Block, Block> FLOOD_WATER = register("flood_water", p -> new FloodBlock(p, Fluids.FLOWING_WATER, false),
            p -> p.mapColor(MapColor.WATER).noCollision().noLootTable().replaceable().randomTicks().noOcclusion()
                    .strength(100.0F));
    public static final DeferredHolder<Block, Block> FLOOD_LAVA = register("flood_lava", p -> new FloodBlock(p, Fluids.FLOWING_LAVA, true),
            p -> p.mapColor(MapColor.FIRE).noCollision().noLootTable().replaceable().randomTicks().noOcclusion()
                    .strength(100.0F).lightLevel(s -> 15));

    public static final DeferredHolder<Block, Block> UNLIT_TORCH = register("unlit_torch", p -> new UnlitTorchBlock(p, () -> Blocks.TORCH),
            p -> p.noCollision().instabreak().sound(SoundType.WOOD));
    public static final DeferredHolder<Block, Block> UNLIT_WALL_TORCH = register("unlit_wall_torch", p -> new UnlitWallTorchBlock(p, () -> Blocks.WALL_TORCH),
            p -> p.noCollision().instabreak().sound(SoundType.WOOD));
    public static final DeferredHolder<Block, Block> UNLIT_SOUL_TORCH = register("unlit_soul_torch", p -> new UnlitTorchBlock(p, () -> Blocks.SOUL_TORCH),
            p -> p.noCollision().instabreak().sound(SoundType.WOOD));
    public static final DeferredHolder<Block, Block> UNLIT_SOUL_WALL_TORCH = register("unlit_soul_wall_torch", p -> new UnlitWallTorchBlock(p, () -> Blocks.SOUL_WALL_TORCH),
            p -> p.noCollision().instabreak().sound(SoundType.WOOD));
    public static final DeferredHolder<Block, Block> UNLIT_LANTERN = register("unlit_lantern", p -> new UnlitLanternBlock(p, () -> Blocks.LANTERN),
            p -> p.mapColor(MapColor.METAL).forceSolidOn().strength(3.5F).sound(SoundType.LANTERN).noOcclusion());
    public static final DeferredHolder<Block, Block> UNLIT_SOUL_LANTERN = register("unlit_soul_lantern", p -> new UnlitLanternBlock(p, () -> Blocks.SOUL_LANTERN),
            p -> p.mapColor(MapColor.METAL).forceSolidOn().strength(3.5F).sound(SoundType.LANTERN).noOcclusion());

    private ModBlocks() {}

    private static DeferredHolder<Block, Block> register(String name, Function<BlockBehaviour.Properties, ? extends Block> factory,
                                                         UnaryOperator<BlockBehaviour.Properties> props) {
        return BLOCKS.register(name, id -> factory.apply(
                props.apply(BlockBehaviour.Properties.of().setId(ResourceKey.create(Registries.BLOCK, id)))));
    }
}
