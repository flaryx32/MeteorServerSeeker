package de.damcraft.serverseeker.gui;

import de.damcraft.serverseeker.api.Api;
import de.damcraft.serverseeker.api.CountResponse;
import de.damcraft.serverseeker.api.Credits;
import de.damcraft.serverseeker.api.CreditsResponse;
import de.damcraft.serverseeker.api.ServerQuery;
import meteordevelopment.meteorclient.gui.GuiThemes;
import meteordevelopment.meteorclient.gui.WindowScreen;
import meteordevelopment.meteorclient.gui.widgets.WLabel;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.utils.network.Http;
import meteordevelopment.meteorclient.utils.network.MeteorExecutor;
import net.minecraft.client.Minecraft;

import static de.damcraft.serverseeker.ServerSeeker.LOG;

public class StatsScreen extends WindowScreen {
    private WLabel creditsLabel;

    public StatsScreen() {
        super(GuiThemes.get(), "Stats");
    }

    @Override
    public void initWidgets() {
        add(theme.label("Loading stats..."));

        MeteorExecutor.execute(() -> {
            CountResponse java = Http.get(ServerQuery.count().url())
                .exceptionHandler(e -> LOG.error("Could not fetch count: ", e))
                .sendJson(CountResponse.class);
            CountResponse bedrock = Http.get(ServerQuery.bedrockCount().url())
                .exceptionHandler(e -> LOG.error("Could not fetch bedrock count: ", e))
                .sendJson(CountResponse.class);
            CreditsResponse credits = Http.get(Api.BASE + "/credits")
                .exceptionHandler(e -> LOG.error("Could not fetch credits: ", e))
                .sendJson(CreditsResponse.class);

            if (java != null) Credits.update(java.credits);
            if (bedrock != null) Credits.update(bedrock.credits);
            if (credits != null && credits.error == null) Credits.update(credits.credits, credits.max);

            Minecraft.getInstance().execute(() -> {
                clear();
                WTable table = add(theme.table()).widget();
                table.add(theme.label("Java servers: "));
                table.add(theme.label(java == null ? "?" : format(java.data)));
                table.row();
                table.add(theme.label("Bedrock servers: "));
                table.add(theme.label(bedrock == null ? "?" : format(bedrock.data)));
                table.row();

                creditsLabel = add(theme.label(Credits.summary())).widget();
                add(theme.label("Data from the Minecraft Server Scanner (cornbread2100)."));
            });
        });
    }

    @Override
    public void tick() {
        super.tick();
        if (creditsLabel != null) creditsLabel.set(Credits.summary());
    }

    private static String format(long n) {
        return String.format("%,d", n);
    }
}
