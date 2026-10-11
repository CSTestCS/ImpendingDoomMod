package com.impendingdoom.client;

import com.impendingdoom.registry.ModEntities;
import com.impendingdoom.network.HallucinationPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Client-only fake entities for Paranoia. They exist only in one player's world, never on the server, and vanish
 * when looked at directly or after a few seconds.
 */
public final class Hallucinations {
    private static final EntityType<?>[] MOBS = {EntityType.ZOMBIE, EntityType.SKELETON, EntityType.CREEPER, EntityType.ENDERMAN,
            EntityType.SPIDER, EntityType.WITCH, EntityType.CREAKING};
    private static final Map<Integer, Integer> LIVES = new HashMap<>();
    private static int nextId = -1_000_000;

    private Hallucinations() {}

    public static void spawn(HallucinationPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        if (level == null || player == null) return;
        EntityType<?> type = payload.kind() == 1 ? ModEntities.HEROBRINE.get() : MOBS[level.getRandom().nextInt(MOBS.length)];
        Entity entity = type.create(level, EntitySpawnReason.LOAD);
        if (entity == null) return;
        entity.setId(nextId--);
        entity.setPos(payload.x(), payload.y(), payload.z());
        float yaw = (float) (Mth.atan2(player.getZ() - payload.z(), player.getX() - payload.x()) * Mth.RAD_TO_DEG) - 90.0F;
        entity.setYRot(yaw);
        entity.setYHeadRot(yaw);
        if (entity instanceof Mob mob) {
            mob.setNoAi(true);
            mob.yBodyRot = yaw;
        }
        entity.setInvisible(false);
        level.addEntity(entity);
        LIVES.put(entity.getId(), 60 + level.getRandom().nextInt(140));
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        if (level == null || player == null) {
            LIVES.clear();
            return;
        }
        Iterator<Map.Entry<Integer, Integer>> it = LIVES.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Integer> e = it.next();
            Entity entity = level.getEntity(e.getKey());
            int life = e.getValue() - 1;
            boolean seen = false;
            if (entity != null) {
                Vec3 to = entity.position().add(0, entity.getBbHeight() * 0.8, 0).subtract(player.getEyePosition()).normalize();
                seen = player.getViewVector(1.0F).dot(to) > 0.985;
            }
            if (entity == null || life <= 0 || seen) {
                if (entity != null) level.removeEntity(e.getKey(), Entity.RemovalReason.DISCARDED);
                it.remove();
            } else {
                e.setValue(life);
            }
        }
    }
}
