package com.impendingdoom.events.major;

import com.impendingdoom.ImpendingDoom;
import com.impendingdoom.core.DoomEvent;
import com.impendingdoom.core.EventScheduler;
import com.impendingdoom.entity.NetherflameBottle;
import com.impendingdoom.registry.ModItems;
import com.impendingdoom.util.EventUtil;
import com.impendingdoom.util.GearUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.StructureTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Nether Invasion: ruined portals are rebuilt and lit, and hostile piglins pour out of every lit portal in large
 * groups. They wear gold/netherite, hunt passive mobs like they hunt hoglins, ride (friendly) hoglins and hostile
 * ghasts wearing harnesses, and convert the land with splash potions of Netherflame.
 */
@EventBusSubscriber(modid = ImpendingDoom.MODID)
public class NetherInvasionEvent extends DoomEvent {
    public static final String ID = "nether_invasion";
    public static final String TAG = "impendingdoom.invader";

    public NetherInvasionEvent() {
        super(ID, true, ChatFormatting.DARK_RED);
    }

    @Override
    public void onStart(ServerLevel level, CompoundTag data) {
        for (ServerPlayer player : level.players()) rebuildNearbyRuins(level, data, player.blockPosition());
    }

    @Override
    public void onTick(ServerLevel level, CompoundTag data) {
        long time = level.getGameTime();
        RandomSource r = level.getRandom();
        if (time % 2400 == 1200) {
            for (ServerPlayer player : level.players()) rebuildNearbyRuins(level, data, player.blockPosition());
        }
        if (time % 600 == 0) {
            for (ServerPlayer player : EventUtil.targets(level)) {
                List<BlockPos> portals = litPortals(level, player.blockPosition());
                if (portals.isEmpty() || r.nextInt(3) != 0) continue;
                BlockPos portal = portals.get(r.nextInt(portals.size()));
                long invaders = level.getEntitiesOfClass(Mob.class, new AABB(portal).inflate(64), m -> m.getTags().contains(TAG)).size();
                if (invaders < 24) spawnGroup(level, portal, r);
            }
        }
        if (time % 40 == 0) {
            for (Mob mob : level.getEntities(EntityTypeTest.forClass(Mob.class), m -> m.getTags().contains(TAG) && m.isAlive())) {
                behave(level, mob, r);
            }
        }
    }

    /** Lit portal blocks near a position, one per portal. */
    private static List<BlockPos> litPortals(ServerLevel level, BlockPos center) {
        List<BlockPos> found = level.getPoiManager().findAll(h -> h.is(PoiTypes.NETHER_PORTAL), p -> true, center, 96, PoiManager.Occupancy.ANY)
                .collect(Collectors.toList());
        List<BlockPos> distinct = new ArrayList<>();
        for (BlockPos p : found) if (distinct.stream().noneMatch(d -> d.closerThan(p, 8))) distinct.add(p);
        return distinct;
    }

