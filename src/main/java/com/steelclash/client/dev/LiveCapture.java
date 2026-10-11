package com.steelclash.client.dev;

import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.platform.InputConstants;
import com.steelclash.SteelClash;
import com.steelclash.client.ClientInput;
import com.steelclash.client.anim.CombatPose;
import com.steelclash.combat.ModAttachments;
import com.steelclash.core.Phase;
import com.steelclash.core.Mat3;
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
import net.minecraft.world.entity.HumanoidArm;
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
import org.lwjgl.glfw.GLFW;

/**
 * Opt-in live input/render diagnostic. Uses normal keyboard bindings, prediction and server packets;
 * never applies a frozen combat state. Staging changes a world: use a disposable copy with cheats.
 */
@EventBusSubscriber(modid = SteelClash.MOD_ID, value = Dist.CLIENT)
public final class LiveCapture {
    private static final String SCENE = System.getProperty("steelclash.liveCapture");
    private static final boolean BACKGROUND = Boolean.getBoolean("steelclash.liveCaptureBackground");
    private static final boolean ACTIVE_PARRY = "active-parry".equals(SCENE);
    private static final boolean FOOD_USE = "itemuse".equals(SCENE) || "use-attack".equals(SCENE);
    private static final boolean ITEM_USE = FOOD_USE || "drinkuse".equals(SCENE);
    private static final boolean LOCOMOTION = "locomotion".equals(SCENE) || "locomotion-attack".equals(SCENE);
    private static final String DEFAULT_ITEM = "hitstop".equals(SCENE) ? "minecraft:mace" : "minecraft:iron_sword";
    private static final String DEFAULT_OFFHAND = "drinkuse".equals(SCENE) ? "minecraft:potion" : FOOD_USE ? "minecraft:bread" : "minecraft:air";
    private static final KeyMapping[] KEYS = {ClientInput.SLASH_RIGHT_TO_LEFT, ClientInput.STAB, ClientInput.FEINT, ClientInput.PARRY, ClientInput.KICK};
    private static final InputConstants.Key[] OLD_KEYS = new InputConstants.Key[KEYS.length];
    private static final List<Map<String, Object>> FRAMES = new ArrayList<>();
    private static final List<Map<String, Object>> INPUTS = new ArrayList<>();
    private static final AtomicInteger SAVED = new AtomicInteger();
    private static int ticks, frames, releaseAt;
    private static volatile int captureTick;
    private static boolean initialized, done, followup, modelSeen, readySeen;
    private static volatile boolean active;
    private static boolean backgroundPrepared, pauseOnLostFocusSaved;
    private static boolean clientAttack, serverAttack, clientHeavy, serverHeavy;
    private static boolean clientRelease, serverRelease, clientMorph, serverMorph, clientGuard, serverGuard;
    private static boolean clientCaught, serverCaught, clientRiposte, serverRiposte, opponentSeen;
    private static boolean clientActiveParry, serverActiveParry;
    private static boolean serverSecondaryJabWindup, serverSecondaryJabRelease;
    private static int serverActiveParryExtensions, previousServerActiveParryTicks, previousServerActiveParrySerial = Integer.MIN_VALUE;
    private static final long MAX_ACTIVE_PARRY_SYNC_DELAY_NANOS = 100_000_000L; // Two 50 ms tick edges, excluding the earlier riposte grant.
    private static final List<Map<String, Object>> ACTIVE_PARRY_MERGES = new ArrayList<>();
    private static final List<Map<String, Object>> SERVER_ACTIVE_PARRY_EXTENSIONS = new java.util.concurrent.CopyOnWriteArrayList<>();
    private static boolean clientCounter, serverCounter, clientThwack, serverThwack;
    private static int opponentRenderFrames;
    private static float initialPlayerHealth, initialOpponentHealth = Float.NaN, minOpponentHealth = Float.POSITIVE_INFINITY;
    private static final Map<String, Object> CONDITIONS = new LinkedHashMap<>();
    private static boolean draining;
    private static int drainTicks;
    private static int firstSerial, maxClientSerial, maxServerSerial, firstServerSerial;
    private static int previousFps;
    private static int previousFov;
    private static KeyMapping[] movementKeys;
    private static long reloadGeneration;
    private static boolean reloadRequested, reloadCompleted;
    private static int reloadOverlayFrames;
    private static boolean previousToggleCrouch, previousToggleSprint;
    @Nullable private static java.util.concurrent.CompletableFuture<Void> reload;
    private static boolean previousHideGui, previousPause;
    private static CameraType previousView;
    @Nullable private static HumanoidArm previousMainArm;
    @Nullable private static KeyMapping useKey;
    @Nullable private static InputConstants.Key previousUseKey;
    private static boolean foodStaged;
    @Nullable private static KeyMapping swapKey;
    @Nullable private static InputConstants.Key previousSwapKey;
    private static long startNanos;
    private static Path out;
    private static volatile UUID playerId;
    @Nullable private static CombatPose renderedPose;
    @Nullable private static float[] carriedRotation;
    @Nullable private static double[] carriedIdleDelta;
    @Nullable private static Double carriedUseWeight;
    private static final Map<String, double[]> renderedBones = new LinkedHashMap<>();
    private static boolean rigBlendSeen;
    @Nullable private static double[] readyView;
    private static boolean playerBodySeen;
    private static final Map<String, double[]> opponentBones = new LinkedHashMap<>();
    @Nullable private static com.steelclash.core.AttackType opponentRenderedType;
    @Nullable private static Phase opponentRenderedPhase;
    private record ServerSample(long tick, Phase phase, int serial, boolean heavy, boolean morphed,
                                int parriedHits, boolean activeParry, int activeParryTicks, boolean countered, boolean thwacked,
                                float health, HumanoidArm arm, boolean usingItem, int food, int offhandCount, String offhandItem,
                                String mainItem, com.steelclash.core.AttackType type,
                                double x, double z, boolean crouching, boolean sprinting,
                                @Nullable LiveOpponent.Sample opponent) {}
    private static volatile ServerSample server;

