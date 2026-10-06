package com.steelclash.core;

import java.util.Locale;

/**
 * Chivalry 2-style damage types versus armour weight: cuts shred the unarmoured but glance off plate, blunt weapons
 * are made for heavy armour, chops sit in between, piercing finds the gaps. Multipliers apply on top of vanilla's own
 * armour reduction.
 */
public enum DamageType {
    //            unarmoured, light, medium, heavy
    CUT(new double[]{1.20, 1.10, 0.90, 0.70}),
    CHOP(new double[]{1.10, 1.05, 1.00, 0.90}),
    BLUNT(new double[]{0.90, 0.95, 1.05, 1.25}),
    PIERCE(new double[]{1.00, 1.00, 1.05, 1.10});

    /** Armour weight classes by the target's total armour points (vanilla: full iron = 15, full diamond = 20). */
    public enum ArmorClass {
        UNARMORED, LIGHT, MEDIUM, HEAVY;

        public static ArmorClass of(double armorPoints) {
            if (armorPoints < 4) {
                return UNARMORED;
            }
            if (armorPoints < 10) {
                return LIGHT;
            }
            if (armorPoints < 16) {
                return MEDIUM;
            }
            return HEAVY;
        }
    }

    private final double[] multipliers;

    DamageType(double[] multipliers) {
        this.multipliers = multipliers;
    }

    public double multiplier(ArmorClass armor) {
        return multipliers[armor.ordinal()];
    }

    public double multiplierFor(double armorPoints) {
        return multiplier(ArmorClass.of(armorPoints));
    }

    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static DamageType byName(String name) {
        for (DamageType type : values()) {
            if (type.serializedName().equals(name.toLowerCase(Locale.ROOT))) {
                return type;
            }
        }
        return CUT;
    }

    /**
     * Couched-lance style bonus for a mounted stab: grows with the mount's horizontal speed (blocks per tick), capped.
     * A walking horse (~0.1) adds little; a galloping one (~0.4+) more than doubles the hit.
     */
    public static double mountedChargeMultiplier(double mountSpeedBlocksPerTick) {
        return Math.min(2.5, 1.0 + Math.max(0, mountSpeedBlocksPerTick) * 3.5);
    }
}
