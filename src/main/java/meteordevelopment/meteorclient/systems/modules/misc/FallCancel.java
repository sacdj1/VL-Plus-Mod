/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.misc;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.misc.VenturelandGate;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.AxeItem;
import net.minecraft.item.BowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.MaceItem;
import net.minecraft.item.SwordItem;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

/**
 * While falling, fires whichever ability actually cancels fall damage for the weapon you're
 * holding, once you're close enough to the ground - the real mechanic here is nothing like a
 * single universal "right-click slot 1" button:
 * <p>
 * - Sword (Charge), Mace (Pounce), Whip (Grapple), Wand (Blink), Bow (Escape Shot) all live on
 *   hotbar slot 2 - you start the cast from slot 1 (right-click, left-click for a bow), then
 *   immediately swap to slot 2 to actually fire that specific ability, instead of slot 2 itself
 *   needing its own separate click.
 * - Scythe's Cyclone is the same idea, but on slot 3, always right-click.
 * - Axe has no ability that cancels fall damage at all - never triggers anything weapon-based.
 * - Sneak Power (a leggings enchant) is independent of whatever's in your hand - if equipped, this
 *   force-sneaks near the ground the whole time you're in range, in addition to (not instead of)
 *   whatever the held weapon can do, since it's the only option axes get at all.
 * - Whip's Grapple only actually cancels damage once its bobber physically reaches the ground, so
 *   it needs its own taller trigger height to have time to travel before you land.
 */
public class FallCancel extends Module {
    private enum WeaponType {
        Sword, Axe, Mace, Scythe, Whip, Wand, Bow, Unknown
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgKeywords = settings.createGroup("Keywords");

    private final Setting<Double> triggerHeight = sgGeneral.add(new DoubleSetting.Builder()
        .name("trigger-height")
        .description("Fires the ability once you're falling and this many blocks (or fewer) above the ground - give it enough room for the activation (and any windup/cast time it has) to actually finish before you'd land. Whip has its own taller height below, since Grapple needs travel time on top of that.")
        .defaultValue(4.0)
        .min(1)
        .sliderRange(1, 20)
        .build()
    );

    private final Setting<Double> whipTriggerHeight = sgGeneral.add(new DoubleSetting.Builder()
        .name("whip-trigger-height")
        .description("Trigger height specifically for Whip's Grapple, which only cancels fall damage once its bobber actually lands - needs to fire earlier/higher than every other weapon's instant ability to have time to travel down first.")
        .defaultValue(10.0)
        .min(1)
        .sliderRange(1, 30)
        .build()
    );

    private final Setting<Integer> maxGroundScan = sgGeneral.add(new IntSetting.Builder()
        .name("max-ground-scan")
        .description("How far down (in blocks) to search for the ground each tick while falling. A fall deeper than this just doesn't trigger yet - it starts checking again every tick once you're within range.")
        .defaultValue(64)
        .min(8)
        .sliderRange(8, 256)
        .build()
    );

    private final Setting<Boolean> sneakPowerEnabled = sgGeneral.add(new BoolSetting.Builder()
        .name("sneak-power")
        .description("Force-sneaks near the ground whenever your leggings have the Sneak Power enchant below - independent of whatever weapon you're holding, and the only thing that can save an Axe (which has no fall-cancelling ability of its own).")
        .defaultValue(true)
        .build()
    );

    // Keywords - this server's custom weapons/enchants are identified by name/lore text, not a
    // dedicated item type (same convention Target Indicator's own weapon keyword already uses).
    // Turns out that's true for ALL seven weapon types here, not just Scythe/Whip/Wand - Mace
    // (and presumably Sword/Axe/Bow too) isn't a real vanilla MaceItem on this server either, so
    // every type gets a keyword checked first, with the vanilla instanceof check kept only as a
    // fallback for the (apparently rare, if it happens at all) case of a genuinely vanilla item.

    private final Setting<String> swordKeyword = sgKeywords.add(new StringSetting.Builder()
        .name("sword-keyword")
        .description("Text (case-insensitive, in the held item's name/lore) that identifies a Sword. Leave empty to rely on it actually being a vanilla sword item instead.")
        .defaultValue("Sword")
        .build()
    );

