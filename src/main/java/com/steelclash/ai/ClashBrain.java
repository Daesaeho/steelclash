package com.steelclash.ai;

import com.steelclash.Config;
import com.steelclash.combat.Combat;
import com.steelclash.combat.CombatData;
import com.steelclash.combat.CombatMath;
import com.steelclash.combat.MobCombat;
import com.steelclash.combat.ModAttachments;
import com.steelclash.core.AttackTokens;
import com.steelclash.core.AttackType;
import com.steelclash.core.BotSkill;
import com.steelclash.core.BotStyle;
import com.steelclash.core.CombatStateMachine;
import com.steelclash.core.Guard;
import com.steelclash.core.Phase;
import com.steelclash.profile.WeaponProfile;
import com.steelclash.profile.WeaponProfiles;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.common.ItemAbilities;

/**
 * Chivalry 2-style bot behaviour for fighter mobs, layered on top of their vanilla AI (vanilla still does pathing
 * toward the target; {@link ClashSpacingGoal} takes over movement while the bot holds back).
 * <ul>
 *     <li>Offense: starts attacks itself when in reach and holding an attack token; mixes types, heavies, feints and
 *     morphs; ripostes and combos; kicks opponents who turtle.</li>
 *     <li>Defense: parries only windups it has seen for its reaction time; parries late against feinters; counters
 *     habitual attacks.</li>
 *     <li>Crowds: {@link AttackTokens} limit how many bots swing at one target; the rest circle.</li>
 * </ul>
 */
public final class ClashBrain {
    /** Entity ids are unique across dimensions, so one token table serves the whole server. */
    public static final AttackTokens TOKENS = new AttackTokens();
    private static final double THREAT_RADIUS = 5.0;
    private static final double THREAT_CONE = 120;
    /** Start swinging this far inside full reach, so the blade actually arrives. */
    private static final double REACH_MARGIN = 0.3;
    private static final double KICK_RANGE = 1.8;

    private ClashBrain() {
    }

    /** Whether the brain drives this mob right now (otherwise the simpler M2 telegraph + reactive parry applies). */
    public static boolean manages(Entity entity) {
        return Config.BOT_BRAIN.get() && entity instanceof PathfinderMob mob && MobCombat.isFighter(mob)
                && mob.getTarget() != null && mob.getTarget().isAlive();
    }

    public static BotSkill skill(Mob mob) {
        BotSkill base = BotSkill.forDifficulty(mob.level().getDifficulty().getId());
        int attackers = switch (mob.level().getDifficulty()) {
            case PEACEFUL, EASY -> Config.MAX_ATTACKERS_EASY.get();
            case NORMAL -> Config.MAX_ATTACKERS_NORMAL.get();
            case HARD -> Config.MAX_ATTACKERS_HARD.get();
        };
        return base.withParryChance(MobCombat.parryChance(mob)).withAttackers(attackers);
    }

    public static void tick(PathfinderMob mob, CombatData data) {
        LivingEntity target = mob.getTarget();
        BrainState brain = data.brain != null ? data.brain : (data.brain = new BrainState());
        if (target == null || !target.isAlive()) {
            TOKENS.releaseAll(mob.getId());
            brain.wantsSpace = false;
            return;
        }
        BotSkill skill = skill(mob);
        observe(brain, target);
        defend(mob, data, brain, skill);
        offend(mob, data, brain, skill, target);
    }

    private static void observe(BrainState brain, LivingEntity target) {
        brain.memory.track(target.getId());
        if (target.hasData(ModAttachments.COMBAT)) {
            CombatStateMachine m = target.getData(ModAttachments.COMBAT).machine;
            brain.memory.observe(m.phase(), m.type(), m.attackSerial(), target.isBlocking() || m.phase() == Phase.PARRY);
        } else {
            brain.memory.observe(Phase.IDLE, AttackType.SLASH, -1, target.isBlocking());
        }
    }

    // ------------------------------------------------------------------ defense

