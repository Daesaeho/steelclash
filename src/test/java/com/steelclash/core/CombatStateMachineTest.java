package com.steelclash.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CombatStateMachineTest {
    private static final AttackTimings TIMINGS = new AttackTimings(3, 2, 4);

    @Test
    void startsIdle() {
        CombatStateMachine m = new CombatStateMachine();
        assertEquals(Phase.IDLE, m.phase());
        assertFalse(m.isAttacking());
        assertNull(m.tick());
    }

    @Test
    void runsThroughAllPhasesWithExactTickCounts() {
        CombatStateMachine m = new CombatStateMachine();
        assertTrue(m.startAttack(AttackType.OVERHEAD, TIMINGS));
        assertEquals(Phase.WINDUP, m.phase());
        assertEquals(AttackType.OVERHEAD, m.type());

        List<Phase> phases = new ArrayList<>();
        for (int i = 0; i < TIMINGS.total(); i++) {
            m.tick();
            phases.add(m.phase());
        }
        assertEquals(List.of(
                Phase.WINDUP, Phase.WINDUP, Phase.RELEASE,
                Phase.RELEASE, Phase.RECOVERY,
                Phase.RECOVERY, Phase.RECOVERY, Phase.RECOVERY, Phase.IDLE), phases);
    }

    @Test
    void releaseSweepsCoverZeroToOneContiguously() {
        CombatStateMachine m = new CombatStateMachine();
        m.startAttack(AttackType.SLASH, new AttackTimings(2, 4, 1));
        List<CombatStateMachine.Sweep> sweeps = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            CombatStateMachine.Sweep s = m.tick();
            if (s != null) {
                sweeps.add(s);
            }
        }
        assertEquals(4, sweeps.size());
        assertEquals(0.0, sweeps.get(0).from(), 1e-9);
        assertEquals(1.0, sweeps.get(3).to(), 1e-9);
        for (int i = 1; i < sweeps.size(); i++) {
            assertEquals(sweeps.get(i - 1).to(), sweeps.get(i).from(), 1e-9, "sweeps must not leave gaps");
        }
    }

    @Test
    void cannotStartWhileAttacking() {
        CombatStateMachine m = new CombatStateMachine();
        m.startAttack(AttackType.SLASH, TIMINGS);
        assertFalse(m.startAttack(AttackType.STAB, TIMINGS));
        assertEquals(AttackType.SLASH, m.type());
    }

    @Test
    void canAttackAgainAfterFinishing() {
        CombatStateMachine m = new CombatStateMachine();
        m.startAttack(AttackType.SLASH, TIMINGS);
        for (int i = 0; i < TIMINGS.total(); i++) {
            m.tick();
        }
        assertTrue(m.startAttack(AttackType.STAB, TIMINGS));
    }

    @Test
    void cancelReturnsToIdle() {
        CombatStateMachine m = new CombatStateMachine();
        m.startAttack(AttackType.SLASH, TIMINGS);
        m.tick();
        m.cancel();
        assertEquals(Phase.IDLE, m.phase());
        assertTrue(m.startAttack(AttackType.STAB, TIMINGS));
    }

    @Test
    void phaseProgressInterpolates() {
        CombatStateMachine m = new CombatStateMachine();
        m.startAttack(AttackType.SLASH, new AttackTimings(4, 1, 1));
        m.tick();
        assertEquals(0.25, m.phaseProgress(0f), 1e-9);
        assertEquals(0.375, m.phaseProgress(0.5f), 1e-9);
    }

    @Test
    void snapshotOverridesState() {
        CombatStateMachine m = new CombatStateMachine();
        m.apply(Phase.RELEASE, AttackType.STAB, 1, 3, new AttackTimings(5, 3, 5), 0, false, false, false, 0, false);
        CombatStateMachine.Sweep s = m.tick();
        assertNotNull(s);
        assertEquals(1 / 3.0, s.from(), 1e-9);
        assertEquals(2 / 3.0, s.to(), 1e-9);
    }

    @Test
    void parryTimesOutIntoGuardRecovery() {
        CombatStateMachine m = new CombatStateMachine();
        assertTrue(m.startParry(3, 2));
        assertEquals(Phase.PARRY, m.phase());
        m.tick();
        m.tick();
        m.tick();
        assertEquals(Phase.GUARD_RECOVERY, m.phase());
        m.tick();
        m.tick();
        assertEquals(Phase.IDLE, m.phase());
    }

    @Test
    void releasingParryLowersGuard() {
        CombatStateMachine m = new CombatStateMachine();
        m.startParry(10, 4);
        m.releaseParry();
        assertEquals(Phase.GUARD_RECOVERY, m.phase());
        assertEquals(4, m.phaseDuration());
    }

    @Test
    void successfulParryOpensRiposteWindowThatExpires() {
        CombatStateMachine m = new CombatStateMachine();
        m.startParry(10, 4);
        m.parrySucceeded(2);
        assertEquals(Phase.IDLE, m.phase());
        assertTrue(m.isRiposteReady());
        m.tick();
        assertTrue(m.isRiposteReady());
        m.tick();
        assertFalse(m.isRiposteReady());
    }

    @Test
    void attackingConsumesRiposteWindow() {
        CombatStateMachine m = new CombatStateMachine();
        m.startParry(10, 4);
        m.parrySucceeded(10);
        assertTrue(m.startAttack(AttackType.STAB, TIMINGS));
        assertEquals(0, m.riposteTicks());
    }

    @Test
    void parryCooldownBlocksImmediateReparry() {
        CombatStateMachine m = new CombatStateMachine();
        assertTrue(m.startParry(10, 2, 5));
        m.parrySucceeded(10);
        assertFalse(m.canParry(), "cooldown right after a successful parry");
        for (int i = 0; i < 4; i++) {
            m.tick();
        }
        assertFalse(m.canParry(), "4 of 5 cooldown ticks");
        m.tick();
        assertTrue(m.canParry(), "cooldown over");
    }

    @Test
    void releasedParryAlsoCoolsDownAndGuardRecoveryCantParry() {
        CombatStateMachine m = new CombatStateMachine();
        m.startParry(10, 2, 5);
        m.releaseParry();
        assertEquals(Phase.GUARD_RECOVERY, m.phase());
        assertFalse(m.canParry(), "no parrying while lowering the guard");
        m.tick();
        m.tick();
        assertEquals(Phase.IDLE, m.phase());
        assertFalse(m.canParry(), "guard is down but the cooldown still runs");
        m.tick();
        m.tick();
        m.tick();
        assertTrue(m.canParry());
    }

    @Test
    void canParryDuringRecoveryButNotWindupOrRelease() {
        CombatStateMachine m = new CombatStateMachine();
        m.startAttack(AttackType.SLASH, new AttackTimings(2, 2, 5));
        assertFalse(m.canParry(), "windup");
        m.tick();
        m.tick();
        assertEquals(Phase.RELEASE, m.phase());
        assertFalse(m.canParry(), "release");
        m.tick();
        m.tick();
        assertEquals(Phase.RECOVERY, m.phase());
        assertTrue(m.startParry(5, 2));
    }

    @Test
    void staggerBlocksAttacksAndOptionallyParries() {
        CombatStateMachine m = new CombatStateMachine();
        m.stagger(3, false);
        assertEquals(Phase.STAGGER, m.phase());
        assertFalse(m.startAttack(AttackType.SLASH, TIMINGS));
        assertFalse(m.canParry(), "guard break: no parry");
        m.stagger(3, true);
        assertTrue(m.canParry(), "parried: may parry the riposte");
        m.tick();
        m.tick();
        m.tick();
        assertEquals(Phase.IDLE, m.phase());
    }

    @Test
    void staggerInterruptsRelease() {
        CombatStateMachine m = new CombatStateMachine();
        m.startAttack(AttackType.SLASH, new AttackTimings(1, 4, 1));
        m.tick();
        assertEquals(Phase.RELEASE, m.phase());
        m.stagger(5, true);
        assertNull(m.tick(), "no more sweeps once staggered");
    }

    @Test
    void attackSerialIncrements() {
        CombatStateMachine m = new CombatStateMachine();
        int before = m.attackSerial();
        m.startAttack(AttackType.SLASH, TIMINGS);
        assertEquals(before + 1, m.attackSerial());
    }

    @Test
    void heavyExtendsWindupOnce() {
        CombatStateMachine m = new CombatStateMachine();
        m.startAttack(AttackType.SLASH, new AttackTimings(10, 4, 8));
        m.tick();
        m.tick();
        assertTrue(m.makeHeavy(16));
        assertTrue(m.isHeavy());
        assertEquals(16, m.phaseDuration());
        assertFalse(m.makeHeavy(30), "only once");
        for (int i = 0; i < 13; i++) {
            m.tick();
        }
        assertEquals(Phase.WINDUP, m.phase(), "15 of 16 ticks");
        m.tick();
        assertEquals(Phase.RELEASE, m.phase());
        assertFalse(m.makeHeavy(40), "not after windup");
    }

    @Test
    void feintOnlyDuringWindup() {
        CombatStateMachine m = new CombatStateMachine();
        assertFalse(m.feint());
        m.startAttack(AttackType.SLASH, new AttackTimings(3, 2, 2));
        assertTrue(m.feint());
        assertEquals(Phase.IDLE, m.phase());
        m.startAttack(AttackType.SLASH, new AttackTimings(1, 2, 2));
        m.tick();
        assertEquals(Phase.RELEASE, m.phase());
        assertFalse(m.feint(), "too late once released");
    }

    @Test
    void morphSwitchesTypeOnceAndRestartsWindup() {
        CombatStateMachine m = new CombatStateMachine();
        m.startAttack(AttackType.SLASH, new AttackTimings(10, 4, 8));
        m.tick();
        m.tick();
        assertFalse(m.morph(AttackType.SLASH, TIMINGS), "same type is not a morph");
        assertTrue(m.morph(AttackType.STAB, new AttackTimings(7, 3, 6)));
        assertEquals(AttackType.STAB, m.type());
        assertEquals(0, m.phaseTick());
        assertEquals(7, m.phaseDuration());
        assertTrue(m.isMorphed());
        assertFalse(m.morph(AttackType.OVERHEAD, TIMINGS), "one morph per swing");
    }

    @Test
    void comboSkipsRecoveryOnlyAfterLandedHit() {
        CombatStateMachine m = new CombatStateMachine();
        m.startAttack(AttackType.SLASH, new AttackTimings(1, 1, 10));
        m.tick();
        m.tick();
        assertEquals(Phase.RECOVERY, m.phase());
        assertFalse(m.startAttack(AttackType.STAB, TIMINGS), "whiffed: must sit out recovery");
        m.allowCombo();
        assertTrue(m.startAttack(AttackType.STAB, TIMINGS), "landed: combo");
        assertEquals(Phase.WINDUP, m.phase());
        assertFalse(m.isComboAllowed(), "combo flag resets with the new attack");
    }

    @Test
    void counterShortensWindup() {
        CombatStateMachine m = new CombatStateMachine();
        m.startAttack(AttackType.SLASH, new AttackTimings(12, 4, 8));
        m.tick();
        assertTrue(m.counter(3));
        assertEquals(4, m.phaseDuration());
        m.tick();
        m.tick();
        m.tick();
        assertEquals(Phase.RELEASE, m.phase());
    }

    @Test
    void variantAndSideAreKeptAndMorphCanChangeThem() {
        CombatStateMachine m = new CombatStateMachine();
        m.startAttack(AttackType.SLASH, TIMINGS, 2, true);
        assertEquals(2, m.variant());
        assertTrue(m.isMirrored());
        m.morph(AttackType.STAB, TIMINGS, 1, false);
        assertEquals(1, m.variant());
        assertFalse(m.isMirrored());
    }
}
