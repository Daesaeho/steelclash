package com.steelclash.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.steelclash.Config;
import com.steelclash.SteelClash;
import com.steelclash.combat.Combat;
import com.steelclash.combat.CombatData;
import com.steelclash.combat.ModAttachments;
import com.steelclash.core.AttackType;
import com.steelclash.core.Phase;
import com.steelclash.net.ActionPayload;
import com.steelclash.net.AttackInputPayload;
import com.steelclash.net.BlockInputPayload;
import com.steelclash.profile.WeaponProfiles;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * Turns mouse input into attacks while a profiled weapon is held.
 * <p>
 * Left click is intercepted in {@link InputEvent.MouseButton.Pre}, before Minecraft registers the click on any
 * {@link KeyMapping}. That stops vanilla attacking and block mining, and also stops Spartan Shields from reading
 * the click as a shield bash (see docs/spikes.md (c)). Sneak + left click on a block still mines.
 */
@EventBusSubscriber(modid = SteelClash.MOD_ID, value = Dist.CLIENT)
public final class ClientInput {
    private static final String CATEGORY = "key.categories.steelclash";
    /** Extra blocks that right-click should interact with instead of parrying (for modded blocks). */
    private static final TagKey<Block> INTERACTABLE = TagKey.create(Registries.BLOCK, SteelClash.id("interactable"));

    public static final KeyMapping OVERHEAD = new KeyMapping("key.steelclash.overhead",
            InputConstants.Type.MOUSE, GLFW.GLFW_MOUSE_BUTTON_5, CATEGORY);
    public static final KeyMapping STAB = new KeyMapping("key.steelclash.stab",
            InputConstants.Type.MOUSE, GLFW.GLFW_MOUSE_BUTTON_4, CATEGORY);
    public static final KeyMapping FEINT = new KeyMapping("key.steelclash.feint",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_X, CATEGORY);
    public static final KeyMapping KICK = new KeyMapping("key.steelclash.kick",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Z, CATEGORY);

    /** Which held input started the current windup; still holding it past {@link Combat#HEAVY_HOLD_TICKS} = heavy. */
    private enum Hold {
        NONE, ATTACK_BUTTON, OVERHEAD_KEY, STAB_KEY
    }

    private static Hold hold = Hold.NONE;
    private static boolean attackButtonDown;

    /** Whether we swallowed the attack button's press, so we also swallow its release. */
    private static boolean suppressedAttackRelease;
    /** Same for the use (block) button. */
    private static boolean suppressedUseRelease;

    private ClientInput() {
    }

