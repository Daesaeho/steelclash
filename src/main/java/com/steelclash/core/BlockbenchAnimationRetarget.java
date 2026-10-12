package com.steelclash.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** Recovers authored local channels only after proving a canonical source matches its body-folded PAL export. */
public final class BlockbenchAnimationRetarget {
    private static final String[] CHILDREN = {"torso", "head", "right_arm", "left_arm", "right_leg", "left_leg"};
    private static final Set<String> PARTS = Set.of("body", "torso", "head", "right_arm", "left_arm", "right_leg", "left_leg");
    private static final Map<String, double[]> PIVOTS = Map.ofEntries(
            Map.entry("body", new double[]{0,12,0}), Map.entry("torso", new double[]{0,24,0}),
            Map.entry("torso_bend", new double[]{0,18,0}), Map.entry("head", new double[]{0,24,0}),
            Map.entry("right_arm", new double[]{5,22,0}), Map.entry("right_arm_bend", new double[]{5,18,0}),
            Map.entry("right_item", new double[]{6,12,-2}), Map.entry("left_arm", new double[]{-5,22,0}),
            Map.entry("left_arm_bend", new double[]{-5,18,0}), Map.entry("left_item", new double[]{-6,12,-2}),
            Map.entry("right_leg", new double[]{2,12,0}), Map.entry("right_leg_bend", new double[]{2,6,0}),
            Map.entry("left_leg", new double[]{-2,12,0}), Map.entry("left_leg_bend", new double[]{-2,6,0}));
    private static final double MATCH_EPSILON = 1e-7;
    private static final double[] ZERO = {0,0,0};

    private BlockbenchAnimationRetarget() {}

