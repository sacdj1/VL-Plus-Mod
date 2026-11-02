/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.render;

import meteordevelopment.meteorclient.mixin.OverlayTextureAccessor;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * Recolors the vanilla "hurt flash" overlay - the tint every entity (mobs, players, yourself)
 * briefly renders with right when it takes damage. Vanilla bakes this as a flat color into a
 * shared 16x16 GPU texture (OverlayTexture, one instance per game session, owned by GameRenderer)
 * rather than computing it per-entity, so there's no per-draw-call color to redirect - this
 * instead rewrites that texture's own pixels directly (and re-uploads them to the GPU) whenever
 * the module (de)activates or the color changes, using OverlayTextureAccessor to reach its
 * otherwise-private backing texture.
 *
 * NativeImage packs color as ABGR (alpha highest byte, then blue, green, red lowest byte) -
 * confirmed by decompiling OverlayTexture's own constructor, which bakes its hurt-flash rows
 * (y 0-7, sampled via OverlayTexture.getV(true) = 3) as the raw int -1308622593, which decodes to
 * r=255 g=0 b=0 a=178 under that layout - plain red at ~70% opacity, vanilla's real color and
 * this setting's own default, so leaving it untouched (or toggling the module off) reproduces
 * vanilla exactly.
 */
public class HitTint extends Module {
    private static final int VANILLA_COLOR = packAbgr(255, 0, 0, 178);

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<SettingColor> color = sgGeneral.add(new ColorSetting.Builder()
        .name("color")
        .description("Color entities flash when they take damage, replacing vanilla's own red flash.")
        .defaultValue(new SettingColor(255, 0, 0, 178))
        .onChanged(v -> {
            if (isActive()) applyCurrentColor();
        })
        .build()
    );

    public HitTint() {
        super(Categories.Hud, "hit-tint", "Recolors the flash every entity briefly shows when it takes damage.");
    }

    @Override
    public void onActivate() {
        applyCurrentColor();
    }

    @Override
    public void onDeactivate() {
        writeColor(VANILLA_COLOR);
    }

    private void applyCurrentColor() {
        SettingColor c = color.get();
        writeColor(packAbgr(c.r, c.g, c.b, c.a));
    }

    private void writeColor(int packed) {
        if (mc.gameRenderer == null) return;

        OverlayTexture overlay = mc.gameRenderer.getOverlayTexture();
        NativeImageBackedTexture tex = ((OverlayTextureAccessor) overlay).getTexture();
        NativeImage image = tex.getImage();
        if (image == null) return;

        for (int x = 0; x < 16; x++) {
            for (int y = 0; y < 8; y++) image.setColor(x, y, packed);
        }

        tex.upload();
    }

    private static int packAbgr(int r, int g, int b, int a) {
        return (a << 24) | (b << 16) | (g << 8) | r;
    }
}
