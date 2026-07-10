package de.damcraft.serverseeker.gui;

import de.damcraft.serverseeker.api.Api;
import de.damcraft.serverseeker.api.Server;
import de.damcraft.serverseeker.api.ServersResponse;
import de.damcraft.serverseeker.api.Twitch;
import meteordevelopment.meteorclient.gui.GuiThemes;
import meteordevelopment.meteorclient.gui.WindowScreen;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.utils.network.Http;
import meteordevelopment.meteorclient.utils.network.MeteorExecutor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static de.damcraft.serverseeker.ServerSeeker.LOG;
import static de.damcraft.serverseeker.utils.MultiplayerScreenUtil.addInfoToServerList;

/**
 * Finds servers that live Twitch Minecraft streamers are currently on, by cross-referencing live streams
 * (fetched credential-free via {@link Twitch}) against the scanner's online-player index.
 */
public class StreamSnipeScreen extends WindowScreen {
    private static final int MAX_STREAM_PAGES = 100; // up to ~3000 live streams

    private final JoinMultiplayerScreen multiplayerScreen;

    private record Match(Server server, List<Twitch.Stream> streams) {}

    public StreamSnipeScreen(JoinMultiplayerScreen multiplayerScreen) {
        super(GuiThemes.get(), "Stream snipe");
        this.multiplayerScreen = multiplayerScreen;
    }

    @Override
    public void initWidgets() {
        add(theme.label("Find servers that live Twitch Minecraft streamers are currently on."));
        add(theme.button("Find streamers")).expandX().widget().action = this::run;
    }

    private void run() {
        clear();
        add(theme.label("Fetching Twitch streams...")).expandX();

        MeteorExecutor.execute(() -> {
            List<Twitch.Stream> streams = Twitch.minecraftStreams(MAX_STREAM_PAGES);
            if (streams.isEmpty()) {
                Minecraft.getInstance().execute(() -> showResult(List.of(), "No live Minecraft streams found."));
                return;
            }

            // Map candidate names (both Twitch login and display name) -> stream
            Map<String, Twitch.Stream> byName = new LinkedHashMap<>();
            List<String> names = new ArrayList<>();
            for (Twitch.Stream stream : streams) {
                addName(byName, names, stream.login, stream);
                addName(byName, names, stream.displayName, stream);
            }

            // Batch-query the scanner for servers with any of those players online now
            Map<String, Object> onlinePlayer = new LinkedHashMap<>();
            onlinePlayer.put("caseInsensitive", true);
            onlinePlayer.put("data", names);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("onlinePlayer", onlinePlayer);

            ServersResponse resp = Http.post(Api.BASE + "/servers?includePlayers=true&limit=1000")
                .bodyJson(body)
                .exceptionHandler(e -> LOG.error("Could not fetch stream-snipe servers: ", e))
                .sendJson(ServersResponse.class);

            List<Match> matches = new ArrayList<>();
            if (resp != null && resp.data != null) {
                for (Server server : resp.data) {
                    long lastSeen = server.lastSeenSeconds();
                    List<Twitch.Stream> matched = new ArrayList<>();
                    if (server.playerHistory != null) {
                        for (Server.PlayerEntry player : server.playerHistory) {
                            if (player.lastSession != lastSeen || player.name == null) continue; // online now only
                            Twitch.Stream stream = byName.get(player.name.toLowerCase(Locale.ROOT));
                            if (stream != null && !matched.contains(stream)) matched.add(stream);
                            if (matched.size() >= 10) break;
                        }
                    }
                    if (!matched.isEmpty()) matches.add(new Match(server, matched));
                }
            }

            Minecraft.getInstance().execute(() -> showResult(matches, null));
        });
    }

    private static void addName(Map<String, Twitch.Stream> byName, List<String> names, String name, Twitch.Stream stream) {
        if (name == null || name.isEmpty()) return;
        String key = name.toLowerCase(Locale.ROOT);
        if (byName.putIfAbsent(key, stream) == null) names.add(name);
    }

    private void showResult(List<Match> matches, String message) {
        clear();
        add(theme.button("Back")).expandX().widget().action = this::reload;

        if (message != null) { add(theme.label(message)).expandX(); return; }
        if (matches.isEmpty()) {
            add(theme.label("No streamers found on any scanned server right now.")).expandX();
            return;
        }

        add(theme.label("Found " + matches.size() + " server" + (matches.size() == 1 ? "" : "s") + " with live streamers")).expandX();

        WTable table = add(theme.table()).widget();
        table.add(theme.label("Server"));
        table.add(theme.label("Streamer(s)"));
        table.row();
        table.add(theme.horizontalSeparator()).expandX();
        table.row();

        for (Match match : matches) {
            String address = match.server.address();
            Twitch.Stream first = match.streams.get(0);
            StringBuilder names = new StringBuilder();
            for (int i = 0; i < match.streams.size(); i++) {
                if (i > 0) names.append(", ");
                names.append(match.streams.get(i).displayName);
            }

            table.add(theme.label(address + "  (" + match.server.versionName() + ")"));
            table.add(theme.label(names.toString()));
            table.add(theme.button("Watch")).widget().action = () -> Util.getPlatform().openUri("https://www.twitch.tv/" + first.login);
            table.add(theme.button("Join")).widget().action = () -> ServerResults.join(address);
            table.add(theme.button("Add")).widget().action = () ->
                addInfoToServerList(multiplayerScreen, new ServerData("ServerSeeker " + address, address, ServerData.Type.OTHER));
            table.row();
        }
    }
}
