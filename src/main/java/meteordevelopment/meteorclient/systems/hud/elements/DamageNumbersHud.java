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
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.math.MathHelper;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * Floating "-X"/"+X" numbers whenever your health changes - split out from Health Bar so it can be
 * positioned, scaled and timed on its own, and read health from a completely different source than
 * whatever the bar itself is showing (e.g. real vanilla health on the bar, but numbers driven by a
 * server's own custom health value pulled out of your nametag, or vice versa).
 */
public class DamageNumbersHud extends HudElement {
    public static final HudElementInfo<DamageNumbersHud> INFO = new HudElementInfo<>(Hud.GROUP, "damage-numbers", "Floating numbers that pop up whenever your health changes.", DamageNumbersHud::new);

    public enum HealthSource {
        Vanilla,
        Nametag
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgTiming = settings.createGroup("Timing");
    private final SettingGroup sgDisplay = settings.createGroup("Display");

    private final Setting<HealthSource> healthSource = sgGeneral.add(new EnumSetting.Builder<HealthSource>()
        .name("health-source")
        .description("Where the health value driving these numbers comes from. Vanilla: the real client-side health value. Nametag: reads it out of your own displayed name instead - for servers with a custom health system that doesn't match real vanilla health/damage. Independent of Health Bar's own Health Source - the two can watch completely different values.")
        .defaultValue(HealthSource.Vanilla)
        .build()
    );

    private final Setting<String> nametagPattern = sgGeneral.add(new StringSetting.Builder()
        .name("nametag-pattern")
        .description("Regex with one capture group for current health, run against your own displayed name. Same idea as Health Bar's Nametag Pattern, but tracked completely separately.")
        .defaultValue("(\\d+(?:\\.\\d+)?)\\s*❤")
        .visible(() -> healthSource.get() == HealthSource.Nametag)
        .build()
    );

    private final Setting<Boolean> damageEnabled = sgGeneral.add(new BoolSetting.Builder()
        .name("damage-indicator")
        .description("Shows how much health was just lost.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> damageThreshold = sgGeneral.add(new DoubleSetting.Builder()
        .name("damage-threshold")
        .description("Ignores a loss smaller than this - regen ticking down, rounding jitter, etc.")
        .defaultValue(1)
        .min(0)
        .sliderRange(0, 20)
        .visible(damageEnabled::get)
        .build()
    );

    private final Setting<SettingColor> damageColor = sgGeneral.add(new ColorSetting.Builder()
        .name("damage-color")
        .description("Color of the damage number.")
        .defaultValue(new SettingColor(255, 70, 70))
        .visible(damageEnabled::get)
        .build()
    );

    private final Setting<Boolean> healEnabled = sgGeneral.add(new BoolSetting.Builder()
        .name("heal-indicator")
        .description("Shows how much health was just gained.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> healThreshold = sgGeneral.add(new DoubleSetting.Builder()
        .name("heal-threshold")
        .description("Ignores a gain smaller than this.")
        .defaultValue(1)
        .min(0)
        .sliderRange(0, 20)
        .visible(healEnabled::get)
        .build()
    );

    private final Setting<SettingColor> healColor = sgGeneral.add(new ColorSetting.Builder()
        .name("heal-color")
        .description("Color of the heal number.")
        .defaultValue(new SettingColor(70, 230, 90))
        .visible(healEnabled::get)
        .build()
    );

    private final Setting<Integer> combineWindow = sgTiming.add(new IntSetting.Builder()
        .name("combine-window")
        .description("Several changes within this many milliseconds of each other (e.g. a multi-hit combo, or several ticks of regen) are added together and shown as one number, instead of a new one flashing up for every single tick health changes.")
        .defaultValue(150)
        .min(0)
        .sliderRange(0, 1000)
        .build()
    );

    private final Setting<Integer> displayDuration = sgTiming.add(new IntSetting.Builder()
        .name("display-duration")
        .description("How long, in milliseconds, a number stays fully visible before it starts fading out. Timing starts once Combine Window above has closed, not at the first hit.")
        .defaultValue(450)
        .min(0)
        .sliderRange(0, 3000)
        .build()
    );

    private final Setting<Integer> fadeDuration = sgTiming.add(new IntSetting.Builder()
        .name("fade-duration")
        .description("How long, in milliseconds, the number takes to fade out after Display Duration above ends.")
        .defaultValue(250)
        .min(0)
        .sliderRange(0, 2000)
        .build()
    );

    private final Setting<Double> scale = sgDisplay.add(new DoubleSetting.Builder()
        .name("scale")
        .description("Text scale.")
        .defaultValue(2.0)
        .min(0.1)
        .sliderRange(0.3, 6)
        .build()
    );

    private final Setting<Boolean> shadow = sgDisplay.add(new BoolSetting.Builder()
        .name("shadow")
        .description("Draws a drop shadow behind the text.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> rise = sgDisplay.add(new BoolSetting.Builder()
        .name("rise")
        .description("The number drifts upward while it's shown, instead of staying put.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> stack = sgDisplay.add(new BoolSetting.Builder()
        .name("stack-vertically")
        .description("Draws damage above heal instead of both on top of each other, so a hit and a heal landing at the same moment are both readable.")
        .defaultValue(true)
        .build()
    );

    // Sampled every tick (matches how often the server actually changes health, unlike render()
    // which just re-reads whatever the current value is every frame) - pending* holds an amount
    // still accumulating (more hits/ticks might still land within Combine Window), shown* holds the
    // finalized total currently being displayed, with its own fade timer.
    private boolean healthInitialized = false;
    private float lastHealthSample = 0;

    private double pendingDamage = 0, pendingHeal = 0;
    private long pendingDamageLastMs = 0, pendingHealLastMs = 0;

    private double shownDamage = 0, shownHeal = 0;
    private long shownDamageAtMs = -1, shownHealAtMs = -1;

    public DamageNumbersHud() {
        super(INFO);
        setSize(60 * scale.get(), 18 * scale.get());

        MeteorClient.EVENT_BUS.subscribe(this);
    }

    private float resolveHealth() {
        if (mc.player == null) return 0;
        if (healthSource.get() == HealthSource.Vanilla) return mc.player.getHealth();

        try {
            Matcher matcher = Pattern.compile(nametagPattern.get()).matcher(mc.player.getDisplayName().getString());
            if (matcher.find()) return Float.parseFloat(matcher.group(1));
        } catch (Exception ignored) {}

        return mc.player.getHealth();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!isActive() || mc.player == null) { healthInitialized = false; return; }
        if (!damageEnabled.get() && !healEnabled.get()) return;

        float current = resolveHealth();
        long now = System.currentTimeMillis();

        if (!healthInitialized) {
            // First sample after activating (or after health was unavailable) - nothing to compare
            // against yet, so this tick never shows as a false damage/heal spike.
            healthInitialized = true;
            lastHealthSample = current;
            return;
        }

        float delta = current - lastHealthSample;
        lastHealthSample = current;

        if (delta < 0 && damageEnabled.get()) {
            pendingDamage += -delta;
            pendingDamageLastMs = now;
        } else if (delta > 0 && healEnabled.get()) {
            pendingHeal += delta;
            pendingHealLastMs = now;
        }

        int window = combineWindow.get();

        if (pendingDamage > 0 && now - pendingDamageLastMs >= window) {
            if (pendingDamage >= damageThreshold.get()) { shownDamage = pendingDamage; shownDamageAtMs = now; }
            pendingDamage = 0;
        }

        if (pendingHeal > 0 && now - pendingHealLastMs >= window) {
            if (pendingHeal >= healThreshold.get()) { shownHeal = pendingHeal; shownHealAtMs = now; }
            pendingHeal = 0;
        }
    }

    /** 1 while fully shown, fading linearly to 0 over Fade Duration, or -1 once it's done and should be skipped entirely. */
    private double indicatorAlpha(long shownAtMs) {
        if (shownAtMs < 0) return -1;

        long age = System.currentTimeMillis() - shownAtMs;
        int duration = displayDuration.get(), fade = fadeDuration.get();

        if (age < duration) return 1;
        if (fade <= 0 || age >= duration + fade) return -1;

        return 1 - (age - duration) / (double) fade;
    }

    @Override
    public void render(HudRenderer renderer) {
        double w = getWidth(), h = getHeight();
        double centerX = x + w / 2.0;
        double topY = y + h / 2.0;

        double damageAlpha = isInEditor() ? 1 : indicatorAlpha(shownDamageAtMs);
        double healAlpha = isInEditor() ? 1 : indicatorAlpha(shownHealAtMs);

        String damageText = isInEditor() ? "-7" : "-" + Math.round(shownDamage);
        String healText = isInEditor() ? "+7" : "+" + Math.round(shownHeal);

        double th = renderer.textHeight(shadow.get(), scale.get());
        double damageY = stack.get() ? topY : topY;
        double healY = stack.get() ? topY + th + 2 * scale.get() : topY;

        if (damageEnabled.get()) drawOne(renderer, centerX, damageY, damageAlpha, damageText, damageColor.get());
        if (healEnabled.get()) drawOne(renderer, centerX, healY, healAlpha, healText, healColor.get());

        double maxW = Math.max(renderer.textWidth(damageText, shadow.get(), scale.get()), renderer.textWidth(healText, shadow.get(), scale.get()));
        setSize(Math.max(maxW, 1), stack.get() ? th * 2 + 2 * scale.get() : th);
    }

    private void drawOne(HudRenderer renderer, double centerX, double baseY, double alpha, String text, SettingColor base) {
        if (alpha <= 0) return;

        double riseAmount = 0;
        if (rise.get()) {
            double t = 1 - alpha; // 0 just as it appears, 1 by the time it's fully faded
            riseAmount = t * 10 * scale.get();
        }

        Color color = new Color(base.r, base.g, base.b, (int) MathHelper.clamp(base.a * alpha, 0, 255));
        double tw = renderer.textWidth(text, shadow.get(), scale.get());
        renderer.text(text, centerX - tw / 2.0, baseY - riseAmount, color, shadow.get(), scale.get());
    }
}