    private LiveCapture() {}

    public static void recordRenderedPose(AbstractClientPlayer player, @Nullable CombatPose pose, boolean ready, double useWeight) {
        if (active && player == Minecraft.getInstance().player) {
            modelSeen = true;
            renderedPose = pose;
            readySeen = ready;
            carriedUseWeight = useWeight;
        }
    }

    public static boolean recording() { return active; }

    /** Owner-thread diagnostic for a positive timer-only server merge in the live active-parry scene. */
    public static void recordActiveParryMerge(int before, int after, int confirmed, int serial) {
        if (ACTIVE_PARRY && active && after > before) {
            ACTIVE_PARRY_MERGES.add(Map.of("time_ns", System.nanoTime() - startNanos, "before", before, "after", after,
                    "confirmed", confirmed, "serial", serial));
        }
    }

    /** Only the opted-in hidden diagnostic may drive keyboard combat without capturing the user's mouse. */
    public static boolean backgroundInputEnabled() { return active && BACKGROUND && backgroundPrepared; }

    public static void recordReadyView(AbstractClientPlayer player, double yaw, double pitch) {
        if (active && player == Minecraft.getInstance().player) readyView = new double[]{yaw, pitch};
    }

    public static void recordMob(net.minecraft.world.entity.LivingEntity entity, @Nullable CombatPose pose, String part, double[] transform) {
        ServerSample sample = server;
        if (active && sample != null && sample.opponent != null && entity.getId() == sample.opponent.id()) {
            opponentBones.put(part, transform);
            if (pose != null) { opponentRenderedType = pose.type(); opponentRenderedPhase = pose.phase(); }
        }
    }

    public static void recordBone(AbstractClientPlayer player, com.zigythebird.playeranimcore.bones.PlayerAnimBone bone, boolean rigBlend) {
        if (active && player == Minecraft.getInstance().player && (com.zigythebird.playeranimcore.api.firstPerson.FirstPersonMode.isFirstPersonPass()
                || !Minecraft.getInstance().options.getCameraType().isFirstPerson())) {
            renderedBones.put(bone.getName(), new double[]{bone.getRotX(), bone.getRotY(), bone.getRotZ(), bone.getPosX(), bone.getPosY(), bone.getPosZ()});
            rigBlendSeen |= rigBlend;
        }
    }

    public static void recordCarriedRotation(AbstractClientPlayer player, float x, float y, float z, double[] reference) {
        if (active && player == Minecraft.getInstance().player) {
            carriedRotation = new float[]{x, y, z};
            carriedIdleDelta = Mat3.zyx(reference[0], reference[1], reference[2]).transpose()
                    .mul(Mat3.zyx(x, y, z)).toZyx();
        }
    }

