package de.damcraft.serverseeker.api;

/**
 * The scanner API stores IPv4 addresses as big-endian 32-bit integers.
 * These helpers convert between that integer form and the usual dotted-quad string,
 * matching the bot's {@code cleanIp} logic.
 */
public final class IpUtil {
    private IpUtil() {}

    /** 32-bit int (as unsigned long) -> "a.b.c.d". */
    public static String intToString(long ip) {
        long v = ip & 0xFFFFFFFFL;
        return ((v >> 24) & 0xFF) + "." + ((v >> 16) & 0xFF) + "." + ((v >> 8) & 0xFF) + "." + (v & 0xFF);
    }

    /** "a.b.c.d" -> 32-bit value (as unsigned long). */
    public static long stringToInt(String ip) {
        String[] parts = ip.trim().split("\\.");
        if (parts.length != 4) throw new IllegalArgumentException("Invalid IPv4 address: " + ip);
        long v = 0;
        for (String part : parts) {
            v = (v << 8) | (Long.parseLong(part.trim()) & 0xFF);
        }
        return v & 0xFFFFFFFFL;
    }

    /**
     * Parses "a.b.c.d" or "a.b.c.d/nn" into an inclusive [min, max] range of unsigned 32-bit values.
     * A bare address (or /32+) yields a single-address range.
     */
    public static long[] cidrToRange(String cidr) {
        String[] split = cidr.trim().split("/");
        long ip = stringToInt(split[0]);
        if (split.length < 2) return new long[]{ip, ip};
        int subnet = Integer.parseInt(split[1].trim());
        if (subnet >= 32) return new long[]{ip, ip};
        if (subnet <= 0) return new long[]{0L, 0xFFFFFFFFL};
        long mask = (0xFFFFFFFFL << (32 - subnet)) & 0xFFFFFFFFL;
        long min = ip & mask;
        long max = ip | (~mask & 0xFFFFFFFFL);
        return new long[]{min, max};
    }
}
