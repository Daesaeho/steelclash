package com.steelclash.combat;

import com.steelclash.Config;
import com.steelclash.core.AttackTimings;
import com.steelclash.core.AttackType;
import com.steelclash.core.CombatStateMachine;
import com.steelclash.core.Phase;
import com.steelclash.net.CombatStatePayload;
import com.steelclash.net.StaminaPayload;
import com.steelclash.profile.WeaponProfile;
import com.steelclash.profile.WeaponProfiles;
import java.util.List;
import java.util.Optional;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/** Starting, ticking and cancelling attacks and parries. Shared by players and mobs. */
public final class Combat {
    private Combat() {
    }

    /**
     * Starts an attack with the entity's weapon. Used by the server (authoritative) and by the local client
     * (prediction); does not sync.
     */
    public static boolean start(LivingEntity entity, CombatData data, AttackType type) {
        if (isHolstered(entity, data)) {
            return false;
        }
        Optional<WeaponProfiles.Resolved> resolved = WeaponProfiles.resolveFor(entity);
        if (resolved.isEmpty()) {
            return false;
        }
        WeaponProfile profile = resolved.get().profile();
        Optional<WeaponProfile.AttackSpec> spec = profile.attack(type);
        if (spec.isEmpty()) {
            return false;
        }
        AttackTimings timings = CombatMath.timings(entity, profile, spec.get());
        if (data.machine.isRiposteReady()) {
            timings = new AttackTimings(Math.round(timings.windup() * profile.riposteWindupMult()),
                    timings.release(), timings.recovery());
        }
        if (!data.machine.startAttack(type, timings)) {
            return false;
        }
        data.profileKey = resolved.get().key();
        data.weapon = entity.getMainHandItem().copy();
        data.queuedAttack = null;
        data.resetSwing();
        data.prevYaw = CombatMath.viewYaw(entity);
        data.prevPitch = entity.getXRot();
        data.prevPivot = CombatMath.pivot(entity, 1f);
        return true;
    }

    /** Raises a weapon parry, if the held weapon has a guard. Shared by server and client prediction. */
    public static boolean startParry(LivingEntity entity, CombatData data) {
        if (isHolstered(entity, data)) {
            return false;
        }
        Optional<WeaponProfiles.Resolved> resolved = WeaponProfiles.resolveFor(entity);
        Optional<WeaponProfile.GuardSpec> guard = resolved.flatMap(r -> r.profile().guard());
        if (guard.isEmpty() || !data.machine.startParry(guard.get().parryTicks(), guard.get().recovery())) {
            return false;
        }
        data.profileKey = resolved.get().key();
        data.weapon = entity.getMainHandItem().copy();
        data.queuedAttack = null;
        return true;
    }

    /**
     * Server entry point for an attack request. Requests arriving while the fighter is finishing something (recovery,
     * lowering a guard) are buffered and start the moment they're free; this also absorbs the latency gap between the
     * client's prediction and the server.
     */
    public static void requestAttack(LivingEntity entity, AttackType type) {
        CombatData data = entity.getData(ModAttachments.COMBAT);
        Phase phase = data.machine.phase();
        if (phase == Phase.RECOVERY || phase == Phase.GUARD_RECOVERY) {
            data.queuedAttack = type;
            return;
        }
        if (start(entity, data, type)) {
            onAttackStarted(entity);
            sync(entity, data, false);
        } else {
            // Tell the client its prediction was wrong so it drops the attack.
            sync(entity, data, true);
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
                onReleaseStarted(entity);
            }
            List<LivingEntity> hits = SwingTracer.trace(entity, data, spec.get(), sweep);
            for (LivingEntity target : hits) {
                applyHit(entity, target, machine.type(), spec.get());
                if (machine.phase() != Phase.RELEASE && machine.phase() != Phase.RECOVERY) {
                    break; // parried or blocked: the swing stops here
                }
            }
            if (before == Phase.RELEASE && machine.phase() == Phase.RECOVERY && data.hitThisSwing.isEmpty()) {
                data.stamina.spend(spec.get().staminaCost()); // whiffing costs stamina
            }
        }
        data.prevYaw = CombatMath.viewYaw(entity);
        data.prevPitch = entity.getXRot();
        data.prevPivot = CombatMath.pivot(entity, 1f);

        if (!machine.isBusy() && data.queuedAttack != null) {
            AttackType queued = data.queuedAttack;
            data.queuedAttack = null;
            if (start(entity, data, queued)) {
                onAttackStarted(entity);
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
        if (!data.machine.isBusy() && data.queuedAttack != null) {
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

    public static Optional<WeaponProfile.AttackSpec> currentSpec(LivingEntity entity, CombatData data) {
        return currentProfile(entity, data).flatMap(profile -> profile.attack(data.machine.type()));
    }

    public static Optional<WeaponProfile> currentProfile(LivingEntity entity, CombatData data) {
        if (data.profileKey == null) {
            return Optional.empty();
        }
        return WeaponProfiles.get(data.profileKey, entity.level().registryAccess());
    }

    private static boolean isHolstered(LivingEntity entity, CombatData data) {
        if (entity instanceof Player player && !entity.getMainHandItem().isEmpty()
                && player.getCooldowns().isOnCooldown(entity.getMainHandItem().getItem())) {
            return true;
        }
        return data.holsteredUntil > entity.level().getGameTime();
    }

    /**
     * Applies a hit through vanilla attack code, so enchantments, Spartan Weaponry traits and other mods'
     * attack listeners all still apply. {@link DefenseEvents} decides parries and blocks, and applies the attack's
     * damage multiplier while the {@link SwingContext} is active.
     */
    private static void applyHit(LivingEntity attacker, LivingEntity target, AttackType type, WeaponProfile.AttackSpec spec) {
        SwingContext.run(attacker, type, spec, () -> {
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

    /** Telegraph: an audible cue at the start of every windup, so attacks from off-screen can still be read. */
    private static void onAttackStarted(LivingEntity entity) {
        entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                SoundEvents.ARMOR_EQUIP_CHAIN.value(), entity.getSoundSource(), 0.6f, 1.4f);
    }

    private static void onReleaseStarted(LivingEntity entity) {
        entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                SoundEvents.PLAYER_ATTACK_SWEEP, entity.getSoundSource() == SoundSource.PLAYERS ? SoundSource.PLAYERS : SoundSource.HOSTILE,
                0.7f, 1.1f);
        if (!(entity instanceof Player)) {
            entity.swing(InteractionHand.MAIN_HAND, true); // mobs have no attack animation of their own yet (M4)
        }
    }

    /**
     * @param authoritative true when the server overrides the client (cancel, rejected prediction, parry outcomes).
     *                      Non-authoritative updates are ignored for the local player, whose own prediction runs the
     *                      same machine.
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
