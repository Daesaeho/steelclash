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
}