    private static void defend(PathfinderMob mob, CombatData data, BrainState brain, BotSkill skill) {
        if (WeaponProfiles.resolveFor(mob).flatMap(r -> r.profile().guard()).isEmpty() && !hasShield(mob)) {
            return; // claws and beasts can't parry
        }
        for (LivingEntity attacker : threats(mob)) {
            CombatStateMachine incoming = attacker.getData(ModAttachments.COMBAT).machine;
            if (brain.answeredAttacker != attacker.getId() || brain.answeredSerial != incoming.attackSerial()) {
                brain.answeredAttacker = attacker.getId();
                brain.answeredSerial = incoming.attackSerial();
                brain.answer = decide(mob.getRandom(), brain, skill, incoming.type(), hasShield(mob));
                if (brain.answer == BrainState.Answer.NONE && mob.getRandom().nextDouble() < BotStyles.of(mob).evadeChance()) {
                    brain.evadeUntil = mob.tickCount + 10; // won't parry this one: step out of reach instead
                }
            }
            // Carrying a shield: raise it as soon as the windup has been read (a shield needs 5 ticks to come up).
            if (hasShield(mob) && brain.answer != BrainState.Answer.NONE && brain.answer != BrainState.Answer.COUNTER) {
                if (skill.canReact(incoming.phaseTick()) && freeToDefend(mob, data, incoming)) {
                    if (!mob.isUsingItem()) {
                        mob.startUsingItem(InteractionHand.OFF_HAND);
                    }
                    brain.shieldDownAt = mob.tickCount + incoming.ticksLeftInPhase() + incoming.timings().release() + 6;
                    brain.answer = BrainState.Answer.NONE;
                }
                return;
            }
            switch (brain.answer) {
                case COUNTER -> {
                    // Start the same attack shortly before theirs lands; the counter window does the rest.
                    if (incoming.phase() == Phase.WINDUP && incoming.ticksLeftInPhase() <= 5 && data.machine.canStartAttack()) {
                        Combat.requestAttack(mob, incoming.type());
                        brain.answer = BrainState.Answer.NONE;
                    }
                }
                case PARRY -> {
                    if (incoming.phase() == Phase.WINDUP && incoming.ticksLeftInPhase() <= 2) {
                        if (skill.canReact(incoming.phaseTick()) && freeToDefend(mob, data, incoming)) {
                            raiseParry(mob, data);
                        }
                        brain.answer = BrainState.Answer.NONE;
                    }
                }
                case LATE_PARRY -> {
                    // Against feinters: wait until the blade is actually coming.
                    if (incoming.phase() == Phase.RELEASE) {
                        if (skill.canReact(incoming.timings().windup()) && freeToDefend(mob, data, incoming)) {
                            raiseParry(mob, data);
                        }
                        brain.answer = BrainState.Answer.NONE;
                    }
                }
                case NONE -> {
                }
            }
            return; // answer one threat at a time
        }
    }

    static boolean hasShield(LivingEntity mob) {
        return mob.getOffhandItem().canPerformAction(ItemAbilities.SHIELD_BLOCK);
    }

    private static BrainState.Answer decide(RandomSource random, BrainState brain, BotSkill skill, AttackType incomingType,
                                            boolean shield) {
        if (incomingType == AttackType.KICK) {
            return BrainState.Answer.NONE; // kicks can't be parried
        }
        if (incomingType == brain.memory.habit() && random.nextDouble() < skill.counterChance()) {
            return BrainState.Answer.COUNTER;
        }
        // Holding a shield up is far easier than timing a parry.
        double chance = shield ? Math.min(1, skill.parryChance() * 2) : skill.parryChance();
        if (random.nextDouble() >= chance) {
            return BrainState.Answer.NONE;
        }
        // A known feinter gets parried late, which can only catch the real attack and is a bit harder to pull off.
        if (brain.memory.isFeinter()) {
            return random.nextDouble() < 0.7 ? BrainState.Answer.LATE_PARRY : BrainState.Answer.NONE;
        }
        return BrainState.Answer.PARRY;
    }

    private static void raiseParry(PathfinderMob mob, CombatData data) {
        // startParry also cancels a windup into the parry (Chivalry 2 parry-cancel), costing stamina.
        if (Combat.startParry(mob, data)) {
            Combat.sync(mob, data, false);
        }
    }

