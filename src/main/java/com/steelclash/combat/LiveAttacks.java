package com.steelclash.combat;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;

/**
 * The fighters whose attack is in its windup or release right now, server-wide: what a defender may have to answer.
 * <p>
 * Defenders used to ask the level for every living entity around them on every tick, although almost always nobody
 * near was attacking; with a crowd that scan was most of what combat cost. Each server-side combat state reports its
 * own attacks as they start ({@link CombatData}), {@link #sweep} forgets the ones that are over, and a defender
 * checks this short list instead.
 * <p>
 * The list holds every attack the level query would find, but not in the level's order. Where the order decides
 * something (which of two attacks gets answered), callers still ask the level. Server thread only.
 */
public final class LiveAttacks {
    private static final List<CombatData> LIVE = new ArrayList<>();
    private static long levelQueries;

    private LiveAttacks() {
    }

    /** A defender found several attacks at once here and asks the level, for its order. */
    public static void askingTheLevel() {
        levelQueries++;
    }

    /**
     * How often defenders have asked the level after all, since the server started. The list is only worth having
     * while this stays rare: tests and the benchmark watch it.
     */
    public static long levelQueries() {
        return levelQueries;
    }

    /** A server-side fighter's attack began (called by its state machine; again for an attack already listed). */
    static void started(CombatData data) {
        if (!data.liveAttackListed) {
            data.liveAttackListed = true;
            LIVE.add(data);
        }
    }

    /** End of the server tick: forget attacks that are over and fighters that have left the world. */
    static void sweep() {
        LIVE.removeIf(data -> {
            if (data.machine.isLiveAttack() && !data.owner().isRemoved()) {
                return false;
            }
            data.liveAttackListed = false;
            return true;
        });
    }

    static void clear() {
        LIVE.forEach(data -> data.liveAttackListed = false);
        LIVE.clear();
        levelQueries = 0;
    }

    public static int size() {
        return LIVE.size();
    }

    /** Whether {@code fighter}'s attack is on the list (an attack that is over must not stay on it). */
    public static boolean lists(LivingEntity fighter) {
        return fighter.hasData(ModAttachments.COMBAT) && fighter.getData(ModAttachments.COMBAT).liveAttackListed;
    }

    /** The {@code index}-th listed attack. Check its phase: an attack that just ended stays listed until the sweep. */
    public static CombatData get(int index) {
        return LIVE.get(index);
    }

    /**
     * Whether a level query for the entities within {@code radius} of {@code defender}'s hitbox would return
     * {@code attacker}: its hitbox reaches into that box, and it is in the defender's level (GameTest mock players and
     * fighters in another dimension aren't).
     */
    public static boolean inReach(LivingEntity attacker, LivingEntity defender, double radius) {
        AABB box = defender.getBoundingBox();
        return attacker.getBoundingBox().intersects(box.minX - radius, box.minY - radius, box.minZ - radius,
                box.maxX + radius, box.maxY + radius, box.maxZ + radius)
                && defender.level().getEntity(attacker.getId()) == attacker;
    }
}