    /** Native Blockbench rotations use the opposite X/Y facing to PAL's local part channels. */
    public static JsonObject recover(JsonObject project, JsonObject nativeExport, String animationName,
                                     boolean leftMain, boolean protectSupport) {
        if (project == null || nativeExport == null || animationName == null) throw new IllegalArgumentException("source project, export and animation are required");
        JsonObject meta = object(required(project,"meta","project"),"project meta");
        if (!text(required(meta,"model_format","project meta"),"model_format").equals("bedrock")) fail("project", "requires the reviewed Bedrock helper rig");
        if (!text(required(meta,"format_version","project meta"),"project format_version").equals("5.0")) fail("project","requires native project format 5.0");
        Map<String, String> groups = validateRig(project);
        JsonObject animation = null;
        for (JsonElement entry : array(required(project,"animations","project"),"animations")) {
            JsonObject candidate = object(entry,"source animation");
            if (text(required(candidate,"name","source animation"),"animation name").equals(animationName)) {
                if (animation != null) fail(animationName,"duplicate source animation name");
                animation = candidate;
            }
        }
        if (animation == null) fail(animationName,"source animation is missing");
        double length = number(required(animation,"length",animationName),animationName+" length",false);
        if (length <= 0) fail(animationName,"length must be positive");
        if (!text(required(animation,"loop",animationName),animationName+" loop").equals("once")) fail(animationName,"looping source is unsupported");
        for (String key : new String[]{"anim_time_update","blend_weight","start_delay","loop_delay"})
            if (animation.has(key) && !text(animation.get(key),animationName+" "+key).isBlank()) fail(animationName+" "+key,"expressions/delays are unsupported");
        requireBoolean(animation,"override",false,animationName);
        Map<String, TreeMap<Double,double[]>> tracks = new LinkedHashMap<>();
        JsonObject animators = object(required(animation,"animators",animationName),animationName+" animators");
        for (var entry : animators.entrySet()) {
            JsonObject animator = object(entry.getValue(),animationName+" animator "+entry.getKey());
            JsonArray frames = animator.has("keyframes") ? array(animator.get("keyframes"),animationName+" animator "+entry.getKey()+" keyframes") : new JsonArray();
            if (frames.isEmpty()) continue;
            String bone = groups.get(entry.getKey());
            if (bone == null || !PARTS.contains(bone)) fail(animationName+" animator "+entry.getKey(),"unknown/effects/helper/item authored track");
            for (String key : animator.keySet()) if (!Set.of("name","type","keyframes","rotation_global","quaternion_interpolation").contains(key))
                fail(bone+" "+key,"unsupported source animator metadata");
            requireBoolean(animator,"rotation_global",false,bone);
            requireBoolean(animator,"quaternion_interpolation",false,bone);
            if (!text(required(animator,"name",bone),bone+" name").equals(bone)
                    || !text(required(animator,"type",bone),bone+" type").equals("bone")) fail(bone,"animator identity does not match its real source bone");
            boolean arm = bone.endsWith("_arm");
            boolean main = bone.equals(leftMain ? "left_arm" : "right_arm");
            if (arm && (main || protectSupport)) fail(bone,"authored rotation is forbidden for this arm");
            TreeMap<Double,double[]> track = new TreeMap<>();
            double previous = -1;
            for (JsonElement frameEntry : frames) {
                JsonObject frame = object(frameEntry,bone+" keyframe");
                if (!text(required(frame,"channel",bone),bone+" channel").equals("rotation")) fail(bone,"position/scale and other source channels require explicit retargeting");
                if (!text(required(frame,"interpolation",bone),bone+" interpolation").equals("linear")) fail(bone,"only linear native interpolation is supported");
                for (String key : frame.keySet()) if (!Set.of("channel","data_points","uuid","time","color","interpolation").contains(key)) fail(bone+" "+key,"unsupported keyframe metadata");
                double time = number(required(frame,"time",bone),bone+" time",false);
                if (time < 0 || time > length || time <= previous) fail(bone+" time "+time,"timestamps must be finite, ordered, unique and within source length");
                previous = time;
                JsonArray points = array(required(frame,"data_points",bone),bone+" data_points");
                if (points.size() != 1) fail(bone,"split pre/post values are unsupported");
                JsonObject point = object(points.get(0),bone+" data point");
                if (!point.keySet().equals(Set.of("x","y","z"))) fail(bone,"data point must contain only numeric X/Y/Z");
                double[] value = {-number(point.get("x"),bone+" X",true), -number(point.get("y"),bone+" Y",true), number(point.get("z"),bone+" Z",true)};
                track.put(time,value);
            }
            tracks.put(bone,track);
        }
        if (!tracks.containsKey("body")) fail(animationName,"an explicit authored body track is required for body-fold recovery");
        validateExport(nativeExport,animationName,length,tracks);
        JsonObject bones = new JsonObject();
        for (var entry : tracks.entrySet()) {
            JsonObject rotation = new JsonObject();
            entry.getValue().forEach((time,value)->rotation.add(Double.toString(time),vector(value)));
            JsonObject bone = new JsonObject(); bone.add("rotation",rotation); bones.add(entry.getKey(),bone);
        }
        JsonObject recoveredAnimation = new JsonObject(); recoveredAnimation.addProperty("animation_length",length);
        recoveredAnimation.addProperty("loop",false); recoveredAnimation.add("bones",bones);
        JsonObject animations = new JsonObject(); animations.add(animationName,recoveredAnimation);
        JsonObject recovered = new JsonObject(); recovered.addProperty("format_version","1.8.0"); recovered.add("animations",animations);
        return recovered;
    }

