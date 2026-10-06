package com.steelclash.core;

import java.util.List;

/**
 * Where the blade points during the release, as offsets from the wielder's current view direction.
 * Because offsets are relative to the live view, turning during the release drags or accelerates the swing,
 * just like Chivalry 2.
 * <p>
 * Yaw offset follows Minecraft's convention: positive turns right. Pitch offset: positive looks down.
 * {@code extension} scales blade length (thrusts start retracted and extend).
 */
public record ArcPath(List<Keyframe> keyframes) {
    public record Keyframe(double t, double yaw, double pitch, double extension) {
    }

    public ArcPath {
        if (keyframes.isEmpty()) {
            throw new IllegalArgumentException("ArcPath needs at least one keyframe");
        }
        keyframes = List.copyOf(keyframes);
    }

    /** Right-to-left horizontal cut, slightly descending. */
    public static ArcPath horizontal(double widthDegrees) {
        double half = widthDegrees / 2;
        return new ArcPath(List.of(
                new Keyframe(0.0, half, -8, 1.0),
                new Keyframe(0.5, 0, 2, 1.0),
                new Keyframe(1.0, -half, 12, 1.0)));
    }

    /** Top-down cut with a slight diagonal. */
    public static ArcPath vertical() {
        return new ArcPath(List.of(
                new Keyframe(0.0, 12, -70, 0.95),
                new Keyframe(0.5, 4, 0, 1.0),
                new Keyframe(1.0, -4, 50, 1.0)));
    }

    /** Straight thrust: the blade extends from retracted to full length, then holds. */
    public static ArcPath thrust() {
        return new ArcPath(List.of(
                new Keyframe(0.0, 4, 4, 0.35),
                new Keyframe(0.6, 0, 0, 1.0),
                new Keyframe(1.0, 0, 0, 1.0)));
    }

    /** A low, short push: kicks and shield bashes aim at the body. */
    public static ArcPath kick() {
        return new ArcPath(List.of(
                new Keyframe(0.0, 0, 25, 0.5),
                new Keyframe(0.5, 0, 20, 1.0),
                new Keyframe(1.0, 0, 20, 1.0)));
    }

    public static ArcPath defaultFor(AttackType type) {
        return switch (type) {
            case SLASH -> horizontal(140);
            case OVERHEAD -> vertical();
            case STAB, JAB -> thrust();
            case KICK, THROW -> kick();
            case SPECIAL -> thrust();
        };
    }

    /** The same swing from the other side: a right-to-left slash becomes left-to-right. */
    public ArcPath mirrored() {
        return new ArcPath(keyframes.stream()
                .map(k -> new Keyframe(k.t(), -k.yaw(), k.pitch(), k.extension()))
                .toList());
    }

    /** Total angle the blade sweeps (degrees); thrusts barely sweep at all, they extend. */
    public double sweepDegrees() {
        double total = 0;
        for (int i = 1; i < keyframes.size(); i++) {
            Keyframe a = keyframes.get(i - 1);
            Keyframe b = keyframes.get(i);
            total += Math.hypot(b.yaw() - a.yaw(), b.pitch() - a.pitch());
        }
        return total;
    }

    /**
     * Blade twist: how far to roll the weapon around its own length at progress {@code t} so the edge leads the cut.
     * 0 = edge down (a vertical cut like an overhead), -90 = turned for a cut travelling right to left (yaw falling),
     * +90 for left to right. Thrusts are held flat (90). Smoothed over a window so keyframe corners don't snap.
     */
    public double edgeAngle(double t) {
        if (sweepDegrees() < THRUST_SWEEP) {
            return 90;
        }
        Keyframe a = sample(t - EDGE_WINDOW);
        Keyframe b = sample(t + EDGE_WINDOW);
        double yaw = b.yaw() - a.yaw();
        double pitch = b.pitch() - a.pitch();
        if (Math.hypot(yaw, pitch) < 1) { // the blade is momentarily still: use the whole swing's direction
            a = sample(0);
            b = sample(1);
            yaw = b.yaw() - a.yaw();
            pitch = b.pitch() - a.pitch();
        }
        return Math.toDegrees(Math.atan2(yaw, pitch));
    }

    private static final double THRUST_SWEEP = 20;
    private static final double EDGE_WINDOW = 0.15;

    /** Linearly interpolated keyframe at release progress {@code t} in [0, 1]. */
    public Keyframe sample(double t) {
        t = Math.max(0, Math.min(1, t));
        Keyframe prev = keyframes.get(0);
        if (t <= prev.t()) {
            return new Keyframe(t, prev.yaw(), prev.pitch(), prev.extension());
        }
        for (int i = 1; i < keyframes.size(); i++) {
            Keyframe next = keyframes.get(i);
            if (t <= next.t()) {
                double span = next.t() - prev.t();
                double f = span <= 0 ? 1 : (t - prev.t()) / span;
                return new Keyframe(t,
                        prev.yaw() + (next.yaw() - prev.yaw()) * f,
                        prev.pitch() + (next.pitch() - prev.pitch()) * f,
                        prev.extension() + (next.extension() - prev.extension()) * f);
            }
            prev = next;
        }
        return new Keyframe(t, prev.yaw(), prev.pitch(), prev.extension());
    }
}
