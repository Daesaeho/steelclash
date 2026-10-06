package com.steelclash.core;

/**
 * Turning during swings. A blade's world direction is the view plus the arc ({@link Blade#at}), so turning with the
 * swing (an <b>accel</b>) lands it earlier in the release and turning against it (a <b>drag</b>) lands it later.
 * The turn cap keeps that a skill rather than a 180° flick: during windup and release the view may only turn so fast.
 */
public final class SwingTurn {
    /** A bot's deliberate turn during its release. */
    public enum Trick {
        NONE, ACCEL, DRAG
    }

    private SwingTurn() {
    }

    public static float wrapDegrees(float degrees) {
        float d = degrees % 360f;
        if (d >= 180f) {
            d -= 360f;
        } else if (d < -180f) {
            d += 360f;
        }
        return d;
    }

    /** Turns {@code from} toward {@code to} by at most {@code maxStep} degrees, the short way round. No limit if maxStep <= 0. */
    public static float approachYaw(float from, float to, float maxStep) {
        if (maxStep <= 0) {
            return to;
        }
        float delta = wrapDegrees(to - from);
        return from + Math.max(-maxStep, Math.min(maxStep, delta));
    }

    /** Same for pitch (no wrapping). */
    public static float approachPitch(float from, float to, float maxStep) {
        if (maxStep <= 0) {
            return to;
        }
        return from + Math.max(-maxStep, Math.min(maxStep, to - from));
    }

    /** Which way an arc sweeps in yaw: +1 (yaw rising, left to right), -1 (right to left), 0 (overheads, thrusts). */
    public static int travel(ArcPath path) {
        double sweep = path.sample(1).yaw() - path.sample(0).yaw();
        return Math.abs(sweep) < 20 ? 0 : (int) Math.signum(sweep);
    }

    /**
     * How far a bot turns its view away from facing its target at release progress {@code t}: with the swing for an
     * accel, against it for a drag, building up linearly to {@code amount} degrees by the end of the release.
     */
    public static double trickYaw(Trick trick, int travel, double t, double amount) {
        if (trick == Trick.NONE || travel == 0) {
            return 0;
        }
        double direction = trick == Trick.ACCEL ? travel : -travel;
        return direction * amount * Math.max(0, Math.min(1, t));
    }
}
