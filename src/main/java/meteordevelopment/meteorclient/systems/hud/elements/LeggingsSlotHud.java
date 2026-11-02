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
import net.minecraft.item.ItemStack;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * Shows whatever item is physically sitting in the leggings armor slot, regardless of what it is -
 * same reasoning as HotbarSlotHud (see there), just reading getArmorStack(1) instead of a hotbar
 * index. Works with Item Info the same way, unlike the vanilla Armor HUD bar.
 */
public class LeggingsSlotHud extends HudElement {

    @Override
    public boolean hasOwnBackground() {
        return true;
    }
    public static final HudElementInfo<LeggingsSlotHud> INFO = new HudElementInfo<>(Hud.GROUP, "leggings-slot", "Displays whatever item is currently in the leggings armor slot.", LeggingsSlotHud::new);

    private static final int LEGGINGS_SLOT = 1;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgBackground = settings.createGroup("Background");
    private final SettingGroup sgBorder = settings.createGroup("Border");

    private final Setting<Double> scale = sgGeneral.add(new DoubleSetting.Builder()
        .name("scale")
        .description("Scale of the item.")
        .defaultValue(2)
        .onChanged(scale -> calculateSize())
        .min(1)
        .sliderRange(1, 4)
        .build()
    );

    private final Setting<Integer> border = sgGeneral.add(new IntSetting.Builder()
        .name("border")
        .description("How much space to add around the element.")
        .defaultValue(0)
        .onChanged(border -> calculateSize())
        .build()
    );

    private final Setting<Boolean> background = sgBackground.add(new BoolSetting.Builder()
        .name("background")
        .description("Displays background.")
        .defaultValue(false)
        .build()
    );

    private final Setting<SettingColor> backgroundColor = sgBackground.add(new ColorSetting.Builder()
        .name("background-color")
        .description("Color used for the background.")
        .visible(background::get)
        .defaultValue(new SettingColor(25, 25, 25, 50))
        .build()
    );

    private final Setting<Boolean> showBorder = sgBorder.add(new BoolSetting.Builder()
        .name("show-border")
        .description("Draws an outline around the element.")
        .defaultValue(false)
        .onChanged(v -> calculateSize())
        .build()
    );

    private final Setting<SettingColor> borderColor = sgBorder.add(new ColorSetting.Builder()
        .name("border-color")
        .description("Color of the outline.")
        .defaultValue(new SettingColor(255, 255, 255))
        .visible(showBorder::get)
        .build()
    );

    private final Setting<Integer> borderThickness = sgBorder.add(new IntSetting.Builder()
        .name("border-thickness")
        .description("Thickness of the outline, in pixels. Also reserves this much extra space around the item so the outline doesn't overlap it.")
        .defaultValue(1)
        .min(1)
        .sliderRange(1, 5)
        .visible(showBorder::get)
        .onChanged(v -> calculateSize())
        .build()
    );

    private LeggingsSlotHud() {
        super(INFO);

        calculateSize();
    }

    // Plain spacing setting - independent of the border outline. The outline itself (below) is
    // drawn OUTSIDE the box's own bounds rather than reserved-for by growing the box - see
    // HeldItemHud's identical itemInset() for the full rationale (growing the box broke anchoring:
    // for a Left/Top-anchored element, growing size only extends the box AWAY from its anchored
    // corner, so the item visibly shifted toward that corner whenever the border toggled on).
    private double itemInset() {
        return border.get();
    }

    @Override
    public void setSize(double width, double height) {
        super.setSize(width, height);
    }

    private void calculateSize() {
        setSize(17 * scale.get(), 17 * scale.get());
    }

    @Override
    public void render(HudRenderer renderer) {
        if (background.get()) renderer.quad(x, y, getWidth(), getHeight(), backgroundColor.get());

        if (showBorder.get()) {
            double t = borderThickness.get() * scale.get();
            double w = getWidth(), h = getHeight();
            Color c = borderColor.get();

            renderer.quad(x - t, y - t, w + t * 2, t, c);
            renderer.quad(x - t, y + h, w + t * 2, t, c);
            renderer.quad(x - t, y, t, h, c);
            renderer.quad(x + w, y, t, h, c);
        }

        ItemStack stack = mc.player != null ? mc.player.getInventory().getArmorStack(LEGGINGS_SLOT) : ItemStack.EMPTY;

        if (stack.isEmpty() && !isInEditor()) return;

        if (stack.isEmpty()) {
            renderer.line(x, y, x + getWidth(), y + getHeight(), Color.GRAY);
            renderer.line(x, y + getHeight(), x + getWidth(), y, Color.GRAY);
        } else {
            renderer.post(() -> {
                double inset = itemInset();
                double px = this.x + inset;
                double py = this.y + inset;

                String countOverride = stack.getCount() > 1 ? String.valueOf(stack.getCount()) : null;
                renderer.item(stack, (int) px, (int) py, scale.get().floatValue(), true, countOverride);
            });
        }
    }
}
