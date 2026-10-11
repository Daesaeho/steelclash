package com.steelclash.core;

/** Maps the canonical physical item rotation to the channels consumed by a rendering backend. */
public enum ItemRotationConvention {
    /** PAL swaps the item bone's Y and Z rotation axes. */
    PAL_SWAPPED {
        @Override public double[] encode(double x, double y, double z) { return new double[]{-x, -z, -y}; }
        @Override public double[] decode(double x, double y, double z) { return new double[]{-x, -z, -y}; }
    },
    /** PAL More Rotation restores ordinary item axes. */
    STANDARD {
        @Override public double[] encode(double x, double y, double z) { return new double[]{-x, -y, -z}; }
        @Override public double[] decode(double x, double y, double z) { return new double[]{-x, -y, -z}; }
    };

    /** Convert physical canonical XYZ angles into the backend's item-bone channels. */
    public abstract double[] encode(double x, double y, double z);

    /** Convert backend item-bone channels into physical canonical XYZ angles. */
    public abstract double[] decode(double x, double y, double z);
}
