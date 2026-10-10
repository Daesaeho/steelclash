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
    private static final String RUN_TAG = TAG + "_" + java.util.UUID.randomUUID();
    private static final String SCENE = System.getProperty("steelclash.liveCapture", "");
    private static final boolean INTERRUPT = SCENE.startsWith("interrupt-");
    private static final boolean ENABLED = INTERRUPT || java.util.List.of("riposte", "counter", "hitstop").contains(SCENE);
    @Nullable private static Husk opponent;
    private static boolean started;
    @Nullable private static Phase hitPhase;
    private static float hitDamage;

    record Sample(int id, Phase phase, long elapsedUs, long durationUs, int serial, float health, boolean started,
                  @Nullable Phase hitPhase, float hitDamage) {}

    private LiveOpponent() {}

    static boolean enabled() { return ENABLED; }

    static String summonCommand() {
        return "summon minecraft:husk ~ 301 ~" + (INTERRUPT ? "1.2" : "2") + " {NoAI:1b,Silent:1b,PersistenceRequired:1b,IsBaby:0b,"
                + "Tags:[\"" + TAG + "\",\"" + RUN_TAG + "\"],Rotation:[180f,0f],HandItems:[{id:\"minecraft:iron_sword\",count:1},{}]}";
    }

    @Nullable
    static Sample tick(ServerPlayer player, boolean captureActive) {
        if (!ENABLED || !captureActive) return null;
        if (opponent == null) {
            var actors = player.serverLevel().getEntitiesOfClass(Husk.class, player.getBoundingBox().inflate(6),
                    actor -> actor.isAlive() && actor.getTags().contains(RUN_TAG));
            if (actors.size() > 1) throw new IllegalStateException("Live opponent fixture is not unique");
            if (actors.size() == 1) opponent = actors.getFirst();
        }
        var playerMachine = player.getData(ModAttachments.COMBAT).machine;
        if (opponent == null) {
            if (playerMachine.phase() == Phase.PARRY) throw new IllegalStateException("Live opponent was not staged");
            return null;
        }
        var machine = opponent.getData(ModAttachments.COMBAT).machine;
        boolean trigger = INTERRUPT
                ? playerMachine.phase() == Phase.WINDUP && (SCENE.equals("interrupt-windup")
                    || playerMachine.phaseElapsedUs() >= playerMachine.phaseDurationUs() - 300_000)
                : playerMachine.phase() == Phase.PARRY;
        if (!SCENE.equals("hitstop") && !started && trigger) {
            if (player.isCreative() || player.isSpectator() || !opponent.isNoAi() || opponent.getTarget() != null)
                throw new IllegalStateException("Live opponent needs a survival player and a passive controlled actor");
            opponent.setYRot(180); opponent.setYHeadRot(180); opponent.yBodyRot = 180;
            Combat.requestAttack(opponent, INTERRUPT ? AttackType.JAB : AttackType.SLASH, 0, false);
            if (machine.phase() != Phase.WINDUP) throw new IllegalStateException("Live opponent attack was rejected");
            started = true;
        }
        return new Sample(opponent.getId(), machine.phase(), machine.phaseElapsedUs(), machine.phaseDurationUs(),
                machine.attackSerial(), opponent.getHealth(), started, hitPhase, hitDamage);
    }

    /** Called before the ordinary flinch listener; retain the phase in which a real damaging contact landed. */
    static void damaged(net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Post event) {
        if (INTERRUPT && event.getEntity() instanceof ServerPlayer player
                && event.getSource().getEntity() == opponent && hitPhase == null && event.getNewDamage() > 0) {
            hitPhase = player.getData(ModAttachments.COMBAT).machine.phase();
            hitDamage = event.getNewDamage();
        }
    }
}
