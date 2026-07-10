package de.damcraft.serverseeker.api;

import java.util.List;

/** Response of {@code /servers} and {@code /bedrockServers}: {@code {"data":[...],"credits":N}}. */
public class ServersResponse {
    public List<Server> data;
    public Long credits;
    public String error;

    public boolean isError() {
        return error != null;
    }
}
