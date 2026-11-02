/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.mixin.sodium;

import net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Sodium skips calling a block's ColorProvider.getColors() ENTIRELY for any quad whose model
 * declares no tintindex (colorIndex -1) - confirmed by decompiling BlockRenderer#colorizeQuad,
 * which early-returns on exactly that check before even looking up the registered provider. That's
 * correct for vanilla's own tinting (nothing to blend for an untinted quad), but it also means
 * AmbienceBlockColorProvider never runs for a block's naturally-untinted quads at all, regardless
 * of registration - e.g. grass_block's dirt-textured bottom/side quads (real tintindex -1, baked
 * directly into their own texture - see grass_block.json) could never be recolored, only its
 * grass-tinted top/side-overlay quads (tintindex 0) could.
 *
 * Forcing the colorIndex seen by that early-return check to 0 whenever it was -1 makes Sodium call
 * the provider for every quad of every block that has one; a block with no registered provider at
 * all is unaffected (colorizeQuad also bails out whenever the provider itself is null, which this
 * doesn't touch), and AmbienceBlockColorProvider still reads each quad's REAL, unmodified colorIndex
 * via ModelQuadView.getColorIndex() to tell a naturally-tinted quad apart from a naturally-untinted
 * one - this only flips Sodium's call/no-call gate, not what the quad itself reports.
 */
@Mixin(value = BlockRenderer.class, remap = false)
public abstract class SodiumBlockRendererMixin {
    @ModifyArg(
        method = "processQuad",
        at = @At(
            value = "INVOKE",
            target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/pipeline/BlockRenderer;colorizeQuad(Lnet/caffeinemc/mods/sodium/client/render/frapi/mesh/MutableQuadViewImpl;I)V"
        ),
        remap = false,
        index = 1
    )
    private int vlplus$forceColorIndex(int colorIndex) {
        return colorIndex == -1 ? 0 : colorIndex;
    }
}