    private static Map<String,String> validateRig(JsonObject project) {
        Map<String,String> names = new HashMap<>(); Set<String> uniqueNames = new HashSet<>();
        for (JsonElement entry : array(required(project,"groups","project"),"groups")) {
            JsonObject group = object(entry,"group"); String name = text(required(group,"name","group"),"group name");
            String id = text(required(group,"uuid",name),name+" uuid");
            if (names.put(id,name) != null || !uniqueNames.add(name)) fail(name,"duplicate source group identity");
            if (!PIVOTS.containsKey(name)) {
                if (!group.has("export") || !group.get("export").isJsonPrimitive() || !group.getAsJsonPrimitive("export").isBoolean() || group.get("export").getAsBoolean())
                    fail(name,"unknown exported group requires explicit retargeting");
                continue;
            }
            compare(vectorValue(required(group,"origin",name),name+" origin"),PIVOTS.get(name),name+" pivot");
            requireBoolean(group,"export",true,name);
            requireBoolean(group,"visibility",true,name);
            requireBoolean(group,"reset",false,name);
            compare(vectorValue(required(group,"rotation",name),name+" rotation"),ZERO,name+" static rotation");
            if (group.has("scale")) compare(vectorValue(group.get("scale"),name+" scale"),new double[]{1,1,1},name+" static scale");
            if (group.has("bedrock_binding") && !text(group.get("bedrock_binding"),name+" binding").isBlank()) fail(name,"bone binding is unsupported");
        }
        if (!uniqueNames.containsAll(PIVOTS.keySet())) fail("rig","missing canonical source bones");
        Map<String,String> parents = new HashMap<>();
        walk(array(required(project,"outliner","project"),"outliner"),"root",names,parents,0);
        if (!parents.keySet().equals(uniqueNames)) fail("rig","outliner must reference every group exactly once");
        for (String name : PIVOTS.keySet()) {
            String expected = name.equals("body") ? "root" : name.endsWith("_bend") ? name.substring(0,name.length()-5)
                    : name.endsWith("_item") ? name.substring(0,name.length()-5)+"_arm_bend" : "body";
            if (!expected.equals(parents.get(name))) fail(name+" parent","noncanonical hierarchy requires explicit retargeting");
        }
        return names;
    }

    private static void walk(JsonArray nodes,String parent,Map<String,String> names,Map<String,String> parents,int depth) {
        if (depth > 32) fail("outliner","hierarchy nesting exceeds 32");
        for (JsonElement entry : nodes) {
            if (entry.isJsonPrimitive()) continue; // Cube/mesh UUIDs are geometry, never animation parents.
            JsonObject node = object(entry,"outliner node"); String id = text(required(node,"uuid","outliner"),"outliner uuid");
            String name = names.get(id);
            if (name == null || parents.put(name,parent) != null) fail("outliner "+id,"unknown or repeated group");
            walk(array(required(node,"children",name),name+" children"),name,names,parents,depth+1);
        }
    }

    private static void validateExport(JsonObject source,String name,double length,Map<String,TreeMap<Double,double[]>> tracks) {
        if (!source.keySet().equals(Set.of("format_version","animations")) || !text(source.get("format_version"),"export format_version").equals("1.8.0")) fail("export","unsupported format/rig metadata");
        JsonObject animation = object(required(object(required(source,"animations","export"),"export animations"),name,"export animations"),"export "+name);
        for (String key : animation.keySet()) if (!Set.of("animation_length","bones","loop").contains(key)) fail("export "+key,"unsupported metadata");
        if (Math.abs(number(required(animation,"animation_length",name),"export length",false)-length)>1e-9) fail(name,"source/export length mismatch");
        requireBoolean(animation,"loop",false,"export "+name);
        JsonObject bones = object(required(animation,"bones",name),"export bones");
        if (!bones.keySet().equals(Set.of(CHILDREN))) fail(name,"export must contain exactly the six body-folded child rotations");
        var body = tracks.get("body");
        for (String bone : CHILDREN) {
            JsonObject channels = object(bones.get(bone),"export "+bone);
            if (!channels.keySet().equals(Set.of("rotation"))) fail("export "+bone,"unexpected position/scale/bend or other channels cannot be discarded");
            JsonObject keys = object(channels.get("rotation"),"export "+bone+" rotation");
            TreeSet<Double> expectedTimes = new TreeSet<>(body.keySet()); expectedTimes.add(0d);
            var local = tracks.get(bone); if (local != null) expectedTimes.addAll(local.keySet());
            TreeSet<Double> actualTimes = new TreeSet<>(); double previous = -1;
            for (var key : keys.entrySet()) {
                double time;
                try {time=Double.parseDouble(key.getKey());} catch(NumberFormatException error){fail("export "+bone,"invalid timestamp");return;}
                if (!Double.isFinite(time) || time < 0 || time > length || time <= previous || !actualTimes.add(time)) fail("export "+bone,"unordered/invalid timestamp");
                previous = time; JsonElement value = key.getValue();
                if (value.isJsonObject()) {
                    JsonObject wrapped = value.getAsJsonObject();
                    if (!wrapped.keySet().equals(Set.of("vector"))) fail("export "+bone,"unsupported easing/key metadata");
                    value=wrapped.get("vector");
                }
                double[] root = sample(body,time), child = sample(local,time);
                compare(vectorValue(value,"export "+bone+" at "+time),new double[]{root[0]+child[0],root[1]+child[1],root[2]+child[2]},"export "+bone+" at "+time+" source mismatch");
            }
            if (!actualTimes.equals(expectedTimes)) fail("export "+bone,"source/export key-time union mismatch");
        }
    }

