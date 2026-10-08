package com.steelclash.core;

/** Blade geometry: a line segment from the wielder's pivot (shoulder) out to the tip. */
public final class Blade {
    private Blade() {
    }

    public record Segment(Vec hilt, Vec tip) {
    }

    /** Unit look vector for a Minecraft yaw/pitch in degrees. */
    public static Vec direction(double yawDegrees, double pitchDegrees) {
        double yaw = Math.toRadians(yawDegrees);
        double pitch = Math.toRadians(pitchDegrees);
        double cosPitch = Math.cos(pitch);
        return new Vec(-Math.sin(yaw) * cosPitch, -Math.sin(pitch), Math.cos(yaw) * cosPitch);
    }

    /**
     * The blade at release progress {@code t}, for a wielder at {@code pivot} currently looking along
     * ({@code viewYaw}, {@code viewPitch}).
     */
    public static Segment at(Vec pivot, double viewYaw, double viewPitch, ArcPath path, double t, double length) {
        ArcPath.Keyframe k = path.sample(t);
        double pitch = Math.max(-90, Math.min(90, viewPitch + k.pitch()));
        Vec dir = direction(viewYaw + k.yaw(), pitch);
        return new Segment(pivot, pivot.add(dir.scale(length * k.extension())));
    }

    /**
     * Slab test: does the segment a→b pass through the axis-aligned box [min, max]?
     */
    public static boolean intersectsBox(Vec a, Vec b, Vec min, Vec max) {
        return intersectsBox(a, b, min.x(), min.y(), min.z(), max.x(), max.y(), max.z());
    }

    /** Scalar bounds let callers use an existing hitbox without allocating two temporary vectors. */
    public static boolean intersectsBox(Vec a, Vec b, double minX, double minY, double minZ,
                                        double maxX, double maxY, double maxZ) {
        double tMin = 0;
        double tMax = 1;
        // This runs for every candidate at every blade sub-step; keep the slab data in scalars.
        for (int axis = 0; axis < 3; axis++) {
            double start;
            double delta;
            double lo;
            double hi;
            switch (axis) {
                case 0 -> {
                    start = a.x();
                    delta = b.x() - start;
                    lo = minX;
                    hi = maxX;
                }
                case 1 -> {
                    start = a.y();
                    delta = b.y() - start;
                    lo = minY;
                    hi = maxY;
                }
                default -> {
                    start = a.z();
                    delta = b.z() - start;
                    lo = minZ;
                    hi = maxZ;
                }
            }
            if (Math.abs(delta) < 1e-9) {
                if (start < lo || start > hi) {
                    return false;
                }
            } else {
                double inv = 1.0 / delta;
                double t1 = (lo - start) * inv;
                double t2 = (hi - start) * inv;
                if (t1 > t2) {
                    double tmp = t1;
                    t1 = t2;
                    t2 = tmp;
                }
                tMin = Math.max(tMin, t1);
                tMax = Math.min(tMax, t2);
                if (tMin > tMax) {
                    return false;
                }
            }
        }
        return true;
    }
}
