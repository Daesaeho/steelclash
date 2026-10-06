package com.steelclash.combat;

import com.steelclash.core.AttackTimings;
import com.steelclash.core.Vec;
import com.steelclash.profile.WeaponProfile;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Minecraft-side helpers shared by the server tracer and the client visuals, so both compute the same blade. */
public final class CombatMath {
    /** Blade pivot sits a little below the eyes, roughly at the shoulder. */
    private static final double PIVOT_BELOW_EYES = 0.2;
    /** Mob reach beyond the edge of their hitbox; vanilla melee reaches roughly 1.4–2 blocks for a zombie. */
    private static final double MOB_ARM_REACH = 1.8;

    private CombatMath() {
    }

    public static Vec3 pivot(LivingEntity entity, float partialTick) {
        return entity.getEyePosition(partialTick).subtract(0, PIVOT_BELOW_EYES, 0);
    }

    /** Where the fighter is looking. Mobs aim with their head, which turns independently of the body. */
    public static float viewYaw(LivingEntity entity) {
        return entity instanceof Player ? entity.getYRot() : entity.getYHeadRot();
    }

    public static float viewYaw(LivingEntity entity, float partialTick) {
        return entity instanceof Player
                ? entity.getViewYRot(partialTick)
                : Mth.rotLerp(partialTick, entity.yHeadRotO, entity.yHeadRot);
    }

    /**
     * Blade length: the wielder's entity interaction range (which Spartan Weaponry's REACH traits modify) plus the
     * attack's own reach bonus.
     */
    public static double bladeLength(LivingEntity entity, WeaponProfile.AttackSpec spec) {
        double base = entity instanceof Player player
                ? player.entityInteractionRange()
                : entity.getBbWidth() / 2 + MOB_ARM_REACH;
        return Math.max(0.5, base + spec.reachBonus());
    }

    /** Phase timings for this wielder, rescaled by their ATTACK_SPEED attribute (Spartan heavy/lightweight traits). */
    public static AttackTimings timings(LivingEntity entity, WeaponProfile profile, WeaponProfile.AttackSpec spec) {
        double attackSpeed = entity.getAttributes().hasAttribute(Attributes.ATTACK_SPEED)
                ? entity.getAttributeValue(Attributes.ATTACK_SPEED)
                : profile.referenceAttackSpeed();
        return spec.timings().scaledForSpeed(attackSpeed, profile.referenceAttackSpeed(), profile.speedScaling());
    }

    public static Vec toVec(Vec3 v) {
        return new Vec(v.x, v.y, v.z);
    }

    public static Vec3 toVec3(Vec v) {
        return new Vec3(v.x(), v.y(), v.z());
    }
}