    private final Setting<String> axeKeyword = sgKeywords.add(new StringSetting.Builder()
        .name("axe-keyword")
        .description("Text (case-insensitive, in the held item's name/lore) that identifies an Axe. Leave empty to rely on it actually being a vanilla axe item instead.")
        .defaultValue("Axe")
        .build()
    );

    private final Setting<String> maceKeyword = sgKeywords.add(new StringSetting.Builder()
        .name("mace-keyword")
        .description("Text (case-insensitive, in the held item's name/lore) that identifies a Mace. Leave empty to rely on it actually being a vanilla mace item instead.")
        .defaultValue("Mace")
        .build()
    );

    private final Setting<String> bowKeyword = sgKeywords.add(new StringSetting.Builder()
        .name("bow-keyword")
        .description("Text (case-insensitive, in the held item's name/lore) that identifies a Bow. Leave empty to rely on it actually being a vanilla bow item instead.")
        .defaultValue("Bow")
        .build()
    );

    private final Setting<String> scytheKeyword = sgKeywords.add(new StringSetting.Builder()
        .name("scythe-keyword")
        .description("Text (case-insensitive, in the held item's name/lore) that identifies a Scythe.")
        .defaultValue("Scythe")
        .build()
    );

    private final Setting<String> whipKeyword = sgKeywords.add(new StringSetting.Builder()
        .name("whip-keyword")
        .description("Text (case-insensitive, in the held item's name/lore) that identifies a Whip.")
        .defaultValue("Whip")
        .build()
    );

    private final Setting<String> wandKeyword = sgKeywords.add(new StringSetting.Builder()
        .name("wand-keyword")
        .description("Text (case-insensitive, in the held item's name/lore) that identifies a Wand.")
        .defaultValue("Wand")
        .build()
    );

    private final Setting<String> sneakPowerKeyword = sgKeywords.add(new StringSetting.Builder()
        .name("sneak-power-keyword")
        .description("Text (case-insensitive, in the equipped leggings' name/lore) that identifies the Sneak Power enchant.")
        .defaultValue("Sneak Power")
        .visible(sneakPowerEnabled::get)
        .build()
    );

    // Reset the moment you're not actively falling anymore (on ground, or moving upward/level), so
    // the NEXT fall gets its own fresh chance to trigger instead of staying permanently used up
    // after the first one. Only guards the one-shot weapon ability - force-sneaking for Sneak Power
    // isn't a one-shot action, so it isn't gated by this at all.
    private boolean triggeredThisFall = false;
    private boolean forcingSneak = false;

    public FallCancel() {
        super(Categories.Cheese, "fall-cancel", "Automatically fires whichever ability actually cancels fall damage for your current weapon, a set height above the ground while falling.");
        hidden = true;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!VenturelandGate.isAllowed()) {
            releaseSneak();
            toggle();
            return;
        }

        if (mc.player == null || mc.world == null) {
            releaseSneak();
            return;
        }

        if (mc.player.isOnGround() || mc.player.getVelocity().y >= 0) {
            triggeredThisFall = false;
            releaseSneak();
            return;
        }

        double groundDistance = distanceToGround();

        updateSneakPower(groundDistance);

        if (triggeredThisFall || groundDistance < 0) return;

        // On cooldown already means this would just get silently blocked (by No Mistakes, if
        // that's also on) or wasted (if not) - no point spending the one attempt this fall gets on
        // an activation that can't actually do anything yet.
        if (mc.player.experienceProgress > 0.001f) return;

        ItemStack weapon = mc.player.getMainHandStack();
        WeaponType type = weaponType(weapon);

        int targetSlot = switch (type) {
            case Sword, Mace, Whip, Wand, Bow -> 1; // Charge / Pounce / Grapple / Blink / Escape Shot
            case Scythe -> 2; // Cyclone
            case Axe, Unknown -> -1; // nothing fires this ability - Sneak Power above is all there is
        };
        if (targetSlot == -1) return;

        double height = type == WeaponType.Whip ? whipTriggerHeight.get() : triggerHeight.get();
        if (groundDistance > height) return;

