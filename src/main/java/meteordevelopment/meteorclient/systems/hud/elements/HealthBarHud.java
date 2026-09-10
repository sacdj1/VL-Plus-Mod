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
import meteordevelopment.meteorclient.systems.modules.render.HealthBarAdjust;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.util.math.MathHelper;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * A plain bar (not vanilla's heart row) showing health as a proportional fill, independently
 * sized/scaled from vanilla's fixed layout. Nametag source reads the player's own displayed name
 * (team prefix/suffix included, same text vanilla's own floating nametag would show for this
 * player to OTHER players) and regex-extracts a current/max health pair from it, for servers that
 * embed health there instead of - or in addition to - real vanilla health.
 */
public class HealthBarHud extends HudElement {
    public static final HudElementInfo<HealthBarHud> INFO = new HudElementInfo<>(Hud.GROUP, "health-bar", "A plain scalable health bar, independent of vanilla's heart row.", HealthBarHud::new);

    public enum DisplayMode {
        Bar,
        Numbers,
        Both
    }

    public enum HealthSource {
        Vanilla,
        Nametag
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgColors = settings.createGroup("Colors");

    private final Setting<Double> width = sgGeneral.add(new DoubleSetting.Builder()
        .name("width")
        .description("Base width, before Scale X/Scale below are applied.")
        .defaultValue(100)
        .min(1)
        .sliderRange(1, 400)
        .onChanged(v -> calculateSize())
        .build()
    );

    private final Setting<Double> height = sgGeneral.add(new DoubleSetting.Builder()
        .name("height")
        .description("Base height, before Scale Y/Scale below are applied.")
        .defaultValue(10)
        .min(1)
        .sliderRange(1, 100)
        .onChanged(v -> calculateSize())
        .build()
    );

    private final Setting<Double> scaleX = sgGeneral.add(new DoubleSetting.Builder()
        .name("scale-x")
        .description("Horizontal scale, independent of Scale Y - stretches/squashes the bar without affecting its height.")
        .defaultValue(1)
        .min(0.1)
        .sliderRange(0.1, 10)
        .onChanged(v -> calculateSize())
        .build()
    );

    private final Setting<Double> scaleY = sgGeneral.add(new DoubleSetting.Builder()
        .name("scale-y")
        .description("Vertical scale, independent of Scale X - stretches/squashes the bar without affecting its width.")
        .defaultValue(1)
        .min(0.1)
        .sliderRange(0.1, 10)
        .onChanged(v -> calculateSize())
        .build()
    );

    private final Setting<Double> scale = sgGeneral.add(new DoubleSetting.Builder()
        .name("scale")
        .description("Overall scale, multiplied on top of Scale X/Scale Y - scales both dimensions together.")
        .defaultValue(1)
        .min(0.1)
        .sliderRange(0.1, 10)
        .onChanged(v -> calculateSize())
        .build()
    );

    private final Setting<Integer> rotation = sgGeneral.add(new IntSetting.Builder()
        .name("rotation")
        .description("Rotates the whole bar around its own center, in degrees clockwise. The current/max text (Numbers/Both) stays upright regardless, same as Item Info's badges.")
        .defaultValue(0)
        .range(0, 359)
        .sliderRange(0, 359)
        .build()
    );

    private final Setting<DisplayMode> displayMode = sgGeneral.add(new EnumSetting.Builder<DisplayMode>()
        .name("display-mode")
        .description("Bar: fill only. Numbers: text only (current/max). Both: bar with the numbers drawn over it.")
        .defaultValue(DisplayMode.Bar)
        .build()
    );

    private final Setting<Boolean> smoothFill = sgGeneral.add(new BoolSetting.Builder()
        .name("smooth-fill")
        .description("Interpolates the fill smoothly toward its real value over time, instead of snapping instantly.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> smoothSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("smooth-speed")
        .description("How fast the displayed fill catches up to the real value. Higher catches up faster/snappier, lower lags more/smoother.")
        .defaultValue(15)
        .min(0.1)
        .sliderRange(1, 60)
        .visible(smoothFill::get)
        .build()
    );

    private final Setting<HealthSource> healthSource = sgGeneral.add(new EnumSetting.Builder<HealthSource>()
        .name("health-source")
        .description("Vanilla: the real client-side health value. Nametag: reads a current/max pair out of your own displayed name (team prefix/suffix included) instead - for servers that show health there. Falls back to Vanilla if the pattern below finds no match.")
        .defaultValue(HealthSource.Vanilla)
        .build()
    );

    private final Setting<String> nametagPattern = sgGeneral.add(new StringSetting.Builder()
        .name("nametag-pattern")
        .description("Regex with two capture groups (current, max) used to find health in your own nametag text, when Health Source above is Nametag. Default matches \"123/456\" anywhere in the name.")
        .defaultValue("(\\d+)\\s*/\\s*(\\d+)")
        .visible(() -> healthSource.get() == HealthSource.Nametag)
        .build()
    );

    private final Setting<SettingColor> backgroundColor = sgColors.add(new ColorSetting.Builder()
        .name("background-color")
        .description("Color of the bar's empty/background portion.")
        .defaultValue(new SettingColor(25, 25, 25, 150))
        .visible(() -> displayMode.get() != DisplayMode.Numbers)
        .build()
    );

    private final Setting<SettingColor> fillColor = sgColors.add(new ColorSetting.Builder()
        .name("fill-color")
        .description("Color of the bar's filled portion.")
        .defaultValue(new SettingColor(220, 30, 30))
        .visible(() -> displayMode.get() != DisplayMode.Numbers)
        .build()
    );

    private final Setting<SettingColor> textColor = sgColors.add(new ColorSetting.Builder()
        .name("text-color")
        .description("Color of the current/max text.")
        .defaultValue(new SettingColor(255, 255, 255))
        .visible(() -> displayMode.get() != DisplayMode.Bar)
        .build()
    );

    private float smoothedProgress = -1;
    private long lastSmoothNanos = -1;

    public HealthBarHud() {
        super(INFO);
        calculateSize();
    }

    private void calculateSize() {
        setSize(width.get() * scaleX.get() * scale.get(), height.get() * scaleY.get() * scale.get());
    }

    @Override
    public int getEditorRotation() {
        return rotation.get();
    }

    // Rotates a rectangle (given in local coords relative to the box's own top-left) around the
    // box's own center by this element's Rotation setting, then draws it as two triangles - same
    // technique as HudEditorScreen's rotated selection box. Used for both the background and fill
    // rectangles so they rotate together as one rigid bar.
    private void drawRotatedQuad(HudRenderer renderer, double localX, double localY, double localW, double localH, meteordevelopment.meteorclient.utils.render.color.Color color) {
        int deg = rotation.get();

        if (deg == 0) {
            renderer.quad(x + localX, y + localY, localW, localH, color);
            return;
        }

        double centerX = x + getWidth() / 2.0;
        double centerY = y + getHeight() / 2.0;
        double rad = Math.toRadians(deg);
        double cos = Math.cos(rad), sin = Math.sin(rad);

        double[] lx = {x + localX - centerX, x + localX - centerX, x + localX + localW - centerX, x + localX + localW - centerX};
        double[] ly = {y + localY - centerY, y + localY + localH - centerY, y + localY + localH - centerY, y + localY - centerY};
        double[] cx = new double[4];
        double[] cy = new double[4];

        for (int i = 0; i < 4; i++) {
            cx[i] = centerX + lx[i] * cos - ly[i] * sin;
            cy[i] = centerY + lx[i] * sin + ly[i] * cos;
        }

        renderer.triangle(cx[0], cy[0], cx[1], cy[1], cx[2], cy[2], color);
        renderer.triangle(cx[0], cy[0], cx[2], cy[2], cx[3], cy[3], color);
    }

    private float getRenderProgress(float target) {
        if (!smoothFill.get()) {
            smoothedProgress = target;
            lastSmoothNanos = -1;
            return target;
        }

        long now = System.nanoTime();
        if (smoothedProgress < 0 || lastSmoothNanos < 0) {
            smoothedProgress = target;
        } else {
            float dt = (now - lastSmoothNanos) / 1_000_000_000f;
            float t = 1f - (float) Math.exp(-smoothSpeed.get() * dt);
            smoothedProgress += (target - smoothedProgress) * t;
        }
        lastSmoothNanos = now;

        return smoothedProgress;
    }

    /** Null if the player's own displayed name has no match for the configured pattern. */
    private float[] parseNametagHealth() {
        if (mc.player == null) return null;

        try {
            Matcher matcher = Pattern.compile(nametagPattern.get()).matcher(mc.player.getDisplayName().getString());
            if (!matcher.find()) return null;

            return new float[]{Float.parseFloat(matcher.group(1)), Float.parseFloat(matcher.group(2))};
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public void render(HudRenderer renderer) {
        float current, max;

        if (isInEditor()) {
            current = 15;
            max = 20;
        }
        else if (mc.player == null) {
            current = 0;
            max = 20;
        }
        else {
            float[] nametag = healthSource.get() == HealthSource.Nametag ? parseNametagHealth() : null;

            if (nametag != null) {
                current = nametag[0];
                max = nametag[1];
            } else {
                current = mc.player.getHealth();
                max = mc.player.getMaxHealth();
            }
        }

        float target = max > 0 ? MathHelper.clamp(current / max, 0, 1) : 0;
        float progress = getRenderProgress(target);

        double w = getWidth();
        double h = getHeight();

        if (displayMode.get() != DisplayMode.Numbers) {
            HealthBarAdjust adjust = Modules.get().get(HealthBarAdjust.class);
            Color background = adjust.isActive() ? adjust.getBackgroundColor(backgroundColor.get()) : backgroundColor.get();
            Color fill = adjust.isActive() ? adjust.getFillColor(fillColor.get(), target) : fillColor.get();

            drawRotatedQuad(renderer, 0, 0, w, h, background);
            if (progress > 0) drawRotatedQuad(renderer, 0, 0, w * progress, h, fill);
        }

        if (displayMode.get() != DisplayMode.Bar) {
            String text = Math.round(current) + "/" + Math.round(max);
            double tx = x + w / 2.0 - renderer.textWidth(text) / 2.0;
            double ty = y + h / 2.0 - renderer.textHeight() / 2.0;

            renderer.text(text, tx, ty, textColor.get(), true);
        }
    }
}
