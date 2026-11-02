/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.misc;

import meteordevelopment.meteorclient.events.entity.player.AttackEntityEvent;
import meteordevelopment.meteorclient.events.entity.player.InteractBlockEvent;
import meteordevelopment.meteorclient.events.entity.player.InteractEntityEvent;
import meteordevelopment.meteorclient.events.entity.player.InteractItemEvent;
import meteordevelopment.meteorclient.events.entity.player.StartBreakingBlockEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;

/**
 * Blocks the button that activates your ability whenever it's still on cooldown, using the same
 * experienceProgress-as-cooldown convention AbilityCooldownHud/NoiseNotif/XPBarAdjust already
 * share on this server (0 = ready, >0 = on cooldown) - a "misfired" activation while on cooldown
 * never does anything server-side anyway, this just stops the client from sending it at all.
 *
 * Most weapons activate their ability on right-click (all three vanilla right-click paths -
 * interact block/entity/item), so that's what's blocked by default. Bows are the one exception on
 * this server: right-click is needed for the normal vanilla draw-and-release, so the ability
 * activation is flipped onto left-click instead - while a bow is held, left-click (attack
 * entity/block) is blocked instead of right-click, and right-click is left alone.
 */
public class NoMistakes extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> switchToSlot1OnPunch = sgGeneral.add(new BoolSetting.Builder()
        .name("switch-to-slot-1-on-punch")
        .description("Switches your hotbar to slot 1 every time you punch (attack an entity) - independent of Move To Slot 1 above, which only switches when an activation actually gets blocked by its own cooldown.")
        .defaultValue(false)
        .build()
    );

    // Bite Recovery - sometimes holding right-click on food never actually starts the real eat (no
    // arm/chewing animation), even though you're still holding the button down wanting to eat -
    // looks like the client's own "using an item" state just doesn't take, for reasons that aren't
    // clear from the client side (a rejected/instantly-reverted first attempt is the most likely
    // explanation, but this can't tell the difference from any other cause of the same symptom).
    // Detects that (button held on food, but not actually mid-use) and taps the interaction again
    // to nudge it into actually starting, instead of you holding down a button that's doing nothing.

    private final Setting<Boolean> biteRecoveryEnabled = sgGeneral.add(new BoolSetting.Builder()
        .name("bite-recovery")
        .description("If you're holding right-click on food but the eating animation never actually starts (a sign you're not really eating), briefly stops and restarts it to force a real attempt instead of holding down a button that isn't doing anything.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> biteRecoveryGraceTicks = sgGeneral.add(new IntSetting.Builder()
        .name("bite-recovery-grace-ticks")
        .description("How many ticks to wait after right-click goes down before deciding the animation really isn't playing (not just \"hasn't started yet this instant\").")
        .defaultValue(3)
        .min(1)
        .sliderRange(1, 20)
        .visible(biteRecoveryEnabled::get)
        .build()
    );

    private final Setting<Integer> biteRecoveryCooldown = sgGeneral.add(new IntSetting.Builder()
        .name("bite-recovery-cooldown")
        .description("Minimum time, in milliseconds, between recovery attempts - stops it retrying every single tick if it keeps failing to start.")
        .defaultValue(500)
        .min(50)
        .sliderRange(100, 3000)
        .visible(biteRecoveryEnabled::get)
        .build()
    );

    private int biteStuckTicks = 0;
    private long biteLastRetryMs = 0;

    public NoMistakes() {
        super(Categories.Cheese, "no-mistakes", "Blocks your ability's activation button while it's still on cooldown, so a mistimed click can't get wasted. Right-click for most weapons, left-click while holding a bow. Also recovers a stuck eating attempt (see Bite Recovery).");
        hidden = true;
    }

    private static boolean isFood(ItemStack stack) {
        return !stack.isEmpty() && stack.get(DataComponentTypes.FOOD) != null;
    }

    @EventHandler
    private void onTickBiteRecovery(TickEvent.Post event) {
        if (!biteRecoveryEnabled.get() || mc.player == null || mc.interactionManager == null || !mc.options.useKey.isPressed()) {
            biteStuckTicks = 0;
            return;
        }

        Hand hand = isFood(mc.player.getMainHandStack()) ? Hand.MAIN_HAND : isFood(mc.player.getOffHandStack()) ? Hand.OFF_HAND : null;
        if (hand == null || mc.player.isUsingItem()) {
            biteStuckTicks = 0;
            return;
        }

        biteStuckTicks++;
        if (biteStuckTicks < biteRecoveryGraceTicks.get()) return;

        long now = java.lang.System.currentTimeMillis();
        if (now - biteLastRetryMs < biteRecoveryCooldown.get()) return;

        mc.interactionManager.stopUsingItem(mc.player);
        mc.interactionManager.interactItem(mc.player, hand);

        biteLastRetryMs = now;
        biteStuckTicks = 0;
    }

    private boolean onCooldown() {
        return mc.player != null && mc.player.experienceProgress > 0.001f;
    }

    private boolean holdingBow() {
        return mc.player != null && PlayerUtils.abilityActivatesViaLeftClick(mc.player.getMainHandStack());
    }

    // An ability can only ever activate from hotbar slot 1 on this server, regardless of what
    // you're holding or doing - blocking an interaction/attack because "the ability is on
    // cooldown" while you're on some other slot was never actually protecting anything, since
    // that interaction could never have been an ability activation in the first place. That was
    // cancelling ordinary right-clicks (opening a chest, placing a block, etc.) any time your
    // ability happened to be on cooldown, purely because onCooldown() only checks the cooldown
    // value itself, never which slot you're actually on.
    private boolean inFirstSlot() {
        return mc.player != null && mc.player.getInventory().selectedSlot == 0;
    }

    @EventHandler
    private void onInteractBlock(InteractBlockEvent event) {
        if (inFirstSlot() && !holdingBow() && onCooldown()) event.cancel();
    }

    @EventHandler
    private void onInteractEntity(InteractEntityEvent event) {
        if (inFirstSlot() && !holdingBow() && onCooldown()) event.cancel();
    }

    @EventHandler
    private void onInteractItem(InteractItemEvent event) {
        if (inFirstSlot() && !holdingBow() && onCooldown()) event.toReturn = ActionResult.FAIL;
    }

    @EventHandler
    private void onAttackEntity(AttackEntityEvent event) {
        // Checked before the slot switch below, not after - otherwise this attack's own
        // cooldown/bow check would see whatever's in slot 1 instead of whatever was actually just
        // swung with.
        if (inFirstSlot() && holdingBow() && onCooldown()) event.cancel();

        // InvUtils.swap(), not a raw field assignment - that never actually tells the server the
        // slot changed (see FallCancel's identical fix/comment for the full explanation).
        if (switchToSlot1OnPunch.get() && mc.player != null) InvUtils.swap(0, false);
    }

    @EventHandler
    private void onStartBreakingBlock(StartBreakingBlockEvent event) {
        if (inFirstSlot() && holdingBow() && onCooldown()) event.cancel();
    }
}
