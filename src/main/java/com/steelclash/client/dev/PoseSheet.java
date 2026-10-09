package com.steelclash.client.dev;

import com.mojang.blaze3d.platform.InputConstants;
import com.steelclash.SteelClash;
import com.steelclash.client.CombatDebugRenderer;
import com.steelclash.client.ProceduralSwingAnimation;
import com.steelclash.client.anim.CombatPose;
import com.steelclash.client.anim.CombatPresentation;
import com.steelclash.combat.CombatData;
import com.steelclash.combat.ModAttachments;
import com.steelclash.core.AttackTimings;
import com.steelclash.core.AttackType;
import com.steelclash.core.Phase;
import com.steelclash.profile.WeaponProfile;
import com.steelclash.profile.WeaponProfiles;
import com.steelclash.profile.Kicks;
import com.steelclash.profile.Jabs;
import com.steelclash.profile.Throws;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.TreeSet;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Dev tool for checking animations without anyone at the keyboard. Started with system properties (see build.gradle:
 * {@code ./gradlew runClient -PposeSheet=slash,overhead,stab -PquickPlay="New World"}):
 * <ul>
 *     <li>{@code steelclash.poseSheet}: attacks to photograph ({@code slash}, {@code slash_mirrored}, {@code overhead},
 *     {@code stab}, {@code parry}, any with {@code _heavy}), or {@code idle} (holding the weapon, no attack), or
 *     {@code ready:<yaw>:<pitch>} (idle, with the first-person ready stance at that aim, for tuning it);</li>
 *     <li>{@code steelclash.poseSheetPoints}: the points of each attack to photograph instead of the default ones, as
 *     {@code phase:progress} pairs ({@code windup:0,windup:0.1,recovery:0.95});</li>
 *     <li>{@code steelclash.poseSheetItem}: item ids to hold, comma-separated (default iron sword); with several,
 *     every attack is shot with each and the file names start with the item's path ({@code pose_iron_axe_slash_...});</li>
 *     <li>{@code steelclash.poseSheetDebug}: draw the traced blade too ({@code /steelclash_debug}), to check that the
 *     rendered weapon lies on it;</li>
 *     <li>{@code steelclash.poseSheetMob}: photograph a mob of this type instead (e.g. {@code minecraft:zombie}),
 *     summoned without AI three blocks in front of the player holding the item, from the front and the side in first
 *     person (file names {@code ..._mobfront}, {@code ..._mobside});</li>
 *     <li>{@code steelclash.poseSheetQuit}: close the game when done.</li>
 * </ul>
 * Each pose is frozen on the local player (client-side only) at fixed points of the attack and photographed from
 * behind, in front and in first person with Minecraft's own screenshot code, into {@code screenshots/pose_*.png}.
 */
@EventBusSubscriber(modid = SteelClash.MOD_ID, value = Dist.CLIENT)
public final class PoseSheet {
    private static final String SPEC = System.getProperty("steelclash.poseSheet");
    private static final String MOB = System.getProperty("steelclash.poseSheetMob");
    /** Mob shots: the mob's yaw (the player looks along +Z): facing the camera, then side-on. */
    private static final float[] MOB_YAWS = {180, 90};
    /** Ticks for the world to settle before the first shot, and per shot for the pose to be rendered. */
    private static final int SETTLE_TICKS = 80;
    private static final int SHOT_TICKS = 3;
    private static final double[][] DEFAULT_POINTS = {
            // phase ordinal-ish: 0 windup, 1 release, 2 recovery; progress
            {0, 0.5}, {0, 0.95}, {1, 0.0}, {1, 0.35}, {1, 0.7}, {1, 0.99}, {2, 0.3}};
    private static final CameraType[] VIEWS = {CameraType.THIRD_PERSON_BACK, CameraType.THIRD_PERSON_FRONT, CameraType.FIRST_PERSON};

    /** @param mobYaw the photographed mob's yaw, or null to photograph the player */
    private record Shot(String item, AttackType type, boolean heavy, boolean mirrored, Phase phase, double progress, CameraType view,
                        String name, @Nullable Float mobYaw) {
    }

