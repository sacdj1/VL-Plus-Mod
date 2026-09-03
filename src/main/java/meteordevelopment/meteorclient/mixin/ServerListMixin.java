/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.mixin;

import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.option.ServerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Keeps a "Ventureland" entry always present in the Multiplayer server list, so the title screen's
// quick-join button (see TitleScreenMixin) can connect using whatever settings the user has
// actually configured for it there (resource pack policy, icon, etc.) instead of a throwaway,
// unconfigurable ServerInfo. Genuinely undeletable via remove() below (matching by address, not
// name/position, so renaming it in the normal server-list UI is still fine) - loadFile() also
// re-adds it if it's ever missing entirely (e.g. hand-edited out of servers.dat directly), as a
// fallback on top of that.
@Mixin(ServerList.class)
public abstract class ServerListMixin {
    private static final String VENTURELAND_ADDRESS = "mc.ventureland.net";

    @Shadow
    public abstract ServerInfo get(String address);

    @Shadow
    public abstract void add(ServerInfo entry, boolean hidden);

    @Inject(method = "loadFile", at = @At("TAIL"))
    private void onLoadFile(CallbackInfo ci) {
        if (get(VENTURELAND_ADDRESS) != null) return;

        add(new ServerInfo("Ventureland", VENTURELAND_ADDRESS, ServerInfo.ServerType.OTHER), false);
    }

    @Inject(method = "remove", at = @At("HEAD"), cancellable = true)
    private void onRemove(ServerInfo entry, CallbackInfo ci) {
        if (entry != null && VENTURELAND_ADDRESS.equals(entry.address)) ci.cancel();
    }
}
