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
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * Some servers repurpose the vanilla XP bar to show an ability cooldown instead of real XP. On
 * this server it runs backwards from vanilla XP: empty (0) means the ability is ready, full (1)
 * means the cooldown was just applied, draining back down to empty as it recharges. The time
 * estimate is derived from the drain rate over a short recent window (not the whole cycle since
 * reset), so it stays accurate even if the drain rate isn't perfectly constant - there's no way to
 * know the server's actual cooldown duration directly, so this is the closest estimate without
 * hardcoding any specific ability's timing.
 */
public class AbilityCooldownHud extends HudElement {
    public static final HudElementInfo<AbilityCooldownHud> INFO = new HudElementInfo<>(Hud.GROUP, "ability-cooldown", "Tracks an ability cooldown via the XP bar, with a color gradient and a percent or ETA readout.", AbilityCooldownHud::new);

    private static final long RATE_WINDOW_MS = 1500;
    private static final long SAMPLE_INTERVAL_MS = 50;

    public enum DisplayMode {
        Percent,
        Time
    }

    public enum SubSecondUnit {
        Milliseconds,
        Ticks
    }

    public enum TextColorMode {
        White,
        Black,
        MatchBar,
        InvertBar
    }

    public enum OutlineColor {
        None,
        Black,
        White
    }

    public enum ReadyStyle {
        Static,
        Gradient,
        Flashing,
        HueShift
    }

    private record Sample(long timestamp, float progress) {
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgColors = settings.createGroup("Colors");
    private final SettingGroup sgBackground = settings.createGroup("Background");

    // General

    private final Setting<DisplayMode> displayMode = sgGeneral.add(new EnumSetting.Builder<DisplayMode>()
        .name("display-mode")
        .description("Show a readiness percentage, or an estimated time until ready.")
        .defaultValue(DisplayMode.Percent)
        .build()
    );

    private final Setting<SubSecondUnit> subSecondUnit = sgGeneral.add(new EnumSetting.Builder<SubSecondUnit>()
        .name("sub-second-unit")
        .description("Once under a second remains, show that time in milliseconds or game ticks instead of rounding to whole seconds.")
        .defaultValue(SubSecondUnit.Milliseconds)
        .visible(() -> displayMode.get() == DisplayMode.Time)
        .build()
    );

    private final Setting<Boolean> padSubSecond = sgGeneral.add(new BoolSetting.Builder()
        .name("pad-sub-second")
        .description("Pads the sub-second time to a fixed width (e.g. \"042ms\" instead of \"42ms\"), so the text doesn't visibly shift left/right as the digit count changes.")
        .defaultValue(true)
        .visible(() -> displayMode.get() == DisplayMode.Time)
        .build()
    );

    private final Setting<Integer> roundSubSecondTo = sgGeneral.add(new IntSetting.Builder()
        .name("round-sub-second-to")
        .description("Rounds the sub-second time to the nearest this-many milliseconds, so it updates in visible steps instead of flickering every frame. 1 = no rounding.")
        .defaultValue(10)
        .min(1)
        .sliderRange(1, 100)
        .visible(() -> displayMode.get() == DisplayMode.Time)
        .build()
    );

    private final Setting<Double> scale = sgGeneral.add(new DoubleSetting.Builder()
        .name("scale")
        .description("Overall size multiplier for the whole element - scales width and height together instead of adjusting them separately.")
        .defaultValue(1.0)
        .min(0.1)
        .sliderRange(0.1, 5)
        .onChanged(v -> calculateSize())
        .build()
    );

    private final Setting<Double> barWidth = sgGeneral.add(new DoubleSetting.Builder()
        .name("width")
        .description("Base width of the bar, before scale is applied.")
        .defaultValue(100)
        .min(10)
        .sliderRange(10, 300)
        .onChanged(v -> calculateSize())
        .build()
    );

