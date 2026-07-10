package de.damcraft.serverseeker.api;

/**
 * Endpoints for the Minecraft Server Scanner API (https://github.com/kgurchiek/Minecraft-Server-Scanner-API),
 * the backend used by the cornbread2100 "MC Server Scanner" Discord bot.
 * The public endpoint is credit-metered and requires no API key.
 */
public final class Api {
    /** Database API (search / count). */
    public static final String BASE = "https://api.cornbread2100.com/v2";
    /** Live ping API (real-time server pings + favicons). */
    public static final String PING = "https://ping.cornbread2100.com";

    private Api() {}
}
