/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.world;

import meteordevelopment.meteorclient.settings.AmbienceRegion;
import meteordevelopment.meteorclient.settings.AmbienceRegionListSetting;
import meteordevelopment.meteorclient.settings.BiomeBlockColorSetting;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ButtonSetting;
import meteordevelopment.meteorclient.gui.screens.settings.RegionListScreen;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringListSetting;
import meteordevelopment.meteorclient.settings.WeightedColorEntry;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BiomeTintedBlocks;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.render.DimensionEffects;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockRenderView;
import net.minecraft.world.WorldView;
import net.minecraft.world.biome.Biome;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * @author Walaryne
 */
public class Ambience extends Module {
    private final SettingGroup sgSky = settings.createGroup("Sky");
    private final SettingGroup sgWorld = settings.createGroup("World");
    private final SettingGroup sgBiome = settings.createGroup("Per-Biome Colors");
    private final SettingGroup sgRegions = settings.createGroup("Regions");

    // Sky

    public final Setting<Boolean> endSky = sgSky.add(new BoolSetting.Builder()
        .name("end-sky")
        .description("Makes the sky like the end.")
        .defaultValue(false)
        .build()
    );

    public final Setting<Boolean> customSkyColor = sgSky.add(new BoolSetting.Builder()
        .name("custom-sky-color")
        .description("Whether the sky color should be changed.")
        .defaultValue(false)
        .build()
    );

    public final Setting<SettingColor> overworldSkyColor = sgSky.add(new ColorSetting.Builder()
        .name("overworld-sky-color")
        .description("The color of the overworld sky.")
        .defaultValue(new SettingColor(11, 41, 72))
        .visible(customSkyColor::get)
        .build()
    );

    public final Setting<SettingColor> netherSkyColor = sgSky.add(new ColorSetting.Builder()
        .name("nether-sky-color")
        .description("The color of the nether sky.")
        .defaultValue(new SettingColor(102, 0, 0))
        .visible(customSkyColor::get)
        .build()
    );

    public final Setting<SettingColor> endSkyColor = sgSky.add(new ColorSetting.Builder()
        .name("end-sky-color")
        .description("The color of the end sky.")
        .defaultValue(new SettingColor(65, 30, 90))
        .visible(customSkyColor::get)
        .build()
    );

    public final Setting<Boolean> customCloudColor = sgSky.add(new BoolSetting.Builder()
        .name("custom-cloud-color")
        .description("Whether the clouds color should be changed.")
        .defaultValue(false)
        .build()
    );

    public final Setting<SettingColor> cloudColor = sgSky.add(new ColorSetting.Builder()
        .name("cloud-color")
        .description("The color of the clouds.")
        .defaultValue(new SettingColor(102, 0, 0))
        .visible(customCloudColor::get)
        .build()
    );

    public final Setting<Boolean> changeLightningColor = sgSky.add(new BoolSetting.Builder()
        .name("custom-lightning-color")
        .description("Whether the lightning color should be changed.")
        .defaultValue(true)
        .build()
    );

    public final Setting<SettingColor> lightningColor = sgSky.add(new ColorSetting.Builder()
        .name("lightning-color")
        .description("The color of the lightning.")
        .defaultValue(new SettingColor(241, 0, 255))
        .visible(changeLightningColor::get)
        .build()
    );

    // World
    public final Setting<Boolean> customGrassColor = sgWorld.add(new BoolSetting.Builder()
        .name("custom-grass-color")
        .description("Overrides the grass color everywhere, ignoring biome and the per-biome colors below.")
        .defaultValue(false)
        .onChanged(val -> reload())
        .build()
    );

    public final Setting<SettingColor> grassColor = sgWorld.add(new ColorSetting.Builder()
        .name("grass-color")
        .description("The color of the grass.")
        .defaultValue(new SettingColor(102, 0, 0))
        .visible(customGrassColor::get)
        .onChanged(val -> reload())
        .build()
    );

    public final Setting<Boolean> customFoliageColor = sgWorld.add(new BoolSetting.Builder()
        .name("custom-foliage-color")
        .description("Overrides the foliage color everywhere, ignoring biome and the per-biome colors below.")
        .defaultValue(false)
        .onChanged(val -> reload())
        .build()
    );

    public final Setting<SettingColor> foliageColor = sgWorld.add(new ColorSetting.Builder()
        .name("foliage-color")
        .description("The color of the foliage.")
        .defaultValue(new SettingColor(102, 0, 0))
        .visible(customFoliageColor::get)
        .onChanged(val -> reload())
        .build()
    );

