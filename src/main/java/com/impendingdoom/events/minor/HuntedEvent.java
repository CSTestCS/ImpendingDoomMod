package com.impendingdoom.events.minor;

import com.impendingdoom.ImpendingDoom;
import com.impendingdoom.config.DoomConfig;
import com.impendingdoom.core.DoomEvent;
import com.impendingdoom.core.DoomSavedData;
import com.impendingdoom.core.EventScheduler;
import com.impendingdoom.entity.HeartWalker;
import com.impendingdoom.entity.ai.ForcedMeleeGoal;
import com.impendingdoom.entity.ai.MoveToPosGoal;
import com.impendingdoom.util.CreakingCompanions;
import com.impendingdoom.util.EventUtil;
import com.impendingdoom.util.GearUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.creaking.Creaking;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Hunted: each player gets a pack of five named, armed hunters picked from every neutral and hostile mob
 * (modded ones included). Hunters never fight each other, are regularly told their target's coordinates, and
 * upgrade themselves every time they kill their target. Surviving hunters are remembered and return the next time
 * this event happens, with fresh recruits replacing the fallen.
 */
@EventBusSubscriber(modid = ImpendingDoom.MODID)
public class HuntedEvent extends DoomEvent {
    public static final String ID = "hunted";
    public static final String TAG = "impendingdoom.hunter";
    private static final String OWNER = "impendingdoom_hunter_owner";
    private static final String SLOT = "impendingdoom_hunter_slot";
    private static final String GEN = "impendingdoom_hunter_gen";
    private static final String COORDS = "impendingdoom_hunter_coords";
    private static final int PACK_SIZE = 5;
    private static final List<Holder<MobEffect>> UPGRADE_EFFECTS = List.of(MobEffects.SPEED, MobEffects.STRENGTH, MobEffects.RESISTANCE,
            MobEffects.FIRE_RESISTANCE, MobEffects.JUMP_BOOST, MobEffects.HEALTH_BOOST, MobEffects.ABSORPTION, MobEffects.WATER_BREATHING,
            MobEffects.HASTE, MobEffects.SLOW_FALLING, MobEffects.INVISIBILITY, MobEffects.DOLPHINS_GRACE, MobEffects.NIGHT_VISION);
    private static final String[] FIRST = {"Grim", "Vex", "Morr", "Skar", "Ash", "Krag", "Nyx", "Rot", "Thorn", "Bane", "Gloam", "Drak",
            "Silt", "Wrath", "Hollow", "Crag", "Murk", "Fang", "Blight", "Sorrow"};
    private static final String[] LAST = {"jaw", "claw", "maw", "eye", "bone", "tooth", "hide", "heart", "tusk", "shade", "spine", "scar"};
    private static final String[] EPITHET = {"the Relentless", "the Patient", "the Unseen", "the Hungry", "Who Waits", "the Tracker",
            "of the Long Night", "the Merciless", "the Cruel", "Who Never Sleeps", "the Butcher", "the Collector"};
    private static final String[] TAUNTS = {"message.impendingdoom.taunt.1", "message.impendingdoom.taunt.2", "message.impendingdoom.taunt.3",
            "message.impendingdoom.taunt.4", "message.impendingdoom.taunt.5"};

    private static List<EntityType<?>> candidates;

    public HuntedEvent() {
        super(ID, false, ChatFormatting.DARK_GREEN);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        candidates = null;
    }

