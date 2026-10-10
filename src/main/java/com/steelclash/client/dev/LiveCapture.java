package com.steelclash.client.dev;

import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.platform.InputConstants;
import com.steelclash.SteelClash;
import com.steelclash.client.ClientInput;
import com.steelclash.client.anim.CombatPose;
import com.steelclash.combat.ModAttachments;
import com.steelclash.core.Phase;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Opt-in live input/render diagnostic. Uses normal keyboard bindings, prediction and server packets;
 * never applies a frozen combat state. Staging changes a world: use a disposable copy with cheats.
 */
@EventBusSubscriber(modid = SteelClash.MOD_ID, value = Dist.CLIENT)
public final class LiveCapture {
    private static final String SCENE = System.getProperty("steelclash.liveCapture");
    private static final KeyMapping[] KEYS = {ClientInput.SLASH_RIGHT_TO_LEFT, ClientInput.STAB, ClientInput.FEINT, ClientInput.PARRY};
    private static final InputConstants.Key[] OLD_KEYS = new InputConstants.Key[KEYS.length];
    private static final List<Map<String, Object>> FRAMES = new ArrayList<>();
    private static final List<Map<String, Object>> INPUTS = new ArrayList<>();
    private static final AtomicInteger SAVED = new AtomicInteger();
    private static int ticks, captureTick, frames, releaseAt;
    private static boolean initialized, active, done, followup, modelSeen, readySeen;
    private static boolean clientAttack, serverAttack, clientHeavy, serverHeavy;
    private static boolean clientRelease, serverRelease, clientMorph, serverMorph, clientGuard, serverGuard;
    private static boolean clientCaught, serverCaught, clientRiposte, serverRiposte, opponentSeen;
    private static int opponentRenderFrames;
    private static float initialPlayerHealth, initialOpponentHealth = Float.NaN, minOpponentHealth = Float.POSITIVE_INFINITY;
    private static final Map<String, Object> CONDITIONS = new LinkedHashMap<>();
    private static boolean draining;
    private static int drainTicks;
    private static int firstSerial, maxClientSerial, maxServerSerial, firstServerSerial;
    private static int previousFps;
    private static boolean previousHideGui, previousPause;
    private static CameraType previousView;
    private static long startNanos;
    private static Path out;
    private static volatile UUID playerId;
    @Nullable private static CombatPose renderedPose;
    private record ServerSample(long tick, Phase phase, int serial, boolean heavy, boolean morphed,
                                int parriedHits, boolean activeParry, float health, @Nullable LiveOpponent.Sample opponent) {}
    private static volatile ServerSample server;

    private LiveCapture() {}

    public static void recordRenderedPose(AbstractClientPlayer player, @Nullable CombatPose pose, boolean ready) {
        if (active && player == Minecraft.getInstance().player) {
            modelSeen = true;
            renderedPose = pose;
            readySeen = ready;
        }
    }

