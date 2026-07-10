package de.damcraft.serverseeker.api;

/** Response of {@code /count} and {@code /bedrockCount}: {@code {"data":N,"credits":M}}. */
public class CountResponse {
    public long data;
    public Long credits;
    public String error;
}
