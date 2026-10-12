package com.steelclash.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class BlockbenchAnimationRetargetTest {
    private static final String ANIMATION = "steelclash.sword.slash.light.right";
    private static final Path SOURCE_PATH = Path.of("docs/animation-sources/sword-original.bbmodel");
    private static final Path EXPORT_PATH = Path.of("docs/animation-sources/sword-original-native-export.pal.json");

    private static JsonObject read(Path path) {
        try (var reader = Files.newBufferedReader(path)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (IOException e) {
            throw new AssertionError("Could not read fixture " + path, e);
        }
    }

    private static JsonObject source() { return read(SOURCE_PATH); }
    private static JsonObject exported() { return read(EXPORT_PATH); }

    private static JsonObject result(JsonObject project, JsonObject nativeExport) {
        return BlockbenchAnimationRetarget.recover(project, nativeExport, ANIMATION, false, false);
    }

    private static JsonObject recoveredAnimation(JsonObject recovered) {
        return recovered.getAsJsonObject("animations").getAsJsonObject(ANIMATION);
    }

    private static String groupUuid(JsonObject project, String name) {
        for (JsonElement group : project.getAsJsonArray("groups")) {
            JsonObject object = group.getAsJsonObject();
            if (name.equals(object.get("name").getAsString())) return object.get("uuid").getAsString();
        }
        throw new AssertionError("Missing group " + name);
    }

    private static JsonObject animator(JsonObject project, String bone) {
        return project.getAsJsonArray("animations").asList().stream()
                .map(JsonElement::getAsJsonObject)
                .filter(a -> ANIMATION.equals(a.get("name").getAsString()))
                .findFirst().orElseThrow()
                .getAsJsonObject("animators").getAsJsonObject(groupUuid(project, bone));
    }

    private static JsonObject sourcePoint(JsonObject project, String bone, int keyIndex) {
        return animator(project, bone).getAsJsonArray("keyframes").get(keyIndex)
                .getAsJsonObject().getAsJsonArray("data_points").get(0).getAsJsonObject();
    }

    private static JsonArray vector(JsonObject bone, String channel, String time) {
        JsonElement point = bone.getAsJsonObject(channel).get(time);
        return point.isJsonObject() ? point.getAsJsonObject().getAsJsonArray("vector") : point.getAsJsonArray();
    }

    private static double[] values(JsonArray array) {
        return new double[]{array.get(0).getAsDouble(), array.get(1).getAsDouble(), array.get(2).getAsDouble()};
    }

    private static void assertRejects(JsonObject project, JsonObject export, String context) {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> result(project, export), context);
        assertTrue(error.getMessage().toLowerCase().contains(context.toLowerCase()),
                "Expected contextual error containing '" + context + "', got: " + error.getMessage());
    }

    private static JsonObject findNode(JsonArray nodes, String uuid) {
        for (JsonElement element : nodes) {
            if (!element.isJsonObject()) continue;
            JsonObject node = element.getAsJsonObject();
            if (uuid.equals(node.get("uuid").getAsString())) return node;
            if (node.has("children")) {
                JsonObject nested = findNode(node.getAsJsonArray("children"), uuid);
                if (nested != null) return nested;
            }
        }
        return null;
    }

    private static boolean removeNode(JsonArray nodes, String uuid) {
        for (int i = 0; i < nodes.size(); i++) {
            JsonElement element = nodes.get(i);
            if (element.isJsonObject()) {
                JsonObject node = element.getAsJsonObject();
                if (uuid.equals(node.get("uuid").getAsString())) { nodes.remove(i); return true; }
                if (node.has("children") && removeNode(node.getAsJsonArray("children"), uuid)) return true;
            }
        }
        return false;
    }

    @Test void recoversBodySeparatelyAndReversesPalFacingOnFreeLimbsWithoutMutatingInputs() {
        JsonObject project = source(), export = exported();
        JsonObject beforeProject = project.deepCopy(), beforeExport = export.deepCopy();

        JsonObject recovered = result(project, export);
        JsonObject bones = recoveredAnimation(recovered).getAsJsonObject("bones");
        assertTrue(bones.has("body"));
        assertTrue(bones.has("left_arm"));
        assertTrue(bones.has("left_leg"));
        assertTrue(bones.has("right_leg"));
        assertFalse(bones.has("right_arm"));
        assertFalse(bones.has("right_item"));
        assertFalse(bones.has("left_item"));

        for (String folded : new String[]{"torso", "head", "right_arm", "left_arm", "right_leg", "left_leg"}) {
            JsonObject nativeBone = export.getAsJsonObject("animations").getAsJsonObject(ANIMATION)
                    .getAsJsonObject("bones").getAsJsonObject(folded);
            assertEquals(1.675, export.getAsJsonObject("animations").getAsJsonObject(ANIMATION)
                    .get("animation_length").getAsDouble(), 1e-9);
            assertEquals(java.util.Set.of("0", "0.25", "0.5", "0.65", "0.925", "1.3", "1.675"),
                    nativeBone.getAsJsonObject("rotation").keySet(), folded + " must retain the exact folded time union");
        }

        JsonObject body = bones.getAsJsonObject("body");
        double[] bbBody = {0, 12, 4};
        double[] recoveredBody = values(vector(body, "rotation", "0.25"));
        assertArrayEquals(new double[]{-bbBody[0], -bbBody[1], bbBody[2]}, recoveredBody, 1e-9);

        for (String name : new String[]{"left_arm", "left_leg", "right_leg"}) {
            JsonObject sourceTrack = animator(project, name).getAsJsonArray("keyframes").get(1).getAsJsonObject()
                    .getAsJsonArray("data_points").get(0).getAsJsonObject();
            JsonObject recoveredBone = bones.getAsJsonObject(name);
            double x = Double.parseDouble(sourceTrack.get("x").getAsString());
            double y = Double.parseDouble(sourceTrack.get("y").getAsString());
            double z = Double.parseDouble(sourceTrack.get("z").getAsString());
            assertArrayEquals(new double[]{-x, -y, z}, values(vector(recoveredBone, "rotation", "0.25")), 1e-9, name);
        }
        assertEquals(beforeProject, project, "recover must not mutate the editable source");
        assertEquals(beforeExport, export, "recover must not mutate the native export");
    }

    @Test void rejectsNativeExportThatDoesNotMatchSourceProvenance() {
        JsonObject badExport = exported();
        JsonObject animation = badExport.getAsJsonObject("animations").getAsJsonObject(ANIMATION);
        animation.addProperty("animation_length", 1.5);
        assertRejects(source(), badExport, ANIMATION);
    }

    @Test void rejectsAuthoredProtectedWeaponTrackEvenWhenItsValueIsZero() {
        JsonObject project = source();
        JsonObject weaponAnimator = animator(project, "right_arm");
        if (!weaponAnimator.has("keyframes")) weaponAnimator.add("keyframes", new JsonArray());
        JsonObject zeroKey = animator(project, "body").getAsJsonArray("keyframes").get(0).getAsJsonObject().deepCopy();
        weaponAnimator.getAsJsonArray("keyframes").add(zeroKey);
        assertRejects(project, exported(), "right_arm");
    }

    @Test void rejectsChangedSourceAnimatorAndExportSemantics() {
        for(String flag:new String[]{"rotation_global","quaternion_interpolation"}) {
            JsonObject changed=source();animator(changed,"body").addProperty(flag,true);
            assertRejects(changed,exported(),flag);
        }
        for(String flag:new String[]{"export","visibility","reset"}) {
            JsonObject changed=source();JsonObject group=changed.getAsJsonArray("groups").asList().stream().map(JsonElement::getAsJsonObject)
                    .filter(g->g.get("name").getAsString().equals("left_arm")).findFirst().orElseThrow();
            group.addProperty(flag,flag.equals("reset"));
            assertRejects(changed,exported(),flag);
        }
        JsonObject changed=source();animator(changed,"body").addProperty("unknown_transform_mode",true);
        assertRejects(changed,exported(),"unknown_transform_mode");
    }

    @Test void rejectsSourceTransformsAndNoncanonicalRigStructure() {
        JsonObject translated = source();
        JsonObject root = translated.getAsJsonArray("groups").asList().stream().map(JsonElement::getAsJsonObject)
                .filter(g -> "body".equals(g.get("name").getAsString())).findFirst().orElseThrow();
        root.getAsJsonArray("origin").set(0, JsonParser.parseString("1"));
        assertRejects(translated, exported(), "body");

        JsonObject rotated = source();
        JsonObject torso = rotated.getAsJsonArray("groups").asList().stream().map(JsonElement::getAsJsonObject)
                .filter(g -> "torso".equals(g.get("name").getAsString())).findFirst().orElseThrow();
        torso.getAsJsonArray("rotation").set(1, JsonParser.parseString("5"));
        assertRejects(rotated, exported(), "torso");

        JsonObject pivoted = source();
        pivoted.getAsJsonArray("groups").asList().stream().map(JsonElement::getAsJsonObject)
                .filter(g -> "left_arm".equals(g.get("name").getAsString())).findFirst().orElseThrow()
                .getAsJsonArray("origin").set(2, JsonParser.parseString("1"));
        assertRejects(pivoted, exported(), "left_arm");

        JsonObject movedParent = source();
        JsonArray outliner = movedParent.getAsJsonArray("outliner");
        String leftLeg = groupUuid(movedParent, "left_leg"), torsoUuid = groupUuid(movedParent, "torso");
        JsonObject movedNode = findNode(outliner, leftLeg).deepCopy();
        assertTrue(removeNode(outliner, leftLeg));
        findNode(outliner, torsoUuid).getAsJsonArray("children").add(movedNode);
        assertRejects(movedParent, exported(), "left_leg");
    }

    @Test void rejectsPositionScaleAndUnknownOrEffectTracks() {
        for (String channel : new String[]{"position", "scale"}) {
            JsonObject project = source();
            JsonObject key = animator(project, "left_arm").getAsJsonArray("keyframes").get(0).getAsJsonObject();
            key.addProperty("channel", channel);
            assertRejects(project, exported(), channel);
        }

        JsonObject effect = source();
        JsonObject anim = effect.getAsJsonArray("animations").asList().stream().map(JsonElement::getAsJsonObject)
                .filter(a -> ANIMATION.equals(a.get("name").getAsString())).findFirst().orElseThrow();
        JsonObject unknown = new JsonObject();
        unknown.addProperty("name", "sound_effect");
        unknown.addProperty("type", "effect");
        JsonArray effectFrames = new JsonArray();
        effectFrames.add(animator(effect, "body").getAsJsonArray("keyframes").get(0).deepCopy());
        unknown.add("keyframes", effectFrames);
        anim.getAsJsonObject("animators").add("effect-uuid", unknown);
        assertRejects(effect, exported(), "animator");
    }

    @Test void rejectsNonlinearDuplicateNonfiniteAndMolangSourceValuesWithContext() {
        JsonObject nonlinear = source();
        animator(nonlinear, "body").getAsJsonArray("keyframes").get(1).getAsJsonObject().addProperty("interpolation", "catmullrom");
        assertRejects(nonlinear, exported(), "body");

        JsonObject duplicate = source();
        JsonObject duplicateKey = animator(duplicate, "body").getAsJsonArray("keyframes").get(1).getAsJsonObject().deepCopy();
        animator(duplicate, "body").getAsJsonArray("keyframes").add(duplicateKey);
        assertRejects(duplicate, exported(), "body");

        JsonObject nonfinite = source();
        sourcePoint(nonfinite, "body", 1).addProperty("x", "Infinity");
        assertRejects(nonfinite, exported(), "body");

        JsonObject molang = source();
        sourcePoint(molang, "left_arm", 1).addProperty("y", "query.foo");
        assertRejects(molang, exported(), "left_arm");
    }

    @Test void validatesFoldedNativeChannelsAndRejectsNativeCompensationData() {
        JsonObject export = exported();
        JsonObject bones = export.getAsJsonObject("animations").getAsJsonObject(ANIMATION).getAsJsonObject("bones");
        for (String child : new String[]{"torso", "head", "right_arm", "left_arm", "right_leg", "left_leg"}) {
            JsonObject bone = bones.getAsJsonObject(child);
            assertNotNull(bone, child);
            assertTrue(bone.has("rotation"), child);
            assertFalse(bone.has("position"), child);
            assertFalse(bone.has("scale"), child);
        }

        JsonObject positioned = exported();
        positioned.getAsJsonObject("animations").getAsJsonObject(ANIMATION).getAsJsonObject("bones")
                .getAsJsonObject("left_arm").add("position", JsonParser.parseString("{\"0\":{\"vector\":[1,0,0]}}"));
        assertRejects(source(), positioned, "left_arm");

        JsonObject metadata = exported();
        metadata.add("source_rig", JsonParser.parseString("{\"name\":\"custom\"}"));
        assertRejects(source(), metadata, "export");

        JsonObject extra = exported();
        extra.getAsJsonObject("animations").getAsJsonObject(ANIMATION).getAsJsonObject("bones")
                .add("unexpected", JsonParser.parseString("{\"rotation\":{\"0\":{\"vector\":[0,0,0]}}}"));
        assertRejects(source(), extra, "export");
    }
}
