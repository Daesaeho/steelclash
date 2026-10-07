package com.steelclash.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ContactPolicyTest {
    @Test
    void bluntStopsInTheFirstBodyUnlessItDiesEverythingElseCleaves() {
        assertEquals(ContactPolicy.CLEAVE_ON_KILL, ContactPolicy.defaultFor(DamageType.BLUNT));
        assertEquals(ContactPolicy.CLEAVE, ContactPolicy.defaultFor(DamageType.CUT));
        assertEquals(ContactPolicy.CLEAVE, ContactPolicy.defaultFor(DamageType.CHOP));
        assertEquals(ContactPolicy.CLEAVE, ContactPolicy.defaultFor(DamageType.PIERCE));
    }

    @Test
    void cleaveNeverStops() {
        assertFalse(ContactPolicy.CLEAVE.stops(false, false));
        assertFalse(ContactPolicy.CLEAVE.stops(true, false));
        assertFalse(ContactPolicy.CLEAVE.stops(false, true));
    }

    @Test
    void thwackStopsLightsEvenOnAKill() {
        assertTrue(ContactPolicy.THWACK.stops(false, false));
        assertTrue(ContactPolicy.THWACK.stops(false, true));
    }

    @Test
    void cleaveOnKillStopsLightsOnlyWhenTheBodyStandsUp() {
        assertTrue(ContactPolicy.CLEAVE_ON_KILL.stops(false, false));
        assertFalse(ContactPolicy.CLEAVE_ON_KILL.stops(false, true));
    }

    @Test
    void heaviesAlwaysCleave() {
        for (ContactPolicy policy : ContactPolicy.values()) {
            assertFalse(policy.stops(true, false), policy + " heavy");
            assertFalse(policy.stops(true, true), policy + " heavy kill");
        }
    }
}
