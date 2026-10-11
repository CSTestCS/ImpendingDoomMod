package com.impendingdoom.events.minor;

import com.impendingdoom.ImpendingDoom;
import com.impendingdoom.block.Relightable;
import com.impendingdoom.core.DayClock;
import com.impendingdoom.core.DoomEvent;
import com.impendingdoom.core.EventScheduler;
import com.impendingdoom.registry.ModBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CarvedPumpkinBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Blackout: non-candle light sources flicker and can burn out (relight with flint and steel). Players are given
 * Darkness at night and while in caves.
 */
@EventBusSubscriber(modid = ImpendingDoom.MODID)
public class BlackoutEvent extends DoomEvent {
    public static final String ID = "blackout";

    public BlackoutEvent() {
        super(ID, false, ChatFormatting.DARK_GREEN);
    }

    @Override
    public void onTick(ServerLevel level, CompoundTag data) {
        long time = level.getGameTime();
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator()) continue;
            if (time % 20 == 0) {
                BlockPos head = player.blockPosition().above();
                boolean inCave = !level.canSeeSky(head) && player.getY() < level.getSeaLevel();
                if (DayClock.isNight(level) || inCave) {
                    player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0, true, false));
                }
            }
            if (time % 5 == 0) affectLights(level, player.blockPosition(), level.getRandom());
        }
    }

    private static void affectLights(ServerLevel level, BlockPos center, RandomSource random) {
        for (int i = 0; i < 60; i++) {
            BlockPos pos = center.offset(random.nextInt(33) - 16, random.nextInt(17) - 8, random.nextInt(33) - 16);
            BlockState state = level.getBlockState(pos);
            boolean burnOut = random.nextInt(250) == 0;
            boolean flicker = !burnOut && random.nextInt(12) == 0;
            if (!burnOut && !flicker) continue;
            BlockState unlit = unlitVersion(state, flicker);
            if (unlit == null) continue;
            level.setBlock(pos, unlit, Block.UPDATE_ALL);
            if (flicker && unlit.hasProperty(Relightable.FLICKER)) {
                level.scheduleTick(pos, unlit.getBlock(), 2 + random.nextInt(6));
            } else if (burnOut) {
                level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.4F, 1.6F);
            }
        }
    }

    private static BlockState unlitVersion(BlockState state, boolean flicker) {
        if (state.is(Blocks.TORCH)) return ModBlocks.UNLIT_TORCH.get().defaultBlockState().setValue(Relightable.FLICKER, flicker);
        if (state.is(Blocks.SOUL_TORCH)) return ModBlocks.UNLIT_SOUL_TORCH.get().defaultBlockState().setValue(Relightable.FLICKER, flicker);
        if (state.is(Blocks.WALL_TORCH)) return ModBlocks.UNLIT_WALL_TORCH.get().defaultBlockState()
                .setValue(WallTorchBlock.FACING, state.getValue(WallTorchBlock.FACING)).setValue(Relightable.FLICKER, flicker);
        if (state.is(Blocks.SOUL_WALL_TORCH)) return ModBlocks.UNLIT_SOUL_WALL_TORCH.get().defaultBlockState()
                .setValue(WallTorchBlock.FACING, state.getValue(WallTorchBlock.FACING)).setValue(Relightable.FLICKER, flicker);
        if (state.is(Blocks.LANTERN)) return ModBlocks.UNLIT_LANTERN.get().defaultBlockState()
                .setValue(LanternBlock.HANGING, state.getValue(LanternBlock.HANGING))
                .setValue(LanternBlock.WATERLOGGED, state.getValue(LanternBlock.WATERLOGGED)).setValue(Relightable.FLICKER, flicker);
        if (state.is(Blocks.SOUL_LANTERN)) return ModBlocks.UNLIT_SOUL_LANTERN.get().defaultBlockState()
                .setValue(LanternBlock.HANGING, state.getValue(LanternBlock.HANGING))
                .setValue(LanternBlock.WATERLOGGED, state.getValue(LanternBlock.WATERLOGGED)).setValue(Relightable.FLICKER, flicker);
        if (flicker) return null; // the remaining sources only burn out
        if ((state.is(Blocks.CAMPFIRE) || state.is(Blocks.SOUL_CAMPFIRE)) && state.getValue(CampfireBlock.LIT)) {
            return state.setValue(CampfireBlock.LIT, false);
        }
        if (state.is(Blocks.JACK_O_LANTERN)) {
            return Blocks.CARVED_PUMPKIN.defaultBlockState().setValue(CarvedPumpkinBlock.FACING, state.getValue(CarvedPumpkinBlock.FACING));
        }
        return null;
    }

    /** A burnt-out jack o'lantern can be relit with flint and steel. */
    @SubscribeEvent
    public static void onRelightPumpkin(PlayerInteractEvent.RightClickBlock event) {
        ItemStack stack = event.getItemStack();
        BlockState state = event.getLevel().getBlockState(event.getPos());
        if (!stack.is(Items.FLINT_AND_STEEL) || !state.is(Blocks.CARVED_PUMPKIN)) return;
        Player player = event.getEntity();
        if (!event.getLevel().isClientSide()) {
            event.getLevel().setBlockAndUpdate(event.getPos(),
                    Blocks.JACK_O_LANTERN.defaultBlockState().setValue(CarvedPumpkinBlock.FACING, state.getValue(CarvedPumpkinBlock.FACING)));
            event.getLevel().playSound(null, event.getPos(), SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
            stack.hurtAndBreak(1, player, event.getHand() == InteractionHand.MAIN_HAND
                    ? net.minecraft.world.entity.EquipmentSlot.MAINHAND : net.minecraft.world.entity.EquipmentSlot.OFFHAND);
        }
        event.setCanceled(true);
    }

    public static boolean active() {
        return EventScheduler.isActive(ID);
    }
}
