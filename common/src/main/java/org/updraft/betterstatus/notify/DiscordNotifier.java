package org.updraft.betterstatus.notify;

import org.updraft.betterstatus.model.ServerSnapshot;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public final class DiscordNotifier {
    private final String webhook;
    private final Executor executor;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public DiscordNotifier(String webhook, Executor executor) {
        this.webhook = webhook == null ? "" : webhook.trim();
        this.executor = executor;
    }

    public void sendTransition(ServerSnapshot before, ServerSnapshot after) {
        if (webhook.isBlank()) return;
        String content = "**" + after.name() + "** changed from **" + before.state() + "** to **" + after.state() + "**";
        String escaped = content.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
        String json = "{\"content\":\"" + escaped + "\"}";
        CompletableFuture.runAsync(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(webhook))
                        .timeout(Duration.ofSeconds(8))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json))
                        .build();
                client.send(request, HttpResponse.BodyHandlers.discarding());
            } catch (Exception ignored) {
                // A notification outage must never affect monitoring.
            }
        }, executor);
    }
}
