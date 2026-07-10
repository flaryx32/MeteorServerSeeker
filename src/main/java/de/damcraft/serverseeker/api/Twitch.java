package de.damcraft.serverseeker.api;

import meteordevelopment.meteorclient.utils.network.Http;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static de.damcraft.serverseeker.ServerSeeker.LOG;

public final class Twitch {
    private static final String GQL = "https://gql.twitch.tv/gql";
    private static final String ANON_CLIENT_ID = "kimne78kx3ncx6brgo4mv6wki5h1ko";
    private static final String MINECRAFT_GAME_ID = "27471";

    private Twitch() {}

    public static class Stream {
        public String login;
        public String displayName;
        public String title;
        public int viewersCount;
    }

    private static class GqlResponse { Data data; }
    private static class Data { Game game; }
    private static class Game { Streams streams; }
    private static class Streams { List<Edge> edges; }
    private static class Edge { Node node; }
    private static class Node { String title; int viewersCount; Broadcaster broadcaster; }
    private static class Broadcaster { String login; String displayName; }

    public static List<Stream> anonymousMinecraftStreams() {
        List<Stream> all = new ArrayList<>();
        String query = "query{game(name:\"Minecraft\"){streams(first:100){edges{node{title viewersCount broadcaster{login displayName}}}}}}";

        GqlResponse resp = Http.post(GQL)
            .header("Client-Id", ANON_CLIENT_ID)
            .bodyJson(Map.of("query", query))
            .exceptionHandler(e -> LOG.error("Could not fetch Twitch streams: ", e))
            .sendJson(GqlResponse.class);

        if (resp == null || resp.data == null || resp.data.game == null
            || resp.data.game.streams == null || resp.data.game.streams.edges == null) return all;

        for (Edge edge : resp.data.game.streams.edges) {
            if (edge.node != null && edge.node.broadcaster != null) {
                Stream stream = new Stream();
                stream.login = edge.node.broadcaster.login;
                stream.displayName = edge.node.broadcaster.displayName;
                stream.title = edge.node.title;
                stream.viewersCount = edge.node.viewersCount;
                all.add(stream);
            }
        }
        return all;
    }

    private static class TokenResponse { String access_token; }
    private static class HelixResponse { List<HelixStream> data; HelixPagination pagination; }
    private static class HelixStream { String user_login; String user_name; String title; int viewer_count; }
    private static class HelixPagination { String cursor; }

    public static String helixToken(String clientId, String clientSecret) {
        String url = "https://id.twitch.tv/oauth2/token?client_id=" + enc(clientId)
            + "&client_secret=" + enc(clientSecret) + "&grant_type=client_credentials";
        TokenResponse token = Http.post(url)
            .exceptionHandler(e -> LOG.error("Could not fetch Twitch token: ", e))
            .sendJson(TokenResponse.class);
        return token == null ? null : token.access_token;
    }

    public static List<Stream> helixMinecraftStreams(String clientId, String token, int maxPages) {
        List<Stream> all = new ArrayList<>();
        String cursor = null;

        for (int i = 0; i < maxPages; i++) {
            String url = "https://api.twitch.tv/helix/streams?game_id=" + MINECRAFT_GAME_ID + "&first=100"
                + (cursor != null ? "&after=" + enc(cursor) : "");
            HelixResponse resp = Http.get(url)
                .header("Client-Id", clientId)
                .bearer(token)
                .exceptionHandler(e -> LOG.error("Could not fetch Twitch streams: ", e))
                .sendJson(HelixResponse.class);

            if (resp == null || resp.data == null || resp.data.isEmpty()) break;
            for (HelixStream stream : resp.data) {
                Stream s = new Stream();
                s.login = stream.user_login;
                s.displayName = stream.user_name;
                s.title = stream.title;
                s.viewersCount = stream.viewer_count;
                all.add(s);
            }
            if (resp.pagination == null || resp.pagination.cursor == null || resp.pagination.cursor.isEmpty()) break;
            cursor = resp.pagination.cursor;
        }
        return all;
    }

    private static String enc(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }
}
