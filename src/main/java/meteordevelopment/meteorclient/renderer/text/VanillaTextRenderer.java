/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.renderer.text;

import com.mojang.blaze3d.systems.RenderSystem;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.client.font.TextRenderer.TextLayerType;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class VanillaTextRenderer implements TextRenderer {
    public static final VanillaTextRenderer INSTANCE = new VanillaTextRenderer();

    private final BufferAllocator buffer = new BufferAllocator(2048);
    private final VertexConsumerProvider.Immediate immediate = VertexConsumerProvider.immediate(buffer);

    private final MatrixStack matrices = new MatrixStack();
    private final Matrix4f emptyMatrix = new Matrix4f();

    public double scale = 2;
    public boolean scaleIndividually;

    // Per-call rotation, in degrees clockwise around (rotationPivotX, rotationPivotY) - both in the
    // same real screen-pixel coordinate space as the x/y passed to render(). Only takes effect when
    // scaleIndividually is true (the normal HUD-render case, see HudRenderer.begin()) - render()
    // batches every call into a shared immediate-mode buffer and only applies ONE transform to the
    // whole batch at end() time, so a transform pushed onto some other MatrixStack around a single
    // render() call (e.g. DrawContext's) has no effect at all; this is the one hook that actually
    // reaches an individual call's own vertices. Caller must reset this to 0 after use.
    public double rotation = 0;
    public double rotationPivotX = 0;
    public double rotationPivotY = 0;

    private boolean building;
    private double alpha = 1;

    private VanillaTextRenderer() {
        // Use INSTANCE
    }

    @Override
    public void setAlpha(double a) {
        alpha = a;
    }

    @Override
    public double getWidth(String text, int length, boolean shadow) {
        if (text.isEmpty()) return 0;

        if (length != text.length()) text = text.substring(0, length);
        return (mc.textRenderer.getWidth(text) + (shadow ? 1 : 0)) * scale;
    }

    @Override
    public double getHeight(boolean shadow) {
        return (mc.textRenderer.fontHeight + (shadow ? 1 : 0)) * scale;
    }

    @Override
    public void begin(double scale, boolean scaleOnly, boolean big) {
        if (building) {
            // A previous pass never reached end() (an exception mid-render). Throwing here used to switch the
            // whole HUD/menu text off for good; drop the stale batch and start clean instead.
            immediate.draw();
            building = false;
        }

        // Every pass starts in the plain (scale-at-end) mode - the HUD opts into scaleIndividually AFTER this call,
        // so a flag left over from a HUD pass can never shrink/shift a menu's text.
        this.scaleIndividually = false;
        this.rotation = 0;

        this.scale = scale * 2;
        this.building = true;
    }

    @Override
    public double render(String text, double x, double y, Color color, boolean shadow) {
        boolean wasBuilding = building;
        if (!wasBuilding) begin();

        x += 0.5 * scale;
        y += 0.5 * scale;

        int preA = color.a;
        color.a = (int) ((color.a / 255 * alpha) * 255);

        Matrix4f matrix = emptyMatrix;
        if (scaleIndividually) {
            matrices.push();
            if (rotation != 0) {
                matrices.translate(rotationPivotX, rotationPivotY, 0);
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) rotation));
                matrices.translate(-rotationPivotX, -rotationPivotY, 0);
            }
            matrices.scale((float) scale, (float) scale, 1);
            matrix = matrices.peek().getPositionMatrix();
        }

        double x2 = mc.textRenderer.draw(text, (float) (x / scale), (float) (y / scale), color.getPacked(), shadow, matrix, immediate, TextLayerType.NORMAL, 0, LightmapTextureManager.MAX_LIGHT_COORDINATE);

        if (scaleIndividually) matrices.pop();

        color.a = preA;

        if (!wasBuilding) end();
        return (x2 - 1) * scale;
    }

    @Override
    public boolean isBuilding() {
        return building;
    }

    @Override
    public void end(MatrixStack matrices) {
        if (!building) throw new RuntimeException("VanillaTextRenderer.end() called without calling begin()");

        Matrix4fStack matrixStack = RenderSystem.getModelViewStack();

        RenderSystem.disableDepthTest();
        matrixStack.pushMatrix();
        if (matrices != null) matrixStack.mul(matrices.peek().getPositionMatrix());
        if (!scaleIndividually) matrixStack.scale((float) scale, (float) scale, 1);
        RenderSystem.applyModelViewMatrix();

        immediate.draw();

        matrixStack.popMatrix();
        RenderSystem.enableDepthTest();
        RenderSystem.applyModelViewMatrix();

        this.scale = 2;
        this.building = false;
    }
}
