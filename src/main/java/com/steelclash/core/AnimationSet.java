package com.steelclash.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

/**
 * One archetype's pose clips (see {@code assets/steelclash/steelclash_animations/*.json}).
 *
 * @param twoHanded        the off hand grips the weapon too
 * @param heavyWindupScale how much more exaggerated a heavy's windup pose is
 * @param gripGap          two-handed grip: pixels between the hands along the weapon (wide for staves and polearms)
 */
public record AnimationSet(boolean twoHanded, double heavyWindupScale, double gripGap, Map<String, PoseClip> clips,
                           FirstPerson firstPerson) {
    public static final double DEFAULT_GRIP_GAP = 2.5;
    public static final AnimationSet NONE = new AnimationSet(false, 1, DEFAULT_GRIP_GAP, Map.of());
    private static final Set<String> PARTS = Set.of("body", "torso", "head", "rightArm", "leftArm", "rightLeg", "leftLeg",
            "rightArmBend", "leftArmBend");
    private static final String[][] CLIP_KEYS = createClipKeys();
    private static final String[] HEAVY_KEYS = java.util.Arrays.stream(AttackType.values())
            .map(t -> t.serializedName() + ".heavy_windup").toArray(String[]::new);

    /** Pixels in model space; retraction is first-person-only, so rigid third-person shoulders stay attached. */
    public record FirstPerson(double forward, double down, double retraction) {
        public static final FirstPerson DEFAULT = new FirstPerson(4, 1.5, 3);
    }

    public AnimationSet(boolean twoHanded, double heavyWindupScale, double gripGap, Map<String, PoseClip> clips) {
        this(twoHanded, heavyWindupScale, gripGap, clips, FirstPerson.DEFAULT);
    }

    public AnimationSet {
        clips = Map.copyOf(clips);
    }

    public PoseClip clip(String key) {
        return clips.getOrDefault(key, PoseClip.EMPTY);
    }

    /** An explicit empty clip disables the action; an absent specialized key inherits its generic action. */
    public PoseClip clip(String key, String genericKey) {
        return clips.getOrDefault(key, clip(genericKey));
    }

    public AnimationSet withFallback(AnimationSet fallback) {
        Map<String, PoseClip> inherited = new HashMap<>(fallback.clips);
        inherited.putAll(clips);
        return new AnimationSet(twoHanded, heavyWindupScale, gripGap, inherited, firstPerson);
    }

    /** Parses the JSON format: {@code {"grip": "two_handed", "grip_gap": 7, "heavy_windup_scale": 1.35, "clips": {"slash.windup": [{"t": 0, "body": [x, y, z]}, ...]}}}. */
    public static AnimationSet parse(JsonObject json) {
        if (json.has("format_version") && json.get("format_version").getAsDouble() != 1) {
            throw new IllegalArgumentException("format_version must be 1");
        }
        String grip = json.has("grip") ? json.get("grip").getAsString() : "one_handed";
        if (!grip.equals("one_handed") && !grip.equals("two_handed")) {
            throw new IllegalArgumentException("grip must be one_handed or two_handed");
        }
        boolean twoHanded = grip.equals("two_handed");
        double heavyScale = json.has("heavy_windup_scale") ? json.get("heavy_windup_scale").getAsDouble() : 1.35;
        double gripGap = json.has("grip_gap") ? json.get("grip_gap").getAsDouble() : DEFAULT_GRIP_GAP;
        bounded("heavy_windup_scale", heavyScale, 0, 5);
        bounded("grip_gap", gripGap, 0.01, 10);
        JsonObject firstPersonJson = json.getAsJsonObject("first_person");
        FirstPerson firstPerson = FirstPerson.DEFAULT;
        if (firstPersonJson != null) {
            firstPerson = new FirstPerson(number(firstPersonJson, "forward", 4, -16, 16),
                    number(firstPersonJson, "down", 1.5, -16, 16), number(firstPersonJson, "retraction", 3, 0, 8));
        }
        Map<String, PoseClip> clips = new HashMap<>();
        JsonObject clipsJson = json.getAsJsonObject("clips");
        if (clipsJson != null) {
            for (Map.Entry<String, JsonElement> clip : clipsJson.entrySet()) {
                List<PoseClip.Keyframe> keyframes = new ArrayList<>();
                Set<Double> times = new HashSet<>();
                for (JsonElement kfElement : clip.getValue().getAsJsonArray()) {
                    JsonObject kf = kfElement.getAsJsonObject();
                    double t = kf.has("t") ? kf.get("t").getAsDouble() : 0;
                    bounded(clip.getKey() + ".t", t, 0, 1);
                    if (!times.add(t == 0 ? 0.0 : t)) {
                        throw new IllegalArgumentException(clip.getKey() + ": duplicate keyframe time " + t);
                    }
                    Map<String, double[]> parts = new HashMap<>();
                    for (Map.Entry<String, JsonElement> part : kf.entrySet()) {
                        if (!part.getKey().equals("t")) {
                            if (!PARTS.contains(part.getKey())) {
                                throw new IllegalArgumentException(clip.getKey() + ": unknown channel " + part.getKey());
                            }
                            if (!part.getValue().isJsonArray()) {
                                throw new IllegalArgumentException(clip.getKey() + "." + part.getKey() + ": expected an axes array");
                            }
                            JsonArray a = part.getValue().getAsJsonArray();
                            if (a.size() == 0 || a.size() > 3) {
                                throw new IllegalArgumentException(clip.getKey() + "." + part.getKey() + ": expected 1–3 axes");
                            }
                            double[] axes = {at(a, 0), at(a, 1), at(a, 2)};
                            for (double axis : axes) {
                                if (!Double.isFinite(axis)) {
                                    throw new IllegalArgumentException(clip.getKey() + "." + part.getKey() + ": non-finite angle");
                                }
                            }
                            parts.put(part.getKey(), axes);
                        }
                    }
                    keyframes.add(new PoseClip.Keyframe(t, parts));
                }
                clips.put(clip.getKey(), new PoseClip(keyframes));
            }
        }
        return new AnimationSet(twoHanded, heavyScale, gripGap, clips, firstPerson);
    }

    /** Clip key for a phase: "slash.windup", "kick.release", "parry", "stagger", ... */
    public static String clipKey(Phase phase, AttackType type) {
        return CLIP_KEYS[phase.ordinal()][type.ordinal()];
    }

    public static String heavyWindupKey(AttackType type) { return HEAVY_KEYS[type.ordinal()]; }

    private static String[][] createClipKeys() {
        String[][] keys = new String[Phase.values().length][AttackType.values().length];
        for (Phase phase : Phase.values()) {
            for (AttackType type : AttackType.values()) {
                keys[phase.ordinal()][type.ordinal()] = switch (phase) {
                    case WINDUP, RELEASE, RECOVERY -> type.serializedName() + "." + phase.name().toLowerCase(java.util.Locale.ROOT);
                    case PARRY -> "parry";
                    case GUARD_RECOVERY -> "guard_recovery";
                    case STAGGER -> "stagger";
                    case IDLE -> "";
                };
            }
        }
        return keys;
    }

    private static double number(JsonObject json, String key, double fallback, double min, double max) {
        double value = json.has(key) ? json.get(key).getAsDouble() : fallback;
        bounded("first_person." + key, value, min, max);
        return value;
    }

    private static void bounded(String key, double value, double min, double max) {
        if (!Double.isFinite(value) || value < min || value > max) {
            throw new IllegalArgumentException(key + " must be finite and in [" + min + ", " + max + "]");
        }
    }

    private static double at(JsonArray array, int i) {
        return i < array.size() ? array.get(i).getAsDouble() : 0;
    }
}
