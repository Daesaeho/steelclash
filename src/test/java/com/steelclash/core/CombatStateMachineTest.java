package com.steelclash.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CombatStateMachineTest {
    private static final AttackTimings TIMINGS = AttackTimings.ofTicks(3, 2, 4);

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
        m.startAttack(AttackType.SLASH, AttackTimings.ofTicks(2, 4, 1));
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
        m.startAttack(AttackType.SLASH, AttackTimings.ofTicks(4, 1, 1));
        m.tick();
        assertEquals(0.25, m.phaseProgress(0f), 1e-9);
        assertEquals(0.375, m.phaseProgress(0.5f), 1e-9);
    }

    @Test
    void snapshotOverridesState() {
        CombatStateMachine m = new CombatStateMachine();
        m.apply(Phase.RELEASE, AttackType.STAB, AttackTimings.TICK_US, 3L * AttackTimings.TICK_US, AttackTimings.ofTicks(5, 3, 5), 0, false, false, false, 0, false);
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
        assertEquals(Phase.PARRY, m.phase(), "the guard stays up after catching a hit");
        assertTrue(m.isRiposteReady());
        m.tick();
        assertTrue(m.isRiposteReady());
        m.tick();
        assertFalse(m.isRiposteReady());
    }

    @Test
    void parryCatchesSeveralHitsAndExtendsNearItsEnd() {
        CombatStateMachine m = new CombatStateMachine();
        m.startParry(6, 4);
        for (int i = 0; i < 5; i++) {
            m.tick();
        }
        m.parrySucceeded(10);
        assertEquals(Phase.PARRY, m.phase());
        assertTrue(m.ticksLeftInPhase() >= 4, "a late catch keeps the guard up for a follow-up");
        m.parrySucceeded(10);
        assertEquals(2, m.parriedHits());
    }

    @Test
    void ripostingStraightOutOfTheGuard() {
        CombatStateMachine m = new CombatStateMachine();
        m.startParry(12, 4, 5);
        m.parrySucceeded(10);
        assertTrue(m.startAttack(AttackType.SLASH, TIMINGS), "riposte out of the guard");
        assertEquals(Phase.WINDUP, m.phase());
        assertTrue(m.isFromGuard());
        assertEquals(5, m.parryCooldownLeft(), "leaving the parry starts its cooldown");
    }

    @Test
    void attackingFromAnEmptyGuardIsACounterAttempt() {
        CombatStateMachine m = new CombatStateMachine();
        m.startParry(12, 4, 5);
        assertTrue(m.startAttack(AttackType.SLASH, TIMINGS), "a counter attempt drops the guard");
        assertFalse(m.isRiposteReady());
        assertTrue(m.isFromGuard());
    }

    @Test
    void forgivenessOnlyRightAfterAnAttackFromTheGuard() {
        CombatStateMachine m = new CombatStateMachine();
        m.startAttack(AttackType.SLASH, TIMINGS);
        assertFalse(m.forgiveIntoParry(2, 4), "an attack from neutral isn't forgiven");
        m.cancel();
        m.startParry(12, 4, 0);
        m.startAttack(AttackType.OVERHEAD, TIMINGS);
        m.tick();
        m.tick();
        m.tick();
        assertFalse(m.forgiveIntoParry(2, 4), "three ticks in is too late");
        m.cancel();
        m.startParry(12, 4, 0);
        m.startAttack(AttackType.OVERHEAD, TIMINGS);
        m.tick();
        assertTrue(m.forgiveIntoParry(2, 4));
        assertEquals(Phase.PARRY, m.phase());
    }

    @Test
    void activeParryLastsWhileAttackingAndCanBeExtended() {
        CombatStateMachine m = new CombatStateMachine();
        m.startAttack(AttackType.SLASH, AttackTimings.ofTicks(10, 5, 5));
        m.startActiveParry(3);
        assertTrue(m.isActiveParry());
        m.tick();
        m.extendActiveParry(2);
        for (int i = 0; i < 3; i++) {
            m.tick();
        }
        assertTrue(m.isActiveParry(), "3 + 2 ticks, 4 gone");
        m.tick();
        assertFalse(m.isActiveParry());
        m.startActiveParry(5);
        m.stagger(4, true);
        assertFalse(m.isActiveParry(), "a stagger ends it");
    }

    @Test
    void parryThatCaughtSomethingDropsWithoutGuardRecovery() {
        CombatStateMachine m = new CombatStateMachine();
        m.startParry(3, 6);
        m.parrySucceeded(10);
        for (int i = 0; i < 4; i++) {
            m.tick();
        }
        assertEquals(Phase.IDLE, m.phase(), "no guard-recovery penalty after a successful parry");
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
        m.releaseParry();
        assertFalse(m.canParry(), "cooldown right after a successful parry ends");
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
        m.startAttack(AttackType.SLASH, AttackTimings.ofTicks(2, 2, 5));
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
        m.startAttack(AttackType.SLASH, AttackTimings.ofTicks(1, 4, 1));
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
        m.startAttack(AttackType.SLASH, AttackTimings.ofTicks(10, 4, 8));
        m.tick();
        m.tick();
        assertTrue(m.makeHeavy(16 * AttackTimings.TICK_US));
        assertTrue(m.isHeavy());
        assertEquals(16, m.phaseDuration());
        assertFalse(m.makeHeavy(30 * AttackTimings.TICK_US), "only once");
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
        m.startAttack(AttackType.SLASH, AttackTimings.ofTicks(3, 2, 2));
        assertTrue(m.feint());
        assertEquals(Phase.IDLE, m.phase());
        m.startAttack(AttackType.SLASH, AttackTimings.ofTicks(1, 2, 2));
        m.tick();
        assertEquals(Phase.RELEASE, m.phase());
        assertFalse(m.feint(), "too late once released");
    }

    @Test
    void morphSwitchesTypeOnceAndRestartsWindup() {
        CombatStateMachine m = new CombatStateMachine();
        m.startAttack(AttackType.SLASH, AttackTimings.ofTicks(10, 4, 8));
        m.tick();
        m.tick();
        assertFalse(m.morph(AttackType.SLASH, TIMINGS), "same type is not a morph");
        assertTrue(m.morph(AttackType.STAB, AttackTimings.ofTicks(7, 3, 6)));
        assertEquals(AttackType.STAB, m.type());
        assertEquals(0, m.phaseTick());
        assertEquals(7, m.phaseDuration());
        assertTrue(m.isMorphed());
        assertFalse(m.morph(AttackType.OVERHEAD, TIMINGS), "one morph per swing");
    }

    @Test
    void comboSkipsRecoveryAfterAnUnblockedAttack() {
        CombatStateMachine m = new CombatStateMachine();
        m.startAttack(AttackType.SLASH, AttackTimings.ofTicks(1, 1, 10));
        m.tick();
        m.tick();
        assertEquals(Phase.RECOVERY, m.phase());
        assertTrue(m.isComboAllowed(), "Chivalry 2: even a whiff can be comboed");
        assertTrue(m.startAttack(AttackType.STAB, TIMINGS), "combo");
        assertEquals(Phase.WINDUP, m.phase());
        assertFalse(m.isComboAllowed(), "combo flag resets with the new attack");
    }

    @Test
    void kicksAndBlockedAttacksDontCombo() {
        CombatStateMachine m = new CombatStateMachine();
        m.startAttack(AttackType.KICK, AttackTimings.ofTicks(1, 1, 10));
        m.tick();
        m.tick();
        assertEquals(Phase.RECOVERY, m.phase());
        assertFalse(m.isComboAllowed());
        assertFalse(m.startAttack(AttackType.STAB, TIMINGS), "a kick's recovery must be sat out");

        CombatStateMachine blocked = new CombatStateMachine();
        blocked.startAttack(AttackType.SLASH, AttackTimings.ofTicks(1, 3, 10));
        blocked.tick();
        blocked.stagger(5, false);
        assertFalse(blocked.isComboAllowed(), "a blocked attack is staggered, not comboed");
        assertFalse(blocked.startAttack(AttackType.STAB, TIMINGS));
    }

    @Test
    void counterShortensWindup() {
        CombatStateMachine m = new CombatStateMachine();
        m.startAttack(AttackType.SLASH, AttackTimings.ofTicks(12, 4, 8));
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

    // ---- sub-tick timeline (architecture plan section 5)

    @Test
    void wholeTickTimingsBehaveExactlyAsBefore() {
        CombatStateMachine m = new CombatStateMachine();
        m.startAttack(AttackType.SLASH, AttackTimings.ofTicks(3, 2, 4));
        assertNull(m.tick());
        assertNull(m.tick());
        assertNull(m.tick(), "the windup ends exactly on the third tick boundary: no sweep that tick");
        assertEquals(Phase.RELEASE, m.phase());
        CombatStateMachine.Sweep first = m.tick();
        assertEquals(0, first.from(), 1e-9);
        assertEquals(0.5, first.to(), 1e-9);
    }

    @Test
    void releaseStartsInsideTheTickWhenTheWindupEndsMidTick() {
        CombatStateMachine m = new CombatStateMachine();
        // 120 ms windup: 2 ticks + 20 ms; 100 ms release.
        m.startAttack(AttackType.SLASH, AttackTimings.ofMillis(120, 100, 200));
        assertNull(m.tick());
        assertNull(m.tick());
        CombatStateMachine.Sweep s = m.tick();
        assertNotNull(s, "30 ms of this tick are already release");
        assertEquals(Phase.RELEASE, m.phase());
        assertEquals(0, s.from(), 1e-9);
        assertEquals(0.3, s.to(), 1e-9);
        assertEquals(30_000, m.phaseElapsedUs());
        assertEquals(0, m.phaseTick(), "30 ms in: no whole tick yet");
        assertEquals(2, m.ticksLeftInPhase(), "70 ms left: 2 ticks, rounded up");
    }

    @Test
    void distinctMillisecondTimingsStayDistinct() {
        // 350 vs 370 ms: whole ticks would round both to 7.
        assertNotEquals(releaseTick(350), releaseTick(380), "380 ms releases a tick later than 350 ms");
        CombatStateMachine a = new CombatStateMachine();
        CombatStateMachine b = new CombatStateMachine();
        a.startAttack(AttackType.SLASH, AttackTimings.ofMillis(350, 100, 100));
        b.startAttack(AttackType.SLASH, AttackTimings.ofMillis(370, 100, 100));
        for (int i = 0; i < 7; i++) {
            a.tick();
            b.tick();
        }
        assertEquals(Phase.RELEASE, a.phase());
        assertEquals(Phase.WINDUP, b.phase(), "370 ms is still winding up after 350 ms");
        b.tick();
        assertEquals(Phase.RELEASE, b.phase());
        assertEquals(30_000, b.phaseElapsedUs(), "and is 30 ms into its release at the end of tick 8");
    }

    private static int releaseTick(int windupMs) {
        CombatStateMachine m = new CombatStateMachine();
        m.startAttack(AttackType.SLASH, AttackTimings.ofMillis(windupMs, 100, 100));
        int tick = 0;
        while (m.phase() == Phase.WINDUP) {
            m.tick();
            tick++;
        }
        return tick;
    }

    @Test
    void aReleaseShorterThanATickIsSweptWholeInOneTick() {
        CombatStateMachine m = new CombatStateMachine();
        m.startAttack(AttackType.STAB, AttackTimings.ofMillis(60, 20, 100));
        assertNull(m.tick(), "first 50 ms: windup");
        CombatStateMachine.Sweep s = m.tick();
        assertNotNull(s);
        assertEquals(0, s.from(), 1e-9);
        assertEquals(1, s.to(), 1e-9, "the whole 20 ms release fits in this tick");
        assertEquals(Phase.RECOVERY, m.phase());
        assertEquals(20_000, m.phaseElapsedUs(), "10 ms windup + 20 ms release + 20 ms of recovery");
    }

    @Test
    void sweepsCoverTheWholeReleaseExactlyOnce() {
        for (int releaseMs : new int[]{20, 70, 100, 135, 333}) {
            CombatStateMachine m = new CombatStateMachine();
            m.startAttack(AttackType.SLASH, AttackTimings.ofMillis(77, releaseMs, 100));
            double covered = 0;
            double last = 0;
            for (int i = 0; i < 40 && m.isAttacking(); i++) {
                CombatStateMachine.Sweep s = m.tick();
                if (s != null) {
                    assertEquals(last, s.from(), 1e-9, "contiguous, release " + releaseMs);
                    covered += s.to() - s.from();
                    last = s.to();
                }
            }
            assertEquals(1, covered, 1e-9, "release " + releaseMs + " ms covered once");
        }
    }

    @Test
    void progressIsInterpolatedInMicroseconds() {
        CombatStateMachine m = new CombatStateMachine();
        m.startAttack(AttackType.SLASH, AttackTimings.ofMillis(200, 100, 100));
        m.tick();
        assertEquals(0.25, m.phaseProgress(0), 1e-9);
        assertEquals(0.375, m.phaseProgress(0.5f), 1e-9);
        assertEquals(3, m.ticksLeftInPhase(), "150 ms left: 3 ticks");
    }

    // ---- thwack (architecture plan section 18)

    /** 100 ms windup (exactly two ticks), then the given release and recovery; ticked into the release's first tick. */
    private static CombatStateMachine inFirstReleaseTick(int releaseMs, int recoveryMs) {
        CombatStateMachine m = new CombatStateMachine();
        m.startAttack(AttackType.SLASH, AttackTimings.ofMillis(100, releaseMs, recoveryMs));
        m.tick();
        m.tick();
        m.tick();
        return m;
    }

    @Test
    void thwackSkipsTheRestOfTheReleaseAndTimesTheRecoveryFromContact() {
        CombatStateMachine m = inFirstReleaseTick(200, 300);
        assertEquals(Phase.RELEASE, m.phase());
        assertEquals(50_000, m.phaseElapsedUs());
        assertTrue(m.thwack(250_000, 0.1), "contact 20 ms into the release");
        assertEquals(Phase.RECOVERY, m.phase());
        assertTrue(m.isThwacked());
        assertEquals(0.1, m.recoverFrom(), 1e-9);
        assertEquals(250_000, m.phaseDurationUs());
        assertEquals(30_000, m.phaseElapsedUs(), "the 30 ms since the contact count toward the thwack recovery");
    }

    @Test
    void thwackWorksAfterTheReleaseRanOutInTheSameTick() {
        // 30 ms release: by the end of the tick the release is over and recovery is 20 ms in.
        CombatStateMachine m = inFirstReleaseTick(30, 300);
        assertEquals(Phase.RECOVERY, m.phase());
        assertEquals(20_000, m.phaseElapsedUs());
        assertTrue(m.thwack(100_000, 0.5));
        assertEquals(100_000, m.phaseDurationUs());
        assertEquals(35_000, m.phaseElapsedUs(), "15 ms of release after the contact + 20 ms of recovery");
    }

    @Test
    void aThwackShorterThanTheTimeSinceContactIsAlreadyOver() {
        CombatStateMachine m = inFirstReleaseTick(200, 300);
        assertTrue(m.thwack(10_000, 0.1));
        assertEquals(Phase.IDLE, m.phase());
    }

    @Test
    void thwackOnlyOncePerAttackAndOnlyFromTheSwing() {
        CombatStateMachine m = new CombatStateMachine();
        assertFalse(m.thwack(100_000, 0.5), "idle");
        m.startAttack(AttackType.SLASH, TIMINGS);
        assertFalse(m.thwack(100_000, 0.5), "windup: nothing to stop yet");
        m.tick();
        m.tick();
        m.tick();
        m.tick();
        assertEquals(Phase.RELEASE, m.phase());
        assertTrue(m.thwack(200_000, 0.4));
        assertFalse(m.thwack(200_000, 0.6), "once per attack");
    }

    @Test
    void aNewAttackClearsTheThwack() {
        CombatStateMachine m = inFirstReleaseTick(200, 300);
        m.thwack(250_000, 0.1);
        m.allowCombo();
        assertTrue(m.startAttack(AttackType.OVERHEAD, TIMINGS), "combo out of the thwack");
        assertFalse(m.isThwacked());
        assertEquals(1, m.recoverFrom(), 1e-9);
    }

    @Test
    void thwackStateTravelsWithTheSnapshot() {
        CombatStateMachine m = new CombatStateMachine();
        m.apply(Phase.RECOVERY, AttackType.SLASH, 0, 200_000, TIMINGS, 0, false, false, false, 0, false, true, 0.4);
        assertTrue(m.isThwacked());
        assertEquals(0.4, m.recoverFrom(), 1e-9);
        m.apply(Phase.RECOVERY, AttackType.SLASH, 0, 200_000, TIMINGS, 0, false, false, false, 0, false);
        assertFalse(m.isThwacked());
        assertEquals(1, m.recoverFrom(), 1e-9);
    }

    @Test
    void rejectedParryCancelPreservesTheAttack() {
        CombatStateMachine m = new CombatStateMachine();
        assertTrue(m.startParry(20, 8, 5));
        assertTrue(m.startAttack(AttackType.SLASH, TIMINGS)); // leaving the guard starts its cooldown
        m.tick();
        long elapsed = m.phaseElapsedUs();
        assertFalse(m.cancelIntoParry(20, 8, 5));
        assertEquals(Phase.WINDUP, m.phase());
        assertEquals(elapsed, m.phaseElapsedUs());
        assertEquals(4, m.parryCooldownLeft());
    }

    @Test
    void parryCancelStartsAfterTheCooldown() {
        CombatStateMachine m = new CombatStateMachine();
        m.startParry(20, 8, 2);
        m.startAttack(AttackType.SLASH, AttackTimings.ofTicks(10, 5, 5));
        m.tick();
        m.tick();
        assertTrue(m.cancelIntoParry(20, 8, 2));
        assertEquals(Phase.PARRY, m.phase());
    }

    private static CombatStateMachine copySnapshot(CombatStateMachine source) {
        CombatStateMachine copy = new CombatStateMachine();
        copy.apply(source.phase(), source.type(), source.phaseElapsedUs(), source.phaseDurationUs(), source.timings(),
                source.riposteTicks(), source.isHeavy(), source.isMorphed(), source.isComboAllowed(), source.variant(),
                source.isMirrored(), source.isThwacked(), source.recoverFrom());
        copy.applyPredictionState(source.predictionState());
        return copy;
    }

    @Test
    void caughtParrySnapshotReleasesToIdleWithTheServerCooldown() {
        CombatStateMachine server = new CombatStateMachine();
        server.startParry(20, 8, 5);
        server.parrySucceeded(10);
        CombatStateMachine client = copySnapshot(server);
        server.releaseParry();
        client.releaseParry();
        assertEquals(Phase.IDLE, client.phase());
        assertEquals(server.parryCooldownLeft(), client.parryCooldownLeft());
        assertEquals(server.isRiposteReady(), client.isRiposteReady());
    }

    @Test
    void snapshotPreservesGuardBreakAndForgivenessRules() {
        CombatStateMachine server = new CombatStateMachine();
        server.stagger(8, false);
        assertFalse(copySnapshot(server).canParry(), "a guard-break correction cannot be parried out of");
        server.cancel();
        server.startParry(20, 8, 5);
        server.startAttack(AttackType.SLASH, TIMINGS);
        CombatStateMachine client = copySnapshot(server);
        assertTrue(client.forgiveIntoParry(2, 8));
        assertTrue(server.forgiveIntoParry(2, 8));
        assertEquals(server.predictionState(), client.predictionState());
    }

    @Test
    void snapshotPreservesActiveParryAndUsedCounterFeint() {
        CombatStateMachine server = new CombatStateMachine();
        server.startAttack(AttackType.SLASH, TIMINGS);
        server.counterFeint(AttackType.STAB, TIMINGS, 1, true);
        server.counter(2, 5);
        CombatStateMachine client = copySnapshot(server);
        assertTrue(client.isActiveParry());
        assertFalse(client.counterFeint(AttackType.SLASH, TIMINGS, 0, false));
        assertEquals(server.attackSerial(), client.attackSerial());
    }

    // ---- counter-feint (architecture plan section 13.1)

    @Test
    void counterFeintIsAllowedOnceEvenAfterAMorph() {
        CombatStateMachine m = new CombatStateMachine();
        m.startAttack(AttackType.SLASH, TIMINGS);
        assertTrue(m.morph(AttackType.OVERHEAD, TIMINGS));
        assertFalse(m.morph(AttackType.STAB, TIMINGS), "a plain morph only once");
        m.tick();
        assertTrue(m.counterFeint(AttackType.STAB, TIMINGS, 0, false));
        assertEquals(AttackType.STAB, m.type());
        assertEquals(Phase.WINDUP, m.phase());
        assertEquals(0, m.phaseElapsedUs(), "the windup starts over");
        assertTrue(m.isCounterFeinted());
        assertFalse(m.counterFeint(AttackType.SLASH, TIMINGS, 0, false), "only one counter-feint");
    }

    @Test
    void counterFeintCanSwitchToTheOtherSideOfTheSameAttack() {
        CombatStateMachine m = new CombatStateMachine();
        m.startAttack(AttackType.SLASH, TIMINGS, 0, false);
        assertFalse(m.counterFeint(AttackType.SLASH, TIMINGS, 0, false), "same attack, same side: nothing to change");
        assertTrue(m.counterFeint(AttackType.SLASH, TIMINGS, 0, true));
        assertTrue(m.isMirrored());
    }

    @Test
    void counterFeintOnlyDuringTheWindupAndResetsWithTheNextAttack() {
        CombatStateMachine m = new CombatStateMachine();
        assertFalse(m.counterFeint(AttackType.STAB, TIMINGS, 0, false), "idle");
        m.startAttack(AttackType.SLASH, TIMINGS);
        for (int i = 0; i < 3; i++) {
            m.tick();
        }
        assertEquals(Phase.RELEASE, m.phase());
        assertFalse(m.counterFeint(AttackType.STAB, TIMINGS, 0, false), "too late: the blade is coming");
        CombatStateMachine n = new CombatStateMachine();
        n.startAttack(AttackType.SLASH, TIMINGS);
        n.counterFeint(AttackType.STAB, TIMINGS, 0, false);
        n.cancel();
        n.startAttack(AttackType.SLASH, TIMINGS);
        assertFalse(n.isCounterFeinted());
        assertTrue(n.counterFeint(AttackType.STAB, TIMINGS, 0, false), "a new attack gets its own counter-feint");
    }

    @Test
    void aCounterThatCaughtItsAttackIsCommittedUntilTheNextAttack() {
        CombatStateMachine m = new CombatStateMachine();
        m.startAttack(AttackType.SLASH, TIMINGS);
        assertFalse(m.isCounterCommitted(), "an ordinary windup can still be dodged out of");
        assertTrue(m.counter(2, 5));
        assertTrue(m.isCounterCommitted());
        CombatStateMachine client = new CombatStateMachine();
        client.applyPredictionState(m.predictionState());
        assertTrue(client.predictionState().countered(), "the client learns the counter is committed too");
        m.tick();
        m.tick();
        assertEquals(Phase.RELEASE, m.phase());
        assertFalse(m.isCounterCommitted(), "only the windup is committed");
        m.cancel();
        m.startAttack(AttackType.SLASH, TIMINGS);
        assertFalse(m.isCounterCommitted(), "a new attack starts uncommitted");
    }

    @Test
    void dodgeCannotCancelJabsKicksOrHeavyWindups() {
        CombatStateMachine m = new CombatStateMachine();
        assertFalse(m.canDodgeWindup(), "idle is not a windup");
        for (AttackType type : new AttackType[]{AttackType.JAB, AttackType.KICK}) {
            assertTrue(m.startAttack(type, TIMINGS));
            assertFalse(m.canDodgeWindup(), type + " is committed");
            m.cancel();
        }
        assertTrue(m.startAttack(AttackType.SLASH, TIMINGS));
        assertTrue(m.canDodgeWindup(), "an ordinary light windup can be abandoned");
        assertTrue(m.makeHeavy(TIMINGS.windupUs() + AttackTimings.TICK_US));
        assertFalse(m.canDodgeWindup(), "upgrading to heavy commits the windup");
        m.cancel();
        assertTrue(m.startAttack(AttackType.STAB, TIMINGS));
        assertTrue(m.canDodgeWindup(), "commitment resets with the next attack");
        assertTrue(m.counter(2, 5));
        assertFalse(m.canDodgeWindup(), "a successful counter is committed");
    }

    @Test
    void dodgeWindupPermissionEndsAtRelease() {
        CombatStateMachine m = new CombatStateMachine();
        m.startAttack(AttackType.SLASH, TIMINGS);
        for (int tick = 0; tick < TIMINGS.windup(); tick++) {
            assertTrue(m.canDodgeWindup());
            m.tick();
        }
        assertEquals(Phase.RELEASE, m.phase());
        assertFalse(m.canDodgeWindup());
        while (m.isAttacking()) {
            m.tick();
            assertFalse(m.canDodgeWindup(), "release/recovery never allow windup cancellation");
        }
    }

    @Test
    void extraRecoveryOnlyLengthensARecovery() {
        CombatStateMachine m = new CombatStateMachine();
        m.startAttack(AttackType.STAB, TIMINGS);
        assertFalse(m.extendRecovery(AttackTimings.TICK_US), "not during a windup");
        while (m.phase() != Phase.RECOVERY) {
            m.tick();
        }
        assertTrue(m.extendRecovery(2L * AttackTimings.TICK_US));
        int ticks = 0;
        while (m.phase() == Phase.RECOVERY) {
            m.tick();
            ticks++;
        }
        assertEquals(TIMINGS.recovery() + 2, ticks, "two ticks longer");
    }
}
