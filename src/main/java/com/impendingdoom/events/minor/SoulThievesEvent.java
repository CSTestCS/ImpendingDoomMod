package com.impendingdoom.events.minor;

import com.impendingdoom.ImpendingDoom;
import com.impendingdoom.core.DoomEvent;
import com.impendingdoom.entity.ai.ForcedMeleeGoal;
import com.impendingdoom.util.EventUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

import java.util.EnumSet;
import java.util.List;

/**
 * Soul Thieves: passive mobs become possessed and slowly creep toward players. On contact they siphon experience;
 * after taking more than 10 levels they ignite with soul fire and turn powerful and hostile.
 */
@EventBusSubscriber(modid = ImpendingDoom.MODID)
public class SoulThievesEvent extends DoomEvent {
    public static final String ID = "soul_thieves";
    private static final String TAG = "impendingdoom.possessed";
    private static final String EMPOWERED = "impendingdoom.soul_empowered";
    private static final String LEVELS = "impendingdoom_siphoned";

    public SoulThievesEvent() {
        super(ID, false, ChatFormatting.DARK_GREEN);
    }

    @Override
    public void onTick(ServerLevel level, CompoundTag data) {
        long time = level.getGameTime();
        if (time % 400 == 0) {
            for (ServerPlayer player : EventUtil.targets(level)) {
                List<Animal> animals = level.getEntitiesOfClass(Animal.class, player.getBoundingBox().inflate(32),
                        a -> !a.entityTags().contains(TAG) && !a.isBaby() && !(a instanceof net.minecraft.world.entity.TamableAnimal t && t.isTame()));
                if (animals.isEmpty()) continue;
                possess(animals.get(level.getRandom().nextInt(animals.size())));
            }
        }
        if (time % 10 == 0) {
            for (Mob mob : level.getEntities(EntityTypeTest.forClass(Mob.class), m -> m.entityTags().contains(TAG))) {
                boolean empowered = mob.entityTags().contains(EMPOWERED);
                level.sendParticles(empowered ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.SOUL, mob.getX(), mob.getY() + mob.getBbHeight() * 0.6,
                        mob.getZ(), empowered ? 4 : 1, mob.getBbWidth() * 0.4, mob.getBbHeight() * 0.3, mob.getBbWidth() * 0.4, 0.01);
                if (!empowered) siphon(level, mob);
            }
        }
    }

    private static void possess(Mob mob) {
        mob.addTag(TAG);
        mob.setPersistenceRequired();
        addGoals(mob);
    }

    private static void addGoals(Mob mob) {
        if (mob.entityTags().contains(EMPOWERED)) {
            mob.targetSelector.addGoal(0, new NearestAttackableTargetGoal<>(mob, Player.class, true));
            mob.goalSelector.addGoal(0, new ForcedMeleeGoal(mob, 1.3, 6.0F));
        } else {
            mob.goalSelector.addGoal(0, new CreepGoal(mob));
        }
    }

    private static void siphon(ServerLevel level, Mob mob) {
        for (Player player : level.getEntitiesOfClass(Player.class, mob.getBoundingBox().inflate(0.4), p -> !p.isCreative() && !p.isSpectator())) {
            if (player.experienceLevel <= 0 && player.experienceProgress <= 0) continue;
            if (mob.tickCount % 20 >= 10) continue;
            player.giveExperienceLevels(-1);
            int levels = mob.getPersistentData().getIntOr(LEVELS, 0) + 1;
            mob.getPersistentData().putInt(LEVELS, levels);
            level.playSound(null, player.blockPosition(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.HOSTILE, 1.0F, 0.6F);
            level.sendParticles(ParticleTypes.SCULK_SOUL, player.getX(), player.getY() + 1, player.getZ(), 6, 0.3, 0.5, 0.3, 0.02);
            if (levels > 10) empower(level, mob);
            return;
        }
    }

    private static void empower(ServerLevel level, Mob mob) {
        mob.addTag(EMPOWERED);
        mob.goalSelector.removeAllGoals(g -> g instanceof CreepGoal);
        EventUtil.addModifier(mob, Attributes.SCALE, "soul_scale", 0.5, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        EventUtil.addModifier(mob, Attributes.MAX_HEALTH, "soul_health", 40, AttributeModifier.Operation.ADD_VALUE);
        EventUtil.addModifier(mob, Attributes.MOVEMENT_SPEED, "soul_speed", 0.5, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        mob.setHealth(mob.getMaxHealth());
        mob.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, MobEffectInstance.INFINITE_DURATION, 0, false, false));
        addGoals(mob);
        level.playSound(null, mob.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 0.6F, 1.6F);
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, mob.getX(), mob.getY() + 1, mob.getZ(), 60, 0.6, 0.8, 0.6, 0.05);
        for (Player p : level.getEntitiesOfClass(Player.class, mob.getBoundingBox().inflate(32))) {
            p.sendSystemMessage(Component.translatable("message.impendingdoom.soul_thief_empowered").withStyle(ChatFormatting.DARK_AQUA));
        }
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.loadedFromDisk() && event.getEntity() instanceof Mob mob && mob.entityTags().contains(TAG)) {
            addGoals(mob);
        }
    }

    /** Siphoned souls are released on death. */
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof Mob mob && mob.entityTags().contains(TAG) && mob.level() instanceof ServerLevel level) {
            int levels = mob.getPersistentData().getIntOr(LEVELS, 0);
            if (levels > 0) ExperienceOrb.award(level, mob.position(), levels * 17);
        }
    }

    @Override
    public void onEnd(ServerLevel level, CompoundTag data) {
        // Possession lifts from creatures that never grew strong.
        for (Mob mob : level.getEntities(EntityTypeTest.forClass(Mob.class), m -> m.entityTags().contains(TAG) && !m.entityTags().contains(EMPOWERED))) {
            mob.removeTag(TAG);
            mob.goalSelector.removeAllGoals(g -> g instanceof CreepGoal);
        }
    }

    /** Slowly creeps toward the nearest player. */
    static class CreepGoal extends Goal {
        private final Mob mob;
        private Player target;

        CreepGoal(Mob mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            Player p = mob.level().getNearestPlayer(mob, 32.0);
            target = p != null && !p.isCreative() && !p.isSpectator() ? p : null;
            return target != null;
        }

        @Override
        public void tick() {
            mob.getLookControl().setLookAt(target, 10.0F, 10.0F);
            if (mob.tickCount % 20 == 0) mob.getNavigation().moveTo(target, 0.55);
        }
    }
}
