package com.steelclash.core;

/** Minimal immutable 3D vector so the core stays free of Minecraft classes. */
public record Vec(double x, double y, double z) {
    public static final Vec ZERO = new Vec(0, 0, 0);

    public Vec add(Vec o) {
        return new Vec(x + o.x, y + o.y, z + o.z);
    }

    public Vec subtract(Vec o) {
        return new Vec(x - o.x, y - o.y, z - o.z);
    }

    public Vec scale(double s) {
        return new Vec(x * s, y * s, z * s);
    }

    public double dot(Vec o) {
        return x * o.x + y * o.y + z * o.z;
    }

    public double length() {
        return Math.sqrt(dot(this));
    }

    public Vec lerp(Vec to, double t) {
        return new Vec(x + (to.x - x) * t, y + (to.y - y) * t, z + (to.z - z) * t);
    }
}
