package de.damcraft.serverseeker.gui;

import de.damcraft.serverseeker.api.Api;
import de.damcraft.serverseeker.api.Credits;
import de.damcraft.serverseeker.api.Server;
import de.damcraft.serverseeker.api.ServersResponse;
import de.damcraft.serverseeker.api.Twitch;
import meteordevelopment.meteorclient.gui.GuiThemes;
import meteordevelopment.meteorclient.gui.WindowScreen;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;
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

public class StreamSnipeScreen extends WindowScreen {
    private static final int LIMIT = 100;
    private static final int HELIX_MAX_PAGES = 50;

    private static String savedClientId = "";
    private static String savedClientSecret = "";

    private final JoinMultiplayerScreen multiplayerScreen;

    private WTextBox clientIdBox;
    private WTextBox clientSecretBox;

    private Map<String, Twitch.Stream> byName;
    private List<String> names;
    private int page;
    private int streamerCount;
    private boolean usedHelix;

    private record Match(Server server, List<Twitch.Stream> streams) {}

    public StreamSnipeScreen(JoinMultiplayerScreen multiplayerScreen) {
        super(GuiThemes.get(), "Stream snipe");
        this.multiplayerScreen = multiplayerScreen;
    }

    @Override
    public void initWidgets() {
        add(theme.label("Finds servers that live Twitch Minecraft streamers are currently on."));
        add(theme.label("Leave the Twitch fields blank for zero-setup (top 100 streamers)."));
        add(theme.label("Optional: a free Twitch app Client ID + Secret unlocks the full streamer list."));

        add(theme.label("Twitch Client ID (optional):"));
        clientIdBox = add(theme.textBox(savedClientId)).minWidth(500).expandX().widget();

        add(theme.label("Twitch Client Secret (optional):"));
        clientSecretBox = add(theme.textBox(savedClientSecret)).minWidth(500).expandX().widget();

        add(theme.button("Find streamers")).expandX().widget().action = this::start;
    }

    private void start() {
        page = 0;
        savedClientId = clientIdBox.get().trim();
        savedClientSecret = clientSecretBox.get().trim();

        String id = savedClientId;
        String secret = savedClientSecret;

        clear();
        add(theme.label("Fetching Twitch streams...")).expandX();

        MeteorExecutor.execute(() -> {
            List<Twitch.Stream> streams;
            if (!id.isEmpty() && !secret.isEmpty()) {
                String token = Twitch.helixToken(id, secret);
                if (token == null) {
                    Minecraft.getInstance().execute(() -> message("Twitch authentication failed - check your Client ID and Secret."));
                    return;
                }
                streams = Twitch.helixMinecraftStreams(id, token, HELIX_MAX_PAGES);
                usedHelix = true;
            } else {
                streams = Twitch.anonymousMinecraftStreams();
                usedHelix = false;
            }

            if (streams.isEmpty()) {
                Minecraft.getInstance().execute(() -> message("No live Minecraft streams found."));
                return;
            }

            streamerCount = streams.size();
            byName = new LinkedHashMap<>();
            names = new ArrayList<>();
            for (Twitch.Stream stream : streams) {
                addName(stream.login, stream);
                addName(stream.displayName, stream);
            }

            loadPage();
        });
    }

    private void loadPage() {
        String url = Api.BASE + "/servers?includePlayers=true&sort=lastSeen&descending=true&limit=" + LIMIT + "&skip=" + (page * LIMIT);

        Map<String, Object> onlinePlayer = new LinkedHashMap<>();
        onlinePlayer.put("caseInsensitive", true);
        onlinePlayer.put("data", names);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("onlinePlayer", onlinePlayer);

        ServersResponse resp = Http.post(url)
            .bodyJson(body)
            .exceptionHandler(e -> LOG.error("Could not fetch stream-snipe servers: ", e))
            .sendJson(ServersResponse.class);

        Credits.update(resp == null ? null : resp.credits);

        List<Match> matches = new ArrayList<>();
        int raw = 0;
        if (resp != null && resp.data != null) {
            raw = resp.data.size();
            for (Server server : resp.data) {
                long lastSeen = server.lastSeenSeconds();
                List<Twitch.Stream> matched = new ArrayList<>();
                if (server.playerHistory != null) {
                    for (Server.PlayerEntry player : server.playerHistory) {
                        if (player.lastSession != lastSeen || player.name == null) continue;
                        Twitch.Stream stream = byName.get(player.name.toLowerCase(Locale.ROOT));
                        if (stream != null && !matched.contains(stream)) matched.add(stream);
                        if (matched.size() >= 10) break;
                    }
                }
                if (!matched.isEmpty()) matches.add(new Match(server, matched));
            }
        }

        boolean hasNext = raw == LIMIT;
        boolean networkError = resp == null;
        Minecraft.getInstance().execute(() -> render(matches, hasNext, networkError));
    }

    private void addName(String name, Twitch.Stream stream) {
        if (name == null || name.isEmpty()) return;
        String key = name.toLowerCase(Locale.ROOT);
        if (byName.putIfAbsent(key, stream) == null) names.add(name);
    }

    private void render(List<Match> matches, boolean hasNext, boolean networkError) {
        clear();

        WHorizontalList nav = add(theme.horizontalList()).expandX().widget();
        nav.add(theme.button("Back")).expandX().widget().action = this::reload;
        nav.add(theme.button("< Prev")).expandX().widget().action = () -> { if (page > 0) { page--; loadingPage(); } };
        nav.add(theme.button("Next >")).expandX().widget().action = () -> { if (hasNext) { page++; loadingPage(); } };

        add(theme.label("Page " + (page + 1) + " • " + matches.size() + " with streamers • scanned " + streamerCount + " streamers (" + (usedHelix ? "Helix full" : "anonymous top 100") + ")")).expandX();
        add(theme.label(Credits.summary())).expandX();

        if (networkError) { add(theme.label("Network error")).expandX(); return; }
        if (matches.isEmpty()) { add(theme.label("No streamers found on scanned servers on this page.")).expandX(); return; }

        WTable table = add(theme.table()).widget();
        table.add(theme.label("Server"));
        table.add(theme.label("Streamer(s)"));
        table.row();
        table.add(theme.horizontalSeparator()).expandX();
        table.row();

        for (Match match : matches) {
            String address = match.server.address();
            Twitch.Stream first = match.streams.get(0);
            StringBuilder streamerNames = new StringBuilder();
            for (int i = 0; i < match.streams.size(); i++) {
                if (i > 0) streamerNames.append(", ");
                streamerNames.append(match.streams.get(i).displayName);
            }

            table.add(theme.label(address + "  (" + match.server.versionName() + ")"));
            table.add(theme.label(streamerNames.toString()));
            table.add(theme.button("Watch")).widget().action = () -> Util.getPlatform().openUri("https://www.twitch.tv/" + first.login);
            table.add(theme.button("Join")).widget().action = () -> ServerResults.join(address);
            table.add(theme.button("Add")).widget().action = () ->
                addInfoToServerList(multiplayerScreen, new ServerData("ServerSeeker " + address, address, ServerData.Type.OTHER));
            table.row();
        }
    }

    private void loadingPage() {
        clear();
        add(theme.label("Loading page " + (page + 1) + "...")).expandX();
        MeteorExecutor.execute(this::loadPage);
    }

    private void message(String text) {
        clear();
        add(theme.label(text)).expandX();
        add(theme.button("Back")).expandX().widget().action = this::reload;
    }
}
