package de.damcraft.serverseeker.gui;

import de.damcraft.serverseeker.ServerSeeker;
import de.damcraft.serverseeker.api.CountResponse;
import de.damcraft.serverseeker.api.IpUtil;
import de.damcraft.serverseeker.api.Server;
import de.damcraft.serverseeker.api.ServerQuery;
import de.damcraft.serverseeker.api.ServersResponse;
import de.damcraft.serverseeker.country.Country;
import de.damcraft.serverseeker.country.CountrySetting;
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

public class FindNewServersScreen extends WindowScreen {
    private static final int LIMIT = 10;
    public static CompoundTag savedSettings;

    private final JoinMultiplayerScreen multiplayerScreen;

    private int page;
    private long total = -1;
    private boolean showingResults;

    public enum NumRangeType {
        Any, Equals, AtLeast, AtMost, Between;
        @Override public String toString() {
            return switch (this) {
                case Any -> "Any";
                case Equals -> "Equal To";
                case AtLeast -> "At Least";
                case AtMost -> "At Most";
                case Between -> "Between";
            };
        }
    }

    public enum Sort {
        None, LastPingNewOld, LastPingOldNew, DiscoveredNewOld, DiscoveredOldNew;
        @Override public String toString() {
            return switch (this) {
                case None -> "None";
                case LastPingNewOld -> "Last Ping (new to old)";
                case LastPingOldNew -> "Last Ping (old to new)";
                case DiscoveredNewOld -> "Discovered (new to old)";
                case DiscoveredOldNew -> "Discovered (old to new)";
            };
        }
    }

    public enum SeenAfter {
        Any, LastHour, Last6Hours, LastDay;
        @Override public String toString() {
            return switch (this) {
                case Any -> "Any";
                case LastHour -> "Last hour";
                case Last6Hours -> "Last 6 hours";
                case LastDay -> "Last day";
            };
        }
        public long secondsAgo() {
            return switch (this) {
                case Any -> -1;
                case LastHour -> 3600;
                case Last6Hours -> 21600;
                case LastDay -> 86400;
            };
        }
    }

    private final Settings settings = new Settings();
    private final SettingGroup sg = settings.getDefaultGroup();
    private final SettingGroup sgAdv = settings.createGroup("Advanced");
    WContainer settingsContainer;

    // Curated
    private final Setting<TriState> cracked = sg.add(new EnumSetting.Builder<TriState>()
        .name("cracked").description("Whether the server is in offline mode.").defaultValue(TriState.Any).build());

    private final Setting<NumRangeType> onlinePlayersType = sg.add(new EnumSetting.Builder<NumRangeType>()
        .name("online-players-range").description("How to match the online player count.").defaultValue(NumRangeType.Any).build());
    private final Setting<Integer> onlinePlayersMin = sg.add(new IntSetting.Builder()
        .name("online-players-min").description("Minimum online players.").defaultValue(1).min(0).noSlider()
        .visible(() -> onlinePlayersType.get() != NumRangeType.Any && onlinePlayersType.get() != NumRangeType.AtMost).build());
    private final Setting<Integer> onlinePlayersMax = sg.add(new IntSetting.Builder()
        .name("online-players-max").description("Maximum online players.").defaultValue(20).min(0).noSlider()
        .visible(() -> onlinePlayersType.get() == NumRangeType.AtMost || onlinePlayersType.get() == NumRangeType.Between).build());

    private final Setting<Integer> playerCap = sg.add(new IntSetting.Builder()
        .name("player-cap").description("Exact max-player capacity (-1 = any).").defaultValue(-1).min(-1).noSlider().build());

    private final Setting<String> version = sg.add(new StringSetting.Builder()
        .name("version").description("Version name contains (e.g. 1.21).").defaultValue("").build());

    private final Setting<String> description = sg.add(new StringSetting.Builder()
        .name("MOTD").description("Text the server description must contain.").defaultValue("").build());

    private final Setting<Country> country = sg.add(new CountrySetting.Builder()
        .name("country").description("Country the server is hosted in.").defaultValue(ServerSeeker.COUNTRY_MAP.get("UN")).build());

    private final Setting<String> onlinePlayer = sg.add(new StringSetting.Builder()
        .name("online-player").description("Name of a player currently online.").defaultValue("").build());

    private final Setting<TriState> whitelisted = sg.add(new EnumSetting.Builder<TriState>()
        .name("whitelisted").description("Whether the server has a whitelist.").defaultValue(TriState.Any).build());

    // Advanced
    private final Setting<Boolean> advanced = sgAdv.add(new BoolSetting.Builder()
        .name("advanced").description("Show advanced filters.").defaultValue(false).build());

    private final Setting<Sort> sort = sgAdv.add(new EnumSetting.Builder<Sort>()
        .name("sort").description("Result ordering.").defaultValue(Sort.LastPingNewOld).visible(advanced::get).build());