    @SubscribeEvent
    static void tick(ClientTickEvent.Pre event) throws IOException {
        if (SCENE == null || done) return;
        Minecraft mc = Minecraft.getInstance();
        if (BACKGROUND && !backgroundPrepared && validScene() && !PoseSheet.running()) {
            previousPause = mc.options.pauseOnLostFocus;
            pauseOnLostFocusSaved = true;
            mc.options.pauseOnLostFocus = false;
            long window = mc.getWindow().getWindow();
            GLFW.glfwHideWindow(window);
            if (GLFW.glfwGetWindowAttrib(window, GLFW.GLFW_VISIBLE) != GLFW.GLFW_FALSE) {
                mc.options.pauseOnLostFocus = previousPause;
                throw new IllegalStateException("Background live capture requires a windowed Minecraft window; GLFW cannot hide fullscreen windows");
            }
            mc.mouseHandler.releaseMouse();
            backgroundPrepared = true;
        }
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
            if (!validScene())
                throw new IllegalArgumentException("Unknown live capture scene: " + SCENE);
            String item = item("steelclash.liveCaptureItem", DEFAULT_ITEM);
            String offhand = item("steelclash.liveCaptureOffhand", DEFAULT_OFFHAND);
            if (ITEM_USE && !offhand.equals(DEFAULT_OFFHAND))
                throw new IllegalArgumentException("Use scene requires " + DEFAULT_OFFHAND + " in the offhand");
            out = Path.of(System.getProperty("steelclash.liveCaptureOut", new java.io.File(mc.gameDirectory,
                    "steelclash-live/" + System.currentTimeMillis()).getPath()));
            if (Files.exists(out.resolve("capture.json")) || Files.exists(out.resolve("screenshots")))
                throw new IllegalStateException("Live capture output must be fresh: " + out);
            Files.createDirectories(out);
            previousFps = mc.options.framerateLimit().get();
            previousFov = mc.options.fov().get();
            previousHideGui = mc.options.hideGui;
            if (!pauseOnLostFocusSaved) {
                previousPause = mc.options.pauseOnLostFocus;
                pauseOnLostFocusSaved = true;
            }
            previousView = mc.options.getCameraType();
            mc.options.pauseOnLostFocus = false;
            int fps = Integer.parseInt(System.getProperty("steelclash.liveCaptureFps", "20"));
            int fov = Integer.parseInt(System.getProperty("steelclash.liveCaptureFov", Integer.toString(previousFov)));
            if (fps < 10 || fps > 120 || fov < 30 || fov > 110) throw new IllegalArgumentException("Capture FPS/FOV out of bounds");
            mc.options.framerateLimit().set(fps);
            mc.options.fov().set(fov);
            movementKeys = new KeyMapping[]{mc.options.keyUp, mc.options.keyDown, mc.options.keyLeft, mc.options.keyRight, mc.options.keyShift, mc.options.keySprint};
            previousToggleCrouch=mc.options.toggleCrouch().get(); previousToggleSprint=mc.options.toggleSprint().get();
            if (LOCOMOTION) { mc.options.toggleCrouch().set(false); mc.options.toggleSprint().set(false); }
            // F1 hides vanilla's first-person hands too; cancel GUI layers separately instead.
            mc.options.hideGui = false;
            mc.options.setCameraType(CameraType.valueOf(System.getProperty("steelclash.liveCaptureView", "FIRST_PERSON")));
            String mainArm = System.getProperty("steelclash.liveCaptureArm");
            if (mainArm != null) {
                previousMainArm = mc.options.mainHand().get();
                HumanoidArm requested = HumanoidArm.valueOf(mainArm);
                mc.options.mainHand().set(requested);
                mc.options.broadcastOptions();
                player.setMainArm(requested);
            }
            for (int i = 0; i < KEYS.length; i++) {
                OLD_KEYS[i] = KEYS[i].getKey();
                KEYS[i].setKey(InputConstants.Type.KEYSYM.getOrCreate(321 + i)); // numpad 1..4
            }
            if (ITEM_USE) {
                useKey = mc.options.keyUse;
                previousUseKey = useKey.getKey();
                useKey.setKey(InputConstants.Type.KEYSYM.getOrCreate(326)); // numpad 6, vanilla item use
            }
            if (SCENE.startsWith("swap-")) {
                int slot = (player.getInventory().selected + 1) % 9;
                swapKey = mc.options.keyHotbarSlots[slot]; previousSwapKey = swapKey.getKey();
                swapKey.setKey(InputConstants.Type.KEYSYM.getOrCreate(327)); // numpad 7
                player.connection.sendCommand("item replace entity @s hotbar." + slot + " with "
                        + (SCENE.equals("swap-empty") ? "minecraft:air" : "minecraft:iron_axe"));
            }
            KeyMapping.resetMapping();
            playerId = player.getUUID();
            for (String command : new String[]{"gamerule doDaylightCycle false", "gamerule doWeatherCycle false",
                    "gamerule doMobLoot false", "gamerule sendCommandFeedback false", "time set noon", "weather clear",
                    "effect clear @s", LOCOMOTION ? "fill ~-12 300 ~-12 ~12 300 ~12 minecraft:polished_andesite" : "fill ~-3 300 ~-3 ~3 300 ~3 minecraft:polished_andesite",
                    "tp @s ~ 301 ~ 0 0", "kill @e[type=!player,distance=..12]",
                    "item replace entity @s weapon.mainhand with " + item,
                    "item replace entity @s weapon.offhand with " + offhand + (FOOD_USE ? " 64" : "")}) player.connection.sendCommand(command);
            if (LiveOpponent.enabled() || ITEM_USE) {
                player.connection.sendCommand("gamemode survival @s");
                player.connection.sendCommand("effect give @s minecraft:instant_health 1 10 true");
                if (LiveOpponent.enabled()) player.connection.sendCommand(LiveOpponent.summonCommand());
                String secondSummon = LiveOpponent.secondSummonCommand();
                if (secondSummon != null) player.connection.sendCommand(secondSummon);
            }
            initialized = true;
        }
        if (++ticks < 100) return;
        if (!BACKGROUND) {
            mc.setScreen(null);
            mc.mouseHandler.grabMouse();
        } else if (mc.screen != null) {
            if (ticks > 1300) throw new IllegalStateException("Background live capture needs a gameplay screen with no GUI");
            return;
        }
        if (!BACKGROUND && !mc.mouseHandler.isMouseGrabbed()) {
            if (ticks > 1300) throw new IllegalStateException("Live capture needs an active game window for normal input");
            return;
        }
        float pitch = Float.parseFloat(System.getProperty("steelclash.liveCapturePitch", "0"));
        if (!Float.isFinite(pitch) || Math.abs(pitch) > 60) throw new IllegalArgumentException("Capture pitch out of range");
        player.setXRot(pitch);
        player.setYRot(LOCOMOTION && captureTick >= 90 ? (captureTick - 90) * 4 : 0);
        if (!LOCOMOTION) player.yBodyRot = player.yBodyRotO = 0;
        var machine = player.getData(ModAttachments.COMBAT).machine;
        if (!active) {
            if (!BuiltInRegistries.ITEM.getKey(player.getMainHandItem().getItem()).toString()
                    .equals(item("steelclash.liveCaptureItem", DEFAULT_ITEM))
                    || !BuiltInRegistries.ITEM.getKey(player.getOffhandItem().getItem()).toString()
                    .equals(item("steelclash.liveCaptureOffhand", DEFAULT_OFFHAND)))
                throw new IllegalStateException("Live capture equipment was not applied by the server");
            player.getRandom().setSeed(61);
            firstSerial = machine.attackSerial();
            firstServerSerial = server == null ? 0 : server.serial;
            initialPlayerHealth = player.getHealth();
            startNanos = System.nanoTime();
            active = true;
            CONDITIONS.put("view", mc.options.getCameraType()); CONDITIONS.put("arm", player.getMainArm());
            CONDITIONS.put("background", BACKGROUND);
            CONDITIONS.put("item", BuiltInRegistries.ITEM.getKey(player.getMainHandItem().getItem()).toString());
            CONDITIONS.put("offhand", BuiltInRegistries.ITEM.getKey(player.getOffhandItem().getItem()).toString());
            CONDITIONS.put("skin", player.getSkin().model() + ":" + player.getSkin().texture());
            CONDITIONS.put("width", mc.getWindow().getWidth()); CONDITIONS.put("height", mc.getWindow().getHeight());
            CONDITIONS.put("fov", mc.options.fov().get()); CONDITIONS.put("fps_limit", mc.options.framerateLimit().get());
            CONDITIONS.put("pitch", pitch);
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
        if (LOCOMOTION) {
            for (var key : movementKeys) key.setDown(false);
            if (captureTick >= 20 && captureTick < 35) movementKeys[0].setDown(true);
            if (captureTick >= 35 && captureTick < 50) movementKeys[1].setDown(true);
            if (captureTick >= 50 && captureTick < 80) {
                movementKeys[4].setDown(true);
                movementKeys[captureTick < 65 ? 3 : 2].setDown(true);
            }
            if (captureTick >= 80 && captureTick < 95) { movementKeys[0].setDown(true); movementKeys[5].setDown(true); }
            if (captureTick >= 95 && captureTick < 110) movementKeys[1].setDown(true);
        }
        int attackAt = SCENE.equals("locomotion-attack") ? 60 : 20;
        if (captureTick == attackAt && !SCENE.startsWith("mob-") && !List.of("idle", "locomotion").contains(SCENE)) {
            if (ITEM_USE) {
                useKey.setDown(true); KeyMapping.click(useKey.getKey());
                INPUTS.add(Map.of("tick", captureTick, "time_ns", System.nanoTime() - startNanos, "key", useKey.getName()));
                releaseAt = captureTick + 35;
            } else {
                boolean guard = SCENE.equals("parry") || SCENE.equals("riposte") || SCENE.equals("counter") || ACTIVE_PARRY;
                press(SCENE.equals("kick-attack") ? 4 : guard ? 3 : 0);
                releaseAt = captureTick + (SCENE.equals("heavy") ? 9 : guard ? 40 : 1);
            }
        }
        if (captureTick == releaseAt) release();
        if (SCENE.equals("reload") && !reloadRequested && machine.phase() == Phase.WINDUP && machine.phaseTick() >= 3) {
            reloadGeneration = com.steelclash.client.anim.AnimationLibrary.INSTANCE.generation();
            reload = mc.reloadResourcePacks(); reloadRequested = true;
            INPUTS.add(Map.of("tick",captureTick,"time_ns",System.nanoTime()-startNanos,"action","resource_reload"));
        }
        if (reload != null && reload.isDone()) { reload.join(); reloadCompleted = true; }
        if (!followup && captureTick > 20) {
            if (SCENE.equals("use-attack") && player.isUsingItem() && captureTick >= 32) {
                release(); press(0); followup = true; releaseAt = captureTick + 1;
            } else if (SCENE.equals("riposte") && machine.parriedHits() > 0 && machine.isRiposteReady()) {
                release(); press(0); followup = true; releaseAt = captureTick + 1;
            } else if (ACTIVE_PARRY && machine.parriedHits() > 0 && machine.isRiposteReady()) {
                release(); press(0); followup = true; releaseAt = captureTick + 1;
            } else if (SCENE.equals("counter") && server != null && server.opponent != null
                    && server.opponent.phase() == Phase.WINDUP
                    && server.opponent.elapsedUs() >= server.opponent.durationUs() - 150_000) {
                release(); press(0); followup = true; releaseAt = captureTick + 1;
            } else if (SCENE.equals("combo") && machine.phase() == Phase.RELEASE && machine.phaseTick() >= 2) {
                press(1); followup = true; releaseAt = captureTick + 1;
            } else if ((SCENE.equals("feint") || SCENE.equals("morph"))
                    && machine.phase() == Phase.WINDUP && machine.phaseTick() >= 3) {
                press(SCENE.equals("feint") ? 2 : 1); followup = true; releaseAt = captureTick + 1;
            } else if (SCENE.equals("weapon-kick") && machine.phase() == Phase.WINDUP && machine.phaseTick() >= 3) {
                press(4); followup = true; releaseAt = captureTick + 1;
            } else if (SCENE.equals("kick-attack") && machine.type() == com.steelclash.core.AttackType.KICK && machine.phase() == Phase.RECOVERY && machine.phaseTick() >= 2) {
                press(0); followup = true; releaseAt = captureTick + 1;
            } else if (SCENE.startsWith("swap-") && machine.phase() == Phase.WINDUP && machine.phaseTick() >= 3) {
                swapKey.setDown(true); KeyMapping.click(swapKey.getKey()); followup = true; releaseAt = captureTick + 1;
                INPUTS.add(Map.of("tick", captureTick, "time_ns", System.nanoTime() - startNanos, "key", swapKey.getName()));
            }
        }
        clientAttack |= machine.phase().isAttack();
        clientHeavy |= machine.isHeavy();
        clientRelease |= machine.phase() == Phase.RELEASE;
        clientMorph |= machine.isMorphed();
        clientGuard |= machine.phase() == Phase.PARRY;
        clientCaught |= machine.parriedHits() > 0;
        clientActiveParry |= ACTIVE_PARRY && machine.isActiveParry();
        clientRiposte |= (SCENE.equals("riposte") || ACTIVE_PARRY) && machine.isActiveParry() && machine.phase().isAttack();
        clientRelease |= ACTIVE_PARRY && machine.phase() == Phase.RELEASE && machine.type() == com.steelclash.core.AttackType.SLASH;
        clientCounter |= machine.predictionState().countered();
        clientThwack |= machine.isThwacked();
        maxClientSerial = Math.max(maxClientSerial, machine.attackSerial());
        if (captureTick >= 140) finish(mc);
    }

