package com.steelclash.core;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.IntPredicate;

/**
 * Chivalry 2-style crowd control for bots: only a limited number of attackers may be swinging at the same target at
 * once; the rest hold back and wait for a token. Entities are identified by int ids.
 */
public final class AttackTokens {
    private final Map<Integer, Set<Integer>> holdersByTarget = new HashMap<>();
    /** Reverse index: cleanup visits only the targets this attacker actually holds, not every fight on the server. */
    private final Map<Integer, Set<Integer>> targetsByAttacker = new HashMap<>();

    /**
     * Try to take (or keep) a token to attack {@code target}.
     *
     * @param stillActive whether a current holder is still using its token (alive, attacking this target)
     */
    public boolean acquire(int target, int attacker, int maxAttackers, IntPredicate stillActive) {
        Set<Integer> holders = holdersByTarget.computeIfAbsent(target, t -> new LinkedHashSet<>());
        holders.removeIf(id -> {
            if (id != attacker && !stillActive.test(id)) {
                forgetTarget(id, target);
                return true;
            }
            return false;
        });
        if (holders.contains(attacker)) {
            return true;
        }
        if (holders.size() < maxAttackers) {
            holders.add(attacker);
            targetsByAttacker.computeIfAbsent(attacker, id -> new HashSet<>()).add(target);
            return true;
        }
        if (holders.isEmpty()) {
            holdersByTarget.remove(target);
        }
        return false;
    }

    public boolean holds(int target, int attacker) {
        Set<Integer> holders = holdersByTarget.get(target);
        return holders != null && holders.contains(attacker);
    }

    public void release(int target, int attacker) {
        Set<Integer> holders = holdersByTarget.get(target);
        if (holders != null) {
            if (holders.remove(attacker)) {
                forgetTarget(attacker, target);
            }
            if (holders.isEmpty()) {
                holdersByTarget.remove(target);
            }
        }
    }

    /** Drops every token an attacker holds (it died, changed target, or left). */
    public void releaseAll(int attacker) {
        Set<Integer> targets = targetsByAttacker.remove(attacker);
        if (targets == null) {
            return;
        }
        for (int target : targets) {
            Set<Integer> holders = holdersByTarget.get(target);
            holders.remove(attacker);
            if (holders.isEmpty()) {
                holdersByTarget.remove(target);
            }
        }
    }

    /** Drops a departed target's whole bucket, and all tokens the departed entity held as an attacker. */
    public void removeEntity(int entity) {
        releaseAll(entity);
        Set<Integer> holders = holdersByTarget.remove(entity);
        if (holders != null) {
            for (int attacker : holders) {
                forgetTarget(attacker, entity);
            }
        }
    }

    private void forgetTarget(int attacker, int target) {
        Set<Integer> targets = targetsByAttacker.get(attacker);
        if (targets != null) {
            targets.remove(target);
            if (targets.isEmpty()) {
                targetsByAttacker.remove(attacker);
            }
        }
    }

    public int holderCount(int target) {
        Set<Integer> holders = holdersByTarget.get(target);
        return holders == null ? 0 : holders.size();
    }

    public void clear() {
        holdersByTarget.clear();
        targetsByAttacker.clear();
    }
}
