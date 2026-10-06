package com.steelclash.combat;

import com.steelclash.Config;
import com.steelclash.core.AttackType;
import com.steelclash.core.CombatStateMachine;
import com.steelclash.core.LagMath;
import com.steelclash.core.Phase;
import com.steelclash.core.PositionHistory;
import com.steelclash.core.Vec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Server-side latency compensation (see {@link LagMath}). Attackers: targets are traced where the lagged attacker saw
 * them. Defenders: a hit on a lagged player is held until their parry, block or counter has had time to arrive.
 */
public final class LagCompensation {
    /** Ticks of position history kept per entity: enough for the largest configurable rewind. */
    public static final int HISTORY_TICKS = 24;

    private record Held(LivingEntity attacker, LivingEntity target, Combat.Hit hit, long dueAt) {
    }

    private static final List<Held> HELD = new ArrayList<>();
    /** Latency overrides for GameTests (mock players and mobs have no connection). */
    private static final Map<UUID, Integer> FORCED_LATENCY = new HashMap<>();

    private LagCompensation() {
    }

    public static boolean enabled() {
        return Config.LAG_COMPENSATION.get();
    }

    /** Round-trip latency in ms: a player's measured ping, 0 for mobs. */
    public static int latencyOf(Entity entity) {
        Integer forced = FORCED_LATENCY.get(entity.getUUID());
        if (forced != null) {
            return forced;
        }
        return entity instanceof ServerPlayer player && player.connection != null ? player.connection.latency() : 0;
    }

    public static void forceLatency(Entity entity, int latencyMs) {
        FORCED_LATENCY.put(entity.getUUID(), latencyMs);
    }

    // ---------------------------------------------------------------- rewind (attacker side)

    /** Called every server tick for every living entity. */
    public static void record(LivingEntity entity) {
        if (enabled()) {
            entity.getData(ModAttachments.POSITION_HISTORY).record(entity.level().getGameTime(), entity.getX(), entity.getY(), entity.getZ());
        }
    }

    /** How many ticks back this attacker's view of the world is. */
    public static int rewindTicks(LivingEntity attacker) {
        if (!enabled()) {
            return 0;
        }
        return LagMath.rewindTicks(latencyOf(attacker), Config.INTERPOLATION_TICKS.get(), Config.MAX_REWIND_MS.get());
    }

    /** Where {@code target} was {@code ticks} ago, relative to where it is now (zero without history). */
    public static Vec3 rewindOffset(LivingEntity target, int ticks) {
        if (ticks <= 0 || !target.hasData(ModAttachments.POSITION_HISTORY)) {
            return Vec3.ZERO;
        }
        Vec then = target.getData(ModAttachments.POSITION_HISTORY).at(target.level().getGameTime() - ticks);
        return then == null ? Vec3.ZERO : new Vec3(then.x() - target.getX(), then.y() - target.getY(), then.z() - target.getZ());
    }

    // ---------------------------------------------------------------- grace (defender side)

    /**
     * Holds a hit on a lagged defender who isn't defending yet.
     *
     * @return true if the hit was held and will be delivered later by {@link #tick()}
     */
    public static boolean hold(LivingEntity attacker, LivingEntity target, Combat.Hit hit) {
        if (!enabled() || hit.type() == AttackType.KICK || readyToDefend(target, hit.type())) {
            return false;
        }
        int grace = LagMath.graceTicks(latencyOf(target), Config.MAX_PARRY_GRACE_MS.get());
        if (grace <= 0) {
            return false;
        }
        HELD.add(new Held(attacker, target, hit, target.level().getGameTime() + grace));
        return true;
    }

    /** The defender has a parry or shield up, or is winding up a matching counter: resolve the hit now. */
    private static boolean readyToDefend(LivingEntity target, AttackType type) {
        if (target.isBlocking()) {
            return true;
        }
        if (!target.hasData(ModAttachments.COMBAT)) {
            return false;
        }
        CombatStateMachine machine = target.getData(ModAttachments.COMBAT).machine;
        return machine.phase() == Phase.PARRY || machine.phase() == Phase.WINDUP && machine.type() == type;
    }

    /** End of server tick: deliver held hits whose grace ran out or whose defender is now defending. */
    public static void tick() {
        if (HELD.isEmpty()) {
            return;
        }
        List<Held> ready = new ArrayList<>();
        HELD.removeIf(held -> {
            if (held.attacker().isRemoved() || held.target().isRemoved() || !held.target().isAlive()) {
                return true;
            }
            if (held.target().level().getGameTime() >= held.dueAt() || readyToDefend(held.target(), held.hit().type())) {
                ready.add(held);
                return true;
            }
            return false;
        });
        for (Held held : ready) {
            Combat.deliverHeld(held.attacker(), held.target(), held.hit());
        }
    }

    public static int heldCount() {
        return HELD.size();
    }

    public static void clear() {
        HELD.clear();
        FORCED_LATENCY.clear();
    }
}