    public final Setting<Boolean> customWaterColor = sgWorld.add(new BoolSetting.Builder()
        .name("custom-water-color")
        .description("Overrides the water color everywhere, ignoring biome and the per-biome colors below.")
        .defaultValue(false)
        .onChanged(val -> reload())
        .build()
    );

    public final Setting<SettingColor> waterColor = sgWorld.add(new ColorSetting.Builder()
        .name("water-color")
        .description("The color of the water.")
        .defaultValue(new SettingColor(102, 0, 0))
        .visible(customWaterColor::get)
        .onChanged(val -> reload())
        .build()
    );

    public final Setting<Boolean> customLavaColor = sgWorld.add(new BoolSetting.Builder()
        .name("custom-lava-color")
        .description("Whether the lava color should be changed.")
        .defaultValue(false)
        .onChanged(val -> reload())
        .build()
    );

    public final Setting<SettingColor> lavaColor = sgWorld.add(new ColorSetting.Builder()
        .name("lava-color")
        .description("The color of the lava.")
        .defaultValue(new SettingColor(102, 0, 0))
        .visible(customLavaColor::get)
        .onChanged(val -> reload())
        .build()
    );

    // Per-biome, per-block colors

    public final Setting<Map<RegistryKey<Biome>, Map<Block, List<WeightedColorEntry>>>> biomeBlockColors = sgBiome.add(new BiomeBlockColorSetting.Builder()
        .name("entries")
        .description("Pick a biome, then pick blocks within it and give each a color. Give a block multiple colors to pick between them at random, weighted by percentage. Works for any grass, foliage or water block.")
        .onChanged(val -> reload())
        .build()
    );

