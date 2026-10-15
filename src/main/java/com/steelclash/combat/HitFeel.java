package com.steelclash.combat;

import com.steelclash.Config;
import com.steelclash.SteelClash;
import com.steelclash.core.AttackType;
import com.steelclash.core.CollisionProvenance;
import com.steelclash.core.CombatStateMachine;
import com.steelclash.core.DamageType;
import com.steelclash.core.HitLocation;
import com.steelclash.net.HitReactionPayload;
import com.steelclash.net.ModNetwork;
import com.steelclash.sound.ModSounds;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * What a blade does where it lands, from the contact {@link SwingTracer} traced: blood sprays from that point the way
 * the blade travelled, the hit rings like the armour on the part it met, a killing blow throws the body along the
 * blade and may take the head, and watching players are told so the body flinches ({@link HitReactionPayload}).
 * A parried blade bounces back from where it met the guard. Server thread only.
 */
public final class HitFeel {
    /** Bodies with a head a blade can take off (and a model the client can hide it on). */
    public static final TagKey<EntityType<?>> CAN_LOSE_HEAD = TagKey.create(Registries.ENTITY_TYPE, SteelClash.id("can_lose_head"));
    /** The last blow each body took, for tests and debugging. Weak, so bodies that are gone are forgotten. */
    private static final Map<LivingEntity, Impact> LAST = new WeakHashMap<>();
    /** Where each defender's guard last turned a traced blade aside. */
    private static final Map<LivingEntity, Vec3> LAST_PARRY = new WeakHashMap<>();
    /** The serial of the attack each attacker last had bounce off a guard. */
    private static final Map<LivingEntity, Integer> LAST_BOUNCE = new WeakHashMap<>();
    private static final Map<LivingEntity, BounceMetadata> LAST_BOUNCE_METADATA = new WeakHashMap<>();

    /** What the armour on the struck part is made of, as far as sound goes. */
    public enum Armour {
        NONE, MAIL, PLATE
    }

    /**
     * One blow: the current body projection, historical body projection and raw collision entry, the blade's travel,
     * the part and armour it met, and whether it killed or took the head.
     */
    public record Impact(Vec3 point, Vec3 capturedPoint, Vec3 entryPoint, Vec3 motion, HitLocation.Region region, Armour armour, boolean killed, boolean severed,
                         CombatStateMachine.AttackIdentity attackIdentity, long serverTick, double progress,
                         @Nullable Vec3 normal, CollisionProvenance collisionProvenance) {
    }

    /** Captured server contact data plus the release position used to show a delayed parry bounce. */
    public record BounceMetadata(CombatStateMachine.AttackIdentity attackIdentity, long serverTick,
                                 double capturedProgress, double bounceProgress) {
    }

    /**
     * The body as the blade met it, taken before the blow lands: the part, the armour over it and whether the head is
     * bare. A blow that kills drops the body's gear as it dies (a player's always, a mob's by chance), so looking
     * afterwards would find an empty helmet slot on a helmeted head.
     */
    record Struck(HitLocation.Region region, Armour armour, boolean bareHead) {
    }

    private HitFeel() {
    }

    /** {@link Struck} for a blow about to land on {@code target} at {@code contact} (the chest without one). */
    static Struck struck(LivingEntity target, @Nullable SwingTracer.Contact contact) {
        HitLocation.Region region = contact == null ? HitLocation.Region.TORSO : region(target, contact.point());
        return new Struck(region, armour(target, region), target.getItemBySlot(EquipmentSlot.HEAD).isEmpty());
    }

    /** The last blow {@code target} took from a traced swing, or null. */
    @Nullable
    public static Impact lastImpact(LivingEntity target) {
        return LAST.get(target);
    }

    /** Where {@code defender}'s guard last turned a traced blade aside, or null. */
    @Nullable
    public static Vec3 lastParry(LivingEntity defender) {
        return LAST_PARRY.get(defender);
    }

    /** The serial of the attack whose blade last bounced off a guard for {@code attacker}, or null. */
    @Nullable
    public static Integer lastBounce(LivingEntity attacker) {
        return LAST_BOUNCE.get(attacker);
    }

