package com.steelclash.core;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Blend displayed player bones across weapon/leg rig changes, without modifying simulation or release poses. */
public final class RigPoseBlend {
    public static final long DURATION_NANOS = 200_000_000L;
    public record Bone(double rx, double ry, double rz, double x, double y, double z, double bend) {
        Bone blend(Bone next, double f, boolean item) {
            // PAL item channels represent hand-frame {x,y,z} as {-x,-z,-y}; interpolate in that physical frame.
            double[] r = item ? RotationBlend.blend(-rx, -rz, -ry, new double[]{-next.rx, -next.rz, -next.ry}, f)
                    : RotationBlend.blend(rx, ry, rz, new double[]{next.rx, next.ry, next.rz}, f);
            if (item) r = new double[]{-r[0], -r[2], -r[1]};
            return new Bone(r[0], r[1], r[2], x+(next.x-x)*f, y+(next.y-y)*f, z+(next.z-z)*f, bend+(next.bend-bend)*f);
        }
    }
    private final Map<String, Bone> displayed = new HashMap<>();
    private Map<String, Bone> from = Map.of();
    private long frame = -1, changedAt;
    private Object identity;
    private boolean initialized, legRig;
    private double fraction = 1;

    public void begin(long nextFrame, long now, boolean nextLegRig, boolean exactRelease, Object nextIdentity) {
        if (frame == nextFrame) {
            if (exactRelease) { from = Map.of(); fraction = 1; legRig = nextLegRig; }
            return;
        }
        if (frame >= 0 && nextFrame > frame + 1) reset();
        if (!Objects.equals(identity, nextIdentity)) reset();
        if (initialized && legRig != nextLegRig) {
            from = Map.copyOf(displayed);
            changedAt = now;
        }
        frame = nextFrame; identity = nextIdentity; legRig = nextLegRig; initialized = true;
        if (exactRelease) from = Map.of(); // a weapon release always follows the current hit arc
        double t = Math.max(0, Math.min(1, (now-changedAt)/(double)DURATION_NANOS));
        fraction = from.isEmpty() ? 1 : t*t*(3-2*t);
        if (t == 1) from = Map.of();
    }

    public Bone apply(String name, Bone current) {
        Bone previous = from.get(name);
        Bone result = previous == null ? current : previous.blend(current, fraction, name.endsWith("_item"));
        displayed.put(name, result);
        return result;
    }
    public boolean blending() { return !from.isEmpty() && fraction < 1; }

    public void reset() { displayed.clear(); from = Map.of(); frame = -1; identity = null; initialized = false; fraction = 1; }
}
