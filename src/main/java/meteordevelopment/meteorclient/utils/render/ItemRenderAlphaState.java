/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.utils.render;

/**
 * Plain (non-mixin) holder so HudRenderer.item() and ItemRendererMixin can share state - two
 * different classes, so a static field declared directly on either isn't a reliable way to talk to
 * the other.
 *
 * RenderLayers.getItemLayer only ever picks a translucent (alpha-respecting) render layer for
 * non-BlockItems - any BlockItem (stone, chests, any placeable block, real or CIT-reskinned) routes
 * through RenderLayers.getEntityBlockLayer instead, which for most blocks resolves to a solid/
 * cutout layer whose shader doesn't apply RenderSystem.setShaderColor's alpha channel the same way
 * the translucent layer does - so a HUD element's Alpha setting silently has no visible effect on
 * those specific items, even though it works fine for every other item. Setting this flag around a
 * faded item draw forces ItemRendererMixin to use the same translucent layer non-BlockItems already
 * use, regardless of what RenderLayers.getItemLayer would otherwise pick.
 */
public class ItemRenderAlphaState {
    public static boolean forcingTranslucent = false;

    private ItemRenderAlphaState() {
    }
}
