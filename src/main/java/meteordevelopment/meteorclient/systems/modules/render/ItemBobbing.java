/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.render;

import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;

/**
 * Vanilla's Bobbing option actually drives two independent things: the world/camera view (set up
 * once per frame in GameRenderer's own render pass) and the held item/hand (set up separately,
 * again, inside GameRenderer.renderHand) - both read the same walk-speed input, but as two entirely
 * separate calls, which is why it's possible to cancel one without touching the other. This cancels
 * just the world/camera one, leaving the item's own vanilla sway completely untouched - so the item
 * still bobs exactly like it always did, only the world itself stops swaying around it.
 */
public class ItemBobbing extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    public ItemBobbing() {
        super(Categories.Camera, "item-bobbing", "Stops the world/camera from swaying while you walk (Options > Bobbing), without touching the held item's own sway - the item keeps bobbing normally, only the world stops moving under it.", "no-view-bob", "view-bobbing", "no-camera-shake");
    }

    public boolean stopVanillaBob() {
        return isActive();
    }
}