    @SubscribeEvent
    static void tick(ClientTickEvent.Pre event) throws IOException {
        if (SCENE == null || done) return;
        Minecraft mc = Minecraft.getInstance();
        if (draining) {
            if (SAVED.get() == frames) {
                for (int i = 0; i < frames; i++) {
                    Path file = out.resolve("screenshots").resolve(String.format(java.util.Locale.ROOT, "frame_%04d.png", i));
                    if (!Files.isRegularFile(file) || Files.size(file) == 0)
                        throw new IllegalStateException("Live screenshot missing: " + file);
                }
                done = true;
                SteelClash.LOGGER.info("Live capture completed: {} actual frames in {}", frames, out);
                mc.stop();
            } else if (++drainTicks > 200) {
                throw new IllegalStateException("Live screenshots did not finish: " + SAVED.get() + "/" + frames);
            }
            return;
        }
        var player = mc.player;
        if (player == null || mc.getOverlay() != null) return;
        if (!initialized) {
            if (PoseSheet.running()) throw new IllegalStateException("Live capture cannot run with frozen pose sheets");
            if (!List.of("attack", "combo", "heavy", "feint", "morph", "parry", "riposte").contains(SCENE))
                throw new IllegalArgumentException("Unknown live capture scene: " + SCENE);
            String item = item("steelclash.liveCaptureItem", "minecraft:iron_sword");
            String offhand = item("steelclash.liveCaptureOffhand", "minecraft:air");
            out = Path.of(System.getProperty("steelclash.liveCaptureOut", new java.io.File(mc.gameDirectory,
                    "steelclash-live/" + System.currentTimeMillis()).getPath()));
            if (Files.exists(out.resolve("capture.json")) || Files.exists(out.resolve("screenshots")))
                throw new IllegalStateException("Live capture output must be fresh: " + out);
            Files.createDirectories(out);
            previousFps = mc.options.framerateLimit().get();
            previousHideGui = mc.options.hideGui;
            previousPause = mc.options.pauseOnLostFocus;
            previousView = mc.options.getCameraType();
            mc.options.pauseOnLostFocus = false;
            mc.options.framerateLimit().set(20);
            // F1 hides vanilla's first-person hands too; cancel GUI layers separately instead.
            mc.options.hideGui = false;
            mc.options.setCameraType(CameraType.valueOf(System.getProperty("steelclash.liveCaptureView", "FIRST_PERSON")));
            for (int i = 0; i < KEYS.length; i++) {
                OLD_KEYS[i] = KEYS[i].getKey();
                KEYS[i].setKey(InputConstants.Type.KEYSYM.getOrCreate(321 + i)); // numpad 1..4
            }
            KeyMapping.resetMapping();
            playerId = player.getUUID();
            for (String command : new String[]{"gamerule doDaylightCycle false", "gamerule doWeatherCycle false",
                    "gamerule doMobLoot false", "gamerule sendCommandFeedback false", "time set noon", "weather clear",
                    "effect clear @s", "fill ~-3 300 ~-3 ~3 300 ~3 minecraft:polished_andesite",
                    "tp @s ~ 301 ~ 0 0", "kill @e[type=!player,distance=..12]",
                    "item replace entity @s weapon.mainhand with " + item,
                    "item replace entity @s weapon.offhand with " + offhand}) player.connection.sendCommand(command);
            if (LiveOpponent.enabled()) {
                player.connection.sendCommand("gamemode survival @s");
                player.connection.sendCommand("effect give @s minecraft:instant_health 1 10 true");
                player.connection.sendCommand(LiveOpponent.summonCommand());
            }
            initialized = true;
        }
        if (++ticks < 100) return;
        mc.setScreen(null);
        mc.mouseHandler.grabMouse();
        if (!mc.mouseHandler.isMouseGrabbed()) {
            if (ticks > 1300) throw new IllegalStateException("Live capture needs an active game window for normal input");
            return;
        }
        player.setXRot(0);
        player.setYRot(0);
        player.yBodyRot = player.yBodyRotO = 0;
        var machine = player.getData(ModAttachments.COMBAT).machine;
        if (!active) {
            if (!BuiltInRegistries.ITEM.getKey(player.getMainHandItem().getItem()).toString()
                    .equals(item("steelclash.liveCaptureItem", "minecraft:iron_sword"))
                    || !BuiltInRegistries.ITEM.getKey(player.getOffhandItem().getItem()).toString()
                    .equals(item("steelclash.liveCaptureOffhand", "minecraft:air")))
                throw new IllegalStateException("Live capture equipment was not applied by the server");
            player.getRandom().setSeed(61);
            firstSerial = machine.attackSerial();
            firstServerSerial = server == null ? 0 : server.serial;
            initialPlayerHealth = player.getHealth();
            startNanos = System.nanoTime();
            active = true;
            CONDITIONS.put("view", mc.options.getCameraType()); CONDITIONS.put("arm", player.getMainArm());
            CONDITIONS.put("item", BuiltInRegistries.ITEM.getKey(player.getMainHandItem().getItem()).toString());
            CONDITIONS.put("offhand", BuiltInRegistries.ITEM.getKey(player.getOffhandItem().getItem()).toString());
            CONDITIONS.put("skin", player.getSkin().model() + ":" + player.getSkin().texture());
            CONDITIONS.put("width", mc.getWindow().getWidth()); CONDITIONS.put("height", mc.getWindow().getHeight());
            CONDITIONS.put("fov", mc.options.fov().get()); CONDITIONS.put("fps_limit", mc.options.framerateLimit().get());
            CONDITIONS.put("camera_motion", com.steelclash.Config.Client.CAMERA_MOTION.get());
            List<String> armor = new ArrayList<>();
            player.getArmorSlots().forEach(stack -> armor.add(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()));
            CONDITIONS.put("armor", armor);
            CONDITIONS.put("ready_stance", com.steelclash.Config.Client.FIRST_PERSON_READY_STANCE.get());
            CONDITIONS.put("game_mode", mc.gameMode == null ? null : mc.gameMode.getPlayerMode());
            CONDITIONS.put("initial_player_health", initialPlayerHealth);
            String renderer = sodiumRenderer();
            CONDITIONS.put("sodium_renderer", renderer);
            String expected = System.getProperty("steelclash.liveCaptureRenderer", "any");
            if (!List.of("any", "sodium", "vanilla").contains(expected))
                throw new IllegalArgumentException("Unknown expected live renderer: " + expected);
            if ((expected.equals("sodium") && !renderer.equals("active"))
                    || (expected.equals("vanilla") && !renderer.equals("absent")))
                throw new IllegalStateException("Live renderer mismatch: expected=" + expected + ", sodium=" + renderer);
            CONDITIONS.put("expected_renderer", expected);
            SteelClash.LOGGER.info("Live capture starting: scene={} view={} arm={} main={} offhand={} out={}", SCENE,
                    mc.options.getCameraType(), player.getMainArm(), player.getMainHandItem(), player.getOffhandItem(), out);
        }
        captureTick++;
        if (captureTick == 20) {
            boolean guard = SCENE.equals("parry") || SCENE.equals("riposte");
            press(guard ? 3 : 0);
            releaseAt = captureTick + (SCENE.equals("heavy") ? 9 : guard ? 40 : 1);
        }
        if (captureTick == releaseAt) release();
        if (!followup && captureTick > 20) {
            if (SCENE.equals("riposte") && machine.parriedHits() > 0 && machine.isRiposteReady()) {
                release(); press(0); followup = true; releaseAt = captureTick + 1;
            } else if (SCENE.equals("combo") && machine.phase() == Phase.RELEASE && machine.phaseTick() >= 2) {
                press(1); followup = true; releaseAt = captureTick + 1;
            } else if ((SCENE.equals("feint") || SCENE.equals("morph"))
                    && machine.phase() == Phase.WINDUP && machine.phaseTick() >= 3) {
                press(SCENE.equals("feint") ? 2 : 1); followup = true; releaseAt = captureTick + 1;
            }
        }
        clientAttack |= machine.phase().isAttack();
        clientHeavy |= machine.isHeavy();
        clientRelease |= machine.phase() == Phase.RELEASE;
        clientMorph |= machine.isMorphed();
        clientGuard |= machine.phase() == Phase.PARRY;
        clientCaught |= machine.parriedHits() > 0;
        clientRiposte |= SCENE.equals("riposte") && machine.isActiveParry() && machine.phase().isAttack();
        maxClientSerial = Math.max(maxClientSerial, machine.attackSerial());
        if (captureTick >= 140) finish(mc);
    }

