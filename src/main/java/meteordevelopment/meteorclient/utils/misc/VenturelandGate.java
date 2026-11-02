/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.utils.misc;

import meteordevelopment.meteorclient.MeteorClient;
import net.minecraft.client.network.ServerInfo;

/**
 * Whether a feature that's only meant to work on Ventureland (or singleplayer, where there's no
 * other player to have an advantage over and no server to have not consented) is actually allowed
 * to run right now - used to gate anything that would otherwise be a cheat with no opt-in on a
 * server that isn't Ventureland.
 * <p>
 * The server check is an exact match against the real address ("mc.ventureland.net"), never a
 * "contains"/substring check - a substring check would let anyone host under a hostname that
 * merely contains that text (e.g. "ventureland.someoneelse.net") and get the same features on
 * their own, unaffiliated server. Nobody but Ventureland's own operators can make a server
 * actually resolve under mc.ventureland.net itself, since that requires controlling
 * ventureland.net's own DNS.
 * <p>
 * Not unbreakable: a user who edits their own hosts file (or DNS) to repoint mc.ventureland.net at
 * a server they control can still fool this. That requires deliberately tampering with their own
 * machine, not a different server operator tricking an unmodified client into thinking it's
 * somewhere it isn't - which is the actual thing this needs to defend against. Out of scope here,
 * same as how nothing stops a sufficiently motivated user from patching their own jar instead.
 */
public final class VenturelandGate {
    private static final String ADDRESS = "mc.ventureland.net";

    private VenturelandGate() {
    }

    /** True on Ventureland itself, or in singleplayer/self-hosted LAN - the two cases where gated features are allowed. */
    public static boolean isAllowed() {
        return isSingleplayer() || isVentureland();
    }

    public static boolean isSingleplayer() {
        return MeteorClient.mc.isIntegratedServerRunning();
    }

    public static boolean isVentureland() {
        ServerInfo entry = MeteorClient.mc.getCurrentServerEntry();
        if (entry == null || entry.isRealm()) return false;

        return ADDRESS.equalsIgnoreCase(hostOf(entry.address));
    }

    private static String hostOf(String address) {
        int hostEnd = address.length();

        int bracketEnd = address.indexOf(']');
        int searchFrom = bracketEnd != -1 ? bracketEnd : 0;

        int colon = address.indexOf(':', searchFrom);
        if (colon != -1) hostEnd = colon;

        String host = address.substring(0, hostEnd);
        if (host.startsWith("[") && host.endsWith("]")) host = host.substring(1, host.length() - 1);

        return host;
    }
}
