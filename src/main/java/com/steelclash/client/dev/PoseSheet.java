package com.steelclash.client.dev;

import com.mojang.blaze3d.platform.InputConstants;
import com.steelclash.SteelClash;
import com.steelclash.client.CombatDebugRenderer;
import com.steelclash.combat.CombatData;
import com.steelclash.combat.ModAttachments;
import com.steelclash.core.AttackTimings;
import com.steelclash.core.AttackType;
import com.steelclash.core.Phase;
import com.steelclash.profile.WeaponProfile;
import com.steelclash.profile.WeaponProfiles;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
 *     {@code stab}, {@code parry}, any with {@code _heavy});</li>
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
    private static final double[][] POINTS = {
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

    private PoseSheet() {
    }

    /** While running, the local player's animation uses partial tick 0, so each frozen pose renders exactly. */
    public static boolean running() {
        return shots != null && index >= 0 && index < shots.size();
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
                "fill ~-3 300 ~-3 ~3 300 ~3 minecraft:polished_andesite", "tp @s ~ 301 ~ 0 0",
                "item replace entity @s weapon.mainhand with " + item}) {
            player.connection.sendCommand(command);
        }
        if (MOB != null) {
            player.connection.sendCommand("kill @e[type=!player,distance=..12]"); // last run's model, still standing there
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
                if (base.equals("parry")) {
                    out.add(new Shot(item, AttackType.SLASH, false, false, Phase.PARRY, 1.0, view, prefix + "parry_" + viewName, mobYaw));
                    continue;
                }
                AttackType type = AttackType.valueOf(base.toUpperCase(Locale.ROOT));
                for (double[] point : POINTS) {
                    Phase phase = point[0] == 0 ? Phase.WINDUP : point[0] == 1 ? Phase.RELEASE : Phase.RECOVERY;
                    String name = prefix + String.format(Locale.ROOT, "%s_%s_%03d_%s", entry, phase.name().toLowerCase(Locale.ROOT),
                            Math.round(point[1] * 100), viewName);
                    out.add(new Shot(item, type, heavy, mirrored, phase, point[1], view, name, mobYaw));
                }
            }
        }
    }

    private static String viewName(CameraType view) {
        return switch (view) {
            case FIRST_PERSON -> "first";
            case THIRD_PERSON_BACK -> "back";
            case THIRD_PERSON_FRONT -> "front";
        };
    }

    private static void setUp(Minecraft mc, LocalPlayer player, Shot shot) {
        ItemStack item = new ItemStack(BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(shot.item())).orElseGet(() -> {
            SteelClash.LOGGER.warn("Pose sheet: unknown item {}, using an iron sword", shot.item());
            return Items.IRON_SWORD;
        }));
        LivingEntity subject = subject(player, shot);
        if (!ItemStack.isSameItem(subject.getMainHandItem(), item)) {
            subject.setItemSlot(EquipmentSlot.MAINHAND, item); // client-side only: just for the picture
        }
        mc.options.hideGui = true;
        mc.options.setCameraType(shot.view());
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
        Optional<WeaponProfiles.Resolved> resolved = WeaponProfiles.resolve(subject.getMainHandItem(), player.level().registryAccess());
        if (resolved.isEmpty()) {
            return;
        }
        data.profileKey = resolved.get().key();
        WeaponProfile profile = resolved.get().profile();
        Optional<WeaponProfile.AttackSpec> spec = profile.attack(shot.type());
        if (spec.isEmpty()) {
            return;
        }
        AttackTimings timings = spec.get().timings();
        if (shot.heavy()) {
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
    }

    /** Who is being photographed: the player, or the nearest mob for mob shots (the player if it isn't there yet). */
    private static LivingEntity subject(LocalPlayer player, Shot shot) {
        if (shot.mobYaw() == null) {
            return player;
        }
        List<Mob> mobs = player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(6));
        return mobs.stream().min(java.util.Comparator.comparingDouble(player::distanceToSqr)).<LivingEntity>map(m -> m).orElse(player);
    }

    private static void capture(Minecraft mc, Shot shot) {
        Screenshot.grab(mc.gameDirectory, "pose_" + shot.name() + ".png", mc.getMainRenderTarget(), message -> {
        });
    }

    private static void finish(Minecraft mc, LocalPlayer player) {
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
