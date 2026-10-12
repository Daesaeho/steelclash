package com.steelclash.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Strict, Minecraft-free importer for the linear subset of PAL player animation JSON. */
public final class PalAnimationConverter {
    private static final int MAX_KEYS_PER_PHASE = 4096;
    private static final Set<String> ROOT_KEYS = Set.of("format_version", "animations", "pal_bend_player_tools", "source_rig");
    private static final Set<String> ANIMATION_KEYS = Set.of("animation_length", "loop", "bones");
    private static final Set<String> BODY_KEYS = Set.of("body", "torso", "head", "left_arm", "right_arm", "left_leg", "right_leg");
    private static final Set<String> BONE_KEYS = Set.of("rotation", "bend");

    private PalAnimationConverter() {}

    /**
     * Converts one named PAL animation to three normalized Steel Clash phase clips.
     * PAL bone offsets are mapped to the runtime's matching camel-case rotation parts. Body rotations encode inverse
     * X/Y because {@code ProceduralSwingAnimation.wholeBody} negates those offsets. Position and scale are rejected;
     * explicit named rotation offsets are required because this importer never infers model pivots or hierarchy.
     */
    public static JsonObject convert(JsonObject source, String animationName, AttackType attack, boolean leftMain,
                                     boolean protectSupport, double releaseStart, double recoveryStart,
                                     double toleranceDegrees) {
        return convert(source, animationName, attack, leftMain, protectSupport, releaseStart, recoveryStart, toleranceDegrees,
                PoseClip.Interpolation.MONOTONE_CUBIC);
    }

