package de.damcraft.serverseeker.api;

public final class Credits {
    private static volatile long remaining = -1;
    private static volatile long max = -1;

    private Credits() {}

    public static void update(Long value) {
        if (value != null) remaining = value;
    }

    public static void update(long remainingValue, long maxValue) {
        remaining = remainingValue;
        max = maxValue;
    }

    public static long remaining() {
        return remaining;
    }

    public static long secondsUntilReset() {
        return 3600 - (System.currentTimeMillis() / 1000 % 3600);
    }

    public static String resetString() {
        long s = secondsUntilReset();
        return String.format("%d:%02d", s / 60, s % 60);
    }

    public static String summary() {
        if (remaining < 0) return "Credits: unknown (resets in " + resetString() + ")";
        String maxStr = max > 0 ? " / " + String.format("%,d", max) : "";
        return "Credits: " + String.format("%,d", remaining) + maxStr + " (resets in " + resetString() + ")";
    }
}
