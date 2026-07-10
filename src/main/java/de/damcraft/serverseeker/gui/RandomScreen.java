package de.damcraft.serverseeker.gui;

import de.damcraft.serverseeker.api.CountResponse;
import de.damcraft.serverseeker.api.Credits;
import de.damcraft.serverseeker.api.Server;
import de.damcraft.serverseeker.api.ServerQuery;
import de.damcraft.serverseeker.api.ServersResponse;
import meteordevelopment.meteorclient.gui.GuiThemes;
import meteordevelopment.meteorclient.gui.WindowScreen;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.utils.network.Http;
import meteordevelopment.meteorclient.utils.network.MeteorExecutor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ServerData;

import java.util.Random;

import static de.damcraft.serverseeker.ServerSeeker.LOG;
import static de.damcraft.serverseeker.utils.MultiplayerScreenUtil.addInfoToServerList;

public class RandomScreen extends WindowScreen {
    private static final Random RANDOM = new Random();
    private static final long WINDOW_SECONDS = 3600; // "recently seen" window, like the bot

    private final JoinMultiplayerScreen multiplayerScreen;
    private WVerticalList content;

    public RandomScreen(JoinMultiplayerScreen multiplayerScreen) {
        super(GuiThemes.get(), "Random server");
        this.multiplayerScreen = multiplayerScreen;
    }

    @Override
    public void initWidgets() {
        content = add(theme.verticalList()).expandX().widget();
        reroll();
    }

    private void reroll() {
        content.clear();
        content.add(theme.label("Getting a random server..."));

        long cutoff = System.currentTimeMillis() / 1000 - WINDOW_SECONDS;
        String countUrl = ServerQuery.count().add("seenAfter", cutoff).url();

        MeteorExecutor.execute(() -> {
            CountResponse count = Http.get(countUrl)
                .exceptionHandler(e -> LOG.error("Could not fetch count: ", e))
                .sendJson(CountResponse.class);
            if (count != null) Credits.update(count.credits);
            long recent = count == null ? 0 : count.data;
            if (recent <= 0) {
                Minecraft.getInstance().execute(() -> render(null));
                return;
            }

            long index = (long) (RANDOM.nextDouble() * recent);
            String url = ServerQuery.servers().add("limit", 1).add("skip", index).add("seenAfter", cutoff).url();
            ServersResponse resp = Http.get(url)
                .exceptionHandler(e -> LOG.error("Could not fetch random server: ", e))
                .sendJson(ServersResponse.class);
            if (resp != null) Credits.update(resp.credits);

            Server server = (resp == null || resp.data == null || resp.data.isEmpty()) ? null : resp.data.get(0);
            Minecraft.getInstance().execute(() -> render(server));
        });
    }

    private void render(Server server) {
        content.clear();

        if (server == null) {
            content.add(theme.label("No recent servers found."));
            content.add(theme.button("Try again")).widget().action = this::reroll;
            return;
        }

        String address = server.address();

        WTable info = content.add(theme.table()).widget();
        info.add(theme.label("Address: ")); info.add(theme.label(address)); info.row();
        info.add(theme.label("Version: ")); info.add(theme.label(server.versionName() + " (" + server.protocol() + ")")); info.row();
        info.add(theme.label("Players: ")); info.add(theme.label(server.players == null ? "?" : server.players.online + "/" + server.players.max)); info.row();
        if (server.geo != null && server.geo.country != null) { info.add(theme.label("Country: ")); info.add(theme.label(server.geo.country)); info.row(); }
        info.add(theme.label("Auth: ")); info.add(theme.label(server.cracked == null ? "Unknown" : server.cracked ? "Cracked" : "Premium")); info.row();

        WHorizontalList actions = content.add(theme.horizontalList()).expandX().widget();
        actions.add(theme.button("Reroll")).expandX().widget().action = this::reroll;
        actions.add(theme.button("Add")).expandX().widget().action = () ->
            addInfoToServerList(multiplayerScreen, new ServerData("ServerSeeker " + address, address, ServerData.Type.OTHER));
        actions.add(theme.button("Join")).expandX().widget().action = () -> ServerResults.join(address);
        actions.add(theme.button("Info")).expandX().widget().action = () -> {
            if (this.minecraft != null) this.minecraft.setScreen(new ServerInfoScreen(address, false));
        };
    }
}
