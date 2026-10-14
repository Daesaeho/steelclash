package com.steelclash.combat;

import com.steelclash.Config;
import com.steelclash.core.SwingTurn;
import com.steelclash.core.AttackTimings;
import com.steelclash.core.Vec;
import com.steelclash.profile.WeaponProfile;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
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

    /**
     * The view the swing is traced along this tick: the actual view, or while {@code capped} (windup and release) one
     * that can only have turned {@code turnCapDegreesPerSecond} since last tick (the turn cap). {@code [yaw, pitch]}.
     */
    public static float[] swingView(LivingEntity entity, CombatData data, boolean capped) {
        float yaw = viewYaw(entity);
        float pitch = entity.getXRot();
        if (!capped) {
            return new float[]{yaw, pitch};
        }
        float step = (float) (Config.TURN_CAP.get() / 20.0);
        return new float[]{SwingTurn.approachYaw(data.prevYaw, yaw, step), SwingTurn.approachPitch(data.prevPitch, pitch, step)};
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

    /** Phase timings for this wielder, rescaled by their attack speed (Spartan heavy/lightweight traits too). */
    public static AttackTimings timings(LivingEntity entity, WeaponProfile profile, WeaponProfile.AttackSpec spec) {
        return spec.timings().scaledForSpeed(attackSpeed(entity, profile), profile.referenceAttackSpeed(), profile.speedScaling());
    }

    /**
     * The wielder's attack speed. Players have the ATTACK_SPEED attribute, with their weapon's modifiers in it. Most mobs
     * don't have it, so they swing as fast as a player would with the same weapon: the weapon's modifiers applied to a
     * fresh attack speed attribute, so vanilla combines them exactly as it does for players. A weapon that sets no
     * speed (or bare claws) swings at the profile's reference speed. Only worked out when an attack starts.
     */
    static double attackSpeed(LivingEntity entity, WeaponProfile profile) {
        if (entity.getAttributes().hasAttribute(Attributes.ATTACK_SPEED)) {
            return entity.getAttributeValue(Attributes.ATTACK_SPEED);
        }
        AttributeInstance speed = new AttributeInstance(Attributes.ATTACK_SPEED, ignored -> {
        });
        entity.getMainHandItem().forEachModifier(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
            if (attribute.value() == Attributes.ATTACK_SPEED.value()) {
                speed.addOrUpdateTransientModifier(modifier);
            }
        });
        return speed.getModifiers().isEmpty() ? profile.referenceAttackSpeed() : speed.getValue();
    }

    public static Vec toVec(Vec3 v) {
        return new Vec(v.x, v.y, v.z);
    }

    public static Vec3 toVec3(Vec v) {
        return new Vec3(v.x(), v.y(), v.z());
    }
}
