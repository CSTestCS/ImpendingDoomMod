package com.impendingdoom.events.minor;

import com.impendingdoom.core.DoomEvent;
import com.impendingdoom.network.HallucinationPayload;
import com.impendingdoom.util.EventUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Paranoia: heavy fog (client), false footsteps and mob sounds, and the occasional hallucinated monster or Herobrine
 * that only one player sees.
 */
public class ParanoiaEvent extends DoomEvent {
    public static final String ID = "paranoia";
    private static final SoundEvent[] STEPS = {SoundEvents.STONE_STEP, SoundEvents.GRAVEL_STEP, SoundEvents.GRASS_STEP, SoundEvents.WOOD_STEP};
    private static final SoundEvent[] MOBS = {SoundEvents.ZOMBIE_AMBIENT, SoundEvents.SKELETON_AMBIENT, SoundEvents.SPIDER_AMBIENT,
            SoundEvents.CREEPER_PRIMED, SoundEvents.ENDERMAN_AMBIENT, SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, SoundEvents.WITCH_AMBIENT,
            SoundEvents.WARDEN_HEARTBEAT, SoundEvents.CREAKING_AMBIENT};

    public ParanoiaEvent() {
        super(ID, false, ChatFormatting.DARK_GREEN);
    }

    @Override
    public void onTick(ServerLevel level, CompoundTag data) {
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator()) continue;
            RandomSource r = level.getRandom();
            if (r.nextInt(300) == 0) footsteps(player, r);
            if (r.nextInt(500) == 0) mobSound(player, r);
            if (r.nextInt(1500) == 0) hallucinate(level, player, r, r.nextInt(6) == 0 ? 1 : 0);
        }
    }

    /** A few footsteps from right behind the player. */
    private static void footsteps(ServerPlayer player, RandomSource r) {
        Vec3 behind = player.position().subtract(player.getViewVector(1.0F).multiply(1, 0, 1).normalize().scale(3 + r.nextInt(3)));
        SoundEvent step = STEPS[r.nextInt(STEPS.length)];
        for (int i = 0; i < 3 + r.nextInt(3); i++) {
            int delay = i * 6;
            EventUtil.defer(new DelayedSound(player, step, behind, delay));
        }
    }

    private static void mobSound(ServerPlayer player, RandomSource r) {
        Vec3 pos = player.position().add(r.nextGaussian() * 6, r.nextGaussian(), r.nextGaussian() * 6);
        EventUtil.privateSound(player, MOBS[r.nextInt(MOBS.length)], pos.x, pos.y, pos.z, 0.9F, 0.8F + r.nextFloat() * 0.4F);
    }

    private static void hallucinate(ServerLevel level, ServerPlayer player, RandomSource r, int kind) {
        // Usually in the corner of the player's eye
        Vec3 look = player.getViewVector(1.0F).multiply(1, 0, 1).normalize();
        double angle = (r.nextBoolean() ? 1 : -1) * (0.6 + r.nextDouble() * 0.6);
        Vec3 dir = new Vec3(look.x * Math.cos(angle) - look.z * Math.sin(angle), 0, look.x * Math.sin(angle) + look.z * Math.cos(angle));
        Vec3 pos = player.position().add(dir.scale(10 + r.nextInt(10)));
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(pos.x), (int) Math.floor(pos.z));
        if (Math.abs(y - player.getY()) > 12) y = (int) player.getY();
        PacketDistributor.sendToPlayer(player, new HallucinationPayload(kind, pos.x, y, pos.z));
    }

    /** Re-defers itself until its delay runs out, then plays a private sound. */
    private record DelayedSound(ServerPlayer player, SoundEvent sound, Vec3 pos, int delay) implements Runnable {
        @Override
        public void run() {
            if (delay > 0) {
                EventUtil.defer(new DelayedSound(player, sound, pos, delay - 1));
            } else if (!player.hasDisconnected()) {
                EventUtil.privateSound(player, sound, pos.x, pos.y, pos.z, 0.6F, 0.9F + player.getRandom().nextFloat() * 0.2F);
            }
        }
    }
}
