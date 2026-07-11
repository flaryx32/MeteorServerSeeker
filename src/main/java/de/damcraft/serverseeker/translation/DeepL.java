package de.damcraft.serverseeker.translation;

import meteordevelopment.meteorclient.utils.network.Http;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static de.damcraft.serverseeker.ServerSeeker.LOG;

public final class DeepL {
    private DeepL() {}

    private static class Response {
        List<Translation> translations;
        static class Translation { String text; }
    }

    public static String translate(String text, String sourceCode, String targetCode, String apiKey) {
        if (text == null || text.isEmpty() || apiKey == null || apiKey.isEmpty() || targetCode == null) return null;

        String key = apiKey.trim();
        String base = key.endsWith(":fx") ? "https://api-free.deepl.com" : "https://api.deepl.com";

        StringBuilder form = new StringBuilder();
        form.append("text=").append(enc(text));
        form.append("&target_lang=").append(enc(targetCode));
        if (sourceCode != null && !sourceCode.isEmpty()) form.append("&source_lang=").append(enc(sourceCode));

        Response response = Http.post(base + "/v2/translate")
            .header("Authorization", "DeepL-Auth-Key " + key)
            .bodyForm(form.toString())
            .exceptionHandler(e -> LOG.error("DeepL request failed: ", e))
            .sendJson(Response.class);

        if (response == null || response.translations == null || response.translations.isEmpty()) return null;
        return response.translations.get(0).text;
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
