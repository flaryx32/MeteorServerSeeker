package de.damcraft.serverseeker.mixin;

import de.damcraft.serverseeker.gui.ServerInfoScreen;
import de.damcraft.serverseeker.gui.ServerSeekerScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(JoinMultiplayerScreen.class)
public abstract class MultiplayerScreenMixin extends Screen {
    @Shadow
    protected ServerSelectionList serverSelectionList;

    @Unique
    private Button getInfoButton;

    protected MultiplayerScreenMixin() {
        super(null);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void onInit(CallbackInfo info) {
        // Add a button which sets the current screen to the ServerSeekerScreen
        this.addRenderableWidget(
            new Button.Builder(
                Component.literal("ServerSeeker"),
                onPress -> {
                    if (this.minecraft == null) return;
                    this.minecraft.setScreen(new ServerSeekerScreen((JoinMultiplayerScreen) (Object) this));
                }
            )
                .pos(this.width - 84, this.height - 52)
                .width(80)
                .build()
        );

        // Add a button to get the info of the selected server
        this.getInfoButton = this.addRenderableWidget(
            new Button.Builder(
                Component.literal("Get players"),
                onPress -> {
                    if (this.minecraft == null) return;
                    ServerSelectionList.Entry entry = this.serverSelectionList.getSelected();
                    if (entry instanceof ServerSelectionList.OnlineServerEntry online) {
                        ServerData data = online.getServerData();
                        this.minecraft.setScreen(new ServerInfoScreen(data.ip, false));
                    }
                }
            )
                .pos(this.width - 84, this.height - 28)
                .width(80)
                .build()
        );

        // Set the correct initial state (the early onSelectedChange fired before this button existed)
        this.getInfoButton.active = this.serverSelectionList.getSelected() instanceof ServerSelectionList.OnlineServerEntry;
    }

    @Inject(method = "onSelectedChange", at = @At("TAIL"))
    private void onUpdateButtonActivationStates(CallbackInfo info) {
        // onSelectedChange() is invoked during init() before our button is added, so guard against null
        if (this.getInfoButton == null) return;
        // Enable the button if a real (online) server is selected
        ServerSelectionList.Entry entry = this.serverSelectionList.getSelected();
        this.getInfoButton.active = entry instanceof ServerSelectionList.OnlineServerEntry;
    }
}
