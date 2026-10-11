package com.steelclash.core;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.FileAlreadyExistsException;
import static org.junit.jupiter.api.Assertions.*;

class PalAnimationConverterTest {
    private static JsonObject source(String bones) {
        return JsonParser.parseString("{\"format_version\":\"1.8.0\",\"animations\":{\"sword\":{\"animation_length\":1.675,\"bones\":"+bones+"}}}").getAsJsonObject();
    }
    private static JsonObject convert(JsonObject source) {
        return PalAnimationConverter.convert(source,"sword",AttackType.SLASH,false,false,.5,.925,.25);
    }
    @Test void phaseBoundariesAndRuntimeInterpolationPreserveAuthoredMotion() {
        var asset=convert(source("{\"body\":{\"rotation\":{\"0\":[0,0,0],\"0.5\":[-8,-35,5],\"0.925\":[-5,-20,2],\"1.675\":[0,0,0]}},\"left_arm\":{\"rotation\":[-20,0,-10]}}"));
        var animation=AnimationSet.parse(asset);
        double[][] body={{0,0,0},{8,35,5},{5,20,2},{0,0,0}};
        String[] phases={"windup","release","recovery"};
        for(int phase=0;phase<3;phase++) {
            PoseClip clip=animation.clip("slash."+phases[phase]);
            for(int i=0;i<=200;i++) {
                double t=i/200.0;
                var sampled=clip.sample(t);
                for(int axis=0;axis<3;axis++) assertEquals(body[phase][axis]+(body[phase+1][axis]-body[phase][axis])*t,sampled.get("body")[axis],.25);
                assertArrayEquals(new double[]{-20,0,-10},sampled.get("leftArm"),1e-9);
            }
        }
    }
    @Test void solvedWeaponAndSupportArmsCannotBeOverridden() {
        assertThrows(IllegalArgumentException.class,()->convert(source("{\"right_arm\":{\"rotation\":[1,0,0]}}")));
        assertThrows(IllegalArgumentException.class,()->PalAnimationConverter.convert(source("{\"left_arm\":{\"bend\":20}}"),"sword",AttackType.SLASH,false,true,.5,.925,.25));
        assertThrows(IllegalArgumentException.class,()->PalAnimationConverter.convert(source("{\"left_arm\":{\"rotation\":[1,0,0]}}"),"sword",AttackType.SLASH,true,false,.5,.925,.25));
        assertThrows(IllegalArgumentException.class,()->convert(source("{\"right_item\":{\"rotation\":[0,0,0]}}")));
    }
    @Test void scalarFreeArmBendIsRetainedAndPhaseSeamsAgree() {
        var constant = AnimationSet.parse(convert(source("{\"left_arm\":{\"bend\":20}}")));
        assertArrayEquals(new double[]{20,0,0}, constant.clip("slash.release").sample(.5).get("leftArmBend"), 1e-9);
        var animated = AnimationSet.parse(convert(source("{\"left_arm\":{\"bend\":{\"0\":0,\"0.5\":20,\"0.925\":10,\"1.675\":0}}}")));
        assertArrayEquals(animated.clip("slash.windup").sample(1).get("leftArmBend"), animated.clip("slash.release").sample(0).get("leftArmBend"), 1e-9);
        assertArrayEquals(animated.clip("slash.release").sample(1).get("leftArmBend"), animated.clip("slash.recovery").sample(0).get("leftArmBend"), 1e-9);
    }
    @Test void leftMainSourceMapsToCanonicalFreeArmAndReflectedLegs() {
        var input=source("{\"right_arm\":{\"rotation\":[-20,15,10],\"bend\":20},\"left_leg\":{\"rotation\":[5,6,7]}}");
        var asset=AnimationSet.parse(PalAnimationConverter.convert(input,"sword",AttackType.SLASH,true,false,.5,.925,.25));
        var pose=asset.clip("slash.release").sample(.5);
        assertArrayEquals(new double[]{-20,-15,-10},pose.get("leftArm"),1e-9);
        assertArrayEquals(new double[]{20,0,0},pose.get("leftArmBend"),1e-9);
        assertArrayEquals(new double[]{5,-6,-7},pose.get("rightLeg"),1e-9);
        assertFalse(pose.containsKey("rightArm"));
    }
    @Test void unsupportedTransformsCurvesAndMetadataFailInsteadOfBeingDropped() {
        for(String bone:new String[]{"{\"position\":[0,0,0]}","{\"scale\":[1,1,1]}","{\"rotation\":{\"vector\":[0,0,0],\"pivot\":[0,0,0]}}","{\"rotation\":{\"0\":{\"vector\":[0,0,0],\"easing\":\"easeInSine\"}}}","{\"bend\":[1,2,0]}"})
            assertThrows(IllegalArgumentException.class,()->convert(source("{\"left_arm\":"+bone+"}")),bone);
    }
    @Test void malformedTimelinesAndUnboundedBakesAreRejected() {
        for(String track:new String[]{"{\"0.5\":[0,0,0],\"0.50\":[1,0,0]}","{\"0.5\":[0,0,0],\"0\":[1,0,0]}","{\"2\":[0,0,0]}","{\"0\":[0,0,0],\"0.1\":[180,0,0]}","[1e999,0,0]","[\"query.foo\",0,0]"})
            assertThrows(IllegalArgumentException.class,()->convert(source("{\"torso\":{\"rotation\":"+track+"}}")),track);
        var input=source("{\"torso\":{\"rotation\":{\"0\":[0,0,0],\"0.5\":[90,0,0]}}}");
        assertThrows(IllegalArgumentException.class,()->PalAnimationConverter.convert(input,"sword",AttackType.SLASH,false,false,.5,.925,1e-9));
        assertThrows(IllegalArgumentException.class,()->PalAnimationConverter.convert(input,"sword",AttackType.SLASH,false,false,.925,.5,.25));
        assertThrows(IllegalArgumentException.class,()->convert(source("{}")));
    }
    @Test void importPreservesBaseSettingsAndOtherClipsWithoutMutatingInputs() {
        var base=JsonParser.parseString("{\"grip\":\"two_handed\",\"grip_gap\":7,\"heavy_windup_scale\":1.4,\"first_person\":{\"ready_yaw\":30},\"clips\":{\"parry\":[{\"t\":0,\"head\":[1,2,3]}]}}").getAsJsonObject();
        var original=base.deepCopy();
        var result=PalAnimationImport.merge(source("{\"torso\":{\"rotation\":[1,2,3]}}"),base,"sword",AttackType.SLASH,false,.5,.925,.25);
        assertEquals(original,base);
        assertEquals(original.get("first_person"),result.get("first_person"));
        assertEquals(original.getAsJsonObject("clips").get("parry"),result.getAsJsonObject("clips").get("parry"));
        var parsed=AnimationSet.parse(result);
        assertTrue(parsed.twoHanded()); assertEquals(7,parsed.gripGap()); assertEquals(1.4,parsed.heavyWindupScale());
        assertFalse(parsed.clip("slash.release").isEmpty());
        assertThrows(IllegalArgumentException.class,()->PalAnimationImport.merge(source("{\"left_arm\":{\"rotation\":[1,0,0]}}"),base,"sword",AttackType.SLASH,false,.5,.925,.25));
    }
    @Test void failedImportAndExistingOutputLeaveFilesUntouched(@TempDir Path dir) throws Exception {
        Path input=dir.resolve("input.json"), base=dir.resolve("base.json"), output=dir.resolve("candidate.json");
        Files.writeString(input,source("{\"torso\":{\"rotation\":[1,2,3]}}").toString());
        Files.writeString(base,"{\"grip\":\"one_handed\",\"clips\":{}}");
        PalAnimationImport.write(input,base,output,"sword",AttackType.SLASH,false,.5,.925,.25);
        String candidate=Files.readString(output);
        assertThrows(FileAlreadyExistsException.class,()->PalAnimationImport.write(input,base,output,"sword",AttackType.SLASH,false,.5,.925,.25));
        assertEquals(candidate,Files.readString(output));
        String inputBefore=Files.readString(input);
        assertThrows(FileAlreadyExistsException.class,()->PalAnimationImport.write(input,base,input,"sword",AttackType.SLASH,false,.5,.925,.25));
        assertEquals(inputBefore,Files.readString(input));
        Files.writeString(input,source("{\"torso\":{\"position\":[0,0,0]}}").toString());
        Path failed=dir.resolve("failed.json");
        assertThrows(IllegalArgumentException.class,()->PalAnimationImport.write(input,base,failed,"sword",AttackType.SLASH,false,.5,.925,.25));
        assertFalse(Files.exists(failed));
        Files.writeString(input,"{\"animations\":{},\"animations\":{}}");
        var duplicate=assertThrows(IllegalArgumentException.class,()->PalAnimationImport.write(input,base,failed,"sword",AttackType.SLASH,false,.5,.925,.25));
        assertTrue(duplicate.getMessage().contains("duplicate JSON key animations"));
        assertFalse(Files.exists(failed));
    }
}
