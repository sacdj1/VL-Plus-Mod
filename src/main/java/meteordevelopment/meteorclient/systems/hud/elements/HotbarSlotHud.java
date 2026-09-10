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
 * Shows whatever item is physically sitting in a fixed hotbar slot, regardless of what it is -
 * unlike ItemHud, which searches the whole inventory for a chosen item type. Renders through the
 * same RenderUtils.drawItem(..., overlay=true) path ItemHud uses, which calls vanilla's own
 * DrawContext.drawItemInSlot - the same method Item Info hooks - so its badges/durability-bar
 * override apply here too, unlike the vanilla Armor HUD bar (which draws generic armor-point
 * sprites, not actual item icons, so there's nothing for Item Info to attach to there).
 */
public class HotbarSlotHud extends HudElement {
    public static final HudElementInfo<HotbarSlotHud> INFO = new HudElementInfo<>(Hud.GROUP, "hotbar-slot", "Displays whatever item is currently in a chosen hotbar slot.", HotbarSlotHud::new);

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgBackground = settings.createGroup("Background");

    private final Setting<Integer> slot = sgGeneral.add(new IntSetting.Builder()
        .name("slot")
        .description("Hotbar slot to display, matching the number keys (1-9).")
        .defaultValue(1)
        .min(1)
        .max(9)
        .sliderRange(1, 9)
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

    private HotbarSlotHud() {
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

        ItemStack stack = mc.player != null ? mc.player.getInventory().getStack(slot.get() - 1) : ItemStack.EMPTY;

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
