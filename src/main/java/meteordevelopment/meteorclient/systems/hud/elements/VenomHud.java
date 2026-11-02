/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.hud.elements;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ArmorStandEntity;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * Your current Venom % - read from the floating "☣N%" status text (an invisible, momentary armor
 * stand near you, same family as the "-X❤ Bleed"/"➷N% Puncture" ones) rather than tracked/computed
 * independently, since the server already does the real accumulation math and prints the result -
 * this just reads it off, same idea as Damage Numbers HUD's own Nametag health source. That text
 * only exists for a moment each time your Venom % actually changes, not continuously, so the last
 * value read is kept on screen (optionally fading after Stale After) rather than needing a match
 * every single tick.
 */
public class VenomHud extends HudElement {
    public static final HudElementInfo<VenomHud> INFO = new HudElementInfo<>(Hud.GROUP, "venom", "Your current Venom % from the nearby floating status text.", VenomHud::new);

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<String> pattern = sgGeneral.add(new StringSetting.Builder()
        .name("pattern")
        .description("Regex with one capture group for the percent, matched against nearby invisible entities' displayed names.")
        .defaultValue("☣(\\d+)%")
        .build()
    );

    private final Setting<Boolean> requireRiding = sgGeneral.add(new BoolSetting.Builder()
        .name("require-riding")
        .description("Only counts a status text armor stand that's actually riding you as a passenger - these momentary indicators are riding whichever entity they're reporting on, so a nearby venomous/poisoned MOB gets its own copy that a plain distance check can't tell apart from your own. Turn off to fall back to distance-only if this turns out not to match how this server actually attaches them.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("How close a matching status text needs to be to you to count. Still applied even with Require Riding on, as a cheap extra sanity check.")
        .defaultValue(3.0)
        .min(0.5)
        .sliderRange(0.5, 10)
        .build()
    );

    private final Setting<Integer> staleAfter = sgGeneral.add(new IntSetting.Builder()
        .name("stale-after")
        .description("Hides the display after this many milliseconds without a fresh reading - the status text only appears for a moment each time it changes. 0 = never hide, always show the last known value.")
        .defaultValue(15000)
        .min(0)
        .sliderRange(0, 60000)
        .build()
    );

    private final Setting<Boolean> hideAtZero = sgGeneral.add(new BoolSetting.Builder()
        .name("hide-at-zero")
        .description("Hides the display entirely while the last known value is 0%.")
        .defaultValue(true)
        .build()
    );

    private final Setting<String> label = sgGeneral.add(new StringSetting.Builder()
        .name("label")
        .description("Text shown before the percent. Leave empty for just the number.")
        .defaultValue("Venom: ")
        .build()
    );

    private final Setting<SettingColor> color = sgGeneral.add(new ColorSetting.Builder()
        .name("color")
        .description("Text color.")
        .defaultValue(new SettingColor(140, 220, 60))
        .build()
    );

    private final Setting<Double> scale = sgGeneral.add(new DoubleSetting.Builder()
        .name("scale")
        .description("Text scale.")
        .defaultValue(2.0)
        .min(0.1)
        .sliderRange(0.3, 6)
        .build()
    );

    private final Setting<Boolean> shadow = sgGeneral.add(new BoolSetting.Builder()
        .name("shadow")
        .description("Draws a drop shadow behind the text.")
        .defaultValue(true)
        .build()
    );

    private int lastPercent = -1;
    private long lastSeenMs = -1;

    private Pattern compiled;
    private String compiledFrom;

    public VenomHud() {
        super(INFO);
        setSize(60 * scale.get(), 18 * scale.get());

        MeteorClient.EVENT_BUS.subscribe(this);
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!isActive() || mc.world == null || mc.player == null) return;

        String patternStr = pattern.get();
        if (compiled == null || !patternStr.equals(compiledFrom)) {
            compiledFrom = patternStr;
            try {
                compiled = Pattern.compile(patternStr);
            } catch (Exception e) {
                compiled = null;
                return;
            }
        }

        double rangeSq = range.get() * range.get();

        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof ArmorStandEntity) || !entity.isInvisible()) continue;
            if (requireRiding.get() && entity.getVehicle() != mc.player) continue;
            if (mc.player.squaredDistanceTo(entity) > rangeSq) continue;

            Matcher m = compiled.matcher(entity.getName().getString());
            if (!m.find()) continue;

            try {
                lastPercent = Integer.parseInt(m.group(1));
                lastSeenMs = System.currentTimeMillis();
            } catch (NumberFormatException ignored) {}
        }
    }

    @Override
    public void render(HudRenderer renderer) {
        boolean preview = isInEditor();

        int percent = preview ? 27 : lastPercent;
        long seenMs = preview ? System.currentTimeMillis() : lastSeenMs;

        if (!preview) {
            if (seenMs < 0) { setSize(1, 1); return; }
            if (hideAtZero.get() && percent <= 0) { setSize(1, 1); return; }

            int stale = staleAfter.get();
            if (stale > 0 && System.currentTimeMillis() - seenMs > stale) { setSize(1, 1); return; }
        }

        String text = label.get() + percent + "%";

        double tw = renderer.textWidth(text, shadow.get(), scale.get());
        double th = renderer.textHeight(shadow.get(), scale.get());

        renderer.text(text, x, y, color.get(), shadow.get(), scale.get());

        setSize(Math.max(tw, 1), Math.max(th, 1));
    }
}
