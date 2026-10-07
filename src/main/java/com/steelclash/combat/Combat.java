package com.steelclash.combat;

import com.steelclash.Config;
import com.steelclash.core.ArcPath;
import com.steelclash.core.AttackTimings;
import com.steelclash.core.AttackType;
import com.steelclash.core.CombatStateMachine;
import com.steelclash.core.DamageType;
import com.steelclash.core.Phase;
import com.steelclash.entity.ThrownWeapon;
import com.steelclash.net.CombatStatePayload;
import com.steelclash.net.ModNetwork;
import com.steelclash.net.StaminaPayload;
import com.steelclash.profile.Jabs;
import com.steelclash.profile.Kicks;
import com.steelclash.profile.Throws;
import com.steelclash.profile.WeaponProfile;
import com.steelclash.profile.WeaponProfiles;
import java.util.Optional;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** Starting, ticking and cancelling attacks and parries. Shared by players and mobs. */
public final class Combat {
    /** Holding an attack input this many ticks into the windup turns it into a heavy. */
    public static final int HEAVY_HOLD_TICKS = 3;

    private Combat() {
    }

    // ---------------------------------------------------------------- actions (server + client prediction)

    /**
     * Starts an attack (or a kick) with the entity's weapon. Used by the server (authoritative) and by the local
     * client (prediction); does not sync.
     */
    public static boolean start(LivingEntity entity, CombatData data, AttackType type) {
        return start(entity, data, type, 0, false);
    }

    /**
     * @param variant  which of the attack's arcs to swing (wrapped to what the profile defines)
     * @param mirrored swing from the other side (left to right)
     */
    public static boolean start(LivingEntity entity, CombatData data, AttackType type, int variant, boolean mirrored) {
        if (isHolstered(entity, data) && type != AttackType.KICK) {
            return false;
        }
        Optional<WeaponProfiles.Resolved> resolved = WeaponProfiles.resolveFor(entity);
        AttackTimings timings;
        if (type == AttackType.KICK) {
            timings = Kicks.forEntity(entity).timings();
        } else if (type == AttackType.JAB) {
            if (data.jabReadyAt > entity.level().getGameTime()) {
                return false; // just disarmed
            }
            timings = Jabs.spec().timings();
            if (Dodge.justDodged(entity, data)) {
                timings = timings.withWindupUs(timings.windupUs()
                        + (long) Dodge.JAB_AFTER_DODGE_EXTRA_WINDUP * AttackTimings.TICK_US);
            }
        } else if (type == AttackType.THROW) {
            if (resolved.isEmpty() || entity.getMainHandItem().isEmpty()) {
                return false; // throw what you're fighting with
            }
            timings = Throws.spec().timings();
        } else {
            if (type == AttackType.SPECIAL && (data.specialReadyAt > entity.level().getGameTime()
                    || resolved.flatMap(r -> r.profile().special()).isEmpty())) {
                return false; // no special, or still on cooldown
            }
            Optional<WeaponProfile.AttackSpec> spec = resolved.flatMap(r -> r.profile().spec(type));
            if (spec.isEmpty()) {
                return false;
            }
            WeaponProfile profile = resolved.get().profile();
            timings = CombatMath.timings(entity, profile, spec.get());
            variant = Math.floorMod(variant, spec.get().variantCount());
            if (data.machine.isRiposteReady()) {
                timings = timings.withWindupUs(Math.round(timings.windupUs() * (double) profile.riposteWindupMult()));
            }
        }
        if (type == AttackType.KICK || type == AttackType.THROW || type == AttackType.JAB) {
            variant = 0;
            mirrored = false;
        }
        boolean riposte = data.machine.isRiposteReady() && isWeaponAttack(type);
        if (!data.machine.startAttack(type, timings, variant, mirrored)) {
            return false;
        }
        if (riposte) {
            data.machine.startActiveParry(Config.RIPOSTE_ACTIVE_PARRY_TICKS.get());
        }
        if (type == AttackType.SPECIAL) {
            data.specialReadyAt = entity.level().getGameTime()
                    + resolved.flatMap(r -> r.profile().special()).map(WeaponProfile.SpecialSpec::cooldown).orElse(0);
        }
        data.profileKey = resolved.map(WeaponProfiles.Resolved::key).orElse(null);
        data.weapon = entity.getMainHandItem().copy();
        data.queuedAttack = null;
        data.lunge = type != AttackType.KICK && type != AttackType.JAB && entity.isSprinting();
        data.jumpAttack = type == AttackType.OVERHEAD && !entity.onGround();
        data.resetSwing();
        data.prevYaw = CombatMath.viewYaw(entity);
        data.prevPitch = entity.getXRot();
        data.prevPivot = CombatMath.pivot(entity, 1f);
        return true;
    }

