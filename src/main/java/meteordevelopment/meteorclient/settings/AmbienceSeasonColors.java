/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.settings;

import meteordevelopment.meteorclient.systems.modules.world.Ambience;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.block.Block;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.Registries;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One season's own fixed set of colors - sky (per dimension), cloud, lightning, lava stay flat
 * values, while grass/foliage/water/regular blocks use the exact same per-block color map every
 * other block-color screen (Global Blocks, Regions, Per-Biome) already uses (individual blocks, or
 * the "Override Entire Season"/"Override Grass/Foliage/Water Color" sentinel rows via
 * {@link Ambience#OVERRIDE_ALL}/OVERRIDE_GRASS/OVERRIDE_FOLIAGE/OVERRIDE_WATER) - so a season can
 * color individual blocks (e.g. just oak leaves) the same way a Region or Global Blocks list can,
 * not just one blanket grass/foliage/water color each.
 *
 * Deliberately NOT a snapshot of Ambience's own override settings (customGrassColor, grassColor,
 * etc.) - a season is a separate, lower-priority color layer the renderer consults directly (see
 * Ambience.currentSeasonColors()), so turning a season on/off never touches, and is never visible
 * through, the main override settings a manual override can still turn on independently and take
 * priority over.
 */
public class AmbienceSeasonColors {
    /** False until "Save Current" is used at least once - an unsaved slot contributes nothing. */
    public boolean saved = false;

    // Each flat color has its own on/off, same as Ambience's own Custom Sky/Cloud/etc. Color
    // toggles - off by default (a freshly-added season, or one channel you've never touched, should
    // never silently start overriding anything) until you explicitly turn it on for that one
    // channel. Lets a season customize just e.g. Sky without also forcing Cloud/Lightning/Lava to
    // this season's (unset, default-white) values.
    public boolean overworldSkyEnabled, netherSkyEnabled, endSkyEnabled, cloudEnabled, lightningEnabled, lavaEnabled;

    public SettingColor overworldSky = new SettingColor(255, 255, 255, 255);
    public SettingColor netherSky = new SettingColor(255, 255, 255, 255);
    public SettingColor endSky = new SettingColor(255, 255, 255, 255);
    public SettingColor cloud = new SettingColor(255, 255, 255, 255);
    public SettingColor lightning = new SettingColor(255, 255, 255, 255);
    public SettingColor lava = new SettingColor(255, 255, 255, 255);

    public Map<Block, List<WeightedColorEntry>> blocks = new LinkedHashMap<>();
    public Set<Block> disabledBlocks = new LinkedHashSet<>();

    public NbtCompound toTag() {
        NbtCompound tag = new NbtCompound();

        tag.putBoolean("saved", saved);
        tag.putBoolean("overworldSkyEnabled", overworldSkyEnabled);
        tag.putBoolean("netherSkyEnabled", netherSkyEnabled);
        tag.putBoolean("endSkyEnabled", endSkyEnabled);
        tag.putBoolean("cloudEnabled", cloudEnabled);
        tag.putBoolean("lightningEnabled", lightningEnabled);
        tag.putBoolean("lavaEnabled", lavaEnabled);
        tag.put("overworldSky", overworldSky.toTag());
        tag.put("netherSky", netherSky.toTag());
        tag.put("endSky", endSky.toTag());
        tag.put("cloud", cloud.toTag());
        tag.put("lightning", lightning.toTag());
        tag.put("lava", lava.toTag());
        tag.put("blockColors", BlockColorMapNbt.toTag(blocks));

        NbtCompound disabledTag = new NbtCompound();
        var disabledList = new net.minecraft.nbt.NbtList();
        for (Block block : disabledBlocks) disabledList.add(NbtString.of(Registries.BLOCK.getId(block).toString()));
        tag.put("disabledBlocks", disabledList);

        return tag;
    }

    public static AmbienceSeasonColors fromTag(NbtCompound tag) {
        AmbienceSeasonColors colors = new AmbienceSeasonColors();

        colors.saved = tag.getBoolean("saved");

        // A season saved before each flat color had its own on/off toggle used every channel
        // uniformly whenever it was saved at all - treat that the same way (every channel enabled)
        // so an already-saved season keeps looking exactly the same instead of silently going
        // blank the moment this update installs. Only applies when NONE of the six toggles exist
        // yet in the tag (i.e. this really is pre-toggle data, not just one channel the user
        // genuinely left off under the new format).
        boolean legacyAllOn = colors.saved && !tag.contains("overworldSkyEnabled") && !tag.contains("netherSkyEnabled")
            && !tag.contains("endSkyEnabled") && !tag.contains("cloudEnabled") && !tag.contains("lightningEnabled") && !tag.contains("lavaEnabled");

        colors.overworldSkyEnabled = legacyAllOn || tag.getBoolean("overworldSkyEnabled");
        colors.netherSkyEnabled = legacyAllOn || tag.getBoolean("netherSkyEnabled");
        colors.endSkyEnabled = legacyAllOn || tag.getBoolean("endSkyEnabled");
        colors.cloudEnabled = legacyAllOn || tag.getBoolean("cloudEnabled");
        colors.lightningEnabled = legacyAllOn || tag.getBoolean("lightningEnabled");
        colors.lavaEnabled = legacyAllOn || tag.getBoolean("lavaEnabled");

        if (tag.contains("overworldSky")) colors.overworldSky.fromTag(tag.getCompound("overworldSky"));
        if (tag.contains("netherSky")) colors.netherSky.fromTag(tag.getCompound("netherSky"));
        if (tag.contains("endSky")) colors.endSky.fromTag(tag.getCompound("endSky"));
        if (tag.contains("cloud")) colors.cloud.fromTag(tag.getCompound("cloud"));
        if (tag.contains("lightning")) colors.lightning.fromTag(tag.getCompound("lightning"));
        if (tag.contains("lava")) colors.lava.fromTag(tag.getCompound("lava"));

        if (tag.contains("blockColors")) colors.blocks = BlockColorMapNbt.fromTag(tag.getCompound("blockColors"));

        for (NbtElement e : tag.getList("disabledBlocks", NbtElement.STRING_TYPE)) {
            Block block = Registries.BLOCK.get(net.minecraft.util.Identifier.of(e.asString()));
            if (block != null) colors.disabledBlocks.add(block);
        }

        // Migrates a season saved before grass/foliage/water had their own per-block map (a plain
        // flat "grass"/"foliage"/"water" SettingColor each) into the new shape - as a single entry
        // under the matching "Override Grass/Foliage/Water Color" sentinel, so an already-saved
        // season keeps looking exactly the same instead of silently losing its grass/foliage/water
        // color the moment this update installs. Only runs when the new map doesn't already have
        // that sentinel (so it can't ever clobber something saved under the new format).
        migrateLegacyFlatColor(tag, "grass", colors, Ambience.OVERRIDE_GRASS);
        migrateLegacyFlatColor(tag, "foliage", colors, Ambience.OVERRIDE_FOLIAGE);
        migrateLegacyFlatColor(tag, "water", colors, Ambience.OVERRIDE_WATER);

        return colors;
    }

    private static void migrateLegacyFlatColor(NbtCompound tag, String legacyKey, AmbienceSeasonColors colors, Block sentinel) {
        if (!tag.contains(legacyKey)) return;
        if (colors.blocks.containsKey(sentinel)) return;

        SettingColor legacy = new SettingColor().fromTag(tag.getCompound(legacyKey));
        List<WeightedColorEntry> list = new ArrayList<>();
        list.add(new WeightedColorEntry(legacy, 100));
        colors.blocks.put(sentinel, list);
    }
}
