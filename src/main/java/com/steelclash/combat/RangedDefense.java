package com.steelclash.combat;

import com.steelclash.Config;
import com.steelclash.core.Guard;
import com.steelclash.core.Phase;
import com.steelclash.net.FeedbackPayload;
import com.steelclash.net.ModNetwork;
import com.steelclash.profile.WeaponProfile;
import com.steelclash.profile.WeaponProfiles;
import java.util.Optional;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Chivalry 2 projectile rules (architecture plan §22, report §19): arrows, bolts and thrown weapons meet a fighter's
 * weapon in one of three ways, and hit harder in the head.
 * <ul>
 *     <li><b>Counter:</b> a slash, overhead or stab started at most {@code projectileCounterMillis} (0.25 s) before the
 *     projectile arrives from the front deflects it. The attack carries on. Not the melee counter rule: any weapon
 *     attack works, whatever the projectile.</li>
 *     <li><b>Weapon block:</b> a held weapon guard facing it takes 30% off and costs a little stamina, but never breaks
 *     the guard or disarms (Chivalry 2 update 2.4.2).</li>
 *     <li><b>Shield:</b> vanilla's shield block, narrowed to the shield's cone ({@link Defense#onShieldBlock}).</li>
 * </ul>
 * Headshots: a projectile whose path crosses the target's head band deals {@code headshotMultiplier} (+25%), and the
 * shooting player hears it. Getting hurt while drawing a bow or loading a crossbow drops the draw.
 */
public final class RangedDefense {
    /** Share of the body, measured down from the eyes, that counts as the head (scaled by the target's height). */
    private static final double HEAD_BELOW_EYES = 0.25 / 1.8;

    private RangedDefense() {
    }

    /** What a projectile hitting a fighter turned into. */
    public enum Outcome { NONE, DEFLECTED, WEAPON_BLOCKED }

    /**
     * A projectile is about to hurt {@code defender}: counter it, or blunt it on a weapon guard.
     *
     * @return the outcome; on {@link Outcome#DEFLECTED} the damage must be cancelled (the projectile then bounces off)
     */
    public static Outcome defend(LivingEntity defender, Projectile projectile) {
        if (!defender.hasData(ModAttachments.COMBAT) || defender.isBlocking()) {
            return Outcome.NONE; // a raised shield is vanilla's business
        }
        CombatData data = defender.getData(ModAttachments.COMBAT);
        Optional<WeaponProfile.GuardSpec> guard = Combat.currentProfile(defender, data).flatMap(WeaponProfile::guard);
        if (guard.isEmpty()) {
            guard = WeaponProfiles.resolveFor(defender).flatMap(r -> r.profile().guard());
        }
        if (guard.isEmpty() || !fromFront(defender, projectile, guard.get().cone())) {
            return Outcome.NONE;
        }
        int counterMillis = Config.PROJECTILE_COUNTER_MILLIS.get();
        if (counterMillis > 0 && data.machine.phase() == Phase.WINDUP && data.machine.type().isWeaponAttack()
                && data.machine.phaseElapsedUs() <= counterMillis * 1000L) {
            data.stamina.spend(Config.PROJECTILE_STAMINA_DAMAGE.get().floatValue() * guard.get().staminaMult() * 0.5f);
            Feedback.parry(defender, projectile);
            return Outcome.DEFLECTED;
        }
        if (data.machine.phase() == Phase.PARRY) {
            // Spent but never below zero into a guard break: arrows can't disarm (Chivalry 2 update 2.4.2).
            float cost = Math.min(data.stamina.current(),
                    Config.PROJECTILE_STAMINA_DAMAGE.get().floatValue() * guard.get().staminaMult());
            data.stamina.spend(cost);
            Feedback.parry(defender, projectile);
            return Outcome.WEAPON_BLOCKED;
        }
        return Outcome.NONE;
    }

    /** Damage after the weapon guard took its share. */
    public static float blockedDamage(float amount) {
        return amount * (float) (1 - Config.PROJECTILE_WEAPON_BLOCK_REDUCTION.get());
    }

    /** Did the projectile come at the defender's front, within the cone? Judged from where it's flying from. */
    static boolean fromFront(LivingEntity defender, Projectile projectile, double cone) {
        Vec3 motion = projectile.getDeltaMovement();
        Vec3 from = motion.horizontalDistanceSqr() > 1e-6
                ? defender.position().subtract(motion.normalize().scale(4)) // back along its flight
                : projectile.position();
        return Guard.inCone(CombatMath.viewYaw(defender), defender.getX(), defender.getZ(), from.x, from.z, cone);
    }

    /**
     * Whether the projectile is hitting the head: its path this tick crosses the target's box at or above the head
     * band (the top of the box down to a little below the eyes).
     */
    public static boolean isHeadshot(LivingEntity target, Projectile projectile) {
        Vec3 start = projectile.position();
        Vec3 end = start.add(projectile.getDeltaMovement());
        AABB box = target.getBoundingBox().inflate(0.3); // projectiles hit boxes inflated like this
        Vec3 hit = box.clip(start, end).orElse(box.contains(start) ? start : end);
        return hit.y >= headBottom(target);
    }

    public static double headBottom(LivingEntity target) {
        return target.getEyeY() - HEAD_BELOW_EYES * target.getBbHeight();
    }

    /** The shooter of a headshot hears it, like Chivalry 2's headshot sound. */
    public static void headshotFeedback(@Nullable Entity shooter) {
        if (shooter instanceof ServerPlayer player) {
            ModNetwork.sendTo(player, new FeedbackPayload(FeedbackPayload.Kind.HEADSHOT, 1f));
        }
    }

    /** Hurt while drawing a bow or loading a crossbow: the draw is lost. */
    public static void interruptDraw(LivingEntity entity) {
        if (!Config.INTERRUPT_DRAW_ON_HIT.get() || !entity.isUsingItem()
                || !(entity.getUseItem().getItem() instanceof ProjectileWeaponItem)) {
            return;
        }
        entity.stopUsingItem();
        if (entity instanceof ServerPlayer player) {
            ModNetwork.sendTo(player, new FeedbackPayload(FeedbackPayload.Kind.DRAW_INTERRUPTED, 1f));
        }
    }
}
