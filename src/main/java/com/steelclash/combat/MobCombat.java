package com.steelclash.combat;

import com.steelclash.Config;
import com.steelclash.ai.ClashBrain;
import com.steelclash.core.AttackType;
import com.steelclash.core.CombatStateMachine;
import com.steelclash.core.Guard;
import com.steelclash.core.Phase;
import com.steelclash.profile.WeaponProfile;
import com.steelclash.profile.WeaponProfiles;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

/**
 * M2 mob combat: telegraphed attacks and simple reactive parries. The full bot brain (spacing, feints, attack
 * tokens) comes in M5.
 */
public final class MobCombat {
    /** How far a mob looks for incoming attacks to react to. */
    private static final double THREAT_RADIUS = 5.0;
    /** Attackers must be roughly facing the mob for it to treat their windup as a threat. */
    private static final double THREAT_CONE = 120;

    private MobCombat() {
    }

    public static boolean isFighter(Entity entity) {
        return entity instanceof Mob mob && mob.getType().is(WeaponProfiles.FIGHTERS);
    }

    /**
     * A fighter mob's vanilla melee hit (instant, unreadable) arrives: cancel it and start a telegraphed Steel Clash
     * attack instead. Works for goal-based and brain-based mobs alike, since both end in {@code doHurtTarget}.
     *
     * @return true if the vanilla damage must be cancelled
     */
    public static boolean interceptVanillaMelee(DamageSource source) {
        if (!Config.TELEGRAPH_MOB_ATTACKS.get() || !(source.getDirectEntity() instanceof Mob mob) || !isFighter(mob)) {
            return false;
        }
        if (!source.is(DamageTypes.MOB_ATTACK) && !source.is(DamageTypes.MOB_ATTACK_NO_AGGRO)) {
            return false;
        }
        Optional<WeaponProfiles.Resolved> profile = WeaponProfiles.resolveFor(mob);
        if (profile.isEmpty() || profile.get().profile().attacks().isEmpty()) {
            return false; // nothing to telegraph with: let vanilla hit
        }
        if (ClashBrain.manages(mob)) {
            return true; // the bot brain decides when to attack; just swallow the instant vanilla hit
        }
        CombatData data = mob.getData(ModAttachments.COMBAT);
        if (!data.machine.isBusy()) {
            Combat.requestAttack(mob, pickAttack(mob, profile.get().profile()));
            if (mob.getRandom().nextDouble() < Config.MOB_HEAVY_CHANCE.get()) {
                Combat.requestHeavy(mob);
            }
        }
        return true;
    }

    private static AttackType pickAttack(Mob mob, WeaponProfile profile) {
        List<AttackType> options = new ArrayList<>(profile.attacks().keySet());
        options.remove(AttackType.KICK);
        options.sort(null);
        return options.get(mob.getRandom().nextInt(options.size()));
    }

    /** Each tick: if someone is winding up an attack at this mob, maybe raise a parry just before it lands. */
    public static void tickDefense(LivingEntity mob, CombatData data, double parryChance) {
        if (parryChance <= 0 || !data.machine.canParry()) {
            return;
        }
        Optional<WeaponProfile.GuardSpec> guard = WeaponProfiles.resolveFor(mob).flatMap(r -> r.profile().guard());
        if (guard.isEmpty()) {
            return;
        }
        List<LivingEntity> threats = mob.level().getEntitiesOfClass(LivingEntity.class, mob.getBoundingBox().inflate(THREAT_RADIUS),
                e -> e != mob && e.hasData(ModAttachments.COMBAT)
                        && e.getData(ModAttachments.COMBAT).machine.phase() == Phase.WINDUP);
        for (LivingEntity attacker : threats) {
            if (!Guard.inCone(CombatMath.viewYaw(attacker), attacker.getX(), attacker.getZ(), mob.getX(), mob.getZ(), THREAT_CONE)) {
                continue;
            }
            CombatStateMachine incoming = attacker.getData(ModAttachments.COMBAT).machine;
            if (data.reactedAttackerId != attacker.getId() || data.reactedSerial != incoming.attackSerial()) {
                data.reactedAttackerId = attacker.getId();
                data.reactedSerial = incoming.attackSerial();
                data.willParry = mob.getRandom().nextDouble() < parryChance;
            }
            // Raise the guard just before the blade goes live, like a player reading the windup.
            if (data.willParry && incoming.ticksLeftInPhase() <= 2) {
                if (Combat.startParry(mob, data)) {
                    Combat.sync(mob, data, false);
                }
                return;
            }
        }
    }

    public static double parryChance(Mob mob) {
        Difficulty difficulty = mob.level().getDifficulty();
        return switch (difficulty) {
            case PEACEFUL, EASY -> Config.MOB_PARRY_CHANCE_EASY.get();
            case NORMAL -> Config.MOB_PARRY_CHANCE_NORMAL.get();
            case HARD -> Config.MOB_PARRY_CHANCE_HARD.get();
        };
    }
}
