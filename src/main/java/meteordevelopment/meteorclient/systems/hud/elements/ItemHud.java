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
import meteordevelopment.meteorclient.systems.vlitems.VLItemChoice;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryWrapper;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class ItemHud extends HudElement {
    public static final HudElementInfo<ItemHud> INFO = new HudElementInfo<>(Hud.GROUP, "item", "Displays the item count.", ItemHud::new);

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgBackground = settings.createGroup("Background");

    // General

    private final Setting<VLItemChoice> item = sgGeneral.add(new VLItemSetting.Builder()
        .name("item")
        .description("Item to display - can also be a captured VL item.")
        .defaultValue(VLItemChoice.of(Items.TOTEM_OF_UNDYING))
        .build()
    );

    private final Setting<NoneMode> noneMode = sgGeneral.add(new EnumSetting.Builder<NoneMode>()
        .name("none-mode")
        .description("How to render the item when you don't have the specified item in your inventory.")
        .defaultValue(NoneMode.HideCount)
        .build()
    );

    private final Setting<Double> scale = sgGeneral.add(new DoubleSetting.Builder()
        .name("scale")
        .description("Scale of the item.")
        .defaultValue(2)
        .onChanged(aDouble -> calculateSize())
        .min(1)
        .sliderRange(1, 4)
        .build()
    );

    private final Setting<Integer> border = sgGeneral.add(new IntSetting.Builder()
        .name("border")
        .description("How much space to add around the element.")
        .defaultValue(0)
        .onChanged(integer -> calculateSize())
        .build()
    );

    // Background

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

    private ItemHud() {
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
        VLItemChoice choice = item.get();
        RegistryWrapper.WrapperLookup registries = mc.world != null ? mc.world.getRegistryManager() : null;

        int count = registries != null ? InvUtils.find(stack -> choice.matches(stack, registries)).count() : 0;

        // Icon stack always stays at its natural count (1) - some CIT/CTM resource pack rules for
        // unique-looking items key off count=1, and would silently fall back to the plain texture
        // if the real inventory count (which can be > 1 once the item stacks) were written onto it.
        // The count shown to the player is passed separately as a text overlay below.
        ItemStack itemStack = registries != null ? choice.getIcon(registries) : choice.getBaseItem().getDefaultStack();

        if (noneMode.get() == NoneMode.HideItem && count <= 0) {
            if (isInEditor()) {
                renderer.line(x, y, x + getWidth(), y + getHeight(), Color.GRAY);
                renderer.line(x, y + getHeight(), x + getWidth(), y, Color.GRAY);
            }
        } else {
            renderer.post(() -> {
                double x = this.x + border.get();
                double y = this.y + border.get();

                render(renderer, itemStack, count, (int) x, (int) y);
            });
        }

        if (background.get()) renderer.quad(x, y, getWidth(), getHeight(), backgroundColor.get());
    }

    private void render(HudRenderer renderer, ItemStack itemStack, int count, int x, int y) {
        String countOverride = (count <= 0 && noneMode.get() == NoneMode.HideCount) ? null : String.valueOf(Math.max(count, 0));

        renderer.item(itemStack, x, y, scale.get().floatValue(), true, countOverride);
    }

    public enum NoneMode {
        HideItem,
        HideCount,
        ShowCount;

        @Override
        public String toString() {
            return switch (this) {
                case HideItem -> "Hide Item";
                case HideCount -> "Hide Count";
                case ShowCount -> "Show Count";
            };
        }
    }
}
