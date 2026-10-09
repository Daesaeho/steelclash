package com.steelclash.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class TimingBarTest {
    private static final int HOLD = 3;
    private static final int RIPOSTE = 10;

    private static TimingBar bar(CombatStateMachine m, boolean holding) {
        return TimingBar.of(m, 0f, holding, HOLD, RIPOSTE);
    }

    @Test
    void idleShowsNothing() {
        assertNull(bar(new CombatStateMachine(), false));
    }

    @Test
    void holdingChargesTowardHeavy() {
        CombatStateMachine m = new CombatStateMachine();
        m.startAttack(AttackType.SLASH, AttackTimings.ofTicks(10, 4, 8));
        m.tick();
        TimingBar b = bar(m, true);
        assertNotNull(b);
        assertEquals(TimingBar.Kind.HEAVY_CHARGE, b.kind());
        assertEquals(1 / 3.0, b.fraction(), 1e-6);
        assertEquals(1.0, b.marker(), 1e-6);
    }

    @Test
    void releasedEarlyShowsALightWindup() {
        CombatStateMachine m = new CombatStateMachine();
        m.startAttack(AttackType.SLASH, AttackTimings.ofTicks(10, 4, 8));
        m.tick();
        TimingBar b = bar(m, false);
        assertEquals(TimingBar.Kind.WINDUP, b.kind());
        assertEquals(0.1, b.fraction(), 1e-6);
    }

    @Test
    void heavyWindupHasItsOwnKind() {
        CombatStateMachine m = new CombatStateMachine();
        m.startAttack(AttackType.SLASH, AttackTimings.ofTicks(10, 4, 8));
        m.makeHeavy(16);
        assertEquals(TimingBar.Kind.HEAVY_WINDUP, bar(m, true).kind(), "once heavy, the charge is done");
    }

    @Test
    void nonweaponRecoveryDrainsWithoutAdvertisingACombo() {
        CombatStateMachine m = new CombatStateMachine();
        m.startAttack(AttackType.KICK, AttackTimings.ofTicks(1, 1, 4)); // a kick's recovery can't be comboed
        m.tick();
        m.tick();
        assertEquals(Phase.RECOVERY, m.phase());
        TimingBar b = bar(m, false);
        assertEquals(TimingBar.Kind.RECOVERY, b.kind());
        assertEquals(1.0, b.fraction(), 1e-6, "full at the start of recovery, draining");
        m.allowCombo();
        assertEquals(TimingBar.Kind.RECOVERY, bar(m, false).kind(), "landing a kick does not unlock a combo");
    }

    @Test
    void aWeaponThwackAdvertisesTheComboWindow() {
        CombatStateMachine m = new CombatStateMachine();
        m.startAttack(AttackType.SLASH, AttackTimings.ofTicks(1, 2, 4));
        m.tick();
        m.thwack(4 * (int) AttackTimings.TICK_US, 0.5);
        m.allowCombo();
        assertEquals(TimingBar.Kind.COMBO, bar(m, false).kind());
    }

    @Test
    void parryDrainsAndRiposteShowsWhatsLeft() {
        CombatStateMachine m = new CombatStateMachine();
        m.startParry(10, 5);
        m.tick();
        TimingBar parry = bar(m, false);
        assertEquals(TimingBar.Kind.PARRY, parry.kind());
        assertEquals(0.9, parry.fraction(), 1e-6);
        m.parrySucceeded(RIPOSTE);
        m.tick();
        TimingBar riposte = bar(m, false);
        assertEquals(TimingBar.Kind.RIPOSTE, riposte.kind());
        assertEquals(0.9, riposte.fraction(), 1e-6);
    }

    @Test
    void kicksNeverChargeHeavy() {
        CombatStateMachine m = new CombatStateMachine();
        m.startAttack(AttackType.KICK, AttackTimings.ofTicks(5, 3, 10));
        assertEquals(TimingBar.Kind.WINDUP, bar(m, true).kind());
    }
}