    /**
     * Can the bot defend right now? Yes if it's free to parry, or if it's winding up an attack that would land
     * <em>after</em> the incoming one: then it cancels its own windup into the defense (Chivalry 2 parry-cancel).
     * If its own swing lands first, it keeps swinging and trades.
     */
    private static boolean freeToDefend(PathfinderMob mob, CombatData data, CombatStateMachine incoming) {
        CombatStateMachine own = data.machine;
        if (own.canParry()) {
            return true;
        }
        if (own.phase() == Phase.WINDUP && own.type() != AttackType.KICK && own.ticksLeftInPhase() > incoming.ticksLeftInPhase()) {
            Combat.requestFeint(mob);
            return own.canParry();
        }
        return false;
    }

    private static List<LivingEntity> threats(PathfinderMob mob) {
        List<LivingEntity> result = new ArrayList<>();
        for (LivingEntity e : mob.level().getEntitiesOfClass(LivingEntity.class, mob.getBoundingBox().inflate(THREAT_RADIUS),
                e -> e != mob && e.hasData(ModAttachments.COMBAT) && e.getData(ModAttachments.COMBAT).machine.phase() == Phase.WINDUP)) {
            if (Guard.inCone(CombatMath.viewYaw(e), e.getX(), e.getZ(), mob.getX(), mob.getZ(), THREAT_CONE)) {
                result.add(e);
            }
        }
        return result;
    }

    // ------------------------------------------------------------------ offense

    private static void offend(PathfinderMob mob, CombatData data, BrainState brain, BotSkill skill, LivingEntity target) {
        CombatStateMachine m = data.machine;
        RandomSource random = mob.getRandom();
        Optional<WeaponProfile> profile = WeaponProfiles.resolveFor(mob).map(WeaponProfiles.Resolved::profile);
        if (profile.isEmpty() || profile.get().attacks().isEmpty()) {
            return;
        }
        double reach = reach(mob, profile.get());
        double distance = edgeDistance(mob, target);
        boolean inReach = distance <= reach - REACH_MARGIN;

        // Mid-windup tricks planned when the attack started.
        if (m.phase() == Phase.WINDUP) {
            if (brain.feintAt >= 0 && m.phaseTick() >= brain.feintAt) {
                brain.feintAt = -1;
                Combat.requestFeint(mob);
                brain.cooldown = 4 + random.nextInt(6); // feint, then come again quickly
            } else if (brain.morphAt >= 0 && m.phaseTick() >= brain.morphAt && brain.morphTo != null) {
                brain.morphAt = -1;
                Combat.requestAttack(mob, brain.morphTo);
            }
            return;
        }
        // Riposte the moment a parry lands, ignoring turns: that's the reward for parrying.
        if (m.isRiposteReady() && inReach) {
            startAttack(mob, brain, skill, profile.get(), target, false);
            return;
        }
        // Combo after a landed hit.
        if (m.phase() == Phase.RECOVERY && m.isComboAllowed() && brain.comboRolledFor != m.attackSerial()) {
            brain.comboRolledFor = m.attackSerial();
            if (inReach && random.nextDouble() < skill.comboChance()) {
                startAttack(mob, brain, skill, profile.get(), target, false);
                return;
            }
        }
        if (m.isBusy()) {
            return;
        }
        if (mob.isUsingItem() && hasShield(mob)) {
            if (mob.tickCount < brain.shieldDownAt) {
                return; // shield up for an incoming attack: hold it, don't swing
            }
            mob.stopUsingItem();
        }
        // Out of breath: hold back until stamina recovers (being parried or blocked costs bots stamina too).
        float stamina = data.stamina.current() / data.stamina.max();
        if (stamina < 0.3f) {
            brain.lowStamina = true;
        } else if (stamina > 0.6f) {
            brain.lowStamina = false;
        }
        if (brain.lowStamina || mob.tickCount < brain.evadeUntil) {
            brain.wantsSpace = true;
            TOKENS.release(target.getId(), mob.getId());
            return;
        }

        if (brain.cooldown > 0) {
            brain.cooldown--;
            brain.wantsSpace = true;
            TOKENS.release(target.getId(), mob.getId()); // give someone else a turn
            return;
        }
        BotStyle style = BotStyles.of(mob);
        if (!inReach) {
            // Lunge in from just outside reach (rushers, skirmishers), otherwise let vanilla pathing close in.
            if (style.lungeRange() > 0 && distance <= reach + style.lungeRange() && random.nextDouble() < 0.08
                    && TOKENS.acquire(target.getId(), mob.getId(), skill.attackers(), id -> stillEngaged(mob, id, target))) {
                startAttack(mob, brain, skill, profile.get(), target, true);
                double dx = target.getX() - mob.getX();
                double dz = target.getZ() - mob.getZ();
                double len = Math.max(0.01, Math.sqrt(dx * dx + dz * dz));
                mob.setDeltaMovement(mob.getDeltaMovement().add(dx / len * 0.45, 0.08, dz / len * 0.45));
                return;
            }
            brain.wantsSpace = false;
            return;
        }
        // Kick a turtle.
        if (skill.kickAfterTicks() > 0 && brain.memory.guardTicks() >= skill.kickAfterTicks() && distance <= KICK_RANGE) {
            Combat.requestAttack(mob, AttackType.KICK);
            brain.cooldown = 10 + random.nextInt(10);
            return;
        }
        if (!TOKENS.acquire(target.getId(), mob.getId(), skill.attackers(), id -> stillEngaged(mob, id, target))) {
            brain.wantsSpace = true; // wait for a turn, circling
            return;
        }
        brain.wantsSpace = false;
        startAttack(mob, brain, skill, profile.get(), target, true);
    }