    public static JsonObject convert(JsonObject source, String animationName, AttackType attack, boolean leftMain,
                                     boolean protectSupport, double releaseStart, double recoveryStart,
                                     double toleranceDegrees, PoseClip.Interpolation interpolation) {
        if (source == null || animationName == null || attack == null) throw new IllegalArgumentException("source, animationName and attack are required");
        for (String key : source.keySet()) if (!ROOT_KEYS.contains(key)) fail("root", key, "unknown metadata");
        if (source.has("format_version") && !string(source.get("format_version"), "format_version").equals("1.8.0")) fail("root", "format_version", "only PAL animations format 1.8.0 is supported");
        if (source.has("source_rig") && !source.get("source_rig").isJsonNull()) {
            JsonElement rig = source.get("source_rig");
            if (!(rig.isJsonPrimitive() && rig.getAsJsonPrimitive().isString() &&
                    rig.getAsString().isBlank())) fail("root", "source_rig", "source rig retargeting is unsupported");
        }
        if (source.has("pal_bend_player_tools")) {
            JsonObject meta = object(source.get("pal_bend_player_tools"), "pal_bend_player_tools");
            if (meta.size() != 1 || !meta.has("format") || !string(meta.get("format"), "pal_bend_player_tools.format").equals("runtime_local_v1"))
                fail("root", "pal_bend_player_tools", "only format runtime_local_v1 is supported");
        }
        JsonObject animations = object(required(source, "animations", "root"), "animations");
        JsonObject animation = object(required(animations, animationName, "animations"), "animation " + animationName);
        for (String key : animation.keySet()) if (!ANIMATION_KEYS.contains(key)) fail("animation " + animationName, key, "unsupported animation metadata/channel");
        double length = number(required(animation, "animation_length", animationName), animationName + ".animation_length");
        if (length <= 0) fail("animation " + animationName, "animation_length", "must be positive");
        if (animation.has("loop") && (!animation.get("loop").isJsonPrimitive() || !animation.getAsJsonPrimitive("loop").isBoolean() || animation.get("loop").getAsBoolean()))
            fail("animation " + animationName, "loop", "only loop=false is supported");
        if (!Double.isFinite(releaseStart) || !Double.isFinite(recoveryStart) || !(0 < releaseStart && releaseStart < recoveryStart && recoveryStart < length))
            throw new IllegalArgumentException("animation " + animationName + ": require 0 < releaseStart < recoveryStart < animation_length");
        if (!Double.isFinite(toleranceDegrees) || toleranceDegrees <= 0) throw new IllegalArgumentException("toleranceDegrees must be finite and positive");

        JsonObject bones = object(required(animation, "bones", animationName), "animation " + animationName + ".bones");
        Map<String, Track> tracks = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : bones.entrySet()) {
            String bone = entry.getKey(), context = "animation " + animationName + " bone " + bone;
            if (bone.toLowerCase(Locale.ROOT).contains("item") || bone.toLowerCase(Locale.ROOT).contains("weapon")) fail(context, "bone", "item/weapon bones are unsupported");
            if (!BODY_KEYS.contains(bone)) fail(context, "bone", "unknown bone or unsupported hierarchy/geometry metadata");
            JsonObject channels = object(entry.getValue(), context);
            for (String channel : channels.keySet()) if (!BONE_KEYS.contains(channel)) fail(context, channel, "unsupported channel or bone metadata");
            String arm = bone.equals("left_arm") ? "leftArm" : bone.equals("right_arm") ? "rightArm" : "";
            boolean weaponArm = !arm.isEmpty() && ((leftMain && arm.equals("leftArm")) || (!leftMain && arm.equals("rightArm")));
            boolean supportArm = !arm.isEmpty() && !weaponArm;
            for (String channel : channels.keySet()) {
                if (channel.equals("rotation") && (weaponArm || (protectSupport && supportArm))) fail(context, channel, "authored rotation is forbidden for this arm");
                if (channel.equals("bend") && (weaponArm || (protectSupport && supportArm))) fail(context, channel, "authored bend is forbidden for this arm");
                String out = channel.equals("bend") ? arm + "Bend" : switch (bone) {
                    case "body" -> "body"; case "torso" -> "torso"; case "head" -> "head";
                    case "left_arm" -> "leftArm"; case "right_arm" -> "rightArm";
                    case "left_leg" -> "leftLeg"; default -> "rightLeg";
                };
                if (channel.equals("bend") && arm.isEmpty()) fail(context, channel, "bend is supported only on a free arm");
                Track track = parseTrack(channels.get(channel), context, channel, length, channel.equals("bend"));
                if (channel.equals("bend") && track.values.stream().anyMatch(v -> v[1] != 0 || v[2] != 0)) fail(context, channel, "only scalar X bend is supported");
                for (int i = 1; i < track.values.size(); i++) for (int axis = 0; axis < 3; axis++)
                    if (Math.abs(track.values.get(i)[axis] - track.values.get(i - 1)[axis]) >= 180)
                        fail(context, channel + " time " + track.times.get(i), "angle jump >= 180 degrees; explicitly unwrap the track");
                if (bone.equals("body")) for (double[] v : track.values) { v[0] = -v[0]; v[1] = -v[1]; }
                // Runtime assets use canonical right-main limbs; CombatPose swaps and reflects them for left-main.
                if (leftMain && (out.startsWith("left") || out.startsWith("right"))) {
                    out = out.startsWith("left") ? "right" + out.substring(4) : "left" + out.substring(5);
                    for (double[] v : track.values) { v[1] = -v[1]; v[2] = -v[2]; }
                }
                tracks.put(out, track);
            }
        }
        if (tracks.isEmpty()) fail("animation " + animationName, "bones", "must contain a supported authored channel");
        double maxSlope = 0;
        for (Track t : tracks.values()) for (int i = 1; i < t.times.size(); i++) {
            double dt = t.times.get(i) - t.times.get(i - 1);
            for (int axis = 0; axis < 3; axis++) maxSlope = Math.max(maxSlope, Math.abs(t.values.get(i)[axis] - t.values.get(i - 1)[axis]) / dt);
        }
        double maxStep = maxSlope == 0 ? length : toleranceDegrees / (4 * maxSlope);
        if (!(maxStep > 0) || !Double.isFinite(maxStep)) fail("animation " + animationName, "tracks", "cannot derive a finite bake interval");
        JsonObject clips = new JsonObject();
        double[] edges = {0, releaseStart, recoveryStart, length};
        String[] phases = {"windup", "release", "recovery"};
        for (int p = 0; p < 3; p++) {
            double start = edges[p], end = edges[p + 1], span = end - start;
            TreeSet<Double> times = new TreeSet<>(); times.add(start); times.add(end);
            for (Track t : tracks.values()) for (double time : t.times) if (time > start && time < end) times.add(time);
            int subdivisions = interpolation == PoseClip.Interpolation.LINEAR ? 1 : (int)Math.ceil(span / maxStep);
            if (subdivisions > MAX_KEYS_PER_PHASE) fail("animation " + animationName, phases[p], "adaptive bake exceeds " + MAX_KEYS_PER_PHASE + " keys");
            for (int i = 1; i < subdivisions; i++) times.add(start + span * i / subdivisions);
            if (times.size() > MAX_KEYS_PER_PHASE) fail("animation " + animationName, phases[p], "phase exceeds " + MAX_KEYS_PER_PHASE + " keys");
            JsonArray keys = new JsonArray();
            for (double time : times) {
                JsonObject key = new JsonObject(); key.addProperty("t", (time - start) / span);
                for (Map.Entry<String, Track> e : tracks.entrySet()) key.add(e.getKey(), vector(sample(e.getValue(), time)));
                keys.add(key);
            }
            clips.add(attack.serializedName() + "." + phases[p], keys);
        }
        JsonObject out = new JsonObject(); out.addProperty("format_version", 1); out.add("clips", clips);
        if (interpolation == PoseClip.Interpolation.LINEAR) {
            JsonObject curves = new JsonObject(); clips.keySet().forEach(key -> curves.addProperty(key,"linear"));
            out.add("clip_interpolation",curves);
        }
        return out;
    }

    private static Track parseTrack(JsonElement json, String context, String channel, double length, boolean bend) {
        String where = context + " channel " + channel; Track out = new Track();
        if (json.isJsonArray() || (bend && json.isJsonPrimitive() && json.getAsJsonPrimitive().isNumber())
                || (json.isJsonObject() && json.getAsJsonObject().has("vector"))) {
            if (json.isJsonObject()) {
                JsonObject key = json.getAsJsonObject();
                for (String name : key.keySet()) if (!Set.of("vector", "easing").contains(name)) fail(where, name, "unsupported key metadata");
                if ((key.size() != 1 && key.size() != 2) || (key.has("easing") && !string(key.get("easing"), where + " easing").equals("linear")))
                    fail(where, "value", "key objects may contain only vector and easing: linear");
                json = key.get("vector");
            }
            double[] v = vectorValue(json, where, bend); out.times.add(0d); out.values.add(v); return out;
        }
        JsonObject keys = object(json, where);
        for (Map.Entry<String, JsonElement> e : keys.entrySet()) {
            double time;
            try { time = Double.parseDouble(e.getKey()); } catch (NumberFormatException ex) { fail(where, e.getKey(), "timestamp must be numeric"); return out; }
            if (!Double.isFinite(time) || time < 0 || time > length) fail(where, e.getKey(), "timestamp must be finite and within animation_length");
            if (!out.times.isEmpty() && time <= out.times.get(out.times.size() - 1)) fail(where, e.getKey(), "timestamps must be strictly increasing and unique");
            JsonElement value = e.getValue();
            if (value.isJsonObject()) {
                JsonObject key = value.getAsJsonObject();
                for (String name : key.keySet()) if (!Set.of("vector", "easing").contains(name)) fail(where, name, "unsupported key metadata");
                if (key.size() != 1 && key.size() != 2 || !key.has("vector") || (key.has("easing") && !string(key.get("easing"), where + " easing").equals("linear"))) fail(where, e.getKey(), "key objects may contain only vector and easing: linear");
                value = key.get("vector");
            }
            out.times.add(time); out.values.add(vectorValue(value, where + " time " + e.getKey(), bend));
        }
        if (out.times.isEmpty()) fail(where, "track", "must contain at least one key");
        return out;
    }

    private static double[] vectorValue(JsonElement json, String context, boolean bend) {
        JsonArray a;
        if (bend && json.isJsonPrimitive() && json.getAsJsonPrimitive().isNumber()) return new double[]{number(json, context), 0, 0};
        if (!json.isJsonArray()) fail(context, "value", "expected a numeric vector");
        a = json.getAsJsonArray(); if (a.size() != 3) fail(context, "value", "expected exactly three numeric axes");
        return new double[]{number(a.get(0), context + " X"), number(a.get(1), context + " Y"), number(a.get(2), context + " Z")};
    }

    private static double[] sample(Track t, double time) {
        if (time <= t.times.get(0)) return t.values.get(0).clone();
        int i = 1; while (i < t.times.size() && t.times.get(i) < time) i++;
        if (i == t.times.size()) return t.values.get(i - 1).clone();
        double f = (time - t.times.get(i - 1)) / (t.times.get(i) - t.times.get(i - 1)); double[] a = t.values.get(i - 1), b = t.values.get(i);
        return new double[]{a[0] + (b[0]-a[0])*f, a[1] + (b[1]-a[1])*f, a[2] + (b[2]-a[2])*f};
    }

    private static JsonArray vector(double[] v) { JsonArray a = new JsonArray(); for (double x : v) a.add(x); return a; }
    private static JsonElement required(JsonObject o, String k, String ctx) { if (!o.has(k)) fail(ctx, k, "is required"); return o.get(k); }
    private static JsonObject object(JsonElement e, String ctx) { if (e == null || !e.isJsonObject()) fail(ctx, "value", "expected an object"); return e.getAsJsonObject(); }
    private static String string(JsonElement e, String ctx) { if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString()) fail(ctx, "value", "expected a string"); return e.getAsString(); }
    private static double number(JsonElement e, String ctx) { if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) fail(ctx, "value", "expected a number"); double v; try { v = e.getAsDouble(); } catch (RuntimeException ex) { fail(ctx, "value", "invalid number"); return 0; } if (!Double.isFinite(v)) fail(ctx, "value", "must be finite"); return v; }
    private static void fail(String context, String where, String message) { throw new IllegalArgumentException(context + " " + where + ": " + message); }
    private static final class Track { final List<Double> times = new ArrayList<>(); final List<double[]> values = new ArrayList<>(); }
}
