/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.utils.render;

/**
 * Plain (non-mixin) holder so InGameHudMixin (rotating the hotbar) and DrawContextMixin
 * (counter-rotating each item icon drawn inside it, for VanillaHotbarHud's Keep Items Upright)
 * can share state - two different mixin classes merge into two different target classes, so a
 * static field declared directly on either mixin class isn't a reliable way to talk to the other.
 */
public class VanillaHudRotationState {
    /** Degrees to counter-rotate each item icon drawn via DrawContext.drawItemInSlot, or 0 for none. Set/cleared around InGameHud.renderHotbar. */
    public static double hotbarItemCounterRotation = 0;

    /**
     * Degrees to counter-rotate each sprite drawn via DrawContext.drawGuiTexture(Identifier,int,int,int,int),
     * or 0 for none. Shared by Armor/Health/Hunger/MountHealth - all four route their icons through
     * this same vanilla method, and InGameHud only ever renders one of those four methods at a time
     * (never nested/concurrently), so one field set/cleared around whichever one is currently
     * running is enough - no risk of one clobbering another's window.
     */
    public static double statusBarSpriteCounterRotation = 0;

    /**
     * Row anchor (left edge for Health, right edge minus 9 for Food) and per-icon spacing in
     * pixels, for redistributing Health's/Food's fixed 8px icon stride - see VanillaHealthHud/
     * VanillaHungerHud's own Spacing setting. spacing == 8 is a no-op (vanilla's own stride).
     * Shared the same way as statusBarSpriteCounterRotation above - only one of Health/Food ever
     * renders at a time.
     */
    public static int spriteRowAnchorX = 0;
    public static int spriteSpacing = 8;

    private VanillaHudRotationState() {
    }
}
