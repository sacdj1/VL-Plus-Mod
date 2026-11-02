/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.utils.world;

import meteordevelopment.meteorclient.utils.misc.Names;
import net.minecraft.block.Block;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
import org.apache.commons.lang3.StringUtils;

/**
 * Shared block-search matching for every block-list screen (Global/Region/Biome/Season Block
 * Colors, Block Data, Block setting picker, Particle Color's own block list) - a plain query
 * substring-matches the block's display name, same as before; a "#tagname" query instead matches
 * every block in that tag, same "#" syntax the vanilla creative inventory search already uses
 * (namespace defaults to "minecraft" when none is given, so "#wood" and "#minecraft:wood" are the
 * same query).
 */
public class BlockSearch {
    private BlockSearch() {
    }

    public static boolean matches(Block block, String query) {
        query = query.trim();
        if (query.isEmpty()) return true;

        if (query.startsWith("#")) {
            String tagId = query.substring(1).trim();
            if (tagId.isEmpty()) return true;

            // tryParse (never Identifier.of(), which THROWS on a bad character instead of
            // returning null) - this runs again on every single keystroke typed into a block
            // search box, so a query not yet a valid identifier (while still typing a tag name,
            // or an accidental stray/non-ASCII character) is just "no match yet", not a crash. One
            // bad keystroke anywhere, in any of this screen's several search boxes, used to crash
            // the whole game instantly.
            Identifier id;
            try {
                id = Identifier.tryParse(tagId.contains(":") ? tagId : "minecraft:" + tagId);
            } catch (Exception e) {
                id = null;
            }
            if (id == null) return false;

            return Registries.BLOCK.getEntry(block).isIn(TagKey.of(RegistryKeys.BLOCK, id));
        }

        return StringUtils.containsIgnoreCase(Names.get(block), query);
    }
}
