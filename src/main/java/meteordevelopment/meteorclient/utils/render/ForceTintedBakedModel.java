/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.utils.render;

import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.world.Ambience;
import net.fabricmc.fabric.api.renderer.v1.model.FabricBakedModel;
import net.fabricmc.fabric.api.renderer.v1.model.ForwardingBakedModel;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockRenderView;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Wraps a block's baked model so every one of its quads (all faces, not just whichever one a
 * per-vertex render hook happens to see) reports as tinted, letting Ambience's color providers -
 * which only ever get consulted for quads that already claim to be tinted - run on blocks that
 * normally never request tinting at all (e.g. stone). Works for both Sodium and vanilla rendering
 * since both read quads straight from the baked model rather than baking their own copy.
 *
 * Only forces quads that aren't already tinted (colorIndex == -1); the actual color for a forced
 * quad still comes from wherever Ambience's normal per-position color resolution already runs.
 * Whether forcing is currently wanted is re-checked on every call, so toggling
 * Ambience.allowUntintedBlocks takes effect immediately without needing a resource/model reload.
 *
 * Extends ForwardingBakedModel (rather than implementing BakedModel directly) so that models
 * relying on the Fabric Rendering API's dynamic quad emission - e.g. connected textures mods
 * like Connector/CTM Selector, which report isVanillaAdapter() == false and generate their quads
 * in emitBlockQuads() instead of getQuads() - keep working through the wrapper instead of being
 * silently downgraded to whatever getQuads() alone returns.
 */
public class ForceTintedBakedModel extends ForwardingBakedModel {
    public ForceTintedBakedModel(BakedModel original) {
        this.wrapped = original;
    }

    private static boolean shouldForce() {
        Ambience ambience = Modules.get() != null ? Modules.get().get(Ambience.class) : null;
        return ambience != null && ambience.isActive() && ambience.allowUntintedBlocks.get();
    }

    @Override
    public List<BakedQuad> getQuads(BlockState state, Direction face, Random random) {
        List<BakedQuad> quads = wrapped.getQuads(state, face, random);
        if (quads.isEmpty() || !shouldForce()) return quads;

        List<BakedQuad> forced = new ArrayList<>(quads.size());
        boolean changed = false;

        for (BakedQuad quad : quads) {
            if (quad.getColorIndex() == -1) {
                forced.add(new BakedQuad(quad.getVertexData(), 0, quad.getFace(), quad.getSprite(), quad.hasShade()));
                changed = true;
            } else {
                forced.add(quad);
            }
        }

        return changed ? forced : quads;
    }

    @Override
    public void emitBlockQuads(BlockRenderView blockView, BlockState state, BlockPos pos, Supplier<Random> randomSupplier, RenderContext context) {
        FabricBakedModel wrappedFabric = (FabricBakedModel) wrapped;

        if (!shouldForce()) {
            wrappedFabric.emitBlockQuads(blockView, state, pos, randomSupplier, context);
            return;
        }

        context.pushTransform(quad -> {
            if (quad.colorIndex() == -1) quad.colorIndex(0);
            return true;
        });

        try {
            wrappedFabric.emitBlockQuads(blockView, state, pos, randomSupplier, context);
        } finally {
            context.popTransform();
        }
    }
}
