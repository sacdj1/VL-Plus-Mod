/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.hud.elements;

import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.misc.ReaderNotifQueue;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * Displays recent NoiseNotif "Reader Notif" matches as on-screen text, preserving the matched
 * text's own color-coding (e.g. from chat/boss bar) - drawn via DrawContext.drawText(Text) directly
 * rather than HudRenderer's flat-string helper, since that only supports a single solid color and
 * would otherwise strip the original formatting. How long each entry stays on screen is set per
 * rule (in NoiseNotif's Reader Notif rule editor), not here - different rules may want different
 * durations, so it's not a single element-wide setting.
 */
public class ReaderNotifHud extends HudElement {
    public static final HudElementInfo<ReaderNotifHud> INFO = new HudElementInfo<>(Hud.GROUP, "reader-notif", "Shows recent Reader Notif matches (from NoiseNotif) as on-screen text, keeping their original color-coding.", ReaderNotifHud::new);

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> scale = sgGeneral.add(new DoubleSetting.Builder()
        .name("scale")
        .description("Text scale.")
        .defaultValue(1.5)
        .min(0.5)
        .sliderRange(0.5, 6)
        .build()
    );

    private final Setting<Integer> maxLines = sgGeneral.add(new IntSetting.Builder()
        .name("max-lines")
        .description("Maximum number of notifications shown at once - oldest ones scroll off first.")
        .defaultValue(5)
        .min(1)
        .sliderRange(1, 15)
        .build()
    );

    private final Setting<Boolean> shadow = sgGeneral.add(new BoolSetting.Builder()
        .name("shadow")
        .description("Draws a drop shadow behind the text.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> newestOnTop = sgGeneral.add(new BoolSetting.Builder()
        .name("newest-on-top")
        .description("Stacks the newest notification at the top instead of the bottom.")
        .defaultValue(true)
        .build()
    );

    public ReaderNotifHud() {
        super(INFO);

        setSize(150, 60);
    }

    @Override
    public void render(HudRenderer renderer) {
        ReaderNotifQueue.prune();

        List<ReaderNotifQueue.Entry> entries = ReaderNotifQueue.get();
        int start = Math.max(0, entries.size() - maxLines.get());
        List<Text> shown = new ArrayList<>();
        for (ReaderNotifQueue.Entry entry : entries.subList(start, entries.size())) shown.add(entry.text());

        // Nothing queued right now doesn't mean there's nothing to show - while positioning it in
        // the HUD editor, an empty element is just an invisible box with nothing to click/drag, so
        // show a placeholder sample instead of rendering nothing.
        if (shown.isEmpty() && isInEditor()) shown.add(Text.literal("Reader Notif"));

        DrawContext drawContext = renderer.drawContext;
        if (drawContext == null || shown.isEmpty()) return;

        double lineHeight = mc.textRenderer.fontHeight * scale.get() + 2;
        double maxWidth = 0;

        for (int i = 0; i < shown.size(); i++) {
            Text text = newestOnTop.get() ? shown.get(shown.size() - 1 - i) : shown.get(i);

            MatrixStack matrices = drawContext.getMatrices();
            matrices.push();
            matrices.translate(x, y + i * lineHeight, 0);
            matrices.scale(scale.get().floatValue(), scale.get().floatValue(), 1f);

            if (shadow.get()) drawContext.drawTextWithShadow(mc.textRenderer, text, 0, 0, -1);
            else drawContext.drawText(mc.textRenderer, text, 0, 0, -1, false);

            matrices.pop();

            maxWidth = Math.max(maxWidth, mc.textRenderer.getWidth(text) * scale.get());
        }

        setSize(Math.max(50, maxWidth), shown.size() * lineHeight);
    }
}
