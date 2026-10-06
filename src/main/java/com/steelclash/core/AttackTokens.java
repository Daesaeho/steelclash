package com.steelclash.core;

import java.util.HashMap;
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

    /**
     * Try to take (or keep) a token to attack {@code target}.
     *
     * @param stillActive whether a current holder is still using its token (alive, attacking this target)
     */
    public boolean acquire(int target, int attacker, int maxAttackers, IntPredicate stillActive) {
        Set<Integer> holders = holdersByTarget.computeIfAbsent(target, t -> new LinkedHashSet<>());
        holders.removeIf(id -> id != attacker && !stillActive.test(id));
        if (holders.contains(attacker)) {
            return true;
        }
        if (holders.size() < maxAttackers) {
            holders.add(attacker);
            return true;
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
            holders.remove(attacker);
            if (holders.isEmpty()) {
                holdersByTarget.remove(target);
            }
        }
    }

    /** Drops every token an attacker holds (it died, changed target, or left). */
    public void releaseAll(int attacker) {
        holdersByTarget.values().forEach(holders -> holders.remove(attacker));
        holdersByTarget.values().removeIf(Set::isEmpty);
    }

    public int holderCount(int target) {
        Set<Integer> holders = holdersByTarget.get(target);
        return holders == null ? 0 : holders.size();
    }

    public void clear() {
        holdersByTarget.clear();
    }
}
