package com.steelclash.gametest;

import com.steelclash.combat.Combat;
import com.steelclash.combat.CombatData;
import com.steelclash.combat.ModAttachments;
import com.steelclash.compat.Compat;
import com.steelclash.core.AttackType;
import com.steelclash.entity.ModEntities;
import com.steelclash.entity.TrainingDummy;
import com.steelclash.profile.WeaponProfiles;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * Shared GameTest helpers. Coordinates are relative to the structure block; the arena floor is at y = 1, so everyone
 * stands at y = 2. The attacker stands at (1.5, 2, 4.5); targets stand at x = 3.
 */
final class TestSupport {
    static final float FACING_POSITIVE_X = -90f;
    static final float FACING_NEGATIVE_X = 90f;

    private TestSupport() {
    }

    static Player swordsman(GameTestHelper helper, ItemStack weapon, float yaw) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Vec3 pos = helper.absoluteVec(new Vec3(1.5, 2, 4.5));
        player.moveTo(pos.x, pos.y, pos.z, yaw, 0);
        player.setYHeadRot(yaw);
        player.setItemInHand(InteractionHand.MAIN_HAND, weapon);
        // Mock players never tick, so apply the weapon's attribute modifiers (damage, speed, reach) by hand.
        weapon.forEachModifier(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
            AttributeInstance instance = player.getAttribute(attribute);
            if (instance != null && !instance.hasModifier(modifier.id())) {
                instance.addTransientModifier(modifier);
            }
        });
        return player;
    }

    /** A passive training dummy (holding an iron sword) standing at block (x, 2, z), facing {@code yaw}. */
    static TrainingDummy dummy(GameTestHelper helper, int x, int z, float yaw) {
        TrainingDummy dummy = helper.spawn(ModEntities.TRAINING_DUMMY.get(), x, 2, z);
        face(dummy, yaw);
        return dummy;
    }

    static void face(LivingEntity entity, float yaw) {
        entity.setYRot(yaw);
        entity.setYHeadRot(yaw);
        entity.setYBodyRot(yaw);
        entity.setXRot(0);
    }

    /** Requests an attack and runs it to completion (mock players aren't ticked by the level). */
    static void swing(LivingEntity attacker, AttackType type) {
        CombatData data = attacker.getData(ModAttachments.COMBAT);
        Combat.requestAttack(attacker, type);
        for (int tick = 0; tick < 200 && data.machine.isAttacking(); tick++) {
            Combat.tickServer(attacker, data);
        }
    }

    static CombatData data(LivingEntity entity) {
        return entity.getData(ModAttachments.COMBAT);
    }

    static boolean isHurt(LivingEntity entity) {
        return entity.getHealth() < entity.getMaxHealth();
    }

    static ItemStack spartan(String path) {
        return new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(Compat.SPARTAN_WEAPONRY.modId(), path)));
    }

    static ItemStack spartanShield(String path) {
        return new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(Compat.SPARTAN_SHIELDS.modId(), path)));
    }

    static void expectProfile(GameTestHelper helper, ItemStack stack, String expected) {
        Optional<ResourceKey<?>> actual = WeaponProfiles.resolve(stack, helper.getLevel().registryAccess())
                .map(WeaponProfiles.Resolved::key);
        Optional<ResourceKey<?>> wanted = Optional.ofNullable(expected).map(WeaponProfiles::key);
        check(helper, actual.equals(wanted), stack + " resolved to " + actual + ", expected " + wanted);
    }

    static void check(GameTestHelper helper, boolean condition, String message) {
        if (!condition) {
            helper.fail(message);
        }
    }
}
