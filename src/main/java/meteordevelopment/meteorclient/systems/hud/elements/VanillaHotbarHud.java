/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.hud.elements;

import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;

/**
 * Doesn't draw anything itself - just a draggable/scalable anchor. InGameHudMixin redirects
 * vanilla's own hotbar draw (background, selection, offhand, items, attack indicator - all of it,
 * pixel-perfect since it's still vanilla's own code running) to this element's position/scale
 * whenever it's present in the HUD, instead of reimplementing that rendering from scratch.
 */
public class VanillaHotbarHud extends HudElement {
    public static final HudElementInfo<VanillaHotbarHud> INFO = new HudElementInfo<>(Hud.VANILLA_GROUP, "vanilla-hotbar", "Moves and scales the real hotbar (items, selection, offhand slot, attack indicator) - reported not working correctly, under investigation.", VanillaHotbarHud::new);

    // The hotbar's real box is a wide 182x22 rectangle - at rotation, the HUD editor's normal
    // rotation-aware outline (matching that exact aspect ratio) turns into a thin, hard-to-grab
    // sliver near 90 degrees. Reporting true here instead gives it the same small, fixed-size
    // square marker other own-background elements use, which looks and handles the same at any
    // rotation regardless of the real element's own aspect ratio.
    @Override
    public boolean hasOwnBackground() {
        return true;
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> hide = sgGeneral.add(new BoolSetting.Builder()
        .name("hide")
        .description("Hides the real hotbar entirely instead of repositioning it - use this on the placed element to remove vanilla's own hotbar from a spot while still keeping this element for something else, or just to hide it outright.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> scale = sgGeneral.add(new DoubleSetting.Builder()
        .name("scale")
        .description("Size multiplier over vanilla's normal size.")
        .defaultValue(1.0)
        .min(0.1)
        .sliderRange(0.1, 20)
        .onChanged(v -> calculateSize())
        .build()
    );

    private final Setting<Integer> rotation = sgGeneral.add(new IntSetting.Builder()
        .name("rotation")
        .description("Rotates the whole hotbar around its own center, in degrees clockwise.")
        .defaultValue(0)
        .range(-359, 359)
        .sliderRange(-359, 359)
        .build()
    );

    private final Setting<Boolean> keepItemsUpright = sgGeneral.add(new BoolSetting.Builder()
        .name("keep-items-upright")
        .description("Counter-rotates each item icon so they stay upright even while the hotbar itself is rotated - only the row layout rotates, not the icons themselves.")
        .defaultValue(true)
        .visible(() -> rotation.get() != 0)
        .build()
    );

    // Three independent alphas instead of the one general Alpha above (which still applies on top
    // of these, and is the only thing affecting the attack indicator specifically, since that's not
    // split out separately here).

    private final Setting<Boolean> singleAlpha = sgGeneral.add(new BoolSetting.Builder()
        .name("single-alpha")
        .description("Use just the one general Alpha for the whole hotbar (items, frame and selector together) instead of the three separate ones below.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> itemAlpha = sgGeneral.add(new IntSetting.Builder()
        .name("item-alpha")
        .description("Transparency of the item icons themselves - 255 is fully opaque, 0 is invisible.")
        .defaultValue(255)
        .visible(() -> !singleAlpha.get())
        .range(0, 255)
        .sliderRange(0, 255)
        .build()
    );

    private final Setting<Integer> backgroundAlpha = sgGeneral.add(new IntSetting.Builder()
        .name("background-alpha")
        .description("Transparency of the hotbar's own frame texture (and the offhand slot frame) - 255 is fully opaque, 0 is invisible.")
        .defaultValue(255)
        .visible(() -> !singleAlpha.get())
        .range(0, 255)
        .sliderRange(0, 255)
        .build()
    );

    private final Setting<Integer> selectorAlpha = sgGeneral.add(new IntSetting.Builder()
        .name("selector-alpha")
        .description("Transparency of the selected-slot highlight - 255 is fully opaque, 0 is invisible.")
        .defaultValue(255)
        .visible(() -> !singleAlpha.get())
        .range(0, 255)
        .sliderRange(0, 255)
        .build()
    );

    public VanillaHotbarHud() {
        super(INFO);
        calculateSize();
    }

    private void calculateSize() {
        setSize(182 * scale.get(), 22 * scale.get());
    }

    public double getScale() {
        return scale.get();
    }

    public int getRotation() {
        return rotation.get();
    }

    @Override
    public int getEditorRotation() {
        return rotation.get();
    }

    public boolean keepItemsUpright() {
        return keepItemsUpright.get();
    }

    public boolean isHidden() {
        return hide.get();
    }

    public double getItemAlpha() {
        if (singleAlpha.get()) return 1.0;
        return itemAlpha.get() / 255.0;
    }

    public double getBackgroundAlpha() {
        if (singleAlpha.get()) return 1.0;
        return backgroundAlpha.get() / 255.0;
    }

    public double getSelectorAlpha() {
        if (singleAlpha.get()) return 1.0;
        return selectorAlpha.get() / 255.0;
    }

    @Override
    public void render(HudRenderer renderer) {
        if (isInEditor()) renderer.quad(x, y, getWidth(), getHeight(), new Color(255, 255, 255, 40));
    }
}