    /** Holding the attack turns the windup into a heavy. */
    public static boolean makeHeavy(LivingEntity entity, CombatData data) {
        AttackType current = data.machine.type();
        if (data.machine.phase() != Phase.WINDUP || current == AttackType.KICK || current == AttackType.THROW
                || current == AttackType.SPECIAL || current == AttackType.JAB) {
            return false;
        }
        Optional<WeaponProfile> profile = currentProfile(entity, data);
        if (profile.isEmpty()) {
            return false;
        }
        long heavyWindupUs = Math.round(data.machine.timings().windupUs() * (double) profile.get().heavy().windupMult());
        return data.machine.makeHeavy((int) Math.min(AttackTimings.MAX_US, heavyWindupUs));
    }

    /**
     * Feint into a kick or jab (Chivalry 2): during a weapon attack's windup, the kick or jab key drops the attack
     * (paying the feint) and starts the kick or jab instead.
     */
    public static boolean feintInto(LivingEntity entity, CombatData data, AttackType type) {
        if ((type != AttackType.KICK && type != AttackType.JAB) || data.machine.phase() != Phase.WINDUP
                || !isWeaponAttack(data.machine.type())) {
            return false;
        }
        data.machine.feint();
        spend(entity, data, Config.FEINT_STAMINA_COST.get());
        return start(entity, data, type, 0, false);
    }

    public static boolean feint(LivingEntity entity, CombatData data) {
        // Kicks and jabs can't be cancelled (Chivalry 2 2.2).
        if (data.machine.type() == AttackType.KICK || data.machine.type() == AttackType.JAB || !data.machine.feint()) {
            return false;
        }
        spend(entity, data, Config.FEINT_STAMINA_COST.get());
        return true;
    }

    public static boolean morph(LivingEntity entity, CombatData data, AttackType newType) {
        return morph(entity, data, newType, 0, data.machine.isMirrored());
    }

    public static boolean morph(LivingEntity entity, CombatData data, AttackType newType, int variant, boolean mirrored) {
        if (data.machine.phase() != Phase.WINDUP || !isWeaponAttack(newType) || !isWeaponAttack(data.machine.type())) {
            return false;
        }
        Optional<WeaponProfile> profile = currentProfile(entity, data);
        Optional<WeaponProfile.AttackSpec> spec = profile.flatMap(p -> p.attack(newType));
        if (spec.isEmpty() || !data.machine.morph(newType, CombatMath.timings(entity, profile.get(), spec.get()),
                Math.floorMod(variant, spec.get().variantCount()), mirrored)) {
            return false;
        }
        spend(entity, data, Config.MORPH_STAMINA_COST.get());
        return true;
    }

    /** Raises a weapon parry; during a windup this cancels the attack into the parry. */
    public static boolean startParry(LivingEntity entity, CombatData data) {
        if (isHolstered(entity, data)) {
            return false;
        }
        Optional<WeaponProfiles.Resolved> resolved = WeaponProfiles.resolveFor(entity);
        Optional<WeaponProfile.GuardSpec> guard = resolved.flatMap(r -> r.profile().guard());
        if (guard.isEmpty()) {
            return false;
        }
        if (data.machine.phase() == Phase.WINDUP && data.machine.type() == AttackType.JAB) {
            return false; // a jab is committed
        }
        if (Dodge.tooSoonToParry(entity, data)) {
            return false; // mid-dash
        }
        boolean parryCancel = data.machine.phase() == Phase.WINDUP && data.machine.type() != AttackType.KICK;
        if (parryCancel) {
            data.machine.feint();
        }
        if (!data.machine.startParry(parryTicks(entity, guard.get()), guard.get().recovery(), Config.PARRY_COOLDOWN_TICKS.get())) {
            return false;
        }
        if (parryCancel) {
            spend(entity, data, Config.PARRY_CANCEL_STAMINA_COST.get());
        }
        data.profileKey = resolved.get().key();
        data.weapon = entity.getMainHandItem().copy();
        data.queuedAttack = null;
        return true;
    }