    private static String item(String property, String fallback) {
        var id = ResourceLocation.parse(System.getProperty(property, fallback));
        if (!BuiltInRegistries.ITEM.containsKey(id)) throw new IllegalArgumentException("Unknown live item: " + id);
        return id.toString();
    }

    /** Optional diagnostic: discovery of a distribution jar alone does not prove its renderer was initialized. */
    private static String sodiumRenderer() {
        try {
            Class<?> renderer = Class.forName("net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer");
            return renderer.getMethod("instanceNullable").invoke(null) != null ? "active" : "present_without_renderer";
        } catch (ClassNotFoundException absent) {
            return "absent";
        } catch (ReflectiveOperationException | LinkageError error) {
            return "unverified:" + error.getClass().getSimpleName();
        }
    }

    private static void press(int index) {
        KEYS[index].setDown(true);
        KeyMapping.click(KEYS[index].getKey());
        INPUTS.add(Map.of("tick", captureTick, "time_ns", System.nanoTime() - startNanos, "key", KEYS[index].getName()));
    }

    private static void release() { for (var key : KEYS) key.setDown(false); }

    @SubscribeEvent
    static void serverTick(ServerTickEvent.Post event) {
        if (SCENE == null || playerId == null) return;
        var player = event.getServer().getPlayerList().getPlayer(playerId);
        if (player == null) return;
        var machine = player.getData(ModAttachments.COMBAT).machine;
        server = new ServerSample(event.getServer().getTickCount(), machine.phase(), machine.attackSerial(), machine.isHeavy(),
                machine.isMorphed(), machine.parriedHits(), machine.isActiveParry(), player.getHealth(), LiveOpponent.tick(player));
    }

