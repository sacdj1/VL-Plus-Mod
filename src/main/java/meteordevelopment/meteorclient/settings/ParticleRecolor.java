/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.settings;

import meteordevelopment.meteorclient.utils.render.color.SettingColor;

/**
 * A particle entry's tint color plus HOW that tint is applied to the particle - each entry in a
 * Particle Color list carries its own mode, so one entry can Colorize while another just Multiplies.
 * Still a plain {@link SettingColor} to everything that only cares about the color.
 */
public class ParticleRecolor extends SettingColor {
    public enum Mode {
        /** Keeps the particle's own real texture (block/item particles still show their actual sprite, not a flat color), but corrects for that texture's own average color first so the result still lands close to the color you pick instead of just darkening toward the texture's own hue. */
        Colorize,
        /** Swaps textured (block/item) particles to a neutral white sprite, then tints - discards the particle's own texture entirely, but gives the exact color you pick, with no dependence on (or limit from) what the original texture looked like. */
        Override,
        /** Same as Override, plus a thin outline around the particle in the inverse of the picked color, for contrast against similarly-colored backgrounds. */
        OverrideOutline,
        /** Blends the particle's own color toward the tint by the tint's alpha. */
        Blend,
        /** Sets the particle's color outright to the tint (still multiplied by its texture). */
        Replace,
        /** Multiplies the particle's color by the tint - only ever darkens/filters. */
        Multiply,
        /** Adds the tint on top of the particle's own color - only ever brightens. */
        Add,
        /** Same Colorize correction, but the target color continuously swings its hue over time instead of staying fixed - this entry's own color is the swing's starting point (Hue Shift Speed/Range below pick how it moves), keeping saturation/brightness/alpha fixed the same way ItemInfo's own Hue Shift color mode does. */
        HueShift
    }

    public Mode mode = Mode.Colorize;

    /** HueShift mode only - how fast the hue swings. Same convention/scale as ItemInfo's own Enchant/Attachment Hue Shift Speed. */
    public double hueShiftSpeed = 1.0;

    /** HueShift mode only - how many degrees the hue swings away from this entry's own color in each direction. */
    public double hueShiftRange = 60.0;

    public ParticleRecolor(int r, int g, int b, int a) {
        super(r, g, b, a);
    }

    public ParticleRecolor(SettingColor other) {
        super(other);
        if (other instanceof ParticleRecolor recolor) {
            mode = recolor.mode;
            hueShiftSpeed = recolor.hueShiftSpeed;
            hueShiftRange = recolor.hueShiftRange;
        }
    }
}