    /** Held block (players, HELD mode): the guard stays up until the key is let go. Timed otherwise. */
    static int parryTicks(LivingEntity entity, WeaponProfile.GuardSpec guard) {
        return heldBlock(entity) ? HELD_GUARD_TICKS : guard.parryTicks();
    }

    /** Whether this fighter's weapon guard is held (drains stamina) rather than timed. */
    public static boolean heldBlock(LivingEntity entity) {
        return entity instanceof Player && Config.BLOCK_MODE.get() == Config.BlockMode.HELD;
    }

    /** A held guard's length: effectively until the key is released (an hour, so a stuck key can't last forever). */
    private static final int HELD_GUARD_TICKS = 20 * 60 * 60;

    // ---------------------------------------------------------------- server entry points

    /**
     * Server entry point for an attack input. During a windup a different attack is a morph. During recovery the
     * input is buffered (or starts immediately as a combo after a landed hit); this also absorbs the latency gap
     * between the client's prediction and the server.
     */
    public static void requestAttack(LivingEntity entity, AttackType type) {
        CombatData data = entity.getData(ModAttachments.COMBAT);
        // Mobs (and callers without their own choice): random arc variant; combos alternate sides like Chivalry 2.
        int variant = entity.getRandom().nextInt(8);
        boolean mirrored = data.machine.phase() == Phase.RECOVERY && data.machine.isComboAllowed()
                ? !data.machine.isMirrored()
                : entity.getRandom().nextBoolean();
        requestAttack(entity, type, variant, mirrored);
    }

    /** Players choose the arc variant and side on the client, so the server traces exactly what they predicted. */
    public static void requestAttack(LivingEntity entity, AttackType type, int variant, boolean mirrored) {
        CombatData data = entity.getData(ModAttachments.COMBAT);
        CombatStateMachine machine = data.machine;
        if (machine.phase() == Phase.WINDUP) {
            if (feintInto(entity, data, type)) {
                onAttackStarted(entity, data);
                sync(entity, data, false);
            } else if (type != machine.type() && morph(entity, data, type, variant, mirrored)) {
                sync(entity, data, false);
            }
            return;
        }
        if (!machine.canStartAttack() && (machine.phase() == Phase.RECOVERY || machine.phase() == Phase.GUARD_RECOVERY
                || machine.phase() == Phase.STAGGER)) {
            // Also buffered while staggered: it starts the moment the stagger ends (Chivalry 2 queued ripostes/counters).
            queue(data, type, variant, mirrored);
            return;
        }
        if (start(entity, data, type, variant, mirrored)) {
            onAttackStarted(entity, data);
            sync(entity, data, false);
        } else {
            // Tell the client its prediction was wrong so it drops the attack.
            sync(entity, data, true);
        }
    }

    public static void requestHeavy(LivingEntity entity) {
        CombatData data = entity.getData(ModAttachments.COMBAT);
        if (makeHeavy(entity, data)) {
            sync(entity, data, false);
        }
    }

    public static void requestFeint(LivingEntity entity) {
        CombatData data = entity.getData(ModAttachments.COMBAT);
        if (feint(entity, data)) {
            Feedback.feint(entity);
            sync(entity, data, false);
        }
    }

    public static void requestParry(LivingEntity entity) {
        CombatData data = entity.getData(ModAttachments.COMBAT);
        if (startParry(entity, data)) {
            sync(entity, data, false);
        } else {
            sync(entity, data, true);
        }
    }

    public static void releaseParry(LivingEntity entity) {
        CombatData data = entity.getData(ModAttachments.COMBAT);
        if (data.machine.phase() == Phase.PARRY) {
            data.machine.releaseParry();
            sync(entity, data, false);
        }
    }

    // ---------------------------------------------------------------- ticking

