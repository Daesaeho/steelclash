package com.steelclash.client.dev;

import com.steelclash.combat.Combat;
import com.steelclash.combat.ModAttachments;
import com.steelclash.core.AttackType;
import com.steelclash.core.Phase;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.Husk;
import org.jetbrains.annotations.Nullable;

/** Integrated-server actor for an opt-in live scene. Starts ordinary combat; never applies a frozen machine state. */
final class LiveOpponent {
    private static final String TAG = "steelclash_live_opponent";
    private static final boolean ENABLED = "riposte".equals(System.getProperty("steelclash.liveCapture"));
    @Nullable private static Husk opponent;
    private static boolean started;

    record Sample(int id, Phase phase, int serial, float health, boolean started) {}

    private LiveOpponent() {}

    static boolean enabled() { return ENABLED; }

    static String summonCommand() {
        return "summon minecraft:husk ~ 301 ~2 {NoAI:1b,Silent:1b,PersistenceRequired:1b,IsBaby:0b,"
                + "Tags:[\"" + TAG + "\"],Rotation:[180f,0f],HandItems:[{id:\"minecraft:iron_sword\",count:1},{}]}";
    }

    @Nullable
    static Sample tick(ServerPlayer player) {
        if (!ENABLED) return null;
        if (opponent == null) {
            var actors = player.serverLevel().getEntitiesOfClass(Husk.class, player.getBoundingBox().inflate(6),
                    actor -> actor.getTags().contains(TAG));
            if (actors.size() > 1) throw new IllegalStateException("Live opponent fixture is not unique");
            if (actors.size() == 1) opponent = actors.getFirst();
        }
        var playerMachine = player.getData(ModAttachments.COMBAT).machine;
        if (opponent == null) {
            if (playerMachine.phase() == Phase.PARRY) throw new IllegalStateException("Live opponent was not staged");
            return null;
        }
        var machine = opponent.getData(ModAttachments.COMBAT).machine;
        if (!started && playerMachine.phase() == Phase.PARRY) {
            if (player.isCreative() || player.isSpectator() || !opponent.isNoAi() || opponent.getTarget() != null)
                throw new IllegalStateException("Live opponent needs a survival player and a passive controlled actor");
            opponent.setYRot(180); opponent.setYHeadRot(180); opponent.yBodyRot = 180;
            Combat.requestAttack(opponent, AttackType.SLASH, 0, false);
            if (machine.phase() != Phase.WINDUP) throw new IllegalStateException("Live opponent attack was rejected");
            started = true;
        }
        return new Sample(opponent.getId(), machine.phase(), machine.attackSerial(), opponent.getHealth(), started);
    }
}
