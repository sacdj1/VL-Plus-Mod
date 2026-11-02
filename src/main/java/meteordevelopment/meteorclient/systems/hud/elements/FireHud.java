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
import meteordevelopment.meteorclient.utils.render.color.SettingColor;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * Shows while you're on fire, with a countdown of how long until it goes out - reads
 * Entity.getFireTicks() directly (client-predicted for the local player, same as vanilla's own
 * fire overlay/damage ticking - not server-synced down to the exact tick, close enough for a
 * countdown display).
 */
public class FireHud extends HudElement {
    public static final HudElementInfo<FireHud> INFO = new HudElementInfo<>(Hud.GROUP, "fire", "Shows while you're on fire, with a countdown until it goes out.", FireHud::new);

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<String> text = sgGeneral.add(new StringSetting.Builder()
        .name("text")
        .description("The text shown while on fire. {time} is replaced with the countdown.")
        .defaultValue("ON FIRE ({time})")
        .build()
    );

    private final Setting<Boolean> decimal = sgGeneral.add(new BoolSetting.Builder()
        .name("decimal")
        .description("Shows the countdown to one decimal place (4.5s) instead of rounded whole seconds (5s).")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> scale = sgGeneral.add(new DoubleSetting.Builder()
        .name("scale")
        .description("Text scale.")
        .defaultValue(2.0)
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
        .description("Color of the text.")
        .defaultValue(new SettingColor(255, 120, 40))
        .build()
    );

    public FireHud() {
        super(INFO);
        setSize(90, 20);
    }

    @Override
    public void render(HudRenderer renderer) {
        int fireTicks = isInEditor() ? 100 : (mc.player != null ? mc.player.getFireTicks() : 0);
        boolean onFire = isInEditor() || fireTicks > 0;

        String timeText = decimal.get()
            ? String.format("%.1fs", fireTicks / 20.0)
            : Math.ceil(fireTicks / 20.0) + "s";

        String shown = text.get().replace("{time}", timeText);

        // Size stays real every frame regardless of visibility so an anchored position never jumps
        // between shown and hidden (same reason as Bones Warning/Target Indicator).
        setSize(renderer.textWidth(shown, shadow.get(), scale.get()), renderer.textHeight(shadow.get(), scale.get()));

        if (!onFire) return;

        renderer.text(shown, x, y, color.get(), shadow.get(), scale.get());
    }
}
