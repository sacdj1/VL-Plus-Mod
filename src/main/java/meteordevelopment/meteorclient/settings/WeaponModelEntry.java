/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.settings;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;

import java.util.ArrayList;
import java.util.List;

/**
 * One weapon's set of resource-pack model swaps for Weapon Models - matched the same way Animations
 * matches a weapon ("minecraft:bow", or "name:Doom Launcher" for the display name), but instead of
 * picking an animation, each of the four ability states below picks a CustomModelData value for the
 * resource pack's own model overrides to key off. -1 means "don't override this state" - the item
 * renders however it normally would (vanilla/CIT's own default), same as not having an entry at all
 * for that state.
 *
 * tierOverrides optionally gives specific tiers (per ItemInfo's own findWeaponTierInfo - plain
 * "Tier N", "Tier N Enhancer", or "Tier N Unique Item" lore lines) their own CustomModelData values
 * instead of the base idle/activate/active/deactivate above - e.g. a Tier 7 bow getting a fancier
 * glow than a Tier 2 one. A tier override's own field left at -1 falls back to this entry's base
 * field for that state (which may itself be -1, i.e. no override at all) - so a tier override only
 * needs to set the states it actually wants to change. "Unique" is its own separate rarity overlay
 * on top of a tier (e.g. a Tier 4 Unique item is a different, rarer thing than a plain Tier 4 one,
 * not just a recolor) - a tier override's `unique` flag picks which of those two it matches, so a
 * weapon can have one override for "Tier 4" and a completely separate one for "Tier 4 Unique".
 */
public class WeaponModelEntry {
    public String weapon = "";
    public int idle = -1;
    public int activate = -1;
    public int active = -1;
    public int deactivate = -1;

    public List<TierOverride> tierOverrides = new ArrayList<>();

    public static class TierOverride {
        public int tier = 1;
        public boolean unique = false;
        public int idle = -1;
        public int activate = -1;
        public int active = -1;
        public int deactivate = -1;

        public NbtCompound toTag() {
            NbtCompound tag = new NbtCompound();

            tag.putInt("tier", tier);
            tag.putBoolean("unique", unique);
            tag.putInt("idle", idle);
            tag.putInt("activate", activate);
            tag.putInt("active", active);
            tag.putInt("deactivate", deactivate);

            return tag;
        }

        public static TierOverride fromTag(NbtCompound tag) {
            TierOverride override = new TierOverride();

            override.tier = tag.contains("tier") ? tag.getInt("tier") : 1;
            override.unique = tag.getBoolean("unique");
            override.idle = tag.contains("idle") ? tag.getInt("idle") : -1;
            override.activate = tag.contains("activate") ? tag.getInt("activate") : -1;
            override.active = tag.contains("active") ? tag.getInt("active") : -1;
            override.deactivate = tag.contains("deactivate") ? tag.getInt("deactivate") : -1;

            return override;
        }
    }

    public NbtCompound toTag() {
        NbtCompound tag = new NbtCompound();

        tag.putString("weapon", weapon);
        tag.putInt("idle", idle);
        tag.putInt("activate", activate);
        tag.putInt("active", active);
        tag.putInt("deactivate", deactivate);

        NbtList tiers = new NbtList();
        for (TierOverride override : tierOverrides) tiers.add(override.toTag());
        tag.put("tierOverrides", tiers);

        return tag;
    }

    public static WeaponModelEntry fromTag(NbtCompound tag) {
        WeaponModelEntry entry = new WeaponModelEntry();

        entry.weapon = tag.getString("weapon");
        entry.idle = tag.contains("idle") ? tag.getInt("idle") : -1;
        entry.activate = tag.contains("activate") ? tag.getInt("activate") : -1;
        entry.active = tag.contains("active") ? tag.getInt("active") : -1;
        entry.deactivate = tag.contains("deactivate") ? tag.getInt("deactivate") : -1;

        if (tag.contains("tierOverrides")) {
            for (NbtElement e : tag.getList("tierOverrides", NbtElement.COMPOUND_TYPE)) {
                entry.tierOverrides.add(TierOverride.fromTag((NbtCompound) e));
            }
        }

        return entry;
    }
}
