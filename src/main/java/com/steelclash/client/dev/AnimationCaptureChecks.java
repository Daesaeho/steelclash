package com.steelclash.client.dev;

import com.google.gson.JsonParser;
import com.steelclash.Config;
import com.steelclash.SteelClash;
import com.steelclash.client.anim.AnimationLibrary;
import com.steelclash.core.AnimationSet;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import net.minecraft.client.Minecraft;

/** Optional capture assertions: selecting a resource pack is not proof of applying its animation. */
public final class AnimationCaptureChecks {
    private static Boolean oldTwoHanded;
    private static Double oldCameraMotion;
    private AnimationCaptureChecks() {}

    public static void prepare(Minecraft mc) {
        String hand=System.getProperty("steelclash.captureTwoHandedSwords"),camera=System.getProperty("steelclash.captureCameraMotion");
        if(hand!=null||camera!=null) SteelClash.LOGGER.info("Capture config before overrides: cameraMotion={} raw={} twoHandedSwords={} raw={}",
                Config.Client.CAMERA_MOTION.get(),Config.Client.CAMERA_MOTION.getRaw(),Config.Client.TWO_HANDED_SWORDS.get(),Config.Client.TWO_HANDED_SWORDS.getRaw());
        if(hand!=null) {
            if(!hand.equals("true")&&!hand.equals("false"))throw new IllegalArgumentException("captureTwoHandedSwords must be true or false");
            oldTwoHanded=Config.Client.TWO_HANDED_SWORDS.get();Config.Client.TWO_HANDED_SWORDS.set(Boolean.parseBoolean(hand));
        }
        if(camera!=null) {
            double value=Double.parseDouble(camera);
            if(!Double.isFinite(value)||value<0||value>2)throw new IllegalArgumentException("captureCameraMotion must be in [0,2]");
            oldCameraMotion=Config.Client.CAMERA_MOTION.get();Config.Client.CAMERA_MOTION.set(value);
        }
        String expected=System.getProperty("steelclash.captureAnimationHash");
        if(expected==null)return;
        try {
            String family=System.getProperty("steelclash.captureAnimationFamily","sword");
            var id=SteelClash.id("steelclash_animations/"+family+".json");
            var resource=mc.getResourceManager().getResource(id).orElseThrow();
            byte[] bytes;try(var input=resource.open()){bytes=input.readAllBytes();}
            String actual=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            if(!actual.equalsIgnoreCase(expected))throw new IllegalStateException("active animation hash mismatch: "+id+" from "+resource.sourcePackId()+" expected "+expected+" actual "+actual);
            AnimationSet parsed=AnimationSet.parse(JsonParser.parseString(new String(bytes,StandardCharsets.UTF_8)).getAsJsonObject());
            AnimationSet active=AnimationLibrary.INSTANCE.get(family);
            for(var entry:parsed.clips().entrySet())for(double progress:new double[]{0,.2,.5,.7,1}) {
                var wanted=entry.getValue().sample(progress);var shown=active.clip(entry.getKey()).sample(progress);
                if(!wanted.keySet().equals(shown.keySet()))throw new IllegalStateException("active animation channels differ from selected resource: "+entry.getKey());
                for(String channel:wanted.keySet())for(int axis=0;axis<3;axis++)
                    if(Math.abs(wanted.get(channel)[axis]-shown.get(channel)[axis])>1e-9)throw new IllegalStateException("active animation samples differ from selected resource: "+entry.getKey()+" "+channel);
            }
            SteelClash.LOGGER.info("Capture animation verified: {} source={} sha256={} generation={} cameraMotion={} twoHandedSwords={}",
                    id,resource.sourcePackId(),actual,AnimationLibrary.INSTANCE.generation(),Config.Client.CAMERA_MOTION.get(),Config.Client.TWO_HANDED_SWORDS.get());
        } catch(Exception error) {restore();throw new IllegalStateException("Capture animation verification failed",error);}
    }

    public static void restore() {
        if(oldTwoHanded!=null){Config.Client.TWO_HANDED_SWORDS.set(oldTwoHanded);oldTwoHanded=null;}
        if(oldCameraMotion!=null){Config.Client.CAMERA_MOTION.set(oldCameraMotion);oldCameraMotion=null;}
    }
}
