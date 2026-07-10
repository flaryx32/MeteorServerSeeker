package de.damcraft.serverseeker.gui;

import de.damcraft.serverseeker.ServerSeeker;
import de.damcraft.serverseeker.api.CountResponse;
import de.damcraft.serverseeker.api.Credits;
import de.damcraft.serverseeker.api.IpUtil;
import de.damcraft.serverseeker.api.Server;
import de.damcraft.serverseeker.api.ServerQuery;
import de.damcraft.serverseeker.api.ServersResponse;
import de.damcraft.serverseeker.country.Country;
import de.damcraft.serverseeker.country.CountrySetting;
import de.damcraft.serverseeker.gui.FindNewServersScreen.NumRangeType;
import de.damcraft.serverseeker.gui.FindNewServersScreen.SeenAfter;
import de.damcraft.serverseeker.gui.FindNewServersScreen.Sort;
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
import net.minecraft.nbt.CompoundTag;

import java.util.List;

import static de.damcraft.serverseeker.ServerSeeker.LOG;

public class BedrockSearchScreen extends WindowScreen {
    private static final int LIMIT = 10;
    public static CompoundTag savedSettings;

    private final JoinMultiplayerScreen multiplayerScreen;
    private int page;
    private long total = -1;
    private boolean showingResults;

    private final Settings settings = new Settings();
    private final SettingGroup sg = settings.getDefaultGroup();
    private final SettingGroup sgAdv = settings.createGroup("Advanced");
    private WContainer settingsContainer;

    // Curated
    private final Setting<NumRangeType> onlinePlayersType = sg.add(new EnumSetting.Builder<NumRangeType>()
        .name("online-players-range").description("How to match the online player count.").defaultValue(NumRangeType.Any).build());
    private final Setting<Integer> onlinePlayersMin = sg.add(new IntSetting.Builder()
        .name("online-players-min").description("Minimum online players.").defaultValue(1).min(0).noSlider()
        .visible(() -> onlinePlayersType.get() != NumRangeType.Any && onlinePlayersType.get() != NumRangeType.AtMost).build());
    private final Setting<Integer> onlinePlayersMax = sg.add(new IntSetting.Builder()
        .name("online-players-max").description("Maximum online players.").defaultValue(20).min(0).noSlider()
        .visible(() -> onlinePlayersType.get() == NumRangeType.AtMost || onlinePlayersType.get() == NumRangeType.Between).build());

    private final Setting<String> version = sg.add(new StringSetting.Builder()
        .name("version").description("Version name contains.").defaultValue("").build());
    private final Setting<String> gamemode = sg.add(new StringSetting.Builder()
        .name("gamemode").description("Exact game mode, e.g. Survival (not a partial match).").defaultValue("").build());
    private final Setting<String> description = sg.add(new StringSetting.Builder()
        .name("MOTD").description("Text the server description must contain.").defaultValue("").build());
    private final Setting<Country> country = sg.add(new CountrySetting.Builder()
        .name("country").description("Country the server is hosted in.").defaultValue(ServerSeeker.COUNTRY_MAP.get("UN")).build());

    // Advanced
    private final Setting<Boolean> advanced = sgAdv.add(new BoolSetting.Builder()
        .name("advanced").description("Show advanced filters.").defaultValue(false).build());
    private final Setting<Sort> sort = sgAdv.add(new EnumSetting.Builder<Sort>()
        .name("sort").description("Result ordering.").defaultValue(Sort.LastPingNewOld).visible(advanced::get).build());
    private final Setting<SeenAfter> seenAfter = sgAdv.add(new EnumSetting.Builder<SeenAfter>()
        .name("seen-after").description("Only servers pinged within this window.").defaultValue(SeenAfter.Any).visible(advanced::get).build());
    private final Setting<Integer> playerCap = sgAdv.add(new IntSetting.Builder()
        .name("player-cap").description("Exact max-player capacity (-1 = any).").defaultValue(-1).min(-1).noSlider().visible(advanced::get).build());
    private final Setting<TriState> isFull = sgAdv.add(new EnumSetting.Builder<TriState>()
        .name("is-full").description("Whether the server is full.").defaultValue(TriState.Any).visible(advanced::get).build());
    private final Setting<Integer> protocol = sgAdv.add(new IntSetting.Builder()
        .name("protocol").description("Exact protocol version (-1 = any).").defaultValue(-1).min(-1).noSlider().visible(advanced::get).build());
    private final Setting<Integer> port = sgAdv.add(new IntSetting.Builder()
        .name("port").description("Server port (-1 = any).").defaultValue(-1).min(-1).noSlider().visible(advanced::get).build());
    private final Setting<String> org = sgAdv.add(new StringSetting.Builder()
        .name("org").description("Hosting organization contains.").defaultValue("").visible(advanced::get).build());
    private final Setting<String> ipRange = sgAdv.add(new StringSetting.Builder()
        .name("ip-range").description("IP or CIDR subnet (e.g. 1.2.3.0/24).").defaultValue("").visible(advanced::get).build());

    public BedrockSearchScreen(JoinMultiplayerScreen multiplayerScreen) {
        super(GuiThemes.get(), "Bedrock search");
        this.multiplayerScreen = multiplayerScreen;
    }