    public static void tickServer(LivingEntity entity, CombatData data) {
        CombatMovement.update(entity, data);
        if (data.machine.phase() == Phase.PARRY && heldBlock(entity)) {
            // Holding a guard drains stamina slowly and keeps it from regenerating (Chivalry 2).
            data.stamina.spend(Config.HELD_BLOCK_DRAIN_PER_SECOND.get().floatValue() / 20f);
        }
        boolean crouchPause = Config.CROUCH_PAUSES_STAMINA_REGEN.get() && entity.isCrouching();
        data.stamina.tick(crouchPause ? 0f : Config.STAMINA_REGEN_PER_SECOND.get().floatValue() / 20f,
                Config.STAMINA_REGEN_DELAY_TICKS.get());
        syncStaminaIfChanged(entity, data);

        CombatStateMachine machine = data.machine;
        if (machine.isAttacking() || machine.phase() == Phase.PARRY) {
            if (!entity.isAlive() || !ItemStack.isSameItem(data.weapon, entity.getMainHandItem())) {
                cancel(entity, data);
                return;
            }
        }

        Phase before = machine.phase();
        CombatStateMachine.Sweep sweep = machine.tick();
        if (sweep != null) {
            Optional<WeaponProfile.AttackSpec> spec = currentSpec(entity, data);
            if (spec.isEmpty()) {
                cancel(entity, data);
                return;
            }
            if (sweep.from() == 0) {
                onReleaseStarted(entity, data);
            }
            if (machine.type() == AttackType.THROW) {
                if (sweep.from() == 0) {
                    throwWeapon(entity, data);
                }
                finishTick(entity, data, before);
                return;
            }
            SwingTracer.Result result = SwingTracer.trace(entity, data, spec.get(), sweep);
            CombatProfiler.count(CombatProfiler.Counter.RELEASES, 1);
            CombatProfiler.count(CombatProfiler.Counter.CONTACTS, result.hits().size());
            CombatProfiler.begin(CombatProfiler.Section.RESOLVE);
            boolean landed = false;
            try {
                for (LivingEntity target : result.hits()) {
                    Hit hit = prepareHit(entity, data, target, spec.get());
                    if (LagCompensation.hold(entity, target, hit)) {
                        continue; // a lagged defender gets time for their parry to arrive; see deliverHeld
                    }
                    float healthBefore = target.getHealth();
                    deliver(entity, target, hit);
                    if (machine.phase() != Phase.RELEASE && machine.phase() != Phase.RECOVERY) {
                        break; // parried, countered or blocked: the swing stops here
                    }
                    landed = true;
                    if (machine.type() != AttackType.KICK && target.getHealth() < healthBefore) {
                        Feedback.hit(entity, target, machine.isHeavy());
                    }
                }
            } finally {
                CombatProfiler.end(CombatProfiler.Section.RESOLVE);
            }
            if (landed && !machine.isComboAllowed()) {
                machine.allowCombo();
                sync(entity, data, false);
            }
            if (result.clank() != null && machine.isAttacking() && Config.ENVIRONMENT_CLANK.get()) {
                clank(entity, data, result.clank());
            }
            if (before == Phase.RELEASE && machine.phase() == Phase.RECOVERY && data.hitThisSwing.isEmpty()) {
                spend(entity, data, spec.get().staminaCost()); // whiffing costs stamina
            }
            if (before == Phase.RELEASE && machine.phase() == Phase.RECOVERY && machine.type() == AttackType.SPECIAL
                    && specialKind(entity, data) == WeaponProfile.SpecialSpec.Kind.SLAM) {
                Specials.slam(entity, spec.get());
            }
        }
        finishTick(entity, data, before);
    }

    /** End of a server tick: remember the view for next tick's sweep, start buffered attacks, sync phase changes. */
    private static void finishTick(LivingEntity entity, CombatData data, Phase before) {
        CombatStateMachine machine = data.machine;
        // The same turn-capped view this tick's sweep used (the phase may already have moved on to recovery).
        boolean capped = isTurnCapped(before) || isTurnCapped(machine.phase());
        float[] view = CombatMath.swingView(entity, data, capped);
        data.prevYaw = view[0];
        data.prevPitch = view[1];
        data.prevPivot = CombatMath.pivot(entity, 1f);

        if (data.queuedAttack != null && machine.canStartAttack()) {
            AttackType queued = data.queuedAttack;
            data.queuedAttack = null;
            if (start(entity, data, queued, data.queuedVariant, data.queuedMirrored)) {
                onAttackStarted(entity, data);
                sync(entity, data, false);
                return;
            }
        }
        if (machine.phase() != before) {
            sync(entity, data, false);
        }
    }

    private static boolean isTurnCapped(Phase phase) {
        return phase == Phase.WINDUP || phase == Phase.RELEASE;
    }