    public final Setting<Boolean> legacyBiomesOnly = sgBiome.add(new BoolSetting.Builder()
        .name("legacy-biomes-only")
        .description("Hides biomes added after 1.8.9 from the list when adding a new entry above.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> allowUntintedBlocks = sgBiome.add(new BoolSetting.Builder()
        .name("allow-untinted-blocks")
        .description("Also allows blocks that don't naturally support biome tinting (like stone or planks) to be picked and recolored. Experimental - their textures weren't designed for tinting, so results may look off.")
        .defaultValue(false)
        .onChanged(val -> reload())
        .build()
    );

    public final Setting<Boolean> legacyBlocksOnly = sgBiome.add(new BoolSetting.Builder()
        .name("legacy-blocks-only")
        .description("Hides blocks added after 1.8.9 from the list when allow-untinted-blocks is on. Has no effect otherwise, since the normal tintable-block list is already small.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> smoothBiomeTransitions = sgBiome.add(new BoolSetting.Builder()
        .name("smooth-biome-transitions")
        .description("Blends the edge between a configured biome and an unconfigured neighbour from both sides, not just the configured one. Slightly more expensive, since every tinted block then has to check its neighbours for a color, not just the ones you've configured.")
        .defaultValue(false)
        .onChanged(val -> reload())
        .build()
    );

    // Hidden persisted state - toggled via checkboxes in the biome/block list screens rather than
    // shown as a raw setting, so a biome/block can be switched off without losing its colors.
    public final Setting<List<String>> disabledBiomes = sgBiome.add(new StringListSetting.Builder()
        .name("disabled-biomes")
        .description("Internal - which biomes are toggled off.")
        .visible(() -> false)
        .build()
    );

    public final Setting<List<String>> disabledBiomeBlocks = sgBiome.add(new StringListSetting.Builder()
        .name("disabled-biome-blocks")
        .description("Internal - which (biome, block) pairs are toggled off.")
        .visible(() -> false)
        .build()
    );

    public final Setting<Boolean> regionsEnabled = sgRegions.add(new BoolSetting.Builder()
        .name("regions-enabled")
        .description("Master switch for all regions. Individual regions can also be toggled off on their own without losing them - see Manage Regions below.")
        .defaultValue(true)
        .onChanged(val -> reload())
        .build()
    );

    public final Setting<List<AmbienceRegion>> regions = sgRegions.add(new AmbienceRegionListSetting.Builder()
        .name("regions")
        .description("Manually placed area overrides, higher priority than per-biome colors. Use these to recolor a specific spot, or a whole Y range (e.g. so a cave reads differently than the biome on the surface above it) regardless of biome.")
        .onChanged(val -> reload())
        .build()
    );

    public final Setting<Void> manageRegions = sgRegions.add(new ButtonSetting.Builder()
        .name("manage-regions")
        .description("Add, edit or remove regions.")
        .buttonText("Manage Regions")
        .screen(theme -> new RegionListScreen(theme, regions))
        .build()
    );

    public Ambience() {
        super(Categories.World, "ambience", "Change the color of various pieces of the environment.");
    }

    @Override
    public void onActivate() {
        reload();
    }

    @Override
    public void onDeactivate() {
        reload();
    }

    private void reload() {
        if (mc.worldRenderer != null && isActive()) mc.worldRenderer.reload();
    }

    public static class Custom extends DimensionEffects {
        public Custom() {
            super(Float.NaN, true, DimensionEffects.SkyType.END, true, false);
        }

        @Override
        public Vec3d adjustFogColor(Vec3d color, float sunHeight) {
            return color.multiply(0.15000000596046448D);
        }

        @Override
        public boolean useThickFog(int camX, int camY) {
            return false;
        }

        @Override
        public float[] getFogColorOverride(float skyAngle, float tickDelta) {
            return null;
        }
    }

    public SettingColor getBiomeBlockColor(Map<RegistryKey<Biome>, Map<Block, List<WeightedColorEntry>>> map, BlockRenderView world, BlockPos pos) {
        if (map.isEmpty() || !(world instanceof WorldView worldView)) return null;

        return getBiomeBlockColor(map, worldView.getBiome(pos), world.getBlockState(pos).getBlock(), pos);
    }

    // Sentinel keys used within a biome's block map to mean "override every block in this biome"
    // or "override every grass/foliage/water block in this biome", instead of a specific block.
    // These blocks are never real grass/foliage/water blocks, so they're safe to reuse as markers
    // without needing a whole new data structure.
    public static final Block OVERRIDE_ALL = Blocks.AIR;
    public static final Block OVERRIDE_GRASS = Blocks.CAVE_AIR;
    public static final Block OVERRIDE_FOLIAGE = Blocks.VOID_AIR;
    public static final Block OVERRIDE_WATER = Blocks.BARRIER;

    private static Block categoryOverrideFor(Block block) {
        if (BiomeTintedBlocks.GRASS.contains(block)) return OVERRIDE_GRASS;
        if (BiomeTintedBlocks.FOLIAGE.contains(block)) return OVERRIDE_FOLIAGE;
        if (BiomeTintedBlocks.WATER.contains(block)) return OVERRIDE_WATER;

        return null;
    }

    // Looks up a color list within a single biome/region's block map. Priority: "override entire
    // biome/region", then the specific block's own entry, then the grass/foliage/water category
    // base (a fallback color for that category that individual block entries can override). Any
    // key that "enabled" rejects is skipped, as if it wasn't configured at all.
    private List<WeightedColorEntry> lookupColors(Map<Block, List<WeightedColorEntry>> blocks, Block block, Predicate<Block> enabled) {
        if (blocks == null || blocks.isEmpty()) return null;

        List<WeightedColorEntry> colors = enabled.test(OVERRIDE_ALL) ? blocks.get(OVERRIDE_ALL) : null;
        if ((colors == null || colors.isEmpty()) && enabled.test(block)) colors = blocks.get(block);

        if (colors == null || colors.isEmpty()) {
            Block category = categoryOverrideFor(block);
            if (category != null && enabled.test(category)) colors = blocks.get(category);
        }

        return (colors == null || colors.isEmpty()) ? null : colors;
    }

    public List<WeightedColorEntry> getBiomeBlockColors(Map<RegistryKey<Biome>, Map<Block, List<WeightedColorEntry>>> map, RegistryEntry<Biome> biomeEntry, Block block) {
        if (map.isEmpty() || biomeEntry == null) return null;

        Optional<RegistryKey<Biome>> biomeKeyOpt = biomeEntry.getKey();
        if (biomeKeyOpt.isEmpty()) return null;

        RegistryKey<Biome> biomeKey = biomeKeyOpt.get();
        if (!isBiomeEnabled(biomeKey)) return null;

        return lookupColors(map.get(biomeKey), block, key -> isBlockEnabled(biomeKey, key));
    }

    // Manually placed area overrides - checked before per-biome colors. First matching region wins.
    public List<WeightedColorEntry> getRegionBlockColors(String dimensionId, BlockPos pos, Block block, RegistryEntry<Biome> biomeEntry) {
        if (!regionsEnabled.get()) return null;

        List<AmbienceRegion> list = regions.get();
        if (list.isEmpty()) return null;

        String biomeId = biomeId(biomeEntry);

        for (AmbienceRegion region : list) {
            if (!region.contains(dimensionId, pos, biomeId)) continue;

            List<WeightedColorEntry> colors = lookupColors(region.blocks, block, key -> !region.disabledBlocks.contains(key));
            if (colors != null) return colors;
        }

        return null;
    }

    private static String biomeId(RegistryEntry<Biome> biomeEntry) {
        if (biomeEntry == null) return "";

        Optional<RegistryKey<Biome>> key = biomeEntry.getKey();
        return key.map(registryKey -> registryKey.getValue().toString()).orElse("");
    }

    public boolean isBiomeEnabled(RegistryKey<Biome> biome) {
        return !disabledBiomes.get().contains(biome.getValue().toString());
    }

    public void setBiomeEnabled(RegistryKey<Biome> biome, boolean enabled) {
        String id = biome.getValue().toString();
        if (enabled) disabledBiomes.get().remove(id);
        else if (!disabledBiomes.get().contains(id)) disabledBiomes.get().add(id);

        disabledBiomes.onChanged();
        reload();
    }

    private static String blockKey(RegistryKey<Biome> biome, Block block) {
        return biome.getValue() + "|" + Registries.BLOCK.getId(block);
    }

    public boolean isBlockEnabled(RegistryKey<Biome> biome, Block block) {
        return !disabledBiomeBlocks.get().contains(blockKey(biome, block));
    }

    public void setBlockEnabled(RegistryKey<Biome> biome, Block block, boolean enabled) {
        String key = blockKey(biome, block);
        if (enabled) disabledBiomeBlocks.get().remove(key);
        else if (!disabledBiomeBlocks.get().contains(key)) disabledBiomeBlocks.get().add(key);

        disabledBiomeBlocks.onChanged();
        reload();
    }

    // Overload that doesn't need a full WorldView - used by the non-Sodium vanilla color hooks, which
    // can only ever do a single flat pick (no true neighbour blending), so the per-entry blend flag
    // doesn't apply there.
    public SettingColor getBiomeBlockColor(Map<RegistryKey<Biome>, Map<Block, List<WeightedColorEntry>>> map, RegistryEntry<Biome> biomeEntry, Block block, BlockPos pos) {
        List<WeightedColorEntry> colors = getBiomeBlockColors(map, biomeEntry, block);
        if (colors == null) return null;

        return pickWeightedEntry(colors, pos).color;
    }

    public WeightedColorEntry pickWeightedEntry(List<WeightedColorEntry> colors, BlockPos pos) {
        if (colors.size() == 1) return colors.get(0);

        int total = 0;
        for (WeightedColorEntry entry : colors) total += Math.max(entry.weight, 0);
        if (total <= 0) return colors.get(0);

        long seed = pos.asLong();
        seed ^= (seed >>> 33);
        seed *= 0xff51afd7ed558ccdL;
        seed ^= (seed >>> 33);

        int roll = (int) Math.floorMod(seed, (long) total);

        int accumulated = 0;
        for (WeightedColorEntry entry : colors) {
            accumulated += Math.max(entry.weight, 0);
            if (roll < accumulated) return entry;
        }

        return colors.get(colors.size() - 1);
    }

    // Vertex color alpha is invisible for opaque block quads (no blending happens in that render
    // pass), so instead of trusting alpha to the GPU, this repurposes it as a software mix ratio
    // between the configured color (alpha=255) and whatever the block would normally look like
    // (alpha=0) - always packed back out fully opaque so it renders correctly either way.
    public static int mixTint(SettingColor color, int basePacked) {
        float factor = color.a / 255f;
        if (factor >= 1f) return 0xFF000000 | (color.r << 16) | (color.g << 8) | color.b;
        if (factor <= 0f) return 0xFF000000 | (basePacked & 0x00FFFFFF);

        int baseR = (basePacked >> 16) & 0xFF;
        int baseG = (basePacked >> 8) & 0xFF;
        int baseB = basePacked & 0xFF;

        int r = Math.round(baseR + (color.r - baseR) * factor);
        int g = Math.round(baseG + (color.g - baseG) * factor);
        int b = Math.round(baseB + (color.b - baseB) * factor);

        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    public SettingColor skyColor() {
        switch (PlayerUtils.getDimension()) {
            case Overworld -> {
                return overworldSkyColor.get();
            }
            case Nether -> {
                return netherSkyColor.get();
            }
            case End -> {
                return endSkyColor.get();
            }
        }

        return null;
    }
}