    /** Mod-bus listener, registered from {@code SteelClashClient}. */
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(OVERHEAD);
        event.register(STAB);
        event.register(FEINT);
        event.register(KICK);
    }

    @SubscribeEvent
    static void onMouseButton(InputEvent.MouseButton.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.keyUse.matchesMouse(event.getButton())) {
            onUseButton(event, mc);
            return;
        }
        if (!mc.options.keyAttack.matchesMouse(event.getButton())) {
            return;
        }
        if (event.getAction() == GLFW.GLFW_RELEASE) {
            attackButtonDown = false;
            if (suppressedAttackRelease) {
                suppressedAttackRelease = false;
                event.setCanceled(true);
            }
            return;
        }
        if (event.getAction() != GLFW.GLFW_PRESS || !inGame(mc) || !holdsWeapon(mc.player)) {
            return;
        }
        if (mc.player.isShiftKeyDown() && mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.BLOCK) {
            return; // let vanilla mine
        }
        tryAttack(mc.player, AttackType.SLASH, Hold.ATTACK_BUTTON);
        attackButtonDown = true;
        suppressedAttackRelease = true;
        event.setCanceled(true);
    }

    /**
     * Right click = weapon parry, as in Chivalry 2. Falls through to vanilla for: sneaking (interact), a shield in the
     * offhand (vanilla shield guard), items with their own use action (bows, tridents, throwables until the throw key
     * exists), and right-clicking things you'd want to use (doors, chests, villagers, mounts).
     */
    private static void onUseButton(InputEvent.MouseButton.Pre event, Minecraft mc) {
        if (event.getAction() == GLFW.GLFW_RELEASE) {
            if (suppressedUseRelease) {
                suppressedUseRelease = false;
                event.setCanceled(true);
                LocalPlayer player = mc.player;
                if (player != null) {
                    player.getData(ModAttachments.COMBAT).machine.releaseParry();
                }
                PacketDistributor.sendToServer(new BlockInputPayload(false));
            }
            return;
        }
        if (event.getAction() != GLFW.GLFW_PRESS || !inGame(mc) || !holdsWeapon(mc.player) || !parriesOnUse(mc)) {
            return;
        }
        LocalPlayer player = mc.player;
        CombatData data = player.getData(ModAttachments.COMBAT);
        if (player.isUsingItem()) {
            player.stopUsingItem();
        }
        Combat.startParry(player, data); // prediction; the server decides
        PacketDistributor.sendToServer(new BlockInputPayload(true));
        suppressedUseRelease = true;
        event.setCanceled(true);
    }

    private static boolean parriesOnUse(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player.isShiftKeyDown() || player.getOffhandItem().canPerformAction(ItemAbilities.SHIELD_BLOCK)) {
            return false;
        }
        ItemStack held = player.getMainHandItem();
        UseAnim anim = held.getUseAnimation();
        if (anim != UseAnim.NONE && anim != UseAnim.BLOCK) {
            return false;
        }
        HitResult hit = mc.hitResult;
        if (hit instanceof EntityHitResult entityHit) {
            return entityHit.getEntity() instanceof Enemy;
        }
        if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
            BlockState state = mc.level.getBlockState(blockHit.getBlockPos());
            return !isInteractable(state, blockHit);
        }
        return true;
    }

    private static boolean isInteractable(BlockState state, BlockHitResult hit) {
        Minecraft mc = Minecraft.getInstance();
        return state.getMenuProvider(mc.level, hit.getBlockPos()) != null
                || mc.level.getBlockEntity(hit.getBlockPos()) != null
                || state.is(BlockTags.DOORS) || state.is(BlockTags.TRAPDOORS) || state.is(BlockTags.FENCE_GATES)
                || state.is(BlockTags.BUTTONS) || state.is(BlockTags.BEDS) || state.getBlock() instanceof LeverBlock
                || state.is(INTERACTABLE);
    }

    @SubscribeEvent
    static void onScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (!Config.Client.SCROLL_ATTACKS.get() || !inGame(mc) || !holdsWeapon(mc.player) || event.getScrollDeltaY() == 0) {
            return;
        }
        tryAttack(mc.player, event.getScrollDeltaY() > 0 ? AttackType.OVERHEAD : AttackType.STAB, Hold.NONE);
        event.setCanceled(true);
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        while (OVERHEAD.consumeClick()) {
            if (inGame(mc) && holdsWeapon(mc.player)) {
                tryAttack(mc.player, AttackType.OVERHEAD, Hold.OVERHEAD_KEY);
            }
        }
        while (STAB.consumeClick()) {
            if (inGame(mc) && holdsWeapon(mc.player)) {
                tryAttack(mc.player, AttackType.STAB, Hold.STAB_KEY);
            }
        }
        while (KICK.consumeClick()) {
            if (inGame(mc)) {
                tryAttack(mc.player, AttackType.KICK, Hold.NONE);
            }
        }
        LocalPlayer player = mc.player;
        if (player == null || !player.hasData(ModAttachments.COMBAT)) {
            return;
        }
        CombatData data = player.getData(ModAttachments.COMBAT);
        while (FEINT.consumeClick()) {
            if (inGame(mc) && Combat.feint(player, data)) {
                hold = Hold.NONE;
                PacketDistributor.sendToServer(new ActionPayload(ActionPayload.Action.FEINT));
            }
        }
        // Still holding the attack that started this windup: it becomes a heavy.
        if (data.machine.phase() != Phase.WINDUP) {
            hold = Hold.NONE;
        } else if (hold != Hold.NONE && !data.machine.isHeavy() && data.machine.phaseTick() >= Combat.HEAVY_HOLD_TICKS) {
            if (isHeld(hold) && Combat.makeHeavy(player, data)) {
                PacketDistributor.sendToServer(new ActionPayload(ActionPayload.Action.HEAVY));
            }
            hold = Hold.NONE;
        }
    }

    /** The input that started the current windup is still held, so the windup may still become a heavy. */
    public static boolean isChargingHeavy() {
        return hold != Hold.NONE && isHeld(hold);
    }

    private static boolean isHeld(Hold source) {
        return switch (source) {
            case ATTACK_BUTTON -> attackButtonDown;
            case OVERHEAD_KEY -> OVERHEAD.isDown();
            case STAB_KEY -> STAB.isDown();
            case NONE -> false;
        };
    }

    /**
     * Predicts the attack locally and tells the server. A different attack during a windup is a morph; during recovery
     * the input starts a combo after a landed hit, otherwise it's buffered (both here and on the server).
     */
    private static void tryAttack(LocalPlayer player, AttackType type, Hold source) {
        CombatData data = player.getData(ModAttachments.COMBAT);
        Phase phase = data.machine.phase();
        if (phase == Phase.WINDUP) {
            if (type != data.machine.type() && Combat.morph(player, data, type)) {
                hold = source;
                PacketDistributor.sendToServer(new AttackInputPayload(type));
            }
            return;
        }
        if (data.machine.canStartAttack()) {
            if (player.isUsingItem()) {
                player.stopUsingItem();
            }
            if (!Combat.start(player, data, type)) {
                return;
            }
            hold = source;
        } else if (phase == Phase.RECOVERY || phase == Phase.GUARD_RECOVERY) {
            data.queuedAttack = type;
        } else {
            return;
        }
        PacketDistributor.sendToServer(new AttackInputPayload(type));
    }

    private static boolean inGame(Minecraft mc) {
        return mc.player != null && mc.screen == null && mc.getOverlay() == null && mc.mouseHandler.isMouseGrabbed()
                && !mc.player.isSpectator();
    }

    static boolean holdsWeapon(LocalPlayer player) {
        return WeaponProfiles.resolve(player.getMainHandItem(), player.level().registryAccess()).isPresent();
    }
}