    private static String item(String property, String fallback) {
        var id = ResourceLocation.parse(System.getProperty(property, fallback));
        if (!BuiltInRegistries.ITEM.containsKey(id)) throw new IllegalArgumentException("Unknown live item: " + id);
        return id.toString();
    }

    private static boolean validScene() {
        return List.of("idle", "attack", "combo", "heavy", "feint", "morph", "parry", "riposte", "counter", "active-parry", "hitstop",
                "interrupt-windup", "interrupt-release", "itemuse", "use-attack", "drinkuse", "weapon-kick", "kick-attack", "swap-weapon", "swap-empty",
                "locomotion", "locomotion-attack", "reload", "mob-attack", "mob-morph", "mob-kick", "mob-track-attack", "mob-track-guard").contains(SCENE);
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

    private static void release() {
        for (var key : KEYS) key.setDown(false);
        if (useKey != null) useKey.setDown(false);
        if (swapKey != null) swapKey.setDown(false);
    }

    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.HIGHEST)
    static void damage(net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Post event) {
        if (active) LiveOpponent.damaged(event);
    }

    @SubscribeEvent
    static void serverTick(ServerTickEvent.Post event) {
        if (SCENE == null || playerId == null) return;
        var player = event.getServer().getPlayerList().getPlayer(playerId);
        if (player == null) return;
        var machine = player.getData(ModAttachments.COMBAT).machine;
        if (ITEM_USE && active && !foodStaged) {
            player.getFoodData().setFoodLevel(14);
            player.getFoodData().setSaturation(0);
            foodStaged = true;
        }
        int activeParryTicks = machine.predictionState().activeParryTicks();
        if (ACTIVE_PARRY && active) {
            if (machine.attackSerial() == previousServerActiveParrySerial && previousServerActiveParryTicks > 0
                    && activeParryTicks > previousServerActiveParryTicks) {
                long observedAt = System.nanoTime() - startNanos;
                serverActiveParryExtensions++;
                SERVER_ACTIVE_PARRY_EXTENSIONS.add(Map.of("time_ns", observedAt, "serial", machine.attackSerial(),
                        "before", previousServerActiveParryTicks, "after", activeParryTicks));
            }
            previousServerActiveParrySerial = machine.attackSerial();
            previousServerActiveParryTicks = activeParryTicks;
        }
        LiveOpponent.Sample opponentSample = LiveOpponent.tick(player, active, captureTick);
        if (ACTIVE_PARRY && opponentSample != null && opponentSample.secondaryStarted()) {
            serverSecondaryJabWindup |= opponentSample.secondaryType() == com.steelclash.core.AttackType.JAB
                    && opponentSample.secondaryPhase() == Phase.WINDUP;
            serverSecondaryJabRelease |= opponentSample.secondaryType() == com.steelclash.core.AttackType.JAB
                    && opponentSample.secondaryPhase() == Phase.RELEASE;
        }
        serverActiveParry |= ACTIVE_PARRY && machine.isActiveParry();
        serverRelease |= ACTIVE_PARRY && machine.phase() == Phase.RELEASE && machine.type() == com.steelclash.core.AttackType.SLASH;
        server = new ServerSample(event.getServer().getTickCount(), machine.phase(), machine.attackSerial(), machine.isHeavy(),
                machine.isMorphed(), machine.parriedHits(), machine.isActiveParry(), activeParryTicks, machine.predictionState().countered(),
                machine.isThwacked(), player.getHealth(), player.getMainArm(), player.isUsingItem(),
                player.getFoodData().getFoodLevel(), player.getOffhandItem().getCount(),
                BuiltInRegistries.ITEM.getKey(player.getOffhandItem().getItem()).toString(),
                BuiltInRegistries.ITEM.getKey(player.getMainHandItem().getItem()).toString(), machine.type(),
                player.getX(),player.getZ(),player.isCrouching(),player.isSprinting(),opponentSample);
    }

