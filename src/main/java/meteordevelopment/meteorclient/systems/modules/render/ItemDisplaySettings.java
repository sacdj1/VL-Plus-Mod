/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.render;

import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.json.Transformation;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Nudges or replaces item model display transforms (translation/rotation/scale, per Minecraft's own
 * model JSON "display" block - see ModelTransformation/Transformation) at the one point every item
 * render actually applies them (ItemRendererMixin), well after any resource pack's CIT model swap
 * has already picked whichever BakedModel it's going to use - so this layers on top of that
 * regardless of which model ended up selected, CIT-picked or not.
 *
 * General applies to every "flat" item (Flat Items Only, on by default) - vanilla's generated
 * item/generated models, built by extruding a 2D texture into a paper-thin model, which is what
 * hasDepth() being false actually signals (3D block/custom models report true). Items below layer
 * an additional override on top, matched by name/lore, for singling out specific items.
 */
public class ItemDisplaySettings extends Module {
    public enum Mode {
        /** Translation/rotation added; scale multiplied (1 = unchanged) - layers on top of whatever the item's own model already specifies. */
        Add,
        /** Translation/rotation/scale replaced outright. */
        Override
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgItems = settings.createGroup("Items");

    private final Setting<Boolean> flatItemsOnly = sgGeneral.add(new BoolSetting.Builder()
        .name("flat-items-only")
        .description("Only affects \"flat\" items - vanilla's generated item/generated models, extruded from a 2D texture (hasDepth() false). Off: every item, including full 3D-modeled ones (tools, blocks-as-items, custom models).")
        .defaultValue(true)
        .build()
    );

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("Add: translation/rotation added, scale multiplied (1 = unchanged) - layers on top of the item's own model. Override: replaces translation/rotation/scale outright.")
        .defaultValue(Mode.Add)
        .build()
    );

    private final Setting<Double> translateX = sgGeneral.add(new DoubleSetting.Builder().name("translate-x").defaultValue(0).sliderRange(-8, 8).build());
    private final Setting<Double> translateY = sgGeneral.add(new DoubleSetting.Builder().name("translate-y").defaultValue(0).sliderRange(-8, 8).build());
    private final Setting<Double> translateZ = sgGeneral.add(new DoubleSetting.Builder().name("translate-z").defaultValue(0).sliderRange(-8, 8).build());

    private final Setting<Double> rotateX = sgGeneral.add(new DoubleSetting.Builder().name("rotate-x").defaultValue(0).sliderRange(-180, 180).build());
    private final Setting<Double> rotateY = sgGeneral.add(new DoubleSetting.Builder().name("rotate-y").defaultValue(0).sliderRange(-180, 180).build());
    private final Setting<Double> rotateZ = sgGeneral.add(new DoubleSetting.Builder().name("rotate-z").defaultValue(0).sliderRange(-180, 180).build());

    private final Setting<Double> scaleX = sgGeneral.add(new DoubleSetting.Builder().name("scale-x").defaultValue(1).sliderRange(-4, 4).build());
    private final Setting<Double> scaleY = sgGeneral.add(new DoubleSetting.Builder().name("scale-y").defaultValue(1).sliderRange(-4, 4).build());
    private final Setting<Double> scaleZ = sgGeneral.add(new DoubleSetting.Builder().name("scale-z").defaultValue(1).sliderRange(-4, 4).build());

    private final Setting<List<ItemDisplayOverride>> items = sgItems.add(new ItemDisplayOverrideListSetting.Builder()
        .name("item-overrides")
        .description("Per-item overrides, matched by name/lore - layered on top of General above. The first matching, enabled entry wins (not combined with others).")
        .defaultValue(new ArrayList<>())
        .build()
    );

    public ItemDisplaySettings() {
        super(Categories.Render, "item-display", "Nudges or replaces item model display transforms (translation/rotation/scale) - works alongside CIT, since it applies after whichever model a resource pack already picked.", "display-settings", "model-display", "item-transform");
    }

    public Transformation apply(ItemStack stack, BakedModel model, Transformation original) {
        if (!isActive()) return original;
        if (flatItemsOnly.get() && model.hasDepth()) return original;

        Transformation result = original;

        result = combine(result, mode.get(), translateX.get(), translateY.get(), translateZ.get(), rotateX.get(), rotateY.get(), rotateZ.get(), scaleX.get(), scaleY.get(), scaleZ.get());

        ItemDisplayOverride match = findMatch(stack);
        if (match != null) {
            result = combine(result, match.mode == ItemDisplayOverride.Mode.Add ? Mode.Add : Mode.Override,
                match.translateX, match.translateY, match.translateZ,
                match.rotateX, match.rotateY, match.rotateZ,
                match.scaleX, match.scaleY, match.scaleZ);
        }

        return result;
    }

    private ItemDisplayOverride findMatch(ItemStack stack) {
        String name = null;
        List<String> loreLines = null;

        for (ItemDisplayOverride o : items.get()) {
            if (!o.enabled) continue;

            if (!o.nameMatch.isBlank()) {
                if (name == null) name = stack.getName().getString().toLowerCase(Locale.ROOT);
                if (!name.contains(o.nameMatch.toLowerCase(Locale.ROOT))) continue;
            }

            if (!o.loreMatch.isBlank()) {
                if (loreLines == null) {
                    loreLines = new ArrayList<>();
                    LoreComponent lore = stack.get(DataComponentTypes.LORE);
                    if (lore != null) for (Text line : lore.lines()) loreLines.add(line.getString().toLowerCase(Locale.ROOT));
                }

                String needle = o.loreMatch.toLowerCase(Locale.ROOT);
                boolean found = false;
                for (String line : loreLines) if (line.contains(needle)) { found = true; break; }
                if (!found) continue;
            }

            return o;
        }

        return null;
    }

    private static Transformation combine(Transformation base, Mode mode, double tx, double ty, double tz, double rx, double ry, double rz, double sx, double sy, double sz) {
        Vector3f translation, rotation, scale;

        if (mode == Mode.Override) {
            translation = new Vector3f((float) tx, (float) ty, (float) tz);
            rotation = new Vector3f((float) rx, (float) ry, (float) rz);
            scale = new Vector3f((float) sx, (float) sy, (float) sz);
        } else {
            translation = new Vector3f(base.translation.x() + (float) tx, base.translation.y() + (float) ty, base.translation.z() + (float) tz);
            rotation = new Vector3f(base.rotation.x() + (float) rx, base.rotation.y() + (float) ry, base.rotation.z() + (float) rz);
            scale = new Vector3f(base.scale.x() * (float) sx, base.scale.y() * (float) sy, base.scale.z() * (float) sz);
        }

        return new Transformation(rotation, translation, scale);
    }
}