    private final Setting<Double> barHeight = sgGeneral.add(new DoubleSetting.Builder()
        .name("height")
        .description("Base height of the bar, before scale is applied.")
        .defaultValue(12)
        .min(4)
        .sliderRange(4, 40)
        .onChanged(v -> calculateSize())
        .build()
    );

    private final Setting<Boolean> showBar = sgGeneral.add(new BoolSetting.Builder()
        .name("show-bar")
        .description("Displays the bar itself. Turn off to only show the text.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> fullBarWhenReady = sgGeneral.add(new BoolSetting.Builder()
        .name("full-bar-when-ready")
        .description("Shows the bar fully filled (using the ready style/color below) once ready, instead of empty/invisible - much clearer at a glance than an empty bar meaning \"ready\".")
        .defaultValue(true)
        .visible(showBar::get)
        .build()
    );

    private final Setting<Boolean> invertProgress = sgGeneral.add(new BoolSetting.Builder()
        .name("invert-progress")
        .description("By default the bar is full right when the cooldown is applied and drains down to nothing as it becomes ready. Turning this on reverses that: it starts at nothing right when applied and fills up to full as it becomes ready. Affects color too, not just the fill amount, so the two always stay consistent with each other.")
        .defaultValue(false)
        .visible(showBar::get)
        .build()
    );

    private final Setting<Boolean> invertFillDirection = sgGeneral.add(new BoolSetting.Builder()
        .name("invert-fill-direction")
        .description("Fills the bar from the right edge growing left, instead of the left edge growing right.")
        .defaultValue(false)
        .visible(showBar::get)
        .build()
    );

    private final Setting<Boolean> hideWhenReady = sgGeneral.add(new BoolSetting.Builder()
        .name("hide-when-ready")
        .description("Hides this element entirely once the ability has been ready for a while, instead of leaving a full/ready-colored bar on screen indefinitely. Still shows while positioning it in the HUD editor.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> hideAfterTicks = sgGeneral.add(new IntSetting.Builder()
        .name("hide-after-ticks")
        .description("How many game ticks the ability must have been ready for before this element hides.")
        .defaultValue(40)
        .min(1)
        .sliderRange(1, 200)
        .visible(hideWhenReady::get)
        .build()
    );

    private final Setting<TextColorMode> textColorMode = sgGeneral.add(new EnumSetting.Builder<TextColorMode>()
        .name("text-color-mode")
        .description("White/Black: fixed color. Match Bar: same color the bar currently is - only really readable with Show Bar off, since otherwise the text matches its own background. Invert Bar: the visual opposite of the bar's current color, which stays readable either way.")
        .defaultValue(TextColorMode.White)
        .build()
    );

    private final Setting<OutlineColor> textOutline = sgGeneral.add(new EnumSetting.Builder<OutlineColor>()
        .name("text-outline")
        .description("Draws a solid outline around the text in the chosen color, replacing the normal drop shadow. None disables it.")
        .defaultValue(OutlineColor.None)
        .build()
    );

    // Colors

    private final Setting<SettingColor> readyColor = sgColors.add(new ColorSetting.Builder()
        .name("ready-color")
        .description("Color when the ability is ready (bar empty). Also the draining gradient's endpoint, regardless of ready style below.")
        .defaultValue(new SettingColor(40, 255, 40))
        .build()
    );

    private final Setting<ReadyStyle> readyStyle = sgColors.add(new EnumSetting.Builder<ReadyStyle>()
        .name("ready-style")
        .description("How the ready state itself looks once the bar reaches empty. Static just uses Ready Color. Gradient smoothly cycles through Ready Colors. Flashing hard-switches between Ready Colors every Flash Ticks.")
        .defaultValue(ReadyStyle.Static)
        .build()
    );

    private final Setting<List<SettingColor>> readyColors = sgColors.add(new ColorListSetting.Builder()
        .name("ready-colors")
        .description("Colors to cycle between when Ready Style is Gradient. Needs at least 2.")
        .defaultValue(List.of(new SettingColor(40, 255, 40), new SettingColor(40, 220, 255)))
        .visible(() -> readyStyle.get() == ReadyStyle.Gradient)
        .build()
    );

    private final Setting<SettingColor> hueShiftBaseColor = sgColors.add(new ColorSetting.Builder()
        .name("hue-shift-base-color")
        .description("Starting color for Hue Shift style - its hue continuously rotates, keeping its saturation/brightness/alpha.")
        .defaultValue(new SettingColor(40, 255, 40))
        .visible(() -> readyStyle.get() == ReadyStyle.HueShift)
        .build()
    );

    private final Setting<Double> hueShiftSpeed = sgColors.add(new DoubleSetting.Builder()
        .name("hue-shift-speed")
        .description("How fast the hue rotates, in Hue Shift style.")
        .defaultValue(1)
        .min(0.1)
        .sliderRange(0.1, 10)
        .visible(() -> readyStyle.get() == ReadyStyle.HueShift)
        .build()
    );

    private final Setting<Double> hueShiftRange = sgColors.add(new DoubleSetting.Builder()
        .name("hue-shift-range")
        .description("How far the hue swings from the base color, in degrees each direction, instead of cycling continuously - keeps it reading as a variation of the base color rather than looking like Gradient/Rainbow. 180 = swings the full way around.")
        .defaultValue(30)
        .range(0, 180)
        .sliderRange(0, 180)
        .visible(() -> readyStyle.get() == ReadyStyle.HueShift)
        .build()
    );

    private final Setting<Double> readyGradientSpeed = sgColors.add(new DoubleSetting.Builder()
        .name("ready-gradient-speed")
        .description("How fast Ready Colors cycles, in Gradient style.")
        .defaultValue(1)
        .min(0.1)
        .sliderRange(0.1, 10)
        .visible(() -> readyStyle.get() == ReadyStyle.Gradient)
        .build()
    );

    private final Setting<List<TimedColorEntry>> flashColors = sgColors.add(new TimedColorListSetting.Builder()
        .name("flash-colors")
        .description("Colors to hard-switch between when Ready Style is Flashing, each with its own hold duration in game ticks. Needs at least 2.")
        .defaultValue(List.of(new TimedColorEntry(new SettingColor(40, 255, 40), 10), new TimedColorEntry(new SettingColor(40, 220, 255), 10)))
        .visible(() -> readyStyle.get() == ReadyStyle.Flashing)
        .build()
    );

    private final Setting<SettingColor> midColor = sgColors.add(new ColorSetting.Builder()
        .name("mid-color")
        .description("Color at half cooldown.")
        .defaultValue(new SettingColor(255, 165, 0))
        .build()
    );

    private final Setting<SettingColor> justUsedColor = sgColors.add(new ColorSetting.Builder()
        .name("just-used-color")
        .description("Color right after the cooldown is applied (bar full).")
        .defaultValue(new SettingColor(255, 40, 40))
        .build()
    );

    // Background

    private final Setting<Boolean> background = sgBackground.add(new BoolSetting.Builder()
        .name("background")
        .description("Displays a background behind the bar.")
        .defaultValue(true)
        .visible(showBar::get)
        .build()
    );

    private final Setting<SettingColor> backgroundColor = sgBackground.add(new ColorSetting.Builder()
        .name("background-color")
        .description("Color used for the background.")
        .defaultValue(new SettingColor(25, 25, 25, 150))
        .visible(() -> showBar.get() && background.get())
        .build()
    );

    // Recent-window rate tracking, for the time-estimate display mode
    private final Deque<Sample> recentSamples = new ArrayDeque<>();
    private long lastSampleTime = -1;
    private float lastProgress = -1;
    private long readySinceMs = -1;

    public AbilityCooldownHud() {
        super(INFO);

        calculateSize();
    }

    private void calculateSize() {
        setSize(barWidth.get() * scale.get(), barHeight.get() * scale.get());
    }

    @Override
    public void render(HudRenderer renderer) {
        // Raw, uninverted progress - drives sample tracking and the ETA estimate below, which both
        // need to reflect the real rate of change regardless of how Invert Progress displays it.
        float progress = mc.player != null ? MathHelper.clamp(mc.player.experienceProgress, 0, 1) : 0;
        long now = System.currentTimeMillis();

        // A visible jump upward means the cooldown was just (re)applied - drop the old window so
        // the estimate doesn't briefly mix pre- and post-reset samples.
        if (lastProgress >= 0 && progress > lastProgress + 0.001f) recentSamples.clear();
        lastProgress = progress;

        if (lastSampleTime == -1 || now - lastSampleTime >= SAMPLE_INTERVAL_MS) {
            recentSamples.addLast(new Sample(now, progress));
            lastSampleTime = now;
        }
        while (!recentSamples.isEmpty() && now - recentSamples.peekFirst().timestamp() > RATE_WINDOW_MS) recentSamples.pollFirst();

        boolean ready = progress <= 0.001f;

        if (ready) {
            if (readySinceMs == -1) readySinceMs = now;
        } else {
            readySinceMs = -1;
        }

        if (hideWhenReady.get() && !isInEditor() && readySinceMs != -1 && now - readySinceMs >= hideAfterTicks.get() * 50L) {
            return;
        }

        // Display progress - what color/fill amount actually show, separate from the raw value
        // tracked above.
        float displayProgress = invertProgress.get() ? 1f - progress : progress;

        Color color = ready ? getReadyStyleColor() : gradientColor(displayProgress);

        if (showBar.get()) {
            if (background.get()) renderer.quad(x, y, getWidth(), getHeight(), backgroundColor.get());

            double fillWidth = (ready && fullBarWhenReady.get()) ? getWidth() : getWidth() * displayProgress;
            double fillX = invertFillDirection.get() ? x + getWidth() - fillWidth : x;
            if (fillWidth > 0) renderer.quad(fillX, y, fillWidth, getHeight(), color);
        }

        String text = displayMode.get() == DisplayMode.Percent ? Math.round((1 - progress) * 100) + "%" : timeEstimate(progress);
        double textWidth = renderer.textWidth(text);
        double textHeight = renderer.textHeight();

        Color textColor = switch (textColorMode.get()) {
            case White -> Color.WHITE;
            case Black -> Color.BLACK;
            case MatchBar -> color;
            case InvertBar -> new Color(255 - color.r, 255 - color.g, 255 - color.b, color.a);
        };

        drawText(renderer, text, x + getWidth() / 2.0 - textWidth / 2.0, y + getHeight() / 2.0 - textHeight / 2.0, textColor);
    }

    private void drawText(HudRenderer renderer, String text, double x, double y, Color color) {
        OutlineColor outline = textOutline.get();

        if (outline == OutlineColor.None) {
            renderer.text(text, x, y, color, true);
            return;
        }

        Color outlineColor = outline == OutlineColor.Black ? Color.BLACK : Color.WHITE;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx == 0 && dy == 0) continue;
                renderer.text(text, x + dx, y + dy, outlineColor, false);
            }
        }

