package com.steelclash.core;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumMap;
import java.util.Map;

/**
 * What a bot has learned about its current opponent, by watching the opponent's combat state each tick:
 * which attacks they favour, how often they feint, and how long they've been turtling behind a guard.
 */
public final class OpponentMemory {
    private static final int HISTORY = 4;
    /** A habit: this many of the last {@link #HISTORY} attacks were the same type. */
    private static final int HABIT = 3;
    /** Feint suspicion decays by one every this many ticks. */
    private static final int FEINT_DECAY_TICKS = 200;

    private final Deque<AttackType> recentAttacks = new ArrayDeque<>();
    private int opponentId = -1;
    private Phase lastPhase = Phase.IDLE;
    private int lastSerial = -1;
    private int feints;
    private int ticksSinceFeintDecay;
    private int guardTicks;

    /** Forget everything when the opponent changes. */
    public void track(int opponentId) {
        if (this.opponentId != opponentId) {
            this.opponentId = opponentId;
            recentAttacks.clear();
            lastPhase = Phase.IDLE;
            lastSerial = -1;
            feints = 0;
            guardTicks = 0;
        }
    }

    /**
     * Observe the opponent for one tick.
     *
     * @param guarding the opponent is holding a weapon parry or a shield up
     */
    public void observe(Phase phase, AttackType type, int attackSerial, boolean guarding) {
        if (phase == Phase.RELEASE && lastPhase == Phase.WINDUP && type != AttackType.KICK) {
            recentAttacks.addLast(type);
            while (recentAttacks.size() > HISTORY) {
                recentAttacks.removeFirst();
            }
        }
        // A windup that ended in anything but a release (and isn't a fresh attack) was feinted or parry-cancelled.
        if (lastPhase == Phase.WINDUP && phase != Phase.WINDUP && phase != Phase.RELEASE && phase != Phase.STAGGER
                && attackSerial == lastSerial) {
            feints++;
        }
        lastPhase = phase;
        lastSerial = attackSerial;
        guardTicks = guarding ? guardTicks + 1 : 0;
        if (++ticksSinceFeintDecay >= FEINT_DECAY_TICKS) {
            ticksSinceFeintDecay = 0;
            feints = Math.max(0, feints - 1);
        }
    }

    /** The attack type the opponent keeps using, if they have such a habit. */
    public AttackType habit() {
        Map<AttackType, Integer> counts = new EnumMap<>(AttackType.class);
        for (AttackType t : recentAttacks) {
            counts.merge(t, 1, Integer::sum);
        }
        return counts.entrySet().stream().filter(e -> e.getValue() >= HABIT).map(Map.Entry::getKey).findFirst().orElse(null);
    }

    /** The opponent feints often enough that parrying early is a trap. */
    public boolean isFeinter() {
        return feints >= 2;
    }

    public int feints() {
        return feints;
    }

    public int guardTicks() {
        return guardTicks;
    }
}
