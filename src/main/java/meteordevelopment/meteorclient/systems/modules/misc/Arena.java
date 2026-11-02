/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.misc;

import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ArenaPerks;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shares your own currently-held perks with party members over party chat, and reads theirs back
 * the same way, so ArenaHud can show everyone's perks (not just your own) - Arena parties cap at
 * 3 people, so this only ever tracks up to 2 others, evicting the longest-untouched one if a third
 * distinct sender ever shows up.
 * <p>
 * Two things here are genuinely this server's own and not something knowable in advance: the
 * actual command that reaches party chat, and the actual way an incoming party chat line is
 * formatted (which this needs to parse to find out who sent it). Both are configurable below -
 * retune them the moment you've seen the real text; the defaults are placeholders, not confirmed.
 */
public class Arena extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgAdvanced = settings.createGroup("Advanced");

    private static final String TAG = "VLPERKS|";

    private final Setting<Boolean> shareMyPerks = sgGeneral.add(new BoolSetting.Builder()
        .name("share-my-perks")
        .description("Broadcasts your own perks to party chat whenever they change (capped by Min Broadcast Interval below), so party members running this mod can see them.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> showPartyPerks = sgGeneral.add(new BoolSetting.Builder()
        .name("show-party-perks")
        .description("Listens for party members' own perk broadcasts and makes them available to ArenaHud - that HUD element's own Show Party Members setting still needs to be on too.")
        .defaultValue(true)
        .build()
    );

    private final Setting<String> bookName = sgGeneral.add(new StringSetting.Builder()
        .name("book-name")
        .description("The exact display name (case-insensitive) of the book your own perks are read from. Kept as its own copy rather than sharing ArenaHud's, since this module is useful without that HUD element even being on.")
        .defaultValue("Current Upgrades")
        .build()
    );

    private final Setting<String> partyChatCommand = sgAdvanced.add(new StringSetting.Builder()
        .name("party-chat-command")
        .description("Typed in chat (as if you'd typed it yourself) right before the encoded perk payload, to actually reach party chat on this server - not knowable in advance. Common guesses: \"/pc \", \"/p \", \"/party \". Confirm against this server's real party chat command and fix if wrong.")
        .defaultValue("/pc ")
        .build()
    );

    private final Setting<String> incomingPattern = sgAdvanced.add(new StringSetting.Builder()
        .name("incoming-pattern")
        .description("Regex matched against every incoming chat line, with two capture groups: the sender's name, then the rest of the message. A placeholder guess, not confirmed - retune once you've seen what a real party chat line actually looks like on this server.")
        .defaultValue("^\\[Party] (\\w+): (.+)$")
        .build()
    );

    private final Setting<Integer> minBroadcastIntervalTicks = sgAdvanced.add(new IntSetting.Builder()
        .name("min-broadcast-interval")
        .description("Minimum ticks between broadcasts, even if your perks keep changing - a floor against spamming party chat.")
        .defaultValue(40)
        .min(20)
        .sliderRange(20, 600)
        .build()
    );

    private final Setting<Integer> staleTimeoutSeconds = sgAdvanced.add(new IntSetting.Builder()
        .name("stale-timeout")
        .description("Drops a party member's stored perks if nothing fresh has come in from them for this many seconds (left the party, swapped servers, stopped running this mod, etc.) instead of showing a permanently stale reading.")
        .defaultValue(120)
        .min(10)
        .sliderRange(10, 600)
        .build()
    );

    private static final int MAX_PARTY_MEMBERS = 2; // + yourself = 3, this server's own party cap

    private record PartySnapshot(List<ArenaPerks.PerkEntry> perks, long lastSeenMs) {
    }

    private final Map<String, PartySnapshot> partyPerks = new LinkedHashMap<>();

    private String lastBroadcastPayload = "";
    private int ticksSinceBroadcast = Integer.MAX_VALUE;

    private Pattern compiledIncoming;
    private String compiledIncomingFrom;

    public Arena() {
        super(Categories.Misc, "arena", "Shares your perks with party members over party chat (and reads theirs back) for ArenaHud to display.", "arena-party-perks");
    }

    @Override
    public void onDeactivate() {
        partyPerks.clear();
        lastBroadcastPayload = "";
        ticksSinceBroadcast = Integer.MAX_VALUE;
    }

    /** Non-stale party member perks only - already pruned, callers (ArenaHud) don't need to think about staleness. */
    public Map<String, List<ArenaPerks.PerkEntry>> getPartyPerks() {
        Map<String, List<ArenaPerks.PerkEntry>> result = new LinkedHashMap<>();
        for (Map.Entry<String, PartySnapshot> entry : partyPerks.entrySet()) result.put(entry.getKey(), entry.getValue().perks());
        return result;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        long now = System.currentTimeMillis();
        long timeoutMs = staleTimeoutSeconds.get() * 1000L;
        partyPerks.entrySet().removeIf(entry -> now - entry.getValue().lastSeenMs() > timeoutMs);

        ticksSinceBroadcast++;

        if (!shareMyPerks.get() || mc.player == null) return;
        if (ticksSinceBroadcast < minBroadcastIntervalTicks.get()) return;

        List<ArenaPerks.PerkEntry> mine = ArenaPerks.readLocal(bookName.get());
        if (mine.isEmpty()) return;

        String payload = ArenaPerks.encode(mine);
        if (payload.equals(lastBroadcastPayload)) return;

        ChatUtils.sendPlayerMsg(partyChatCommand.get() + TAG + payload);
        lastBroadcastPayload = payload;
        ticksSinceBroadcast = 0;
    }

    @EventHandler
    private void onReceiveMessage(ReceiveMessageEvent event) {
        if (!showPartyPerks.get()) return;

        Pattern pattern = compiledIncoming();
        if (pattern == null) return;

        Matcher m = pattern.matcher(event.getMessage().getString());
        if (!m.find() || m.groupCount() < 2) return;

        String sender = m.group(1);
        String rest = m.group(2);
        if (!rest.startsWith(TAG)) return;

        if (mc.player != null && sender.equalsIgnoreCase(mc.getSession().getUsername())) return;

        if (!partyPerks.containsKey(sender) && partyPerks.size() >= MAX_PARTY_MEMBERS) evictOldest();

        partyPerks.put(sender, new PartySnapshot(ArenaPerks.decode(rest.substring(TAG.length())), System.currentTimeMillis()));
    }

    private void evictOldest() {
        String oldest = null;
        long oldestTime = Long.MAX_VALUE;

        for (Map.Entry<String, PartySnapshot> entry : partyPerks.entrySet()) {
            if (entry.getValue().lastSeenMs() < oldestTime) {
                oldestTime = entry.getValue().lastSeenMs();
                oldest = entry.getKey();
            }
        }

        if (oldest != null) partyPerks.remove(oldest);
    }

    private Pattern compiledIncoming() {
        String patternStr = incomingPattern.get();

        if (compiledIncoming == null || !patternStr.equals(compiledIncomingFrom)) {
            compiledIncomingFrom = patternStr;
            try {
                compiledIncoming = Pattern.compile(patternStr);
            } catch (Exception e) {
                compiledIncoming = null;
            }
        }

        return compiledIncoming;
    }
}
