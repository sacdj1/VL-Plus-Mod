/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.gui.screens;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.settings.QuickAction;
import meteordevelopment.meteorclient.systems.modules.misc.ActionWheel;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * The radial overlay ActionWheel's hold key opens - see that module for the actual interaction
 * contract. Rendered over whatever was already on screen (the live game view, or another screen
 * if Allow While Screen Open let it open on top of one) rather than its own blank backdrop.
 * <p>
 * Left-click on a Submenu slice pushes its own Children list and shows a fresh ring for it - the
 * wheel stays open, nothing fires. Left-click on a Command/Module slice fires it and then either
 * closes the whole wheel (Close) or stays open at the SAME ring (Stay), so several can be fired
 * without re-holding the key. Right-click steps back one ring (or closes, already at the root).
 * Releasing the hold key (module calls cancel()) always closes outright, regardless of ring depth.
 */
public class ActionWheelScreen extends Screen {
    private final ActionWheel module;
    private final Screen previousScreen;

    private final Deque<List<QuickAction>> rings = new ArrayDeque<>();
    private int page = 0;
    private int hoveredIndex = -1;

    public ActionWheelScreen(ActionWheel module, Screen previousScreen) {
        super(Text.literal("Action Wheel"));

        this.module = module;
        this.previousScreen = previousScreen;

        rings.push(module.getActions());
    }

    @Override
    protected void init() {
        if (previousScreen != null) previousScreen.init(client, width, height);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (previousScreen != null) {
            previousScreen.render(context, mouseX, mouseY, delta);
        } else if (module.dimBackground()) {
            context.fill(0, 0, width, height, 0x90000000);
        }

        List<QuickAction> page = currentPage();
        double centerX = width / 2.0;
        double centerY = height / 2.0;

        double dx = mouseX - centerX;
        double dy = mouseY - centerY;
        double distSq = dx * dx + dy * dy;
        double deadzoneSq = module.deadzoneRadius() * module.deadzoneRadius();

        hoveredIndex = (page.isEmpty() || distSq < deadzoneSq) ? -1 : nearestIndex(dx, dy, page.size());

        double radius = module.radius();
        double size = module.slotSize();

        for (int i = 0; i < page.size(); i++) {
            double angle = sliceAngle(i, page.size());
            double px = centerX + Math.cos(angle) * radius;
            double py = centerY + Math.sin(angle) * radius;

            boolean highlighted = i == hoveredIndex;

            int boxColor = (highlighted ? module.highlightColor() : module.slotColor()).getPacked();
            context.fill((int) (px - size / 2), (int) (py - size / 2), (int) (px + size / 2), (int) (py + size / 2), boxColor);

            String label = page.get(i).label;
            int textColor = (highlighted ? module.highlightTextColor() : module.textColor()).getPacked();
            int textWidth = client.textRenderer.getWidth(label);
            context.drawText(client.textRenderer, label, (int) (px - textWidth / 2.0), (int) (py - client.textRenderer.fontHeight / 2.0), textColor, true);
        }

        int totalPages = totalPages();
        if (totalPages > 1) {
            String pageText = "Page " + (this.page + 1) + "/" + totalPages + " - scroll to change";
            int pageTextWidth = client.textRenderer.getWidth(pageText);
            context.drawText(client.textRenderer, pageText, (int) (centerX - pageTextWidth / 2.0), (int) (centerY + radius + size), 0xFFFFFFFF, true);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            confirm();
            return true;
        }

        if (button == 1) {
            goBack();
            return true;
        }

        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int totalPages = totalPages();
        if (totalPages <= 1) return true;

        page = Math.floorMod(page + (verticalAmount > 0 ? -1 : 1), totalPages);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            goBack();
            return true;
        }

        return previousScreen != null && previousScreen.keyPressed(keyCode, scanCode, modifiers);
    }

    private void confirm() {
        List<QuickAction> actions = currentPage();
        QuickAction action = (hoveredIndex >= 0 && hoveredIndex < actions.size()) ? actions.get(hoveredIndex) : null;
        if (action == null) return;

        if (action.type == QuickAction.Type.Submenu) {
            rings.push(action.children);
            page = 0;
            return;
        }

        module.execute(action);
        if (action.closeBehavior == QuickAction.CloseBehavior.Close) restorePreviousScreen();
    }

    /** Right-click/Escape: one ring back, or closes outright if already at the root. */
    private void goBack() {
        if (rings.size() > 1) rings.pop();
        else restorePreviousScreen();

        page = 0;
    }

    /** The hold key being released - always closes outright, regardless of how deep the current ring is. */
    public void cancel() {
        restorePreviousScreen();
    }

    private void restorePreviousScreen() {
        MeteorClient.mc.setScreen(previousScreen);
    }

    private List<QuickAction> currentPage() {
        List<QuickAction> all = rings.peek();
        int perPage = Math.max(1, module.maxPerPage());
        int from = Math.min(page * perPage, all.size());
        int to = Math.min(from + perPage, all.size());

        return all.subList(from, to);
    }

    private int totalPages() {
        int perPage = Math.max(1, module.maxPerPage());
        return Math.max(1, (rings.peek().size() + perPage - 1) / perPage);
    }

    /** Starting from straight up, going clockwise, evenly spaced around the circle. */
    private static double sliceAngle(int index, int count) {
        return -Math.PI / 2 + (2 * Math.PI * index / count);
    }

    private static int nearestIndex(double dx, double dy, int count) {
        double mouseAngle = Math.atan2(dy, dx);

        int best = 0;
        double bestDiff = Double.MAX_VALUE;

        for (int i = 0; i < count; i++) {
            double diff = Math.abs(angleDiff(mouseAngle, sliceAngle(i, count)));
            if (diff < bestDiff) {
                bestDiff = diff;
                best = i;
            }
        }

        return best;
    }

    private static double angleDiff(double a, double b) {
        double diff = (a - b) % (2 * Math.PI);
        if (diff > Math.PI) diff -= 2 * Math.PI;
        if (diff < -Math.PI) diff += 2 * Math.PI;
        return diff;
    }
}
