package de.damcraft.serverseeker.gui;

import de.damcraft.serverseeker.api.IpUtil;
import de.damcraft.serverseeker.api.Server;
import de.damcraft.serverseeker.api.ServerQuery;
import de.damcraft.serverseeker.api.ServersResponse;
import meteordevelopment.meteorclient.gui.GuiThemes;
import meteordevelopment.meteorclient.gui.WindowScreen;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.systems.accounts.Account;
import meteordevelopment.meteorclient.systems.accounts.Accounts;
import meteordevelopment.meteorclient.systems.accounts.types.CrackedAccount;
import meteordevelopment.meteorclient.utils.network.Http;
import meteordevelopment.meteorclient.utils.network.MeteorExecutor;
import net.minecraft.client.Minecraft;

import java.net.InetAddress;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.List;

import static de.damcraft.serverseeker.ServerSeeker.LOG;
import static meteordevelopment.meteorclient.MeteorClient.mc;

public class ServerInfoScreen extends WindowScreen {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT);

    private final String address;
    private final boolean bedrock;

    public ServerInfoScreen(String address, boolean bedrock) {
        super(GuiThemes.get(), "Server Info: " + address);
        this.address = address;
        this.bedrock = bedrock;
    }

    @Override
    public void initWidgets() {
        add(theme.label("Fetching server info..."));

        MeteorExecutor.execute(() -> {
            String host = address.split(":")[0];
            String[] parts = address.split(":");
            int port = parts.length > 1 ? parseIntOr(parts[1], bedrock ? 19132 : 25565) : (bedrock ? 19132 : 25565);

            long ipInt;
            try {
                ipInt = IpUtil.stringToInt(host);
            } catch (Exception notNumeric) {
                try {
                    ipInt = IpUtil.stringToInt(InetAddress.getByName(host).getHostAddress());
                } catch (Exception e) {
                    Minecraft.getInstance().execute(() -> { clear(); add(theme.label("Could not resolve " + host)); });
                    return;
                }
            }

            ServerQuery q = (bedrock ? ServerQuery.bedrockServers() : ServerQuery.servers())
                .add("ip", ipInt)
                .add("port", port);
            if (!bedrock) q.add("includePlayers", true);

            ServersResponse resp = Http.get(q.url())
                .exceptionHandler(e -> LOG.error("Could not fetch server info: ", e))
                .sendJson(ServersResponse.class);

            Minecraft.getInstance().execute(() -> render(resp));
        });
    }

    private void render(ServersResponse resp) {
        clear();

        if (resp == null) { add(theme.label("Network error")).expandX(); return; }
        if (resp.isError()) { add(theme.label(resp.error)).expandX(); return; }
        if (resp.data == null || resp.data.isEmpty()) {
            add(theme.label("Server not found in the database.")).expandX();
            add(theme.button("Join anyway")).expandX().widget().action = () -> ServerResults.join(address);
            return;
        }

        Server server = resp.data.get(0);

        WTable info = add(theme.table()).widget();
        row(info, "Version", server.versionName() + " (" + server.protocol() + ")");
        row(info, "Description", trim(server.description));
        if (server.players != null) row(info, "Players", server.players.online + "/" + server.players.max);
        if (bedrock && server.gamemode != null) row(info, "Game mode", server.gamemode.name + " (" + server.gamemode.id + ")");
        if (bedrock && server.education != null) row(info, "Education Edition", server.education.toString());
        if (server.geo != null && server.geo.country != null) row(info, "Country", server.geo.country);
        if (server.org != null) row(info, "Organization", server.org);
        if (!bedrock) row(info, "Auth", server.cracked == null ? "Unknown" : server.cracked ? "Cracked" : "Premium");
        if (!bedrock) row(info, "Whitelist", server.whitelisted == null ? "Unknown" : server.whitelisted ? "Enabled" : "Disabled");
        row(info, "Discovered", date(server.discoveredSeconds()));
        row(info, "Last seen", date(server.lastSeenSeconds()));

        add(theme.button("Join this server")).expandX().widget().action = () -> ServerResults.join(address);

        List<Server.PlayerEntry> history = server.playerHistory;
        if (history != null && !history.isEmpty()) {
            boolean cracked = server.cracked != null && server.cracked;
            if (!cracked) add(theme.label("Note: this server is not cracked; login may not work.")).expandX();

            add(theme.label("Player history:"));
            WTable players = add(theme.table()).widget();
            players.add(theme.label("Name "));
            players.add(theme.label("Last seen "));
            players.add(theme.label("Login (cracked)"));
            players.row();
            players.add(theme.horizontalSeparator()).expandX();
            players.row();

            history.sort((a, b) -> Long.compare(b.lastSession, a.lastSession));
            for (Server.PlayerEntry player : history) {
                players.add(theme.label(player.name + " "));
                players.add(theme.label(date(player.lastSession) + " "));

                if (mc.getUser().getName().equals(player.name)) {
                    players.add(theme.label("Logged in")).expandCellX();
                } else {
                    WButton login = players.add(theme.button("Login")).widget();
                    login.action = () -> {
                        login.visible = false;
                        loginCracked(player.name);
                    };
                }
                players.row();
            }
        }
    }

    private static void loginCracked(String name) {
        for (Account<?> account : Accounts.get()) {
            if (account instanceof CrackedAccount && account.getUsername().equals(name)) {
                account.login();
                return;
            }
        }
        CrackedAccount account = new CrackedAccount(name);
        account.login();
        Accounts.get().add(account);
    }

    private void row(WTable table, String key, String value) {
        table.add(theme.label(key + ": "));
        table.add(theme.label(value == null ? "" : value));
        table.row();
    }

    private static String date(long seconds) {
        if (seconds <= 0) return "Unknown";
        return DATE.format(Instant.ofEpochSecond(seconds).atZone(ZoneId.systemDefault()).toLocalDateTime());
    }

    private static String trim(String s) {
        if (s == null) return "";
        s = s.replace("\n", "\\n").replace("§r", "");
        return s.length() > 100 ? s.substring(0, 100) + "..." : s;
    }

    private static int parseIntOr(String s, int fallback) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return fallback; }
    }
}
