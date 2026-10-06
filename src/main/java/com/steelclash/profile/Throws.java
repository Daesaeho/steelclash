package com.steelclash.profile;

/** The built-in throw: same for every weapon. The weapon leaves the hand at the start of the release. */
public final class Throws {
    private static final WeaponProfile.AttackSpec SPEC = new WeaponProfile.AttackSpec(7, 2, 10, 1f,
            new WeaponProfile.ArcSpec(WeaponProfile.ArcSpec.Shape.KICK, 1), 1, 0f, 0f, 0f);

    private Throws() {
    }

    public static WeaponProfile.AttackSpec spec() {
        return SPEC;
    }
}
