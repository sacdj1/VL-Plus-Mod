/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.render;

import meteordevelopment.meteorclient.events.game.OpenScreenEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ingame.HandledScreen;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * Two independent mechanisms, since they need different techniques:
 *
 * Inventory/Other Screens Scale override the window's real scale factor (Window.setScaleFactor
 * takes a double, unlike the integer-only vanilla GUI Scale option) while the corresponding kind
 * of screen is open, reverting to whatever the real GUI Scale option computes the moment it
 * closes. Only one of these (or neither) is ever in effect at once, tracked by currentTarget so
 * sync() only touches the real scale factor when the desired target actually changed - not on
 * every single screen transition regardless.
 *
 * Tab List Scale instead wraps the tab list's own render call in a plain matrix scale (see
 * InGameHudMixin's onRenderPlayerList hooks) - the tab list isn't a Screen at all (it's a HUD
 * overlay shown by holding a key, independent of screen state), so it was never covered by the
 * window-scale-factor mechanism above regardless of whether that was active.
 */
public class GuiScaleAdjust extends Module {
    private enum ScaleTarget {
        None,
        Inventory,
        OtherScreen
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> inventoryScaleEnabled = sgGeneral.add(new BoolSetting.Builder()
        .name("inventory-scale-enabled")
        .description("Overrides the GUI scale while an inventory-type screen (chest, crafting table, your own inventory, etc) is open.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> inventoryScale = sgGeneral.add(new DoubleSetting.Builder()
        .name("inventory-scale")
        .description("Scale factor used while an inventory-type screen is open. Fractional values work, unlike vanilla's own GUI Scale option.")
        .defaultValue(2)
        .min(0.5)
        .sliderRange(0.5, 10)
        .visible(inventoryScaleEnabled::get)
        .onChanged(v -> sync())
        .build()
    );

    private final Setting<Boolean> otherScreensScaleEnabled = sgGeneral.add(new BoolSetting.Builder()
        .name("other-screens-scale-enabled")
        .description("Overrides the GUI scale while any OTHER (non-inventory) screen is open - pause menu, mod menus, etc.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> otherScreensScale = sgGeneral.add(new DoubleSetting.Builder()
        .name("other-screens-scale")
        .description("Scale factor used while a non-inventory screen is open, when Other Screens Scale Enabled above is on.")
        .defaultValue(2)
        .min(0.5)
        .sliderRange(0.5, 10)
        .visible(otherScreensScaleEnabled::get)
        .onChanged(v -> sync())
        .build()
    );

    private final Setting<Boolean> tabListScaleEnabled = sgGeneral.add(new BoolSetting.Builder()
        .name("tab-list-scale-enabled")
        .description("Scales the real tab list (holding the player list key) independently of the GUI scale settings above - it's a HUD overlay, not a screen, so it needs its own separate control.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> tabListScale = sgGeneral.add(new DoubleSetting.Builder()
        .name("tab-list-scale")
        .description("Scale multiplier for the tab list, when Tab List Scale Enabled above is on.")
        .defaultValue(1.5)
        .min(0.1)
        .sliderRange(0.1, 5)
        .visible(tabListScaleEnabled::get)
        .build()
    );

    // currentTarget tracks which override (if any) is actually in effect right now, so sync()
    // only touches the real window scale factor when the desired target changed - not on every
    // single screen transition regardless. applying guards against reentrancy: onResolutionChanged()
    // notifies the current screen of a "resize", and at least one other mod's screen (confirmed
    // live: Sodium's options GUI) reacts to that by calling setScreen() again, which re-fires
    // OpenScreenEvent and would otherwise call back into this same method - infinite recursion
    // (StackOverflowError, crashed the game). Being already inside sync() short-circuits that
    // immediately instead.
    private ScaleTarget currentTarget = ScaleTarget.None;
    private boolean applying = false;

    public GuiScaleAdjust() {
        super(Categories.Render, "gui-scale-adjust", "Overrides the GUI scale per kind of screen (inventory vs everything else), plus a separate scale for the tab list overlay.");
    }

    @Override
    public void onDeactivate() {
        sync();
    }

    public double getTabListScale() {
        return tabListScaleEnabled.get() ? tabListScale.get() : 1.0;
    }

    private ScaleTarget desiredTarget() {
        if (!isActive()) return ScaleTarget.None;
        if (inventoryScaleEnabled.get() && mc.currentScreen instanceof HandledScreen) return ScaleTarget.Inventory;
        if (otherScreensScaleEnabled.get() && mc.currentScreen != null) return ScaleTarget.OtherScreen;
        return ScaleTarget.None;
    }

    private void sync() {
        if (applying) return;

        ScaleTarget desired = desiredTarget();
        if (desired == currentTarget) return;

        applying = true;
        try {
            switch (desired) {
                case Inventory -> {
                    mc.getWindow().setScaleFactor(inventoryScale.get());
                    mc.mouse.onResolutionChanged();
                }
                case OtherScreen -> {
                    mc.getWindow().setScaleFactor(otherScreensScale.get());
                    mc.mouse.onResolutionChanged();
                }
                case None -> mc.onResolutionChanged();
            }
            currentTarget = desired;
        } finally {
            applying = false;
        }
    }

    @EventHandler
    private void onOpenScreen(OpenScreenEvent event) {
        sync();
    }

    // Safety net on top of the event hook above: re-checks reality every tick and self-corrects,
    // so a single missed/misordered OpenScreenEvent can't leave the override permanently stuck out
    // of sync either way - within one tick it always matches whatever screen is actually current.
    @EventHandler
    private void onTick(TickEvent.Post event) {
        sync();
    }
}
