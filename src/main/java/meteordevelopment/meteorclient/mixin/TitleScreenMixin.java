/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.mixin;

import com.google.gson.JsonParser;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.systems.config.Config;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.misc.Version;
import meteordevelopment.meteorclient.utils.network.Http;
import meteordevelopment.meteorclient.utils.network.MeteorExecutor;
import meteordevelopment.meteorclient.utils.player.TitleScreenCredits;
import meteordevelopment.meteorclient.utils.render.prompts.OkPrompt;
import meteordevelopment.meteorclient.utils.render.prompts.YesNoPrompt;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.option.ServerList;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {
    public TitleScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void onRender(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (Config.get().titleScreenCredits.get()) TitleScreenCredits.render(context);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void onMouseClicked(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> info) {
        if (Config.get().titleScreenCredits.get() && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (TitleScreenCredits.onClicked(mouseX, mouseY)) info.setReturnValue(true);
        }
    }

    // Placed below the vanilla Options/Quit row (which sits at height/4 + 48 + 72 + 12) and above
    // the copyright text in the bottom-right corner, matching the width/style of the Singleplayer
    // button above it.
    @Inject(method = "init", at = @At("TAIL"))
    private void onInitQuickJoin(CallbackInfo ci) {
        Config config = Config.get();
        if (config == null || !config.titleScreenQuickJoin.get()) return;

        int y = this.height / 4 + 48 + 72 + 12 + 24;

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Join Ventureland"), button -> connectToVentureland())
            .dimensions(this.width / 2 - 100, y, 200, 20).build());
    }

    // Only meant to fire once, right after the game finishes starting up - static so it survives
    // across every TitleScreen instance, since returning to the title screen later (e.g. after
    // disconnecting from a server) re-runs init() and shouldn't trigger this again.
    private static boolean autoJoinAttempted = false;

    @Inject(method = "init", at = @At("TAIL"))
    private void onInitAutoJoin(CallbackInfo ci) {
        if (autoJoinAttempted) return;
        autoJoinAttempted = true;

        Config config = Config.get();
        if (config == null || !config.autoJoinVentureland.get()) return;

        connectToVentureland();
    }

    // Connects using whatever the "mc.ventureland.net" entry is actually configured as in the
    // Multiplayer server list (name, resource pack policy, etc.) instead of a fixed,
    // unconfigurable stand-in - ServerListMixin guarantees an entry with this address always
    // exists there.
    private void connectToVentureland() {
        ServerList serverList = new ServerList(this.client);
        serverList.loadFile();

        ServerInfo server = serverList.get("mc.ventureland.net");
        if (server == null) server = new ServerInfo("Ventureland", "mc.ventureland.net", ServerInfo.ServerType.OTHER);

        ConnectScreen.connect(this, this.client, ServerAddress.parse(server.address), server, false, null);
    }
}
