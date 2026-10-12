package com.steelclash.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * The server lists live attacks from {@link CombatStateMachine#onAttackStart} instead of polling every fighter, so a
 * windup or release the machine reaches without announcing it would be invisible to defending bots.
 */
class LiveAttackListenerTest {
    private static final AttackTimings TIMINGS = AttackTimings.ofTicks(3, 2, 4);
    private static final AttackType[] ATTACKS = {AttackType.SLASH, AttackType.OVERHEAD, AttackType.STAB, AttackType.KICK,
            AttackType.JAB};

    @Test
    void anAttackIsAnnouncedWhenItStarts() {
        CombatStateMachine m = new CombatStateMachine();
        int[] announced = {0};
        m.onAttackStart(() -> {
            assertEquals(Phase.WINDUP, m.phase(), "listeners see the attack already winding up");
            announced[0]++;
        });
        assertTrue(m.startAttack(AttackType.SLASH, TIMINGS));
        assertEquals(1, announced[0]);
        assertFalse(m.startAttack(AttackType.STAB, TIMINGS), "busy: refused");
        assertEquals(1, announced[0], "a refused attack is not announced");
        while (m.phase() != Phase.IDLE) {
            m.tick();
        }
        assertEquals(1, announced[0], "release and recovery belong to the attack already announced");
        assertTrue(m.startAttack(AttackType.STAB, TIMINGS));
        assertEquals(2, announced[0], "the next attack is announced again");
    }

    @Test
    void aGuardOrStaggerIsNotAnAttack() {
        CombatStateMachine m = new CombatStateMachine();
        int[] announced = {0};
        m.onAttackStart(() -> announced[0]++);
        assertTrue(m.startParry(10, 5));
        m.releaseParry();
        m.stagger(5, true);
        m.cancel();
        assertEquals(0, announced[0]);
    }

    @Test
    void aSnapshotOfALiveAttackIsAnnounced() {
        for (Phase phase : Phase.values()) {
            CombatStateMachine m = new CombatStateMachine();
            int[] announced = {0};
            m.onAttackStart(() -> announced[0]++);
            m.apply(phase, AttackType.SLASH, 0, AttackTimings.TICK_US, TIMINGS, 0, false, false, false, 0, false);
            boolean live = phase == Phase.WINDUP || phase == Phase.RELEASE;
            assertEquals(live, m.isLiveAttack(), phase.toString());
            assertEquals(live ? 1 : 0, announced[0], "a snapshot in " + phase);
        }
    }

    /**
     * Keeps the list as the server does (add when announced, drop at a sweep once the attack is over) through long random
     * sequences of everything a machine can be asked to do: a live attack must always be on it.
     */
    @Test
    void noRouteIntoALiveAttackGoesUnannounced() {
        for (long seed = 1; seed <= 20; seed++) {
            Random random = new Random(seed);
            CombatStateMachine m = new CombatStateMachine();
            boolean[] listed = {false};
            m.onAttackStart(() -> listed[0] = true);
            for (int step = 0; step < 2000; step++) {
                int op = random.nextInt(20);
                AttackType type = ATTACKS[random.nextInt(ATTACKS.length)];
                switch (op) {
                    case 0, 1, 2 -> m.startAttack(type, TIMINGS, random.nextInt(3), random.nextBoolean());
                    case 3 -> m.makeHeavy(TIMINGS.windupUs() + 2 * AttackTimings.TICK_US);
                    case 4 -> m.feint();
                    case 5 -> m.morph(type, TIMINGS, random.nextInt(3), random.nextBoolean());
                    case 6 -> m.counterFeint(type, TIMINGS, random.nextInt(3), random.nextBoolean());
                    case 7 -> m.counter(1 + random.nextInt(3), random.nextInt(4));
                    case 8 -> m.forgiveIntoParry(4, 5);
                    case 9 -> m.thwack(3 * AttackTimings.TICK_US, random.nextDouble());
                    case 10 -> m.startParry(1 + random.nextInt(10), 5, random.nextInt(4));
                    case 11 -> m.cancelIntoParry(1 + random.nextInt(10), 5, random.nextInt(4));
                    case 12 -> m.releaseParry();
                    case 13 -> m.parrySucceeded(6);
                    case 14 -> m.stagger(1 + random.nextInt(6), random.nextBoolean());
                    case 15 -> m.cancel();
                    case 16 -> m.apply(Phase.byId(random.nextInt(Phase.values().length)), type, 0, AttackTimings.TICK_US,
                            TIMINGS, 0, false, false, false, 0, false);
                    case 17 -> {
                        m.allowCombo();
                        m.extendRecovery(AttackTimings.TICK_US);
                        m.startActiveParry(3);
                    }
                    default -> m.tick();
                }
                assertTrue(!m.isLiveAttack() || listed[0],
                        "seed " + seed + ", step " + step + ": " + m.phase() + " after operation " + op + " was never announced");
                if (random.nextInt(3) == 0 && !m.isLiveAttack()) {
                    listed[0] = false; // the end-of-tick sweep forgets an attack that is over
                }
            }
        }
    }
}
