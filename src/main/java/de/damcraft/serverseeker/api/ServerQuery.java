package de.damcraft.serverseeker.api;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds a GET query string for the scanner API. Empty/null string values are skipped so callers
 * can add optional filters unconditionally.
 */
public class ServerQuery {
    private final String endpoint;
    private final List<String> params = new ArrayList<>();

    public ServerQuery(String endpoint) {
        this.endpoint = endpoint;
    }

    public static ServerQuery servers()        { return new ServerQuery(Api.BASE + "/servers"); }
    public static ServerQuery count()          { return new ServerQuery(Api.BASE + "/count"); }
    public static ServerQuery bedrockServers() { return new ServerQuery(Api.BASE + "/bedrockServers"); }
    public static ServerQuery bedrockCount()   { return new ServerQuery(Api.BASE + "/bedrockCount"); }

    public ServerQuery add(String key, String value) {
        if (value == null || value.isEmpty()) return this;
        params.add(enc(key) + "=" + enc(value));
        return this;
    }

    public ServerQuery add(String key, long value) {
        params.add(enc(key) + "=" + value);
        return this;
    }

    public ServerQuery add(String key, boolean value) {
        params.add(enc(key) + "=" + value);
        return this;
    }

    /** Adds an ip range: a single address as {@code ip=<int>}, or a subnet as {@code minIp=[..]&maxIp=[..]}. */
    public ServerQuery addIpRange(long[] range) {
        if (range[0] == range[1]) {
            add("ip", range[0]);
        } else {
            add("minIp", "[" + range[0] + "]");
            add("maxIp", "[" + range[1] + "]");
        }
        return this;
    }

    /** Adds the complement of a range so results fall outside [min, max]. */
    public ServerQuery addExcludeRange(long[] range) {
        long a = range[0], b = range[1];
        StringBuilder mins = new StringBuilder("[");
        StringBuilder maxs = new StringBuilder("[");
        boolean any = false;
        if (a > 0) { mins.append(0); maxs.append(a - 1); any = true; }
        if (b < 0xFFFFFFFFL) {
            if (any) { mins.append(','); maxs.append(','); }
            mins.append(b + 1); maxs.append(0xFFFFFFFFL); any = true;
        }
        mins.append(']'); maxs.append(']');
        if (any) { add("minIp", mins.toString()); add("maxIp", maxs.toString()); }
        return this;
    }

    public String url() {
        if (params.isEmpty()) return endpoint;
        return endpoint + "?" + String.join("&", params);
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
