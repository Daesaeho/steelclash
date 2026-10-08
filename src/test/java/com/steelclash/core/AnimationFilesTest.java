package com.steelclash.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.HashSet;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Every shipped animation file parses and covers every phase it can be asked for. */
class AnimationFilesTest {
    private static final Path DIR = Path.of("src/main/resources/assets/steelclash/steelclash_animations");

    @Test
    void allShippedAnimationsParseAndAreComplete() throws IOException {
        List<Path> files;
        try (Stream<Path> stream = Files.list(DIR)) {
            files = stream.filter(p -> p.toString().endsWith(".json")).toList();
        }
        assertFalse(files.isEmpty());
        for (Path file : files) {
            AnimationSet animation;
            try (Reader reader = Files.newBufferedReader(file)) {
                animation = AnimationSet.parse(JsonParser.parseReader(reader).getAsJsonObject());
            }
            for (AttackType type : AttackType.values()) {
                for (Phase phase : new Phase[]{Phase.WINDUP, Phase.RELEASE, Phase.RECOVERY}) {
                    String key = AnimationSet.clipKey(phase, type);
                    assertFalse(animation.clip(key).isEmpty(), file.getFileName() + " is missing " + key);
                }
            }
            for (Phase phase : new Phase[]{Phase.PARRY, Phase.GUARD_RECOVERY, Phase.STAGGER}) {
                assertFalse(animation.clip(AnimationSet.clipKey(phase, AttackType.SLASH)).isEmpty(),
                        file.getFileName() + " is missing " + phase);
            }
            for (AttackType type : AttackType.WEAPON_ATTACKS) {
                assertFalse(animation.clip(type.serializedName() + ".heavy_windup").isEmpty(),
                        file.getFileName() + " is missing " + type.serializedName() + ".heavy_windup");
                assertPoseEquals(animation.clip(AnimationSet.heavyWindupKey(type)).sample(1),
                        animation.clip(AnimationSet.clipKey(Phase.RELEASE, type)).sample(0), file + ": " + type + " heavy seam");
            }
            for (AttackType type : AttackType.values()) {
                assertPoseEquals(animation.clip(AnimationSet.clipKey(Phase.WINDUP, type)).sample(1),
                        animation.clip(AnimationSet.clipKey(Phase.RELEASE, type)).sample(0), file + ": " + type + " windup seam");
                assertPoseEquals(animation.clip(AnimationSet.clipKey(Phase.RELEASE, type)).sample(1),
                        animation.clip(AnimationSet.clipKey(Phase.RECOVERY, type)).sample(0), file + ": " + type + " recovery seam");
            }
            if (file.getFileName().toString().equals("default.json")) {
                for (String action : List.of("bash", "special.lunge", "special.slam", "special.sweep")) {
                    for (String phase : List.of("windup", "release", "recovery")) {
                        assertFalse(animation.clip(action + "." + phase).isEmpty(), action + "." + phase);
                    }
                    assertPoseEquals(animation.clip(action + ".windup").sample(1),
                            animation.clip(action + ".release").sample(0), action + " windup seam");
                    assertPoseEquals(animation.clip(action + ".release").sample(1),
                            animation.clip(action + ".recovery").sample(0), action + " recovery seam");
                }
            }
            assertTrue(animation.gripGap() > 0 && animation.gripGap() <= 10, file.getFileName() + ": grip gap " + animation.gripGap());
            if (file.getFileName().toString().equals("staff.json")) {
                assertTrue(animation.gripGap() > AnimationSet.DEFAULT_GRIP_GAP, "a staff is held with the hands wide apart");
            }
            if (file.getFileName().toString().equals("two_handed.json")) {
                assertTrue(animation.twoHanded(), "two_handed grips with both hands");
            }
        }
    }

    private static void assertPoseEquals(Map<String, double[]> from, Map<String, double[]> to, String message) {
        var channels = new HashSet<>(from.keySet());
        channels.addAll(to.keySet());
        for (String channel : channels) {
            assertArrayEquals(from.getOrDefault(channel, new double[3]), to.getOrDefault(channel, new double[3]), 1e-9,
                    message + " " + channel);
        }
    }
}
