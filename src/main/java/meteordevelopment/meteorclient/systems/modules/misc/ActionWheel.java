/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.misc;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.screens.ActionWheelScreen;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.Screen;

import java.util.ArrayList;
import java.util.List;

/**
 * Hold a key, a circular menu takes over the mouse so you can pick a slice with it, release (or
 * click away) to close - a Figura-style action wheel, but for toggling modules/running commands
 * instead of avatar animations. Deliberately a separate module from Quick Actions (a plain
 * button-list menu) rather than another mode of it, even though both share the same per-entry
 * "label + module-to-toggle or command(s)" shape (QuickAction) for its entries.
 * <p>
 * Left-click fires whichever slice is currently highlighted and closes. Right-click closes
 * without firing anything. Scroll changes page if there are more entries than fit on one ring.
 * Releasing the hold key with nothing clicked also just closes without firing anything - holding
 * it is what keeps the wheel open, not a toggle.
 */
public class ActionWheel extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgAppearance = settings.createGroup("Appearance");

    private final Setting<Keybind> openKey = sgGeneral.add(new KeybindSetting.Builder()
        .name("open-key")
        .description("Hold this to open the wheel. Releasing it closes the wheel again (without firing anything, unless you'd already left-clicked a slice).")
        .defaultValue(Keybind.none())
        .build()
    );

    private final Setting<Boolean> allowWhileScreenOpen = sgGeneral.add(new BoolSetting.Builder()
        .name("allow-while-screen-open")
        .description("Lets the wheel open even while another screen (inventory, chat, etc.) is already open, drawn on top of it - it's restored underneath once the wheel closes. Off: only opens during plain gameplay with nothing else open, to avoid fighting a text box or another screen's own clicks. Experimental with container screens (inventory, chests) specifically - replacing the screen to show the wheel and then putting the original back isn't guaranteed to leave server-side container sync in a good state.")
        .defaultValue(false)
        .build()
    );

    private final Setting<List<QuickAction>> actions = sgGeneral.add(new QuickActionListSetting.Builder()
        .name("actions")
        .description("The wheel's slices, in order - each with its own label, and either a module to toggle, command(s) to run, or a Submenu of its own nested slices to navigate into (one layer deeper, wheel stays open) - same entry type Quick Actions uses, plus Submenu, which only this wheel actually navigates into.")
        .defaultValue(new ArrayList<>())
        .build()
    );

    private final Setting<Integer> maxPerPage = sgGeneral.add(new IntSetting.Builder()
        .name("max-per-page")
        .description("How many slices show at once - with more entries than this, scroll while the wheel is open to page through the rest.")
        .defaultValue(8)
        .min(2)
        .sliderRange(2, 16)
        .build()
    );

    private final Setting<Double> radius = sgAppearance.add(new DoubleSetting.Builder()
        .name("radius")
        .description("Distance from the center of the screen to each slice, in pixels.")
        .defaultValue(90.0)
        .min(30)
        .sliderRange(30, 300)
        .build()
    );

    private final Setting<Double> deadzoneRadius = sgAppearance.add(new DoubleSetting.Builder()
        .name("deadzone-radius")
        .description("How close to dead-center the mouse can be without highlighting (and being able to confirm) any slice at all - gives you a safe \"pick nothing\" spot in the middle.")
        .defaultValue(20.0)
        .min(0)
        .sliderRange(0, 100)
        .build()
    );

    private final Setting<Double> slotSize = sgAppearance.add(new DoubleSetting.Builder()
        .name("slot-size")
        .description("Size (width and height) of each slice's box, in pixels.")
        .defaultValue(50.0)
        .min(16)
        .sliderRange(16, 150)
        .build()
    );

    private final Setting<Boolean> dimBackground = sgAppearance.add(new BoolSetting.Builder()
        .name("dim-background")
        .description("Dims the view behind the wheel while it's open (only when nothing else was already open - Allow While Screen Open's own screen provides its own backdrop).")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> slotColor = sgAppearance.add(new ColorSetting.Builder()
        .name("slot-color")
        .description("Color of a slice that isn't currently highlighted.")
        .defaultValue(new SettingColor(30, 30, 30, 200))
        .build()
    );

    private final Setting<SettingColor> highlightColor = sgAppearance.add(new ColorSetting.Builder()
        .name("highlight-color")
        .description("Color of the slice the mouse is currently pointing at.")
        .defaultValue(new SettingColor(85, 255, 85, 230))
        .build()
    );

    private final Setting<SettingColor> textColor = sgAppearance.add(new ColorSetting.Builder()
        .name("text-color")
        .description("Label color on a slice that isn't highlighted.")
        .defaultValue(new SettingColor(255, 255, 255))
        .build()
    );

    private final Setting<SettingColor> highlightTextColor = sgAppearance.add(new ColorSetting.Builder()
        .name("highlight-text-color")
        .description("Label color on the highlighted slice.")
        .defaultValue(new SettingColor(0, 0, 0))
        .build()
    );

    public ActionWheel() {
        super(Categories.Misc, "action-wheel", "A Figura-style radial menu, held open with a key, for quickly toggling modules or running commands.", "wheel", "quick-actions-ring", "radial-menu");
    }

    public List<QuickAction> getActions() {
        return actions.get();
    }

    public int maxPerPage() {
        return maxPerPage.get();
    }

    public double radius() {
        return radius.get();
    }

    public double deadzoneRadius() {
        return deadzoneRadius.get();
    }

    public double slotSize() {
        return slotSize.get();
    }

    public boolean dimBackground() {
        return dimBackground.get();
    }

    public SettingColor slotColor() {
        return slotColor.get();
    }

    public SettingColor highlightColor() {
        return highlightColor.get();
    }

    public SettingColor textColor() {
        return textColor.get();
    }

    public SettingColor highlightTextColor() {
        return highlightTextColor.get();
    }

    /** Runs a slice's action - toggles its module, or sends its command(s) as if typed in chat. */
    public void execute(QuickAction action) {
        if (action.type == QuickAction.Type.Module) {
            Module target = Modules.get().get(action.moduleId.trim());
            if (target != null) target.toggle();
            return;
        }

        for (String command : action.command.split(";")) {
            String trimmed = command.trim();
            if (!trimmed.isEmpty()) ChatUtils.sendPlayerMsg(trimmed);
        }
    }

    @Override
    public void onDeactivate() {
        if (mc.currentScreen instanceof ActionWheelScreen wheel) wheel.cancel();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        boolean held = openKey.get().isPressed();
        boolean wheelOpen = mc.currentScreen instanceof ActionWheelScreen;

        if (held && !wheelOpen) {
            Screen current = mc.currentScreen;
            if (current != null && !allowWhileScreenOpen.get()) return;

            mc.setScreen(new ActionWheelScreen(this, current));
        } else if (!held && wheelOpen) {
            ((ActionWheelScreen) mc.currentScreen).cancel();
        }
    }
}
