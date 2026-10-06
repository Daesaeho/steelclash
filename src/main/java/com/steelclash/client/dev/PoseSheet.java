package com.steelclash.client.dev;

import com.mojang.blaze3d.platform.InputConstants;
import com.steelclash.SteelClash;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Dev tool for checking animations without anyone at the keyboard. Started with system properties (see build.gradle:
 * {@code ./gradlew runClient -PposeSheet=slash,overhead,stab -PquickPlay="New World"}):
 * <ul>
 *     <li>{@code steelclash.poseSheet}: attacks to photograph ({@code slash}, {@code slash_mirrored}, {@code overhead},
 *     {@code stab}, {@code parry}, any with {@code _heavy});</li>
 *     <li>{@code steelclash.poseSheetItem}: item id to hold (default iron sword);</li>
 *     <li>{@code steelclash.poseSheetQuit}: close the game when done.</li>
 * </ul>
 * Each pose is frozen on the local player (client-side only) at fixed points of the attack and photographed from
 * behind, in front and in first person with Minecraft's own screenshot code, into {@code screenshots/pose_*.png}.
 */
@EventBusSubscriber(modid = SteelClash.MOD_ID, value = Dist.CLIENT)
public final class PoseSheet {
    private static final String SPEC = System.getProperty("steelclash.poseSheet");
    /** Ticks for the world to settle before the first shot, and per shot for the pose to be rendered. */
    private static final int SETTLE_TICKS = 80;
    private static final int SHOT_TICKS = 3;
    private static final double[][] POINTS = {
            // phase ordinal-ish: 0 windup, 1 release, 2 recovery; progress
            {0, 0.5}, {0, 0.95}, {1, 0.0}, {1, 0.35}, {1, 0.7}, {1, 0.99}, {2, 0.3}};
    private static final CameraType[] VIEWS = {CameraType.THIRD_PERSON_BACK, CameraType.THIRD_PERSON_FRONT, CameraType.FIRST_PERSON};

    private record Shot(AttackType type, boolean heavy, boolean mirrored, Phase phase, double progress, CameraType view, String name) {
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
        String item = System.getProperty("steelclash.poseSheetItem", "minecraft:iron_sword");
        for (String command : new String[]{
                "gamerule doDaylightCycle false", "gamerule doWeatherCycle false", "time set noon", "weather clear",
                "fill ~-3 300 ~-3 ~3 300 ~3 minecraft:polished_andesite", "tp @s ~ 301 ~ 0 0",
                "item replace entity @s weapon.mainhand with " + item}) {
            player.connection.sendCommand(command);
        }
    }

    private static List<Shot> plan() {
        List<Shot> out = new ArrayList<>();
        for (String raw : SPEC.split(",")) {
            String entry = raw.trim().toLowerCase(Locale.ROOT);
            if (entry.isEmpty()) {
                continue;
            }
            boolean heavy = entry.contains("_heavy");
            boolean mirrored = entry.contains("_mirrored");
            String base = entry.replace("_heavy", "").replace("_mirrored", "");
            for (CameraType view : VIEWS) {
                if (base.equals("parry")) {
                    out.add(new Shot(AttackType.SLASH, false, false, Phase.PARRY, 1.0, view, "parry_" + viewName(view)));
                    continue;
                }
                AttackType type = AttackType.valueOf(base.toUpperCase(Locale.ROOT));
                for (double[] point : POINTS) {
                    Phase phase = point[0] == 0 ? Phase.WINDUP : point[0] == 1 ? Phase.RELEASE : Phase.RECOVERY;
                    String name = String.format(Locale.ROOT, "%s_%s_%03d_%s", entry, phase.name().toLowerCase(Locale.ROOT),
                            Math.round(point[1] * 100), viewName(view));
                    out.add(new Shot(type, heavy, mirrored, phase, point[1], view, name));
                }
            }
        }
        return out;
    }

    private static String viewName(CameraType view) {
        return switch (view) {
            case FIRST_PERSON -> "first";
            case THIRD_PERSON_BACK -> "back";
            case THIRD_PERSON_FRONT -> "front";
        };
    }

    private static void setUp(Minecraft mc, LocalPlayer player, Shot shot) {
        String itemId = System.getProperty("steelclash.poseSheetItem", "minecraft:iron_sword");
        ItemStack item = new ItemStack(BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(itemId)).orElse(Items.IRON_SWORD));
        if (!ItemStack.isSameItem(player.getMainHandItem(), item)) {
            player.getInventory().setItem(player.getInventory().selected, item); // client-side only: just for the picture
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
        CombatData data = player.getData(ModAttachments.COMBAT);
        Optional<WeaponProfiles.Resolved> resolved = WeaponProfiles.resolve(player.getMainHandItem(), player.level().registryAccess());
        if (resolved.isEmpty()) {
            return;
        }
        data.profileKey = resolved.get().key();
        WeaponProfile profile = resolved.get().profile();
        Optional<WeaponProfile.AttackSpec> spec = profile.attack(shot.type());
        if (spec.isEmpty()) {
            return;
        }
        int windup = spec.get().windup();
        if (shot.heavy()) {
            windup = Math.round(windup * profile.heavy().windupMult());
        }
        AttackTimings timings = new AttackTimings(windup, spec.get().release(), spec.get().recovery());
        int duration = switch (shot.phase()) {
            case WINDUP -> timings.windup();
            case RELEASE -> timings.release();
            case RECOVERY -> timings.recovery();
            default -> 20;
        };
        int tick = (int) Math.min(duration - 1, Math.round(shot.progress() * duration));
        data.machine.apply(shot.phase(), shot.type(), Math.max(0, tick), duration, timings, 0, shot.heavy(), false, false, 0,
                shot.mirrored());
    }

    private static void capture(Minecraft mc, Shot shot) {
        Screenshot.grab(mc.gameDirectory, "pose_" + shot.name() + ".png", mc.getMainRenderTarget(), message -> {
        });
    }

    private static void finish(Minecraft mc, LocalPlayer player) {
        mc.options.hideGui = false;
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        player.getData(ModAttachments.COMBAT).machine.apply(Phase.IDLE, AttackType.SLASH, 0, 0, new AttackTimings(1, 1, 1), 0,
                false, false, false, 0, false);
        SteelClash.LOGGER.info("Pose sheet: done, {} screenshots in {}", shots.size(), new File(mc.gameDirectory, "screenshots"));
        if (System.getProperty("steelclash.poseSheetQuit") != null) {
            mc.stop();
        }
    }
}