    @Override
    public void initWidgets() {
        showingResults = false;
        if (savedSettings != null) settings.fromTag(savedSettings);
        onClosed(() -> savedSettings = settings.toTag());

        settingsContainer = add(theme.verticalList()).widget();
        settingsContainer.add(theme.settings(settings));

        WHorizontalList buttons = add(theme.horizontalList()).expandX().widget();
        buttons.add(theme.button("Reset all")).expandX().widget().action = () -> { settings.reset(); reload(); };
        buttons.add(theme.button("Find")).expandX().widget().action = () -> { page = 0; total = -1; fetchPage(); };
    }

    @Override
    public void tick() {
        super.tick();
        if (!showingResults) settings.tick(settingsContainer, theme);
    }

    private void fetchPage() {
        savedSettings = settings.toTag();
        showingResults = true;
        clear();
        add(theme.label("Searching...")).expandX();

        final String serversUrl = buildQuery(ServerQuery.bedrockServers()).add("limit", LIMIT).add("skip", (long) page * LIMIT).url();
        final boolean needCount = total < 0;
        final String countUrl = needCount ? buildQuery(ServerQuery.bedrockCount()).url() : null;

        MeteorExecutor.execute(() -> {
            ServersResponse resp = Http.get(serversUrl)
                .exceptionHandler(e -> LOG.error("Could not fetch bedrock servers: ", e))
                .sendJson(ServersResponse.class);
            long count = total;
            if (needCount) {
                CountResponse cr = Http.get(countUrl)
                    .exceptionHandler(e -> LOG.error("Could not fetch bedrock count: ", e))
                    .sendJson(CountResponse.class);
                if (cr != null) count = cr.data;
            }
            final long finalCount = count;
            Minecraft.getInstance().execute(() -> render(resp, finalCount));
        });
    }

    private void render(ServersResponse resp, long count) {
        clear();
        total = count;

        add(theme.button("Back")).expandX().widget().action = () -> { showingResults = false; reload(); };

        if (resp == null) { add(theme.label("Network error")).expandX(); return; }
        if (resp.isError()) { add(theme.label(resp.error)).expandX(); return; }

        Credits.update(resp.credits);

        List<Server> servers = resp.data;
        int shown = servers == null ? 0 : servers.size();
        String totalStr = total < 0 ? "?" : String.valueOf(total);
        add(theme.label("Page " + (page + 1) + " • " + shown + " shown • " + totalStr + " total")).expandX();
        add(theme.label(Credits.summary())).expandX();

        WHorizontalList nav = add(theme.horizontalList()).expandX().widget();
        nav.add(theme.button("< Prev")).expandX().widget().action = () -> { if (page > 0) { page--; fetchPage(); } };
        nav.add(theme.button("Next >")).expandX().widget().action = () -> { if (shown == LIMIT) { page++; fetchPage(); } };

        if (servers == null || servers.isEmpty()) {
            add(theme.label("No servers found")).expandX();
            return;
        }

        WButton addAll = add(theme.button("Add all on this page")).expandX().widget();
        addAll.action = () -> {
            ServerResults.addAll(servers, multiplayerScreen);
            if (this.minecraft != null) this.minecraft.setScreen(multiplayerScreen);
        };

        WTable table = add(theme.table()).widget();
        ServerResults.fill(theme, table, servers, multiplayerScreen, true);
    }

    private ServerQuery buildQuery(ServerQuery q) {
        switch (onlinePlayersType.get()) {
            case Equals -> q.add("playerCount", onlinePlayersMin.get());
            case AtLeast -> q.add("minPlayers", onlinePlayersMin.get());
            case AtMost -> q.add("maxPlayers", onlinePlayersMax.get());
            case Between -> { q.add("minPlayers", onlinePlayersMin.get()); q.add("maxPlayers", onlinePlayersMax.get()); }
            case Any -> {}
        }
        q.add("version", version.get());
        q.add("gamemode", gamemode.get());
        if (!description.get().isEmpty()) q.add("description", "%" + description.get() + "%");
        Country c = country.get();
        if (c != null && !c.code.equalsIgnoreCase("UN")) q.add("country", c.code.toUpperCase());

        if (playerCap.get() >= 0) q.add("playerLimit", playerCap.get());
        Boolean full = isFull.get().toBoolOrNull();
        if (full != null) q.add("full", full);
        if (protocol.get() >= 0) q.add("protocol", protocol.get());
        if (port.get() >= 0) q.add("port", port.get());
        if (!org.get().isEmpty()) q.add("org", "%" + org.get() + "%");
        if (!ipRange.get().isBlank()) {
            try { q.addIpRange(IpUtil.cidrToRange(ipRange.get())); }
            catch (Exception e) { LOG.warn("Invalid IP range '{}': {}", ipRange.get(), e.getMessage()); }
        }
        if (seenAfter.get() != SeenAfter.Any) {
            q.add("seenAfter", System.currentTimeMillis() / 1000 - seenAfter.get().secondsAgo());
        }
        switch (sort.get()) {
            case LastPingNewOld -> { q.add("sort", "lastSeen"); q.add("descending", true); }
            case LastPingOldNew -> { q.add("sort", "lastSeen"); q.add("descending", false); }
            case DiscoveredNewOld -> { q.add("sort", "discovered"); q.add("descending", true); }
            case DiscoveredOldNew -> { q.add("sort", "discovered"); q.add("descending", false); }
            case None -> {}
        }
        return q;
    }

    @Override
    protected void onClosed() {
        ServerSeeker.COUNTRY_MAP.values().forEach(Country::dispose);
    }
}
