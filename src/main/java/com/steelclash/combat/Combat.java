package com.steelclash.combat;

import com.steelclash.Config;
import com.steelclash.core.AttackTimings;
import com.steelclash.core.AttackType;
import com.steelclash.core.CombatStateMachine;
import com.steelclash.core.Phase;
import com.steelclash.net.CombatStatePayload;
import com.steelclash.net.StaminaPayload;
import com.steelclash.profile.Kicks;
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
import net.neoforged.neoforge.network.PacketDistributor;

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
        if (isHolstered(entity, data) && type != AttackType.KICK) {
            return false;
        }
        Optional<WeaponProfiles.Resolved> resolved = WeaponProfiles.resolveFor(entity);
        AttackTimings timings;
        if (type == AttackType.KICK) {
            timings = Kicks.forEntity(entity).timings();
        } else {
            Optional<WeaponProfile.AttackSpec> spec = resolved.flatMap(r -> r.profile().attack(type));
            if (spec.isEmpty()) {
                return false;
            }
            WeaponProfile profile = resolved.get().profile();
            timings = CombatMath.timings(entity, profile, spec.get());
            if (data.machine.isRiposteReady()) {
                timings = new AttackTimings(Math.round(timings.windup() * profile.riposteWindupMult()),
                        timings.release(), timings.recovery());
            }
        }
        if (!data.machine.startAttack(type, timings)) {
            return false;
        }
        data.profileKey = resolved.map(WeaponProfiles.Resolved::key).orElse(null);
        data.weapon = entity.getMainHandItem().copy();
        data.queuedAttack = null;
        data.lunge = type != AttackType.KICK && entity.isSprinting();
        data.jumpAttack = type == AttackType.OVERHEAD && !entity.onGround();
        data.resetSwing();
        data.prevYaw = CombatMath.viewYaw(entity);
        data.prevPitch = entity.getXRot();
        data.prevPivot = CombatMath.pivot(entity, 1f);
        return true;
    }

    /** Holding the attack turns the windup into a heavy. */
    public static boolean makeHeavy(LivingEntity entity, CombatData data) {
        if (data.machine.phase() != Phase.WINDUP || data.machine.type() == AttackType.KICK) {
            return false;
        }
        Optional<WeaponProfile> profile = currentProfile(entity, data);
        if (profile.isEmpty()) {
            return false;
        }
        int heavyWindup = Math.round(data.machine.timings().windup() * profile.get().heavy().windupMult());
        return data.machine.makeHeavy(heavyWindup);
    }

    public static boolean feint(LivingEntity entity, CombatData data) {
        if (data.machine.type() == AttackType.KICK || !data.machine.feint()) {
            return false;
        }
        spend(entity, data, Config.FEINT_STAMINA_COST.get());
        return true;
    }

    public static boolean morph(LivingEntity entity, CombatData data, AttackType newType) {
        if (data.machine.phase() != Phase.WINDUP || newType == AttackType.KICK || data.machine.type() == AttackType.KICK) {
            return false;
        }
        Optional<WeaponProfile> profile = currentProfile(entity, data);
        Optional<WeaponProfile.AttackSpec> spec = profile.flatMap(p -> p.attack(newType));
        if (spec.isEmpty() || !data.machine.morph(newType, CombatMath.timings(entity, profile.get(), spec.get()))) {
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
        boolean parryCancel = data.machine.phase() == Phase.WINDUP && data.machine.type() != AttackType.KICK;
        if (parryCancel) {
            data.machine.feint();
        }
        if (!data.machine.startParry(guard.get().parryTicks(), guard.get().recovery())) {
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

    // ---------------------------------------------------------------- server entry points

    /**
     * Server entry point for an attack input. During a windup a different attack is a morph. During recovery the
     * input is buffered (or starts immediately as a combo after a landed hit); this also absorbs the latency gap
     * between the client's prediction and the server.
     */
    public static void requestAttack(LivingEntity entity, AttackType type) {
        CombatData data = entity.getData(ModAttachments.COMBAT);
        CombatStateMachine machine = data.machine;
        if (machine.phase() == Phase.WINDUP) {
            if (type != machine.type() && morph(entity, data, type)) {
                sync(entity, data, false);
            }
            return;
        }
        if (!machine.canStartAttack() && (machine.phase() == Phase.RECOVERY || machine.phase() == Phase.GUARD_RECOVERY)) {
            data.queuedAttack = type;
            return;
        }
        if (start(entity, data, type)) {
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
        data.stamina.tick(Config.STAMINA_REGEN_PER_SECOND.get().floatValue() / 20f, Config.STAMINA_REGEN_DELAY_TICKS.get());
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
            SwingTracer.Result result = SwingTracer.trace(entity, data, spec.get(), sweep);
            boolean landed = false;
            for (LivingEntity target : result.hits()) {
                float healthBefore = target.getHealth();
                applyHit(entity, data, target, spec.get());
                if (machine.phase() != Phase.RELEASE && machine.phase() != Phase.RECOVERY) {
                    break; // parried, countered or blocked: the swing stops here
                }
                landed = true;
                if (machine.type() != AttackType.KICK && target.getHealth() < healthBefore) {
                    Feedback.hit(entity, target, machine.isHeavy());
                }
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
        }
        data.prevYaw = CombatMath.viewYaw(entity);
        data.prevPitch = entity.getXRot();
        data.prevPivot = CombatMath.pivot(entity, 1f);

        if (data.queuedAttack != null && machine.canStartAttack()) {
            AttackType queued = data.queuedAttack;
            data.queuedAttack = null;
            if (start(entity, data, queued)) {
                onAttackStarted(entity, data);
                sync(entity, data, false);
                return;
            }
        }
        if (machine.phase() != before) {
            sync(entity, data, false);
        }
    }

    /** Client-side tick: advances the local copy of the machine for visuals and prediction. */
    public static void tickClient(LivingEntity entity, CombatData data) {
        data.machine.tick();
        if (data.queuedAttack != null && data.machine.canStartAttack()) {
            AttackType queued = data.queuedAttack;
            data.queuedAttack = null;
            start(entity, data, queued);
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
        return currentProfile(entity, data).flatMap(profile -> profile.attack(data.machine.type()));
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
     * Applies a hit through vanilla attack code, so enchantments, Spartan Weaponry traits and other mods'
     * attack listeners all still apply. {@link Defense} decides parries, counters and blocks, and the swing's damage
     * multiplier is applied while the {@link SwingContext} is active.
     */
    private static void applyHit(LivingEntity attacker, CombatData data, LivingEntity target, WeaponProfile.AttackSpec spec) {
        AttackType type = data.machine.type();
        if (type == AttackType.KICK) {
            applyKick(attacker, target, spec);
            return;
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
        SwingContext.run(attacker, type, spec, damageMult, staminaDamage, () -> {
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

    /**
     * Kick / shield bash: breaks a raised parry or shield guard (draining stamina); an unguarded target is staggered
     * and takes a little damage. Never parried or blocked itself.
     */
    private static void applyKick(LivingEntity attacker, LivingEntity target, WeaponProfile.AttackSpec spec) {
        CombatData targetData = target.getData(ModAttachments.COMBAT);
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
            stagger(target, targetData, Config.KICK_STAGGER_TICKS.get(), true);
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

    /** The blade hit a wall: the swing stops and the attacker reels. */
    private static void clank(LivingEntity entity, CombatData data, Vec3 where) {
        stagger(entity, data, Config.CLANK_STAGGER_TICKS.get(), true);
        Feedback.clank(entity, where);
    }

    // ---------------------------------------------------------------- feedback & sync

    /** Telegraph: an audible cue at the start of every windup, so attacks from off-screen can still be read. */
    private static void onAttackStarted(LivingEntity entity, CombatData data) {
        Feedback.windup(entity);
        if (data.machine.type() == AttackType.KICK) {
            spend(entity, data, Config.KICK_STAMINA_COST.get());
            if (entity.isUsingItem()) {
                entity.stopUsingItem();
            }
        }
    }

    private static void onReleaseStarted(LivingEntity entity, CombatData data) {
        Feedback.swing(entity, data.machine.isHeavy());
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
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity, CombatStatePayload.of(entity, data, authoritative));
    }

    private static void syncStaminaIfChanged(LivingEntity entity, CombatData data) {
        if (!(entity instanceof ServerPlayer player)) {
            return;
        }
        float current = data.stamina.current();
        if (Math.abs(current - data.lastSentStamina) >= 1f || (current == data.stamina.max() && data.lastSentStamina != current)) {
            data.lastSentStamina = current;
            PacketDistributor.sendToPlayer(player, new StaminaPayload(current, data.stamina.max()));
        }
    }
}