    private static void startAttack(PathfinderMob mob, BrainState brain, BotSkill skill, WeaponProfile profile,
                                    LivingEntity target, boolean allowTricks) {
        RandomSource random = mob.getRandom();
        mob.getLookControl().setLookAt(target, 30, 30);
        if (mob.isUsingItem()) {
            mob.stopUsingItem(); // lower the shield to swing
        }
        List<AttackType> options = new ArrayList<>(profile.attacks().keySet());
        options.sort(null);
        AttackType type = options.get(random.nextInt(options.size()));
        Combat.requestAttack(mob, type);
        CombatData data = mob.getData(ModAttachments.COMBAT);
        if (data.machine.phase() != Phase.WINDUP) {
            return;
        }
        brain.feintAt = -1;
        brain.morphAt = -1;
        if (allowTricks && random.nextDouble() < skill.feintChance()) {
            int windup = data.machine.timings().windup();
            brain.feintAt = Math.max(2, (int) (windup * (0.5 + random.nextDouble() * 0.3)));
        } else if (allowTricks && options.size() > 1 && random.nextDouble() < skill.morphChance()) {
            List<AttackType> others = new ArrayList<>(options);
            others.remove(type);
            brain.morphTo = others.get(random.nextInt(others.size()));
            brain.morphAt = 2 + random.nextInt(3);
        } else if (random.nextDouble() < skill.heavyChance()) {
            Combat.requestHeavy(mob);
        }
        brain.cooldown = (int) Math.round((8 + random.nextInt(18)) * BotStyles.of(mob).cooldownMult());
    }

    /** A token holder keeps its turn while it's alive, still fighting this target, and close. */
    private static boolean stillEngaged(Mob self, int id, LivingEntity target) {
        Entity e = self.level().getEntity(id);
        return e instanceof Mob other && other.isAlive() && other.getTarget() == target && other.distanceTo(target) < 6;
    }

    static double reach(Mob mob, WeaponProfile profile) {
        WeaponProfile.AttackSpec spec = profile.attack(AttackType.SLASH)
                .orElseGet(() -> profile.attacks().values().iterator().next());
        return CombatMath.bladeLength(mob, spec);
    }

    /** Distance from the mob's pivot to the near edge of the target's hitbox, horizontally. */
    static double edgeDistance(Mob mob, LivingEntity target) {
        double dx = target.getX() - mob.getX();
        double dz = target.getZ() - mob.getZ();
        return Math.max(0, Math.sqrt(dx * dx + dz * dz) - target.getBbWidth() / 2);
    }
}