    private final Setting<SeenAfter> seenAfter = sgAdv.add(new EnumSetting.Builder<SeenAfter>()
        .name("seen-after").description("Only servers pinged within this window.").defaultValue(SeenAfter.Any).visible(advanced::get).build());

    private final Setting<Integer> protocol = sgAdv.add(new IntSetting.Builder()
        .name("protocol").description("Exact protocol version (-1 = any).").defaultValue(-1).min(-1).noSlider().visible(advanced::get).build());

    private final Setting<Integer> port = sgAdv.add(new IntSetting.Builder()
        .name("port").description("Server port (-1 = any).").defaultValue(-1).min(-1).noSlider().visible(advanced::get).build());

    private final Setting<String> org = sgAdv.add(new StringSetting.Builder()
        .name("org").description("Hosting organization contains.").defaultValue("").visible(advanced::get).build());

    private final Setting<String> ipRange = sgAdv.add(new StringSetting.Builder()
        .name("ip-range").description("IP or CIDR subnet (e.g. 1.2.3.0/24).").defaultValue("").visible(advanced::get).build());

    private final Setting<String> excludeRange = sgAdv.add(new StringSetting.Builder()
        .name("exclude-range").description("IP or CIDR subnet to exclude.").defaultValue("").visible(advanced::get).build());

    private final Setting<TriState> hasFavicon = sgAdv.add(new EnumSetting.Builder<TriState>()
        .name("has-favicon").description("Whether the server has a custom icon.").defaultValue(TriState.Any).visible(advanced::get).build());

    private final Setting<TriState> hasPlayerList = sgAdv.add(new EnumSetting.Builder<TriState>()
        .name("has-player-list").description("Whether the server exposes a player sample.").defaultValue(TriState.Any).visible(advanced::get).build());

    private final Setting<TriState> vanilla = sgAdv.add(new EnumSetting.Builder<TriState>()
        .name("vanilla").description("Whether the server is vanilla.").defaultValue(TriState.Any).visible(advanced::get).build());

    private final Setting<TriState> isFull = sgAdv.add(new EnumSetting.Builder<TriState>()
        .name("is-full").description("Whether the server is full.").defaultValue(TriState.Any).visible(advanced::get).build());

    private final Setting<String> playerHistory = sgAdv.add(new StringSetting.Builder()
        .name("player-history").description("Name of a player ever seen on the server.").defaultValue("").visible(advanced::get).build());

    private final Setting<String> uuidHistory = sgAdv.add(new StringSetting.Builder()
        .name("uuid-history").description("UUID of a player ever seen on the server.").defaultValue("").visible(advanced::get).build());

    private final Setting<String> onlineUuid = sgAdv.add(new StringSetting.Builder()
        .name("online-uuid").description("UUID of a player currently online.").defaultValue("").visible(advanced::get).build());

    public FindNewServersScreen(JoinMultiplayerScreen multiplayerScreen) {
        super(GuiThemes.get(), "Find servers");
        this.multiplayerScreen = multiplayerScreen;
    }

    @Override
    public void initWidgets() {
        showingResults = false;
        loadSettings();
        onClosed(this::saveSettings);

        settingsContainer = add(theme.verticalList()).widget();
        settingsContainer.add(theme.settings(settings));

        WHorizontalList buttons = add(theme.horizontalList()).expandX().widget();
        buttons.add(theme.button("Reset all")).expandX().widget().action = this::resetSettings;
        buttons.add(theme.button("Find")).expandX().widget().action = this::runSearch;
    }

    @Override
    public void tick() {
        super.tick();
        if (!showingResults) settings.tick(settingsContainer, theme);
    }

    private void runSearch() {
        page = 0;
        total = -1;
        fetchPage();
    }

    private void fetchPage() {
        saveSettings();
        showingResults = true;
        clear();
        add(theme.label("Searching...")).expandX();

        final String serversUrl = buildServersQuery().url();
        final boolean needCount = total < 0;
        final String countUrl = needCount ? buildFilterQuery(ServerQuery.count()).url() : null;

        MeteorExecutor.execute(() -> {
            ServersResponse resp = Http.get(serversUrl)
                .exceptionHandler(e -> LOG.error("Could not fetch servers: ", e))
                .sendJson(ServersResponse.class);

            long count = total;
            if (needCount) {
                CountResponse cr = Http.get(countUrl)
                    .exceptionHandler(e -> LOG.error("Could not fetch count: ", e))
                    .sendJson(CountResponse.class);
                if (cr != null) count = cr.data;
            }

            final long finalCount = count;
            Minecraft.getInstance().execute(() -> renderResults(resp, finalCount));
        });
    }

