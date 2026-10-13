package com.impendingdoom.events.minor;

import com.impendingdoom.ImpendingDoom;
import com.impendingdoom.config.DoomConfig;
import com.impendingdoom.core.DoomEvent;
import com.impendingdoom.core.EventScheduler;
import com.impendingdoom.util.EventUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import java.util.List;

/**
 * Monster Mutations: monsters on the valid mutations list spawn bigger and stronger, with random potion effects
 * and a new ability.
 */
@EventBusSubscriber(modid = ImpendingDoom.MODID)
public class MonsterMutationsEvent extends DoomEvent {
    public static final String ID = "monster_mutations";
    public static final String TAG = "impendingdoom.mutated";
    private static final String ABILITY = "impendingdoom_ability";
    private static final String[] ABILITIES = {"leaper", "venomous", "volatile", "blinker", "splitter", "thorny", "webber", "frenzied"};
    private static final List<Holder<MobEffect>> EFFECTS = List.of(MobEffects.SPEED, MobEffects.STRENGTH, MobEffects.RESISTANCE,
            MobEffects.FIRE_RESISTANCE, MobEffects.JUMP_BOOST, MobEffects.REGENERATION, MobEffects.INVISIBILITY, MobEffects.ABSORPTION);

    public MonsterMutationsEvent() {
        super(ID, false, ChatFormatting.DARK_GREEN);
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel) || event.loadedFromDisk() || !EventScheduler.isActive(ID)) return;
        if (event.getEntity() instanceof Mob mob && !mob.entityTags().contains(TAG) && DoomConfig.listContains(DoomConfig.MUTATION_MOBS, mob.getType())) {
            mutate(mob, mob.getRandom());
        }
    }

    public static void mutate(Mob mob, RandomSource random) {
        mob.addTag(TAG);
        double scale = 0.25 + random.nextDouble() * 0.6;
        EventUtil.addModifier(mob, Attributes.SCALE, "mutation_scale", scale, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        EventUtil.addModifier(mob, Attributes.MAX_HEALTH, "mutation_health", 0.5 + random.nextDouble(), AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        EventUtil.addModifier(mob, Attributes.ATTACK_DAMAGE, "mutation_damage", 0.5 + random.nextDouble() * 0.5, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        EventUtil.addModifier(mob, Attributes.MOVEMENT_SPEED, "mutation_speed", 0.1, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        EventUtil.addModifier(mob, Attributes.KNOCKBACK_RESISTANCE, "mutation_knockback", 0.4, AttributeModifier.Operation.ADD_VALUE);
        mob.setHealth(mob.getMaxHealth());
        int effects = 1 + random.nextInt(2);
        for (int i = 0; i < effects; i++) {
            Holder<MobEffect> effect = EFFECTS.get(random.nextInt(EFFECTS.size()));
            mob.addEffect(new MobEffectInstance(effect, MobEffectInstance.INFINITE_DURATION, random.nextInt(2)));
        }
        String ability = ABILITIES[random.nextInt(ABILITIES.length)];
        mob.getPersistentData().putString(ABILITY, ability);
        if (ability.equals("frenzied")) {
            EventUtil.addModifier(mob, Attributes.ATTACK_SPEED, "mutation_frenzy", 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
            EventUtil.addModifier(mob, Attributes.MOVEMENT_SPEED, "mutation_speed", 0.35, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        }
    }

    private static String ability(Entity entity) {
        return entity.entityTags().contains(TAG) ? entity.getPersistentData().getStringOr(ABILITY, "") : "";
    }

    @SubscribeEvent
    public static void onTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof Mob mob) || !(mob.level() instanceof ServerLevel level) || mob.tickCount % 20 != 0) return;
        String ability = ability(mob);
        if (ability.isEmpty()) return;
        LivingEntity target = mob.getTarget();
        if (level.getGameTime() % 60 == 0) {
            level.sendParticles(ParticleTypes.SCULK_SOUL, mob.getX(), mob.getY() + mob.getBbHeight(), mob.getZ(), 1, 0.2, 0.1, 0.2, 0.01);
        }
        if (target == null) return;
        double dist = mob.distanceTo(target);
        switch (ability) {
            case "leaper" -> {
                if (mob.onGround() && dist > 3 && dist < 12 && mob.getRandom().nextInt(3) == 0) {
                    Vec3 dir = target.position().subtract(mob.position()).normalize();
                    mob.setDeltaMovement(dir.x * 1.1, 0.6, dir.z * 1.1);
                    mob.needsSync = true;
                }
            }
            case "blinker" -> {
                if (dist > 4 && dist < 24 && mob.getRandom().nextInt(6) == 0) {
                    level.sendParticles(ParticleTypes.PORTAL, mob.getX(), mob.getY() + 1, mob.getZ(), 30, 0.4, 0.8, 0.4, 0.2);
                    Vec3 behind = target.position().subtract(target.getViewVector(1.0F).multiply(2, 0, 2));
                    mob.randomTeleport(behind.x, target.getY(), behind.z, true, state -> true);
                }
            }
            default -> {
            }
        }
    }

    @SubscribeEvent
    public static void onDamage(LivingDamageEvent.Post event) {
        LivingEntity victim = event.getEntity();
        if (!(victim.level() instanceof ServerLevel level)) return;
        Entity attacker = event.getSource().getEntity();
        if (attacker != null) {
            switch (ability(attacker)) {
                case "venomous" -> victim.addEffect(new MobEffectInstance(victim.getRandom().nextBoolean() ? MobEffects.POISON : MobEffects.WITHER, 100, 0));
                case "webber" -> {
                    BlockPos feet = victim.blockPosition();
                    if (level.getBlockState(feet).isAir() && victim.getRandom().nextInt(3) == 0) level.setBlockAndUpdate(feet, Blocks.COBWEB.defaultBlockState());
                }
                default -> {
                }
            }
        }
        if (ability(victim).equals("thorny") && attacker instanceof LivingEntity living && attacker != victim) {
            living.hurtServer(level, victim.damageSources().thorns(victim), Math.max(1.0F, event.getInflictedDamage() * 0.35F));
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (!(dead.level() instanceof ServerLevel level)) return;
        switch (ability(dead)) {
            case "volatile" -> level.explode(dead, dead.getX(), dead.getY(), dead.getZ(), 2.5F, Level.ExplosionInteraction.MOB);
            case "splitter" -> {
                if (dead.getPersistentData().getBooleanOr("impendingdoom_split", false)) return;
                EntityType<?> type = dead.getType();
                for (int i = 0; i < 2; i++) {
                    EventUtil.defer(() -> {
                        Entity e = type.create(level, net.minecraft.world.entity.EntitySpawnReason.EVENT);
                        if (!(e instanceof Mob child)) return;
                        child.setPos(dead.getX() + level.getRandom().nextGaussian() * 0.5, dead.getY(), dead.getZ() + level.getRandom().nextGaussian() * 0.5);
                        child.addTag(TAG);
                        EventUtil.noReinforcements(child);
                        child.getPersistentData().putBoolean("impendingdoom_split", true);
                        EventUtil.addModifier(child, Attributes.SCALE, "mutation_scale", -0.45, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
                        EventUtil.addModifier(child, Attributes.MAX_HEALTH, "mutation_health", -0.5, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
                        child.setHealth(child.getMaxHealth());
                        level.addFreshEntity(child);
                        if (child instanceof net.minecraft.world.entity.monster.creaking.Creaking creaking) {
                            com.impendingdoom.util.CreakingCompanions.attach(level, creaking);
                        }
                    });
                }
            }
            default -> {
            }
        }
    }
}