    private static List<Shot> shots;
    private static int index = -1;
    private static int wait;
    private static boolean stagedDowned;
    @Nullable
    private static CombatPose renderedPose;
    private static int renderedIndex = -1;
    @Nullable
    private static CameraType renderedView;
    @Nullable
    private static ResourceLocation renderedItem;

    private PoseSheet() {
    }

    /** While running, the local player's animation uses partial tick 0, so each frozen pose renders exactly. */
    /** While a {@code ready:<yaw>:<pitch>} shot is up: the ready stance's aim to use instead of the built-in one. */
    @Nullable
    private static double[] readyOverride;

    @Nullable
    public static double[] readyOverride() {
        return running() ? readyOverride : null;
    }

    public static boolean running() {
        return shots != null && index >= 0 && index < shots.size();
    }

    public static boolean photographingMob() {
        return MOB != null && running();
    }

    /** The actual player-layer sample, retained until the frozen screenshot is read back. */
    public static void recordRenderedPose(LivingEntity entity, @Nullable CombatPose pose) {
        if (!running()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && entity == subject(mc.player, shots.get(index))) {
            renderedPose = pose;
            renderedIndex = index;
            renderedView = mc.options.getCameraType();
            renderedItem = BuiltInRegistries.ITEM.getKey(entity.getMainHandItem().getItem());
        }
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        if (SPEC == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || (shots != null && index >= shots.size())) {
            return;
        }
        if (shots == null) {
            shots = plan();
            wait = SETTLE_TICKS;
            mc.options.pauseOnLostFocus = false;
            CombatDebugRenderer.setEnabled(System.getProperty("steelclash.poseSheetDebug") != null);
            buildStage(player);
            SteelClash.LOGGER.info("Pose sheet: {} shots planned", shots.size());
        }
        if (mc.screen != null) {
            mc.setScreen(null); // pause menu, chat, death screen: not in the picture
        }
        if (index >= 0) {
            hold(player, shots.get(index));
        }
        if (wait-- > 0) {
            return;
        }
        if (index >= 0) {
            capture(mc, shots.get(index));
        }
        index++;
        if (index >= shots.size()) {
            finish(mc, player);
            return;
        }
        setUp(mc, player, shots.get(index));
        hold(player, shots.get(index));
        wait = SHOT_TICKS;
    }

    /**
     * A clean backdrop: a small platform high in the sky at noon, so poses read as silhouettes against the sky and the
     * third-person camera never bumps into terrain. Needs a world with cheats on (the dev world has them).
     */
    private static void buildStage(LocalPlayer player) {
        String item = items().get(0);
        for (String command : new String[]{
                "gamerule doDaylightCycle false", "gamerule doWeatherCycle false", "time set noon", "weather clear",
                "effect clear @s",
                "fill ~-3 300 ~-3 ~3 300 ~3 minecraft:polished_andesite", "tp @s ~ 301 ~ 0 0",
                "item replace entity @s weapon.mainhand with " + item}) {
            player.connection.sendCommand(command);
        }
        // A mob run's model is still standing there: clear it, or player shots photograph it too.
        player.connection.sendCommand("kill @e[type=!player,distance=..12]");
        if (MOB != null) {
            player.connection.sendCommand("summon " + MOB + " ~ 301 ~3 {NoAI:1b,Silent:1b,PersistenceRequired:1b,"
                    + "Invulnerable:1b,IsBaby:0b,HandItems:[{id:\"" + item + "\",count:1},{}]}");
        }
    }

    private static List<String> items() {
        List<String> out = new ArrayList<>();
        for (String raw : System.getProperty("steelclash.poseSheetItem", "minecraft:iron_sword").split(",")) {
            if (!raw.isBlank()) {
                out.add(raw.trim());
            }
        }
        return out.isEmpty() ? List.of("minecraft:iron_sword") : out;
    }

    private static List<Shot> plan() {
        List<Shot> out = new ArrayList<>();
        List<String> items = items();
        for (String item : items) {
            String prefix = items.size() > 1 ? ResourceLocation.parse(item).getPath() + "_" : "";
            plan(out, item, prefix);
        }
        return out;
    }

