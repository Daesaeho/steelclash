package com.steelclash.core;

import java.util.Map;

/** Resolves the legacy hip counter-turn or an explicit authored override, including cross-policy handovers. */
public final class PoseChannels {
    private static final double[] ZERO = {0,0,0};
    private PoseChannels() {}

    public static double[] value(Map<String,double[]> offsets, String channel) {
        double[] explicit = offsets.get(channel);
        if (explicit != null) return explicit;
        return channel.equals("hipLag") ? new double[]{0, offsets.getOrDefault("body",ZERO)[1] * .5, 0} : ZERO;
    }

    public static float hipLagRadians(Map<String,double[]> offsets, double weight) {
        double[] authored = offsets.get("hipLag");
        if (authored != null) return (float)Math.toRadians(authored[1] * weight);
        // Preserve the old float operations exactly when this clip has no override.
        return .5f * (float)Math.toRadians(offsets.getOrDefault("body",ZERO)[1] * weight);
    }
}
