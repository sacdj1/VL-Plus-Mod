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
 * Shows whatever item is physically sitting in the chestplate armor slot, regardless of what it
 * is - same reasoning as HotbarSlotHud (see there), just reading getArmorStack(2) instead of a
 * hotbar index. Works with Item Info the same way, unlike the vanilla Armor HUD bar.
 */
public class ChestplateSlotHud extends HudElement {
    public static final HudElementInfo<ChestplateSlotHud> INFO = new HudElementInfo<>(Hud.GROUP, "chestplate-slot", "Displays whatever item is currently in the chestplate armor slot.", ChestplateSlotHud::new);

    private static final int CHESTPLATE_SLOT = 2;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgBackground = settings.createGroup("Background");

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

    private ChestplateSlotHud() {
        super(INFO);

        calculateSize();
    }

    @Override
    public void setSize(double width, double height) {
        super.setSize(width + border.get() * 2, height + border.get() * 2);
    }

    private void calculateSize() {
        setSize(17 * scale.get(), 17 * scale.get());
    }

    @Override
    public void render(HudRenderer renderer) {
        if (background.get()) renderer.quad(x, y, getWidth(), getHeight(), backgroundColor.get());

        ItemStack stack = mc.player != null ? mc.player.getInventory().getArmorStack(CHESTPLATE_SLOT) : ItemStack.EMPTY;

        if (stack.isEmpty() && !isInEditor()) return;

        if (stack.isEmpty()) {
            renderer.line(x, y, x + getWidth(), y + getHeight(), Color.GRAY);
            renderer.line(x, y + getHeight(), x + getWidth(), y, Color.GRAY);
        } else {
            renderer.post(() -> {
                double px = this.x + border.get();
                double py = this.y + border.get();

                String countOverride = stack.getCount() > 1 ? String.valueOf(stack.getCount()) : null;
                renderer.item(stack, (int) px, (int) py, scale.get().floatValue(), true, countOverride);
            });
        }
    }
}
