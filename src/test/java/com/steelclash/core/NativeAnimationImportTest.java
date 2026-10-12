package com.steelclash.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class NativeAnimationImportTest {
    private static final String LIGHT="steelclash.sword.slash.light.right", HEAVY="steelclash.sword.slash.heavy.right";
    private static final Mat3 FACING=new Mat3(-1,0,0,0,-1,0,0,0,1);
    private static JsonObject read(String name) throws Exception {return JsonParser.parseString(Files.readString(Path.of(name))).getAsJsonObject();}
    private static JsonObject source() throws Exception {return read("docs/animation-sources/sword-original.bbmodel");}
    private static JsonObject exported() throws Exception {return read("docs/animation-sources/sword-original-native-export.pal.json");}
    private static JsonObject base() throws Exception {return read("src/main/resources/assets/steelclash/steelclash_animations/sword.json");}
    private static JsonObject imported() throws Exception {return PalAnimationImport.merge(exported(),source(),base(),LIGHT,HEAVY,AttackType.SLASH,false,.5,.925,.25);}
    private static Map<String,double[]> sample(AnimationSet set, double time, boolean heavy) {
        String phase=time<=.5?"windup":time<=.925?"release":"recovery";
        double start=time<=.5?0:time<=.925?.5:.925,end=time<=.5?.5:time<=.925?.925:1.675;
        return set.clip("slash."+(heavy?"heavy_":"")+phase).sample((time-start)/(end-start));
    }
    private static Mat3 matrix(JsonArray m) {return new Mat3(m.get(0).getAsDouble(),m.get(4).getAsDouble(),m.get(8).getAsDouble(),m.get(1).getAsDouble(),m.get(5).getAsDouble(),m.get(9).getAsDouble(),m.get(2).getAsDouble(),m.get(6).getAsDouble(),m.get(10).getAsDouble());}

    @Test void convertedAuthoredBonesMatchActualNativeWorldMatricesAndJointPositions() throws Exception {
        AnimationSet set=AnimationSet.parse(imported());
        JsonObject fixture=read("src/test/resources/steelclash/sword-native-matrices.json");
        for(var takeEntry:fixture.getAsJsonArray("takes")) {
            JsonObject take=takeEntry.getAsJsonObject();boolean heavy=take.get("name").getAsString().equals(HEAVY);
            for(var poseEntry:take.getAsJsonArray("poses")) {
                JsonObject pose=poseEntry.getAsJsonObject();double time=pose.get("time").getAsDouble();
                Map<String,double[]> offsets=sample(set,time,heavy);
                Mat3 body=WeaponRig.bodyRotation(offsets.get("body"));
                for(var bone:pose.getAsJsonObject("bones").entrySet()) {
                    String name=bone.getKey();JsonArray nativeMatrix=bone.getValue().getAsJsonArray();
                    String channel=switch(name){case "left_arm"->"leftArm";case "left_leg"->"leftLeg";case "right_leg"->"rightLeg";default->null;};
                    double[] rotation=channel==null?new double[3]:offsets.get(channel);
                    float hipLag=name.endsWith("_leg")?PoseChannels.hipLagRadians(offsets,1):0;
                    Mat3 actual=body.mul(Mat3.zyx(Math.toRadians(rotation[0]),Math.toRadians(rotation[1])+hipLag,Math.toRadians(rotation[2])));
                    Mat3 expected=FACING.mul(matrix(nativeMatrix)).mul(FACING);
                    assertEquals(0,actual.distance(expected),1e-9,take.get("name")+" "+time+" "+name);
                    Vec joint=switch(name){case "left_arm"->new Vec(5,2,0);case "left_leg"->new Vec(2,12,0);case "right_leg"->new Vec(-2,12,0);default->new Vec(0,12,0);};
                    Vec pivot=new Vec(0,12,0), transformed=pivot.add(body.apply(joint.subtract(pivot)));
                    assertEquals(-nativeMatrix.get(12).getAsDouble(),transformed.x(),1e-9,name+" X");
                    assertEquals(24-nativeMatrix.get(13).getAsDouble(),transformed.y(),1e-9,name+" Y");
                    assertEquals(nativeMatrix.get(14).getAsDouble(),transformed.z(),1e-9,name+" Z");
                }
            }
        }
    }

    @Test void importedBodyCannotPullSolvedBladeOffItsArc() throws Exception {
        AnimationSet set=AnimationSet.parse(imported());
        for(boolean heavy:new boolean[]{false,true})for(int i=0;i<=100;i++) {
            double[] body=sample(set,i*1.675/100,heavy).get("body");
            for(double yaw:new double[]{-80,20,120})for(double pitch:new double[]{-40,0,60}) {
                Mat3 expected=WeaponRig.solve(yaw,pitch,-80,25,WeaponRig.TwistAxis.Z,new double[3],.45).bladeOrientation(new double[3]);
                Mat3 actual=WeaponRig.solve(yaw,pitch,-80,25,WeaponRig.TwistAxis.Z,body,.45).bladeOrientation(body);
                assertEquals(0,expected.distance(actual),1e-9);
            }
        }
    }

    @Test void heavyPhaseSelectionAndSeamsUseMatchingSourceTakes() throws Exception {
        AnimationSet set=AnimationSet.parse(imported());
        assertEquals(28,set.phaseClip(Phase.RELEASE,AttackType.SLASH,false,"slash.release").sample(0).get("body")[1],1e-9);
        assertEquals(48,set.phaseClip(Phase.RELEASE,AttackType.SLASH,true,"slash.release").sample(0).get("body")[1],1e-9);
        assertEquals(48,set.phaseClip(Phase.RELEASE,AttackType.SLASH,true,"slash.variant_2.release").sample(0).get("body")[1],1e-9);
        assertEquals(-22,set.phaseClip(Phase.RECOVERY,AttackType.SLASH,true,"slash.variant_2.recovery").sample(0).get("body")[1],1e-9);
        for(boolean heavy:new boolean[]{false,true}) {
            String stem="slash."+(heavy?"heavy_":"");
            assertArrayEquals(set.clip(stem+"windup").sample(1).get("body"),set.clip(stem+"release").sample(0).get("body"),1e-9);
            assertArrayEquals(set.clip(stem+"release").sample(1).get("leftArm"),set.clip(stem+"recovery").sample(0).get("leftArm"),1e-9);
        }
        AnimationSet old=AnimationSet.parse(base());
        assertSame(old.clip("slash.release"),old.phaseClip(Phase.RELEASE,AttackType.SLASH,true,"slash.release"));
        assertSame(old.clip("slash.recovery"),old.phaseClip(Phase.RECOVERY,AttackType.SLASH,true,"slash.recovery"));
        JsonObject variants=imported();
        JsonObject clips=variants.getAsJsonObject("clips");
        clips.add("slash.variant_2.release",JsonParser.parseString("[{\"t\":0,\"body\":[0,17,0]}]"));
        AnimationSet ordinaryVariant=AnimationSet.parse(variants);
        assertEquals(17,ordinaryVariant.phaseClip(Phase.RELEASE,AttackType.SLASH,true,"slash.variant_2.release").sample(0).get("body")[1]);
        clips.add("slash.variant_2.heavy_release",JsonParser.parseString("[{\"t\":0,\"body\":[0,31,0]}]"));
        AnimationSet heavyVariant=AnimationSet.parse(variants);
        assertEquals(31,heavyVariant.phaseClip(Phase.RELEASE,AttackType.SLASH,true,"slash.variant_2.release").sample(0).get("body")[1]);
        clips.remove("slash.variant_2.heavy_release");clips.add("slash.variant_2.release",new JsonArray());
        assertTrue(AnimationSet.parse(variants).phaseClip(Phase.RELEASE,AttackType.SLASH,true,"slash.variant_2.release").isEmpty());
    }

    @Test void mergePreservesSettingsAndOtherActionsAndRequiresPairedHeavyReplacement() throws Exception {
        JsonObject base=base(),original=base.deepCopy();JsonObject candidate=PalAnimationImport.merge(exported(),source(),base,LIGHT,HEAVY,AttackType.SLASH,false,.5,.925,.25);
        assertEquals(original,base);
        for(String key:base.keySet())if(!key.equals("clips"))assertEquals(base.get(key),candidate.get(key));
        for(var clip:base.getAsJsonObject("clips").entrySet())if(!clip.getKey().startsWith("slash."))assertEquals(clip.getValue(),candidate.getAsJsonObject("clips").get(clip.getKey()));
        assertThrows(IllegalArgumentException.class,()->PalAnimationImport.merge(exported(),source(),base,LIGHT,AttackType.SLASH,false,.5,.925,.25));
        JsonObject noHeavy=base.deepCopy();noHeavy.getAsJsonObject("clips").add("slash.heavy_windup",new JsonArray());
        assertDoesNotThrow(()->PalAnimationImport.merge(exported(),source(),noHeavy,LIGHT,AttackType.SLASH,false,.5,.925,.25));
        for(String key:List.of("slash.windup","slash.release","slash.recovery","slash.heavy_windup","slash.heavy_release","slash.heavy_recovery")) {
            assertEquals(3,candidate.getAsJsonObject("clips").getAsJsonArray(key).size());
            assertEquals("linear",candidate.getAsJsonObject("clip_interpolation").get(key).getAsString());
            assertFalse(AnimationSet.parse(candidate).clip(key).sample(.5).containsKey("rightArm"));
        }
    }

    @Test void authoredLinearCurvesRemainLinearAndLegacyCubicIsUnchanged() {
        JsonObject json=JsonParser.parseString("{\"clips\":{\"slash.windup\":[{\"t\":0,\"body\":[0,0,0]},{\"t\":1,\"body\":[100,0,0]}]}}").getAsJsonObject();
        assertEquals(15.625,AnimationSet.parse(json).clip("slash.windup").sample(.25).get("body")[0],1e-9);
        json.add("clip_interpolation",JsonParser.parseString("{\"slash.windup\":\"linear\"}"));
        var linear=AnimationSet.parse(json);
        assertEquals(25,linear.clip("slash.windup").sample(.25).get("body")[0],1e-9);
        assertEquals(25,AnimationSet.NONE.withFallback(linear).clip("slash.windup").sample(.25).get("body")[0],1e-9);
    }

    @Test void hipPolicyAndCrossPolicyChannelValuesPreserveLegacyAndAuthoredMotion() {
        Map<String,double[]> legacy=Map.of("body",new double[]{5,28,8});
        Map<String,double[]> authored=Map.of("body",new double[]{5,28,8},"hipLag",new double[3]);
        for(double weight:new double[]{0,.17,.5,1})assertEquals(.5f*(float)Math.toRadians(28*weight),PoseChannels.hipLagRadians(legacy,weight));
        assertEquals(0,PoseChannels.hipLagRadians(authored,1));
        assertArrayEquals(new double[]{0,14,0},PoseChannels.value(legacy,"hipLag"));
        assertArrayEquals(new double[3],PoseChannels.value(authored,"hipLag"));
        PoseClip mirrored=new PoseClip(List.of(new PoseClip.Keyframe(0,Map.of("body",new double[]{0,28,8},"hipLag",new double[]{0,14,0}))));
        assertEquals(-14,mirrored.sample(0,1,true).get("hipLag")[1]);
    }

    @Test void malformedInterpolationAndHipPolicyMetadataAreRejected() {
        for(String extra:List.of("\"clip_interpolation\":true","\"clip_interpolation\":{\"missing\":\"linear\"}","\"clip_interpolation\":{\"slash.windup\":\"spline\"}"))
            assertThrows(IllegalArgumentException.class,()->AnimationSet.parse(JsonParser.parseString("{\"clips\":{\"slash.windup\":[]},"+extra+"}").getAsJsonObject()));
        assertThrows(IllegalArgumentException.class,()->AnimationSet.parse(JsonParser.parseString("{\"clips\":{\"slash.windup\":[{\"t\":0,\"hipLag\":[1,0,0]}]}}").getAsJsonObject()));
    }
}
