/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.render;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringListSetting;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

import org.joml.Matrix4f;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Plays a first-person animation on the item in your main hand when you right-click it or press the drop key
 * (hotbar slot 1 by default). Every trigger has a built-in default preset; it can be changed per item in the settings,
 * or replaced entirely from a resource pack:
 *
 *   assets/vlplus/animations/default.json                 - used for every item without a more specific file
 *   assets/vlplus/animations/<item>.json                  - e.g. diamond_sword.json (other mods' items: <namespace>/<item>.json)
 *
 * A file holds either one animation (used for right-click and drop key), or per-ability sections
 * {"use": {...}, "sneak_use": {...}, "drop": {...}, "sneak_drop": {...}} - the four ways a weapon's abilities fire.
 * Weapon files can also be named after the weapon's display name: name/<lowercase_name_with_underscores>.json. An animation is
 *   { "duration": 350, "keyframes": [ {"t":0, "pos":[x,y,z], "rot":[x,y,z], "scale":1, "ease":"out"}, ... ] }
 * or just { "preset": "Slash", "duration": 400 } to reuse a built-in. "t" runs 0..1 over the duration, pos is in blocks
 * (x right, y up, -z forward), rot in degrees, ease is linear / in / out / inout for the segment ending at that key.
 */
public class Animations extends Module {
    /** The four ways an ability is fired: right-click, or drop key, each also while sneaking. */
    public enum Trigger {
        Use("use", ""), SneakUse("sneak_use", ":sneak"), Drop("drop", ":drop"), SneakDrop("sneak_drop", ":sneak-drop");

        final String section, suffix;

        Trigger(String section, String suffix) { this.section = section; this.suffix = suffix; }
    }

    public enum Preset { Off, Thrust, Slash, Spin, Recoil, Pulse, Flourish, Missile }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgTriggers = settings.createGroup("Triggers");
    private final SettingGroup sgPack = settings.createGroup("Resource Pack");

    private final Setting<String> slots = sgGeneral.add(new StringSetting.Builder()
        .name("hotbar-slots")
        .description("Which hotbar slots animate, as numbers 1-9 separated by commas (\"1\" = the first slot). \"all\" for every slot.")
        .defaultValue("1")
        .build()
    );

    private final Setting<Integer> duration = sgGeneral.add(new IntSetting.Builder()
        .name("duration")
        .description("Default length of a built-in animation, in milliseconds.")
        .defaultValue(350)
        .range(50, 3000)
        .sliderRange(100, 1500)
        .build()
    );

    private final Setting<Double> intensity = sgGeneral.add(new DoubleSetting.Builder()
        .name("intensity")
        .description("Scales how far every animation moves and turns (built-in and resource pack). 1 = as authored.")
        .defaultValue(1.0)
        .min(0)
        .sliderRange(0, 3)
        .build()
    );

    private final Setting<Boolean> restart = sgGeneral.add(new BoolSetting.Builder()
        .name("restart-while-playing")
        .description("Starts the animation over if you trigger it again before it finished. Off: extra triggers are ignored until it ends.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> onUse = sgTriggers.add(new BoolSetting.Builder()
        .name("on-right-click")
        .description("Animate when you right-click.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Preset> usePreset = sgTriggers.add(new EnumSetting.Builder<Preset>()
        .name("right-click-animation")
        .description("Default animation for right-click.")
        .defaultValue(Preset.Thrust)
        .visible(onUse::get)
        .build()
    );

    private final Setting<Boolean> onDrop = sgTriggers.add(new BoolSetting.Builder()
        .name("on-drop-key")
        .description("Animate when you press the drop key.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Preset> dropPreset = sgTriggers.add(new EnumSetting.Builder<Preset>()
        .name("drop-key-animation")
        .description("Default animation for the drop key.")
        .defaultValue(Preset.Flourish)
        .visible(onDrop::get)
        .build()
    );

    private final Setting<Boolean> onSneakUse = sgTriggers.add(new BoolSetting.Builder()
        .name("on-sneak-right-click")
        .description("Animate when you right-click while sneaking (a separate ability from plain right-click).")
        .defaultValue(true)
        .build()
    );

    private final Setting<Preset> sneakUsePreset = sgTriggers.add(new EnumSetting.Builder<Preset>()
        .name("sneak-right-click-animation")
        .description("Default animation for sneak + right-click.")
        .defaultValue(Preset.Slash)
        .visible(onSneakUse::get)
        .build()
    );

    private final Setting<Boolean> onSneakDrop = sgTriggers.add(new BoolSetting.Builder()
        .name("on-sneak-drop-key")
        .description("Animate when you press the drop key while sneaking (a separate ability from plain drop key).")
        .defaultValue(true)
        .build()
    );

    private final Setting<Preset> sneakDropPreset = sgTriggers.add(new EnumSetting.Builder<Preset>()
        .name("sneak-drop-key-animation")
        .description("Default animation for sneak + drop key.")
        .defaultValue(Preset.Spin)
        .visible(onSneakDrop::get)
        .build()
    );

    private final Setting<Boolean> skipContainerInteract = sgTriggers.add(new BoolSetting.Builder()
        .name("skip-container-interact")
        .description("Doesn't animate a right-click that's actually opening a screen instead of using the item - villagers/other merchants, chests, trapped chests, ender chests, barrels, shulker boxes.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> stopVanillaSwing = sgTriggers.add(new BoolSetting.Builder()
        .name("stop-vanilla-swing")
        .description("Cancels vanilla's own arm-swing animation while one of these is playing, so it doesn't fight the custom one. Global - applies to every trigger's animation, not configurable per trigger yet.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> stopWorldBob = sgTriggers.add(new BoolSetting.Builder()
        .name("stop-world-bob")
        .description("Cancels the world/camera sway from Options > Bobbing while one of these is playing (same mechanism as the standalone Item Bobbing module - having both on doesn't double up, and this works even if that module is off).")
        .defaultValue(false)
        .build()
    );

    private final Setting<List<String>> overrides = sgTriggers.add(new StringListSetting.Builder()
        .name("item-overrides")
        .description("Per weapon AND per ability, one \"weapon+ability=Preset\" per line. The weapon is an item id (minecraft:bow) or part of its display name (name:Doom Launcher). Add the ability as a suffix: none = right-click, :sneak = sneak + right-click, :drop = drop key, :sneak-drop = sneak + drop key. E.g. \"name:Doom Launcher=Missile\", \"name:Doom Launcher:drop=Spin\", \"minecraft:bow:sneak-drop=Off\". Preset Off disables that ability.")
        .defaultValue(new ArrayList<>())
        .build()
    );

    private final Setting<SettingColor> holoColor = sgTriggers.add(new ColorSetting.Builder()
        .name("hologram-color")
        .description("Tint of the hologram missile (the Missile animation / \"effect\": \"missile\").")
        .defaultValue(new SettingColor(70, 210, 255, 170))
        .build()
    );

    private final Setting<Double> holoTravel = sgTriggers.add(new DoubleSetting.Builder()
        .name("hologram-travel")
        .description("How far, in blocks, the hologram missile flies away from you.")
        .defaultValue(5.0)
        .min(0.5)
        .sliderRange(1, 15)
        .build()
    );

    private final Setting<Double> holoSize = sgTriggers.add(new DoubleSetting.Builder()
        .name("hologram-size")
        .description("Size multiplier of the hologram missile.")
        .defaultValue(1.0)
        .min(0.2)
        .sliderRange(0.3, 3)
        .build()
    );

    private final Setting<Boolean> usePack = sgPack.add(new BoolSetting.Builder()
        .name("use-resource-pack")
        .description("Look for animation files in resource packs (assets/vlplus/animations/) before the built-in and per-item settings.")
        .defaultValue(true)
        .build()
    );

    // --- animation model ---

    private record Key(double t, double px, double py, double pz, double rx, double ry, double rz, double scale, String ease) {}

    private record Anim(double durationMs, List<Key> keys, String effect) {}

    private static Key k(double t, double px, double py, double pz, double rx, double ry, double rz, double scale, String ease) {
        return new Key(t, px, py, pz, rx, ry, rz, scale, ease);
    }

    private static Anim preset(Preset preset, double ms) {
        List<Key> keys = new ArrayList<>();
        keys.add(k(0, 0, 0, 0, 0, 0, 0, 1, "out"));

        switch (preset) {
            case Thrust -> {
                keys.add(k(0.25, 0.02, -0.03, 0.10, 8, 0, 0, 1, "out"));
                keys.add(k(0.55, -0.02, 0.02, -0.38, -14, 0, 0, 1, "out"));
            }
            case Slash -> {
                keys.add(k(0.22, 0.12, 0.10, 0.02, 10, 12, 28, 1, "out"));
                keys.add(k(0.62, -0.28, -0.12, -0.12, -28, -22, -72, 1, "out"));
            }
            case Spin -> keys.add(k(0.6, 0, 0.04, -0.1, 0, 0, 360, 1, "inout"));
            case Recoil -> {
                keys.add(k(0.18, 0, 0.05, 0.28, -22, 0, 0, 1, "out"));
                keys.add(k(0.5, 0, 0.02, 0.08, -6, 0, 0, 1, "out"));
            }
            case Pulse -> {
                keys.add(k(0.3, 0, 0, -0.05, 0, 0, 0, 1.22, "out"));
                keys.add(k(0.6, 0, 0, 0, 0, 0, 0, 0.96, "inout"));
            }
            case Missile -> {
                // Launcher kick: item snaps back as the hologram missile leaves, then settles.
                keys.add(k(0.15, 0.0, 0.03, 0.22, -18, 0, 0, 1, "out"));
                keys.add(k(0.45, 0.0, 0.01, 0.06, -5, 0, 0, 1, "out"));
            }
            case Flourish -> {
                keys.add(k(0.35, 0.05, 0.22, -0.1, -40, 0, 180, 1.05, "out"));
                keys.add(k(0.75, 0, 0.06, -0.05, -10, 0, 360, 1.0, "inout"));
            }
            default -> {}
        }

        keys.add(k(1, 0, 0, 0, 0, 0, 0, 1, "inout"));
        return new Anim(ms, keys, preset == Preset.Missile ? "missile" : null);
    }

    // --- state ---

    private Anim current;
    private long startedAt;
    private boolean usePrev, dropPrev;

    private ResourceManager packManager;
    private final Map<String, Optional<JsonObject>> packCache = new HashMap<>();

    public Animations() {
        super(Categories.Camera, "animations", "Animates the item in your hand (hotbar slot 1 by default) when you right-click or use the drop key. Change the animation per item in the settings or with a resource pack (assets/vlplus/animations/).", "weapon-animations", "ability-animation", "missile");
    }

    @Override
    public void onDeactivate() {
        current = null;
    }

    /** True while the crosshair is on something a plain right-click would open a screen with instead of using the held item. */
    private boolean targetingContainer() {
        net.minecraft.util.hit.HitResult target = mc.crosshairTarget;
        if (target == null) return false;

        if (target instanceof net.minecraft.util.hit.EntityHitResult entityHit) {
            return entityHit.getEntity() instanceof net.minecraft.village.Merchant;
        }

        if (target instanceof net.minecraft.util.hit.BlockHitResult blockHit && mc.world != null) {
            net.minecraft.block.entity.BlockEntity be = mc.world.getBlockEntity(blockHit.getBlockPos());
            return be instanceof net.minecraft.block.entity.LockableContainerBlockEntity
                || be instanceof net.minecraft.block.entity.ChestBlockEntity
                || be instanceof net.minecraft.block.entity.EnderChestBlockEntity
                || be instanceof net.minecraft.block.entity.BarrelBlockEntity
                || be instanceof net.minecraft.block.entity.ShulkerBoxBlockEntity;
        }

        return false;
    }

    private boolean slotEnabled(int index) {
        String text = slots.get().trim().toLowerCase(Locale.ROOT);
        if (text.equals("all")) return true;

        for (String part : text.split("[,\\s]+")) {
            try {
                if (Integer.parseInt(part) - 1 == index) return true;
            } catch (NumberFormatException ignored) {}
        }

        return false;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null) return;

        boolean usePressed = mc.options.useKey.isPressed();
        boolean dropPressed = mc.options.dropKey.isPressed();

        boolean useEdge = usePressed && !usePrev;
        boolean dropEdge = dropPressed && !dropPrev;
        usePrev = usePressed;
        dropPrev = dropPressed;

        if (mc.currentScreen != null) return;
        if (!useEdge && !dropEdge) return;
        if (useEdge && skipContainerInteract.get() && targetingContainer()) return;
        if (!slotEnabled(mc.player.getInventory().selectedSlot)) return;

        ItemStack stack = mc.player.getMainHandStack();
        if (stack.isEmpty()) return;

        boolean sneak = mc.player.isSneaking();

        Trigger trigger;
        if (useEdge) trigger = sneak ? Trigger.SneakUse : Trigger.Use;
        else trigger = sneak ? Trigger.SneakDrop : Trigger.Drop;

        boolean enabled = switch (trigger) {
            case Use -> onUse.get();
            case SneakUse -> onSneakUse.get();
            case Drop -> onDrop.get();
            case SneakDrop -> onSneakDrop.get();
        };

        if (enabled) start(stack, trigger);
    }

    private void start(ItemStack stack, Trigger trigger) {
        if (current != null && !restart.get() && !finished()) return;

        Anim anim = resolve(stack, trigger);
        if (anim == null) return;

        current = anim;
        startedAt = System.nanoTime();
    }

    private boolean finished() {
        return current == null || (System.nanoTime() - startedAt) / 1_000_000.0 >= current.durationMs();
    }

    // --- resolving what to play ---

    /** Lower-cased display name without §-formatting codes. */
    private static String ChatFormatting(String text) {
        return text.replaceAll("§.", "").toLowerCase(Locale.ROOT).trim();
    }

    private Anim resolve(ItemStack stack, Trigger trigger) {
        Identifier item = Registries.ITEM.getId(stack.getItem());
        String name = ChatFormatting(stack.getName().getString());

        if (usePack.get()) {
            Anim fromPack = fromPack(item, name, trigger);
            if (fromPack != null) return fromPack;
        }

        Preset chosen = switch (trigger) {
            case Use -> usePreset.get();
            case SneakUse -> sneakUsePreset.get();
            case Drop -> dropPreset.get();
            case SneakDrop -> sneakDropPreset.get();
        };

        for (String line : overrides.get()) {
            int eq = line.indexOf('=');
            if (eq < 0) continue;

            String key = line.substring(0, eq).trim().toLowerCase(Locale.ROOT);

            // Ability suffix (longest first so ":sneak-drop" isn't read as ":drop").
            Trigger keyTrigger = Trigger.Use;
            for (Trigger t : new Trigger[]{Trigger.SneakDrop, Trigger.SneakUse, Trigger.Drop}) {
                if (key.endsWith(t.suffix)) { keyTrigger = t; key = key.substring(0, key.length() - t.suffix.length()); break; }
            }

            boolean matches = key.startsWith("name:") ? name.contains(key.substring(5).trim()) : key.equals(item.toString().toLowerCase(Locale.ROOT));
            if (!matches || keyTrigger != trigger) continue;

            for (Preset p : Preset.values()) if (p.name().equalsIgnoreCase(line.substring(eq + 1).trim())) chosen = p;
        }

        if (chosen == Preset.Off) return null;
        return preset(chosen, duration.get());
    }

    private Anim fromPack(Identifier item, String displayName, Trigger trigger) {
        ResourceManager manager = mc.getResourceManager();
        if (manager != packManager) { packManager = manager; packCache.clear(); }

        String itemPath = item.getNamespace().equals("minecraft") ? item.getPath() : item.getNamespace() + "/" + item.getPath();

        // Most specific first: the ability/weapon's display name (assets/vlplus/animations/name/<name_with_underscores>.json),
        // then the item id, then default.
        String slug = displayName.replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        for (String name : new String[]{"name/" + slug, itemPath, "default"}) {
            if (name.equals("name/")) continue;
            JsonObject json = packCache.computeIfAbsent(name, n -> load(manager, n)).orElse(null);
            if (json == null) continue;

            // Per-ability sections; a file with none of them is one animation shared by the plain (non-sneak) triggers.
            JsonElement section;
            if (json.has(trigger.section)) section = json.get(trigger.section);
            else if (trigger == Trigger.Use || trigger == Trigger.Drop) section = json;
            else continue;
            if (!section.isJsonObject()) continue;

            Anim anim = parse(section.getAsJsonObject());
            if (anim != null) return anim;
        }

        return null;
    }

    private static Optional<JsonObject> load(ResourceManager manager, String name) {
        Optional<Resource> resource = manager.getResource(Identifier.of("vlplus", "animations/" + name + ".json"));
        if (resource.isEmpty()) return Optional.empty();

        try (BufferedReader reader = resource.get().getReader()) {
            JsonElement root = JsonParser.parseReader(reader);
            return root.isJsonObject() ? Optional.of(root.getAsJsonObject()) : Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private Anim parse(JsonObject json) {
        double ms = json.has("duration") ? json.get("duration").getAsDouble() : duration.get();

        if (json.has("preset")) {
            for (Preset p : Preset.values()) {
                if (p.name().equalsIgnoreCase(json.get("preset").getAsString())) return p == Preset.Off ? null : preset(p, ms);
            }
            return null;
        }

        if (!json.has("keyframes") || !json.get("keyframes").isJsonArray()) return null;

        List<Key> keys = new ArrayList<>();
        for (JsonElement el : json.getAsJsonArray("keyframes")) {
            if (!el.isJsonObject()) continue;
            JsonObject o = el.getAsJsonObject();

            double[] pos = vec(o, "pos", 0), rot = vec(o, "rot", 0);
            keys.add(k(o.has("t") ? o.get("t").getAsDouble() : 0, pos[0], pos[1], pos[2], rot[0], rot[1], rot[2],
                o.has("scale") ? o.get("scale").getAsDouble() : 1, o.has("ease") ? o.get("ease").getAsString() : "inout"));
        }

        if (keys.size() < 2) return null;
        keys.sort((a, b) -> Double.compare(a.t(), b.t()));
        return new Anim(ms, keys, json.has("effect") ? json.get("effect").getAsString() : null);
    }

    private static double[] vec(JsonObject o, String name, double def) {
        double[] out = {def, def, def};
        if (!o.has(name) || !o.get(name).isJsonArray()) return out;

        JsonArray arr = o.getAsJsonArray(name);
        for (int i = 0; i < 3 && i < arr.size(); i++) out[i] = arr.get(i).getAsDouble();
        return out;
    }

    /** True while a custom animation is actively playing on the main hand and it should suppress vanilla's own arm-swing. */
    public boolean shouldStopVanillaSwing() {
        return isActive() && current != null && stopVanillaSwing.get();
    }

    /** True while a custom animation is actively playing and it should cancel the world/camera bob (same mechanism as ItemBobbing). */
    public boolean shouldStopWorldBob() {
        return isActive() && current != null && stopWorldBob.get();
    }

    // --- applying ---

    private static double ease(double x, String mode) {
        return switch (mode) {
            case "linear" -> x;
            case "in" -> x * x;
            case "out" -> 1 - (1 - x) * (1 - x);
            default -> x * x * (3 - 2 * x);
        };
    }

    /** Called by HeldItemRendererMixin at the start of rendering the first-person item; matrices are already pushed by the caller. */
    public void apply(MatrixStack matrices, Hand hand, VertexConsumerProvider vertexConsumers) {
        if (hand != Hand.MAIN_HAND || current == null) return;

        double t = (System.nanoTime() - startedAt) / 1_000_000.0 / current.durationMs();
        if (t >= 1) { current = null; return; }

        // Drawn before the item's own animation transform below, so the hologram flies straight ahead of you
        // instead of spinning/scaling with the item.
        if ("missile".equalsIgnoreCase(current.effect())) drawMissile(matrices, vertexConsumers, t);

        List<Key> keys = current.keys();
        Key a = keys.get(0), b = keys.get(keys.size() - 1);
        for (int i = 1; i < keys.size(); i++) {
            if (t <= keys.get(i).t()) { a = keys.get(i - 1); b = keys.get(i); break; }
        }

        double span = b.t() - a.t();
        double f = span <= 1e-9 ? 1 : ease(Math.max(0, Math.min(1, (t - a.t()) / span)), b.ease());
        double s = intensity.get();

        double px = (a.px() + (b.px() - a.px()) * f) * s;
        double py = (a.py() + (b.py() - a.py()) * f) * s;
        double pz = (a.pz() + (b.pz() - a.pz()) * f) * s;
        double rx = (a.rx() + (b.rx() - a.rx()) * f) * s;
        double ry = (a.ry() + (b.ry() - a.ry()) * f) * s;
        double rz = (a.rz() + (b.rz() - a.rz()) * f) * s;
        double sc = 1 + ((a.scale() + (b.scale() - a.scale()) * f) - 1) * s;

        // Pivot near where the held item sits on screen, so turns swing the item, not the whole view.
        final double pivotX = 0.56, pivotY = -0.52, pivotZ = -0.72;

        matrices.translate(px, py, pz);
        matrices.translate(pivotX, pivotY, pivotZ);
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees((float) rx));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) ry));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) rz));
        matrices.scale((float) sc, (float) sc, (float) sc);
        matrices.translate(-pivotX, -pivotY, -pivotZ);
    }

    // --- hologram missile: boxes only, x/y across, z along the flight (nose at -z) ---

    // {minX, minY, minZ, maxX, maxY, maxZ, brightness}
    private static final double[][] MISSILE = {
        // body, three bands so it reads as segmented hologram slices
        {-0.040, -0.040, -0.06, 0.040, 0.040, 0.06, 1.0},
        {-0.040, -0.040, -0.20, 0.040, 0.040, -0.06, 0.8},
        {-0.040, -0.040, 0.06, 0.040, 0.040, 0.16, 0.9},
        // warhead ring
        {-0.046, -0.046, -0.20, 0.046, 0.046, -0.17, 1.15},
        // nose cone, stepped
        {-0.030, -0.030, -0.25, 0.030, 0.030, -0.20, 0.95},
        {-0.018, -0.018, -0.29, 0.018, 0.018, -0.25, 1.1},
        {-0.007, -0.007, -0.32, 0.007, 0.007, -0.29, 1.3},
        // fins (horizontal and vertical pairs)
        {0.040, -0.006, 0.08, 0.115, 0.006, 0.17, 0.85},
        {-0.115, -0.006, 0.08, -0.040, 0.006, 0.17, 0.85},
        {-0.006, 0.040, 0.08, 0.006, 0.115, 0.17, 0.85},
        {-0.006, -0.115, 0.08, 0.006, -0.040, 0.17, 0.85},
        // exhaust glow
        {-0.028, -0.028, 0.16, 0.028, 0.028, 0.20, 1.4},
        {-0.016, -0.016, 0.20, 0.016, 0.016, 0.26, 1.6}
    };

    private void drawMissile(MatrixStack matrices, VertexConsumerProvider vertexConsumers, double t) {
        // Launch starts a quarter of the way in, flies for the rest.
        double flight = (t - 0.2) / 0.8;
        if (flight <= 0 || flight >= 1) return;

        double dist = holoTravel.get() * (1 - Math.pow(1 - flight, 2.2));
        double fadeIn = Math.min(1, flight / 0.12);
        double fadeOut = Math.min(1, (1 - flight) / 0.35);
        double alphaScale = fadeIn * fadeOut;

        long ms = System.currentTimeMillis();
        double flicker = 0.8 + 0.2 * Math.sin(ms * 0.05) * Math.sin(ms * 0.013 + 1.3);

        SettingColor c = holoColor.get();
        double size = holoSize.get() * (1.0 - 0.35 * flight);

        matrices.push();
        // Start beside the item and a little ahead, rise slightly as it goes.
        matrices.translate(0.42, -0.28 + 0.06 * flight, -0.85 - dist);
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees((float) (-4 * flight)));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) (flight * 40)));
        matrices.scale((float) size, (float) size, (float) size);

        VertexConsumer vc = vertexConsumers.getBuffer(RenderLayer.getDebugFilledBox());
        Matrix4f m = matrices.peek().getPositionMatrix();

        for (int i = 0; i < MISSILE.length; i++) {
            double[] b = MISSILE[i];
            // Each slice shimmers on its own phase - the "scanline" look.
            double shimmer = 0.85 + 0.15 * Math.sin(ms * 0.02 + i * 1.7);
            double bright = b[6] * shimmer;

            int r = (int) Math.min(255, c.r * bright), g = (int) Math.min(255, c.g * bright), bl = (int) Math.min(255, c.b * bright);
            int a = (int) Math.max(0, Math.min(255, c.a * flicker * alphaScale * Math.min(1.2, 0.6 + bright * 0.4)));

            box(vc, m, (float) b[0], (float) b[1], (float) b[2], (float) b[3], (float) b[4], (float) b[5], r, g, bl, a);
        }

        matrices.pop();
    }

    private static void box(VertexConsumer vc, Matrix4f m, float x1, float y1, float z1, float x2, float y2, float z2, int r, int g, int b, int a) {
        quad(vc, m, x1, y1, z1, x2, y1, z1, x2, y2, z1, x1, y2, z1, r, g, b, a); // front (z1)
        quad(vc, m, x1, y1, z2, x1, y2, z2, x2, y2, z2, x2, y1, z2, r, g, b, a); // back (z2)
        quad(vc, m, x1, y1, z1, x1, y1, z2, x2, y1, z2, x2, y1, z1, r, g, b, a); // bottom
        quad(vc, m, x1, y2, z1, x2, y2, z1, x2, y2, z2, x1, y2, z2, r, g, b, a); // top
        quad(vc, m, x1, y1, z1, x1, y2, z1, x1, y2, z2, x1, y1, z2, r, g, b, a); // left
        quad(vc, m, x2, y1, z1, x2, y1, z2, x2, y2, z2, x2, y2, z1, r, g, b, a); // right
    }

    private static void quad(VertexConsumer vc, Matrix4f m,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz,
                             int r, int g, int b, int a) {
        vc.vertex(m, ax, ay, az).color(r, g, b, a);
        vc.vertex(m, bx, by, bz).color(r, g, b, a);
        vc.vertex(m, cx, cy, cz).color(r, g, b, a);
        vc.vertex(m, dx, dy, dz).color(r, g, b, a);
    }
}
