package de.damcraft.serverseeker.utils;

import de.damcraft.serverseeker.api.IpUtil;
import de.damcraft.serverseeker.api.Server;
import de.damcraft.serverseeker.api.ServerQuery;
import de.damcraft.serverseeker.api.ServersResponse;
import de.damcraft.serverseeker.hud.HistoricPlayersHud;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.utils.network.Http;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.multiplayer.ClientPacketListener;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

import static de.damcraft.serverseeker.ServerSeeker.LOG;
import static meteordevelopment.meteorclient.MeteorClient.mc;

public class HistoricPlayersUpdater {
    @EventHandler
    private static void onGameJoinEvent(GameJoinedEvent ignoredEvent) {
        new Thread(HistoricPlayersUpdater::update).start();
    }

    public static void update() {
        List<HistoricPlayersHud> huds = new ArrayList<>();
        for (HudElement hudElement : Hud.get()) {
            if (hudElement instanceof HistoricPlayersHud && hudElement.isActive()) {
                huds.add((HistoricPlayersHud) hudElement);
            }
        }
        if (huds.isEmpty()) return;

        ClientPacketListener networkHandler = mc.getConnection();
        if (networkHandler == null) return;

        String address = networkHandler.getConnection().getRemoteAddress().toString();
        String[] addressParts = address.split("/");
        if (addressParts.length < 2) return;
        addressParts = addressParts[1].split(":");

        String host = addressParts[0];
        int port = Integer.parseInt(addressParts[1]);

        long ipInt;
        try {
            ipInt = IpUtil.stringToInt(host);
        } catch (Exception notNumeric) {
            try {
                ipInt = IpUtil.stringToInt(InetAddress.getByName(host).getHostAddress());
            } catch (Exception e) {
                return;
            }
        }

        String url = ServerQuery.servers().add("ip", ipInt).add("port", port).add("includePlayers", true).url();
        ServersResponse response = Http.get(url)
            .exceptionHandler(e -> LOG.error("Could not fetch server info: ", e))
            .sendJson(ServersResponse.class);

        if (response == null || response.data == null || response.data.isEmpty()) return;

        Server server = response.data.get(0);
        for (HistoricPlayersHud hud : huds) {
            hud.players = server.playerHistory != null ? server.playerHistory : List.of();
            hud.isCracked = server.cracked != null && server.cracked;
        }
    }
}