        renderer.text(text, x, y, color, false);
    }

    private Color gradientColor(float progress) {
        // progress: 0 = ready, 1 = just used
        if (progress <= 0.5f) return lerp(readyColor.get(), midColor.get(), progress / 0.5f);

        return lerp(midColor.get(), justUsedColor.get(), (progress - 0.5f) / 0.5f);
    }

    private Color getReadyStyleColor() {
        return switch (readyStyle.get()) {
            case Static -> readyColor.get();
            case Gradient -> cycleGradient(readyColors.get(), readyGradientSpeed.get());
            case Flashing -> flashColor(flashColors.get());
            case HueShift -> hueShiftColor();
        };
    }

    private Color hueShiftColor() {
        SettingColor base = hueShiftBaseColor.get();
        float[] hsb = java.awt.Color.RGBtoHSB(base.r, base.g, base.b, null);

        double time = System.currentTimeMillis() / 1000.0 * hueShiftSpeed.get();
        double offset = Math.sin(time) * hueShiftRange.get();
        float hue = (float) (((hsb[0] * 360.0 + offset) % 360.0 + 360.0) % 360.0);

        Color color = Color.fromHsv(hue, hsb[1], hsb[2]);
        color.a = base.a;
        return color;
    }

    private Color cycleGradient(List<SettingColor> colors, double speed) {
        if (colors.isEmpty()) return readyColor.get();
        if (colors.size() == 1) return colors.get(0);

        double time = (System.currentTimeMillis() / 1000.0 * speed) % colors.size();
        int index = (int) time;
        float t = (float) (time - index);

        return lerp(colors.get(index), colors.get((index + 1) % colors.size()), t);
    }

