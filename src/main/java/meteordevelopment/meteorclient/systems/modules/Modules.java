/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Lifecycle;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.utils.misc.VLPlusAdditions;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.game.OpenScreenEvent;
import meteordevelopment.meteorclient.events.meteor.ActiveModulesChangedEvent;
import meteordevelopment.meteorclient.events.meteor.KeyEvent;
import meteordevelopment.meteorclient.events.meteor.ModuleBindChangedEvent;
import meteordevelopment.meteorclient.events.meteor.MouseButtonEvent;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.System;
import meteordevelopment.meteorclient.systems.Systems;
import meteordevelopment.meteorclient.systems.config.Config;
import meteordevelopment.meteorclient.systems.modules.misc.*;
import meteordevelopment.meteorclient.systems.modules.player.*;
import meteordevelopment.meteorclient.systems.modules.render.*;
import meteordevelopment.meteorclient.systems.modules.world.*;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.meteorclient.utils.misc.ValueComparableMap;
import meteordevelopment.meteorclient.utils.misc.input.Input;
import meteordevelopment.meteorclient.utils.misc.input.KeyAction;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.SimpleRegistry;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.entry.RegistryEntryList;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class Modules extends System<Modules> {
    public static final ModuleRegistry REGISTRY = new ModuleRegistry();

    private static final List<Category> CATEGORIES = new ArrayList<>();

    private final List<Module> modules = new ArrayList<>();
    private final Map<Class<? extends Module>, Module> moduleInstances = new Reference2ReferenceOpenHashMap<>();
    private final Map<Category, List<Module>> groups = new Reference2ReferenceOpenHashMap<>();

    private final List<Module> active = new ArrayList<>();
    private Module moduleToBind;
    private boolean awaitingKeyRelease = false;
    private boolean devUnlocked = false;
    private boolean cheeseUnlocked = false;
    private static final String DEFAULT_ADMIN_PIN = "1337";
    private String adminPinHash = hashPin(DEFAULT_ADMIN_PIN);

    public Modules() {
        super("modules");
    }

    public static Modules get() {
        return Systems.get(Modules.class);
    }

    @Override
    public void init() {
        initPlayer();
        initRender();
        initWorld();
        initMisc();

        if (isFirstInit) {
            get(DiscordPresence.class).toggle();
            get(Collisions.class).toggle();
            get(NoRender.class).toggle();
            get(HandView.class).toggle();
            get(XPBarAdjust.class).toggle();
        }
    }

    @Override
    public void load(File folder) {
        for (Module module : modules) {
            for (SettingGroup group : module.settings) {
                for (Setting<?> setting : group) setting.reset();
            }
        }

        super.load(folder);
    }

    public void sortModules() {
        for (List<Module> modules : groups.values()) {
            modules.sort(Comparator.comparing(o -> o.title));
        }
        modules.sort(Comparator.comparing(o -> o.title));
    }

    public static void registerCategory(Category category) {
        if (!Categories.REGISTERING) throw new RuntimeException("Modules.registerCategory - Cannot register category outside of onRegisterCategories callback.");

        CATEGORIES.add(category);
    }

    public static Iterable<Category> loopCategories() {
        return CATEGORIES;
    }

    @Deprecated(forRemoval = true)
    public static Category getCategoryByHash(int hash) {
        for (Category category : CATEGORIES) {
            if (category.hashCode() == hash) return category;
        }

        return null;
    }

    @SuppressWarnings("unchecked")
    public <T extends Module> T get(Class<T> klass) {
        return (T) moduleInstances.get(klass);
    }

    public Module get(String name) {
        for (Module module : moduleInstances.values()) {
            if (module.name.equalsIgnoreCase(name)) return module;
        }

        return null;
    }

    public boolean isActive(Class<? extends Module> klass) {
        Module module = get(klass);
        return module != null && module.isActive();
    }

    public List<Module> getGroup(Category category) {
        return groups.computeIfAbsent(category, category1 -> new ArrayList<>());
    }

    public Collection<Module> getAll() {
        return moduleInstances.values();
    }

    public List<Module> getList() {
        return modules;
    }

    public int getCount() {
        return moduleInstances.values().size();
    }

    public List<Module> getActive() {
        synchronized (active) {
            return active;
        }
    }

    public Set<Module> searchTitles(String text) {
        Map<Module, Integer> modules = new ValueComparableMap<>(Comparator.naturalOrder());

        for (Module module : this.moduleInstances.values()) {
            int score = Utils.searchLevenshteinDefault(module.title, text, false);
            if (Config.get().moduleAliases.get()) {
                for (String alias : module.aliases) {
                    int aliasScore = Utils.searchLevenshteinDefault(alias, text, false);
                    if (aliasScore < score) score = aliasScore;
                }
            }
            modules.put(module, modules.getOrDefault(module, 0) + score);
        }

        return modules.keySet();
    }

    public Set<Module> searchSettingTitles(String text) {
        Map<Module, Integer> modules = new ValueComparableMap<>(Comparator.naturalOrder());

        for (Module module : this.moduleInstances.values()) {
            int lowest = Integer.MAX_VALUE;
            for (SettingGroup sg : module.settings) {
                for (Setting<?> setting : sg) {
                    int score = Utils.searchLevenshteinDefault(setting.title, text, false);
                    if (score < lowest) lowest = score;
                }
            }
            modules.put(module, modules.getOrDefault(module, 0) + lowest);
        }

        return modules.keySet();
    }

    void addActive(Module module) {
        synchronized (active) {
            if (!active.contains(module)) {
                active.add(module);
                MeteorClient.EVENT_BUS.post(ActiveModulesChangedEvent.get());
            }
        }
    }

    void removeActive(Module module) {
        synchronized (active) {
            if (active.remove(module)) {
                MeteorClient.EVENT_BUS.post(ActiveModulesChangedEvent.get());
            }
        }
    }

    // Binding

    public void setModuleToBind(Module moduleToBind) {
        this.moduleToBind = moduleToBind;
    }

    /***
     * @see meteordevelopment.meteorclient.commands.commands.BindCommand
     * For ensuring we don't instantly bind the module to the enter key.
     */
    public void awaitKeyRelease() {
        this.awaitingKeyRelease = true;
    }

    public boolean isBinding() {
        return moduleToBind != null;
    }

    public boolean isDevUnlocked() {
        return devUnlocked;
    }

    public boolean toggleDev() {
        devUnlocked = !devUnlocked;
        return devUnlocked;
    }

    public boolean isCheeseUnlocked() {
        return cheeseUnlocked;
    }

    public boolean hasAdminPin() {
        return !adminPinHash.isEmpty();
    }

    public boolean isDefaultAdminPin() {
        return checkAdminPin(DEFAULT_ADMIN_PIN);
    }

    public boolean checkAdminPin(String pin) {
        return hasAdminPin() && adminPinHash.equals(hashPin(pin));
    }

    public void setAdminPin(String pin) {
        adminPinHash = hashPin(pin);
    }

    private static String hashPin(String pin) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256").digest(("vlplus-admin:" + pin).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public boolean toggleCheese() {
        cheeseUnlocked = !cheeseUnlocked;
        return cheeseUnlocked;
    }

    /** Only disables hidden active modules OUTSIDE the Cheese category - .dev and .Admin are separate unlocks, each should only affect its own set of hidden modules. */
    public int disableHiddenActiveModules() {
        int count = 0;

        for (Module module : getAll()) {
            if (module.hidden && !module.adminOnly && module.category != Categories.Cheese && module.isActive()) {
                module.toggle();
                count++;
            }
        }

        return count;
    }

    public int disableHiddenCheeseActiveModules() {
        int count = 0;

        for (Module module : getAll()) {
            if ((module.adminOnly || (module.hidden && module.category == Categories.Cheese)) && module.isActive()) {
                module.toggle();
                count++;
            }
        }

        return count;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onKeyBinding(KeyEvent event) {
        if (event.action == KeyAction.Release && onBinding(true, event.key, event.modifiers)) event.cancel();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onButtonBinding(MouseButtonEvent event) {
        if (event.action == KeyAction.Release && onBinding(false, event.button, 0)) event.cancel();
    }

    private boolean onBinding(boolean isKey, int value, int modifiers) {
        if (!isBinding()) return false;

        if (awaitingKeyRelease) {
            if (!isKey || (value != GLFW.GLFW_KEY_ENTER && value != GLFW.GLFW_KEY_KP_ENTER)) return false;

            awaitingKeyRelease = false;
            return false;
        }

        if (moduleToBind.keybind.canBindTo(isKey, value, modifiers)) {
            moduleToBind.keybind.set(isKey, value, modifiers);
            moduleToBind.info("Bound to (highlight)%s(default).", moduleToBind.keybind);
        }
        else if (value == GLFW.GLFW_KEY_ESCAPE) {
            moduleToBind.keybind.set(Keybind.none());
            moduleToBind.info("Removed bind.");
        }
        else return false;

        MeteorClient.EVENT_BUS.post(ModuleBindChangedEvent.get(moduleToBind));
        moduleToBind = null;

        return true;
    }

    @EventHandler(priority = EventPriority.HIGH)
    private void onKey(KeyEvent event) {
        if (event.action == KeyAction.Repeat) return;
        onAction(true, event.key, event.modifiers, event.action == KeyAction.Press);
    }

    @EventHandler(priority = EventPriority.HIGH)
    private void onMouseButton(MouseButtonEvent event) {
        if (event.action == KeyAction.Repeat) return;
        onAction(false, event.button, 0, event.action == KeyAction.Press);
    }

    private void onAction(boolean isKey, int value, int modifiers, boolean isPress) {
        if (mc.currentScreen != null || Input.isKeyPressed(GLFW.GLFW_KEY_F3)) return;

        for (Module module : moduleInstances.values()) {
            if (module.keybind.matches(isKey, value, modifiers) && (isPress || (module.toggleOnBindRelease && module.isActive()))) {
                module.toggle();
                module.sendToggledMsg();
            }
        }
    }

    // End of binding

    @EventHandler(priority = EventPriority.HIGHEST + 1)
    private void onOpenScreen(OpenScreenEvent event) {
        if (!Utils.canUpdate()) return;

        for (Module module : moduleInstances.values()) {
            if (module.toggleOnBindRelease && module.isActive()) {
                module.toggle();
                module.sendToggledMsg();
            }
        }
    }

    @EventHandler
    private void onGameJoined(GameJoinedEvent event) {
        synchronized (active) {
            for (Module module : modules) {
                if (module.isActive() && !module.runInMainMenu) {
                    MeteorClient.EVENT_BUS.subscribe(module);
                    module.onActivate();
                }
            }
        }

        applyAdminAutoEnable();
    }

    /**
     * With the Cheese category unlocked, the admin-only Bones features (a section of the Visualizer) are meant to just be there - switch the
     * Visualizer on (once per unlock / world join, so turning it off by hand still sticks for the session).
     * Controlled by Cheese Control's "auto-enable" switch, on by default.
     */
    public void applyAdminAutoEnable() {
        if (!cheeseUnlocked || !meteordevelopment.meteorclient.systems.modules.misc.CheeseControl.autoEnable()) return;

        meteordevelopment.meteorclient.systems.modules.render.Visualizer visualizer = get(meteordevelopment.meteorclient.systems.modules.render.Visualizer.class);
        if (visualizer != null && !visualizer.isActive()) visualizer.toggle();
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        synchronized (active) {
            for (Module module : modules) {
                if (module.isActive() && !module.runInMainMenu) {
                    MeteorClient.EVENT_BUS.unsubscribe(module);
                    module.onDeactivate();
                }
            }
        }
    }

    public void disableAll() {
        synchronized (active) {
            for (Module module : modules) {
                if (module.isActive()) module.toggle();
            }
        }
    }

    @Override
    public NbtCompound toTag() {
        NbtCompound tag = new NbtCompound();

        NbtList modulesTag = new NbtList();
        for (Module module : getAll()) {
            NbtCompound moduleTag = module.toTag();
            if (moduleTag != null) modulesTag.add(moduleTag);
        }
        tag.put("modules", modulesTag);

        tag.putBoolean("devUnlocked", devUnlocked);
        tag.putBoolean("cheeseUnlocked", cheeseUnlocked);
        tag.putBoolean("bonesRingsMigrated", bonesRingsMigrated);
        tag.putString("adminPinHash", adminPinHash);

        return tag;
    }

    @Override
    public Modules fromTag(NbtCompound tag) {
        disableAll();

        // Unlock flags FIRST: admin-only modules check them in onActivate(), and restoring a saved-on module
        // while the flag was still false switched it straight back off on every launch.
        devUnlocked = tag.getBoolean("devUnlocked");
        cheeseUnlocked = tag.getBoolean("cheeseUnlocked");

        NbtList modulesTag = tag.getList("modules", 10);
        for (NbtElement moduleTagI : modulesTag) {
            NbtCompound moduleTag = (NbtCompound) moduleTagI;
            Module module = get(moduleTag.getString("name"));
            if (module != null) module.fromTag(moduleTag);
        }

        migrateBonesRings(tag);

        String savedPin = tag.getString("adminPinHash");
        adminPinHash = savedPin.isEmpty() ? hashPin(DEFAULT_ADMIN_PIN) : savedPin;

        return this;
    }

    /**
     * Bones Rings used to be its own module; it's now a section of the Visualizer. Carries its saved settings over once
     * (from this file, or from the backup copy taken before the merge if this file no longer has them).
     */
    private void migrateBonesRings(NbtCompound tag) {
        if (tag.getBoolean("bonesRingsMigrated")) { bonesRingsMigrated = true; return; }

        meteordevelopment.meteorclient.systems.modules.render.Visualizer visualizer = get(meteordevelopment.meteorclient.systems.modules.render.Visualizer.class);
        if (visualizer == null) return;

        NbtCompound found = findModuleTag(tag, "bones-rings");

        if (found == null) {
            java.nio.file.Path backup = MeteorClient.FOLDER.toPath().resolve("modules.nbt.bak-before-visualizer-merge");
            try {
                if (java.nio.file.Files.exists(backup)) found = findModuleTag(net.minecraft.nbt.NbtIo.read(backup), "bones-rings");
            } catch (Exception e) {
                MeteorClient.LOG.warn("Couldn't read the Bones Rings settings backup", e);
            }
        }

        if (found != null && found.get("settings") instanceof NbtCompound settingsTag) visualizer.bones().settings.fromTag(settingsTag);
        bonesRingsMigrated = true;
    }

    private static NbtCompound findModuleTag(NbtCompound tag, String name) {
        if (tag == null) return null;

        for (NbtElement el : tag.getList("modules", 10)) {
            if (el instanceof NbtCompound c && c.getString("name").equals(name)) return c;
        }

        return null;
    }

    private boolean bonesRingsMigrated;

    // INIT MODULES

    public void add(Module module) {
        // Check if the module's category is registered
        if (!CATEGORIES.contains(module.category)) {
            throw new RuntimeException("Modules.addModule - Module's category was not registered.");
        }

        // Remove the previous module with the same name
        AtomicReference<Module> removedModule = new AtomicReference<>();
        if (moduleInstances.values().removeIf(module1 -> {
            if (module1.name.equals(module.name)) {
                removedModule.set(module1);
                module1.settings.unregisterColorSettings();

                return true;
            }

            return false;
        })) {
            getGroup(removedModule.get().category).remove(removedModule.get());
        }

        // Mark whether this module is a stock Meteor module or a VL+ addition - done here, after
        // the module's own constructor has fully run, so it applies on top of any title override
        // a module makes in its own constructor (e.g. ParticleColor's "Particle Adjust" rename).
        VLPlusAdditions.refreshModuleTitle(module);

        // Add the module
        moduleInstances.put(module.getClass(), module);
        modules.add(module);
        getGroup(module.category).add(module);

        // Register color settings for the module
        module.settings.registerColorSettings(module);
    }

    private void initPlayer() {
        add(new Click());
        add(new FakePlayer());
        add(new NoInteract());
        add(new NoRotate());
        add(new Portals());
    }

    private void initRender() {
        add(new BetterTab());
        add(new BetterTooltips());
        add(new BlockSelection());
        add(new Blur());
        add(new Breadcrumbs());
        add(new CameraTweaks());
        add(new EntityInspector());
        add(new Freecam());
        add(new FreeLook());
        add(new Fullbright());
        add(new HandView());
        add(new Animations());
        add(new QuickActions());
        add(new ActionWheel());
        add(new Arena());
        add(new ItemPhysics());
        add(new ItemHighlight());
        add(new ItemInfo());
        add(new NoRender());
        add(new TimeChanger());
        add(new TrueSight());
        add(new Awareness());
        add(new ItemBobbing());
        add(new Hitboxes());
        add(new ParticleColor());
        add(new XPBarAdjust());
        add(new XPLevelAdjust());
        add(new HealthBarAdjust());
        add(new StaminaBarAdjust());
        add(new HitTint());
        add(new WaypointsModule());
        add(new Zoom());
    }

    private void initWorld() {
        add(new Ambience());
        add(new Collisions());
        add(new NoGhostBlocks());
    }

    private void initMisc() {
        add(new AntiPacketKick());
        add(new AutoLog());
        add(new AutoReconnect());
        add(new AutoRespawn());
        add(new BetterChat());
        add(new BossDebug());
        add(new DebugLog());
        add(new ResourceProfiler());
        add(new ItemDisplaySettings());
        add(new FightRecorder());
        add(new EntityDataDumper());
        add(new ItemDataDumper());
        add(new HudSnapshotDumper());
        add(new DiscordPresence());
        add(new InventoryTweaks());
        add(new NameProtect());
        add(new Notifier());
        add(new SoundBlocker());
        add(new NoiseNotif());
        add(new NoMistakes());
        add(new FallCancel());
        add(new LineOfSight());
        add(new Visualizer());
        add(new CheeseControl());
    }

    public static class ModuleRegistry extends SimpleRegistry<Module> {
        public ModuleRegistry() {
            super(RegistryKey.ofRegistry(MeteorClient.identifier("modules")), Lifecycle.stable());
        }

        @Override
        public int size() {
            return Modules.get().getAll().size();
        }

        @Override
        public Identifier getId(Module entry) {
            return null;
        }

        @Override
        public Optional<RegistryKey<Module>> getKey(Module entry) {
            return Optional.empty();
        }

        @Override
        public int getRawId(Module entry) {
            return 0;
        }

        @Override
        public Module get(RegistryKey<Module> key) {
            return null;
        }

        @Override
        public Module get(Identifier id) {
            return null;
        }

        @Override
        public Lifecycle getLifecycle() {
            return null;
        }

        @Override
        public Set<Identifier> getIds() {
            return null;
        }
        @Override
        public boolean containsId(Identifier id) {
            return false;
        }

        @Nullable
        @Override
        public Module get(int index) {
            return null;
        }

        @Override
        public @NotNull Iterator<Module> iterator() {
            return new ModuleIterator();
        }

        @Override
        public boolean contains(RegistryKey<Module> key) {
            return false;
        }

        @Override
        public Set<Map.Entry<RegistryKey<Module>, Module>> getEntrySet() {
            return null;
        }

        @Override
        public Set<RegistryKey<Module>> getKeys() {
            return null;
        }

        @Override
        public Optional<RegistryEntry.Reference<Module>> getRandom(Random random) {
            return Optional.empty();
        }

        @Override
        public Registry<Module> freeze() {
            return null;
        }

        @Override
        public RegistryEntry.Reference<Module> createEntry(Module value) {
            return null;
        }

        @Override
        public Optional<RegistryEntry.Reference<Module>> getEntry(int rawId) {
            return Optional.empty();
        }

        @Override
        public Optional<RegistryEntry.Reference<Module>> getEntry(RegistryKey<Module> key) {
            return Optional.empty();
        }

        @Override
        public Stream<RegistryEntry.Reference<Module>> streamEntries() {
            return null;
        }

        @Override
        public Optional<RegistryEntryList.Named<Module>> getEntryList(TagKey<Module> tag) {
            return Optional.empty();
        }

        @Override
        public RegistryEntryList.Named<Module> getOrCreateEntryList(TagKey<Module> tag) {
            return null;
        }

        @Override
        public Stream<Pair<TagKey<Module>, RegistryEntryList.Named<Module>>> streamTagsAndEntries() {
            return null;
        }

        @Override
        public Stream<TagKey<Module>> streamTags() {
            return null;
        }

        @Override
        public void clearTags() {}

        @Override
        public void populateTags(Map<TagKey<Module>, List<RegistryEntry<Module>>> tagEntries) {}

        private static class ModuleIterator implements Iterator<Module> {
            private final Iterator<Module> iterator = Modules.get().getAll().iterator();

            @Override
            public boolean hasNext() {
                return iterator.hasNext();
            }

            @Override
            public Module next() {
                return iterator.next();
            }
        }
    }
}