    @Nullable
    public static BounceMetadata lastBounceMetadata(LivingEntity attacker) {
        return LAST_BOUNCE_METADATA.get(attacker);
    }

    /**
     * A swing's blow hurt {@code target}, which was {@code struck} as it landed. Without a traced contact (or with
     * tracing off) it is the plain hit feedback.
     */
    static void hit(LivingEntity attacker, LivingEntity target, Combat.Hit hit, Struck struck) {
        SwingTracer.Contact contact = hit.contact();
        if (contact == null || !Config.TRACED_CONTACTS.get()) {
            Feedback.hit(attacker, target, hit.heavy(), contact);
            return;
        }
        Vec3 point = contact.point();
        Vec3 motion = travel(contact, attacker, target);
        HitLocation.Region region = struck.region();
        boolean killed = target.isDeadOrDying();
        DamageType damageType = hit.damageType();
        // The head is only ever found with hitLocations on (region() says chest otherwise).
        boolean severed = killed && region == HitLocation.Region.HEAD && Config.DECAPITATION.get()
                && (damageType == DamageType.CUT || damageType == DamageType.CHOP)
                && hit.type() != AttackType.STAB && hit.type() != AttackType.JAB
                && target.getType().is(CAN_LOSE_HEAD) && struck.bareHead();
        Armour armour = struck.armour();
        LAST.put(target, new Impact(point, contact.capturedPoint(), contact.entryPoint(), motion, region, armour, killed, severed, contact.attackIdentity(),
                contact.serverTick(), contact.progress(), contact.normal(), contact.collisionProvenance()));

        Feedback.sound(target, point, ModSounds.HIT, 1f, 1f);
        if (armour != Armour.NONE && Config.ARMOUR_HIT_SOUNDS.get()) {
            Feedback.sound(target, point, armour == Armour.PLATE ? ModSounds.HIT_PLATE : ModSounds.HIT_MAIL, 1f, pitch(damageType));
        }
        if (severed) {
            Feedback.sound(target, point, ModSounds.SEVER, 1f, 1f);
        }
        boolean blood = Config.BLOOD_PARTICLES.get();
        if (blood) {
            Feedback.blood(target, point, hit.heavy() ? 14 : 8); // the spray along the blade is the clients' (payload)
        }
        if (killed && Config.DEATH_PUSH.get() > 0) {
            Vec3 across = new Vec3(motion.x, 0, motion.z);
            Vec3 push = across.lengthSqr() < 1e-6 ? Vec3.ZERO : across.normalize().scale(Config.DEATH_PUSH.get());
            target.push(push.x, 0.1 * Config.DEATH_PUSH.get(), push.z);
            target.hurtMarked = true;
        }
        HitReactionPayload.Kind kind = severed ? HitReactionPayload.Kind.SEVER
                : killed ? HitReactionPayload.Kind.KILL : HitReactionPayload.Kind.HIT;
        int spray = !blood ? 0 : severed ? 16 : hit.heavy() ? 7 : 4;
        ModNetwork.sendToTrackingAndSelf(target, payload(target, kind, region, point, motion, hit.heavy() ? 1.4f : 1f,
                0, contact, contact.attackIdentity().serial(), spray));
        Feedback.landed(attacker, target, hit.heavy(), contact);
    }