    @SubscribeEvent
    static void beforeFrame(RenderFrameEvent.Pre event) {
        if (active) { renderedPose = null; modelSeen = readySeen = opponentSeen = false; }
    }

    @SubscribeEvent
    static void opponentRendered(RenderLivingEvent.Post<?, ?> event) {
        ServerSample sample = server;
        if (active && sample != null && sample.opponent != null && event.getEntity().getId() == sample.opponent.id())
            opponentSeen = true;
    }

    @SubscribeEvent
    static void hideHud(RenderGuiLayerEvent.Pre event) { if (active) event.setCanceled(true); }

    @SubscribeEvent
    static void afterFrame(RenderFrameEvent.Post event) {
        if (!active) return;
        Minecraft mc = Minecraft.getInstance();
        var player = mc.player;
        if (player == null || mc.screen != null || mc.isPaused()) throw new IllegalStateException("Live capture was interrupted");
        var machine = player.getData(ModAttachments.COMBAT).machine;
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("frame", frames); row.put("time_ns", System.nanoTime() - startNanos); row.put("client_tick", player.tickCount);
        row.put("partial", event.getPartialTick().getGameTimeDeltaPartialTick(false)); row.put("phase", machine.phase());
        row.put("phase_us", machine.phaseElapsedUs()); row.put("serial", machine.attackSerial()); row.put("type", machine.type());
        row.put("heavy", machine.isHeavy()); row.put("variant", machine.variant()); row.put("mirrored", machine.isMirrored());
        row.put("model_seen", modelSeen); row.put("ready_seen", readySeen);
        row.put("opponent_seen", opponentSeen);
        if (opponentSeen) opponentRenderFrames++;
        row.put("parried_hits", machine.parriedHits()); row.put("active_parry", machine.isActiveParry());
        row.put("health", player.getHealth());
        row.put("rendered_phase", renderedPose == null ? null : renderedPose.phase());
        row.put("rendered_progress", renderedPose == null || renderedPose.swing() == null ? null : renderedPose.swing().progress());
        row.put("rendered_weight", renderedPose == null ? null : renderedPose.weight());
        row.put("arm", player.getMainArm()); row.put("offhand", BuiltInRegistries.ITEM.getKey(player.getOffhandItem().getItem()));
        ServerSample sample = server;
        row.put("server", sample);
        if (sample != null) {
            serverAttack |= sample.phase.isAttack(); serverHeavy |= sample.heavy;
            serverRelease |= sample.phase == Phase.RELEASE; serverMorph |= sample.morphed; serverGuard |= sample.phase == Phase.PARRY;
            serverCaught |= sample.parriedHits > 0;
            serverRiposte |= SCENE.equals("riposte") && sample.activeParry && sample.phase.isAttack();
            if (sample.opponent != null) {
                if (Float.isNaN(initialOpponentHealth)) initialOpponentHealth = sample.opponent.health();
                minOpponentHealth = Math.min(minOpponentHealth, sample.opponent.health());
            }
            maxServerSerial = Math.max(maxServerSerial, sample.serial);
        }
        FRAMES.add(row);
        Screenshot.grab(out.toFile(), String.format(java.util.Locale.ROOT, "frame_%04d.png", frames++),
                mc.getMainRenderTarget(), ignored -> SAVED.incrementAndGet());
    }

