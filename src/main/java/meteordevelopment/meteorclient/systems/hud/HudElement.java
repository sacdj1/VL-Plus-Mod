/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.hud;

import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.Settings;
import meteordevelopment.meteorclient.systems.hud.screens.HudEditorScreen;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.misc.ISerializable;
import meteordevelopment.meteorclient.utils.other.Snapper;
import net.minecraft.nbt.NbtCompound;

public abstract class HudElement implements Snapper.Element, ISerializable<HudElement> {
    public final HudElementInfo<?> info;
    private boolean active;

    public final Settings settings = new Settings();
    public final HudBox box = new HudBox(this);

    // Declared here (rather than per-element) so every HudElement gets it automatically - applied
    // uniformly by HudRenderer around each element's own render() call (see Hud.onRender/
    // HudEditorScreen.renderElements), covering quads/text/textures/items alike without each of the
    // ~30 element subclasses needing its own copy. Vanilla* relocator elements (which redirect
    // vanilla's own draw calls instead of drawing through HudRenderer) are NOT covered by this -
    // see VanillaHudRotationState-style per-element alpha there instead, where present.
    public final Setting<Integer> alpha = settings.getDefaultGroup().add(new IntSetting.Builder()
        .name("alpha")
        .description("Overall transparency of this element - 255 is fully opaque, 0 is invisible.")
        .defaultValue(255)
        .range(0, 255)
        .sliderRange(0, 255)
        .build()
    );

    public boolean autoAnchors = true;
    public int x, y;

    public HudElement(HudElementInfo<?> info) {
        this.info = info;
        this.active = true;
    }

    public boolean isActive() {
        return active;
    }

    public void toggle() {
        active = !active;
    }

    public void setSize(double width, double height) {
        box.setSize(width, height);
    }

    @Override
    public void setPos(int x, int y) {
        if (autoAnchors) {
            box.setPos(x, y);
            box.xAnchor = XAnchor.Left;
            box.yAnchor = YAnchor.Top;
            box.updateAnchors();
        }
        else {
            box.setPos(box.x + (x - this.x), box.y + (y - this.y));
        }

        updatePos();
    }

    @Override
    public void move(int deltaX, int deltaY) {
        box.move(deltaX, deltaY);
        updatePos();
    }

    public void updatePos() {
        x = box.getRenderX();
        y = box.getRenderY();
    }

    protected double alignX(double width, Alignment alignment) {
        return box.alignX(getWidth(), width, alignment);
    }

    @Override
    public int getX() {
        return x;
    }

    @Override
    public int getY() {
        return y;
    }

    @Override
    public int getWidth() {
        return box.width;
    }

    @Override
    public int getHeight() {
        return box.height;
    }

    protected boolean isInEditor() {
        return !Utils.canUpdate() || HudEditorScreen.isOpen();
    }

    /**
     * Degrees clockwise the element's own rendered content is rotated by, around its box's own
     * center - 0 for every element without a rotation concept. Purely informational for the editor
     * (HudEditorScreen.getHovered()), which uses this to rotate its click/hover hit-test box to
     * match the rotated visual instead of testing against an axis-aligned box that no longer lines
     * up with what's actually drawn at nonzero rotation. Override wherever an element has its own
     * "rotation" setting (see e.g. VanillaHotbarHud).
     */
    public int getEditorRotation() {
        return 0;
    }

    public void remove() {
        Hud.get().remove(this);
    }

    public void tick(HudRenderer renderer) {}

    public void render(HudRenderer renderer) {}

    public void onFontChanged() {}

    public WWidget getWidget(GuiTheme theme) {
        return null;
    }

    // Serialization

    @Override
    public NbtCompound toTag() {
        NbtCompound tag = new NbtCompound();

        tag.putString("name", info.name);
        tag.putBoolean("active", active);

        tag.put("settings", settings.toTag());
        tag.put("box", box.toTag());

        tag.putBoolean("autoAnchors", autoAnchors);

        return tag;
    }

    @Override
    public HudElement fromTag(NbtCompound tag) {
        settings.reset();

        active = tag.getBoolean("active");

        settings.fromTag(tag.getCompound("settings"));
        box.fromTag(tag.getCompound("box"));

        autoAnchors = tag.getBoolean("autoAnchors");

        x = box.getRenderX();
        y = box.getRenderY();

        return this;
    }
}