    private static void plan(List<Shot> out, String item, String prefix) {
        for (String raw : SPEC.split(",")) {
            String entry = raw.trim().toLowerCase(Locale.ROOT);
            if (entry.isEmpty()) {
                continue;
            }
            boolean heavy = entry.contains("_heavy");
            boolean mirrored = entry.contains("_mirrored");
            String base = entry.replace("_heavy", "").replace("_mirrored", "");
            for (int v = 0; v < (MOB != null ? MOB_YAWS.length : VIEWS.length); v++) {
                CameraType view = MOB != null ? CameraType.FIRST_PERSON : VIEWS[v];
                Float mobYaw = MOB != null ? MOB_YAWS[v] : null;
                String viewName = MOB != null ? (v == 0 ? "mobfront" : "mobside") : viewName(view);
                if (base.startsWith("ready:")) {
                    out.add(new Shot(item, AttackType.SLASH, false, false, Phase.IDLE, 0, view,
                            prefix + base.replace(':', '_') + "_" + viewName, mobYaw));
                    continue;
                }
                if (base.equals("idle") || base.equals("downed")) {
                    if (base.equals("downed") && MOB != null) {
                        throw new IllegalArgumentException("Pose sheet: downed is a player-only scene");
                    }
                    out.add(new Shot(item, AttackType.SLASH, false, false, Phase.IDLE, 0, view, prefix + base + "_" + viewName, mobYaw));
                    continue;
                }
                if (base.equals("parry")) {
                    out.add(new Shot(item, AttackType.SLASH, false, false, Phase.PARRY, 1.0, view, prefix + "parry_" + viewName, mobYaw));
                    continue;
                }
                AttackType type = AttackType.valueOf(base.toUpperCase(Locale.ROOT));
                for (double[] point : points()) {
                    Phase phase = point[0] == 0 ? Phase.WINDUP : point[0] == 1 ? Phase.RELEASE : Phase.RECOVERY;
                    String name = prefix + String.format(Locale.ROOT, "%s_%s_%03d_%s", entry, phase.name().toLowerCase(Locale.ROOT),
                            Math.round(point[1] * 100), viewName);
                    out.add(new Shot(item, type, heavy, mirrored, phase, point[1], view, name, mobYaw));
                }
            }
        }
    }

    /** Phase (0 windup, 1 release, 2 recovery) and progress of each shot of an attack. */
    private static double[][] points() {
        String custom = System.getProperty("steelclash.poseSheetPoints");
        if (custom == null || custom.isBlank()) {
            return DEFAULT_POINTS;
        }
        List<double[]> out = new ArrayList<>();
        for (String pair : custom.split(",")) {
            String[] parts = pair.trim().split(":");
            int phase = switch (parts[0].trim().toLowerCase(Locale.ROOT)) {
                case "windup" -> 0;
                case "release" -> 1;
                default -> 2;
            };
            out.add(new double[]{phase, Double.parseDouble(parts[1].trim())});
        }
        return out.toArray(new double[0][]);
    }

    private static String viewName(CameraType view) {
        return switch (view) {
            case FIRST_PERSON -> "first";
            case THIRD_PERSON_BACK -> "back";
            case THIRD_PERSON_FRONT -> "front";
        };
    }

