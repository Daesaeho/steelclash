package com.steelclash.profile;

import com.steelclash.Config;

/**
 * The built-in jab (Chivalry 2): a quick, short thrust that interrupts a slow or greedy attack at close range for
 * little damage. Same for every weapon, can be parried, can't be feinted, made heavy or cancelled into a parry.
 */
public final class Jabs {
    private Jabs() {
    }

    /** Reach bonus is relative to the 3-block interaction range: about a 2.2 block jab. */
    public static WeaponProfile.AttackSpec spec() {
        return new WeaponProfile.AttackSpec(5, 2, 8, Config.JAB_DAMAGE_MULT.get().floatValue(),
                new WeaponProfile.ArcSpec(WeaponProfile.ArcSpec.Shape.THRUST, 1), 1, -0.8f,
                Config.JAB_STAMINA_DAMAGE.get().floatValue(), 0f);
    }
}
