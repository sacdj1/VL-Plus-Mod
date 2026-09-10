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
 * Doesn't draw anything itself - see VanillaHotbarHud. InGameHudMixin redirects vanilla's own
 * "held item name" popup (the text that briefly shows the item's name above the hotbar when you
 * switch to it) to this element's position/scale. Center-anchored like Title/Subtitle/Action Bar,
 * since the text width varies per item name.
 */
public class VanillaItemNameHud extends HudElement {
    public static final HudElementInfo<VanillaItemNameHud> INFO = new HudElementInfo<>(Hud.VANILLA_GROUP, "vanilla-item-name", "Moves and scales the real held item name popup (centered on this box, since its width varies per item).", VanillaItemNameHud::new);

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> hide = sgGeneral.add(new BoolSetting.Builder()
        .name("hide")
        .description("Hides the real vanilla element entirely instead of repositioning it.")
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
        .description("Rotates the whole element around its own center, in degrees clockwise.")
        .defaultValue(0)
        .range(0, 359)
        .sliderRange(0, 359)
        .build()
    );

    public VanillaItemNameHud() {
        super(INFO);
        calculateSize();
    }

    private void calculateSize() {
        setSize(150 * scale.get(), 20 * scale.get());
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

    public boolean isHidden() {
        return hide.get();
    }

    @Override
    public void render(HudRenderer renderer) {
        if (!isInEditor()) return;

        renderer.quad(x, y, getWidth(), getHeight(), new Color(255, 255, 255, 40));

        double textScale = scale.get();
        String example = "Example Item Name";
        double centerX = x + getWidth() / 2.0;
        double centerY = y + getHeight() / 2.0;

        renderer.text(example, centerX - renderer.textWidth(example, true, textScale) / 2.0, centerY - renderer.textHeight(true, textScale) / 2.0, Color.WHITE, true, textScale);
    }
}