        // InvUtils.swap(), not a raw field assignment - that only updates the client's own local
        // idea of which slot is selected, the server is never told and still thinks you're on
        // whatever slot you last actually scrolled/pressed a number key to. swap() calls vanilla's
        // own syncSelectedSlot() too, so the click below (and the swap onto targetSlot right after
        // it) both actually reach the server on the slot they're supposed to.
        if (mc.player.getInventory().selectedSlot != 0) InvUtils.swap(0, false);

        if (type == WeaponType.Bow) Utils.leftClick();
        else Utils.rightClick();

        InvUtils.swap(targetSlot, false);

        triggeredThisFall = true;
    }

    private void updateSneakPower(double groundDistance) {
        if (!sneakPowerEnabled.get() || mc.player == null) {
            releaseSneak();
            return;
        }

        boolean shouldSneak = groundDistance >= 0 && groundDistance <= triggerHeight.get() && hasSneakPower();

        if (shouldSneak && !forcingSneak) {
            mc.options.sneakKey.setPressed(true);
            forcingSneak = true;
        } else if (!shouldSneak && forcingSneak) {
            releaseSneak();
        }
    }

    private void releaseSneak() {
        if (!forcingSneak) return;

        mc.options.sneakKey.setPressed(false);
        forcingSneak = false;
    }

    private boolean hasSneakPower() {
        String keyword = sneakPowerKeyword.get();
        if (keyword.isEmpty()) return false;

        ItemStack leggings = mc.player.getEquippedStack(EquipmentSlot.LEGS);
        return !leggings.isEmpty() && hasKeyword(leggings, keyword);
    }

    private WeaponType weaponType(ItemStack stack) {
        if (stack.isEmpty()) return WeaponType.Unknown;

        // Keyword checked first for every type - none of this server's weapons turned out to be
        // their obvious vanilla item class (Mace included), so the keyword is the real check and
        // the instanceof below is just a fallback for a genuinely vanilla item slipping through.
        if (hasKeyword(stack, scytheKeyword.get())) return WeaponType.Scythe;
        if (hasKeyword(stack, whipKeyword.get())) return WeaponType.Whip;
        if (hasKeyword(stack, wandKeyword.get())) return WeaponType.Wand;
        if (hasKeyword(stack, maceKeyword.get())) return WeaponType.Mace;
        if (hasKeyword(stack, bowKeyword.get())) return WeaponType.Bow;
        if (hasKeyword(stack, axeKeyword.get())) return WeaponType.Axe;
        if (hasKeyword(stack, swordKeyword.get())) return WeaponType.Sword;

        if (stack.getItem() instanceof BowItem) return WeaponType.Bow;
        if (stack.getItem() instanceof MaceItem) return WeaponType.Mace;
        if (stack.getItem() instanceof AxeItem) return WeaponType.Axe;
        if (stack.getItem() instanceof SwordItem) return WeaponType.Sword;

        return WeaponType.Unknown;
    }

    private static boolean hasKeyword(ItemStack stack, String keyword) {
        if (keyword.isEmpty()) return false;
        String kw = keyword.toLowerCase();

        if (stack.getName().getString().toLowerCase().contains(kw)) return true;

        LoreComponent lore = stack.get(DataComponentTypes.LORE);
        if (lore != null) {
            for (Text line : lore.lines()) {
                if (line.getString().toLowerCase().contains(kw)) return true;
            }
        }

        return false;
    }

    /** Blocks straight down from the player's feet to the first solid collision, or -1 if none found within maxGroundScan. */
    private double distanceToGround() {
        BlockPos.Mutable pos = new BlockPos.Mutable();
        double feetY = mc.player.getY();
        int startY = (int) Math.floor(feetY);
        int x = mc.player.getBlockX(), z = mc.player.getBlockZ();

        for (int i = 0; i <= maxGroundScan.get(); i++) {
            int y = startY - i;
            pos.set(x, y, z);

            if (!mc.world.getBlockState(pos).getCollisionShape(mc.world, pos).isEmpty()) {
                return feetY - (y + 1);
            }
        }

        return -1;
    }
}
