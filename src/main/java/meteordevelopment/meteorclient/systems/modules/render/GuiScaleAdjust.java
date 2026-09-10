/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.render;

import meteordevelopment.meteorclient.events.game.OpenScreenEvent;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ingame.HandledScreen;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * Overrides the window's real scale factor (Window.setScaleFactor takes a double, unlike the
 * integer-only vanilla GUI Scale option) while an inventory-type (HandledScreen) screen is open -
 * chests, crafting tables, your own inventory, etc - then reverts to whatever the real GUI Scale
 * option computes via MinecraftClient.onResolutionChanged() the moment that screen closes or a
 * different (non-inventory) screen replaces it. Already supports fractional values today (e.g.
 * 3.5), not just Minecraft's own whole-number steps - "smoother values" was the plan for later,
 * but Window.setScaleFactor already takes a double, so there's nothing coarser to build around.
 * Currently one shared scale for every inventory-type screen - a per-screen-type list is possible
 * later if that's wanted, but "mostly for inv size" is the one case that matters right now.
 */
public class GuiScaleAdjust extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> inventoryScale = sgGeneral.add(new DoubleSetting.Builder()
        .name("inventory-scale")
        .description("Scale factor used while an inventory-type screen (chest, crafting table, your own inventory, etc) is open. Fractional values work, unlike vanilla's own GUI Scale option.")
        .defaultValue(2)
        .min(0.5)
        .sliderRange(0.5, 10)
        .onChanged(v -> reapplyIfInventoryOpen())
        .build()
    );

    // overridden tracks whether OUR scale override is currently the one in effect, so revert()
    // only ever calls onResolutionChanged() when there's actually something to undo - not on every
    // single non-inventory screen open regardless. applying guards against reentrancy:
    // onResolutionChanged() notifies the current screen of a "resize", and at least one other mod's
    // screen (confirmed live: Sodium's options GUI) reacts to that by calling setScreen() again,
    // which re-fires OpenScreenEvent and would otherwise call back into this same method - infinite
    // recursion (StackOverflowError, crashed the game). Being already inside apply()/revert() short-
    // circuits that immediately instead.
    private boolean overridden = false;
    private boolean applying = false;

    public GuiScaleAdjust() {
        super(Categories.Render, "gui-scale-adjust", "Overrides the GUI scale while an inventory-type screen is open, independent of your normal GUI Scale option.");
    }

    @Override
    public void onDeactivate() {
        revert();
    }

    private void reapplyIfInventoryOpen() {
        if (isActive() && mc.currentScreen instanceof HandledScreen) apply();
    }

    private void apply() {
        if (applying) return;
        applying = true;

        try {
            mc.getWindow().setScaleFactor(inventoryScale.get());
            mc.mouse.onResolutionChanged();
            overridden = true;
        } finally {
            applying = false;
        }
    }

    private void revert() {
        if (!overridden || applying) return;
        applying = true;

        try {
            mc.onResolutionChanged();
            overridden = false;
        } finally {
            applying = false;
        }
    }

    @EventHandler
    private void onOpenScreen(OpenScreenEvent event) {
        if (!isActive()) return;

        if (event.screen instanceof HandledScreen) {
            apply();
        } else {
            revert();
        }
    }
}
