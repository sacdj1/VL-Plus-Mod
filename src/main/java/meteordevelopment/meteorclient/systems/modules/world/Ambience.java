/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.world;

import meteordevelopment.meteorclient.settings.AmbiencePreset;
import meteordevelopment.meteorclient.settings.AmbiencePresetListSetting;
import meteordevelopment.meteorclient.settings.AmbienceSeasonColors;
import meteordevelopment.meteorclient.settings.AmbienceSeasonColorsSetting;
import meteordevelopment.meteorclient.settings.AmbienceRegion;
import meteordevelopment.meteorclient.settings.AmbienceRegionListSetting;
import meteordevelopment.meteorclient.settings.BiomeBlockColorSetting;
import meteordevelopment.meteorclient.settings.BlockColorMapSetting;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ButtonSetting;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.meteor.KeyEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.screens.settings.BlockColorMapScreen;
import meteordevelopment.meteorclient.gui.screens.settings.PresetListScreen;
import meteordevelopment.meteorclient.gui.screens.settings.SeasonPresetsScreen;
import meteordevelopment.meteorclient.gui.screens.settings.RegionListScreen;
import meteordevelopment.meteorclient.renderer.Renderer3D;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.KeybindSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringListSetting;
import meteordevelopment.meteorclient.settings.WeightedColorEntry;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.meteorclient.utils.misc.input.KeyAction;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BiomeTintedBlocks;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.render.DimensionEffects;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockRenderView;
import net.minecraft.world.WorldView;
import net.minecraft.world.biome.Biome;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.function.Predicate;

/**
 * @author Walaryne
 */
public class Ambience extends Module {
    // Bobby keeps client-side chunks around (cached to disk) well past the server's own view
    // distance, instead of unloading them the moment you walk away - a biome-restricted region's
    // boundary scan is only as accurate as the chunk data actually backing it, so with Bobby
    // installed it's reasonable to let Visualize Range go further out than it'd otherwise be safe
    // to promise, since the chunks it needs are far more likely to actually be there.
    private static final boolean BOBBY_LOADED = net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("bobby");