    /** Client-side tick: advances the local copy of the machine for visuals and prediction. */
    public static void tickClient(LivingEntity entity, CombatData data) {
        data.machine.tick();
        if (entity instanceof Player player && player.isLocalPlayer()) {
            CombatMovement.update(entity, data); // predicted: the client moves the local player
        }
        if (data.queuedAttack != null && data.machine.canStartAttack()) {
            AttackType queued = data.queuedAttack;
            data.queuedAttack = null;
            start(entity, data, queued, data.queuedVariant, data.queuedMirrored);
        }
    }

    public static void cancel(LivingEntity entity, CombatData data) {
        data.machine.cancel();
        data.queuedAttack = null;
        sync(entity, data, true);
    }

    public static void stagger(LivingEntity entity, CombatData data, int ticks, boolean allowParry) {
        data.machine.stagger(ticks, allowParry);
        data.queuedAttack = null;
        sync(entity, data, true);
    }

    // ---------------------------------------------------------------- lookups

    public static Optional<WeaponProfile.AttackSpec> currentSpec(LivingEntity entity, CombatData data) {
        if (data.machine.type() == AttackType.KICK) {
            return Optional.of(Kicks.forEntity(entity));
        }
        if (data.machine.type() == AttackType.THROW) {
            return Optional.of(Throws.spec());
        }
        if (data.machine.type() == AttackType.JAB) {
            return Optional.of(Jabs.spec());
        }
        return currentProfile(entity, data).flatMap(profile -> profile.spec(data.machine.type()));
    }

    /** Slash, overhead or stab (the attacks that can be morphed between). */
    public static boolean isWeaponAttack(AttackType type) {
        return type == AttackType.SLASH || type == AttackType.OVERHEAD || type == AttackType.STAB;
    }

    public static WeaponProfile.SpecialSpec.Kind specialKind(LivingEntity entity, CombatData data) {
        return currentProfile(entity, data).flatMap(WeaponProfile::special).map(WeaponProfile.SpecialSpec::kind).orElse(null);
    }

    /** Buffers an attack input to start the moment the fighter is free. */
    public static void queue(CombatData data, AttackType type, int variant, boolean mirrored) {
        data.queuedAttack = type;
        data.queuedVariant = variant;
        data.queuedMirrored = mirrored;
    }

    /** The arc the current attack follows: its variant, mirrored if swung from the other side. */
    public static ArcPath currentPath(CombatData data, WeaponProfile.AttackSpec spec) {
        ArcPath path = spec.arc(data.machine.variant()).toPath();
        return data.machine.isMirrored() ? path.mirrored() : path;
    }

    public static Optional<WeaponProfile> currentProfile(LivingEntity entity, CombatData data) {
        if (data.profileKey == null) {
            return Optional.empty();
        }
        return WeaponProfiles.get(data.profileKey, entity.level().registryAccess());
    }

    /** Heavy attacks of hyper-armored weapons can't be flinched. */
    public static boolean hasHyperArmor(LivingEntity entity, CombatData data) {
        return data.machine.isHeavy() && currentProfile(entity, data).map(WeaponProfile::hyperArmorOnHeavy).orElse(false);
    }

    private static boolean isHolstered(LivingEntity entity, CombatData data) {
        if (entity instanceof Player player && !entity.getMainHandItem().isEmpty()
                && player.getCooldowns().isOnCooldown(entity.getMainHandItem().getItem())) {
            return true;
        }
        return data.holsteredUntil > entity.level().getGameTime();
    }

    /** Stamina is server-authoritative: client prediction never spends it. */
    private static void spend(LivingEntity entity, CombatData data, double amount) {
        if (!entity.level().isClientSide()) {
            data.stamina.spend((float) amount);
        }
    }

    // ---------------------------------------------------------------- hits

    /**
     * A hit's numbers, fixed when the blade connects: a hit held for a lagged defender lands with them later.
     *
     * @param damageMult    final damage multiplier (attack x heavy x lunge x jump x damage type x mount)
     * @param staminaDamage stamina a parrying or blocking defender loses
     */
    public record Hit(AttackType type, WeaponProfile.AttackSpec spec, float damageMult, float staminaDamage, boolean heavy) {
    }