    /**
     * A weapon guard turned this swing's blade aside: sparks where it met the guard, and watching players see the
     * attacker's blade bounce back along its path from there. A special isn't turned back (its attacker keeps the
     * initiative and isn't staggered), so it only sparks.
     *
     * @return whether the parry was shown from the traced contact (otherwise the caller shows the plain one)
     */
    static boolean parried(LivingEntity defender, Entity attacker) {
        SwingContext.Active swing = SwingContext.forAttacker(attacker);
        SwingTracer.Contact contact = swing == null ? null : swing.contact();
        if (contact == null || !Config.TRACED_CONTACTS.get() || !Config.PARRY_BOUNCE.get()) {
            return false;
        }
        // The guard is held in front of the body, between it and the blade.
        Vec3 toAttacker = attacker.getEyePosition().subtract(contact.point());
        Vec3 across = new Vec3(toAttacker.x, 0, toAttacker.z);
        Vec3 point = across.lengthSqr() < 1e-6 ? contact.point() : contact.point().add(across.normalize().scale(0.3));
        LAST_PARRY.put(defender, point);
        Feedback.parried(defender, attacker, point);
        if (swing.type() != AttackType.SPECIAL && attacker instanceof LivingEntity living && living.hasData(ModAttachments.COMBAT)) {
            int serial = contact.attackIdentity().serial();
            LAST_BOUNCE.put(living, serial);
            LAST_BOUNCE_METADATA.put(living, new BounceMetadata(contact.attackIdentity(), contact.serverTick(),
                    contact.progress(), swing.releaseProgress()));
            ModNetwork.sendToTrackingAndSelf(attacker, payload(attacker, HitReactionPayload.Kind.BOUNCE,
                    HitLocation.Region.TORSO, point, travel(contact, attacker, defender), 1f, swing.releaseProgress(),
                    contact, serial, 0));
        }
        return true;
    }

    /** The blade's normalised travel at the contact; straight from the attacker to the target if it wasn't moving. */
    private static Vec3 travel(SwingTracer.Contact contact, Entity attacker, Entity target) {
        Vec3 motion = contact.motion();
        if (motion.lengthSqr() < 1e-8) {
            motion = target.position().subtract(attacker.position());
        }
        return motion.lengthSqr() < 1e-8 ? new Vec3(0, -1, 0) : motion.normalize();
    }

    static HitLocation.Region region(LivingEntity target, Vec3 point) {
        if (!Config.HIT_LOCATIONS.get()) {
            return HitLocation.Region.TORSO;
        }
        return HitLocation.classify(point.x - target.getX(), point.y - target.getY(), point.z - target.getZ(),
                target.yBodyRot, target.getBbHeight(), target.getBbWidth(), target.getEyeHeight());
    }

    /** The armour worn over the part: the helmet for the head, leggings for the legs, the chestplate otherwise. */
    static Armour armour(LivingEntity target, HitLocation.Region region) {
        EquipmentSlot slot = region == HitLocation.Region.HEAD ? EquipmentSlot.HEAD
                : region.isLeg() ? EquipmentSlot.LEGS : EquipmentSlot.CHEST;
        ItemStack worn = target.getItemBySlot(slot);
        if (!(worn.getItem() instanceof ArmorItem armor)) {
            return Armour.NONE;
        }
        ArmorMaterial material = armor.getMaterial().value();
        if (material == ArmorMaterials.LEATHER.value() || material == ArmorMaterials.TURTLE.value()
                || material == ArmorMaterials.ARMADILLO.value()) {
            return Armour.NONE;
        }
        return material == ArmorMaterials.CHAIN.value() ? Armour.MAIL : Armour.PLATE;
    }

    /** Blunt weapons ring lowest, cuts highest. */
    private static float pitch(@Nullable DamageType type) {
        if (type == null) {
            return 1f;
        }
        return switch (type) {
            case BLUNT -> 0.75f;
            case CHOP -> 0.9f;
            case CUT -> 1.15f;
            case PIERCE -> 1.05f;
        };
    }

    private static HitReactionPayload payload(Entity entity, HitReactionPayload.Kind kind, HitLocation.Region region,
                                              Vec3 point, Vec3 motion, float strength, double progress,
                                              SwingTracer.Contact contact, int serial, int spray) {
        return new HitReactionPayload(entity.getId(), kind, region.ordinal(), point.x, point.y,
                point.z, (float) motion.x, (float) motion.y, (float) motion.z, strength, progress,
                contact.progress(), serial, contact.attackIdentity(), contact.serverTick(), contact.capturedPoint(),
                contact.entryPoint(), contact.normal(),
                contact.collisionProvenance(), spray);
    }
}
