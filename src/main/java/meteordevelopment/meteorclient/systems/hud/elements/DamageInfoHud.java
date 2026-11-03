/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.hud.elements;

import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * Your own damage reduction % (from total equipped Armor, all 4 slots summed) plus your main-hand
 * weapon's damage (base, buffed, or both). Self only, for now - showing this for OTHER players
 * would need reading their boot mods/Arena perks/Vulcan buff, which isn't solved yet; see the
 * conversation that led to this.
 * <p>
 * The reduction formula (provided directly, not derived/guessed): armor <= 172 uses
 * {@code armor - armor^2/360}; armor > 172 switches to {@code 100 - (20000/(armor+272)^2)*100} -
 * a diminishing-returns curve past that point rather than the quadratic continuing (which would
 * eventually curve back down past 100%). Both are already in the "shows as %" form this server
 * actually displays, not the raw multiplier form (which would be the same without the *100).
 */
public class DamageInfoHud extends HudElement {
    public static final HudElementInfo<DamageInfoHud> INFO = new HudElementInfo<>(Hud.GROUP, "damage-info", "Your damage reduction % (from total armor) and main-hand weapon damage.", DamageInfoHud::new);

    private static final EquipmentSlot[] ARMOR_SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    public enum WeaponDamageMode {
        Off,
        Separate,
        Combined
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgColors = settings.createGroup("Colors");

    private final Setting<Boolean> showReduction = sgGeneral.add(new BoolSetting.Builder()
        .name("show-damage-reduction")
        .description("Shows your total damage reduction %, from all 4 equipped armor pieces' own \"Armor: X(+Y)\" lore lines summed together.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> showReductionBar = sgGeneral.add(new BoolSetting.Builder()
        .name("show-reduction-bar")
        .description("Draws a filled bar behind the reduction % text, scaled 0-100%.")
        .defaultValue(true)
        .visible(showReduction::get)
        .build()
    );

    private final Setting<WeaponDamageMode> weaponDamageMode = sgGeneral.add(new EnumSetting.Builder<WeaponDamageMode>()
        .name("weapon-damage")
        .description("Off: hidden. Separate: base and buff shown apart, e.g. \"24-36 (+8.2/+11.9)\". Combined: pre-added into one range, e.g. \"32.2-47.9\". Read from your main-hand weapon's own \"Damage: X(+Y) - Z(+W)\" lore line.")
        .defaultValue(WeaponDamageMode.Separate)
        .build()
    );

    private final Setting<Double> scale = sgGeneral.add(new DoubleSetting.Builder()
        .name("scale")
        .description("Text/bar scale.")
        .defaultValue(1.5)
        .min(0.5)
        .sliderRange(0.5, 6)
        .build()
    );

    private final Setting<Double> barWidth = sgGeneral.add(new DoubleSetting.Builder()
        .name("bar-width")
        .description("Reduction bar width, before Scale above.")
        .defaultValue(100)
        .min(10)
        .sliderRange(10, 400)
        .visible(() -> showReduction.get() && showReductionBar.get())
        .build()
    );

    private final Setting<Double> barHeight = sgGeneral.add(new DoubleSetting.Builder()
        .name("bar-height")
        .description("Reduction bar height, before Scale above.")
        .defaultValue(10)
        .min(4)
        .sliderRange(4, 60)
        .visible(() -> showReduction.get() && showReductionBar.get())
        .build()
    );

    private final Setting<Boolean> shadow = sgGeneral.add(new BoolSetting.Builder()
        .name("shadow")
        .description("Draws a drop shadow behind the text.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> decimalPlaces = sgGeneral.add(new IntSetting.Builder()
        .name("decimal-places")
        .description("Decimal places shown on the reduction %.")
        .defaultValue(1)
        .range(0, 3)
        .sliderRange(0, 3)
        .visible(showReduction::get)
        .build()
    );

    private final Setting<String> armorPattern = sgGeneral.add(new StringSetting.Builder()
        .name("armor-pattern")
        .description("Regex with two capture groups (base, bonus - bonus optional) matched against each equipped armor piece's own lore line. Default matches \"Armor: 44(+11.0)\".")
        .defaultValue("^Armor:\\s*(\\d+)(?:\\(\\+?([\\d.]+)\\))?")
        .build()
    );

    private final Setting<Void> printArmorLore = sgGeneral.add(new ButtonSetting.Builder()
        .name("print-armor-lore")
        .description("Prints each currently equipped armor piece's lore to chat, one line per message - use this to confirm/adjust Armor Pattern above.")
        .buttonText("Print Equipped Armor Lore")
        .action(this::printArmorLore)
        .build()
    );

    private final Setting<String> weaponPattern = sgGeneral.add(new StringSetting.Builder()
        .name("weapon-damage-pattern")
        .description("Regex with four capture groups (low base, low bonus, high base, high bonus - bonuses optional) matched against your main-hand weapon's own lore line. Default matches \"Damage: 24(+8.2) - 36(+11.9)\".")
        .defaultValue("^Damage:\\s*(\\d+)(?:\\(\\+?([\\d.]+)\\))?\\s*-\\s*(\\d+)(?:\\(\\+?([\\d.]+)\\))?")
        .visible(() -> weaponDamageMode.get() != WeaponDamageMode.Off)
        .build()
    );

    private final Setting<Void> printWeaponLore = sgGeneral.add(new ButtonSetting.Builder()
        .name("print-weapon-lore")
        .description("Prints your main-hand weapon's lore to chat, one line per message - use this to confirm/adjust Weapon Damage Pattern above.")
        .buttonText("Print Weapon Lore")
        .action(this::printWeaponLore)
        .visible(() -> weaponDamageMode.get() != WeaponDamageMode.Off)
        .build()
    );

    private final Setting<SettingColor> backgroundColor = sgColors.add(new ColorSetting.Builder()
        .name("background-color")
        .description("Reduction bar's background color.")
        .defaultValue(new SettingColor(25, 25, 25, 150))
        .visible(() -> showReduction.get() && showReductionBar.get())
        .build()
    );

    private final Setting<Boolean> fillByValue = sgColors.add(new BoolSetting.Builder()
        .name("fill-by-value")
        .description("Blends the fill between Fill Low Color (0%) and Fill High Color (100%) instead of a single fixed color.")
        .defaultValue(true)
        .visible(() -> showReduction.get() && showReductionBar.get())
        .build()
    );

    private final Setting<SettingColor> fillColor = sgColors.add(new ColorSetting.Builder()
        .name("fill-color")
        .description("Reduction bar's fill color, when Fill By Value above is off.")
        .defaultValue(new SettingColor(80, 170, 255))
        .visible(() -> showReduction.get() && showReductionBar.get() && !fillByValue.get())
        .build()
    );

    private final Setting<SettingColor> fillLowColor = sgColors.add(new ColorSetting.Builder()
        .name("fill-low-color")
        .description("Fill color at 0% reduction, when Fill By Value above is on.")
        .defaultValue(new SettingColor(220, 30, 30))
        .visible(() -> showReduction.get() && showReductionBar.get() && fillByValue.get())
        .build()
    );

    private final Setting<SettingColor> fillHighColor = sgColors.add(new ColorSetting.Builder()
        .name("fill-high-color")
        .description("Fill color at 100% reduction, when Fill By Value above is on.")
        .defaultValue(new SettingColor(60, 220, 60))
        .visible(() -> showReduction.get() && showReductionBar.get() && fillByValue.get())
        .build()
    );

    private final Setting<SettingColor> textColor = sgColors.add(new ColorSetting.Builder()
        .name("text-color")
        .description("Color of all text drawn by this element.")
        .defaultValue(new SettingColor(255, 255, 255))
        .build()
    );

    public DamageInfoHud() {
        super(INFO);

        setSize(100, 40);
    }

    private double reductionPercent(double armor) {
        if (armor <= 172) return armor - (armor * armor) / 360.0;

        double denom = armor + 272;
        return 100.0 - (20000.0 / (denom * denom)) * 100.0;
    }

    private Pattern compiledArmorPattern() {
        try {
            return Pattern.compile(armorPattern.get());
        } catch (Exception e) {
            return null;
        }
    }

    private Pattern compiledWeaponPattern() {
        try {
            return Pattern.compile(weaponPattern.get());
        } catch (Exception e) {
            return null;
        }
    }

    /** Sums base+bonus from every equipped armor piece's own matching lore line, 0 for a slot with no match/no lore. */
    private double totalArmor() {
        Pattern pattern = compiledArmorPattern();
        if (pattern == null || mc.player == null) return 0;

        double total = 0;

        for (EquipmentSlot slot : ARMOR_SLOTS) {
            ItemStack stack = mc.player.getEquippedStack(slot);
            if (stack.isEmpty()) continue;

            LoreComponent lore = stack.get(DataComponentTypes.LORE);
            if (lore == null) continue;

            for (Text line : lore.lines()) {
                Matcher m = pattern.matcher(line.getString().trim());
                if (!m.find()) continue;

                total += Double.parseDouble(m.group(1));
                if (m.groupCount() >= 2 && m.group(2) != null) total += Double.parseDouble(m.group(2));

                break;
            }
        }

        return total;
    }

    private record WeaponDamage(double lowBase, double lowBonus, double highBase, double highBonus) {
    }

    private WeaponDamage findWeaponDamage() {
        Pattern pattern = compiledWeaponPattern();
        if (pattern == null || mc.player == null) return null;

        ItemStack stack = mc.player.getMainHandStack();
        if (stack.isEmpty()) return null;

        LoreComponent lore = stack.get(DataComponentTypes.LORE);
        if (lore == null) return null;

        for (Text line : lore.lines()) {
            Matcher m = pattern.matcher(line.getString().trim());
            if (!m.find() || m.groupCount() < 3) continue;

            double lowBase = Double.parseDouble(m.group(1));
            double lowBonus = m.group(2) != null ? Double.parseDouble(m.group(2)) : 0;
            double highBase = Double.parseDouble(m.group(3));
            double highBonus = m.groupCount() >= 4 && m.group(4) != null ? Double.parseDouble(m.group(4)) : 0;

            return new WeaponDamage(lowBase, lowBonus, highBase, highBonus);
        }

        return null;
    }

    private void printArmorLore() {
        if (mc.player == null) return;

        for (EquipmentSlot slot : ARMOR_SLOTS) {
            ItemStack stack = mc.player.getEquippedStack(slot);
            if (stack.isEmpty()) continue;

            ChatUtils.info("--- %s ---", slot.name());

            LoreComponent lore = stack.get(DataComponentTypes.LORE);
            if (lore == null) {
                ChatUtils.info("(no lore)");
                continue;
            }

            for (Text line : lore.lines()) ChatUtils.info("%s", line.getString());
        }
    }

    private void printWeaponLore() {
        if (mc.player == null) return;

        ItemStack stack = mc.player.getMainHandStack();
        if (stack.isEmpty()) {
            ChatUtils.info("Main hand is empty.");
            return;
        }

        LoreComponent lore = stack.get(DataComponentTypes.LORE);
        if (lore == null) {
            ChatUtils.info("(no lore)");
            return;
        }

        for (Text line : lore.lines()) ChatUtils.info("%s", line.getString());
    }

    private static Color blend(SettingColor from, SettingColor to, double t) {
        t = MathHelper.clamp(t, 0, 1);

        return new Color(
            (int) (from.r + (to.r - from.r) * t),
            (int) (from.g + (to.g - from.g) * t),
            (int) (from.b + (to.b - from.b) * t),
            (int) (from.a + (to.a - from.a) * t)
        );
    }

    @Override
    public void render(HudRenderer renderer) {
        boolean editor = isInEditor();

        double s = scale.get();
        Color text = new Color(textColor.get().r, textColor.get().g, textColor.get().b, textColor.get().a);

        double curY = y;
        double maxWidth = 0;

        if (showReduction.get()) {
            double armor = editor ? 100 : totalArmor();
            double percent = MathHelper.clamp(reductionPercent(armor), 0, 100);
            String label = String.format("%." + decimalPlaces.get() + "f%% Reduction", percent);

            if (showReductionBar.get()) {
                double w = barWidth.get() * s, h = barHeight.get() * s;

                renderer.quad(x, curY, w, h, new Color(backgroundColor.get().r, backgroundColor.get().g, backgroundColor.get().b, backgroundColor.get().a));

                Color fill = fillByValue.get() ? blend(fillLowColor.get(), fillHighColor.get(), percent / 100.0)
                    : new Color(fillColor.get().r, fillColor.get().g, fillColor.get().b, fillColor.get().a);
                renderer.quad(x, curY, w * (percent / 100.0), h, fill);

                double textScale = Math.min(s, h / (mc.textRenderer.fontHeight + 2));
                double tw = mc.textRenderer.getWidth(label) * textScale;
                renderer.text(label, x + (w - tw) / 2.0, curY + (h - mc.textRenderer.fontHeight * textScale) / 2.0, text, shadow.get(), textScale);

                curY += h + 2;
                maxWidth = Math.max(maxWidth, w);
            } else {
                double tw = renderer.text(label, x, curY, text, shadow.get(), s);
                curY += mc.textRenderer.fontHeight * s + 2;
                maxWidth = Math.max(maxWidth, tw);
            }
        }

        if (weaponDamageMode.get() != WeaponDamageMode.Off) {
            WeaponDamage dmg = editor ? new WeaponDamage(24, 8.2, 36, 11.9) : findWeaponDamage();

            if (dmg != null) {
                String label = weaponDamageMode.get() == WeaponDamageMode.Combined
                    ? String.format("%.1f - %.1f Damage", dmg.lowBase() + dmg.lowBonus(), dmg.highBase() + dmg.highBonus())
                    : String.format("%.0f-%.0f Damage (+%.1f/+%.1f)", dmg.lowBase(), dmg.highBase(), dmg.lowBonus(), dmg.highBonus());

                double tw = renderer.text(label, x, curY, text, shadow.get(), s);
                curY += mc.textRenderer.fontHeight * s + 2;
                maxWidth = Math.max(maxWidth, tw);
            } else if (editor) {
                double tw = renderer.text("No weapon held", x, curY, text, shadow.get(), s);
                curY += mc.textRenderer.fontHeight * s + 2;
                maxWidth = Math.max(maxWidth, tw);
            }
        }

        setSize(Math.max(50, maxWidth), Math.max(20, curY - y));
    }
}
