package com.steelclash.core;

import java.util.HashMap;
import java.util.Map;

/**
 * How a body flinches from a blow (presentation only): the part the blade met is thrown the way the blade was
 * travelling, and the whole body tips a little the same way. Angles are degrees in Minecraft's model-part convention:
 * {@code xRot} + swings a limb back and nods the head down; {@code yRot} + turns the head to the body's right;
 * {@code zRot} + swings a limb toward the body's right and tips the head to its left.
 *
 * @param parts additive part rotations ({@code head}, {@code rightArm}, {@code leftArm}, {@code rightLeg},
 *              {@code leftLeg}) at full strength
 * @param tilt  whole-body tip at full strength, degrees, toward ({@code forward}, {@code left})
 */
public record HitReaction(Map<String, double[]> parts, double tilt, double forward, double left) {
    /** Seconds the snap takes to peak. */
    static final double RISE = 0.05;
    /** Seconds over which it then fades by a factor e. */
    static final double DECAY = 0.11;
    /** After this many seconds the reaction is over. */
    public static final double LENGTH = 0.6;

    /**
     * @param forward  the blade's travel along the body's facing (+ = pushes the body forward), normalised with
     *                 {@code left} and {@code down}
     * @param left     its travel toward the body's left
     * @param down     its travel downward
     * @param strength 1 for a light blow, more for a heavy
     */
    public static HitReaction of(HitLocation.Region region, double forward, double left, double down, double strength) {
        Map<String, double[]> parts = new HashMap<>();
        double tilt;
        switch (region) {
            case HEAD -> {
                parts.put("head", new double[]{(30 * forward + 25 * Math.max(0, down)) * strength,
                        -25 * left * strength, 15 * left * strength});
                tilt = 6;
            }
            case LEFT_ARM, RIGHT_ARM -> {
                parts.put(region == HitLocation.Region.LEFT_ARM ? "leftArm" : "rightArm",
                        new double[]{-45 * forward * strength, 0, -40 * left * strength});
                tilt = 5;
            }
            case LEFT_LEG, RIGHT_LEG -> {
                parts.put(region == HitLocation.Region.LEFT_LEG ? "leftLeg" : "rightLeg",
                        new double[]{-35 * forward * strength, 0, -20 * left * strength});
                tilt = 4;
            }
            default -> {
                parts.put("head", new double[]{10 * forward * strength, 0, 0});
                tilt = 10;
            }
        }
        double across = Math.hypot(forward, left);
        // A blow straight down (an overhead) has no sideways travel: the body folds forward under it.
        double towardForward = across < 1e-3 ? 1 : forward / across;
        double towardLeft = across < 1e-3 ? 0 : left / across;
        return new HitReaction(parts, tilt * strength, towardForward, towardLeft);
    }

    /** How much of the reaction shows {@code seconds} after the blow: a quick snap, then an easing back. */
    public static double envelope(double seconds) {
        if (seconds < 0 || seconds >= LENGTH) {
            return 0;
        }
        if (seconds < RISE) {
            double t = seconds / RISE;
            return t * t * (3 - 2 * t);
        }
        return Math.exp(-(seconds - RISE) / DECAY);
    }

    /**
     * Axis to turn a body about so its top tips toward ({@code forward}, {@code left}), in the frame a renderer has just
     * after turning to the body's yaw (before the model flip): forward is -Z, the body's left is -X, up is +Y. A
     * positive (right-handed) turn about it tips the body that way.
     *
     * @return a unit axis {x, y, z}, or null for no direction
     */
    public static double[] tiltAxis(double forward, double left) {
        double length = Math.hypot(forward, left);
        if (length < 1e-9) {
            return null;
        }
        return new double[]{-forward / length, 0, left / length};
    }
}
