package com.steelclash.core;

/** Shortest-path rotation blending, independent of Minecraft and Euler angle branch choices. */
public final class RotationBlend {
    private RotationBlend() {}

    public static double[] blend(double x, double y, double z, double[] target, double weight) {
        double w = Math.max(0, Math.min(1, weight));
        if (w == 0) return new double[]{x, y, z};
        if (w == 1) return target.clone();
        Quaternion a = Quaternion.zyx(x, y, z);
        Quaternion b = Quaternion.zyx(target[0], target[1], target[2]);
        double dot = a.dot(b);
        if (dot < 0) { b = b.scale(-1); dot = -dot; }
        double first = 1 - w, second = w;
        if (dot < 0.9995) {
            double angle = Math.acos(Math.max(-1, Math.min(1, dot)));
            double sin = Math.sin(angle);
            first = Math.sin((1 - w) * angle) / sin;
            second = Math.sin(w * angle) / sin;
        }
        return a.scale(first).add(b.scale(second)).normalized().matrix().toZyx();
    }

    private record Quaternion(double x, double y, double z, double w) {
        static Quaternion zyx(double x, double y, double z) {
            double cx = Math.cos(x / 2), sx = Math.sin(x / 2);
            double cy = Math.cos(y / 2), sy = Math.sin(y / 2);
            double cz = Math.cos(z / 2), sz = Math.sin(z / 2);
            return new Quaternion(sx * cy * cz - cx * sy * sz, cx * sy * cz + sx * cy * sz,
                    cx * cy * sz - sx * sy * cz, cx * cy * cz + sx * sy * sz);
        }
        double dot(Quaternion b) { return x * b.x + y * b.y + z * b.z + w * b.w; }
        Quaternion scale(double s) { return new Quaternion(x * s, y * s, z * s, w * s); }
        Quaternion add(Quaternion b) { return new Quaternion(x + b.x, y + b.y, z + b.z, w + b.w); }
        Quaternion normalized() { return scale(1 / Math.sqrt(dot(this))); }
        Mat3 matrix() {
            return new Mat3(1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w),
                    2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w),
                    2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y));
        }
    }
}
