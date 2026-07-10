package de.damcraft.serverseeker.gui;

import de.damcraft.serverseeker.api.Credits;
import de.damcraft.serverseeker.api.Server;
import de.damcraft.serverseeker.api.ServerQuery;
import de.damcraft.serverseeker.api.ServersResponse;
import meteordevelopment.meteorclient.gui.GuiThemes;
import meteordevelopment.meteorclient.gui.WindowScreen;
import meteordevelopment.meteorclient.gui.widgets.containers.WContainer;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.utils.network.Http;
import meteordevelopment.meteorclient.utils.network.MeteorExecutor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;

import java.util.List;

import static de.damcraft.serverseeker.ServerSeeker.LOG;

public class FindPlayerScreen extends WindowScreen {
    private static final int LIMIT = 50;

    private final JoinMultiplayerScreen multiplayerScreen;
    private boolean showingResults;

    public enum NameOrUUID { Name, UUID }
    public enum When {
        CurrentlyOnline, EverSeen;
        @Override public String toString() {
            return this == CurrentlyOnline ? "Currently online" : "Ever seen (history)";
        }
    }

    private final Settings settings = new Settings();
    private final SettingGroup sg = settings.getDefaultGroup();
    private WContainer settingsContainer;

    private final Setting<When> when = sg.add(new EnumSetting.Builder<When>()
        .name("when").description("Match players online now, or ever seen on the server.").defaultValue(When.EverSeen).build());

    private final Setting<NameOrUUID> nameOrUUID = sg.add(new EnumSetting.Builder<NameOrUUID>()
        .name("name-or-uuid").description("Search by name or UUID.").defaultValue(NameOrUUID.Name).build());

    private final Setting<String> name = sg.add(new StringSetting.Builder()
        .name("name").description("The name to search for.").defaultValue("")
        .visible(() -> nameOrUUID.get() == NameOrUUID.Name).build());

    private final Setting<String> uuid = sg.add(new StringSetting.Builder()
        .name("uuid").description("The UUID to search for.").defaultValue("")
        .visible(() -> nameOrUUID.get() == NameOrUUID.UUID).build());

    public FindPlayerScreen(JoinMultiplayerScreen multiplayerScreen) {
        super(GuiThemes.get(), "Find players");
        this.multiplayerScreen = multiplayerScreen;
    }

    @Override
    public void initWidgets() {
        showingResults = false;
        settingsContainer = add(theme.verticalList()).widget();
        settingsContainer.add(theme.settings(settings)).expandX();
        add(theme.button("Find player")).expandX().widget().action = this::search;
    }

    @Override
    public void tick() {
        super.tick();
        if (!showingResults) settings.tick(settingsContainer, theme);
    }

    private void search() {
        showingResults = true;
        clear();
        add(theme.label("Searching...")).expandX();

        boolean byName = nameOrUUID.get() == NameOrUUID.Name;
        String value = byName ? name.get() : uuid.get();
        boolean online = when.get() == When.CurrentlyOnline;

        ServerQuery q = ServerQuery.servers().add("limit", LIMIT);
        if (online) q.add(byName ? "onlinePlayer" : "onlineUuid", value);
        else q.add(byName ? "playerHistory" : "uuidHistory", value);

        final String url = q.url();
        MeteorExecutor.execute(() -> {
            ServersResponse resp = Http.get(url)
                .exceptionHandler(e -> LOG.error("Could not search players: ", e))
                .sendJson(ServersResponse.class);
            Minecraft.getInstance().execute(() -> render(resp));
        });
    }

    private void render(ServersResponse resp) {
        clear();
        add(theme.button("Back")).expandX().widget().action = () -> { showingResults = false; reload(); };

        if (resp == null) { add(theme.label("Network error")).expandX(); return; }
        if (resp.isError()) { add(theme.label(resp.error)).expandX(); return; }

        Credits.update(resp.credits);

        List<Server> servers = resp.data;
        if (servers == null || servers.isEmpty()) {
            add(theme.label("No servers found")).expandX();
            add(theme.label(Credits.summary())).expandX();
            return;
        }

        add(theme.label("Found " + servers.size() + (servers.size() == LIMIT ? "+" : "") + " servers")).expandX();
        add(theme.label(Credits.summary())).expandX();
        WButton addAll = add(theme.button("Add all")).expandX().widget();
        addAll.action = () -> {
            ServerResults.addAll(servers, multiplayerScreen);
            if (this.minecraft != null) this.minecraft.setScreen(multiplayerScreen);
        };

        WTable table = add(theme.table()).widget();
        ServerResults.fill(theme, table, servers, multiplayerScreen, false);
    }
}