    private static void finish(Minecraft mc) throws IOException {
        release();
        for (int i = 0; i < KEYS.length; i++) KEYS[i].setKey(OLD_KEYS[i]);
        KeyMapping.resetMapping();
        active = false; draining = true;
        mc.options.framerateLimit().set(previousFps);
        mc.options.hideGui = previousHideGui; mc.options.pauseOnLostFocus = previousPause;
        mc.options.setCameraType(previousView);
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("scene", SCENE); report.put("frames", FRAMES); report.put("inputs", INPUTS);
        report.put("conditions", CONDITIONS);
        report.put("mods", ModList.get().getMods().stream().map(m -> m.getModId() + ":" + m.getVersion()).toList());
        report.put("client_attack", clientAttack); report.put("server_attack", serverAttack);
        report.put("client_heavy", clientHeavy); report.put("server_heavy", serverHeavy);
        report.put("client_release", clientRelease); report.put("server_release", serverRelease);
        report.put("client_morph", clientMorph); report.put("server_morph", serverMorph);
        report.put("client_guard", clientGuard); report.put("server_guard", serverGuard);
        if (SCENE.equals("riposte")) {
            report.put("client_caught", clientCaught); report.put("server_caught", serverCaught);
            report.put("client_riposte", clientRiposte); report.put("server_riposte", serverRiposte);
            report.put("opponent_render_frames", opponentRenderFrames);
            report.put("initial_opponent_health", initialOpponentHealth); report.put("min_opponent_health", minOpponentHealth);
        }
        report.put("client_serials", maxClientSerial - firstSerial); report.put("server_serials", maxServerSerial - firstServerSerial);
        Files.writeString(out.resolve("capture.json"), new GsonBuilder().serializeNulls().setPrettyPrinting().create().toJson(report));
        if (!SCENE.equals("parry") && (!clientAttack || !serverAttack)) throw new IllegalStateException("Live input did not reach client and server");
        if (SCENE.equals("combo") && (!followup || maxClientSerial - firstSerial < 2 || maxServerSerial - firstServerSerial < 2))
            throw new IllegalStateException("Live combo did not start on client and server");
        if (SCENE.equals("heavy") && (!clientHeavy || !serverHeavy)) throw new IllegalStateException("Live heavy upgrade did not reach client and server");
        if (SCENE.equals("morph") && (!followup || !clientMorph || !serverMorph)) throw new IllegalStateException("Live morph did not reach client and server");
        if (SCENE.equals("feint") && (!followup || clientRelease || serverRelease)) throw new IllegalStateException("Live feint did not abort release");
        if (SCENE.equals("parry") && (!clientGuard || !serverGuard)) throw new IllegalStateException("Live guard did not reach client and server");
        if (SCENE.equals("riposte") && (!followup || !clientCaught || !serverCaught || !clientRiposte || !serverRiposte
                || opponentRenderFrames == 0 || !(minOpponentHealth < initialOpponentHealth)
                || FRAMES.stream().anyMatch(frame -> ((Number) frame.get("health")).floatValue() < initialPlayerHealth)))
            throw new IllegalStateException("Live block/riposte did not catch the attack and damage the rendered opponent without player damage");
    }
}
