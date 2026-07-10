package de.damcraft.serverseeker.gui;

import de.damcraft.serverseeker.api.Server;
import de.damcraft.serverseeker.utils.MultiplayerScreenUtil;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;

import java.util.List;

/** Renders a list of {@link Server} results into a table with Add / Join / Info actions. */
public class ServerResults {

    /** Adds header + one row per server to {@code table}. */
    public static void fill(GuiTheme theme, WTable table, List<Server> servers, JoinMultiplayerScreen mps, boolean bedrock) {
        table.add(theme.label("Server"));
        table.add(theme.label("Version"));
        table.add(theme.label("Players"));
        table.row();
        table.add(theme.horizontalSeparator()).expandX();
        table.row();

        for (Server server : servers) {
            String address = server.address();

            table.add(theme.label(address));
            table.add(theme.label(server.versionName()));
            table.add(theme.label(server.players == null ? "?" : server.players.online + "/" + server.players.max));

            WButton addButton = theme.button("Add");
            addButton.action = () -> {
                MultiplayerScreenUtil.addInfoToServerList(mps, new ServerData("ServerSeeker " + address, address, ServerData.Type.OTHER));
                addButton.visible = false;
            };

            WButton joinButton = theme.button("Join");
            joinButton.action = () -> join(address);

            WButton infoButton = theme.button("Info");
            infoButton.action = () -> Minecraft.getInstance().setScreen(new ServerInfoScreen(address, bedrock));

            table.add(addButton);
            table.add(joinButton);
            table.add(infoButton);
            table.row();
        }
    }

    /** Adds every server to the multiplayer server list, then saves once. */
    public static void addAll(List<Server> servers, JoinMultiplayerScreen mps) {
        for (Server server : servers) {
            String address = server.address();
            MultiplayerScreenUtil.addInfoToServerList(mps, new ServerData("ServerSeeker " + address, address, ServerData.Type.OTHER), false);
        }
        MultiplayerScreenUtil.saveList(mps);
        MultiplayerScreenUtil.reloadServerList(mps);
    }

    /** Connects to the given "host" or "host:port" address (ViaFabricPlus handles Bedrock addresses). */
    public static void join(String address) {
        ServerAddress serverAddress = ServerAddress.parseString(address);
        ConnectScreen.startConnecting(
            new TitleScreen(),
            Minecraft.getInstance(),
            serverAddress,
            new ServerData("ServerSeeker", address, ServerData.Type.OTHER),
            false,
            null
        );
    }
}
