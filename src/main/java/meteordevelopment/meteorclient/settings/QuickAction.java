/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.settings;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;

import java.util.ArrayList;
import java.util.List;

/**
 * A single button in the Quick Actions menu (or a slice in the Action Wheel) - deliberately a
 * plain POJO edited via raw widgets (like AmbienceRegion/ReaderRule), not a mini Settings
 * instance. Submenu-type entries carry their own nested Children list, recursively of the same
 * type - only the Action Wheel actually navigates into these (hold-and-release radial menus are
 * naturally suited to "select this to go a layer deeper"); Quick Actions' own plain button list
 * has no layering concept and just skips Submenu entries entirely.
 */
public class QuickAction {
    public enum Type {
        Command,
        Module,
        Submenu
    }

    /** Purely per-action - there's no overall module setting this falls back to. Submenu entries ignore this; selecting one never "closes" anything, it navigates deeper instead. */
    public enum CloseBehavior {
        Close,
        Stay
    }

    public String label = "Action";
    public Type type = Type.Command;

    /** Used when type is Command - one or more commands, ";" separated, run as if typed in chat. */
    public String command = "";

    /** Used when type is Module - the module's own id (not its display title). */
    public String moduleId = "";

    /** Used when type is Submenu - this entry's own nested slices, one layer deeper. */
    public List<QuickAction> children = new ArrayList<>();

    public CloseBehavior closeBehavior = CloseBehavior.Stay;

    public NbtCompound toTag() {
        NbtCompound tag = new NbtCompound();

        tag.putString("label", label);
        tag.putString("type", type.name());
        tag.putString("command", command);
        tag.putString("moduleId", moduleId);
        tag.putString("closeBehavior", closeBehavior.name());

        NbtList childrenTag = new NbtList();
        for (QuickAction child : children) childrenTag.add(child.toTag());
        tag.put("children", childrenTag);

        return tag;
    }

    public static QuickAction fromTag(NbtCompound tag) {
        QuickAction action = new QuickAction();

        action.label = tag.getString("label");
        action.command = tag.getString("command");
        action.moduleId = tag.getString("moduleId");

        try {
            action.type = Type.valueOf(tag.getString("type"));
        } catch (IllegalArgumentException e) {
            action.type = Type.Command;
        }

        try {
            action.closeBehavior = CloseBehavior.valueOf(tag.getString("closeBehavior"));
        } catch (IllegalArgumentException e) {
            // Also catches a pre-existing saved entry's "Default" - removed as its own value now
            // that there's no overall setting left for it to mean "follow" - Stay matches what
            // that setting's own default value used to be, so an old save doesn't change behavior.
            action.closeBehavior = CloseBehavior.Stay;
        }

        for (var childTag : tag.getList("children", net.minecraft.nbt.NbtElement.COMPOUND_TYPE)) {
            action.children.add(fromTag((NbtCompound) childTag));
        }

        return action;
    }
}
