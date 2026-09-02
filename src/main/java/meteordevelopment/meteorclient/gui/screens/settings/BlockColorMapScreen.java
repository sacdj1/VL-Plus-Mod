/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.gui.screens.settings;

import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.WindowScreen;
import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;
import meteordevelopment.meteorclient.settings.BlockColorMapNbt;
import meteordevelopment.meteorclient.settings.WeightedColorEntry;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.world.Ambience;
import meteordevelopment.meteorclient.utils.misc.Names;
import meteordevelopment.meteorclient.utils.misc.NbtUtils;
import meteordevelopment.meteorclient.utils.world.BiomeTintedBlocks;
import meteordevelopment.meteorclient.utils.world.LegacyBlocks;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class BlockColorMapScreen extends WindowScreen {
    // Sentinel "override category" blocks, shown as special rows instead of in the normal block lists.
    private static final Set<Block> OVERRIDES = Set.of(
        Ambience.OVERRIDE_ALL, Ambience.OVERRIDE_GRASS, Ambience.OVERRIDE_FOLIAGE, Ambience.OVERRIDE_WATER
    );

    private final Map<Block, List<WeightedColorEntry>> blocks;
    private final Runnable onChanged;
    private final String scopeLabel;
    private final Predicate<Block> isEnabled;
    private final BiConsumer<Block, Boolean> setEnabled;

    private WTable table;

    private String filterText = "";

    public BlockColorMapScreen(GuiTheme theme, String title, String scopeLabel, Map<Block, List<WeightedColorEntry>> blocks, Runnable onChanged, Predicate<Block> isEnabled, BiConsumer<Block, Boolean> setEnabled) {
        super(theme, title + " - Blocks");

        this.blocks = blocks;
        this.onChanged = onChanged;
        this.scopeLabel = scopeLabel;
        this.isEnabled = isEnabled;
        this.setEnabled = setEnabled;
    }

    // Some tinted blocks (fluids) have no BlockItem, so their default item icon is air.
    private static ItemStack icon(Block block) {
        Item item;
        if (block == Blocks.WATER || block == Blocks.BUBBLE_COLUMN) item = Items.WATER_BUCKET;
        else if (block == Blocks.FIRE || block == Blocks.SOUL_FIRE) item = Items.FLINT_AND_STEEL;
        else if (block == Blocks.LAVA) item = Items.LAVA_BUCKET;
        else item = block.asItem();

        return (item == Items.AIR ? Items.BARRIER : item).getDefaultStack();
    }

    private static NbtCompound colorsToTag(List<WeightedColorEntry> colors) {
        NbtCompound tag = new NbtCompound();

        NbtList list = new NbtList();
        for (WeightedColorEntry entry : colors) list.add(entry.toTag());
        tag.put("colors", list);

        return tag;
    }

    private static List<WeightedColorEntry> colorsFromTag(NbtCompound tag) {
        List<WeightedColorEntry> colors = new ArrayList<>();
        for (NbtElement e : tag.getList("colors", NbtElement.COMPOUND_TYPE)) {
            colors.add(WeightedColorEntry.fromTag((NbtCompound) e));
        }

        return colors;
    }

    // Copy/paste an entire block's color list (and blend settings) at once.
    private void copyPasteButtons(WHorizontalList list, String label, List<WeightedColorEntry> colors) {
        list.add(theme.button(GuiRenderer.COPY)).right().widget().action = () ->
            NbtUtils.toClipboard(label, colorsToTag(colors));

        list.add(theme.button(GuiRenderer.PASTE)).right().widget().action = () -> {
            NbtCompound clipboard = NbtUtils.fromClipboard(colorsToTag(colors));
            if (clipboard == null) return;

            colors.clear();
            colors.addAll(colorsFromTag(clipboard));
            onChanged.run();

            table.clear();
            initTable();
        };
    }

    private enum ViewMode { Both, ConfiguredOnly, UnconfiguredOnly }

    private ViewMode viewMode = ViewMode.Both;

    private static String viewModeLabel(ViewMode mode) {
        return switch (mode) {
            case Both -> "Showing: All Blocks";
            case ConfiguredOnly -> "Showing: Configured Only";
            case UnconfiguredOnly -> "Showing: Unconfigured Only";
        };
    }

    private static ViewMode nextViewMode(ViewMode mode) {
        return switch (mode) {
            case Both -> ViewMode.ConfiguredOnly;
            case ConfiguredOnly -> ViewMode.UnconfiguredOnly;
            case UnconfiguredOnly -> ViewMode.Both;
        };
    }

    @Override
    public void initWidgets() {
        WHorizontalList header = add(theme.horizontalList()).expandX().widget();

        WTextBox filter = header.add(theme.textBox("")).minWidth(300).expandCellX().widget();
        filter.setFocused(true);
        filter.action = () -> {
            filterText = filter.get().trim();

            table.clear();
            initTable();
        };

        header.add(theme.button(viewModeLabel(viewMode))).right().widget().action = () -> {
            viewMode = nextViewMode(viewMode);

            clear();
            initWidgets();
        };

        // Copies/pastes this whole block list at once - e.g. copy from a biome's blocks and
        // paste onto a region's, or vice versa.
        header.add(theme.button(GuiRenderer.COPY)).right().widget().action = () ->
            NbtUtils.toClipboard(scopeLabel, BlockColorMapNbt.toTag(blocks));

        header.add(theme.button(GuiRenderer.PASTE)).right().widget().action = () -> {
            NbtCompound clipboard = NbtUtils.fromClipboard(BlockColorMapNbt.toTag(blocks));
            if (clipboard == null) return;

            blocks.clear();
            blocks.putAll(BlockColorMapNbt.fromTag(clipboard));
            onChanged.run();

            table.clear();
            initTable();
        };

        table = add(theme.table()).expandX().widget();

        initTable();
    }

    private void enabledCheckbox(WHorizontalList list, Block key) {
        var enabled = list.add(theme.checkbox(isEnabled.test(key))).right().widget();
        enabled.action = () -> {
            setEnabled.accept(key, enabled.checked);
            onChanged.run();
        };
    }

    private void overrideRow(Map<Block, List<WeightedColorEntry>> blocks, Block key, String label) {
        List<WeightedColorEntry> colors = blocks.get(key);

        table.add(theme.label(label)).expandCellX();

        if (colors != null) {
            WHorizontalList list = table.add(theme.horizontalList()).expandCellX().widget();

            enabledCheckbox(list, key);

            list.add(theme.button("Colors (" + colors.size() + ")")).widget().action = () -> {
                WeightedColorListScreen screen = new WeightedColorListScreen(theme, colors, label);
                screen.onClosed(() -> {
                    onChanged.run();
                    table.clear();
                    initTable();
                });

                mc.setScreen(screen);
            };

            copyPasteButtons(list, label, colors);

            list.add(theme.minus()).right().widget().action = () -> {
                blocks.remove(key);
                onChanged.run();

                table.clear();
                initTable();
            };
        } else {
            table.add(theme.plus()).expandCellX().right().widget().action = () -> {
                blocks.put(key, new ArrayList<>());
                onChanged.run();

                table.clear();
                initTable();
            };
        }

        table.row();
    }

    private void initTable() {
        boolean showConfigured = viewMode != ViewMode.UnconfiguredOnly;
        boolean showUnconfigured = viewMode != ViewMode.ConfiguredOnly;

        if (showConfigured) {
            overrideRow(blocks, Ambience.OVERRIDE_ALL, "Override Entire " + scopeLabel);
            overrideRow(blocks, Ambience.OVERRIDE_GRASS, "Override Grass Color");
            overrideRow(blocks, Ambience.OVERRIDE_FOLIAGE, "Override Foliage Color");
            overrideRow(blocks, Ambience.OVERRIDE_WATER, "Override Water Color");

            table.add(theme.label("The 3 colors above act as a base for their category - individual blocks below can still override them.")).expandX();
            table.row();
            table.add(theme.horizontalSeparator()).expandX();
            table.row();
        }

        // Selected blocks
        List<Block> selected = new ArrayList<>(blocks.keySet());
        selected.removeAll(OVERRIDES);
        selected.sort(Comparator.comparing(Names::get));

        if (showConfigured) {
            for (Block block : selected) {
                String name = Names.get(block);
                if (!StringUtils.containsIgnoreCase(name, filterText)) continue;

                int colorCount = blocks.get(block).size();

                table.add(theme.itemWithLabel(icon(block), name)).expandCellX();

                WHorizontalList list = table.add(theme.horizontalList()).expandCellX().widget();

                enabledCheckbox(list, block);

                list.add(theme.button("Colors (" + colorCount + ")")).widget().action = () -> {
                    WeightedColorListScreen screen = new WeightedColorListScreen(theme, blocks.get(block), name);
                    screen.onClosed(() -> {
                        onChanged.run();
                        table.clear();
                        initTable();
                    });

                    mc.setScreen(screen);
                };

                copyPasteButtons(list, name, blocks.get(block));

                list.add(theme.minus()).right().widget().action = () -> {
                    blocks.remove(block);
                    onChanged.run();

                    table.clear();
                    initTable();
                };

                table.row();
            }

            if (!selected.isEmpty()) table.add(theme.horizontalSeparator()).expandX();
            table.row();
        }

        if (!showUnconfigured) return;

        // Blocks not yet added - only ones Ambience can actually color, unless allow-untinted-blocks is on
        Ambience ambience = Modules.get().get(Ambience.class);
        boolean allowUntinted = ambience.allowUntintedBlocks.get();
        boolean legacyOnly = allowUntinted && ambience.legacyBlocksOnly.get();
        Iterable<Block> candidates = allowUntinted ? Registries.BLOCK : BiomeTintedBlocks.BLOCKS;

        List<Block> all = new ArrayList<>();
        for (Block block : candidates) {
            if (blocks.containsKey(block) || OVERRIDES.contains(block)) continue;
            if (legacyOnly && !LegacyBlocks.isLegacy(Registries.BLOCK.getId(block).getPath())) continue;

            String name = Names.get(block);
            if (StringUtils.containsIgnoreCase(name, filterText)) all.add(block);
        }
        all.sort(Comparator.comparing(Names::get));

        for (Block block : all) {
            table.add(theme.itemWithLabel(icon(block), Names.get(block))).expandCellX();

            table.add(theme.plus()).expandCellX().right().widget().action = () -> {
                blocks.put(block, new ArrayList<>());
                onChanged.run();

                table.clear();
                initTable();
            };

            table.row();
        }
    }
}
