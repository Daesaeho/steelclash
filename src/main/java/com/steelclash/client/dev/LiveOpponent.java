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
    private static final boolean MOB = SCENE.startsWith("mob-");
    private static final boolean TRACKING = SCENE.startsWith("mob-track-");
    private static final boolean ENABLED = INTERRUPT || MOB || java.util.List.of("riposte", "counter", "hitstop").contains(SCENE);
    @Nullable private static Husk opponent;
    private static boolean started;
    @Nullable private static Phase hitPhase;
    private static float hitDamage;
    private static boolean followup;
    private static boolean away, returned, forcedBefore, restored;
    private static int attackTick, returnTick;
    @Nullable private static net.minecraft.world.level.ChunkPos farChunk;
    @Nullable private static net.minecraft.world.phys.Vec3 home;

    record Sample(int id, Phase phase, long elapsedUs, long durationUs, int serial, float health, boolean started,
                  @Nullable Phase hitPhase, float hitDamage, AttackType type, boolean followup, boolean away, boolean returned,
                  double x, double y, double z, int entityTick) {}

    private LiveOpponent() {}

    static boolean enabled() { return ENABLED; }

    static String summonCommand() {
        return "summon minecraft:husk ~ 301 ~" + (MOB ? "4" : INTERRUPT ? "1.2" : "2") + " {NoAI:1b,Silent:1b,PersistenceRequired:1b,IsBaby:0b,"
                + "Tags:[\"" + TAG + "\",\"" + RUN_TAG + "\"],Rotation:[180f,0f],HandItems:[{id:\"minecraft:iron_sword\",count:1},{}]}";
    }

    @Nullable
    static Sample tick(ServerPlayer player, boolean captureActive, int captureTick) {
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
        if (TRACKING) {
            if (farChunk == null) {
                home = player.position();
                farChunk = opponent.chunkPosition();
                forcedBefore = player.serverLevel().getForcedChunks().contains(farChunk.toLong());
                player.serverLevel().setChunkForced(farChunk.x, farChunk.z, true);
            }
            if (!away && captureTick >= 10) {
                player.setNoGravity(true);
                player.connection.teleport(home.x + 256, home.y, home.z, 0, 0);
                player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
                player.fallDistance = 0;
                away = true;
            }
            if (started && !returned && captureTick >= attackTick + 1) {
                player.connection.teleport(home.x, home.y, home.z, 0, 0);
                player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
                player.setNoGravity(false);
                player.fallDistance = 0;
                returned = true;
                returnTick = captureTick;
            }
            if (returned && !restored && captureTick >= returnTick + 4) {
                player.serverLevel().setChunkForced(farChunk.x, farChunk.z, forcedBefore);
                restored = true;
            }
        }
        boolean trigger = MOB ? captureTick>=20 : INTERRUPT
                ? playerMachine.phase() == Phase.WINDUP && (SCENE.equals("interrupt-windup")
                    || playerMachine.phaseElapsedUs() >= playerMachine.phaseDurationUs() - 300_000)
                : playerMachine.phase() == Phase.PARRY;
        if (!SCENE.equals("hitstop") && !started && trigger
                && (!TRACKING || player.serverLevel().isPositionEntityTicking(opponent.blockPosition()))) {
            if (player.isCreative() || player.isSpectator() || !opponent.isNoAi() || opponent.getTarget() != null)
                throw new IllegalStateException("Live opponent needs a survival player and a passive controlled actor");
            opponent.setYRot(180); opponent.setYHeadRot(180); opponent.yBodyRot = 180;
            if (SCENE.equals("mob-track-guard")) Combat.startParry(opponent, opponent.getData(ModAttachments.COMBAT));
            else Combat.requestAttack(opponent, INTERRUPT ? AttackType.JAB : AttackType.SLASH, 0, false);
            if (SCENE.equals("mob-track-attack")) Combat.requestHeavy(opponent);
            if (machine.phase() != (SCENE.equals("mob-track-guard") ? Phase.PARRY : Phase.WINDUP))
                throw new IllegalStateException("Live opponent action was rejected");
            started = true;
            attackTick = captureTick;
        }
        if ((SCENE.equals("mob-morph") || SCENE.equals("mob-kick")) && !followup && machine.phase()==Phase.WINDUP
                && machine.phaseElapsedUs()>=Math.min(150_000,machine.phaseDurationUs()/3)) {
            AttackType type=SCENE.equals("mob-kick") ? AttackType.KICK : AttackType.STAB;
            Combat.requestAttack(opponent,type,0,false);
            if (machine.type()!=type) throw new IllegalStateException("Mob replacement was rejected");
            followup=true;
        }
        return new Sample(opponent.getId(), machine.phase(), machine.phaseElapsedUs(), machine.phaseDurationUs(),
                machine.attackSerial(), opponent.getHealth(), started, hitPhase, hitDamage, machine.type(), followup, away, returned,
                opponent.getX(), opponent.getY(), opponent.getZ(), opponent.tickCount);
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
