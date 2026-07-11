package de.damcraft.serverseeker.translation;

import de.damcraft.serverseeker.ServerSeeker;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.events.game.SendMessageEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.network.MeteorExecutor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TranslatorModule extends Module {
    private static final Pattern ANGLE = Pattern.compile("^<([A-Za-z0-9_]{1,16})>\\s?(.+)$", Pattern.DOTALL);
    private static final Pattern COLON = Pattern.compile("^(?:\\[[^\\]]{1,32}\\]\\s*)*([A-Za-z0-9_]{1,16})\\s*[:>»]\\s+(.+)$", Pattern.DOTALL);

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgIncoming = settings.createGroup("Incoming");
    private final SettingGroup sgOutgoing = settings.createGroup("Outgoing");

    private final Setting<String> apiKey = sgGeneral.add(new StringSetting.Builder()
        .name("deepl-api-key")
        .description("Your DeepL API key (free keys end with ':fx'). Required.")
        .defaultValue("")
        .build());

    private final Setting<Boolean> showHover = sgGeneral.add(new BoolSetting.Builder()
        .name("show-original-on-hover")
        .description("Hover a translated message to see the original text.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> translateIncoming = sgIncoming.add(new BoolSetting.Builder()
        .name("translate-incoming")
        .description("Translate messages other players send.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> autoDetectSource = sgIncoming.add(new BoolSetting.Builder()
        .name("auto-detect-source")
        .description("Let DeepL auto-detect the language of incoming messages.")
        .defaultValue(true)
        .visible(translateIncoming::get)
        .build());

    private final Setting<Lang> incomingSource = sgIncoming.add(new EnumSetting.Builder<Lang>()
        .name("source-language")
        .description("The language incoming messages are in.")
        .defaultValue(Lang.ENGLISH_US)
        .visible(() -> translateIncoming.get() && !autoDetectSource.get())
        .build());

    private final Setting<Lang> incomingTarget = sgIncoming.add(new EnumSetting.Builder<Lang>()
        .name("translate-into")
        .description("The language to translate incoming messages into.")
        .defaultValue(Lang.ENGLISH_US)
        .visible(translateIncoming::get)
        .build());

    private final Setting<Boolean> translateOutgoing = sgOutgoing.add(new BoolSetting.Builder()
        .name("translate-outgoing")
        .description("Translate your own messages before they are sent.")
        .defaultValue(false)
        .build());

    private final Setting<Lang> outgoingTarget = sgOutgoing.add(new EnumSetting.Builder<Lang>()
        .name("translate-into")
        .description("The language to translate your messages into before sending.")
        .defaultValue(Lang.ENGLISH_US)
        .visible(translateOutgoing::get)
        .build());

    private final Map<String, String> cache = Collections.synchronizedMap(new LinkedHashMap<>(256, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
            return size() > 500;
        }
    });

    private volatile boolean sending;
    private boolean injecting;

    public TranslatorModule() {
        super(ServerSeeker.CATEGORY, "ChatTranslator", "Translates incoming and outgoing chat using DeepL.");
    }

    @Override
    public void onActivate() {
        if (apiKey.get().trim().isEmpty()) {
            error("Set your DeepL API key in the module settings first.");
            toggle();
        }
    }

    @EventHandler
    private void onSendMessage(SendMessageEvent event) {
        if (sending || !translateOutgoing.get()) return;
        if (apiKey.get().trim().isEmpty()) return;

        String text = event.message;
        if (text == null || text.isBlank() || text.startsWith("/")) return;

        event.setCancelled(true);
        String target = outgoingTarget.get().code();

        translateAsync(text, null, target, translated -> {
            String toSend = translated == null ? text : translated;
            mc.execute(() -> {
                ClientPacketListener connection = mc.getConnection();
                if (connection == null) return;
                sending = true;
                try {
                    connection.sendChat(toSend);
                } finally {
                    sending = false;
                }
            });
        });
    }

    @EventHandler
    private void onReceiveMessage(ReceiveMessageEvent event) {
        if (injecting || !translateIncoming.get()) return;
        if (apiKey.get().trim().isEmpty()) return;

        Component original = event.getMessage();
        if (original == null) return;

        String full = original.getString();
        String[] parts = detectPlayerMessage(full);
        if (parts == null) return;

        String prefix = parts[0];
        String body = parts[1];
        String name = parts[2];
        if (body.trim().isEmpty() || name.equalsIgnoreCase(ownName())) return;

        event.setCancelled(true);
        String source = autoDetectSource.get() ? null : incomingSource.get().sourceCode();
        String target = incomingTarget.get().code();
        boolean hover = showHover.get();

        translateAsync(body, source, target, translated -> {
            Component out = (translated == null || translated.equals(body))
                ? original
                : buildMessage(prefix, translated, body, hover);
            addLine(out);
        });
    }

    private void translateAsync(String text, String source, String target, Consumer<String> callback) {
        String cacheKey = source + "|" + target + "|" + text;
        String cached = cache.get(cacheKey);
        if (cached != null) {
            callback.accept(cached);
            return;
        }
        String key = apiKey.get().trim();
        MeteorExecutor.execute(() -> {
            String result = DeepL.translate(text, source, target, key);
            if (result != null) cache.put(cacheKey, result);
            callback.accept(result);
        });
    }

    private void addLine(Component component) {
        mc.execute(() -> {
            injecting = true;
            try {
                mc.gui.getChat().addClientSystemMessage(component);
            } finally {
                injecting = false;
            }
        });
    }

    private Component buildMessage(String prefix, String translated, String originalBody, boolean hover) {
        MutableComponent bodyComponent = Component.literal(translated);
        if (hover) {
            bodyComponent = bodyComponent.withStyle(style ->
                style.withHoverEvent(new HoverEvent.ShowText(Component.literal("Original: " + originalBody))));
        }
        return Component.literal(prefix).append(bodyComponent);
    }

    private static String[] detectPlayerMessage(String full) {
        Matcher angle = ANGLE.matcher(full);
        if (angle.matches()) {
            return new String[]{full.substring(0, angle.start(2)), angle.group(2), angle.group(1)};
        }
        Matcher colon = COLON.matcher(full);
        if (colon.matches()) {
            return new String[]{full.substring(0, colon.start(2)), colon.group(2), colon.group(1)};
        }
        return null;
    }

    private String ownName() {
        return mc.getUser() != null ? mc.getUser().getName() : "";
    }
}