    private final SettingGroup sgSky = settings.createGroup("Sky");
    private final SettingGroup sgWorld = settings.createGroup("World");
    private final SettingGroup sgBiome = settings.createGroup("Per-Biome Colors");
    private final SettingGroup sgRegions = settings.createGroup("Regions");
    private final SettingGroup sgBlocks = settings.createGroup("Global Blocks");
    private final SettingGroup sgPresets = settings.createGroup("Presets");

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
        .defaultValue(new SettingColor(94, 71, 124))
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
        .defaultValue(new SettingColor(124, 217, 123))
        .visible(customCloudColor::get)
        .build()
    );

    public final Setting<Boolean> changeLightningColor = sgSky.add(new BoolSetting.Builder()
        .name("custom-lightning-color")
        .description("Whether the lightning color should be changed.")
        .defaultValue(false)
        .build()
    );

    public final Setting<SettingColor> lightningColor = sgSky.add(new ColorSetting.Builder()
        .name("lightning-color")
        .description("The color of the lightning.")
        .defaultValue(new SettingColor(255, 216, 52))
        .visible(changeLightningColor::get)
        .build()
    );

    // World
    public final Setting<Boolean> customGrassColor = sgWorld.add(new BoolSetting.Builder()
        .name("custom-grass-color")
        .description("Overrides the grass color everywhere, ignoring biome and the per-biome colors below.")
        .defaultValue(true)
        .onChanged(val -> reload())
        .build()
    );

    public final Setting<SettingColor> grassColor = sgWorld.add(new ColorSetting.Builder()
        .name("grass-color")
        .description("The color of the grass.")
        .defaultValue(new SettingColor(255, 148, 0))
        .visible(customGrassColor::get)
        .onChanged(val -> reload())
        .build()
    );

    public final Setting<Boolean> customFoliageColor = sgWorld.add(new BoolSetting.Builder()
        .name("custom-foliage-color")
        .description("Overrides the foliage color everywhere, ignoring biome and the per-biome colors below.")
        .defaultValue(true)
        .onChanged(val -> reload())
        .build()
    );

    public final Setting<SettingColor> foliageColor = sgWorld.add(new ColorSetting.Builder()
        .name("foliage-color")
        .description("The color of the foliage.")
        .defaultValue(new SettingColor(255, 101, 0))
        .visible(customFoliageColor::get)
        .onChanged(val -> reload())
        .build()
    );

    public final Setting<Boolean> customWaterColor = sgWorld.add(new BoolSetting.Builder()
        .name("custom-water-color")
        .description("Overrides the water color everywhere, ignoring biome and the per-biome colors below.")
        .defaultValue(true)
        .onChanged(val -> reload())
        .build()
    );

    public final Setting<SettingColor> waterColor = sgWorld.add(new ColorSetting.Builder()
        .name("water-color")
        .description("The color of the water.")
        .defaultValue(new SettingColor(88, 192, 201))
        .visible(customWaterColor::get)
        .onChanged(val -> reload())
        .build()
    );

    public final Setting<Boolean> customLavaColor = sgWorld.add(new BoolSetting.Builder()
        .name("custom-lava-color")
        .description("Whether the lava color should be changed.")
        .defaultValue(true)
        .onChanged(val -> reload())
        .build()
    );

    public final Setting<SettingColor> lavaColor = sgWorld.add(new ColorSetting.Builder()
        .name("lava-color")
        .description("The color of the lava.")
        .defaultValue(new SettingColor(30, 180, 30))
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

    public final Setting<Boolean> visualizeRegions = sgRegions.add(new BoolSetting.Builder()
        .name("visualize-regions")
        .description("Draws an outline around every enabled region in your current dimension - a box for box mode, a sphere/cylinder for point mode. A placement aid while you're setting regions up, not a gameplay feature - works independently of the Regions master switch above.")
        .defaultValue(false)
        .build()
    );

    public final Setting<Keybind> visualizeRegionsKey = sgRegions.add(new KeybindSetting.Builder()
        .name("visualize-regions-key")
        .description("Key to flip Visualize Regions above on/off, without having to open the settings to do it.")
        .defaultValue(Keybind.none())
        .build()
    );

    public final Setting<ShapeMode> visualizeShapeMode = sgRegions.add(new EnumSetting.Builder<ShapeMode>()
        .name("visualize-shape-mode")
        .description("How the region outlines are drawn.")
        .defaultValue(ShapeMode.Lines)
        .visible(visualizeRegions::get)
        .build()
    );

    public final Setting<Boolean> visualizeColorPerRegion = sgRegions.add(new BoolSetting.Builder()
        .name("visualize-color-per-region")
        .description("On (default): each region gets its own distinct color (cycling through well-spread hues, by position in your region list) instead of every region using the same Line/Side Color below - so overlapping or nearby regions are easy to tell apart. Off: Line/Side Color below applies to every region the same.")
        .defaultValue(true)
        .visible(visualizeRegions::get)
        .build()
    );

    public final Setting<SettingColor> visualizeLineColor = sgRegions.add(new ColorSetting.Builder()
        .name("visualize-line-color")
        .description("The region outline color - its own alpha still applies per region when Color Per Region above is on, just not its hue.")
        .defaultValue(new SettingColor(0, 255, 255, 255))
        .visible(visualizeRegions::get)
        .build()
    );

    public final Setting<SettingColor> visualizeSideColor = sgRegions.add(new ColorSetting.Builder()
        .name("visualize-side-color")
        .description("The region fill color - its own alpha still applies per region when Color Per Region above is on, just not its hue.")
        .defaultValue(new SettingColor(0, 255, 255, 25))
        .visible(visualizeRegions::get)
        .build()
    );

    public final Setting<Boolean> visualizeOccluded = sgRegions.add(new BoolSetting.Builder()
        .name("visualize-occluded")
        .description("Off (default) draws through walls/terrain like a normal ESP, so you can always see the outline regardless of what's in front of it. On hides it behind blocks like a real object, so it only shows where you'd actually be able to see it.")
        .defaultValue(false)
        .visible(visualizeRegions::get)
        .build()
    );

    public enum BoundaryMode {
        /** Just the smooth sphere/cylinder approximation above - no real per-block check. */
        Off,
        /** A flat 2D cross-section at your current Y, redrawn as you move up/down - the real boundary, connected into one continuous outline (and optionally a filled floor) instead of the whole 3D shape. Cheap, and the clearest for ground-level placement. */
        Plane,
        /** The real 3D voxel shell around the whole region at once - more useful from a distance/while flying, but checks (and can draw) far more cells than Plane. */
        Full3D,
        /** Several Plane-style 2D cross-sections stacked at fixed Y intervals through the whole region at once, like a topographic map - gives a real sense of the 3D shape for far fewer elements than Full3D, and still reads fine on curved shapes since each ring is just a flat silhouette. */
        Contour,
        /** Sparse sample points scattered across the real boundary surface, not a continuous mesh at all - the cheapest option by far, for "huge region, just give me a rough sense of its shape" rather than a precise outline. */
        PointCloud,
        /** No scanning at all - just raycasts along where you're actually looking and marks the exact block where your look direction crosses the region's real boundary. A precision tool for checking one exact spot, not an overview. */
        Probe
    }

    public final Setting<BoundaryMode> visualizeBoundaryMode = sgRegions.add(new EnumSetting.Builder<BoundaryMode>()
        .name("visualize-boundary-mode")
        .description("Point mode only: shows the real, blocky edge between affected and unaffected blocks - the smooth sphere/cylinder outline above is only an approximation of this actual shape. See each option's own tooltip.")
        .defaultValue(BoundaryMode.Off)
        .visible(visualizeRegions::get)
        .build()
    );

    // Two different, independent ways to cut down what gets checked/drawn - not the same thing,
    // despite both being about "how much detail far away": LOD trades detail for distance (still
    // renders everything, just blockier past a certain range); FOV Culling trades coverage for
    // direction (full detail, just skips whatever's outside your current view entirely, regardless
    // of distance). Either, both, or neither can be on at once.

    public final Setting<Boolean> visualizeLod = sgRegions.add(new BoolSetting.Builder()
        .name("visualize-lod")
        .description("Full 3D only. Level of detail by distance. Off (default): the boundary is checked/drawn at full, single-block resolution everywhere. On: parts of it far from you are grouped into bigger cells instead (blockier, but far fewer blocks to check and faces to draw) - detail only drops off with distance, it's never missing up close.")
        .defaultValue(false)
        .visible(() -> visualizeRegions.get() && visualizeBoundaryMode.get() == BoundaryMode.Full3D)
        .build()
    );

    public final Setting<Boolean> visualizeFovCulling = sgRegions.add(new BoolSetting.Builder()
        .name("visualize-fov-culling")
        .description("Foveated rendering. Off (default): the boundary draws everywhere within range regardless of where you're looking. On: only the part currently within your field of view gets drawn at all - nothing behind you or off to the side, regardless of how close it is.")
        .defaultValue(false)
        .visible(() -> visualizeRegions.get() && visualizeBoundaryMode.get() != BoundaryMode.Off)
        .build()
    );

    public final Setting<Boolean> visualizeChunkedScan = sgRegions.add(new BoolSetting.Builder()
        .name("visualize-chunked-scan")
        .description("Full 3D only. On (default): a rescan spreads its work across several frames instead of all at once, so moving/turning never causes a single hitch - the tradeoff is it takes a little while (usually under a second) to reach full detail each time it rescans, rather than appearing complete instantly. Off: the old behavior - a rescan finishes immediately in one frame, at the cost of a bigger (but shorter) stall right when it happens.")
        .defaultValue(true)
        .visible(() -> visualizeRegions.get() && visualizeBoundaryMode.get() == BoundaryMode.Full3D)
        .build()
    );

    public final Setting<Integer> visualizeChunkRange = sgRegions.add(new IntSetting.Builder()
        .name("visualize-chunk-range")
        .description("Full 3D + Chunked Scan only - replaces Visualize Range above. How far from you to check/outline the real boundary, measured in chunks (16 blocks each) instead of raw blocks, since that's the unit the scan itself is actually working in while this is on.")
        .defaultValue(1)
        .range(1, BOBBY_LOADED ? 24 : 8)
        .sliderMax(BOBBY_LOADED ? 24 : 8)
        .visible(() -> visualizeRegions.get() && visualizeBoundaryMode.get() == BoundaryMode.Full3D && visualizeChunkedScan.get())
        .build()
    );

    public final Setting<Integer> visualizeShowBlocksRange = sgRegions.add(new IntSetting.Builder()
        .name("visualize-show-blocks-range")
        .description("How far from you (in blocks) to check/outline the real boundary. Not used by Probe mode (its own separate distance below) or by Full 3D while Visualize Chunked Scan is on (Visualize Chunk Range above, measured in chunks instead)." +
            (BOBBY_LOADED ? " Bobby detected - capped higher than usual (384 instead of 128), since its cached chunks make the extra range actually reliable." : ""))
        .defaultValue(16)
        .range(4, BOBBY_LOADED ? 384 : 128)
        .sliderMax(BOBBY_LOADED ? 384 : 128)
        .visible(() -> visualizeRegions.get() && visualizeBoundaryMode.get() != BoundaryMode.Off && visualizeBoundaryMode.get() != BoundaryMode.Probe
            && !(visualizeBoundaryMode.get() == BoundaryMode.Full3D && visualizeChunkedScan.get()))
        .build()
    );

    public final Setting<Integer> visualizeContourSpacing = sgRegions.add(new IntSetting.Builder()
        .name("visualize-contour-spacing")
        .description("Contour mode only: vertical gap (in blocks) between each stacked cross-section ring.")
        .defaultValue(8)
        .min(2)
        .sliderRange(2, 32)
        .visible(() -> visualizeRegions.get() && visualizeBoundaryMode.get() == BoundaryMode.Contour)
        .build()
    );

    public final Setting<Integer> visualizePointCloudCount = sgRegions.add(new IntSetting.Builder()
        .name("visualize-point-cloud-count")
        .description("Point Cloud mode only: how many sample points to scatter across the region's surface. More looks more solid but costs more to draw.")
        .defaultValue(200)
        .min(8)
        .sliderRange(8, 2000)
        .visible(() -> visualizeRegions.get() && visualizeBoundaryMode.get() == BoundaryMode.PointCloud)
        .build()
    );

    public final Setting<Integer> visualizeProbeMaxDistance = sgRegions.add(new IntSetting.Builder()
        .name("visualize-probe-max-distance")
        .description("Probe mode only: how far along your look direction to search for a boundary crossing.")
        .defaultValue(64)
        .min(8)
        .sliderRange(8, 256)
        .visible(() -> visualizeRegions.get() && visualizeBoundaryMode.get() == BoundaryMode.Probe)
        .build()
    );

    // Global block overrides - not tied to any biome/region, apply everywhere a matching block
    // shows up. Lowest priority of the three: a Region or Per-Biome entry for the same block wins.

    public final Setting<Map<Block, List<WeightedColorEntry>>> globalBlockColors = sgBlocks.add(new BlockColorMapSetting.Builder()
        .name("entries")
        .description("Pick blocks and give each a color, applied everywhere regardless of biome or region. Give a block multiple colors to pick between them at random, weighted by percentage. Lower priority than Regions and Per-Biome Colors above.")
        .onChanged(val -> reload())
        .build()
    );

    // Hidden persisted state - toggled via checkboxes in the block list screen rather than shown
    // as a raw setting, so a block can be switched off without losing its colors.
    public final Setting<List<String>> disabledGlobalBlocks = sgBlocks.add(new StringListSetting.Builder()
        .name("disabled-global-blocks")
        .description("Internal - which globally-overridden blocks are toggled off.")
        .visible(() -> false)
        .build()
    );

    public final Setting<Void> manageGlobalBlocks = sgBlocks.add(new ButtonSetting.Builder()
        .name("manage-global-blocks")
        .description("Add, edit or remove globally overridden blocks.")
        .buttonText("Manage Blocks")
        .screen(theme -> new BlockColorMapScreen(theme, "Global", "Global", globalBlockColors.get(), this::reload,
            this::isGlobalBlockEnabled,
            (block, enabled) -> setGlobalBlockEnabled(block, enabled)))
        .build()
    );

    // Presets - named snapshots of every override above (sky/world colors, per-biome colors,
    // regions, global blocks), so a whole look can be saved and switched back to later - e.g. one
    // preset per season.

    public final Setting<List<AmbiencePreset>> presets = sgPresets.add(new AmbiencePresetListSetting.Builder()
        .name("presets")
        .description("Saved snapshots of every override above - see Manage Presets below.")
        .build()
    );

    public final Setting<Void> managePresets = sgPresets.add(new ButtonSetting.Builder()
        .name("manage-presets")
        .description("Save the current overrides as a new preset, or apply/overwrite/delete an existing one.")
        .buttonText("Manage Presets")
        .screen(theme -> new PresetListScreen(theme, this))
        .build()
    );

    public final Setting<Boolean> autoSeasonEnabled = sgPresets.add(new BoolSetting.Builder()
        .name("auto-season")
        .description("Applies the matching season's own colors below for the real-world calendar date, as an independent layer underneath everything else - a manual override (the Custom X Color toggles, or a configured Region/Biome/Global block) still takes priority over it exactly like it would over plain vanilla. Does nothing for a season whose colors haven't been saved yet (see Season Colors below).")
        .defaultValue(false)
        // Sky/cloud/lightning/lava pick this up live on their own (read fresh every frame), but
        // block colors are baked into Sodium's chunk meshes - without forcing a reload here too,
        // flipping this on mid-session left every already-meshed chunk showing no season color
        // until something else happened to force a re-mesh.
        .onChanged(val -> checkAutoSeason())
        .build()
    );

    public final Setting<Boolean> southernHemisphere = sgPresets.add(new BoolSetting.Builder()
        .name("southern-hemisphere")
        .description("Swaps which months count as which season for Auto Season above (Southern Hemisphere's summer is during the North's winter, and vice versa).")
        .defaultValue(false)
        .visible(autoSeasonEnabled::get)
        .build()
    );

    public final Setting<Boolean> fadeBetweenSeasons = sgPresets.add(new BoolSetting.Builder()
        .name("fade-between-seasons")
        .description("Smoothly blends the sky/cloud/lightning/lava colors toward the upcoming season over the last Fade Days below, instead of switching all at once on the boundary date. Block colors (grass/foliage/water/individual blocks) still switch at the exact boundary - blending two completely different sets of configured blocks against each other doesn't have one obviously correct answer.")
        .defaultValue(false)
        .visible(autoSeasonEnabled::get)
        .build()
    );

    public final Setting<Integer> fadeDays = sgPresets.add(new IntSetting.Builder()
        .name("fade-days")
        .description("How many days before a season boundary the blend starts - e.g. 7 starts blending a week out, landing fully on the new season's colors exactly on the day it starts.")
        .defaultValue(7)
        .min(1)
        .sliderRange(1, 30)
        .visible(() -> autoSeasonEnabled.get() && fadeBetweenSeasons.get())
        .build()
    );

    // Season Elements - blanket kill-switches independent of every individual season's own
    // per-dimension/per-channel toggles above. E.g. turning Sky off here means NO season (Spring,
    // Summer, Autumn, Winter, or a preview) ever touches sky color, full stop - without having to
    // go into all four seasons across all three dimensions and turn their own Sky toggle off one
    // by one. A manual override (Custom Sky Color etc.) already took priority over Seasons
    // regardless - this just removes Seasons from the picture entirely for that one element.
    public final Setting<Boolean> seasonAffectSky = sgPresets.add(new BoolSetting.Builder()
        .name("season-affect-sky")
        .description("Lets any season (and Auto Season's fade) change sky color at all. Off = Seasons never touches sky, no matter which season or dimension.")
        .defaultValue(true)
        .visible(autoSeasonEnabled::get)
        .build()
    );

    public final Setting<Boolean> seasonAffectClouds = sgPresets.add(new BoolSetting.Builder()
        .name("season-affect-clouds")
        .description("Lets any season change cloud color at all. Off = Seasons never touches clouds.")
        .defaultValue(true)
        .visible(autoSeasonEnabled::get)
        .build()
    );

    public final Setting<Boolean> seasonAffectLightning = sgPresets.add(new BoolSetting.Builder()
        .name("season-affect-lightning")
        .description("Lets any season change lightning color at all. Off = Seasons never touches lightning.")
        .defaultValue(true)
        .visible(autoSeasonEnabled::get)
        .build()
    );

    public final Setting<Boolean> seasonAffectLava = sgPresets.add(new BoolSetting.Builder()
        .name("season-affect-lava")
        .description("Lets any season change lava color at all. Off = Seasons never touches lava.")
        .defaultValue(true)
        .visible(autoSeasonEnabled::get)
        .build()
    );

    public final Setting<Boolean> seasonAffectBlocks = sgPresets.add(new BoolSetting.Builder()
        .name("season-affect-blocks")
        .description("Lets any season change grass/foliage/water/individual block colors at all. Off = Seasons never touches block colors - you'll need to re-trigger a re-mesh (e.g. toggle Auto Season) to see already-meshed chunks drop their season block colors.")
        .defaultValue(true)
        .visible(autoSeasonEnabled::get)
        .build()
    );

    // Season Colors - four fixed, always-present color slots (one per season), each a standalone
    // set of colors Auto Season reads straight from at render time (see currentSeasonColors()) -
    // deliberately NOT a snapshot of the customGrassColor/grassColor/regions/etc. settings above.
    // Applying a season never writes into those settings, so it can never show up as "on" in the
    // main Sky/World/Regions/Global Blocks sections above, and a manual override there still wins
    // over it exactly as if no season were active at all - the two are independent layers, not one
    // driving the other.
    public final Setting<AmbienceSeasonColors> springColors = sgPresets.add(new AmbienceSeasonColorsSetting.Builder()
        .name("spring-colors")
        .build()
    );

    public final Setting<AmbienceSeasonColors> summerColors = sgPresets.add(new AmbienceSeasonColorsSetting.Builder()
        .name("summer-colors")
        .build()
    );

    public final Setting<AmbienceSeasonColors> autumnColors = sgPresets.add(new AmbienceSeasonColorsSetting.Builder()
        .name("autumn-colors")
        .build()
    );

    public final Setting<AmbienceSeasonColors> winterColors = sgPresets.add(new AmbienceSeasonColorsSetting.Builder()
        .name("winter-colors")
        .build()
    );

    public final Setting<Void> seasonPresets = sgPresets.add(new ButtonSetting.Builder()
        .name("season-colors")
        .description("Save the current look's colors into one of the four season slots Auto Season reads from, or preview one on demand - no typing a name required.")
        .buttonText("Season Colors")
        .screen(theme -> new SeasonPresetsScreen(theme, this))
        .build()
    );

    public Ambience() {
        super(Categories.World, "ambience", "Change the color of various pieces of the environment.");

        // Same two Setting objects as the Per-Biome Colors section above (not copies - toggling
        // either row changes the one underlying value), also shown here since "why can't I pick
        // untintable blocks" kept coming up specifically for Global Block Overrides, and the actual
        // toggle only ever lived in the Per-Biome section with nothing pointing at it from here.
        sgBlocks.add(allowUntintedBlocks);
        sgBlocks.add(legacyBlocksOnly);
    }

    @Override
    public void onActivate() {
        reload();

        migrateLegacySeasonColors();

        lastCheckedEpochDay = -1;
        checkAutoSeason();
    }

    // One-time migration for whoever already had general presets named after a season before the
    // four fixed Season Colors slots existed - scrapes each matching one's colors into its matching
    // slot the first time this activates after updating, by briefly applying the legacy preset
    // through the general Presets mechanism it was actually saved under and then reading the
    // resulting live color values back out (same as a manual apply-then-save-current would do),
    // rather than duplicating the legacy NBT shape by hand. A no-op on every later activation once a
    // slot actually has something saved, so this never overwrites a slot the user has since set up.
    private void migrateLegacySeasonColors() {
        migrateLegacySeasonColors(springColors, "spring");
        migrateLegacySeasonColors(summerColors, "summer");
        migrateLegacySeasonColors(autumnColors, "fall", "autumn");
        migrateLegacySeasonColors(winterColors, "winter");
    }

    private void migrateLegacySeasonColors(Setting<AmbienceSeasonColors> slot, String... nameContains) {
        if (slot.get().saved) return;

        for (AmbiencePreset legacy : presets.get()) {
            String n = legacy.name.toLowerCase();
            boolean matches = false;
            for (String s : nameContains) {
                if (n.contains(s)) {
                    matches = true;
                    break;
                }
            }
            if (!matches) continue;

            applyPreset(legacy);
            captureCurrentIntoSeason(slot.get());
            slot.onChanged();
            return;
        }
    }

    @Override
    public void onDeactivate() {
        reload();
    }

    // Public so a screen editing a nested piece of state the "regions"/"globalBlockColors" Setting
    // objects can't see changing on their own (a block added to one region's own block map, not the
    // regions list itself) can still force the re-mesh that change needs, the same way every setting
    // above already does through its own onChanged(val -> reload()).
    public void reload() {
        if (mc.worldRenderer != null && isActive()) mc.worldRenderer.reload();
    }

    // Presets - every setting a saved preset captures/restores. Order doesn't matter - capture
    // keys each one by its own name, restore looks entries up by that same name.
    private List<Setting<?>> presetSettings() {
        return List.of(
            endSky, customSkyColor, overworldSkyColor, netherSkyColor, endSkyColor,
            customCloudColor, cloudColor, changeLightningColor, lightningColor,
            customGrassColor, grassColor, customFoliageColor, foliageColor, customWaterColor, waterColor, customLavaColor, lavaColor,
            biomeBlockColors, disabledBiomes, disabledBiomeBlocks,
            regionsEnabled, regions,
            globalBlockColors, disabledGlobalBlocks
        );
    }

    /** Writes every preset-eligible setting's current value into tag, keyed by setting name - used both for a brand new preset and to overwrite an existing one with the current look. */
    public void captureInto(NbtCompound tag) {
        for (Setting<?> setting : presetSettings()) tag.put(setting.name, setting.toTag());
    }

    public AmbiencePreset capturePreset(String name) {
        AmbiencePreset preset = new AmbiencePreset();
        preset.name = name;
        captureInto(preset.data);

        return preset;
    }

    public void applyPreset(AmbiencePreset preset) {
        for (Setting<?> setting : presetSettings()) {
            if (!preset.data.contains(setting.name)) continue;
            setting.fromTag(preset.data.getCompound(setting.name));
        }

        reload();
    }

    // Season Colors - an independent layer, not a snapshot of the settings above. "Save Current"
    // just reads the current live color values straight off those settings (regardless of whether
    // their own Custom X Color toggle happens to be on or off right now - a season's own grass
    // color is just a plain color, with no on/off of its own); it never touches the settings
    // themselves, and applying a season later never writes back into them either. Grass/Foliage/
    // Water specifically are written as a single entry under the matching OVERRIDE_* sentinel in
    // the season's own block map, exactly what the season's own block editor screen shows them as -
    // any OTHER entries already in that list (individual blocks the user added by hand) are left
    // untouched.
    public void captureCurrentIntoSeason(AmbienceSeasonColors target) {
        target.overworldSky.set(overworldSkyColor.get());
        target.netherSky.set(netherSkyColor.get());
        target.endSky.set(endSkyColor.get());
        target.cloud.set(cloudColor.get());
        target.lightning.set(lightningColor.get());
        target.lava.set(lavaColor.get());

        // Capturing a value and leaving its toggle off would just silently do nothing - Save
        // Current explicitly captures all six, so all six turn on with it.
        target.overworldSkyEnabled = true;
        target.netherSkyEnabled = true;
        target.endSkyEnabled = true;
        target.cloudEnabled = true;
        target.lightningEnabled = true;
        target.lavaEnabled = true;

        setSeasonSentinelColor(target, OVERRIDE_GRASS, grassColor.get());
        setSeasonSentinelColor(target, OVERRIDE_FOLIAGE, foliageColor.get());
        setSeasonSentinelColor(target, OVERRIDE_WATER, waterColor.get());

        target.saved = true;
    }

    private void setSeasonSentinelColor(AmbienceSeasonColors target, Block sentinel, SettingColor color) {
        List<WeightedColorEntry> list = target.blocks.computeIfAbsent(sentinel, k -> new ArrayList<>());
        if (list.isEmpty()) list.add(new WeightedColorEntry(new SettingColor(color), 100));
        else list.get(0).color.set(color);
    }

    // Set only by the Season Colors screen's own "Preview" button, to force a specific season's
    // look on demand (e.g. checking what Winter looks like in July) regardless of the calendar or
    // even whether Auto Season itself is on - cleared by its own "Stop Previewing" button. Not a
    // Setting - deliberately session-only, never saved, since it's a one-off manual check rather
    // than a real configuration choice.
    //
    // previewBlendTarget/previewBlendFactor let the same screen ALSO preview a fade partway toward
    // a second season (e.g. checking what 50% Autumn -> Winter looks like), without waiting days
    // for the real calendar to get there - the actual Fade Between Seasons feature still only ever
    // runs off the calendar; this is purely a manual "what would that look like" check.
    private AmbienceSeasonColors previewSeason = null;
    private AmbienceSeasonColors previewBlendTarget = null;
    private float previewBlendFactor = 0.5f;

    public void previewSeasonColors(AmbienceSeasonColors season) {
        previewSeason = season;
        previewBlendTarget = null;
        reload();
    }

    public boolean isPreviewingSeason() {
        return previewSeason != null;
    }

    public void stopPreviewingSeason() {
        previewSeason = null;
        previewBlendTarget = null;
        reload();
    }

    public void setPreviewBlendTarget(AmbienceSeasonColors target) {
        previewBlendTarget = target;
        reload();
    }

    public AmbienceSeasonColors getPreviewBlendTarget() {
        return previewBlendTarget;
    }

    public void setPreviewBlendFactor(float factor) {
        previewBlendFactor = MathHelper.clamp(factor, 0f, 1f);
        reload();
    }

    public float getPreviewBlendFactor() {
        return previewBlendFactor;
    }

    /** The currently active season's own colors (ignoring any in-progress fade toward the next one), or null if nothing's previewing/matching/saved, or (handled by each call site) a manual override takes priority anyway. Used for block colors, which don't fade (see currentSeasonBlend()). */
    public AmbienceSeasonColors currentSeasonColors() {
        return currentSeasonBlend().from();
    }

    /** this season's own per-block color list for a block (its individual entries, or its Override Grass/Foliage/Water/Entire Season sentinel), the same lowest-priority tier a Region/Biome/Global block config would otherwise leave to plain vanilla. Never fades - see currentSeasonBlend()'s own note. */
    public List<WeightedColorEntry> getSeasonBlockColors(Block block) {
        if (!seasonAffectBlocks.get()) return null;

        AmbienceSeasonColors season = currentSeasonColors();
        if (season == null) return null;

        return lookupColors(season.blocks, block, b -> !season.disabledBlocks.contains(b));
    }

    private record SeasonBlend(AmbienceSeasonColors from, AmbienceSeasonColors to, float t) {
    }

    // Season boundaries, Northern-hemisphere months - southernHemisphere flips the RESULT, same as
    // currentSeasonName() below always has.
    private static String seasonForMonth(int month) {
        return switch (month) {
            case 12, 1, 2 -> "Winter";
            case 3, 4, 5 -> "Spring";
            case 6, 7, 8 -> "Summer";
            default -> "Fall";
        };
    }

    private String applyHemisphere(String season) {
        if (!southernHemisphere.get()) return season;

        return switch (season) {
            case "Winter" -> "Summer";
            case "Summer" -> "Winter";
            case "Spring" -> "Fall";
            default -> "Spring";
        };
    }

    private AmbienceSeasonColors seasonSlot(String name) {
        return switch (name) {
            case "Winter" -> winterColors.get();
            case "Spring" -> springColors.get();
            case "Summer" -> summerColors.get();
            default -> autumnColors.get();
        };
    }

    // The 1st of Mar/Jun/Sep/Dec, whichever comes next (today itself counts as "next" if today IS
    // one of those dates) - matches seasonForMonth()'s own boundaries exactly.
    private LocalDate nextSeasonBoundary(LocalDate today) {
        int year = today.getYear();
        LocalDate[] boundaries = {
            LocalDate.of(year, 3, 1), LocalDate.of(year, 6, 1), LocalDate.of(year, 9, 1), LocalDate.of(year, 12, 1),
            LocalDate.of(year + 1, 3, 1)
        };

        for (LocalDate boundary : boundaries) {
            if (!boundary.isBefore(today)) return boundary;
        }

        return boundaries[boundaries.length - 1];
    }

    /**
     * The season layer's current state - "from" is always today's plain season (or the previewed
     * one), exactly what currentSeasonColors()/getSeasonBlockColors() use. "to" is only set while
     * Fade Between Seasons is on AND within Fade Days of the next boundary, in which case "t" (0-1)
     * says how far into that blend today is - callers that fade (sky/cloud/lightning/lava below)
     * lerp from "from" toward "to" by "t"; callers that don't (block colors) just use "from" and
     * ignore the rest.
     */
    private SeasonBlend currentSeasonBlend() {
        if (previewSeason != null) {
            AmbienceSeasonColors from = previewSeason.saved ? previewSeason : null;
            AmbienceSeasonColors to = (previewBlendTarget != null && previewBlendTarget.saved) ? previewBlendTarget : null;

            return new SeasonBlend(from, to, to != null ? previewBlendFactor : 0f);
        }
        if (!autoSeasonEnabled.get()) return new SeasonBlend(null, null, 0f);

        AmbienceSeasonColors current = seasonSlot(currentSeasonName());
        AmbienceSeasonColors from = current.saved ? current : null;

        if (!fadeBetweenSeasons.get()) return new SeasonBlend(from, null, 0f);

        LocalDate today = LocalDate.now();
        LocalDate boundary = nextSeasonBoundary(today);
        long daysUntil = java.time.temporal.ChronoUnit.DAYS.between(today, boundary);

        int fadeWindow = fadeDays.get();
        if (daysUntil > fadeWindow) return new SeasonBlend(from, null, 0f);

        AmbienceSeasonColors next = seasonSlot(applyHemisphere(seasonForMonth(boundary.getMonthValue())));
        AmbienceSeasonColors to = next.saved ? next : null;

        float t = fadeWindow <= 0 ? 1f : 1f - (float) daysUntil / fadeWindow;
        return new SeasonBlend(from, to, MathHelper.clamp(t, 0f, 1f));
    }

    private static SettingColor lerpColor(SettingColor a, SettingColor b, float t) {
        return new SettingColor(
            Math.round(a.r + (b.r - a.r) * t),
            Math.round(a.g + (b.g - a.g) * t),
            Math.round(a.b + (b.b - a.b) * t),
            Math.round(a.a + (b.a - a.a) * t)
        );
    }

    /** Blends a flat per-season color (sky/cloud/lightning/lava) across an in-progress season fade - extractor picks that one color out of a given season. */
    private SettingColor blendedSeasonColor(java.util.function.Function<AmbienceSeasonColors, SettingColor> extractor) {
        SeasonBlend blend = currentSeasonBlend();

        SettingColor from = blend.from() != null ? extractor.apply(blend.from()) : null;
        SettingColor to = blend.to() != null ? extractor.apply(blend.to()) : null;

        if (from == null) return to;
        if (to == null || blend.t() <= 0f) return from;

        return lerpColor(from, to, blend.t());
    }

    /** Custom Sky Color if it's on, else the active season's own sky color for the current dimension if one's active AND that specific channel is enabled for it, else null (caller keeps vanilla). */
    public SettingColor effectiveSkyColor() {
        if (customSkyColor.get()) return skyColor();
        if (!seasonAffectSky.get()) return null;

        return blendedSeasonColor(season -> switch (PlayerUtils.getDimension()) {
            case Nether -> season.netherSkyEnabled ? season.netherSky : null;
            case End -> season.endSkyEnabled ? season.endSky : null;
            default -> season.overworldSkyEnabled ? season.overworldSky : null;
        });
    }

    public SettingColor effectiveCloudColor() {
        if (customCloudColor.get()) return cloudColor.get();
        if (!seasonAffectClouds.get()) return null;

        return blendedSeasonColor(season -> season.cloudEnabled ? season.cloud : null);
    }

    public SettingColor effectiveLightningColor() {
        if (changeLightningColor.get()) return lightningColor.get();
        if (!seasonAffectLightning.get()) return null;

        return blendedSeasonColor(season -> season.lightningEnabled ? season.lightning : null);
    }

    public SettingColor effectiveLavaColor() {
        if (customLavaColor.get()) return lavaColor.get();
        if (!seasonAffectLava.get()) return null;

        return blendedSeasonColor(season -> season.lavaEnabled ? season.lava : null);
    }

    // Auto Season - checked once on activation and at most once per real day after that (cheap
    // date math, but no reason to redo it every single tick).
    private long lastCheckedEpochDay = -1;

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!autoSeasonEnabled.get()) return;

        long today = LocalDate.now().toEpochDay();
        if (today == lastCheckedEpochDay) return;
        lastCheckedEpochDay = today;

        checkAutoSeason();
    }

    // Our own Renderer3D rather than the shared event.renderer - so toggling Visualize Occluded's
    // depth test only ever affects this outline, never any other module drawing into the same
    // shared renderer this same frame (BonesRings/Visualizer do the same for the same reason).
    private final Renderer3D regionRenderer = new Renderer3D();

    @EventHandler
    private void onRenderRegions(Render3DEvent event) {
        if (!visualizeRegions.get() || mc.world == null) return;

        boolean occluded = visualizeOccluded.get();
        regionRenderer.lines.depthTest = occluded;
        regionRenderer.triangles.depthTest = occluded;

        String dimensionId = mc.world.getRegistryKey().getValue().toString();
        Color baseSide = visualizeSideColor.get();
        Color baseLine = visualizeLineColor.get();
        boolean colorPerRegion = visualizeColorPerRegion.get();
        ShapeMode mode = visualizeShapeMode.get();
        BoundaryMode boundaryMode = visualizeBoundaryMode.get();
        // Full 3D's own chunked scan works chunk by chunk, not block by block - measuring the
        // range in chunks there instead of raw blocks matches the unit it's actually using
        // internally, and lines up with how Bobby (see BOBBY_LOADED) itself thinks about range too.
        int blockRange = (boundaryMode == BoundaryMode.Full3D && visualizeChunkedScan.get())
            ? visualizeChunkRange.get() * 16
            : visualizeShowBlocksRange.get();

        regionRenderer.begin();

        int index = 0;
        for (AmbienceRegion region : regions.get()) {
            if (!region.enabled || !region.visualize) continue;
            if (!region.dimension.isEmpty() && !region.dimension.equals(dimensionId)) continue;

            Color side = colorPerRegion ? regionColor(index, baseSide) : baseSide;
            Color line = colorPerRegion ? regionColor(index, baseLine) : baseLine;
            index++;

            if (region.pointMode) {
                drawPointRegion(regionRenderer, region, line);

                // Only point mode benefits from this - a box region's real per-block boundary is
                // already exactly its own 6 flat faces, i.e. exactly what drawBoxRegion already
                // draws, so running the voxel scan for it would just redraw the same faces.
                boolean fovCulling = visualizeFovCulling.get();
                switch (boundaryMode) {
                    case Plane -> drawPointRegionPlane(regionRenderer, region, dimensionId, side, line, mode, blockRange, fovCulling);
                    case Full3D -> drawPointRegionBoundary(regionRenderer, region, dimensionId, side, line, mode, blockRange, visualizeLod.get(), fovCulling, visualizeChunkedScan.get());
                    case Contour -> drawPointRegionContour(regionRenderer, region, dimensionId, side, line, mode, blockRange, fovCulling);
                    case PointCloud -> drawPointRegionCloud(regionRenderer, region, dimensionId, line, fovCulling);
                    case Probe -> drawPointRegionProbe(regionRenderer, region, dimensionId, line);
                    case Off -> {}
                }
            } else {
                drawBoxRegion(regionRenderer, region, side, line, mode);
            }
        }

        regionRenderer.end();
        regionRenderer.render(event.matrices);
    }

    // Face directions for a cached boundary entry.
    private static final int FACE_WEST = 0, FACE_EAST = 1, FACE_NORTH = 2, FACE_SOUTH = 3, FACE_DOWN = 4, FACE_UP = 5;

    private static class BoundaryCache {
        int playerX, playerY, playerZ;
        long scannedAtMs;
        int blockRange;
        boolean lod;
        boolean fovCulling;
        float yaw, pitch;
        List<int[]> faces = new ArrayList<>(); // {dir, x0, y0, z0, x1, y1, z1} - already-merged rectangles, see greedyRects
    }

    // Rescanning (region.contains() + a biome lookup per cell) is the expensive part - re-running
    // it every single rendered frame for no reason (a region's shape never changes just because
    // time passed) was the actual cost here, not the drawing. The cache below reuses the last
    // scan's result and only redraws it most frames, rescanning only when you've moved enough to
    // matter or the cache is old enough that something else (editing the region live) might have
    // changed - this is the real fix for "minimise what this has to render".
    private final Map<AmbienceRegion, BoundaryCache> boundaryCaches = new WeakHashMap<>();
    private static final double BOUNDARY_RESCAN_MOVE_THRESHOLD_SQ = 4.0 * 4.0;
    private static final long BOUNDARY_RESCAN_MAX_AGE_MS = 3000;

    private static class PlaneCache {
        int playerX, y, playerZ;
        long scannedAtMs;
        int blockRange;
        // Connected Textures-style: only the silhouette edges get drawn (one continuous outline),
        // not a line around every individual cell - same idea as the 3D boundary shell, just in 2D.
        List<float[]> edges = new ArrayList<>(); // {x1, z1, x2, z2}
        List<int[]> filledCells = new ArrayList<>(); // {x, z} - only used for the optional filled floor
    }

    private final Map<AmbienceRegion, PlaneCache> planeCaches = new WeakHashMap<>();

    // A flat 2D cross-section at your current Y instead of the full 3D shell below - far fewer
    // cells to check (one Y layer, not a whole range of them) and far fewer edges to draw, and
    // it's usually what you actually want while placing something at ground level: what does the
    // region look like right here, right now, redrawing itself as you walk up/down through it.
    private void drawPointRegionPlane(Renderer3D r, AmbienceRegion region, String dimensionId, Color side, Color line, ShapeMode mode, int blockRange, boolean fovCulling) {
        if (mc.player == null || mc.world == null) return;

        int pxp = mc.player.getBlockX(), yLevel = mc.player.getBlockY(), pzp = mc.player.getBlockZ();

        PlaneCache cache = planeCaches.get(region);
        boolean stale = cache == null
            || cache.y != yLevel
            || cache.blockRange != blockRange
            || System.currentTimeMillis() - cache.scannedAtMs > BOUNDARY_RESCAN_MAX_AGE_MS
            || distSq(cache.playerX, yLevel, cache.playerZ, pxp, yLevel, pzp) > BOUNDARY_RESCAN_MOVE_THRESHOLD_SQ;

        if (stale) {
            cache = scanPointRegionPlane(region, dimensionId, pxp, yLevel, pzp, blockRange);
            planeCaches.put(region, cache);
        }

        if (mode.sides()) {
            for (int[] cell : cache.filledCells) {
                if (fovCulling && !isInFov(cell[0] + 0.5, yLevel, cell[1] + 0.5)) continue;
                r.quadHorizontal(cell[0], yLevel, cell[1], cell[0] + 1, cell[1] + 1, side);
            }
        }

        if (mode.lines()) {
            for (float[] edge : cache.edges) {
                if (fovCulling && !isInFov((edge[0] + edge[2]) / 2.0, yLevel, (edge[1] + edge[3]) / 2.0)) continue;
                r.line(edge[0], yLevel, edge[1], edge[2], yLevel, edge[3], line);
            }
        }
    }

    private PlaneCache scanPointRegionPlane(AmbienceRegion region, String dimensionId, int pxp, int yLevel, int pzp, int blockRange) {
        PlaneCache cache = new PlaneCache();
        cache.playerX = pxp;
        cache.y = yLevel;
        cache.playerZ = pzp;
        cache.scannedAtMs = System.currentTimeMillis();
        cache.blockRange = blockRange;

        if (yLevel < region.effectiveMinY() || yLevel > region.effectiveMaxY()) return cache; // this Y slice misses the region entirely

        int minX = Math.max(region.px - region.radiusX, pxp - blockRange), maxX = Math.min(region.px + region.radiusX, pxp + blockRange);
        int minZ = Math.max(region.pz - region.radiusZ, pzp - blockRange), maxZ = Math.min(region.pz + region.radiusZ, pzp + blockRange);
        if (minX > maxX || minZ > maxZ) return cache;

        int sx = maxX - minX + 3, sz = maxZ - minZ + 3;
        boolean[] filled = new boolean[sx * sz];

        BlockPos.Mutable mutable = new BlockPos.Mutable();
        for (int x = minX - 1; x <= maxX + 1; x++) {
            for (int z = minZ - 1; z <= maxZ + 1; z++) {
                mutable.set(x, yLevel, z);

                String biomeId = region.biome.isEmpty() ? "" : cachedBiomeId(dimensionId, mutable);
                if (region.contains(dimensionId, mutable, biomeId)) {
                    filled[(x - minX + 1) * sz + (z - minZ + 1)] = true;
                }
            }
        }

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                int lx = x - minX + 1, lz = z - minZ + 1;
                if (!filled[lx * sz + lz]) continue;

                cache.filledCells.add(new int[]{x, z});

                if (!filled[(lx - 1) * sz + lz]) cache.edges.add(new float[]{x, z, x, z + 1});
                if (!filled[(lx + 1) * sz + lz]) cache.edges.add(new float[]{x + 1, z, x + 1, z + 1});
                if (!filled[lx * sz + (lz - 1)]) cache.edges.add(new float[]{x, z, x + 1, z});
                if (!filled[lx * sz + (lz + 1)]) cache.edges.add(new float[]{x, z + 1, x + 1, z + 1});
            }
        }

        return cache;
    }

    // The sphere/cylinder outline above is a smooth approximation - the real per-block membership
    // check Ambience uses is blocky, not smooth, so the two don't quite line up at the edge. This
    // draws only the actual boundary faces (an affected block touching an unaffected one), not
    // every affected block's own full outline - a thin shell instead of a dense solid block grid.
    // Only checked within blockRange of the player (clamped further to the region's own radius)
    // since even a modest region is tens of thousands of blocks otherwise.
    private void drawPointRegionBoundary(Renderer3D r, AmbienceRegion region, String dimensionId, Color side, Color line, ShapeMode mode, int blockRange, boolean lod, boolean fovCulling, boolean chunked) {
        if (mc.player == null || mc.world == null) return;

        int pxp = mc.player.getBlockX(), pyp = mc.player.getBlockY(), pzp = mc.player.getBlockZ();

        float yaw = mc.player.getYaw(1f), pitch = mc.player.getPitch(1f);

        BoundaryCache cache = boundaryCaches.get(region);
        BoundaryScanJob job = boundaryScanJobs.get(region);

        boolean stale = job == null && (cache == null
            || cache.blockRange != blockRange
            || cache.lod != lod
            || cache.fovCulling != fovCulling
            || System.currentTimeMillis() - cache.scannedAtMs > BOUNDARY_RESCAN_MAX_AGE_MS
            || distSq(cache.playerX, cache.playerY, cache.playerZ, pxp, pyp, pzp) > BOUNDARY_RESCAN_MOVE_THRESHOLD_SQ
            // The scan box itself shrinks to the camera's view cone when FOV Culling is on AND
            // not chunked (see startBoundaryScanJob), so turning to look somewhere new also needs
            // a rescan then - not on every tiny mouse twitch though, only once you've turned far
            // enough that the old box plausibly no longer covers what you're now looking at.
            // Irrelevant when chunked (the scan box never depends on look direction there).
            || (fovCulling && !chunked && angleDiffDeg(cache.yaw, cache.pitch, yaw, pitch) > 20.0));

        if (stale) {
            job = startBoundaryScanJob(region, dimensionId, pxp, pyp, pzp, blockRange, lod, fovCulling, chunked);
            job.yaw = yaw;
            job.pitch = pitch;
            boundaryScanJobs.put(region, job);
        }

        if (job != null) {
            // Chunked: one budget's worth of progress this frame, picking up next frame where it
            // left off (see BOUNDARY_FILL_BUDGET). Not chunked: finish the whole job right now, in
            // this one frame - the original behavior, a bigger but one-time stall instead of
            // several smaller ones spread out while it catches up to full detail.
            boolean finished = false;
            if (chunked) {
                finished = advanceBoundaryScanJob(job);
            } else {
                while (!finished) finished = advanceBoundaryScanJob(job);
            }

            if (finished) {
                cache = finishBoundaryScanJob(job);
                cache.yaw = job.yaw;
                cache.pitch = job.pitch;
                boundaryCaches.put(region, cache);
                boundaryScanJobs.remove(region);
            }
        }

        if (cache == null) return;

        for (int[] face : cache.faces) {
            if (fovCulling && !isInFov((face[1] + face[4]) / 2.0, (face[2] + face[5]) / 2.0, (face[3] + face[6]) / 2.0)) continue;

            drawBoundaryFace(r, face[0], face[1], face[2], face[3], face[4], face[5], face[6], side, line, mode);
        }
    }

    // The golden angle (~137.5 degrees) spaces hues as evenly as possible no matter how many
    // regions there are or what order they're in - region 0 and region 1 always land far apart on
    // the color wheel, instead of a plain fraction-of-360 split clustering nearby indices together
    // once you have more than a handful of regions. Keeps the base color's own alpha (so Visualize
    // Line/Side Color's opacity still applies), just not its hue.
    private static Color regionColor(int index, Color base) {
        float hue = (float) ((index * 137.508) % 360.0) / 360f;
        int rgb = MathHelper.hsvToRgb(hue, 0.85f, 1f);
        return new Color((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, base.a);
    }

    private static double distSq(int x1, int y1, int z1, int x2, int y2, int z2) {
        double dx = x1 - x2, dy = y1 - y2, dz = z1 - z2;
        return dx * dx + dy * dy + dz * dz;
    }

    // True foveated rendering: whether a point is within the player's current field of view at
    // all, regardless of distance - not a distance-based detail falloff (see Visualize LOD above,
    // a genuinely different thing). +10 degrees of margin over the client's own FOV setting so a
    // merged rectangle's own size (checked only at its center, not edge to edge) doesn't get
    // prematurely culled right as it crosses the true edge of the screen.
    private boolean isInFov(double x, double y, double z) {
        if (mc.player == null) return true;

        Vec3d camPos = mc.player.getCameraPosVec(1f);
        Vec3d toPoint = new Vec3d(x - camPos.x, y - camPos.y, z - camPos.z);
        if (toPoint.lengthSquared() < 1.0e-6) return true;

        Vec3d look = mc.player.getRotationVec(1f);
        double cosAngle = toPoint.normalize().dotProduct(look);

        double halfFov = mc.options.getFov().getValue() / 2.0 + 10;
        return cosAngle >= Math.cos(Math.toRadians(halfFov));
    }

    // FOV Culling used to only skip DRAWING faces outside your view - the scan itself still
    // checked the whole range cube regardless of where you were looking, so it did nothing for the
    // actual rescan cost (the thing that causes a hitch when you move). This shrinks the scanned
    // box itself to roughly the camera's view cone before the expensive per-cell loop even starts -
    // an approximation (an AABB wrapped around the cone, not the cone itself, sampled at a handful
    // of points around its far edge, always including the camera's own position), but a real,
    // substantial reduction at anything narrower than a full 180-degree view, and it stacks with
    // the existing region/range clamp rather than replacing it.
    private int[] fovScanBounds(int minX, int maxX, int minY, int maxY, int minZ, int maxZ, int blockRange) {
        if (mc.player == null) return new int[]{minX, maxX, minY, maxY, minZ, maxZ};

        Vec3d camPos = mc.player.getCameraPosVec(1f);
        Vec3d look = mc.player.getRotationVec(1f);

        Vec3d worldUp = new Vec3d(0, 1, 0);
        Vec3d right = look.crossProduct(worldUp);
        if (right.lengthSquared() < 1.0e-6) right = new Vec3d(1, 0, 0);
        right = right.normalize();
        Vec3d up = right.crossProduct(look).normalize();

        double halfFovRad = Math.toRadians(mc.options.getFov().getValue() / 2.0 + 15);
        double edgeRadius = Math.tan(halfFovRad) * blockRange;
        Vec3d farCenter = camPos.add(look.multiply(blockRange));

        double bx0 = camPos.x, bx1 = camPos.x, by0 = camPos.y, by1 = camPos.y, bz0 = camPos.z, bz1 = camPos.z;

        int samples = 12;
        for (int i = 0; i < samples; i++) {
            double angle = 2 * Math.PI * i / samples;
            Vec3d point = farCenter.add(right.multiply(Math.cos(angle) * edgeRadius)).add(up.multiply(Math.sin(angle) * edgeRadius));

            bx0 = Math.min(bx0, point.x); bx1 = Math.max(bx1, point.x);
            by0 = Math.min(by0, point.y); by1 = Math.max(by1, point.y);
            bz0 = Math.min(bz0, point.z); bz1 = Math.max(bz1, point.z);
        }

        return new int[]{
            Math.max(minX, (int) Math.floor(bx0)), Math.min(maxX, (int) Math.ceil(bx1)),
            Math.max(minY, (int) Math.floor(by0)), Math.min(maxY, (int) Math.ceil(by1)),
            Math.max(minZ, (int) Math.floor(bz0)), Math.min(maxZ, (int) Math.ceil(bz1))
        };
    }

    private static double angleDiffDeg(float yaw1, float pitch1, float yaw2, float pitch2) {
        double dYaw = MathHelper.wrapDegrees(yaw1 - yaw2);
        double dPitch = pitch1 - pitch2;
        return Math.sqrt(dYaw * dYaw + dPitch * dPitch);
    }

    // The fill pass (one contains() check, sometimes a biome lookup, per padded cell) is the part
    // that actually costs anything - re-running it all synchronously the instant the cache goes
    // stale is what causes a hitch right as you move or turn. This spreads it across frames
    // instead: each frame does at most BOUNDARY_FILL_BUDGET cells' worth of work and picks up
    // exactly where it left off next frame, so what used to be one big stall becomes several
    // frames each a little more expensive than normal - not imperceptible, but nowhere near as
    // sharp a spike. The OLD complete result keeps drawing the whole time a new scan is building,
    // so there's no blank gap or flicker while this runs in the background.
    private static final int BOUNDARY_FILL_BUDGET = 25000;

    private static class BoundaryScanJob {
        AmbienceRegion region;
        String dimensionId;
        int pxp, pyp, pzp, blockRange;
        boolean lod, fovCulling;
        float yaw, pitch;

        int minX, minY, minZ, step;
        int cellsX, cellsY, cellsZ, sx, sy, sz;
        boolean[] filled;
        int nextIdx;
        int totalCells;
    }

    private final Map<AmbienceRegion, BoundaryScanJob> boundaryScanJobs = new WeakHashMap<>();

    private BoundaryScanJob startBoundaryScanJob(AmbienceRegion region, String dimensionId, int pxp, int pyp, int pzp, int blockRange, boolean lod, boolean fovCulling, boolean chunked) {
        BoundaryScanJob job = new BoundaryScanJob();
        job.region = region;
        job.dimensionId = dimensionId;
        job.pxp = pxp;
        job.pyp = pyp;
        job.pzp = pzp;
        job.blockRange = blockRange;
        job.lod = lod;
        job.fovCulling = fovCulling;

        int regionMinX = region.px - region.radiusX, regionMaxX = region.px + region.radiusX;
        int regionMinY = region.effectiveMinY(), regionMaxY = region.effectiveMaxY();
        int regionMinZ = region.pz - region.radiusZ, regionMaxZ = region.pz + region.radiusZ;

        int minX = Math.max(regionMinX, pxp - blockRange), maxX = Math.min(regionMaxX, pxp + blockRange);
        int minY = Math.max(regionMinY, pyp - blockRange), maxY = Math.min(regionMaxY, pyp + blockRange);
        int minZ = Math.max(regionMinZ, pzp - blockRange), maxZ = Math.min(regionMaxZ, pzp + blockRange);

        // Skipped when chunked: a chunked job can take several frames to finish, and the FOV box
        // is only ever computed once, right at the start, from wherever you were looking THEN - if
        // you keep turning while it's still catching up, the finished result can come out with
        // gaps in the direction you ended up looking, since that part of the box was never
        // actually scanned. Not chunked doesn't have this problem (it finishes in the same frame
        // it started, so the look direction can't have changed by the time it's done) and still
        // benefits from the smaller scan, so it keeps doing this.
        if (minX <= maxX && minY <= maxY && minZ <= maxZ && fovCulling && !chunked) {
            int[] b = fovScanBounds(minX, maxX, minY, maxY, minZ, maxZ, blockRange);
            minX = b[0]; maxX = b[1]; minY = b[2]; maxY = b[3]; minZ = b[4]; maxZ = b[5];
        }

        if (minX > maxX || minY > maxY || minZ > maxZ) {
            job.totalCells = 0;
            return job;
        }

        // Used to also apply an automatic step floor here regardless of this setting, scaling with
        // Visualize Range, so cranking the range couldn't cause a multi-million-cell scan - but
        // that meant LOD off still showed coarsened, blockier detail at long range, not the full
        // per-block precision "off" is supposed to mean. The scan being spread across frames now
        // (see BoundaryScanJob) already protects against that hitch regardless of cell count, so
        // this doesn't need to trade away detail for it anymore - off is really off.
        double regionDistSq = distSq(pxp, pyp, pzp, region.px, region.py, region.pz);
        int step = 1;
        if (lod) {
            if (regionDistSq > 40.0 * 40.0) step = Math.max(step, 4);
            else if (regionDistSq > 24.0 * 24.0) step = Math.max(step, 3);
            else if (regionDistSq > 12.0 * 12.0) step = Math.max(step, 2);
        }

        job.minX = minX;
        job.minY = minY;
        job.minZ = minZ;
        job.step = step;
        job.cellsX = (maxX - minX) / step + 1;
        job.cellsY = (maxY - minY) / step + 1;
        job.cellsZ = (maxZ - minZ) / step + 1;
        job.sx = job.cellsX + 2;
        job.sy = job.cellsY + 2;
        job.sz = job.cellsZ + 2;
        job.filled = new boolean[job.sx * job.sy * job.sz];
        job.totalCells = job.sx * job.sy * job.sz;
        job.nextIdx = 0;

        return job;
    }

    /** Processes up to BOUNDARY_FILL_BUDGET cells starting from where the job left off. Returns true once the whole fill pass is done. */
    private boolean advanceBoundaryScanJob(BoundaryScanJob job) {
        if (job.totalCells == 0) return true;

        BlockPos.Mutable mutable = new BlockPos.Mutable();
        int end = Math.min(job.totalCells, job.nextIdx + BOUNDARY_FILL_BUDGET);

        for (int idx = job.nextIdx; idx < end; idx++) {
            int lz = idx / (job.sy * job.sx);
            int rem = idx % (job.sy * job.sx);
            int ly = rem / job.sx;
            int lx = rem % job.sx;

            int x = job.minX + (lx - 1) * job.step;
            int y = job.minY + (ly - 1) * job.step;
            int z = job.minZ + (lz - 1) * job.step;
            mutable.set(x, y, z);

            String biomeId = job.region.biome.isEmpty() ? "" : cachedBiomeId(job.dimensionId, mutable);
            if (job.region.contains(job.dimensionId, mutable, biomeId)) {
                job.filled[idx] = true;
            }
        }

        job.nextIdx = end;
        return job.nextIdx >= job.totalCells;
    }

    private BoundaryCache finishBoundaryScanJob(BoundaryScanJob job) {
        BoundaryCache cache = new BoundaryCache();
        cache.playerX = job.pxp;
        cache.playerY = job.pyp;
        cache.playerZ = job.pzp;
        cache.scannedAtMs = System.currentTimeMillis();
        cache.blockRange = job.blockRange;
        cache.lod = job.lod;
        cache.fovCulling = job.fovCulling;

        if (job.totalCells == 0) return cache;

        AmbienceRegion region = job.region;
        boolean[] filled = job.filled;
        int minX = job.minX, minY = job.minY, minZ = job.minZ, step = job.step;
        int cellsX = job.cellsX, cellsY = job.cellsY, cellsZ = job.cellsZ;
        int sx = job.sx, sy = job.sy;

        // Greedy meshing (same idea as Optifine's CTM / standard voxel-engine chunk meshing): a
        // naive per-cell pass here would emit one separate quad - and one separate 4-edge outline -
        // per individual macro-cell, so a whole flat run of coplanar, adjacent boundary cells (most
        // of any sphere/cylinder's surface) came out as a dense grid of many small quares with a
        // line between every single pair of neighbours, instead of looking like one continuous
        // surface. Each of the 6 directions is instead merged into as few rectangles as possible
        // first, one "layer" (constant coordinate along that direction's own axis) at a time - the
        // result is the real boundary silhouette (and a handful of internal lines only where the
        // merged rectangles themselves had to split, e.g. around the poles of a sphere), not a grid.
        for (int ix = 0; ix < cellsX; ix++) {
            int x = minX + ix * step;

            boolean[][] westGrid = new boolean[cellsY][cellsZ];
            boolean[][] eastGrid = new boolean[cellsY][cellsZ];
            for (int iy = 0; iy < cellsY; iy++) {
                for (int iz = 0; iz < cellsZ; iz++) {
                    int lx = ix + 1, ly = iy + 1, lz = iz + 1;
                    boolean self = filled[boundaryIndex(lx, ly, lz, sx, sy)];
                    westGrid[iy][iz] = self && !filled[boundaryIndex(lx - 1, ly, lz, sx, sy)];
                    eastGrid[iy][iz] = self && !filled[boundaryIndex(lx + 1, ly, lz, sx, sy)];
                }
            }

            for (int[] rect : greedyRects(westGrid, cellsY, cellsZ)) {
                int y0 = minY + rect[0] * step, z0 = minZ + rect[1] * step;
                int y1 = minY + (rect[0] + rect[2]) * step, z1 = minZ + (rect[1] + rect[3]) * step;
                cache.faces.add(new int[]{FACE_WEST, x, y0, z0, x, y1, z1});
            }
            for (int[] rect : greedyRects(eastGrid, cellsY, cellsZ)) {
                int y0 = minY + rect[0] * step, z0 = minZ + rect[1] * step;
                int y1 = minY + (rect[0] + rect[2]) * step, z1 = minZ + (rect[1] + rect[3]) * step;
                cache.faces.add(new int[]{FACE_EAST, x + step, y0, z0, x + step, y1, z1});
            }
        }

        for (int iy = 0; iy < cellsY; iy++) {
            int y = minY + iy * step;

            boolean[][] downGrid = new boolean[cellsX][cellsZ];
            boolean[][] upGrid = new boolean[cellsX][cellsZ];
            for (int ix = 0; ix < cellsX; ix++) {
                for (int iz = 0; iz < cellsZ; iz++) {
                    int lx = ix + 1, ly = iy + 1, lz = iz + 1;
                    boolean self = filled[boundaryIndex(lx, ly, lz, sx, sy)];
                    downGrid[ix][iz] = self && !filled[boundaryIndex(lx, ly - 1, lz, sx, sy)];
                    upGrid[ix][iz] = self && !filled[boundaryIndex(lx, ly + 1, lz, sx, sy)];
                }
            }

            for (int[] rect : greedyRects(downGrid, cellsX, cellsZ)) {
                int x0 = minX + rect[0] * step, z0 = minZ + rect[1] * step;
                int x1 = minX + (rect[0] + rect[2]) * step, z1 = minZ + (rect[1] + rect[3]) * step;
                cache.faces.add(new int[]{FACE_DOWN, x0, y, z0, x1, y, z1});
            }
            for (int[] rect : greedyRects(upGrid, cellsX, cellsZ)) {
                int x0 = minX + rect[0] * step, z0 = minZ + rect[1] * step;
                int x1 = minX + (rect[0] + rect[2]) * step, z1 = minZ + (rect[1] + rect[3]) * step;
                cache.faces.add(new int[]{FACE_UP, x0, y + step, z0, x1, y + step, z1});
            }
        }

        for (int iz = 0; iz < cellsZ; iz++) {
            int z = minZ + iz * step;

            boolean[][] northGrid = new boolean[cellsX][cellsY];
            boolean[][] southGrid = new boolean[cellsX][cellsY];
            for (int ix = 0; ix < cellsX; ix++) {
                for (int iy = 0; iy < cellsY; iy++) {
                    int lx = ix + 1, ly = iy + 1, lz = iz + 1;
                    boolean self = filled[boundaryIndex(lx, ly, lz, sx, sy)];
                    northGrid[ix][iy] = self && !filled[boundaryIndex(lx, ly, lz - 1, sx, sy)];
                    southGrid[ix][iy] = self && !filled[boundaryIndex(lx, ly, lz + 1, sx, sy)];
                }
            }

            for (int[] rect : greedyRects(northGrid, cellsX, cellsY)) {
                int x0 = minX + rect[0] * step, y0 = minY + rect[1] * step;
                int x1 = minX + (rect[0] + rect[2]) * step, y1 = minY + (rect[1] + rect[3]) * step;
                cache.faces.add(new int[]{FACE_NORTH, x0, y0, z, x1, y1, z});
            }
            for (int[] rect : greedyRects(southGrid, cellsX, cellsY)) {
                int x0 = minX + rect[0] * step, y0 = minY + rect[1] * step;
                int x1 = minX + (rect[0] + rect[2]) * step, y1 = minY + (rect[1] + rect[3]) * step;
                cache.faces.add(new int[]{FACE_SOUTH, x0, y0, z + step, x1, y1, z + step});
            }
        }

        return cache;
    }

    /** Standard greedy rectangle cover: scans for an unvisited true cell, grows it right as far as it can, then grows that whole strip down as far as every cell in it stays true - repeats until every true cell belongs to exactly one emitted rectangle. Not the fewest possible rectangles, but simple, fast, and a given cell is never covered twice. */
    private static List<int[]> greedyRects(boolean[][] grid, int w, int h) {
        boolean[][] visited = new boolean[w][h];
        List<int[]> rects = new ArrayList<>();

        for (int u = 0; u < w; u++) {
            for (int v = 0; v < h; v++) {
                if (!grid[u][v] || visited[u][v]) continue;

                int uLen = 1;
                while (u + uLen < w && grid[u + uLen][v] && !visited[u + uLen][v]) uLen++;

                int vLen = 1;
                outer:
                while (v + vLen < h) {
                    for (int du = 0; du < uLen; du++) {
                        if (!grid[u + du][v + vLen] || visited[u + du][v + vLen]) break outer;
                    }
                    vLen++;
                }

                for (int du = 0; du < uLen; du++) {
                    for (int dv = 0; dv < vLen; dv++) {
                        visited[u + du][v + dv] = true;
                    }
                }

                rects.add(new int[]{u, v, uLen, vLen});
            }
        }

        return rects;
    }

    private void drawBoundaryFace(Renderer3D r, int dir, int x0, int y0, int z0, int x1, int y1, int z1, Color side, Color line, ShapeMode mode) {
        if (dir == FACE_DOWN || dir == FACE_UP) r.sideHorizontal(x0, y0, z0, x1, z1, side, line, mode);
        else r.sideVertical(x0, y0, z0, x1, y1, z1, side, line, mode);
    }

    private static int boundaryIndex(int x, int y, int z, int sx, int sy) {
        return (z * sy + y) * sx + x;
    }

    // ===== Contour: several Plane-style 2D slices stacked at fixed Y intervals =====

    private static class ContourCache {
        int playerX, playerY, playerZ;
        long scannedAtMs;
        int blockRange;
        int spacing;
        List<float[]> edges = new ArrayList<>(); // {x1, y, z1, x2, y, z2}
        List<float[]> filledCells = new ArrayList<>(); // {x, y, z}
    }

    private final Map<AmbienceRegion, ContourCache> contourCaches = new WeakHashMap<>();

    private void drawPointRegionContour(Renderer3D r, AmbienceRegion region, String dimensionId, Color side, Color line, ShapeMode mode, int blockRange, boolean fovCulling) {
        if (mc.player == null || mc.world == null) return;

        int pxp = mc.player.getBlockX(), pyp = mc.player.getBlockY(), pzp = mc.player.getBlockZ();
        int spacing = visualizeContourSpacing.get();

        ContourCache cache = contourCaches.get(region);
        boolean stale = cache == null
            || cache.blockRange != blockRange
            || cache.spacing != spacing
            || System.currentTimeMillis() - cache.scannedAtMs > BOUNDARY_RESCAN_MAX_AGE_MS
            || distSq(cache.playerX, cache.playerY, cache.playerZ, pxp, pyp, pzp) > BOUNDARY_RESCAN_MOVE_THRESHOLD_SQ;

        if (stale) {
            cache = scanPointRegionContour(region, dimensionId, pxp, pyp, pzp, blockRange, spacing);
            contourCaches.put(region, cache);
        }

        if (mode.sides()) {
            for (float[] cell : cache.filledCells) {
                if (fovCulling && !isInFov(cell[0] + 0.5, cell[1], cell[2] + 0.5)) continue;
                r.quadHorizontal(cell[0], cell[1], cell[2], cell[0] + 1, cell[2] + 1, side);
            }
        }

        if (mode.lines()) {
            for (float[] edge : cache.edges) {
                if (fovCulling && !isInFov((edge[0] + edge[3]) / 2.0, edge[1], (edge[2] + edge[5]) / 2.0)) continue;
                r.line(edge[0], edge[1], edge[2], edge[3], edge[4], edge[5], line);
            }
        }
    }

    private ContourCache scanPointRegionContour(AmbienceRegion region, String dimensionId, int pxp, int pyp, int pzp, int blockRange, int spacing) {
        ContourCache cache = new ContourCache();
        cache.playerX = pxp;
        cache.playerY = pyp;
        cache.playerZ = pzp;
        cache.scannedAtMs = System.currentTimeMillis();
        cache.blockRange = blockRange;
        cache.spacing = spacing;

        int minX = Math.max(region.px - region.radiusX, pxp - blockRange), maxX = Math.min(region.px + region.radiusX, pxp + blockRange);
        int minZ = Math.max(region.pz - region.radiusZ, pzp - blockRange), maxZ = Math.min(region.pz + region.radiusZ, pzp + blockRange);
        int minY = Math.max(region.effectiveMinY(), pyp - blockRange), maxY = Math.min(region.effectiveMaxY(), pyp + blockRange);
        if (minX > maxX || minZ > maxZ || minY > maxY) return cache;

        BlockPos.Mutable mutable = new BlockPos.Mutable();

        for (int y = minY; y <= maxY; y += spacing) {
            int sx = maxX - minX + 3, sz = maxZ - minZ + 3;
            boolean[] filled = new boolean[sx * sz];

            for (int x = minX - 1; x <= maxX + 1; x++) {
                for (int z = minZ - 1; z <= maxZ + 1; z++) {
                    mutable.set(x, y, z);

                    String biomeId = region.biome.isEmpty() ? "" : cachedBiomeId(dimensionId, mutable);
                    if (region.contains(dimensionId, mutable, biomeId)) {
                        filled[(x - minX + 1) * sz + (z - minZ + 1)] = true;
                    }
                }
            }

            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    int lx = x - minX + 1, lz = z - minZ + 1;
                    if (!filled[lx * sz + lz]) continue;

                    cache.filledCells.add(new float[]{x, y, z});

                    if (!filled[(lx - 1) * sz + lz]) cache.edges.add(new float[]{x, y, z, x, y, z + 1});
                    if (!filled[(lx + 1) * sz + lz]) cache.edges.add(new float[]{x + 1, y, z, x + 1, y, z + 1});
                    if (!filled[lx * sz + (lz - 1)]) cache.edges.add(new float[]{x, y, z, x + 1, y, z});
                    if (!filled[lx * sz + (lz + 1)]) cache.edges.add(new float[]{x, y, z + 1, x + 1, y, z + 1});
                }
            }
        }

        return cache;
    }

    // ===== Point Cloud: sparse sample points scattered across the real surface, no scanning =====

    private static class PointCloudCache {
        int count;
        List<double[]> points = new ArrayList<>(); // {x, y, z}
    }

    private final Map<AmbienceRegion, PointCloudCache> pointCloudCaches = new WeakHashMap<>();

    private void drawPointRegionCloud(Renderer3D r, AmbienceRegion region, String dimensionId, Color line, boolean fovCulling) {
        if (mc.player == null || mc.world == null) return;

        int count = visualizePointCloudCount.get();

        PointCloudCache cache = pointCloudCaches.get(region);
        if (cache == null || cache.count != count) {
            cache = generatePointCloud(region, dimensionId, count);
            pointCloudCaches.put(region, cache);
        }

        int blockRange = visualizeShowBlocksRange.get();
        double rangeSq = (double) blockRange * blockRange;
        Vec3d playerPos = mc.player.getPos();

        for (double[] p : cache.points) {
            double dx = p[0] - playerPos.x, dy = p[1] - playerPos.y, dz = p[2] - playerPos.z;
            if (dx * dx + dy * dy + dz * dz > rangeSq) continue;
            if (fovCulling && !isInFov(p[0], p[1], p[2])) continue;

            drawOriginMarker(r, p[0], p[1], p[2], line);
        }
    }

    // Fibonacci sphere distribution - spreads N points as evenly as possible across a unit sphere,
    // then scaled per-axis by the region's own radii (and, for Cylinder, split between the flat
    // caps and the curved side wall instead). Generated once per region/count (not per frame) and
    // checked against the region's own real contains() once at generation time, so a biome
    // restriction correctly thins out points that land outside it instead of ignoring it.
    private PointCloudCache generatePointCloud(AmbienceRegion region, String dimensionId, int count) {
        PointCloudCache cache = new PointCloudCache();
        cache.count = count;

        String dimensionIdForBiome = region.dimension.isEmpty() ? dimensionId : region.dimension;
        BlockPos.Mutable mutable = new BlockPos.Mutable();
        double goldenAngle = Math.PI * (3.0 - Math.sqrt(5.0));

        if (region.pointShape == AmbienceRegion.PointShape.Cylinder) {
            double minY = region.effectiveMinY(), maxY = region.effectiveMaxY() + 1.0;

            int capCount = count / 4;
            int sideCount = count - capCount * 2;

            for (int i = 0; i < sideCount; i++) {
                double angle = goldenAngle * i;
                double v = i / (double) Math.max(sideCount - 1, 1);
                addCloudPoint(cache, region, dimensionIdForBiome, mutable,
                    region.px + 0.5 + Math.cos(angle) * region.radiusX,
                    minY + v * (maxY - minY),
                    region.pz + 0.5 + Math.sin(angle) * region.radiusZ);
            }

            for (int cap = 0; cap < 2; cap++) {
                double y = cap == 0 ? minY : maxY;

                for (int i = 0; i < capCount; i++) {
                    double r = Math.sqrt((i + 0.5) / capCount);
                    double angle = goldenAngle * i;
                    addCloudPoint(cache, region, dimensionIdForBiome, mutable,
                        region.px + 0.5 + Math.cos(angle) * r * region.radiusX,
                        y,
                        region.pz + 0.5 + Math.sin(angle) * r * region.radiusZ);
                }
            }
        } else {
            for (int i = 0; i < count; i++) {
                double yFrac = 1 - (i / (double) Math.max(count - 1, 1)) * 2;
                double radiusAtY = Math.sqrt(Math.max(0, 1 - yFrac * yFrac));
                double angle = goldenAngle * i;

                addCloudPoint(cache, region, dimensionIdForBiome, mutable,
                    region.px + 0.5 + Math.cos(angle) * radiusAtY * region.radiusX,
                    region.py + 0.5 + yFrac * region.radiusY,
                    region.pz + 0.5 + Math.sin(angle) * radiusAtY * region.radiusZ);
            }
        }

        return cache;
    }

    private void addCloudPoint(PointCloudCache cache, AmbienceRegion region, String dimensionId, BlockPos.Mutable mutable, double x, double y, double z) {
        mutable.set(MathHelper.floor(x), MathHelper.floor(y), MathHelper.floor(z));

        String biomeId = region.biome.isEmpty() ? "" : cachedBiomeId(dimensionId, mutable);
        if (region.contains(dimensionId, mutable, biomeId)) cache.points.add(new double[]{x, y, z});
    }

    // ===== Probe: raycast along your look direction, mark the first real boundary crossing =====

    private void drawPointRegionProbe(Renderer3D r, AmbienceRegion region, String dimensionId, Color line) {
        if (mc.player == null || mc.world == null) return;

        Vec3d camPos = mc.player.getCameraPosVec(1f);
        Vec3d look = mc.player.getRotationVec(1f);
        int maxDistance = visualizeProbeMaxDistance.get();

        BlockPos.Mutable mutable = new BlockPos.Mutable();
        Boolean lastState = null;

        int steps = maxDistance * 2;
        for (int i = 0; i <= steps; i++) {
            double t = i * 0.5;
            double x = camPos.x + look.x * t, y = camPos.y + look.y * t, z = camPos.z + look.z * t;
            mutable.set(MathHelper.floor(x), MathHelper.floor(y), MathHelper.floor(z));

            String biomeId = region.biome.isEmpty() ? "" : cachedBiomeId(dimensionId, mutable);
            boolean state = region.contains(dimensionId, mutable, biomeId);

            if (lastState != null && state != lastState) {
                drawOriginMarker(r, mutable.getX() + 0.5, mutable.getY() + 0.5, mutable.getZ() + 0.5, line);
                return;
            }

            lastState = state;
        }
    }

    private void drawBoxRegion(Renderer3D r, AmbienceRegion region, Color side, Color line, ShapeMode mode) {
        if (region.yOnly) {
            // X/Z don't matter for this region - a true-to-bounds box would span the whole world
            // width, so just band the two Y bounds across a reasonable area around the player
            // instead of trying to draw the (undefined) horizontal extent.
            if (mc.player == null) return;

            double half = 24;
            double px = mc.player.getX(), pz = mc.player.getZ();
            double minY = Math.min(region.y1, region.y2);
            double maxY = Math.max(region.y1, region.y2) + 1;

            drawFlatSquareOutline(r, px - half, minY, pz - half, px + half, pz + half, line);
            drawFlatSquareOutline(r, px - half, maxY, pz - half, px + half, pz + half, line);
            return;
        }

        double minX = Math.min(region.x1, region.x2);
        double minY = Math.min(region.y1, region.y2);
        double minZ = Math.min(region.z1, region.z2);
        double maxX = Math.max(region.x1, region.x2) + 1;
        double maxY = Math.max(region.y1, region.y2) + 1;
        double maxZ = Math.max(region.z1, region.z2) + 1;

        r.box(minX, minY, minZ, maxX, maxY, maxZ, side, line, mode, 0);

        // The box outline alone doesn't tell you which exact block each corner setting is at
        // (minX/minY/minZ above are already sorted, not necessarily x1/y1/z1) - mark both corners
        // at their own real coordinates so they're identifiable at a glance.
        drawOriginMarker(r, region.x1 + 0.5, region.y1 + 0.5, region.z1 + 0.5, line);
        drawOriginMarker(r, region.x2 + 0.5, region.y2 + 0.5, region.z2 + 0.5, line);
    }

    private void drawFlatSquareOutline(Renderer3D r, double minX, double y, double minZ, double maxX, double maxZ, Color color) {
        r.line(minX, y, minZ, maxX, y, minZ, color);
        r.line(maxX, y, minZ, maxX, y, maxZ, color);
        r.line(maxX, y, maxZ, minX, y, maxZ, color);
        r.line(minX, y, maxZ, minX, y, minZ, color);
    }

    /** A small 3-axis jack marking one exact point - the sphere/cylinder/box outlines show the region's extent, but not precisely where its own origin coordinate sits within that. */
    private void drawOriginMarker(Renderer3D r, double x, double y, double z, Color color) {
        // Was 0.5 (a 1-block-wide cross) - barely visible at any real distance, especially next
        // to a region that might span hundreds of blocks. 1.5 reads clearly from much further away
        // while still being obviously a point marker, not a shape of its own.
        double s = 1.5;
        r.line(x - s, y, z, x + s, y, z, color);
        r.line(x, y - s, z, x, y + s, z, color);
        r.line(x, y, z - s, x, y, z + s, color);
    }

    private interface RingPoint {
        double[] at(double angle);
    }

    private static final int REGION_WIRE_SEGMENTS = 32;

    private void drawRing(Renderer3D r, RingPoint fn, Color color) {
        double[] prev = fn.at(0);
        for (int i = 1; i <= REGION_WIRE_SEGMENTS; i++) {
            double angle = (Math.PI * 2 * i) / REGION_WIRE_SEGMENTS;
            double[] cur = fn.at(angle);
            r.line(prev[0], prev[1], prev[2], cur[0], cur[1], cur[2], color);
            prev = cur;
        }
    }

    private void drawPointRegion(Renderer3D r, AmbienceRegion region, Color line) {
        double cx = region.px + 0.5, cy = region.py + 0.5, cz = region.pz + 0.5;
        double rx = region.radiusX, ry = region.radiusY, rz = region.radiusZ;

        drawOriginMarker(r, cx, cy, cz, line);

        if (region.pointShape == AmbienceRegion.PointShape.Cylinder) {
            double bottomY = region.effectiveMinY(), topY = region.effectiveMaxY() + 1.0;

            drawRing(r, a -> new double[]{cx + Math.cos(a) * rx, bottomY, cz + Math.sin(a) * rz}, line);
            drawRing(r, a -> new double[]{cx + Math.cos(a) * rx, topY, cz + Math.sin(a) * rz}, line);

            int struts = 8;
            for (int i = 0; i < struts; i++) {
                double angle = (Math.PI * 2 * i) / struts;
                double x = cx + Math.cos(angle) * rx;
                double z = cz + Math.sin(angle) * rz;
                r.line(x, bottomY, z, x, topY, z, line);
            }
        } else {
            drawRing(r, a -> new double[]{cx + Math.cos(a) * rx, cy, cz + Math.sin(a) * rz}, line);
            drawRing(r, a -> new double[]{cx + Math.cos(a) * rx, cy + Math.sin(a) * ry, cz}, line);
            drawRing(r, a -> new double[]{cx, cy + Math.sin(a) * ry, cz + Math.cos(a) * rz}, line);
        }
    }

    // currentSeasonColors()/effectiveXColor() above are pure, stateless lookups re-evaluated live on
    // every render/color-provider call - nothing here needs to push a value into any setting
    // anymore. The one thing still needed on a day change (or Auto Season being flipped) is a
    // reload(), the same as every other color setting already triggers on its own onChanged, since
    // block colors specifically are baked into Sodium's chunk meshes rather than read live like
    // sky/cloud/lightning/lava are - without it, a mesh built under yesterday's (or no) season just
    // keeps showing it until something else happens to force a re-mesh.
    private void checkAutoSeason() {
        reload();
    }

    private String currentSeasonName() {
        int month = LocalDate.now().getMonthValue();

        String season = switch (month) {
            case 12, 1, 2 -> "Winter";
            case 3, 4, 5 -> "Spring";
            case 6, 7, 8 -> "Summer";
            default -> "Fall";
        };

        if (!southernHemisphere.get()) return season;

        return switch (season) {
            case "Winter" -> "Summer";
            case "Summer" -> "Winter";
            case "Spring" -> "Fall";
            default -> "Spring";
        };
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

    // Whether this block has been explicitly added anywhere (a Region, a biome, or the global list) -
    // used by SodiumColorProviderRegistryMixin to decide whether a block that isn't naturally
    // biome-tinted still needs an AmbienceBlockColorProvider wrapper even while Allow Untinted
    // Blocks is off. Without this, a block added ONLY to a Region's own list (the Region's own block
    // picker never required that global toggle in the first place, see BlockColorMapScreen's own
    // Show All Blocks) silently never rendered at all - Sodium never even asked for a color at that
    // block, since nothing told it this specific block type might need one.
    public boolean isBlockConfiguredAnywhere(Block block) {
        if (globalBlockColors.get().containsKey(block)) return true;

        for (Map<Block, List<WeightedColorEntry>> map : biomeBlockColors.get().values()) {
            if (map.containsKey(block)) return true;
        }

        for (AmbienceRegion region : regions.get()) {
            if (region.blocks.containsKey(block)) return true;
        }

        for (AmbienceSeasonColors season : List.of(springColors.get(), summerColors.get(), autumnColors.get(), winterColors.get())) {
            if (season.blocks.containsKey(block)) return true;
        }

        return false;
    }

    private static String biomeId(RegistryEntry<Biome> biomeEntry) {
        if (biomeEntry == null) return "";

        Optional<RegistryKey<Biome>> key = biomeEntry.getKey();
        return key.map(registryKey -> registryKey.getValue().toString()).orElse("");
    }

    // Biome at a given position never changes while you're in the same world - the region
    // visualizer's own rescans kept re-querying it from scratch every time regardless, which is
    // the expensive part of scanning a biome-restricted region (plain geometry checks alone are
    // cheap; a world.getBiome() call per cell is not). Cached forever per exact position instead
    // of re-queried every rescan - shared across every region and mode, since the real-world
    // answer for a given spot is the same no matter who's asking. Keyed by dimension + position -
    // the same X/Z in the Nether and the Overworld are different places with different biomes.
    private final Map<String, String> biomeIdCache = new HashMap<>();

    @EventHandler
    private void onGameJoined(GameJoinedEvent event) {
        biomeIdCache.clear();
    }

    @EventHandler
    private void onKey(KeyEvent event) {
        if (event.action != KeyAction.Press) return;
        if (!visualizeRegionsKey.get().isSet() || !visualizeRegionsKey.get().matches(true, event.key, event.modifiers)) return;

        visualizeRegions.set(!visualizeRegions.get());
    }

    private String cachedBiomeId(String dimensionId, BlockPos pos) {
        String key = dimensionId + ":" + pos.asLong();
        String cached = biomeIdCache.get(key);
        if (cached != null) return cached;

        String id = biomeId(mc.world.getBiome(pos));
        biomeIdCache.put(key, id);
        return id;
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

    // Global block overrides - not tied to any biome/region.

    public List<WeightedColorEntry> getGlobalBlockColors(Block block) {
        return lookupColors(globalBlockColors.get(), block, this::isGlobalBlockEnabled);
    }

    public SettingColor getGlobalBlockColor(Block block, BlockPos pos) {
        List<WeightedColorEntry> colors = getGlobalBlockColors(block);
        if (colors == null) return null;

        return pickWeightedEntry(colors, pos).color;
    }

    public boolean isGlobalBlockEnabled(Block block) {
        return !disabledGlobalBlocks.get().contains(Registries.BLOCK.getId(block).toString());
    }

    public void setGlobalBlockEnabled(Block block, boolean enabled) {
        String id = Registries.BLOCK.getId(block).toString();
        if (enabled) disabledGlobalBlocks.get().remove(id);
        else if (!disabledGlobalBlocks.get().contains(id)) disabledGlobalBlocks.get().add(id);

        disabledGlobalBlocks.onChanged();
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
