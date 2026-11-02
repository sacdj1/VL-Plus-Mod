/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.hud.elements;

import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.misc.CheeseControl;
import meteordevelopment.meteorclient.systems.modules.render.BonesRings;
import meteordevelopment.meteorclient.systems.modules.render.Visualizer;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;

/**
 * Shows a warning while you're outside Bones's Teleport Ring (see the admin-only Bones Rings module) during a
 * fight - hitting from out there gets you teleported to him. Uses the Visualizer's own boss
 * detection, Only In Fight check and Teleport Radius, so it needs that module on (admin-only).
 */
public class BonesWarningHud extends HudElement {
    public static final HudElementInfo<BonesWarningHud> INFO = new HudElementInfo<>(Hud.GROUP, "bones-warning", "Shows a warning while you're outside Bones's teleport ring (needs the admin-only Bones Rings module).", BonesWarningHud::new);

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<String> text = sgGeneral.add(new StringSetting.Builder()
        .name("text")
        .description("The warning shown while you're outside the teleport ring.")
        .defaultValue("DONT HIT")
        .build()
    );

    private final Setting<Boolean> showDistance = sgGeneral.add(new BoolSetting.Builder()
        .name("show-distance")
        .description("Appends your distance from Bones, in blocks, to the warning.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> scale = sgGeneral.add(new DoubleSetting.Builder()
        .name("scale")
        .description("Text scale.")
        .defaultValue(3.0)
        .min(0.5)
        .sliderRange(0.5, 8)
        .build()
    );

    private final Setting<Boolean> shadow = sgGeneral.add(new BoolSetting.Builder()
        .name("shadow")
        .description("Renders the text with a shadow.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> color = sgGeneral.add(new ColorSetting.Builder()
        .name("color")
        .description("Color of the warning text.")
        .defaultValue(new SettingColor(255, 40, 40))
        .build()
    );

    public BonesWarningHud() {
        super(INFO);
        setSize(120, 30);
    }

    @Override
    public void render(HudRenderer renderer) {
        Visualizer owner = Modules.get().get(Visualizer.class);
        BonesRings visualizer = owner != null ? owner.bones() : null;
        Double distance = visualizer != null && CheeseControl.warningHud() ? visualizer.getBossDistance() : null;

        String shown = text.get();
        if (showDistance.get() && distance != null) shown += " (" + Math.round(distance) + ")";

        // Size stays real every frame regardless of visibility so an anchored position never jumps
        // between shown and hidden (same reason as Target Indicator).
        setSize(renderer.textWidth(shown, shadow.get(), scale.get()), renderer.textHeight(shadow.get(), scale.get()));

        if (!isInEditor()) {
            if (distance == null || distance <= visualizer.getTeleportRadius()) return;
        }

        renderer.text(shown, x, y, color.get(), shadow.get(), scale.get());
    }
}