    private static void rebuildNearbyRuins(ServerLevel level, CompoundTag data, BlockPos center) {
        BlockPos ruin = level.findNearestMapStructure(StructureTags.RUINED_PORTAL, center, 8, false);
        if (ruin == null || !level.isLoaded(ruin) || ruin.distSqr(center) > 160 * 160) return;
        ListTag done = data.getListOrEmpty("rebuilt").copy();
        long key = new BlockPos(ruin.getX() >> 4, 0, ruin.getZ() >> 4).asLong();
        for (int i = 0; i < done.size(); i++) if (done.get(i) instanceof LongTag lt && lt.longValue() == key) return;
        done.add(LongTag.valueOf(key));
        data.put("rebuilt", done);

        // Find the remains of the frame and rebuild a complete, lit portal there.
        BlockPos base = null;
        for (BlockPos p : BlockPos.betweenClosed(ruin.offset(-12, -10, -12), ruin.offset(12, 30, 12))) {
            BlockState s = level.getBlockState(p);
            if (s.is(Blocks.OBSIDIAN) || s.is(Blocks.CRYING_OBSIDIAN)) {
                if (base == null || p.getY() < base.getY()) base = p.immutable();
            }
        }
        if (base == null) base = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ruin);
        buildPortal(level, base);
        for (ServerPlayer p : level.players()) {
            if (p.blockPosition().closerThan(base, 160)) {
                p.sendSystemMessage(Component.translatable("message.impendingdoom.portal_rebuilt").withStyle(ChatFormatting.DARK_RED));
            }
        }
    }

    private static void buildPortal(ServerLevel level, BlockPos origin) {
        BlockState obsidian = Blocks.OBSIDIAN.defaultBlockState();
        BlockState portal = Blocks.NETHER_PORTAL.defaultBlockState().setValue(NetherPortalBlock.AXIS, Direction.Axis.X);
        for (int x = 0; x < 4; x++) {
            for (int y = 0; y < 5; y++) {
                BlockPos p = origin.offset(x, y, 0);
                boolean frame = x == 0 || x == 3 || y == 0 || y == 4;
                level.setBlock(p, frame ? obsidian : Blocks.AIR.defaultBlockState(), 2);
            }
        }
        for (int x = 1; x < 3; x++) {
            for (int y = 1; y < 4; y++) level.setBlock(origin.offset(x, y, 0), portal, 18);
        }
    }

    private static void spawnGroup(ServerLevel level, BlockPos portal, RandomSource r) {
        int size = 4 + r.nextInt(5);
        for (int i = 0; i < size; i++) {
            BlockPos pos = EventUtil.surfacePos(level, portal, 1, 4, r);
            if (pos == null) pos = portal;
            int roll = r.nextInt(20);
            if (roll < 2) {
                // Ghast rider
                Entity g = EventUtil.spawn(level, EntityType.GHAST, pos.above(6));
                if (g instanceof Ghast ghast) {
                    invader(ghast);
                    ghast.setItemSlot(EquipmentSlot.BODY, new ItemStack(Items.RED_HARNESS));
                    AbstractPiglin rider = piglin(level, pos, r, false);
                    if (rider != null) rider.startRiding(ghast, true);
                }
            } else if (roll < 5) {
                Entity h = EventUtil.spawn(level, EntityType.HOGLIN, pos);
                if (h instanceof Hoglin hoglin) {
                    invader(hoglin);
                    hoglin.setImmuneToZombification(true);
                    AbstractPiglin rider = piglin(level, pos, r, false);
                    if (rider != null) rider.startRiding(hoglin, true);
                }
            } else {
                piglin(level, pos, r, roll < 9);
            }
        }
    }

    private static AbstractPiglin piglin(ServerLevel level, BlockPos pos, RandomSource r, boolean brute) {
        Entity e = EventUtil.spawn(level, brute ? EntityType.PIGLIN_BRUTE : EntityType.PIGLIN, pos);
        if (!(e instanceof AbstractPiglin piglin)) return null;
        invader(piglin);
        piglin.setImmuneToZombification(true);
        GearUtil.equipArmorFrom(piglin, r, GearUtil.PIGLIN_SETS, 0.75F);
        ItemStack[] weapons = {new ItemStack(Items.GOLDEN_SWORD), new ItemStack(Items.GOLDEN_AXE), new ItemStack(Items.NETHERITE_SWORD),
                new ItemStack(Items.NETHERITE_AXE), new ItemStack(Items.CROSSBOW)};
        GearUtil.equip(piglin, EquipmentSlot.MAINHAND, weapons[r.nextInt(weapons.length)], 0.085F);
        if (r.nextInt(3) == 0) piglin.getPersistentData().putBoolean("impendingdoom_thrower", true);
        return piglin;
    }

    private static void invader(Mob mob) {
        mob.addTag(TAG);
        mob.setPersistenceRequired();
    }

    private static void behave(ServerLevel level, Mob mob, RandomSource r) {
        if (!(mob instanceof AbstractPiglin piglin)) return;
        LivingEntity target = mob.getTarget();
        if (target == null || !target.isAlive()) {
            // Hostile to players; otherwise hunts passive mobs the way piglins hunt hoglins.
            target = level.getNearestPlayer(mob.getX(), mob.getY(), mob.getZ(), 32, p -> !((Player) p).isCreative() && !p.isSpectator());
            if (target == null) {
                List<Animal> prey = level.getEntitiesOfClass(Animal.class, mob.getBoundingBox().inflate(24), a -> !a.getTags().contains(TAG));
                if (!prey.isEmpty()) target = prey.get(r.nextInt(prey.size()));
            }
            if (target != null) {
                piglin.setTarget(target);
                piglin.getBrain().setMemoryWithExpiry(MemoryModuleType.ANGRY_AT, target.getUUID(), 600L);
                piglin.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, target);
            }
        }
        if (piglin.getPersistentData().getBooleanOr("impendingdoom_thrower", false) && r.nextInt(5) == 0) {
            throwNetherflame(level, piglin, target, r);
        }
    }

    private static void throwNetherflame(ServerLevel level, AbstractPiglin piglin, LivingEntity target, RandomSource r) {
        Vec3 aim = target != null && r.nextBoolean() ? target.position()
                : piglin.position().add(r.nextInt(17) - 8, -1, r.nextInt(17) - 8);
        NetherflameBottle bottle = new NetherflameBottle(level, piglin, new ItemStack(ModItems.NETHERFLAME_BOTTLE.get()));
        Vec3 dir = aim.subtract(piglin.getEyePosition());
        bottle.shoot(dir.x, dir.y + dir.horizontalDistance() * 0.2, dir.z, 0.75F, 6.0F);
        level.addFreshEntity(bottle);
        piglin.swing(net.minecraft.world.InteractionHand.OFF_HAND);
    }

    /** Invaders never fight each other (hoglin mounts included). */
    @SubscribeEvent
    public static void onTarget(LivingChangeTargetEvent event) {
        LivingEntity target = event.getNewAboutToBeSetTarget();
        if (target != null && event.getEntity().getTags().contains(TAG) && target.getTags().contains(TAG)) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.loadedFromDisk() && event.getEntity().getTags().contains(TAG) && !EventScheduler.isActive(ID)) {
            event.setCanceled(true);
        }
    }

    @Override
    public void onEnd(ServerLevel level, CompoundTag data) {
        for (Mob mob : level.getEntities(EntityTypeTest.forClass(Mob.class), m -> m.getTags().contains(TAG))) mob.discard();
    }

}
