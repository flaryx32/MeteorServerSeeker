package de.damcraft.serverseeker.gui;

import de.damcraft.serverseeker.utils.MultiplayerScreenUtil;
import meteordevelopment.meteorclient.gui.GuiThemes;
import meteordevelopment.meteorclient.gui.WindowScreen;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;

public class ServerSeekerScreen extends WindowScreen {
    private final JoinMultiplayerScreen multiplayerScreen;

    public ServerSeekerScreen(JoinMultiplayerScreen multiplayerScreen) {
        super(GuiThemes.get(), "ServerSeeker");
        this.multiplayerScreen = multiplayerScreen;
    }

    @Override
    public void initWidgets() {
        WVerticalList list = add(theme.verticalList()).expandX().widget();

        WHorizontalList row1 = list.add(theme.horizontalList()).expandX().widget();
        row1.add(theme.button("Find servers")).expandX().widget().action = () ->
            setScreenSafe(new FindNewServersScreen(this.multiplayerScreen));
        row1.add(theme.button("Find players")).expandX().widget().action = () ->
            setScreenSafe(new FindPlayerScreen(this.multiplayerScreen));
        row1.add(theme.button("Bedrock search")).expandX().widget().action = () ->
            setScreenSafe(new BedrockSearchScreen(this.multiplayerScreen));

        WHorizontalList row2 = list.add(theme.horizontalList()).expandX().widget();
        row2.add(theme.button("Ping server")).expandX().widget().action = () ->
            setScreenSafe(new PingScreen(this.multiplayerScreen));
        row2.add(theme.button("Random server")).expandX().widget().action = () ->
            setScreenSafe(new RandomScreen(this.multiplayerScreen));
        row2.add(theme.button("Stats")).expandX().widget().action = () ->
            setScreenSafe(new StatsScreen());

        list.add(theme.button("Stream snipe")).expandX().widget().action = () ->
            setScreenSafe(new StreamSnipeScreen(this.multiplayerScreen));

        WButton cleanUpServersButton = list.add(theme.button("Clean up")).expandX().widget();
        cleanUpServersButton.action = () -> {
            if (this.minecraft == null) return;
            clear();
            if (hasAnyServers()) {
                add(theme.label("Are you sure you want to clean up your server list?"));
                add(theme.label("This will remove all servers that start with \"ServerSeeker\""));
                WHorizontalList buttonList = add(theme.horizontalList()).expandX().widget();
                WButton backButton = buttonList.add(theme.button("Back")).expandX().widget();
                backButton.action = this::reload;
                WButton confirmButton = buttonList.add(theme.button("Confirm")).expandX().widget();
                confirmButton.action = this::cleanUpServers;
            } else {
                add(theme.label("There are no servers to clean up."));
                WHorizontalList buttonList = add(theme.horizontalList()).expandX().widget();
                WButton backButton = buttonList.add(theme.button("Back")).expandX().widget();
                backButton.action = this::reload;
            }
        };
    }

    private void setScreenSafe(WindowScreen screen) {
        if (this.minecraft == null) return;
        this.minecraft.setScreen(screen);
    }

    private boolean hasAnyServers() {
        if (this.minecraft == null) return false;

        for (int i = 0; i < this.multiplayerScreen.getServers().size(); i++) {
            if (this.multiplayerScreen.getServers().get(i).name.startsWith("ServerSeeker")) {
                return true;
            }
        }

        return false;
    }

    public void cleanUpServers() {
        if (this.minecraft == null) return;

        for (int i = 0; i < this.multiplayerScreen.getServers().size(); i++) {
            if (this.multiplayerScreen.getServers().get(i).name.startsWith("ServerSeeker")) {
                this.multiplayerScreen.getServers().remove(this.multiplayerScreen.getServers().get(i));
                i--;
            }
        }

        MultiplayerScreenUtil.saveList(multiplayerScreen);
        MultiplayerScreenUtil.reloadServerList(multiplayerScreen);

        this.minecraft.setScreen(this.multiplayerScreen);
    }
}
