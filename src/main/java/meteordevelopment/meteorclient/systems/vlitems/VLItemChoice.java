/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.vlitems;

import meteordevelopment.meteorclient.utils.misc.Names;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryWrapper;

/**
 * Either a plain vanilla {@link Item} or a captured {@link VLItem}, so a single picker/setting can
 * offer both interchangeably. Exactly one of item/vlItem is non-null.
 */
public class VLItemChoice {
    public final Item item;
    public final VLItem vlItem;

    private VLItemChoice(Item item, VLItem vlItem) {
        this.item = item;
        this.vlItem = vlItem;
    }

    public static VLItemChoice of(Item item) {
        return new VLItemChoice(item, null);
    }

    public static VLItemChoice of(VLItem vlItem) {
        return new VLItemChoice(null, vlItem);
    }

    public Item getBaseItem() {
        return vlItem != null ? vlItem.baseItem : item;
    }

    public String getName() {
        return vlItem != null ? vlItem.displayLabel : Names.get(item);
    }

    public ItemStack getIcon(RegistryWrapper.WrapperLookup registries) {
        return vlItem != null ? vlItem.buildStack(registries) : item.getDefaultStack();
    }

    public boolean matches(ItemStack stack, RegistryWrapper.WrapperLookup registries) {
        return vlItem != null ? vlItem.matches(stack, registries) : stack.getItem() == item;
    }
}
