package com.steelclash.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DamageTypeTest {
    @Test
    void armorClassesFollowVanillaArmorPoints() {
        assertEquals(DamageType.ArmorClass.UNARMORED, DamageType.ArmorClass.of(0));
        assertEquals(DamageType.ArmorClass.LIGHT, DamageType.ArmorClass.of(7));   // full leather
        assertEquals(DamageType.ArmorClass.MEDIUM, DamageType.ArmorClass.of(15)); // full iron
        assertEquals(DamageType.ArmorClass.HEAVY, DamageType.ArmorClass.of(20));  // full diamond
    }

    @Test
    void bluntBeatsPlateAndCutsBeatCloth() {
        assertTrue(DamageType.BLUNT.multiplierFor(20) > DamageType.CUT.multiplierFor(20), "blunt beats heavy armour");
        assertTrue(DamageType.CUT.multiplierFor(0) > DamageType.BLUNT.multiplierFor(0), "cut beats the unarmoured");
        assertTrue(DamageType.CUT.multiplierFor(20) < 1, "cuts glance off plate");
        assertTrue(DamageType.BLUNT.multiplierFor(20) > 1);
    }

    @Test
    void namesRoundTripAndUnknownIsCut() {
        for (DamageType type : DamageType.values()) {
            assertEquals(type, DamageType.byName(type.serializedName()));
        }
        assertEquals(DamageType.CUT, DamageType.byName("nonsense"));
    }

    @Test
    void mountedChargeScalesWithSpeedAndIsCapped() {
        assertEquals(1.0, DamageType.mountedChargeMultiplier(0), 1e-9);
        assertTrue(DamageType.mountedChargeMultiplier(0.4) > 2.0, "a galloping lance hits more than twice as hard");
        assertEquals(2.5, DamageType.mountedChargeMultiplier(5), 1e-9, "capped");
    }

    @Test
    void mountedSwingsGetHalfTheChargeBonus() {
        assertEquals(1.0, DamageType.mountedSwingMultiplier(0), 1e-9);
        assertEquals(1.7, DamageType.mountedSwingMultiplier(0.4), 1e-9, "half of a galloping lance's +140%");
        assertEquals(1.75, DamageType.mountedSwingMultiplier(5), 1e-9, "capped at half the lance's cap");
    }

    @Test
    void chopsAndBluntDrainMoreStaminaFromAGuard() {
        org.junit.jupiter.api.Assertions.assertEquals(1.0, DamageType.CUT.staminaDamageMultiplier(), 1e-9);
        org.junit.jupiter.api.Assertions.assertEquals(1.10, DamageType.CHOP.staminaDamageMultiplier(), 1e-9);
        org.junit.jupiter.api.Assertions.assertEquals(1.25, DamageType.BLUNT.staminaDamageMultiplier(), 1e-9);
    }
}