    private void renderResults(ServersResponse resp, long count) {
        clear();
        total = count;

        if (resp == null) {
            showError("Network error");
            return;
        }
        if (resp.isError()) {
            showError(resp.error);
            return;
        }

        List<Server> servers = resp.data;
        int shown = servers == null ? 0 : servers.size();
        String totalStr = total < 0 ? "?" : String.valueOf(total);
        add(theme.label("Page " + (page + 1) + " • " + shown + " shown • " + totalStr + " total")).expandX();

        WHorizontalList nav = add(theme.horizontalList()).expandX().widget();
        nav.add(theme.button("Back")).expandX().widget().action = () -> { showingResults = false; reload(); };
        WButton prev = nav.add(theme.button("< Prev")).expandX().widget();
        prev.action = () -> { if (page > 0) { page--; fetchPage(); } };
        WButton next = nav.add(theme.button("Next >")).expandX().widget();
        next.action = () -> { if (shown == LIMIT) { page++; fetchPage(); } };

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
        ServerResults.fill(theme, table, servers, multiplayerScreen, false);
    }

    private void showError(String message) {
        add(theme.label(message)).expandX();
        add(theme.button("Back")).expandX().widget().action = () -> { showingResults = false; reload(); };
    }

    private ServerQuery buildServersQuery() {
        ServerQuery q = buildFilterQuery(ServerQuery.servers());
        q.add("limit", LIMIT);
        q.add("skip", (long) page * LIMIT);
        switch (sort.get()) {
            case LastPingNewOld -> { q.add("sort", "lastSeen"); q.add("descending", true); }
            case LastPingOldNew -> { q.add("sort", "lastSeen"); q.add("descending", false); }
            case DiscoveredNewOld -> { q.add("sort", "discovered"); q.add("descending", true); }
            case DiscoveredOldNew -> { q.add("sort", "discovered"); q.add("descending", false); }
            case None -> {}
        }
        return q;
    }

    private ServerQuery buildFilterQuery(ServerQuery q) {
        Boolean cr = cracked.get().toBoolOrNull();
        if (cr != null) q.add("cracked", cr);

        Boolean wl = whitelisted.get().toBoolOrNull();
        if (wl != null) q.add("whitelisted", wl);

        switch (onlinePlayersType.get()) {
            case Equals -> q.add("playerCount", onlinePlayersMin.get());
            case AtLeast -> q.add("minPlayers", onlinePlayersMin.get());
            case AtMost -> q.add("maxPlayers", onlinePlayersMax.get());
            case Between -> { q.add("minPlayers", onlinePlayersMin.get()); q.add("maxPlayers", onlinePlayersMax.get()); }
            case Any -> {}
        }

        if (playerCap.get() >= 0) q.add("playerLimit", playerCap.get());
        q.add("version", version.get());
        if (!description.get().isEmpty()) q.add("description", "%" + description.get() + "%");
        q.add("onlinePlayer", onlinePlayer.get());

        Country c = country.get();
        if (c != null && !c.code.equalsIgnoreCase("UN")) q.add("country", c.code.toUpperCase());

        // Advanced
        if (protocol.get() >= 0) q.add("protocol", protocol.get());
        if (port.get() >= 0) q.add("port", port.get());
        if (!org.get().isEmpty()) q.add("org", "%" + org.get() + "%");
        addRange(q, ipRange.get(), false);
        addRange(q, excludeRange.get(), true);

        Boolean fav = hasFavicon.get().toBoolOrNull();
        if (fav != null) q.add("hasFavicon", fav);
        Boolean pl = hasPlayerList.get().toBoolOrNull();
        if (pl != null) q.add("hasPlayerSample", pl);
        Boolean van = vanilla.get().toBoolOrNull();
        if (van != null) q.add("vanilla", van);
        Boolean full = isFull.get().toBoolOrNull();
        if (full != null) q.add("full", full);

        q.add("playerHistory", playerHistory.get());
        q.add("uuidHistory", uuidHistory.get());
        q.add("onlineUuid", onlineUuid.get());

        if (seenAfter.get() != SeenAfter.Any) {
            long cutoff = System.currentTimeMillis() / 1000 - seenAfter.get().secondsAgo();
            q.add("seenAfter", cutoff);
        }
        return q;
    }

    private void addRange(ServerQuery q, String value, boolean exclude) {
        if (value == null || value.isBlank()) return;
        try {
            long[] range = IpUtil.cidrToRange(value);
            if (exclude) q.addExcludeRange(range);
            else q.addIpRange(range);
        } catch (Exception e) {
            LOG.warn("Invalid IP range '{}': {}", value, e.getMessage());
        }
    }

    public void saveSettings() {
        savedSettings = settings.toTag();
    }

    public void loadSettings() {
        if (savedSettings != null) settings.fromTag(savedSettings);
    }

    public void resetSettings() {
        settings.reset();
        saveSettings();
        reload();
    }

    @Override
    protected void onClosed() {
        ServerSeeker.COUNTRY_MAP.values().forEach(Country::dispose);
    }
}
