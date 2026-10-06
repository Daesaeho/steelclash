package com.steelclash.combat;

import com.steelclash.Config;
import com.steelclash.ai.BrainState;
import com.steelclash.core.AttackType;
import com.steelclash.core.CombatStateMachine;
import com.steelclash.core.Stamina;
import com.steelclash.profile.WeaponProfile;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Per-entity combat state, stored as a transient data attachment on any {@code LivingEntity} that fights.
 * Exists on both sides: the server copy is authoritative, client copies drive animation, HUD and debug rendering.
 */
public class CombatData {
    public final CombatStateMachine machine = new CombatStateMachine();
    public final Stamina stamina = new Stamina(Config.MAX_STAMINA.get().floatValue());

    /** Profile of the weapon the current attack or parry was started with. */
    @Nullable
    public ResourceKey<WeaponProfile> profileKey;

    /** Attack input buffered during recovery; starts as soon as the fighter is free. */
    @Nullable
    public AttackType queuedAttack;
    public int queuedVariant;
    public boolean queuedMirrored;

    // ---- server only ----
    /** Weapon the attack was started with; switching weapons cancels the attack. */
    public ItemStack weapon = ItemStack.EMPTY;
    /** Entities already hit by the current swing (each target is hit at most once per swing). */
    public final IntSet hitThisSwing = new IntOpenHashSet();
    /** View and pivot at the end of the previous tick, so each tick's sweep interpolates the wielder's turning. */
    public float prevYaw;
    public float prevPitch;
    public Vec3 prevPivot = Vec3.ZERO;
    /** Current attack was started while sprinting (extra reach and damage, forward push). */
    public boolean lunge;
    /** Current attack is an overhead started in mid-air. */
    public boolean jumpAttack;
    /** Game time when the weapon special is off cooldown. */
    public long specialReadyAt;
    /** Weapon unusable until this game time (HOLSTER disarm mode for mobs). */
    public long holsteredUntil;
    public float lastSentStamina = -1;

    // ---- mob decision making ----
    /** Bot brain state (spacing, plans, opponent memory); created on first use. */
    @Nullable
    public BrainState brain;

    /** The attack (attacker id + serial) this mob already decided how to answer, so it rolls once per attack. */
    public int reactedAttackerId = -1;
    public int reactedSerial = -1;
    public boolean willParry;

    public void resetSwing() {
        hitThisSwing.clear();
    }
}
