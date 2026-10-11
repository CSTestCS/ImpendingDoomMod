package com.impendingdoom.events.minor;

import com.impendingdoom.ImpendingDoom;
import com.impendingdoom.core.DoomEvent;
import com.impendingdoom.util.EventUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.bee.Bee;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

/**
 * Swarms: large groups of naturally hostile bees spawn and attack together. A swarm circles its victim first, then
 * dives in all at once, regroups, and repeats.
 */
public class SwarmsEvent extends DoomEvent {
    public static final String ID = "swarms";
    private static final String TAG = "impendingdoom.swarm";
    private static final String SWARM = "impendingdoom_swarm";
    private static final String PREY = "impendingdoom_swarm_prey";

    public SwarmsEvent() {
        super(ID, false, ChatFormatting.DARK_GREEN);
    }

    @Override
    public void onTick(ServerLevel level, CompoundTag data) {
        int cooldown = data.getIntOr("cooldown", 600) - 1;
        if (cooldown <= 0) {
            cooldown = 1800 + level.getRandom().nextInt(2400);
            ServerPlayer player = EventUtil.randomTarget(level);
            if (player != null) spawnSwarm(level, player);
        }
        data.putInt("cooldown", cooldown);
        if (level.getGameTime() % 5 == 0) tickSwarms(level);
    }

    private static void spawnSwarm(ServerLevel level, ServerPlayer player) {
        BlockPos origin = EventUtil.surfacePos(level, player.blockPosition(), 20, 28, level.getRandom());
        if (origin == null) return;
        String id = UUID.randomUUID().toString();
        int size = 10 + level.getRandom().nextInt(8);
        for (int i = 0; i < size; i++) {
            Bee bee = EntityType.BEE.create(level, EntitySpawnReason.EVENT);
            if (bee == null) continue;
            bee.setPos(origin.getX() + level.getRandom().nextGaussian(), origin.getY() + 4 + level.getRandom().nextInt(3),
                    origin.getZ() + level.getRandom().nextGaussian());
            bee.addTag(TAG);
            bee.getPersistentData().putString(SWARM, id);
            bee.getPersistentData().putString(PREY, player.getUUID().toString());
            bee.getPersistentData().putLong("impendingdoom_born", level.getGameTime());
            EventUtil.addModifier(bee, Attributes.FLYING_SPEED, "swarm_speed", 0.4, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
            level.addFreshEntity(bee);
        }
        player.sendSystemMessage(Component.translatable("message.impendingdoom.swarm").withStyle(ChatFormatting.GOLD), true);
    }

    private static void tickSwarms(ServerLevel level) {
        List<Bee> bees = level.getEntities(EntityTypeTest.forClass(Bee.class), b -> b.getTags().contains(TAG));
        long time = level.getGameTime();
        for (Bee bee : bees) {
            if (time - bee.getPersistentData().getLongOr("impendingdoom_born", time) > 3600) {
                bee.discard();
                continue;
            }
            UUID preyId = EventUtil.uuid(bee.getPersistentData(), PREY);
            Player prey = preyId == null ? null : level.getPlayerByUUID(preyId);
            if (prey == null || prey.isCreative() || prey.isSpectator()) continue;
            // All bees in one swarm share the same 20 second rhythm: circle for 12s, then attack for 8s.
            long phase = (time + Math.abs(bee.getPersistentData().getStringOr(SWARM, "").hashCode())) % 400;
            if (phase < 240) {
                bee.setTarget(null);
                double a = time * 0.08 + bee.getId();
                Vec3 orbit = prey.position().add(Math.cos(a) * 6, 2.5 + Math.sin(a * 0.7), Math.sin(a) * 6);
                bee.getMoveControl().setWantedPosition(orbit.x, orbit.y, orbit.z, 1.2);
            } else if (bee.getTarget() != prey) {
                bee.setTarget(prey);
                bee.startPersistentAngerTimer();
            }
        }
    }

    @Override
    public void onEnd(ServerLevel level, CompoundTag data) {
        for (Bee bee : level.getEntities(EntityTypeTest.forClass(Bee.class), b -> b.getTags().contains(TAG))) bee.discard();
    }
}
