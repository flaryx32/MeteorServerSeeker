package de.damcraft.serverseeker.commands;

import com.google.common.net.HostAndPort;
import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import de.damcraft.serverseeker.api.IpUtil;
import de.damcraft.serverseeker.api.Server;
import de.damcraft.serverseeker.api.ServerQuery;
import de.damcraft.serverseeker.api.ServersResponse;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.utils.network.Http;
import meteordevelopment.meteorclient.utils.network.MeteorExecutor;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;

import java.net.InetAddress;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.List;

import static de.damcraft.serverseeker.ServerSeeker.LOG;

public class ServerInfoCommand extends Command {
    private static final SimpleCommandExceptionType SINGLEPLAYER_EXCEPTION = new SimpleCommandExceptionType(new LiteralMessage("Cannot run command in singleplayer."));

    public ServerInfoCommand() {
        super("serverInfo", "");
    }

    @Override
    public void build(LiteralArgumentBuilder<ClientSuggestionProvider> builder) {
        builder.executes(context -> {
            if (mc.getCurrentServer() == null) {
                throw SINGLEPLAYER_EXCEPTION.create();
            }

            HostAndPort hap = HostAndPort.fromString(mc.getCurrentServer().ip);
            String host = hap.getHost();
            int port = hap.hasPort() ? hap.getPort() : 25565;

            MeteorExecutor.execute(() -> {
                long ipInt;
                try {
                    ipInt = IpUtil.stringToInt(host);
                } catch (Exception notNumeric) {
                    try {
                        ipInt = IpUtil.stringToInt(InetAddress.getByName(host).getHostAddress());
                    } catch (Exception e) {
                        mc.execute(() -> error("Could not resolve " + host));
                        return;
                    }
                }

                String url = ServerQuery.servers().add("ip", ipInt).add("port", port).add("includePlayers", true).url();
                ServersResponse response = Http.get(url)
                    .exceptionHandler(e -> LOG.error("Could not fetch server info: ", e))
                    .sendJson(ServersResponse.class);

                mc.execute(() -> {
                    if (response == null) { error("Network error"); return; }
                    if (response.isError()) { error(response.error); return; }
                    if (response.data == null || response.data.isEmpty()) { warning("Server not found in the database."); return; }

                    Server server = response.data.get(0);
                    String description = server.description == null ? "" : server.description.replace("\n", "\\n").replace("§r", "");
                    if (description.length() > 100) description = description.substring(0, 100) + "...";

                    info("-- Server Info --");
                    info("Auth: (highlight)" + (server.cracked == null ? "Unknown" : server.cracked ? "Cracked" : "Premium"));
                    info("Whitelist: (highlight)" + (server.whitelisted == null ? "Unknown" : server.whitelisted ? "Enabled" : "Disabled"));
                    info("Description: (highlight)" + description);
                    if (server.players != null) info("Online Players (last scan): (highlight)" + server.players.online + "/" + server.players.max);
                    info("Last Scanned: (highlight)" + date(server.lastSeenSeconds()));
                    info("Version: (highlight)" + server.versionName() + " (default)(" + server.protocol() + ")");

                    List<Server.PlayerEntry> players = server.playerHistory;
                    if (players == null || players.isEmpty()) {
                        warning("No player history.");
                    } else {
                        info("-- Player History --");
                        for (Server.PlayerEntry player : players) {
                            info("- (highlight)" + player.name + " " + date(player.lastSession));
                        }
                    }
                });
            });

            return SINGLE_SUCCESS;
        });
    }

    private static String date(long seconds) {
        if (seconds <= 0) return "Unknown";
        return DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
            .format(Instant.ofEpochSecond(seconds).atZone(ZoneId.systemDefault()).toLocalDateTime());
    }
}
