package com.impendingdoom.entity;

import com.impendingdoom.registry.ModEntities;
import com.impendingdoom.registry.ModItems;
import com.impendingdoom.util.EventUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;

/** Thrown splash potion of Netherflame. Converts terrain to nether materials and sets it alight. */
public class NetherflameBottle extends ThrowableItemProjectile {
    public NetherflameBottle(EntityType<? extends NetherflameBottle> type, Level level) {
        super(type, level);
    }

    public NetherflameBottle(Level level, LivingEntity owner, ItemStack stack) {
        super(ModEntities.NETHERFLAME_BOTTLE.get(), owner, level, stack);
    }

    @Override
    protected Item getDefaultItem() {
        return ModItems.NETHERFLAME_BOTTLE.get();
    }

    @Override
    protected double getDefaultGravity() {
        return 0.05;
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (level() instanceof ServerLevel level) {
            BlockPos center = BlockPos.containing(result.getLocation());
            level.levelEvent(2002, center, 0xFF4A00);
            if (EventUtil.canDestroyBlocks(level)) convert(level, center, 3);
            discard();
        }
    }

    public static void convert(ServerLevel level, BlockPos center, int radius) {
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-radius, -radius, -radius), center.offset(radius, radius, radius))) {
            if (p.distSqr(center) > radius * radius + 1 || level.getRandom().nextInt(5) == 0) continue;
            BlockState state = level.getBlockState(p);
            BlockState result = netherize(state);
            if (result != null) {
                level.setBlockAndUpdate(p, result);
                BlockPos above = p.above();
                if (level.getBlockState(above).isAir() && level.getRandom().nextInt(4) == 0) {
                    level.setBlockAndUpdate(above, BaseFireBlock.getState(level, above));
                }
            }
        }
    }

    private static BlockState netherize(BlockState state) {
        if (state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.PODZOL) || state.is(Blocks.MYCELIUM)) return Blocks.CRIMSON_NYLIUM.defaultBlockState();
        if (state.is(BlockTags.DIRT) || state.is(Blocks.COARSE_DIRT)) return Blocks.NETHERRACK.defaultBlockState();
        if (state.is(Blocks.STONE) || state.is(Blocks.COBBLESTONE) || state.is(Blocks.ANDESITE) || state.is(Blocks.DIORITE) || state.is(Blocks.GRANITE))
            return Blocks.BLACKSTONE.defaultBlockState();
        if (state.is(Blocks.DEEPSLATE) || state.is(Blocks.TUFF)) return Blocks.BASALT.defaultBlockState();
        if (state.is(BlockTags.SAND)) return Blocks.SOUL_SAND.defaultBlockState();
        if (state.is(Blocks.GRAVEL) || state.is(Blocks.CLAY)) return Blocks.SOUL_SOIL.defaultBlockState();
        if (state.is(BlockTags.LOGS)) return Blocks.CRIMSON_STEM.defaultBlockState();
        if (state.is(BlockTags.LEAVES)) return Blocks.NETHER_WART_BLOCK.defaultBlockState();
        if (state.is(Blocks.SHORT_GRASS) || state.is(Blocks.FERN) || state.is(BlockTags.SMALL_FLOWERS)) return Blocks.CRIMSON_ROOTS.defaultBlockState();
        if (state.is(Blocks.WATER)) return Blocks.MAGMA_BLOCK.defaultBlockState();
        if (state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.ICE) || state.is(Blocks.PACKED_ICE)) return Blocks.NETHERRACK.defaultBlockState();
        return null;
    }
}
