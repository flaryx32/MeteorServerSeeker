package de.damcraft.serverseeker.api;

import meteordevelopment.meteorclient.utils.network.Http;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static de.damcraft.serverseeker.ServerSeeker.LOG;

/**
 * Fetches live Minecraft Twitch streams with no user credentials, via Twitch's public web GraphQL
 * endpoint using the well-known anonymous web Client-Id (the same one the Twitch website and tools like
 * streamlink use for anonymous reads). This is an unofficial/undocumented endpoint and may change.
 */
public final class Twitch {
    private static final String GQL = "https://gql.twitch.tv/gql";
    private static final String CLIENT_ID = "kimne78kx3ncx6brgo4mv6wki5h1ko"; // Twitch public web client id

    private Twitch() {}

    public static class Stream {
        public String login;
        public String displayName;
        public String title;
        public int viewersCount;
    }

    // ---- GraphQL response DTOs ----
    private static class GqlResponse { Data data; }
    private static class Data { Game game; }
    private static class Game { Streams streams; }
    private static class Streams { List<Edge> edges; PageInfo pageInfo; }
    private static class Edge { String cursor; Node node; }
    private static class Node { String title; int viewersCount; Broadcaster broadcaster; }
    private static class Broadcaster { String login; String displayName; }
    private static class PageInfo { boolean hasNextPage; }

    /** All live Minecraft streams (paginated), capped at {@code maxPages} pages of 30. */
    public static List<Stream> minecraftStreams(int maxPages) {
        List<Stream> all = new ArrayList<>();
        String cursor = null;

        for (int i = 0; i < maxPages; i++) {
            String after = cursor == null ? "" : ",after:\"" + cursor + "\"";
            String query = "query{game(name:\"Minecraft\"){streams(first:30" + after + "){edges{cursor node{title viewersCount broadcaster{login displayName}}}pageInfo{hasNextPage}}}}";

            GqlResponse resp = Http.post(GQL)
                .header("Client-Id", CLIENT_ID)
                .bodyJson(Map.of("query", query))
                .exceptionHandler(e -> LOG.error("Could not fetch Twitch streams: ", e))
                .sendJson(GqlResponse.class);

            if (resp == null || resp.data == null || resp.data.game == null || resp.data.game.streams == null) break;
            Streams streams = resp.data.game.streams;
            if (streams.edges == null || streams.edges.isEmpty()) break;

            String last = null;
            for (Edge edge : streams.edges) {
                if (edge.node != null && edge.node.broadcaster != null) {
                    Stream stream = new Stream();
                    stream.login = edge.node.broadcaster.login;
                    stream.displayName = edge.node.broadcaster.displayName;
                    stream.title = edge.node.title;
                    stream.viewersCount = edge.node.viewersCount;
                    all.add(stream);
                }
                last = edge.cursor;
            }

            if (streams.pageInfo == null || !streams.pageInfo.hasNextPage || last == null) break;
            cursor = last;
        }

        return all;
    }
}
