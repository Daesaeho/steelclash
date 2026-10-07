package com.steelclash.core;

/** Directional guard math. */
public final class Guard {
    private Guard() {
    }

    /**
     * Is the attacker inside the defender's frontal guard cone (horizontal plane only)?
     *
     * @param defenderYaw Minecraft yaw of the defender's view, degrees
     * @param coneDegrees full cone width, e.g. 140 means 70° to either side
     */
    public static boolean inCone(double defenderYaw, double defenderX, double defenderZ,
                                 double attackerX, double attackerZ, double coneDegrees) {
        double dx = attackerX - defenderX;
        double dz = attackerZ - defenderZ;
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1e-6) {
            return true;
        }
        Vec look = Blade.direction(defenderYaw, 0);
        double cos = (look.x() * dx + look.z() * dz) / len;
        return cos >= Math.cos(Math.toRadians(Math.min(360, coneDegrees) / 2));
    }

    /** Windup (microseconds) of the weapon the base counter window is tuned for: the sword's slash, 500 ms. */
    public static final int COUNTER_REFERENCE_WINDUP_US = 10 * AttackTimings.TICK_US;

    /**
     * Counter window against an attack with the given windup: faster weapons give a slightly smaller window, slower
     * ones a slightly bigger one (Chivalry 2: "counter windows are variable depending on weapon speed"), within
     * 70%–130% of the base.
     */
    public static int counterWindow(int baseTicks, int attackerWindupUs) {
        double scale = Math.max(0.7, Math.min(1.3, attackerWindupUs / (double) COUNTER_REFERENCE_WINDUP_US));
        return Math.max(1, (int) Math.round(baseTicks * scale));
    }
}