    private Color flashColor(List<TimedColorEntry> entries) {
        if (entries.isEmpty()) return readyColor.get();
        if (entries.size() == 1) return entries.get(0).color;

        long totalTicks = 0;
        for (TimedColorEntry entry : entries) totalTicks += Math.max(1, entry.ticks);

        long tick = System.currentTimeMillis() / 50; // ~1 game tick, assuming a stable 20 TPS
        long pos = tick % totalTicks;

        long accumulated = 0;
        for (TimedColorEntry entry : entries) {
            accumulated += Math.max(1, entry.ticks);
            if (pos < accumulated) return entry.color;
        }

        return entries.get(entries.size() - 1).color;
    }

    private Color lerp(Color from, Color to, float t) {
        return new Color(
            (int) (from.r + (to.r - from.r) * t),
            (int) (from.g + (to.g - from.g) * t),
            (int) (from.b + (to.b - from.b) * t),
            (int) (from.a + (to.a - from.a) * t)
        );
    }

    private String timeEstimate(float progress) {
        if (progress <= 0.001f) return "Ready";
        if (recentSamples.size() < 3) return "...";

        // Least-squares linear fit over every sample in the window, rather than just the oldest
        // and current one - a two-point estimate is very sensitive to exactly which sample happens
        // to be "oldest" as the window slides, and experienceProgress updates in discrete
        // server-synced steps (not smoothly), so that noise otherwise shows up directly in the ETA.
        long baseTime = recentSamples.peekFirst().timestamp();
        int n = recentSamples.size();
        double sumT = 0, sumP = 0, sumTP = 0, sumTT = 0;

        for (Sample s : recentSamples) {
            double t = s.timestamp() - baseTime;
            double p = s.progress();

            sumT += t;
            sumP += p;
            sumTP += t * p;
            sumTT += t * t;
        }

        double denom = n * sumTT - sumT * sumT;
        if (Math.abs(denom) < 1e-9) return "...";

        double slope = (n * sumTP - sumT * sumP) / denom; // progress change per ms
        if (slope >= -1e-9) return "..."; // not draining right now - can't estimate

        double rate = -slope;
        long remainingMs = (long) (progress / rate);

        return formatDuration(remainingMs);
    }

    private String formatDuration(long ms) {
        if (ms < 1000) {
            long roundTo = Math.max(1, roundSubSecondTo.get());
            long rounded = Math.round(ms / (double) roundTo) * roundTo;

            if (rounded < 1000) {
                boolean pad = padSubSecond.get();

                if (subSecondUnit.get() == SubSecondUnit.Ticks) {
                    long ticks = rounded / 50;
                    return pad ? String.format("%02dt", ticks) : ticks + "t";
                }

                return pad ? String.format("%03dms", rounded) : rounded + "ms";
            }

            ms = rounded; // rounded up into the next whole second
        }

        long totalSeconds = ms / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;

        return minutes > 0 ? minutes + "m " + seconds + "s" : seconds + "s";
    }
}
