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
import net.minecraft.util.Hand;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * Shows whatever item is currently held, main hand or off hand (your choice) - tracks the active
 * slot dynamically, unlike HotbarSlotHud which always shows one fixed slot number regardless of
 * what's selected. Same render path as HotbarSlotHud otherwise (see there).
 */
public class HeldItemHud extends HudElement {
    public static final HudElementInfo<HeldItemHud> INFO = new HudElementInfo<>(Hud.GROUP, "held-item", "Displays whatever item is currently held.", HeldItemHud::new);

    public enum HandChoice {
        MainHand,
        OffHand
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgBackground = settings.createGroup("Background");

    private final Setting<HandChoice> hand = sgGeneral.add(new EnumSetting.Builder<HandChoice>()
        .name("hand")
        .description("Which hand to show the held item of.")
        .defaultValue(HandChoice.MainHand)
        .build()
    );

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

    private HeldItemHud() {
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

        ItemStack stack = mc.player != null ? mc.player.getStackInHand(hand.get() == HandChoice.MainHand ? Hand.MAIN_HAND : Hand.OFF_HAND) : ItemStack.EMPTY;

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
