package com.steelclash.core;

/**
 * Which part of a body a blade met, from where on the body's hitbox it crossed in. Minecraft bodies are one box, so the
 * parts are proportions of a biped standing in it: the head above the eyes' band, the legs below the hips (12 of 32
 * pixels), and the arms at the box's sides. Presentation only: reactions, sounds and blood use it, damage doesn't.
 */
public final class HitLocation {
    /** Legs end at the hips: 12 of a biped's 32 pixels. */
    static final double LEGS_BELOW = 0.375;
    /** The head starts this far (fraction of the height) below the eyes. */
    static final double HEAD_BELOW_EYES = 0.12;
    /** Past this fraction of the half width from the middle, a hit at chest height is on an arm. */
    static final double ARM_FROM = 0.85;

    public enum Region {
        HEAD, TORSO, LEFT_ARM, RIGHT_ARM, LEFT_LEG, RIGHT_LEG;

        public boolean isArm() {
            return this == LEFT_ARM || this == RIGHT_ARM;
        }

        public boolean isLeg() {
            return this == LEFT_LEG || this == RIGHT_LEG;
        }
    }

    private HitLocation() {
    }

    /**
     * @param dx       contact point minus the body's feet, blocks
     * @param bodyYaw  the body's Minecraft yaw, degrees
     * @param height   hitbox height
     * @param width    hitbox width
     * @param eyes     eye height above the feet
     */
    public static Region classify(double dx, double dy, double dz, double bodyYaw, double height, double width, double eyes) {
        double up = height <= 0 ? 0.5 : dy / height;
        double headFrom = Math.max(0.55, Math.min(0.95, eyes / Math.max(1e-6, height) - HEAD_BELOW_EYES));
        double lateral = left(dx, dz, bodyYaw); // + = the body's left
        if (up >= headFrom) {
            return Region.HEAD;
        }
        if (up < LEGS_BELOW) {
            return lateral >= 0 ? Region.LEFT_LEG : Region.RIGHT_LEG;
        }
        if (Math.abs(lateral) >= ARM_FROM * width / 2) {
            return lateral > 0 ? Region.LEFT_ARM : Region.RIGHT_ARM;
        }
        return Region.TORSO;
    }

    /** How far {@code (x, z)} points along a body's facing, for a Minecraft yaw in degrees. */
    public static double forward(double x, double z, double bodyYaw) {
        double yaw = Math.toRadians(bodyYaw);
        return -Math.sin(yaw) * x + Math.cos(yaw) * z;
    }

    /** How far {@code (x, z)} points to the body's left, for a Minecraft yaw in degrees. */
    public static double left(double x, double z, double bodyYaw) {
        double yaw = Math.toRadians(bodyYaw);
        return Math.cos(yaw) * x + Math.sin(yaw) * z;
    }
}
