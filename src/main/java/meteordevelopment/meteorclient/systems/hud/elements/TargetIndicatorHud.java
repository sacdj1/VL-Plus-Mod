/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.hud.elements;

import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

import java.util.Optional;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * Only shows itself while you're actually looking at a live target within reach - a raycast from
 * your own crosshair using the same real reach attribute vanilla itself uses for entity
 * interaction (PLAYER_ENTITY_INTERACTION_RANGE), plus an optional bonus while holding a weapon
 * whose name/lore contains a configured keyword (e.g. a Scythe with extra range the server
 * doesn't reflect in that attribute itself). Hides again while the target is still flashing from
 * its last hit (LivingEntity.hurtTime > 0), not just while it's alive - so it visibly gates on
 * "you can hit it again right now", not just "you're looking at something".
 */
public class TargetIndicatorHud extends HudElement {

    @Override
    public boolean hasOwnBackground() {
        return true;
    }

    public static final HudElementInfo<TargetIndicatorHud> INFO = new HudElementInfo<>(Hud.GROUP, "target-indicator", "Shows a symbol only while you're able to hit something - it's within reach/line of sight AND not still on its post-hit cooldown.", TargetIndicatorHud::new);

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<String> symbol = sgGeneral.add(new StringSetting.Builder()
        .name("symbol")
        .description("The symbol shown while you can hit the target - any text works, but this is meant for a single short symbol/character, e.g. a crosshair-like glyph.")
        .defaultValue("⌾")
        .build()
    );

    private final Setting<Double> scale = sgGeneral.add(new DoubleSetting.Builder()
        .name("scale")
        .description("Symbol scale.")
        .defaultValue(2.0)
        .min(0.5)
        .sliderRange(0.5, 5)
        .build()
    );

    private final Setting<Boolean> shadow = sgGeneral.add(new BoolSetting.Builder()
        .name("shadow")
        .description("Renders the symbol with a shadow.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> symbolColor = sgGeneral.add(new ColorSetting.Builder()
        .name("symbol-color")
        .description("Color of the symbol.")
        .defaultValue(new SettingColor(255, 40, 40))
        .build()
    );

    private final Setting<Boolean> ignoreFriends = sgGeneral.add(new BoolSetting.Builder()
        .name("ignore-friends")
        .description("Never targets players on your Friends list.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> weaponExtraRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("weapon-extra-range")
        .description("Extra reach added on top of your real interaction range while holding a weapon whose name/lore contains the keyword below - for weapons (e.g. a Scythe) with extra range the server doesn't reflect in the actual reach attribute.")
        .defaultValue(3.0)
        .min(0)
        .sliderRange(0, 10)
        .build()
    );

    private final Setting<String> weaponKeyword = sgGeneral.add(new StringSetting.Builder()
        .name("weapon-keyword")
        .description("Text (case-insensitive, anywhere in the held item's name/lore) that grants the extra range above.")
        .defaultValue("Scythe")
        .build()
    );

    public enum LoreRangeMode {
        Replace,
        Add
    }

    private final Setting<Boolean> rangeFromLore = sgGeneral.add(new BoolSetting.Builder()
        .name("range-from-lore")
        .description("Reads the reach from the held item's own lore (a line like \"Attack Range: 4.3\") instead of guessing - works for every weapon, scythes included. Falls back to the keyword bonus below when the item has no such line.")
        .defaultValue(true)
        .build()
    );

    private final Setting<String> loreRangeLabel = sgGeneral.add(new StringSetting.Builder()
        .name("lore-range-label")
        .description("The text right before the number in the lore line (case-insensitive).")
        .defaultValue("Attack Range")
        .visible(rangeFromLore::get)
        .build()
    );

    private final Setting<LoreRangeMode> loreRangeMode = sgGeneral.add(new EnumSetting.Builder<LoreRangeMode>()
        .name("lore-range-mode")
        .description("Replace: the lore number IS the reach. Add: it's added on top of your normal interaction range.")
        .defaultValue(LoreRangeMode.Replace)
        .visible(rangeFromLore::get)
        .build()
    );

    private final Setting<Boolean> ignoreInvulnerability = sgGeneral.add(new BoolSetting.Builder()
        .name("ignore-invulnerability")
        .description("Show the symbol whenever you're looking at something in reach, even while it's still flashing from its last hit (invulnerable). Off: it hides during the post-hit cooldown.")
        .defaultValue(false)
        .build()
    );

    public TargetIndicatorHud() {
        super(INFO);
        setSize(80, 20);
    }

    private boolean hasExtraRangeWeapon() {
        if (mc.player == null) return false;

        String keyword = weaponKeyword.get();
        if (keyword.isEmpty()) return false;
        String kw = keyword.toLowerCase();

        ItemStack stack = mc.player.getMainHandStack();
        if (stack.isEmpty()) return false;

        if (stack.getName().getString().toLowerCase().contains(kw)) return true;

        LoreComponent lore = stack.get(DataComponentTypes.LORE);
        if (lore != null) {
            for (Text line : lore.lines()) {
                if (line.getString().toLowerCase().contains(kw)) return true;
            }
        }

        return false;
    }

    /** The number from the held item's "Attack Range: X" lore line, or -1 if it has none. */
    private double loreRange() {
        if (mc.player == null) return -1;

        ItemStack stack = mc.player.getMainHandStack();
        if (stack.isEmpty()) return -1;

        LoreComponent lore = stack.get(DataComponentTypes.LORE);
        if (lore == null) return -1;

        String label = loreRangeLabel.get().trim();
        if (label.isEmpty()) return -1;

        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(java.util.regex.Pattern.quote(label) + "\\s*:?\\s*(\\d+(?:[.,]\\d+)?)", java.util.regex.Pattern.CASE_INSENSITIVE);
        for (Text line : lore.lines()) {
            java.util.regex.Matcher m = pattern.matcher(line.getString().replaceAll("§.", ""));
            if (m.find()) {
                try { return Double.parseDouble(m.group(1).replace(',', '.')); } catch (NumberFormatException ignored) {}
            }
        }

        return -1;
    }

    /** Null if nothing's currently under the crosshair within reach. */
    private LivingEntity findTarget() {
        if (mc.player == null || mc.world == null) return null;

        double reach = mc.player.getAttributeValue(EntityAttributes.PLAYER_ENTITY_INTERACTION_RANGE);
        double lore = rangeFromLore.get() ? loreRange() : -1;
        if (lore > 0) reach = loreRangeMode.get() == LoreRangeMode.Add ? reach + lore : lore;
        else if (hasExtraRangeWeapon()) reach += weaponExtraRange.get();

        Vec3d start = mc.player.getCameraPosVec(1.0f);
        Vec3d look = mc.player.getRotationVec(1.0f);
        Vec3d end = start.add(look.multiply(reach));

        LivingEntity closest = null;
        double closestDistSq = reach * reach;

        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof LivingEntity living) || entity == mc.player || !entity.isAlive()) continue;
            if (entity instanceof PlayerEntity player && ignoreFriends.get() && Friends.get().isFriend(player)) continue;

            Optional<Vec3d> hit = entity.getBoundingBox().expand(0.1).raycast(start, end);
            if (hit.isEmpty()) continue;

            double distSq = start.squaredDistanceTo(hit.get());
            if (distSq < closestDistSq) {
                closestDistSq = distSq;
                closest = living;
            }
        }

        return closest;
    }

    @Override
    public void render(HudRenderer renderer) {
        String text = symbol.get();

        // Size is set every frame regardless of whether the symbol actually draws below - keeping
        // it constant (rather than shrinking to a 1x1 placeholder while hidden) means an anchored
        // position (center/right/bottom) never visibly jumps between "hidden" and "shown", since
        // the element's own bounding box never changes size, only whether it's drawn into.
        double width = renderer.textWidth(text, shadow.get(), scale.get());
        double height = renderer.textHeight(shadow.get(), scale.get());
        setSize(width, height);

        if (!isInEditor()) {
            LivingEntity target = findTarget();
            if (target == null || (!ignoreInvulnerability.get() && target.hurtTime > 0)) return;
        }

        renderer.text(text, x, y, symbolColor.get(), shadow.get(), scale.get());
    }
}