    @SubscribeEvent
    static void beforeFrame(RenderFrameEvent.Pre event) {
        if (BACKGROUND && backgroundPrepared && !done) GLFW.glfwHideWindow(Minecraft.getInstance().getWindow().getWindow());
        readyView = null;
        if (active) { renderedPose = null; carriedRotation = null; carriedIdleDelta = null; carriedUseWeight = null; renderedBones.clear(); opponentBones.clear(); opponentRenderedType=null; opponentRenderedPhase=null; rigBlendSeen = playerBodySeen = false; modelSeen = readySeen = opponentSeen = false; }
    }

    @SubscribeEvent
    static void opponentRendered(RenderLivingEvent.Post<?, ?> event) {
        ServerSample sample = server;
        if (active && sample != null && sample.opponent != null && event.getEntity().getId() == sample.opponent.id())
            opponentSeen = true;
    }

    @SubscribeEvent
    static void playerRendered(net.neoforged.neoforge.client.event.RenderPlayerEvent.Post event) {
        if (active && event.getEntity() == Minecraft.getInstance().player) playerBodySeen = true;
    }

    @SubscribeEvent
    static void hideHud(RenderGuiLayerEvent.Pre event) { if (active) event.setCanceled(true); }

    @SubscribeEvent
    static void afterFrame(RenderFrameEvent.Post event) {
        if (!active) return;
        Minecraft mc = Minecraft.getInstance();
        if (reloadRequested && (mc.getOverlay()!=null || mc.isPaused() || (reload!=null && !reload.isDone()))) { reloadOverlayFrames++; return; }
        var player = mc.player;
        if (player == null || mc.screen != null || mc.isPaused()) throw new IllegalStateException("Live capture was interrupted");
        var machine = player.getData(ModAttachments.COMBAT).machine;
        Map<String, Object> row = new LinkedHashMap<>();
        if (BACKGROUND) {
            long window = mc.getWindow().getWindow();
            boolean visible = GLFW.glfwGetWindowAttrib(window, GLFW.GLFW_VISIBLE) == GLFW.GLFW_TRUE;
            boolean focused = GLFW.glfwGetWindowAttrib(window, GLFW.GLFW_FOCUSED) == GLFW.GLFW_TRUE;
            boolean mouseGrabbed = mc.mouseHandler.isMouseGrabbed();
            row.put("window_visible", visible); row.put("window_focused", focused);
            row.put("mouse_grabbed", mouseGrabbed);
            if (visible) throw new IllegalStateException("Background capture window became visible");
            if (mouseGrabbed) throw new IllegalStateException("Background capture unexpectedly grabbed the mouse");
        }
        row.put("frame", frames); row.put("time_ns", System.nanoTime() - startNanos); row.put("client_tick", player.tickCount);
        row.put("partial", event.getPartialTick().getGameTimeDeltaPartialTick(false)); row.put("phase", machine.phase());
        row.put("phase_us", machine.phaseElapsedUs()); row.put("serial", machine.attackSerial()); row.put("type", machine.type());
        row.put("heavy", machine.isHeavy()); row.put("variant", machine.variant()); row.put("mirrored", machine.isMirrored());
        row.put("model_seen", modelSeen); row.put("ready_seen", readySeen);
        row.put("carried_rotation", carriedRotation);
        row.put("carried_idle_delta", carriedIdleDelta);
        row.put("carried_use_weight", carriedUseWeight);
        row.put("rendered_bones", new LinkedHashMap<>(renderedBones));
        row.put("ready_view", readyView);
        row.put("rig_blend_applied", rigBlendSeen);
        row.put("player_body_seen", playerBodySeen);
        row.put("opponent_bones", new LinkedHashMap<>(opponentBones)); row.put("opponent_rendered_type",opponentRenderedType); row.put("opponent_rendered_phase",opponentRenderedPhase);
        row.put("position",new double[]{player.getX(),player.getY(),player.getZ()}); row.put("yaw",player.getYRot()); row.put("body_yaw",player.yBodyRot);
        row.put("crouching",player.isCrouching()); row.put("sprinting",player.isSprinting()); row.put("eye_height",player.getEyeHeight());
        row.put("animation_generation",com.steelclash.client.anim.AnimationLibrary.INSTANCE.generation());
        row.put("opponent_seen", opponentSeen);
        if (opponentSeen) opponentRenderFrames++;
        row.put("parried_hits", machine.parriedHits()); row.put("active_parry", machine.isActiveParry());
        row.put("client_active_parry_ticks", machine.predictionState().activeParryTicks());
        row.put("server_active_parry_ticks", server == null ? 0 : server.activeParryTicks);
        row.put("countered", machine.predictionState().countered()); row.put("thwacked", machine.isThwacked());
        row.put("health", player.getHealth());
        row.put("using_item", player.isUsingItem());
        row.put("use_hand", player.isUsingItem() ? player.getUsedItemHand() : null);
        row.put("food", player.getFoodData().getFoodLevel());
        row.put("offhand_count", player.getOffhandItem().getCount());
        row.put("rendered_phase", renderedPose == null ? null : renderedPose.phase());
        row.put("rendered_progress", renderedPose == null || renderedPose.swing() == null ? null : renderedPose.swing().progress());
        row.put("rendered_weight", renderedPose == null ? null : renderedPose.weight());
        row.put("arm", player.getMainArm()); row.put("offhand", BuiltInRegistries.ITEM.getKey(player.getOffhandItem().getItem()).toString());
        row.put("main_item", BuiltInRegistries.ITEM.getKey(player.getMainHandItem().getItem()).toString());
        row.put("attack_item", BuiltInRegistries.ITEM.getKey(player.getData(ModAttachments.COMBAT).weapon.getItem()).toString());
        ServerSample sample = server;
        row.put("server", sample);
        if (sample != null && sample.opponent != null) {
            var actor = mc.level.getEntity(sample.opponent.id());
            row.put("opponent_present", actor != null);
            row.put("opponent_client_position", actor == null ? null : new double[]{actor.getX(), actor.getY(), actor.getZ()});
            if (actor instanceof net.minecraft.world.entity.LivingEntity fighter) {
                var opponentMachine = fighter.getData(ModAttachments.COMBAT).machine;
                row.put("opponent_client_phase", opponentMachine.phase());
                row.put("opponent_client_serial", opponentMachine.attackSerial());
                row.put("opponent_client_heavy", opponentMachine.isHeavy());
            }
        }
        if (sample != null) {
            serverAttack |= sample.phase.isAttack(); serverHeavy |= sample.heavy;
            serverRelease |= sample.phase == Phase.RELEASE; serverMorph |= sample.morphed; serverGuard |= sample.phase == Phase.PARRY;
            serverCaught |= sample.parriedHits > 0;
            serverRiposte |= (SCENE.equals("riposte") || ACTIVE_PARRY) && sample.activeParry && sample.phase.isAttack();
            serverCounter |= sample.countered; serverThwack |= sample.thwacked;
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
        // The server continues ticking after active becomes false; keep this final outcome immutable for guards.
        ServerSample finalServer = server;
        release();
        for (int i = 0; i < KEYS.length; i++) KEYS[i].setKey(OLD_KEYS[i]);
        if (useKey != null) useKey.setKey(previousUseKey);
        if (swapKey != null) swapKey.setKey(previousSwapKey);
        for (var key : movementKeys) key.setDown(false);
        KeyMapping.resetMapping();
        active = false; draining = true;
        mc.options.framerateLimit().set(previousFps);
        mc.options.fov().set(previousFov);
        mc.options.toggleCrouch().set(previousToggleCrouch); mc.options.toggleSprint().set(previousToggleSprint);
        mc.options.hideGui = previousHideGui; mc.options.pauseOnLostFocus = previousPause;
        mc.options.setCameraType(previousView);
        if (previousMainArm != null) {
            mc.options.mainHand().set(previousMainArm); mc.options.broadcastOptions();
            if (mc.player != null) mc.player.setMainArm(previousMainArm);
        }
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("scene", SCENE); report.put("frames", FRAMES); report.put("inputs", INPUTS);
        report.put("conditions", CONDITIONS);
        report.put("reload_requested",reloadRequested); report.put("reload_completed",reloadCompleted); report.put("reload_overlay_frames",reloadOverlayFrames);
        report.put("mods", ModList.get().getMods().stream().map(m -> m.getModId() + ":" + m.getVersion()).toList());
        report.put("client_attack", clientAttack); report.put("server_attack", serverAttack);
        report.put("client_heavy", clientHeavy); report.put("server_heavy", serverHeavy);
        report.put("client_release", clientRelease); report.put("server_release", serverRelease);
        report.put("client_morph", clientMorph); report.put("server_morph", serverMorph);
        report.put("client_guard", clientGuard); report.put("server_guard", serverGuard);
        report.put("client_counter", clientCounter); report.put("server_counter", serverCounter);
        report.put("client_thwack", clientThwack); report.put("server_thwack", serverThwack);
        if (LiveOpponent.enabled()) {
            report.put("client_caught", clientCaught); report.put("server_caught", serverCaught);
            report.put("client_riposte", clientRiposte); report.put("server_riposte", serverRiposte);
            report.put("opponent_render_frames", opponentRenderFrames);
            report.put("initial_opponent_health", initialOpponentHealth); report.put("min_opponent_health", minOpponentHealth);
        }
        if (ACTIVE_PARRY) {
            report.put("client_active_parry", clientActiveParry);
            report.put("server_active_parry", serverActiveParry);
            report.put("server_active_parry_extensions", serverActiveParryExtensions);
            report.put("server_active_parry_extension_samples", SERVER_ACTIVE_PARRY_EXTENSIONS);
            report.put("client_active_parry_merges", ACTIVE_PARRY_MERGES);
            report.put("client_merge_near_server_extension", hasNearbyActiveParryMerge());
            report.put("server_secondary_jab_windup", serverSecondaryJabWindup);
            report.put("server_secondary_jab_release", serverSecondaryJabRelease);
            report.put("client_riposte_release", clientRelease);
            report.put("server_riposte_release", serverRelease);
        }
        report.put("client_serials", maxClientSerial - firstSerial); report.put("server_serials", maxServerSerial - firstServerSerial);
        Files.writeString(out.resolve("capture.json"), new GsonBuilder().serializeNulls().setPrettyPrinting().create().toJson(report));
        if (!SCENE.startsWith("mob-") && !List.of("idle", "parry", "itemuse", "drinkuse", "locomotion").contains(SCENE) && (!clientAttack || !serverAttack)) throw new IllegalStateException("Live input did not reach client and server");
        if (SCENE.equals("idle") && (clientAttack || serverAttack || !INPUTS.isEmpty()
                || FRAMES.stream().anyMatch(frame -> frame.get("phase") != Phase.IDLE)))
            throw new IllegalStateException("Live idle scene was not idle throughout");
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
        if (SCENE.equals("counter") && (!followup || !clientCounter || !serverCounter || opponentRenderFrames == 0
                || !(minOpponentHealth < initialOpponentHealth)
                || FRAMES.stream().anyMatch(frame -> ((Number) frame.get("health")).floatValue() < initialPlayerHealth)))
            throw new IllegalStateException("Live matching counter failed to catch and return the attack");
        if (ACTIVE_PARRY && (!followup || !clientCaught || !serverCaught || !clientRiposte || !serverRiposte
                || !clientActiveParry || !serverActiveParry
                || !clientRelease || !serverRelease
                || !serverSecondaryJabWindup || !serverSecondaryJabRelease || opponentRenderFrames == 0
                || serverActiveParryExtensions == 0 || !hasNearbyActiveParryMerge()
                || FRAMES.stream().anyMatch(frame -> ((Number) frame.get("health")).floatValue() < initialPlayerHealth
                    || frame.get("server") instanceof ServerSample sample && sample.health < initialPlayerHealth)))
            throw new IllegalStateException("Live active-parry extension did not reach the client during the ordinary riposte without interrupting release or damaging the player");
        if (SCENE.equals("hitstop") && (!clientThwack || !serverThwack || opponentRenderFrames == 0
                || !(minOpponentHealth > 0 && minOpponentHealth < initialOpponentHealth)))
            throw new IllegalStateException("Live light blunt strike did not stop in the surviving opponent");
        if (SCENE.equals("weapon-kick") && (!followup || FRAMES.stream().noneMatch(f -> f.get("type") == com.steelclash.core.AttackType.KICK && f.get("phase") == Phase.RELEASE)
                || FRAMES.stream().noneMatch(f -> f.get("server") instanceof ServerSample s && s.type == com.steelclash.core.AttackType.KICK && s.phase == Phase.RELEASE)))
            throw new IllegalStateException("Windup replacement did not become a released kick on both sides");
        if (SCENE.equals("kick-attack") && (!followup || maxClientSerial-firstSerial < 2 || maxServerSerial-firstServerSerial < 2
                || FRAMES.stream().noneMatch(f -> f.get("type") == com.steelclash.core.AttackType.SLASH && f.get("phase") == Phase.RELEASE)
                || FRAMES.stream().noneMatch(f -> f.get("server") instanceof ServerSample s && s.type == com.steelclash.core.AttackType.SLASH && s.phase == Phase.RELEASE)))
            throw new IllegalStateException("Buffered attack did not follow the kick on both sides");
        if (SCENE.startsWith("swap-")) {
            String expected = SCENE.equals("swap-empty") ? "minecraft:air" : "minecraft:iron_axe";
            if (!followup || finalServer == null || !finalServer.mainItem.equals(expected) || finalServer.phase != Phase.IDLE
                    || FRAMES.subList(Math.max(0, FRAMES.size()-20), FRAMES.size()).stream().anyMatch(f -> !f.get("main_item").equals(expected) || f.get("phase") != Phase.IDLE)
                    || clientRelease || serverRelease)
                throw new IllegalStateException("Hotbar swap did not cancel the windup before release");
        }
        if (LOCOMOTION && (FRAMES.stream().noneMatch(f -> Boolean.TRUE.equals(f.get("crouching")))
                || FRAMES.stream().noneMatch(f -> Boolean.TRUE.equals(f.get("sprinting")))
                || FRAMES.stream().noneMatch(f -> f.get("server") instanceof ServerSample s && s.crouching)
                || FRAMES.stream().noneMatch(f -> f.get("server") instanceof ServerSample s && s.sprinting)
                || FRAMES.stream().noneMatch(f -> ((Number)f.get("yaw")).doubleValue()>180)))
            throw new IllegalStateException("Locomotion did not crouch/sprint/turn on both sides");
        if (SCENE.equals("locomotion") && (clientAttack || serverAttack)) throw new IllegalStateException("Locomotion idle unexpectedly attacked");
        if (SCENE.equals("reload") && (!reloadRequested || !reloadCompleted
                || com.steelclash.client.anim.AnimationLibrary.INSTANCE.generation()<=reloadGeneration
                || finalServer==null || finalServer.phase!=Phase.IDLE || mc.player.getData(ModAttachments.COMBAT).machine.phase()!=Phase.IDLE))
            throw new IllegalStateException("Reload did not replace animation generation and return both sides to idle");
        if (SCENE.startsWith("mob-") && (opponentRenderFrames<30 || (!SCENE.equals("mob-track-guard") && FRAMES.stream().noneMatch(f -> f.get("server") instanceof ServerSample s && s.opponent!=null && s.opponent.phase()==Phase.RELEASE))
                || (SCENE.equals("mob-morph") && FRAMES.stream().noneMatch(f -> f.get("opponent_rendered_type")==com.steelclash.core.AttackType.STAB))
                || (SCENE.equals("mob-kick") && FRAMES.stream().noneMatch(f -> f.get("opponent_rendered_type")==com.steelclash.core.AttackType.KICK))))
            throw new IllegalStateException("Requested ordinary mob transition was not rendered");
        if (SCENE.startsWith("mob-track-")) {
            Phase expected = SCENE.equals("mob-track-guard") ? Phase.PARRY : Phase.WINDUP;
            if (FRAMES.stream().noneMatch(f -> f.get("server") instanceof ServerSample s && s.opponent != null
                        && s.opponent.away() && !s.opponent.returned() && !Boolean.TRUE.equals(f.get("opponent_seen")))
                    || FRAMES.stream().noneMatch(f -> f.get("server") instanceof ServerSample s && s.opponent != null
                        && s.opponent.returned() && s.opponent.phase() == expected && f.get("opponent_client_phase") == expected))
                throw new IllegalStateException("Late tracking did not restore the current opponent pose");
        }
        if (SCENE.startsWith("interrupt-")) {
            Phase expected = SCENE.equals("interrupt-windup") ? Phase.WINDUP : Phase.RELEASE;
            if (finalServer == null || finalServer.opponent == null || finalServer.opponent.hitPhase() != expected
                    || !(finalServer.opponent.hitDamage() > 0) || opponentRenderFrames == 0
                    || FRAMES.stream().noneMatch(frame -> frame.get("phase") == Phase.STAGGER)
                    || FRAMES.stream().noneMatch(frame -> frame.get("server") instanceof ServerSample sample && sample.phase == Phase.STAGGER)
                    || (expected == Phase.WINDUP && (clientRelease || serverRelease)))
                throw new IllegalStateException("Incoming hit did not interrupt the requested live phase: " + expected);
        }
        if (ITEM_USE) {
            if (FRAMES.stream().noneMatch(frame -> Boolean.TRUE.equals(frame.get("using_item")))
                    || FRAMES.stream().noneMatch(frame -> frame.get("server") instanceof ServerSample sample && sample.usingItem)
                    || FRAMES.subList(Math.max(0, FRAMES.size() - 20), FRAMES.size()).stream()
                        .anyMatch(frame -> Boolean.TRUE.equals(frame.get("using_item"))
                            || !(frame.get("server") instanceof ServerSample sample) || sample.usingItem))
                throw new IllegalStateException("Item use did not start and stop on client and server");
            if (SCENE.equals("itemuse") && (finalServer == null || finalServer.offhandCount >= 64 || finalServer.food <= 14))
                throw new IllegalStateException("Live food use did not consume food");
            if (SCENE.equals("use-attack") && (!followup || finalServer == null || finalServer.offhandCount != 64))
                throw new IllegalStateException("Attack did not cancel food use before consumption");
            if (SCENE.equals("drinkuse") && (finalServer == null || !finalServer.offhandItem.equals("minecraft:glass_bottle")
                    || mc.player == null || !mc.player.getOffhandItem().is(net.minecraft.world.item.Items.GLASS_BOTTLE)))
                throw new IllegalStateException("Drinking did not return a bottle on client and server");
        }
    }

    private static boolean hasNearbyActiveParryMerge() {
        for (Map<String, Object> merge : ACTIVE_PARRY_MERGES) {
            long mergedAt = ((Number) merge.get("time_ns")).longValue();
            int serial = ((Number) merge.get("serial")).intValue();
            int before = ((Number) merge.get("before")).intValue();
            int after = ((Number) merge.get("after")).intValue();
            int confirmed = ((Number) merge.get("confirmed")).intValue();
            if (after <= before || after <= 0 || confirmed < after) continue;
            for (Map<String, Object> extension : SERVER_ACTIVE_PARRY_EXTENSIONS) {
                if (((Number) extension.get("serial")).intValue() == serial
                        && Math.abs(mergedAt - ((Number) extension.get("time_ns")).longValue())
                            <= MAX_ACTIVE_PARRY_SYNC_DELAY_NANOS) {
                    return true;
                }
            }
        }
        return false;
    }
}
