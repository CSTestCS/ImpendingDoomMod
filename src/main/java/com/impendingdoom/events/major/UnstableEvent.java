package com.impendingdoom.events.major;

import com.impendingdoom.ImpendingDoom;
import com.impendingdoom.core.DoomEvent;
import com.impendingdoom.core.EventScheduler;
import com.impendingdoom.util.EventUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;

/**
 * Unstable: earthquakes strike at random. During a quake, breaking (or starting to mine) a block collapses a large,
 * randomly shaped area around it into falling blocks.
 */
@EventBusSubscriber(modid = ImpendingDoom.MODID)
public class UnstableEvent extends DoomEvent {
    public static final String ID = "unstable";
    private static volatile float intensity;

    public UnstableEvent() {
        super(ID, true, ChatFormatting.DARK_RED);
    }

    /** Current earthquake strength, 0 when calm. Synced to clients for camera shake. */
    public static float intensity() {
        return EventScheduler.isActive(ID) ? intensity : 0.0F;
    }

    public static boolean quaking() {
        return intensity() > 0.0F;
    }

    @Override
    public void onStart(ServerLevel level, CompoundTag data) {
        data.putInt("cooldown", 600 + level.getRandom().nextInt(1800));
    }

    @Override
    public void onTick(ServerLevel level, CompoundTag data) {
        RandomSource r = level.getRandom();
        int quake = data.getIntOr("quake", 0);
        if (quake > 0) {
            quake--;
            int total = Math.max(1, data.getIntOr("quakeLength", 1));
            float progress = 1.0F - quake / (float) total;
            intensity = (float) Math.sin(progress * Math.PI) * data.getFloatOr("strength", 1.0F);
            if (level.getGameTime() % 8 == 0) rumble(level, r);
            if (quake == 0) {
                intensity = 0;
                // 2000-4400 ticks of calm between quakes: about five quakes per Minecraft day.
                data.putInt("cooldown", 2000 + r.nextInt(2400));
                EventScheduler.syncAll(level, EventScheduler.data());
            }
            data.putInt("quake", quake);
            return;
        }
        intensity = 0;
        int cooldown = data.getIntOr("cooldown", 6000) - 1;
        data.putInt("cooldown", cooldown);
        if (cooldown <= 0) {
            int length = 400 + r.nextInt(500);
            data.putInt("quake", length);
            data.putInt("quakeLength", length);
            data.putFloat("strength", 0.5F + r.nextFloat());
            for (ServerPlayer p : level.players()) {
            }
        }
    }

    private static void rumble(ServerLevel level, RandomSource r) {
        for (ServerPlayer p : level.players()) {
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.WARDEN_DIG, SoundSource.AMBIENT, 0.8F + intensity, 0.3F);
            BlockPos below = p.blockPosition().below();
            BlockState ground = level.getBlockState(below);
            if (!ground.isAir()) {
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), p.getX(), p.getY() + 0.1, p.getZ(), 30, 5, 0.1, 5, 0.15);
            }
            // Everything standing on the ground is thrown upward.
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(48),
                    e -> e.onGround() && !(e instanceof Player pl && (pl.isCreative() || pl.isSpectator())))) {
                if (r.nextInt(3) != 0) continue;
                double up = (0.35 + r.nextDouble() * 0.35) * intensity;
                e.setDeltaMovement(e.getDeltaMovement().add((r.nextDouble() - 0.5) * 0.2 * intensity, up, (r.nextDouble() - 0.5) * 0.2 * intensity));
                e.needsSync = true;
                if (e instanceof ServerPlayer sp) sp.connection.send(new ClientboundSetEntityMotionPacket(sp));
            }
            // The ground heaves: surface blocks jump up and fall back down.
            if (EventUtil.canDestroyBlocks(level)) {
                for (int i = 0; i < 6; i++) {
                    BlockPos column = p.blockPosition().offset(r.nextInt(33) - 16, 0, r.nextInt(33) - 16);
                    if (!level.isLoaded(column)) continue;
                    BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column).below();
                    BlockState state = level.getBlockState(top);
                    if (!(state.is(BlockTags.DIRT) || state.is(BlockTags.SAND) || state.is(Blocks.GRAVEL) || state.is(BlockTags.BASE_STONE_OVERWORLD))) continue;
                    if (!level.getBlockState(top.above()).isAir()) continue;
                    FallingBlockEntity fb = FallingBlockEntity.fall(level, top, state);
                    fb.setDeltaMovement(0, 0.35 + r.nextDouble() * 0.4 * intensity, 0);
                    fb.needsSync = true;
                }
            }
        }
    }

    @SubscribeEvent
    public static void onBreak(BreakBlockEvent event) {
        if (quaking() && event.getLevel() instanceof ServerLevel level) collapse(level, event.getPos());
    }

    @SubscribeEvent
    public static void onStartMining(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START || !quaking()) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        Player player = event.getEntity();
        long now = level.getGameTime();
        if (now - player.getPersistentData().getLongOr("impendingdoom_collapse", 0L) < 60) return;
        player.getPersistentData().putLong("impendingdoom_collapse", now);
        collapse(level, event.getPos());
    }

    private static void collapse(ServerLevel level, BlockPos center) {
        if (!EventUtil.canDestroyBlocks(level)) return;
        RandomSource r = level.getRandom();
        int rx = 3 + r.nextInt(3), ry = 2 + r.nextInt(3), rz = 3 + r.nextInt(3);
        double nx = r.nextDouble() * 10, nz = r.nextDouble() * 10;
        int count = 0;
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-rx, -ry, -rz), center.offset(rx, ry, rz))) {
            double dx = (pos.getX() - center.getX()) / (double) rx;
            double dy = (pos.getY() - center.getY()) / (double) ry;
            double dz = (pos.getZ() - center.getZ()) / (double) rz;
            // Wobbly ellipsoid: random lumpy outline
            double wobble = 0.75 + 0.35 * Math.sin(pos.getX() * 0.9 + nx) * Math.cos(pos.getZ() * 0.9 + nz);
            if (dx * dx + dy * dy + dz * dz > wobble || pos.equals(center)) continue;
            BlockState state = level.getBlockState(pos);
            if (!EventUtil.isBreakable(level, pos, state, 50.0F) || !state.getFluidState().isEmpty()) continue;
            FallingBlockEntity.fall(level, pos.immutable(), state);
            if (++count > 220) break;
        }
        level.playSound(null, center, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 1.0F, 0.4F);
    }

}
