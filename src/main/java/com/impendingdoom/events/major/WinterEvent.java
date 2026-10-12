package com.impendingdoom.events.major;

import com.impendingdoom.util.EventUtil;
import com.impendingdoom.ImpendingDoom;
import com.impendingdoom.core.DoomEvent;
import com.impendingdoom.core.EventScheduler;
import com.impendingdoom.registry.ModEffects;
import com.impendingdoom.registry.ModTags;
import com.impendingdoom.util.GearUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.BlockGrowFeatureEvent;
import net.neoforged.neoforge.event.level.block.CropGrowEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Winter (season 3/4): clear weather is snow and any weather is a blizzard (dense fog, rapid snowfall). Snow
 * accumulates anywhere it can see the sky (via BiomeMixin). Crops and saplings break, cannot be planted or grow.
 * Without leather/netherite armor or a nearby fire based light, players freeze.
 */
@EventBusSubscriber(modid = ImpendingDoom.MODID)
public class WinterEvent extends DoomEvent {
    public static final String ID = "winter";

    public WinterEvent() {
        super(ID, true, ChatFormatting.AQUA);
    }

    @Override
    public boolean isSeason() {
        return true;
    }

    @Override
    public void onTick(ServerLevel level, CompoundTag data) {
        // It always snows ("clear" weather); every other weather is a blizzard (thunder state).
        int timer = data.getIntOr("weatherTimer", 0) - 1;
        boolean blizzard = data.getBooleanOr("blizzard", false);
        if (timer <= 0) {
            blizzard = !blizzard && level.getRandom().nextInt(3) == 0;
            timer = blizzard ? 6000 + level.getRandom().nextInt(6000) : 12000 + level.getRandom().nextInt(12000);
            data.putBoolean("blizzard", blizzard);
            EventUtil.setWeather(level, (blizzard ? "thunder" : "rain"), timer + 200);
        } else if (!level.isRaining() || level.isThundering() != blizzard) {
            EventUtil.setWeather(level, (blizzard ? "thunder" : "rain"), timer + 200);
        }
        data.putInt("weatherTimer", timer);
        long time = level.getGameTime();
        RandomSource r = level.getRandom();
        for (ServerPlayer player : level.players()) {
            if (time % (blizzard ? 2 : 10) == 0) accumulate(level, player.blockPosition(), r, blizzard);
            if (time % 20 == 0) breakCrops(level, player.blockPosition(), r);
        }
    }

    private static void accumulate(ServerLevel level, BlockPos center, RandomSource r, boolean blizzard) {
        int maxLayers = blizzard ? 8 : 3;
        for (int i = 0; i < (blizzard ? 24 : 6); i++) {
            BlockPos column = center.offset(r.nextInt(65) - 32, 0, r.nextInt(65) - 32);
            if (!level.isLoaded(column)) continue;
            BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, column);
            BlockState state = level.getBlockState(top);
            if (state.is(Blocks.SNOW)) {
                int layers = state.getValue(SnowLayerBlock.LAYERS);
                if (layers < maxLayers) {
                    level.setBlockAndUpdate(top, layers == 7 ? Blocks.SNOW_BLOCK.defaultBlockState() : state.setValue(SnowLayerBlock.LAYERS, layers + 1));
                }
            } else if (state.isAir() && level.getBlockState(top.below()).is(Blocks.SNOW)) {
                // handled by the branch above next time
            } else if (state.isAir()) {
                BlockState snow = Blocks.SNOW.defaultBlockState();
                if (snow.canSurvive(level, top)) level.setBlockAndUpdate(top, snow);
            } else if (state.is(Blocks.WATER) && r.nextInt(4) == 0 && state.getFluidState().isSource()) {
                level.setBlockAndUpdate(top, Blocks.ICE.defaultBlockState());
            }
        }
    }

    private static boolean isPlant(BlockState state) {
        return state.is(BlockTags.CROPS) || state.is(BlockTags.SAPLINGS) || state.getBlock() instanceof SweetBerryBushBlock
                || state.is(Blocks.COCOA) || state.is(Blocks.MELON_STEM) || state.is(Blocks.PUMPKIN_STEM)
                || state.is(Blocks.ATTACHED_MELON_STEM) || state.is(Blocks.ATTACHED_PUMPKIN_STEM);
    }

    private static void breakCrops(ServerLevel level, BlockPos center, RandomSource r) {
        for (int i = 0; i < 80; i++) {
            BlockPos pos = center.offset(r.nextInt(65) - 32, r.nextInt(17) - 8, r.nextInt(65) - 32);
            if (isPlant(level.getBlockState(pos))) level.destroyBlock(pos, true);
        }
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (EventScheduler.isActive(ID) && isPlant(event.getPlacedBlock())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onCropGrow(CropGrowEvent.Pre event) {
        if (EventScheduler.isActive(ID)) event.setResult(CropGrowEvent.Pre.Result.DO_NOT_GROW);
    }

    @SubscribeEvent
    public static void onTreeGrow(BlockGrowFeatureEvent event) {
        if (EventScheduler.isActive(ID)) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onBonemeal(PlayerInteractEvent.RightClickBlock event) {
        if (EventScheduler.isActive(ID) && event.getItemStack().is(Items.BONE_MEAL) && isPlant(event.getLevel().getBlockState(event.getPos()))) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 20 != 0 || !EventScheduler.isActive(ID)) return;
        if (player.isCreative() || player.isSpectator() || !player.level().dimensionType().hasSkyLight()) return;
        int warm = 0;
        for (EquipmentSlot slot : GearUtil.ARMOR_SLOTS) if (player.getItemBySlot(slot).is(ModTags.WARM_ARMOR)) warm++;
        if (warm >= 4 || nearHeat(player)) return;
        player.addEffect(new MobEffectInstance(ModEffects.FREEZING, 40, 3 - warm, true, true));
    }

    private static boolean nearHeat(Player player) {
        BlockPos p = player.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(p.offset(-4, -2, -4), p.offset(4, 2, 4))) {
            BlockState s = player.level().getBlockState(pos);
            if (s.is(BlockTags.FIRE) || s.is(Blocks.LAVA) || s.is(Blocks.MAGMA_BLOCK) || s.is(Blocks.TORCH) || s.is(Blocks.WALL_TORCH)
                    || s.is(Blocks.SOUL_TORCH) || s.is(Blocks.SOUL_WALL_TORCH) || s.is(Blocks.LANTERN) || s.is(Blocks.SOUL_LANTERN)
                    || s.is(Blocks.JACK_O_LANTERN)
                    || ((s.is(Blocks.CAMPFIRE) || s.is(Blocks.SOUL_CAMPFIRE)) && s.getValue(CampfireBlock.LIT))
                    || ((s.is(Blocks.FURNACE) || s.is(Blocks.BLAST_FURNACE) || s.is(Blocks.SMOKER)) && s.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT))) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void onEnd(ServerLevel level, CompoundTag data) {
        EventUtil.setWeather(level, "clear", 6000);
    }
}
