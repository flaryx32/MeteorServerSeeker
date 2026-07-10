package de.damcraft.serverseeker.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import de.damcraft.serverseeker.api.Api;
import meteordevelopment.meteorclient.gui.GuiThemes;
import meteordevelopment.meteorclient.gui.WindowScreen;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;
import meteordevelopment.meteorclient.utils.network.Http;
import meteordevelopment.meteorclient.utils.network.MeteorExecutor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ServerData;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import static de.damcraft.serverseeker.ServerSeeker.gson;
import static de.damcraft.serverseeker.ServerSeeker.LOG;
import static de.damcraft.serverseeker.utils.MultiplayerScreenUtil.addInfoToServerList;

public class PingScreen extends WindowScreen {
    private final JoinMultiplayerScreen multiplayerScreen;
    private final String prefill;
    private WTextBox addressBox;
    private WVerticalList results;

    public PingScreen(JoinMultiplayerScreen multiplayerScreen) {
        this(multiplayerScreen, "");
    }

    public PingScreen(JoinMultiplayerScreen multiplayerScreen, String prefill) {
        super(GuiThemes.get(), "Ping server");
        this.multiplayerScreen = multiplayerScreen;
        this.prefill = prefill;
    }

    @Override
    public void initWidgets() {
        WHorizontalList row = add(theme.horizontalList()).expandX().widget();
        addressBox = row.add(theme.textBox(prefill)).minWidth(300).expandX().widget();
        row.add(theme.button("Ping")).widget().action = this::ping;

        results = add(theme.verticalList()).expandX().widget();

        if (!prefill.isEmpty()) ping();
    }

    private void ping() {
        String address = addressBox.get().trim();
        if (address.isEmpty()) return;

        String[] parts = address.split(":");
        String host = parts[0];
        int port = parts.length > 1 ? parseIntOr(parts[1], 25565) : 25565;
        String display = port == 25565 ? host : host + ":" + port;

        results.clear();
        results.add(theme.label("Pinging " + display + "..."));

        String url = Api.PING + "/ping?ip=" + enc(host) + "&port=" + port;
        MeteorExecutor.execute(() -> {
            String text = Http.get(url).exceptionHandler(e -> LOG.error("Could not ping: ", e)).sendString();
            Minecraft.getInstance().execute(() -> render(text, display));
        });
    }

    private void render(String text, String display) {
        results.clear();

        if (text == null) { results.add(theme.label("Network error")); return; }
        if (text.startsWith("Error") || text.equals("timeout")) { results.add(theme.label(text)); return; }

        JsonObject json;
        try {
            json = gson.fromJson(text, JsonObject.class);
        } catch (Exception e) {
            results.add(theme.label("Could not parse ping response"));
            return;
        }
        if (json == null) { results.add(theme.label("Empty response")); return; }

        String versionName = "?";
        int protocol = 0;
        if (json.has("version") && json.get("version").isJsonObject()) {
            JsonObject v = json.getAsJsonObject("version");
            if (v.has("name")) versionName = v.get("name").getAsString();
            if (v.has("protocol")) protocol = v.get("protocol").getAsInt();
        }

        int online = 0, max = 0;
        JsonArray sample = null;
        if (json.has("players") && json.get("players").isJsonObject()) {
            JsonObject p = json.getAsJsonObject("players");
            if (p.has("online")) online = p.get("online").getAsInt();
            if (p.has("max")) max = p.get("max").getAsInt();
            if (p.has("sample") && p.get("sample").isJsonArray()) sample = p.getAsJsonArray("sample");
        }

        String description = json.has("description") ? flatten(json.get("description")) : "";

        WTable table = results.add(theme.table()).widget();
        rowKV(table, "Address", display);
        rowKV(table, "Version", versionName + " (" + protocol + ")");
        rowKV(table, "Players", online + "/" + max);
        rowKV(table, "Description", trim(description));

        if (sample != null && !sample.isEmpty()) {
            results.add(theme.label("Online players:"));
            WVerticalList list = results.add(theme.verticalList()).widget();
            for (JsonElement el : sample) {
                if (el.isJsonObject() && el.getAsJsonObject().has("name")) {
                    list.add(theme.label(el.getAsJsonObject().get("name").getAsString()));
                }
            }
        }

        WHorizontalList actions = results.add(theme.horizontalList()).expandX().widget();
        actions.add(theme.button("Add")).widget().action = () ->
            addInfoToServerList(multiplayerScreen, new ServerData("ServerSeeker " + display, display, ServerData.Type.OTHER));
        actions.add(theme.button("Join")).widget().action = () -> ServerResults.join(display);
    }

    private void rowKV(WTable table, String key, String value) {
        table.add(theme.label(key + ": "));
        table.add(theme.label(value == null || value.isEmpty() ? "" : value));
        table.row();
    }

    /** Flattens a chat-component description (string, object, or array) to plain text with § codes. */
    private static String flatten(JsonElement element) {
        if (element == null || element.isJsonNull()) return "";
        if (element.isJsonPrimitive()) return element.getAsString();
        if (element.isJsonArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonElement child : element.getAsJsonArray()) sb.append(flatten(child));
            return sb.toString();
        }
        JsonObject obj = element.getAsJsonObject();
        StringBuilder sb = new StringBuilder();
        if (obj.has("text")) sb.append(obj.get("text").getAsString());
        if (obj.has("extra")) sb.append(flatten(obj.get("extra")));
        return sb.toString();
    }

    private static String trim(String s) {
        if (s == null) return "";
        s = s.replace("\n", "\\n");
        return s.length() > 100 ? s.substring(0, 100) + "..." : s;
    }

    private static int parseIntOr(String s, int fallback) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return fallback; }
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
