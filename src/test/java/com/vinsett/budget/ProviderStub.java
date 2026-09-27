
package com.vinsett.budget;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

final class ProviderStub implements AutoCloseable {
    private final HttpServer server;
    private final Map<String, Queue<Reply>> replies = new ConcurrentHashMap<>();
    final List<Request> requests = new CopyOnWriteArrayList<>();
    private final ObjectMapper json = new ObjectMapper();

    ProviderStub() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                String path = exchange.getRequestURI().getPath();
                byte[] request = exchange.getRequestBody().readAllBytes();
                requests.add(new Request(path, new String(request, StandardCharsets.UTF_8),
                        exchange.getRequestHeaders().getFirst("Content-Type")));
                Reply reply = replies.getOrDefault(path, new ConcurrentLinkedQueue<>()).poll();
                if (reply == null) reply = new Reply(500, "application/json",
                        "{\"error\":{\"message\":\"Unexpected provider call\",\"type\":\"server_error\"}}".getBytes(StandardCharsets.UTF_8));
                exchange.getResponseHeaders().set("Content-Type", reply.contentType());
                exchange.sendResponseHeaders(reply.status(), reply.body().length);
                exchange.getResponseBody().write(reply.body());
                exchange.close();
            });
            server.start();
        } catch (IOException exception) { throw new IllegalStateException(exception); }
    }

    String url() { return "http://127.0.0.1:" + server.getAddress().getPort() + "/v1"; }
    void reset() { replies.clear(); requests.clear(); }
    long count(String path) { return requests.stream().filter(request -> request.path().equals(path)).count(); }

    void enqueue(String path, int status, String type, byte[] body) {
        replies.computeIfAbsent(path, ignored -> new ConcurrentLinkedQueue<>()).add(new Reply(status, type, body));
    }

    void json(String path, Object body) {
        try { enqueue(path, 200, "application/json", json.writeValueAsBytes(body)); }
        catch (IOException exception) { throw new IllegalStateException(exception); }
    }

    void answer(String text) { completion(Map.of("role", "assistant", "content", text), "stop"); }

    void tool(String name, Map<String, Object> arguments) {
        try {
            completion(Map.of("role", "assistant", "tool_calls", List.of(
                    Map.of("id", "call_" + UUID.randomUUID(), "type", "function",
                            "function", Map.of("name", name, "arguments", json.writeValueAsString(arguments))))), "tool_calls");
        } catch (IOException exception) { throw new IllegalStateException(exception); }
    }

    private void completion(Map<String, Object> message, String finishReason) {
        json("/v1/chat/completions", Map.of(
                "id", "chatcmpl-test", "object", "chat.completion", "created", 1790553600L, "model", "gpt-4.1-mini",
                "choices", List.of(Map.of("index", 0, "message", message, "finish_reason", finishReason)),
                "usage", Map.of("prompt_tokens", 20, "completion_tokens", 20, "total_tokens", 40)));
    }

    void failure(String path) {
        enqueue(path, 503, "application/json",
                "{\"error\":{\"message\":\"Simulated outage\",\"type\":\"server_error\"}}".getBytes(StandardCharsets.UTF_8));
    }

    @Override public void close() { server.stop(0); }
    record Reply(int status, String contentType, byte[] body) {}
    record Request(String path, String body, String contentType) {}
}