    private static void setUp(Minecraft mc, LocalPlayer player, Shot shot) {
        ItemStack item = new ItemStack(BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(shot.item()))
                .orElseThrow(() -> new IllegalArgumentException("Pose sheet: unknown item " + shot.item())));
        LivingEntity subject = subject(player, shot);
        if (!ItemStack.isSameItem(subject.getMainHandItem(), item)) {
            subject.setItemSlot(EquipmentSlot.MAINHAND, item); // client-side only: just for the picture
        }
        // Hiding the GUI (F1) also hides vanilla's first-person hand, which is what an idle shot is for.
        mc.options.hideGui = MOB != null || shot.phase() != Phase.IDLE || shot.name().contains("ready_");
        mc.options.setCameraType(shot.view());
        int at = shot.name().indexOf("ready_"); // after the item prefix, if several items are shot
        String[] ready = at >= 0 ? shot.name().substring(at).split("_") : null;
        readyOverride = ready != null ? new double[]{Double.parseDouble(ready[1]), Double.parseDouble(ready[2])} : null;
    }

    /** Pins the pose every tick so the client's own combat tick can't move it on. */
    private static void hold(LocalPlayer player, Shot shot) {
        player.setYRot(0);
        player.setXRot(0);
        player.yRotO = 0;
        player.xRotO = 0;
        player.setYHeadRot(0);
        player.yHeadRotO = 0;
        player.yBodyRot = 0;
        player.yBodyRotO = 0;
        LivingEntity subject = subject(player, shot);
        if (shot.mobYaw() != null) {
            if (subject instanceof Mob mob) {
                mob.setAggressive(true); // as a fighter is in game: illagers only show their arms and weapon then
            }
            float yaw = shot.mobYaw();
            subject.setYRot(yaw);
            subject.yRotO = yaw;
            subject.setYHeadRot(yaw);
            subject.yHeadRotO = yaw;
            subject.yBodyRot = yaw;
            subject.yBodyRotO = yaw;
            subject.setXRot(0);
            subject.xRotO = 0;
        }
        CombatData data = subject.getData(ModAttachments.COMBAT);
        boolean downedShot = shot.name().startsWith("downed_") || shot.name().contains("_downed_");
        if (downedShot) {
            data.downedTicksLeft = 200; // the client state a DownedPayload supplies, without changing server gameplay
            player.setForcedPose(Pose.SWIMMING);
            stagedDowned = true;
        } else if (stagedDowned) {
            data.downedTicksLeft = -1;
            player.setForcedPose(null);
            stagedDowned = false;
        }
        Optional<WeaponProfiles.Resolved> resolved = WeaponProfiles.resolve(subject.getMainHandItem(), player.level().registryAccess());
        data.profileKey = resolved.map(WeaponProfiles.Resolved::key).orElse(null);
        Optional<WeaponProfile.AttackSpec> spec = switch (shot.type()) {
            case KICK -> Optional.of(Kicks.forEntity(subject));
            case JAB -> Optional.of(Jabs.spec());
            case THROW -> Optional.of(Throws.spec());
            default -> resolved.flatMap(r -> r.profile().spec(shot.type()));
        };
        if (spec.isEmpty()) {
            data.machine.cancel();
            throw new IllegalStateException("Pose sheet: " + shot.item() + " has no " + shot.type() + " spec for " + shot.name());
        }
        AttackTimings timings = spec.get().timings();
        if (shot.heavy()) {
            if (!shot.type().isWeaponAttack() || resolved.isEmpty()) {
                data.machine.cancel();
                throw new IllegalArgumentException("Pose sheet: heavy is unsupported for " + shot.name());
            }
            WeaponProfile profile = resolved.get().profile();
            timings = timings.withWindupUs(profile.heavy().windupUs(timings.windupUs(), 1))
                    .withRecoveryUs(profile.heavy().recoveryUs(timings.recoveryUs(), 1));
        }
        long duration = switch (shot.phase()) {
            case WINDUP -> timings.windupUs();
            case RELEASE -> timings.releaseUs();
            case RECOVERY -> timings.recoveryUs();
            default -> 20L * AttackTimings.TICK_US;
        };
        long elapsed = Math.min(duration - 1, Math.round(shot.progress() * duration));
        data.machine.apply(shot.phase(), shot.type(), Math.max(0, elapsed), duration, timings, 0, shot.heavy(), false, false, 0,
                shot.mirrored());
        if (downedShot) {
            CombatPresentation.get(player, 0); // discard any standing-pose tail, as normal downed rendering does
            if (CombatPose.ready(player, 0).isPresent()) {
                throw new IllegalStateException("DownedReadyScene: a downed player still has a combat-ready pose");
            }
            if (new ProceduralSwingAnimation(player).isActive()) {
                throw new IllegalStateException("DownedReadyScene: the ready animation still activates while downed");
            }
        }
    }

    /** The player or the requested nearby mob type. A missing mob must not produce a substitute player image. */
    private static LivingEntity subject(LocalPlayer player, Shot shot) {
        if (shot.mobYaw() == null) {
            return player;
        }
        List<Mob> mobs = player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(6));
        var requestedType = BuiltInRegistries.ENTITY_TYPE.getOptional(ResourceLocation.parse(MOB))
                .orElseThrow(() -> new IllegalArgumentException("Pose sheet: unknown mob " + MOB));
        return mobs.stream().filter(m -> m.getType() == requestedType)
                .min(java.util.Comparator.comparingDouble(player::distanceToSqr)).<LivingEntity>map(m -> m)
                .orElseThrow(() -> new IllegalStateException("Pose sheet: requested mob " + MOB + " is missing for " + shot.name()));
    }

    private static void capture(Minecraft mc, Shot shot) {
        if (Boolean.getBoolean("steelclash.poseSheetInspectModel")) {
            LivingEntity entity = subject(mc.player, shot);
            ItemStack held = entity.getMainHandItem();
            var model = mc.getItemRenderer().getModel(held, entity.level(), entity, entity.getId());
            var sprites = new TreeSet<String>();
            for (var pass : model.getRenderPasses(held, false)) {
                for (var quad : pass.getQuads(null, null, RandomSource.create(0))) {
                    sprites.add(quad.getSprite().contents().name().toString());
                }
            }
            SteelClash.LOGGER.info("Pose sheet model: {} held={} model={} sprites={}", shot.name(),
                    BuiltInRegistries.ITEM.getKey(held.getItem()), model.getClass().getName(), sprites);
        }
        if (shot.phase().isAttack()) {
            double duration = subject(mc.player, shot).getData(ModAttachments.COMBAT).machine.phaseDurationUs();
            double expected = Math.min(duration - 1, Math.round(shot.progress() * duration)) / duration;
            double actual = renderedPose == null || renderedPose.swing() == null ? Double.NaN : renderedPose.swing().progress();
            SteelClash.LOGGER.info("Pose sheet sample: {} requested={}:{} rendered={}:{} index={}/{} view={}/{} item={}/{}",
                    shot.name(), shot.phase(), shot.progress(), renderedPose == null ? null : renderedPose.phase(),
                    renderedPose == null || renderedPose.swing() == null ? null : renderedPose.swing().progress(),
                    renderedIndex, index, renderedView, shot.view(), renderedItem, shot.item());
            if (renderedIndex != index || renderedView != shot.view() || renderedPose == null
                    || renderedPose.phase() != shot.phase() || !Double.isFinite(actual)
                    || Math.abs(actual - expected) > 1.0 / Math.max(1, duration)
                    || !ResourceLocation.parse(shot.item()).equals(renderedItem)) {
                throw new IllegalStateException("Pose sheet: model sample does not match requested shot " + shot.name());
            }
        }
        Screenshot.grab(mc.gameDirectory, "pose_" + shot.name() + ".png", mc.getMainRenderTarget(), message -> {
        });
    }

    private static void finish(Minecraft mc, LocalPlayer player) {
        if (stagedDowned) {
            player.getData(ModAttachments.COMBAT).downedTicksLeft = -1;
            player.setForcedPose(null);
            stagedDowned = false;
            SteelClash.LOGGER.info("Pose sheet: downed ready-pose and layer-activation checks passed");
        }
        mc.options.hideGui = false;
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        player.getData(ModAttachments.COMBAT).machine.apply(Phase.IDLE, AttackType.SLASH, 0, 0, AttackTimings.ofTicks(1, 1, 1), 0,
                false, false, false, 0, false);
        SteelClash.LOGGER.info("Pose sheet: done, {} screenshots in {}", shots.size(), new File(mc.gameDirectory, "screenshots"));
        if (System.getProperty("steelclash.poseSheetQuit") != null) {
            mc.stop();
        }
    }
}
