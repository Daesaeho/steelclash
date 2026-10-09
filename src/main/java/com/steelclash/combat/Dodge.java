package com.steelclash.combat;

import com.steelclash.Config;
import com.steelclash.core.Phase;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Chivalry 2 dash / dodge: a quick burst of movement that costs stamina and has a cooldown. It can abandon a windup
 * (like a feint) or a raised guard, but not a jab, kick, heavy, counter that already caught its attack, or swing in release,
 * its recovery, or a stagger. The rules are shared: the client checks them before moving itself (players move
 * client-side), the server checks them again to charge the stamina and cancel the attack or guard.
 */
public final class Dodge {
    /** The dash lasts about this long; a guard can't be raised during the first half of it (Chivalry 2 2.11). */
    public static final int DASH_TICKS = 6;
    /** A jab started this soon after a dodge winds up {@link #JAB_AFTER_DODGE_EXTRA_WINDUP} ticks slower (2.9). */
    public static final int JAB_AFTER_DODGE_TICKS = 10;
    public static final int JAB_AFTER_DODGE_EXTRA_WINDUP = 3;
    /** After being disarmed, no dodge or jab for this long (2.5: winning the stamina war earns a free hit). */
    public static final int POST_DISARM_COOLDOWN_TICKS = 30;

    private Dodge() {
    }

    /** Within the first half of a dash: too soon to raise a guard. */
    public static boolean tooSoonToParry(LivingEntity entity, CombatData data) {
        return entity.level().getGameTime() - data.dodgedAt < DASH_TICKS / 2;
    }

    /** Right after a dodge: jabs come out slower. */
    public static boolean justDodged(LivingEntity entity, CombatData data) {
        return entity.level().getGameTime() - data.dodgedAt < JAB_AFTER_DODGE_TICKS;
    }

    public static boolean canDodge(LivingEntity entity, CombatData data) {
        Phase phase = data.machine.phase();
        boolean free = phase == Phase.IDLE || data.machine.canDodgeWindup()
                || phase == Phase.PARRY || phase == Phase.GUARD_RECOVERY;
        return free && entity.onGround() && entity.level().getGameTime() >= data.dodgeReadyAt
                && !data.stamina.isExhausted() && data.stamina.current() >= Config.DODGE_STAMINA_COST.get();
    }

    /** Spends the stamina, starts the cooldown and drops a windup or guard. Moving is up to the caller. */
    public static boolean perform(LivingEntity entity, CombatData data) {
        if (data.isDowned() || !canDodge(entity, data)) {
            return false;
        }
        if (data.machine.phase() == Phase.WINDUP) {
            data.machine.feint();
        } else if (data.machine.phase() == Phase.PARRY) {
            data.machine.releaseParry();
        }
        data.queuedAttack = null;
        data.stamina.spend(Config.DODGE_STAMINA_COST.get().floatValue());
        data.dodgeReadyAt = entity.level().getGameTime() + Config.DODGE_COOLDOWN_TICKS.get();
        data.dodgedAt = entity.level().getGameTime();
        return true;
    }

    /**
     * The burst velocity for a dodge in the given input direction (vanilla movement input: {@code strafe} positive to
     * the left, {@code forward} positive ahead); no input dodges backwards.
     */
    public static Vec3 velocity(float yawDegrees, float strafe, float forward) {
        if (Math.abs(strafe) < 1e-3 && Math.abs(forward) < 1e-3) {
            forward = -1;
        }
        double length = Math.sqrt(strafe * strafe + forward * forward);
        double speed = Config.DODGE_SPEED.get();
        double x = strafe / length * speed;
        double z = forward / length * speed;
        float sin = Mth.sin(yawDegrees * Mth.DEG_TO_RAD);
        float cos = Mth.cos(yawDegrees * Mth.DEG_TO_RAD);
        return new Vec3(x * cos - z * sin, 0, z * cos + x * sin);
    }

    /** Server entry point for a player's dodge. */
    public static void request(LivingEntity entity) {
        CombatData data = entity.getData(ModAttachments.COMBAT);
        if (perform(entity, data)) {
            Feedback.feint(entity);
            Combat.sync(entity, data, false);
        } else {
            Combat.sync(entity, data, true);
        }
    }
}