    private static Hit prepareHit(LivingEntity attacker, CombatData data, LivingEntity target, WeaponProfile.AttackSpec spec) {
        AttackType type = data.machine.type();
        if (type == AttackType.KICK) {
            return new Hit(type, spec, 1f, spec.staminaDamage(), false);
        }
        float damageMult = spec.damage();
        float staminaDamage = spec.staminaDamage();
        Optional<WeaponProfile> profile = currentProfile(attacker, data);
        if (data.machine.isHeavy() && profile.isPresent()) {
            damageMult *= profile.get().heavy().damageMult();
            staminaDamage *= profile.get().heavy().staminaDamageMult();
        }
        if (data.lunge) {
            damageMult *= Config.LUNGE_DAMAGE_MULT.get().floatValue();
        }
        if (data.jumpAttack) {
            damageMult *= Config.JUMP_ATTACK_DAMAGE_MULT.get().floatValue();
        }
        if (Config.DAMAGE_TYPES.get() && profile.isPresent()) {
            damageMult *= (float) profile.get().damageTypeOf(spec).multiplierFor(target.getArmorValue());
            staminaDamage *= (float) profile.get().damageTypeOf(spec).staminaDamageMultiplier();
        }
        // Couched lance: stabs and lunges from a moving mount hit harder the faster it goes.
        if (attacker.getVehicle() != null && (type == AttackType.STAB || type == AttackType.SPECIAL)) {
            damageMult *= (float) DamageType.mountedChargeMultiplier(attacker.getVehicle().getDeltaMovement().horizontalDistance());
        }
        return new Hit(type, spec, damageMult, staminaDamage, data.machine.isHeavy());
    }

    /**
     * Applies a hit through vanilla attack code, so enchantments, Spartan Weaponry traits and other mods'
     * attack listeners all still apply. {@link Defense} decides parries, counters and blocks, and the swing's damage
     * multiplier is applied while the {@link SwingContext} is active.
     */
    private static void deliver(LivingEntity attacker, LivingEntity target, Hit hit) {
        if (hit.type() == AttackType.KICK) {
            applyKick(attacker, target, hit.spec());
            return;
        }
        SwingContext.run(attacker, hit.type(), hit.spec(), hit.damageMult(), hit.staminaDamage(), () -> {
            // Our swings hit each target at most once, so vanilla i-frames would only eat legitimate hits.
            target.invulnerableTime = 0;
            if (attacker instanceof Player player) {
                // Our windup replaces vanilla's attack cooldown: always attack at full strength.
                player.attackStrengthTicker = 10_000;
                player.attack(target);
            } else if (attacker instanceof Mob mob) {
                mob.doHurtTarget(target);
            }
        });
    }

    /** Lands a hit {@link LagCompensation} held for a lagged defender; it may now be parried, blocked or countered. */
    static void deliverHeld(LivingEntity attacker, LivingEntity target, Hit hit) {
        float healthBefore = target.getHealth();
        deliver(attacker, target, hit);
        if (target.getHealth() < healthBefore) {
            Feedback.hit(attacker, target, hit.heavy());
            CombatData data = attacker.getData(ModAttachments.COMBAT);
            if (data.machine.phase() == Phase.RECOVERY && !data.machine.isComboAllowed()) {
                data.machine.allowCombo();
                sync(attacker, data, false);
            }
        }
    }

    /**
     * Kick / shield bash: breaks a raised parry or shield guard (draining stamina); an unguarded target is staggered
     * and takes a little damage. Never parried or blocked itself.
     */
    private static void applyKick(LivingEntity attacker, LivingEntity target, WeaponProfile.AttackSpec spec) {
        CombatData targetData = target.getData(ModAttachments.COMBAT);
        CombatStateMachine targetMachine = targetData.machine;
        boolean striking = targetMachine.phase() == Phase.WINDUP || targetMachine.phase() == Phase.RELEASE;
        if (striking && targetMachine.type() == AttackType.KICK) {
            Feedback.kick(target, false); // kicks block kicks: the two cancel out
            return;
        }
        boolean guarding = target.isBlocking() || targetData.machine.phase() == Phase.PARRY;
        boolean bash = Kicks.canBash(attacker);
        if (guarding) {
            if (target.isBlocking()) {
                target.stopUsingItem();
            }
            targetData.stamina.spend(spec.staminaDamage());
            stagger(target, targetData, Config.KICK_GUARD_BREAK_TICKS.get(), false);
            Feedback.kick(target, true);
        } else {
            Feedback.kick(target, false);
            if (!striking) {
                // A kick doesn't interrupt someone already attacking (Chivalry 2): they swing straight through it.
                stagger(target, targetData, Config.KICK_STAGGER_TICKS.get(), true);
            }
            DamageSource source = attacker instanceof Player player
                    ? attacker.damageSources().playerAttack(player)
                    : attacker.damageSources().mobAttack(attacker);
            SwingContext.run(attacker, AttackType.KICK, spec, 1f, spec.staminaDamage(), () -> {
                target.invulnerableTime = 0;
                target.hurt(source, spec.damage());
            });
        }
        target.knockback(bash ? 0.7 : 0.4, attacker.getX() - target.getX(), attacker.getZ() - target.getZ());
        target.hurtMarked = true;
    }

