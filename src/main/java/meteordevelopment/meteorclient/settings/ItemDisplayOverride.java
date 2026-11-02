/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.settings;

import net.minecraft.nbt.NbtCompound;

/**
 * One named-item entry in Item Display's per-item overrides list - matches by name/lore (either one
 * blank skips that check), then nudges (Add) or replaces (Override) that item's model display
 * transform (translation/rotation/scale) the same way the General override does, just scoped to
 * whatever matches here instead of every flat item. Deliberately a plain POJO, like QuickAction.
 */
public class ItemDisplayOverride {
    public enum Mode {
        /** Added on top of whatever the item's own model already specifies (CIT-selected model included). */
        Add,
        /** Replaces the item's own value outright. */
        Override
    }

    public String label = "Item";
    public boolean enabled = true;

    /** Case-insensitive, anywhere in the item's display name - blank skips this check. */
    public String nameMatch = "";
    /** Case-insensitive, anywhere in any one lore line - blank skips this check. */
    public String loreMatch = "";

    public Mode mode = Mode.Add;

    public double translateX = 0, translateY = 0, translateZ = 0;
    public double rotateX = 0, rotateY = 0, rotateZ = 0;
    public double scaleX = 1, scaleY = 1, scaleZ = 1;

    public NbtCompound toTag() {
        NbtCompound tag = new NbtCompound();

        tag.putString("label", label);
        tag.putBoolean("enabled", enabled);
        tag.putString("nameMatch", nameMatch);
        tag.putString("loreMatch", loreMatch);
        tag.putString("mode", mode.name());
        tag.putDouble("translateX", translateX);
        tag.putDouble("translateY", translateY);
        tag.putDouble("translateZ", translateZ);
        tag.putDouble("rotateX", rotateX);
        tag.putDouble("rotateY", rotateY);
        tag.putDouble("rotateZ", rotateZ);
        tag.putDouble("scaleX", scaleX);
        tag.putDouble("scaleY", scaleY);
        tag.putDouble("scaleZ", scaleZ);

        return tag;
    }

    public static ItemDisplayOverride fromTag(NbtCompound tag) {
        ItemDisplayOverride o = new ItemDisplayOverride();

        o.label = tag.getString("label");
        o.enabled = !tag.contains("enabled") || tag.getBoolean("enabled");
        o.nameMatch = tag.getString("nameMatch");
        o.loreMatch = tag.getString("loreMatch");

        try {
            o.mode = Mode.valueOf(tag.getString("mode"));
        } catch (IllegalArgumentException e) {
            o.mode = Mode.Add;
        }

        o.translateX = tag.getDouble("translateX");
        o.translateY = tag.getDouble("translateY");
        o.translateZ = tag.getDouble("translateZ");
        o.rotateX = tag.getDouble("rotateX");
        o.rotateY = tag.getDouble("rotateY");
        o.rotateZ = tag.getDouble("rotateZ");
        o.scaleX = tag.contains("scaleX") ? tag.getDouble("scaleX") : 1;
        o.scaleY = tag.contains("scaleY") ? tag.getDouble("scaleY") : 1;
        o.scaleZ = tag.contains("scaleZ") ? tag.getDouble("scaleZ") : 1;

        return o;
    }
}