    /** Every neutral or hostile mob in the game, modded included, minus the blacklist. */
    private static List<EntityType<?>> candidates(ServerLevel level) {
        if (candidates != null) return candidates;
        List<EntityType<?>> list = new ArrayList<>();
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            if (!type.canSummon() || DoomConfig.listContains(DoomConfig.HUNTER_BLACKLIST, type)) continue;
            try {
                Entity e = type.create(level, EntitySpawnReason.EVENT);
                if (e instanceof Mob && (e instanceof Enemy || e instanceof NeutralMob)) list.add(type);
                if (e != null) e.discard();
            } catch (RuntimeException ignored) {
                // Some modded entities cannot be created this way.
            }
        }
        if (list.isEmpty()) list.add(EntityTypes.ZOMBIE);
        candidates = list;
        return list;
    }

    private static String randomName(RandomSource r) {
        return FIRST[r.nextInt(FIRST.length)] + LAST[r.nextInt(LAST.length)] + " " + EPITHET[r.nextInt(EPITHET.length)];
    }

    private static CompoundTag newMember(ServerLevel level) {
        List<EntityType<?>> list = candidates(level);
        EntityType<?> type = list.get(level.getRandom().nextInt(list.size()));
        CompoundTag m = new CompoundTag();
        m.putString("type", BuiltInRegistries.ENTITY_TYPE.getKey(type).toString());
        m.putString("name", randomName(level.getRandom()));
        m.putBoolean("alive", true);
        m.put("effects", new ListTag());
        return m;
    }

    private static CompoundTag pack(DoomSavedData d, UUID player) {
        return d.hunters.getCompoundOrEmpty(player.toString());
    }

    @Override
    public void onStart(ServerLevel level, CompoundTag data) {
        for (ServerPlayer player : EventUtil.targets(level)) {
            unleash(level, player);
        }
    }

    @Override
    public void onPlayerJoin(ServerPlayer player, CompoundTag data) {
        if (player.level() instanceof ServerLevel level && level == level.getServer().overworld()) unleash(level, player);
    }

    /** Builds (or refills) the player's pack and spawns it some distance away. */
    private static void unleash(ServerLevel level, ServerPlayer player) {
        DoomSavedData d = EventScheduler.data();
        if (d == null) return;
        CompoundTag pack = pack(d, player.getUUID()).copy();
        ListTag members = pack.getListOrEmpty("members").copy();
        while (members.size() < PACK_SIZE) members.add(newMember(level));
        for (int i = 0; i < members.size(); i++) {
            if (!members.getCompoundOrEmpty(i).getBooleanOr("alive", true)) members.set(i, newMember(level));
        }
        int gen = pack.getIntOr("gen", 0) + 1;
        pack.putInt("gen", gen);
        pack.put("members", members);
        d.hunters.put(player.getUUID().toString(), pack);
        d.setDirty();

        BlockPos origin = EventUtil.surfacePos(level, player.blockPosition(), 48, 64, level.getRandom());
        if (origin == null) return;
        for (int i = 0; i < members.size(); i++) {
            spawnMember(level, player, members.getCompoundOrEmpty(i), i, gen, origin);
        }
        player.sendSystemMessage(Component.translatable("message.impendingdoom.hunted").withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC));
    }

    private static void spawnMember(ServerLevel level, ServerPlayer owner, CompoundTag record, int slot, int gen, BlockPos origin) {
        Identifier typeId = Identifier.tryParse(record.getStringOr("type", "minecraft:zombie"));
        EntityType<?> type = typeId == null ? EntityTypes.ZOMBIE : BuiltInRegistries.ENTITY_TYPE.getOptional(typeId).orElse(EntityTypes.ZOMBIE);
        BlockPos pos = EventUtil.surfacePos(level, origin, 0, 4, level.getRandom());
        Entity entity = EventUtil.spawn(level, type, pos == null ? origin : pos);
        if (!(entity instanceof Mob mob)) {
            if (entity != null) entity.discard();
            return;
        }
        tagHunter(mob, owner.getUUID(), slot, gen);
        mob.setCustomName(Component.literal(record.getStringOr("name", "Hunter")).withStyle(ChatFormatting.RED));
        mob.setCustomNameVisible(true);
        mob.setPersistenceRequired();
        RandomSource r = level.getRandom();
        GearUtil.equipArmor(mob, r, 2, 4, 0.85F);
        // Sun-burning mobs are protected by an indestructible helmet (every hunter gets one).
        Holder<net.minecraft.world.item.Item> helmet = BuiltInRegistries.ITEM.wrapAsHolder(r.nextBoolean() ? Items.IRON_HELMET : Items.DIAMOND_HELMET);
        GearUtil.equip(mob, EquipmentSlot.HEAD, GearUtil.unbreakable(new ItemStack(helmet)), 0.0F);
        if (mob.getMainHandItem().isEmpty()) GearUtil.equip(mob, EquipmentSlot.MAINHAND, GearUtil.randomMeleeWeapon(r), 0.085F);
        if (mob instanceof AbstractPiglin piglin) piglin.setImmuneToZombification(true);
        if (mob instanceof Hoglin hoglin) hoglin.setImmuneToZombification(true);
        EventUtil.addModifier(mob, Attributes.FOLLOW_RANGE, "hunter_range", 64, AttributeModifier.Operation.ADD_VALUE);
        applyUpgrades(mob, record);
        mob.setHealth(mob.getMaxHealth());
        mob.getPersistentData().putLong(COORDS, owner.blockPosition().asLong());
        setupGoals(mob);
        if (mob instanceof Creaking creaking) {
            HeartWalker heart = CreakingCompanions.heartOf(level, creaking);
            if (heart != null) tagHunter(heart, owner.getUUID(), -1, gen); // ally, not counted as a hunter
        }
    }

    private static void tagHunter(Mob mob, UUID owner, int slot, int gen) {
        mob.addTag(TAG);
        mob.getPersistentData().putString(OWNER, owner.toString());
        mob.getPersistentData().putInt(SLOT, slot);
        mob.getPersistentData().putInt(GEN, gen);
    }

    private static void setupGoals(Mob mob) {
        mob.goalSelector.addGoal(3, new MoveToPosGoal(mob, () -> {
            long packed = mob.getPersistentData().getLongOr(COORDS, Long.MIN_VALUE);
            return packed == Long.MIN_VALUE ? null : BlockPos.of(packed);
        }, 1.15, 4));
        if (mob instanceof PathfinderMob && !EventUtil.hasAttribute(mob, Attributes.ATTACK_DAMAGE)) {
            mob.goalSelector.addGoal(2, new ForcedMeleeGoal(mob, 1.2, 4.0F));
        }
    }

    private static void applyUpgrades(Mob mob, CompoundTag record) {
        ListTag effects = record.getListOrEmpty("effects");
        for (int i = 0; i < effects.size(); i++) {
            Identifier id = Identifier.tryParse(effects.getStringOr(i, ""));
            if (id == null) continue;
            BuiltInRegistries.MOB_EFFECT.get(id).ifPresent(holder -> {
                MobEffectInstance existing = mob.getEffect(holder);
                int amp = existing == null ? 0 : existing.getAmplifier() + 1;
                mob.addEffect(new MobEffectInstance(holder, MobEffectInstance.INFINITE_DURATION, Math.min(amp, 3)));
            });
        }
        EventUtil.addModifier(mob, Attributes.MAX_HEALTH, "hunter_health", record.getDoubleOr("health", 0), AttributeModifier.Operation.ADD_VALUE);
        EventUtil.addModifier(mob, Attributes.ATTACK_DAMAGE, "hunter_damage", record.getDoubleOr("damage", 0), AttributeModifier.Operation.ADD_VALUE);
        EventUtil.addModifier(mob, Attributes.MOVEMENT_SPEED, "hunter_speed", record.getDoubleOr("speed", 0), AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        EventUtil.addModifier(mob, Attributes.ARMOR, "hunter_armor", record.getDoubleOr("armor", 0), AttributeModifier.Operation.ADD_VALUE);
    }

    private static UUID owner(Entity e) {
        return e.entityTags().contains(TAG) ? EventUtil.uuid(e.getPersistentData(), OWNER) : null;
    }

    @Override
    public void onTick(ServerLevel level, CompoundTag data) {
        long time = level.getGameTime();
        if (time % 20 == 0) {
            for (Mob hunter : level.getEntities(EntityTypeTest.forClass(Mob.class), m -> m.entityTags().contains(TAG) && m.isAlive())) {
                UUID ownerId = owner(hunter);
                ServerPlayer owner = ownerId == null ? null : (ServerPlayer) level.getPlayerByUUID(ownerId);
                if (owner == null || owner.isCreative() || owner.isSpectator()) continue;
                // Every few minutes the pack is told where its prey is.
                if (time % 2400 == 0) hunter.getPersistentData().putLong(COORDS, owner.blockPosition().asLong());
                if (hunter.getPersistentData().getIntOr(SLOT, 0) < 0) continue; // walking heart ally
                if (hunter.distanceToSqr(owner) < 40 * 40 && hunter.getTarget() != owner) {
                    hunter.setTarget(owner);
                    hunter.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, owner);
                    hunter.getBrain().setMemoryWithExpiry(MemoryModuleType.ANGRY_AT, owner.getUUID(), 600L);
                    if (hunter instanceof NeutralMob neutral) neutral.startPersistentAngerTimer();
                    if (hunter instanceof Warden warden) warden.increaseAngerAt(owner, 150, false);
                }
            }
        }
        // Packs that are no longer loaded (the player travelled far) are re-summoned near their prey.
        if (time % 1200 == 600) {
            DoomSavedData d = EventScheduler.data();
            if (d == null) return;
            for (ServerPlayer player : EventUtil.targets(level)) {
                CompoundTag pack = pack(d, player.getUUID());
                int gen = pack.getIntOr("gen", 0);
                boolean anyAlive = false;
                ListTag members = pack.getListOrEmpty("members");
                for (int i = 0; i < members.size(); i++) anyAlive |= members.getCompoundOrEmpty(i).getBooleanOr("alive", false);
                if (!anyAlive) continue;
                boolean loaded = !level.getEntities(EntityTypeTest.forClass(Mob.class),
                        m -> player.getUUID().equals(owner(m)) && m.getPersistentData().getIntOr(GEN, -1) == gen).isEmpty();
                if (!loaded) resummon(level, player, d);
            }
        }
    }

    private static void resummon(ServerLevel level, ServerPlayer player, DoomSavedData d) {
        CompoundTag pack = pack(d, player.getUUID()).copy();
        int gen = pack.getIntOr("gen", 0) + 1;
        pack.putInt("gen", gen);
        d.hunters.put(player.getUUID().toString(), pack);
        d.setDirty();
        BlockPos origin = EventUtil.surfacePos(level, player.blockPosition(), 48, 64, level.getRandom());
        if (origin == null) return;
        ListTag members = pack.getListOrEmpty("members");
        for (int i = 0; i < members.size(); i++) {
            CompoundTag m = members.getCompoundOrEmpty(i);
            if (m.getBooleanOr("alive", false)) spawnMember(level, player, m, i, gen, origin);
        }
    }

    @Override
    public void onEnd(ServerLevel level, CompoundTag data) {
        // Hunters vanish; survivors are remembered for next time.
        for (Mob m : level.getEntities(EntityTypeTest.forClass(Mob.class), m -> m.entityTags().contains(TAG))) {
            m.discard();
        }
    }

    /** Stale hunters (old generation, or event over) are removed when they load. */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !event.loadedFromDisk() || !(event.getEntity() instanceof Mob mob)) return;
        UUID ownerId = owner(mob);
        if (ownerId == null) return;
        DoomSavedData d = EventScheduler.data();
        if (!EventScheduler.isActive(ID) || d == null || mob.getPersistentData().getIntOr(GEN, -1) != pack(d, ownerId).getIntOr("gen", 0)) {
            event.setCanceled(true);
            return;
        }
        setupGoals(mob);
    }

    /** No hostility within a pack. */
    @SubscribeEvent
    public static void onTarget(LivingChangeTargetEvent event) {
        LivingEntity target = event.getNewAboutToBeSetTarget();
        if (target == null) return;
        UUID a = owner(event.getEntity());
        if (a != null && a.equals(owner(target))) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onFriendlyFire(LivingIncomingDamageEvent event) {
        Entity attacker = event.getSource().getEntity();
        if (attacker == null) return;
        UUID a = owner(attacker);
        if (a != null && a.equals(owner(event.getEntity()))) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel level)) return;
        DoomSavedData d = EventScheduler.data();
        if (d == null) return;
        LivingEntity dead = event.getEntity();
        // A hunter fell: it will be replaced next time.
        UUID ownerId = owner(dead);
        int slot = dead.getPersistentData().getIntOr(SLOT, -1);
        if (ownerId != null && slot >= 0) {
            CompoundTag pack = pack(d, ownerId).copy();
            ListTag members = pack.getListOrEmpty("members").copy();
            if (slot < members.size()) {
                CompoundTag m = members.getCompoundOrEmpty(slot).copy();
                m.putBoolean("alive", false);
                members.set(slot, m);
                pack.put("members", members);
                d.hunters.put(ownerId.toString(), pack);
                d.setDirty();
            }
        }
        // A hunter killed its prey: taunt and upgrade.
        Entity killer = event.getSource().getEntity();
        if (dead instanceof ServerPlayer player && killer instanceof Mob hunter && player.getUUID().equals(owner(hunter))) {
            int hunterSlot = hunter.getPersistentData().getIntOr(SLOT, -1);
            if (hunterSlot < 0) return;
            level.getServer().getPlayerList().broadcastSystemMessage(Component.literal("<").append(hunter.getDisplayName()).append("> ")
                    .append(Component.translatable(TAUNTS[level.getRandom().nextInt(TAUNTS.length)], player.getDisplayName())), false);
            upgrade(level, d, hunter, player.getUUID(), hunterSlot);
        }
    }

    private static void upgrade(ServerLevel level, DoomSavedData d, Mob hunter, UUID ownerId, int slot) {
        CompoundTag pack = pack(d, ownerId).copy();
        ListTag members = pack.getListOrEmpty("members").copy();
        if (slot >= members.size()) return;
        CompoundTag m = members.getCompoundOrEmpty(slot).copy();
        RandomSource r = level.getRandom();
        switch (r.nextInt(5)) {
            case 0 -> m.putDouble("health", m.getDoubleOr("health", 0) + 6);
            case 1 -> m.putDouble("damage", m.getDoubleOr("damage", 0) + 2);
            case 2 -> m.putDouble("speed", m.getDoubleOr("speed", 0) + 0.08);
            case 3 -> m.putDouble("armor", m.getDoubleOr("armor", 0) + 3);
            default -> {
                // Regeneration and instant health are deliberately not in this list.
                Holder<MobEffect> effect = UPGRADE_EFFECTS.get(r.nextInt(UPGRADE_EFFECTS.size()));
                ListTag effects = m.getListOrEmpty("effects").copy();
                effects.add(StringTag.valueOf(BuiltInRegistries.MOB_EFFECT.getKey(effect.value()).toString()));
                m.put("effects", effects);
            }
        }
        members.set(slot, m);
        pack.put("members", members);
        d.hunters.put(ownerId.toString(), pack);
        d.setDirty();
        applyUpgrades(hunter, m);
        hunter.heal(hunter.getMaxHealth());
    }
}
