package com.impendingdoom.events.major;

import com.impendingdoom.ImpendingDoom;
import com.impendingdoom.core.DoomEvent;
import com.impendingdoom.core.EventScheduler;
import com.impendingdoom.entity.ai.ForcedMeleeGoal;
import com.impendingdoom.util.EventUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.level.block.CropGrowEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Spring (season 4/4): snow in biomes that do not naturally have it melts rapidly (snow blocks included). Crops
 * grow faster and yield more. Any animal spawn may be an alpha: larger and hostile.
 */
@EventBusSubscriber(modid = ImpendingDoom.MODID)
public class SpringEvent extends DoomEvent {
    public static final String ID = "spring";
    private static final String ALPHA = "impendingdoom.alpha";

    public SpringEvent() {
        super(ID, true, ChatFormatting.GREEN);
    }

    @Override
    public boolean isSeason() {
        return true;
    }

    @Override
    public void onTick(ServerLevel level, CompoundTag data) {
        if (level.getGameTime() % 4 != 0) return;
        RandomSource r = level.getRandom();
        for (ServerPlayer player : level.players()) {
            for (int i = 0; i < 40; i++) {
                BlockPos column = player.blockPosition().offset(r.nextInt(97) - 48, 0, r.nextInt(97) - 48);
                if (!level.isLoaded(column)) continue;
                BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, column).below();
                melt(level, top);
                melt(level, top.above());
            }
        }
    }

    private static void melt(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!state.is(Blocks.SNOW) && !state.is(Blocks.SNOW_BLOCK)) return;
        if (level.getBiome(pos).value().coldEnoughToSnow(pos, level.getSeaLevel())) return;
        if (state.is(Blocks.SNOW_BLOCK)) {
            level.setBlockAndUpdate(pos, Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 7));
        } else {
            int layers = state.getValue(SnowLayerBlock.LAYERS);
            level.setBlockAndUpdate(pos, layers <= 1 ? Blocks.AIR.defaultBlockState() : state.setValue(SnowLayerBlock.LAYERS, layers - 1));
        }
    }

    @SubscribeEvent
    public static void onCropGrow(CropGrowEvent.Pre event) {
        if (EventScheduler.isActive(ID) && event.getLevel().getRandom().nextInt(3) == 0) event.setResult(CropGrowEvent.Pre.Result.GROW);
    }

    @SubscribeEvent
    public static void onHarvest(BlockDropsEvent event) {
        if (!EventScheduler.isActive(ID)) return;
        BlockState state = event.getState();
        if (!(state.getBlock() instanceof CropBlock crop) || !crop.isMaxAge(state)) return;
        List<ItemEntity> extra = new ArrayList<>();
        for (ItemEntity drop : event.getDrops()) {
            extra.add(new ItemEntity(event.getLevel(), drop.getX(), drop.getY(), drop.getZ(), drop.getItem().copyWithCount(Math.max(1, drop.getItem().getCount() / 2))));
        }
        event.getDrops().addAll(extra);
    }

    @SubscribeEvent
    public static void onAnimalSpawn(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof Animal animal)) return;
        if (animal.getTags().contains(ALPHA)) {
            if (event.loadedFromDisk()) addAlphaGoals(animal);
            return;
        }
        if (event.loadedFromDisk() || !EventScheduler.isActive(ID) || animal.isBaby() || level.getRandom().nextInt(10) != 0) return;
        animal.addTag(ALPHA);
        EventUtil.addModifier(animal, Attributes.SCALE, "alpha_scale", 0.6, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        EventUtil.addModifier(animal, Attributes.MAX_HEALTH, "alpha_health", 2.0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        EventUtil.addModifier(animal, Attributes.MOVEMENT_SPEED, "alpha_speed", 0.3, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        animal.setHealth(animal.getMaxHealth());
        animal.setCustomName(Component.translatable("entity.impendingdoom.alpha", animal.getType().getDescription()).withStyle(ChatFormatting.DARK_RED));
        addAlphaGoals(animal);
    }

    private static void addAlphaGoals(Animal animal) {
        animal.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(animal, Player.class, true));
        animal.goalSelector.addGoal(1, new ForcedMeleeGoal(animal, 1.25, 5.0F));
    }
}
