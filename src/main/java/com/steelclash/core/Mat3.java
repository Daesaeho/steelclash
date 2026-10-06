package com.steelclash.core;

/**
 * Minimal immutable 3×3 rotation matrix (row-major) so pose math can be unit tested without Minecraft. Angles are
 * radians; {@link #zyx} builds the matrix Minecraft's model parts and pose stacks use ({@code Rz · Ry · Rx}, so X acts
 * on a vertex first).
 */
public record Mat3(double m00, double m01, double m02, double m10, double m11, double m12, double m20, double m21, double m22) {
    public static final Mat3 IDENTITY = new Mat3(1, 0, 0, 0, 1, 0, 0, 0, 1);

    public static Mat3 rotX(double a) {
        double c = Math.cos(a), s = Math.sin(a);
        return new Mat3(1, 0, 0, 0, c, -s, 0, s, c);
    }

    public static Mat3 rotY(double a) {
        double c = Math.cos(a), s = Math.sin(a);
        return new Mat3(c, 0, s, 0, 1, 0, -s, 0, c);
    }

    public static Mat3 rotZ(double a) {
        double c = Math.cos(a), s = Math.sin(a);
        return new Mat3(c, -s, 0, s, c, 0, 0, 0, 1);
    }

    /** {@code Rz(z) · Ry(y) · Rx(x)}: a model part's rotation (xRot, yRot, zRot). */
    public static Mat3 zyx(double x, double y, double z) {
        return rotZ(z).mul(rotY(y)).mul(rotX(x));
    }

    public Mat3 mul(Mat3 o) {
        return new Mat3(
                m00 * o.m00 + m01 * o.m10 + m02 * o.m20, m00 * o.m01 + m01 * o.m11 + m02 * o.m21, m00 * o.m02 + m01 * o.m12 + m02 * o.m22,
                m10 * o.m00 + m11 * o.m10 + m12 * o.m20, m10 * o.m01 + m11 * o.m11 + m12 * o.m21, m10 * o.m02 + m11 * o.m12 + m12 * o.m22,
                m20 * o.m00 + m21 * o.m10 + m22 * o.m20, m20 * o.m01 + m21 * o.m11 + m22 * o.m21, m20 * o.m02 + m21 * o.m12 + m22 * o.m22);
    }

    /** The inverse, for a rotation. */
    public Mat3 transpose() {
        return new Mat3(m00, m10, m20, m01, m11, m21, m02, m12, m22);
    }

    public Vec apply(Vec v) {
        return new Vec(m00 * v.x() + m01 * v.y() + m02 * v.z(), m10 * v.x() + m11 * v.y() + m12 * v.z(),
                m20 * v.x() + m21 * v.y() + m22 * v.z());
    }

    /** Splits the matrix back into {@link #zyx} angles: {x, y, z}. */
    public double[] toZyx() {
        double cosY = Math.sqrt(m00 * m00 + m10 * m10);
        double y = Math.atan2(-m20, cosY); // better conditioned than asin near ±90°
        double x, z;
        if (cosY > 1e-9) {
            x = Math.atan2(m21, m22);
            z = Math.atan2(m10, m00);
        } else { // gimbal lock: only x - z (or x + z) is defined; put it all in x
            x = Math.atan2(-m12, m11);
            z = 0;
        }
        return new double[]{x, y, z};
    }

    /** Largest element difference, for tests. */
    public double distance(Mat3 o) {
        double[] a = {m00, m01, m02, m10, m11, m12, m20, m21, m22};
        double[] b = {o.m00, o.m01, o.m02, o.m10, o.m11, o.m12, o.m20, o.m21, o.m22};
        double d = 0;
        for (int i = 0; i < 9; i++) {
            d = Math.max(d, Math.abs(a[i] - b[i]));
        }
        return d;
    }
}
