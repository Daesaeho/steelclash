package com.steelclash.core;

/**
 * Converts "point the weapon this way" into Minecraft humanoid arm rotations (radians), using the same convention
 * vanilla uses to aim a bow: an arm hangs down at xRot 0 and points along the view at xRot = pitch - 90°, with
 * yRot = yaw relative to the body.
 * <p>
 * Vanilla holds a handheld item with its blade roughly perpendicular to the arm (pointing forward when the arm hangs),
 * {@link #BLADE_TO_ARM_DEGREES} short of the arm's axis. To put the <em>blade</em> on the arc either the item is
 * rotated by that much in the hand ({@link #aimArm}, players via Player Animation Library) or, where the item can't be rotated,
 * the arm is pitched below the aim by that much ({@link #aimArmForBlade}, mobs).
 */
public final class ArmAim {
    /** Angle between a vanilla handheld item's blade and the holding arm's axis (third person, handheld model). */
    public static final double BLADE_TO_ARM_DEGREES = 80;

    private ArmAim() {
    }

    /** Arm rotation pointing the arm itself along (yaw relative to body, pitch), degrees in, radians out. */
    public static double[] aimArm(double relativeYawDegrees, double pitchDegrees) {
        double pitch = Math.max(-90, Math.min(90, pitchDegrees));
        return new double[]{Math.toRadians(pitch - 90), Math.toRadians(relativeYawDegrees), 0};
    }

    /** Arm rotation that puts an un-rotated held blade along (yaw relative to body, pitch). */
    public static double[] aimArmForBlade(double relativeYawDegrees, double pitchDegrees) {
        double pitch = Math.max(-90, Math.min(90, pitchDegrees));
        double armPitch = Math.max(-170, Math.min(60, pitch - 90 + BLADE_TO_ARM_DEGREES));
        return new double[]{Math.toRadians(armPitch), Math.toRadians(relativeYawDegrees), 0};
    }

    /** Direction an arm with the given rotation points, in model space (+Y down, -Z forward, -X = right side). */
    public static Vec armDirection(double xRot, double yRot) {
        // ModelPart applies Rz * Ry * Rx; the arm's axis is +Y.
        return new Vec(Math.sin(xRot) * Math.sin(yRot), Math.cos(xRot), Math.sin(xRot) * Math.cos(yRot));
    }

    /** Model-space direction for a view-relative (yaw, pitch): forward -Z, down +Y, right -X. */
    public static Vec modelDirection(double relativeYawDegrees, double pitchDegrees) {
        double yaw = Math.toRadians(relativeYawDegrees);
        double pitch = Math.toRadians(pitchDegrees);
        return new Vec(-Math.sin(yaw) * Math.cos(pitch), Math.sin(pitch), -Math.cos(yaw) * Math.cos(pitch));
    }
}