    private static double[] sample(TreeMap<Double,double[]> track,double time) {
        if (track == null) return ZERO;
        var from=track.floorEntry(time); var to=track.ceilingEntry(time);
        if(from==null)return track.firstEntry().getValue(); if(to==null)return track.lastEntry().getValue();
        if(from.getKey().equals(to.getKey()))return from.getValue();
        double fraction=(time-from.getKey())/(to.getKey()-from.getKey()); double[] a=from.getValue(),b=to.getValue();
        return new double[]{a[0]+(b[0]-a[0])*fraction,a[1]+(b[1]-a[1])*fraction,a[2]+(b[2]-a[2])*fraction};
    }
    private static void compare(double[] actual,double[] expected,String where){for(int i=0;i<3;i++)if(Math.abs(actual[i]-expected[i])>MATCH_EPSILON)fail(where,"does not match the reviewed source transform");}
    private static JsonArray vector(double[] value){JsonArray a=new JsonArray();for(double v:value)a.add(v);return a;}
    private static double[] vectorValue(JsonElement value,String where){JsonArray a=array(value,where);if(a.size()!=3)fail(where,"expected three axes");return new double[]{number(a.get(0),where,false),number(a.get(1),where,false),number(a.get(2),where,false)};}
    private static JsonElement required(JsonObject o,String key,String where){if(!o.has(key))fail(where+" "+key,"is required");return o.get(key);}
    private static JsonObject object(JsonElement e,String where){if(e==null||!e.isJsonObject())fail(where,"expected object");return e.getAsJsonObject();}
    private static JsonArray array(JsonElement e,String where){if(e==null||!e.isJsonArray())fail(where,"expected array");return e.getAsJsonArray();}
    private static String text(JsonElement e,String where){if(e==null||!e.isJsonPrimitive()||!e.getAsJsonPrimitive().isString())fail(where,"expected string");return e.getAsString();}
    private static double number(JsonElement e,String where,boolean numericString){
        if(e==null||!e.isJsonPrimitive()||!(e.getAsJsonPrimitive().isNumber()||numericString&&e.getAsJsonPrimitive().isString()))fail(where,"expected finite number");
        double value;try{value=Double.parseDouble(e.getAsString());}catch(NumberFormatException error){fail(where,"Molang/non-numeric value is unsupported");return 0;}
        if(!Double.isFinite(value))fail(where,"non-finite value");return value;
    }
    private static void requireBoolean(JsonObject object,String key,boolean expected,String where) {
        if (!object.has(key)) return; // Native codec omits default-valued optional fields.
        JsonElement value=object.get(key);
        if(!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isBoolean()||value.getAsBoolean()!=expected)
            fail(where+" "+key,"unsupported source/export flag; expected "+expected);
    }
    private static void fail(String where,String message){throw new IllegalArgumentException(where+": "+message);}
}
