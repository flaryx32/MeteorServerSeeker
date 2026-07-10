package de.damcraft.serverseeker.api;

import java.util.List;

/**
 * A server record as returned by the scanner API ({@code /servers}, {@code /bedrockServers}) or a live
 * ping ({@code /ping}). Fields not present in a given response stay null/0. Gson populates it directly.
 */
public class Server {
    public long ip;                 // big-endian 32-bit int; 0 for live-ping results
    public int port;
    public Version version;
    public String description;      // already flattened to plain text by the API
    public String rawDescription;   // JSON chat-component form
    public Players players;
    public Geo geo;
    public String org;
    public Boolean cracked;         // Java only, nullable
    public Boolean whitelisted;     // Java only, nullable
    public boolean hasFavicon;
    public String discovered;       // stringified unix seconds
    public String lastSeen;         // stringified unix seconds
    public List<PlayerEntry> playerHistory; // present with includePlayers=true
    public Gamemode gamemode;       // Bedrock only
    public Boolean education;       // Bedrock only

    public static class Version {
        public String name;
        public int protocol;
    }

    public static class Players {
        public int online;
        public int max;
        public boolean hasPlayerSample;
        public List<Sample> sample; // live-ping online player list
    }

    public static class Sample {
        public String name;
        public String id;
    }

    public static class Geo {
        public String country;
        public String city;
    }

    public static class PlayerEntry {
        public String name;
        public String id;
        public long lastSession;
        public long lastSeen;
    }

    public static class Gamemode {
        public String name;
        public int id;
    }

    public String ipString() {
        return IpUtil.intToString(ip);
    }

    /** "a.b.c.d" or "a.b.c.d:port" (port omitted when it's the Java default 25565). */
    public String address() {
        String ipStr = ipString();
        return port == 25565 ? ipStr : ipStr + ":" + port;
    }

    public String versionName() {
        return version == null || version.name == null ? "?" : version.name;
    }

    public int protocol() {
        return version == null ? 0 : version.protocol;
    }

    public long lastSeenSeconds() {
        try { return Long.parseLong(lastSeen); } catch (Exception e) { return 0; }
    }

    public long discoveredSeconds() {
        try { return Long.parseLong(discovered); } catch (Exception e) { return 0; }
    }
}