    /** The weapon leaves the hand: a {@link ThrownWeapon} flies along the view. Creative players keep theirs. */
    private static void throwWeapon(LivingEntity entity, CombatData data) {
        ItemStack weapon = entity.getMainHandItem();
        if (weapon.isEmpty()) {
            return;
        }
        ThrownWeapon projectile = new ThrownWeapon(entity.level(), entity, weapon);
        projectile.shootFromRotation(entity, entity.getXRot(), CombatMath.viewYaw(entity), 0f, ThrownWeapon.SPEED, 1.0f);
        entity.level().addFreshEntity(projectile);
        if (!(entity instanceof Player player && player.getAbilities().instabuild)) {
            weapon.shrink(1);
        }
        data.weapon = entity.getMainHandItem().copy(); // the hand changed on purpose: don't cancel the recovery
    }

    /** The blade hit a wall: the swing stops and the attacker reels. */
    private static void clank(LivingEntity entity, CombatData data, Vec3 where) {
        stagger(entity, data, Config.CLANK_STAGGER_TICKS.get(), true);
        Feedback.clank(entity, where);
    }

    // ---------------------------------------------------------------- feedback & sync

    /** Telegraph: an audible cue at the start of every windup, so attacks from off-screen can still be read. */
    private static void onAttackStarted(LivingEntity entity, CombatData data) {
        Feedback.windup(entity);
        if (data.machine.type() == AttackType.JAB) {
            spend(entity, data, Config.JAB_STAMINA_COST.get());
        }
        if (data.machine.type() == AttackType.KICK) {
            spend(entity, data, Config.KICK_STAMINA_COST.get());
            if (entity.isUsingItem()) {
                entity.stopUsingItem();
            }
        }
    }

    private static void onReleaseStarted(LivingEntity entity, CombatData data) {
        Feedback.swing(entity, data.machine.isHeavy());
        if (data.machine.type() == AttackType.SPECIAL && specialKind(entity, data) == WeaponProfile.SpecialSpec.Kind.LUNGE) {
            Vec3 look = entity.getLookAngle();
            entity.setDeltaMovement(entity.getDeltaMovement().add(look.x * 0.9, 0.1, look.z * 0.9));
            entity.hurtMarked = true;
        }
        if (data.lunge) {
            Vec3 look = entity.getLookAngle();
            entity.setDeltaMovement(entity.getDeltaMovement().add(look.x * 0.6, 0.05, look.z * 0.6));
            entity.hurtMarked = true; // sends the velocity to the client for players
        }
        if (!(entity instanceof Player)) {
            entity.swing(InteractionHand.MAIN_HAND, true); // mobs have no attack animation of their own yet (M4)
        }
    }

    /**
     * @param authoritative true when the server overrides the client (cancel, rejected prediction, parry outcomes).
     *                      For the local player, non-authoritative updates only merge server-decided windows (combo,
     *                      riposte); its own prediction runs the same machine.
     */
    public static void sync(LivingEntity entity, CombatData data, boolean authoritative) {
        if (entity.level().isClientSide()) {
            return;
        }
        CombatProfiler.begin(CombatProfiler.Section.SYNC);
        try {
            ModNetwork.sendToTrackingAndSelf(entity, CombatStatePayload.of(entity, data, authoritative));
        } finally {
            CombatProfiler.end(CombatProfiler.Section.SYNC);
        }
    }

    private static void syncStaminaIfChanged(LivingEntity entity, CombatData data) {
        if (!(entity instanceof ServerPlayer player)) {
            return;
        }
        float current = data.stamina.current();
        if (Math.abs(current - data.lastSentStamina) >= 1f || (current == data.stamina.max() && data.lastSentStamina != current)) {
            data.lastSentStamina = current;
            ModNetwork.sendTo(player, new StaminaPayload(current, data.stamina.max()));
        }
    }
}
