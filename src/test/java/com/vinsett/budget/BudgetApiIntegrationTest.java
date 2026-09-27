
package com.vinsett.budget;

import com.fasterxml.jackson.databind.*;
import com.vinsett.budget.command.*;
import com.vinsett.budget.ledger.*;
import com.vinsett.budget.shared.ApiException;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static com.vinsett.budget.ledger.LedgerTypes.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:budget-tests;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "app.api-token=test-api-token-0000000000000000000000",
        "spring.ai.openai.api-key=not-a-real-key"
})
class BudgetApiIntegrationTest {
    static final String TOKEN = "test-api-token-0000000000000000000000";
    static final UUID ACCOUNT = UUID.fromString("79d2a37e-8ec3-4231-97cb-47160c28e103");
    static final String CHAT = "/v1/chat/completions";
    static final String STT = "/v1/audio/transcriptions";
    static final String TTS = "/v1/audio/speech";
    static final byte[] MP3 = "ID3-transport-test-bytes".getBytes(StandardCharsets.US_ASCII);
    static final ProviderStub PROVIDER = new ProviderStub();
    final ObjectMapper json = new ObjectMapper();
    final HttpClient client = HttpClient.newHttpClient();

    @Value("${local.server.port}") int port;
    @Autowired EntryRepository entries;
    @Autowired BudgetRepository budgets;
    @Autowired ActionRepository actions;
    @Autowired CommandRepository commands;
    @Autowired LedgerService ledger;
    @Autowired CommandStore store;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

    @DynamicPropertySource
    static void providerProperties(DynamicPropertyRegistry properties) {
        properties.add("spring.ai.openai.base-url", PROVIDER::url);
    }

    @BeforeEach void reset() {
        actions.deleteAll();
        entries.deleteAll();
        budgets.deleteAll();
        commands.deleteAll();
        PROVIDER.reset();
    }

    @AfterAll static void shutdown() { PROVIDER.close(); }

    @Test void authenticationIsRequiredButHealthIsPublic() throws Exception {
        assertThat(client.send(HttpRequest.newBuilder(uri("/api/v1/summary?month=2026-09")).GET().build(),
                HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(401);
        assertThat(client.send(HttpRequest.newBuilder(uri("/actuator/health")).GET().build(),
                HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(200);
        assertThat(PROVIDER.requests).isEmpty();
    }

    @Test void restWritesArePersistentAndDecimalArithmeticIsExact() throws Exception {
        create("INCOME", "1000.10", "2026-09-01", UUID.randomUUID());
        create("EXPENSE", "0.20", "2026-09-27", UUID.randomUUID());
        create("EXPENSE", "0.10", "2026-09-28", UUID.randomUUID());
        var response = get("/api/v1/summary?month=2026-09");
        assertThat(response.status()).isEqualTo(200);
        assertThat(response.body().path("income").decimalValue()).isEqualByComparingTo("1000.10");
        assertThat(response.body().path("expenses").decimalValue()).isEqualByComparingTo("0.30");
        assertThat(response.body().path("net").decimalValue()).isEqualByComparingTo("999.80");
        assertThat(entries.count()).isEqualTo(3);
        assertThat(PROVIDER.requests).isEmpty();
    }

    @Test void sameIdempotencyKeyReplaysAndChangedPayloadConflicts() throws Exception {
        UUID key = UUID.randomUUID();
        var first = create("EXPENSE", "42.90", "2026-09-27", key);
        var second = create("EXPENSE", "42.90", "2026-09-27", key);
        assertThat(first.status()).isEqualTo(200);
        assertThat(second.body().path("replayed").asBoolean()).isTrue();
        assertThat(second.body().path("commandId")).isEqualTo(first.body().path("commandId"));
        assertThat(create("EXPENSE", "43.00", "2026-09-27", key).status()).isEqualTo(409);
        assertThat(entries.count()).isEqualTo(1);
    }

    @Test void concurrentDuplicateRequestsCreateOnlyOneEntry() throws Exception {
        var executor = Executors.newFixedThreadPool(4);
        try {
            UUID key = UUID.randomUUID();
            var start = new CountDownLatch(1);
            var futures = new ArrayList<Future<Reply>>();
            for (int index = 0; index < 4; index++) futures.add(executor.submit(() -> {
                start.await();
                return create("EXPENSE", "12.34", "2026-09-27", key);
            }));
            start.countDown();
            for (var future : futures) assertThat(future.get(20, TimeUnit.SECONDS).status()).isIn(200, 409);
            assertThat(entries.count()).isEqualTo(1);
            assertThat(actions.count()).isEqualTo(1);
        } finally { executor.shutdownNow(); }
    }

    @Test void monthlyBudgetsAndMonthBoundariesAreCalculatedByDatabase() throws Exception {
        create("EXPENSE", "30.00", "2024-02-29", UUID.randomUUID());
        create("EXPENSE", "99.00", "2024-03-01", UUID.randomUUID());
        assertThat(send("PUT", "/api/v1/budgets/2024-02/FOOD", UUID.randomUUID(),
                Map.of("amount", new BigDecimal("20.00"))).status()).isEqualTo(200);
        var summary = get("/api/v1/summary?month=2024-02").body();
        assertThat(summary.path("expenses").decimalValue()).isEqualByComparingTo("30.00");
        var food = summary.path("categories").get(0);
        assertThat(food.path("category").asText()).isEqualTo("FOOD");
        assertThat(food.path("remaining").decimalValue()).isEqualByComparingTo("-10.00");
        assertThat(food.path("exceeded").asBoolean()).isTrue();
        assertThat(get("/api/v1/entries?month=2024-02&size=1").body().path("totalElements").asInt()).isEqualTo(1);
    }

    @Test void negativeOrOverPreciseMoneyIsRejectedBeforePersistence() throws Exception {
        assertThat(create("EXPENSE", "-1.00", "2026-09-27", UUID.randomUUID()).status()).isEqualTo(400);
        assertThat(create("EXPENSE", "1.001", "2026-09-27", UUID.randomUUID()).status()).isEqualTo(400);
        assertThat(create("EXPENSE", "0.00", "2026-09-27", UUID.randomUUID()).status()).isEqualTo(400);
        assertThat(entries.count()).isZero();
        assertThat(get("/api/v1/summary?month=2026-13").status()).isEqualTo(400);
        assertThat(get("/api/v1/entries?month=2026-09&size=101").status()).isEqualTo(400);
    }

    @Test void accountScopeCannotBeChangedByResourceIdentifiers() throws Exception {
        var response = create("EXPENSE", "8.00", "2026-09-27", UUID.randomUUID()).body();
        UUID id = UUID.fromString(response.path("actions").get(0).path("resourceId").asText());
        UUID commandId = UUID.fromString(response.path("commandId").asText());
        UUID other = UUID.randomUUID();
        assertThatThrownBy(() -> ledger.get(other, id)).isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).status()).isEqualTo(404);
        assertThatThrownBy(() -> store.view(other, commandId, false)).isInstanceOf(ApiException.class);
        assertThat(ledger.summary(other, "2026-09").expenses()).isEqualByComparingTo("0.00");
    }

    @Test void realChatClientDispatchesToolsAndDeduplicatesRepeatedToolCalls() throws Exception {
        var argument = Map.<String, Object>of("entry", entry("EXPENSE", "42.90", "2026-09-27"));
        PROVIDER.tool("recordEntry", argument);
        PROVIDER.tool("recordEntry", argument);
        PROVIDER.answer("Registrei R$ 42,90 de alimentação em 27/09/2026.");
        var response = text("Registre 42,90 reais de almoço em 27/09/2026.", false, UUID.randomUUID());
        assertThat(response.status()).isEqualTo(200);
        assertThat(response.body().path("command").path("actions").size()).isEqualTo(1);
        assertThat(entries.count()).isEqualTo(1);
        assertThat(PROVIDER.count(CHAT)).isEqualTo(3);
        String schema = PROVIDER.requests.get(0).body();
        assertThat(schema).contains("recordEntry", "getMonthlySummary").doesNotContain(ACCOUNT.toString());
        assertThat(PROVIDER.requests.get(1).body()).contains("ENTRY_CREATED");
    }

    @Test void toolArgumentsAreValidatedInsideBusinessService() throws Exception {
        PROVIDER.tool("recordEntry", Map.of("entry", entry("EXPENSE", "-4.00", "2026-09-27")));
        var response = text("Registre uma despesa.", false, UUID.randomUUID());
        assertThat(response.status()).isEqualTo(400);
        assertThat(response.body().path("command").path("errorCode").asText()).isEqualTo("INVALID_FINANCIAL_DATA");
        assertThat(entries.count()).isZero();
    }

    @Test void toolLoopStopsAndReportsPartialCompletion() throws Exception {
        for (int index = 0; index < 4; index++) {
            PROVIDER.tool("getMonthlySummary", Map.of("month", "2026-09"));
        }
        var response = text("Consulte meu orçamento.", false, UUID.randomUUID());
        assertThat(response.status()).isEqualTo(422);
        assertThat(response.body().path("command").path("errorCode").asText()).isEqualTo("TOOL_LIMIT_REACHED");
        assertThat(PROVIDER.count(CHAT)).isEqualTo(4);
        assertThat(entries.count()).isZero();
    }

    @Test void literalBracesArePreservedInUserText() throws Exception {
        PROVIDER.answer("Qual valor você deseja registrar?");
        var response = text("Explique o texto {amount} e {accountId}.", false, UUID.randomUUID());
        assertThat(response.status()).isEqualTo(200);
        assertThat(PROVIDER.requests.get(0).body()).contains("{amount}", "{accountId}");
    }

    @Test void fullVoicePipelineTranscribesWritesAndReturnsAudio() throws Exception {
        PROVIDER.json(STT, Map.of("text", "Registre 42,90 de almoço em 27/09/2026."));
        PROVIDER.tool("recordEntry", Map.of("entry", entry("EXPENSE", "42.90", "2026-09-27")));
        PROVIDER.answer("Despesa registrada.");
        PROVIDER.enqueue(TTS, 200, "audio/mpeg", MP3);
        UUID key = UUID.randomUUID();
        var response = voice(key, "recording.wav", "audio/wav", wav());
        assertThat(response.status()).isEqualTo(200);
        assertThat(response.body().path("command").path("input").asText()).contains("42,90");
        assertThat(Base64.getDecoder().decode(response.body().path("audio").path("base64").asText())).isEqualTo(MP3);
        assertThat(response.body().path("audio").path("aiGenerated").asBoolean()).isTrue();
        assertThat(entries.count()).isEqualTo(1);
        assertThat(PROVIDER.count(STT)).isEqualTo(1);
        assertThat(PROVIDER.requests.stream().filter(request -> request.path().equals(STT)).findFirst().orElseThrow().body())
                .contains("recording.wav", "gpt-4o-mini-transcribe");

        PROVIDER.enqueue(TTS, 200, "audio/mpeg", MP3);
        var replay = voice(key, "recording.wav", "audio/wav", wav());
        assertThat(replay.body().path("command").path("replayed").asBoolean()).isTrue();
        assertThat(entries.count()).isEqualTo(1);
        assertThat(PROVIDER.count(STT)).isEqualTo(1);
        assertThat(PROVIDER.count(CHAT)).isEqualTo(2);
    }

    @Test void speechFailurePreservesSuccessfulCommandAndCanBeRetriedAlone() throws Exception {
        PROVIDER.tool("recordEntry", Map.of("entry", entry("EXPENSE", "7.00", "2026-09-27")));
        PROVIDER.answer("Registrei R$ 7,00.");
        PROVIDER.failure(TTS);
        var response = text("Registre sete reais em alimentação.", true, UUID.randomUUID());
        assertThat(response.status()).isEqualTo(200);
        assertThat(response.body().path("audio").isNull()).isTrue();
        assertThat(response.body().path("audioWarning").asText()).contains("preservadas");
        UUID id = UUID.fromString(response.body().path("command").path("commandId").asText());
        PROVIDER.enqueue(TTS, 200, "audio/mpeg", MP3);
        var audio = client.send(request("/api/v1/assistant/commands/" + id + "/speech")
                .POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofByteArray());
        assertThat(audio.statusCode()).isEqualTo(200);
        assertThat(audio.headers().firstValue("Content-Type")).hasValue("audio/mpeg");
        assertThat(audio.body()).isEqualTo(MP3);
        assertThat(entries.count()).isEqualTo(1);
        assertThat(PROVIDER.count(CHAT)).isEqualTo(2);
    }

    @Test void failedFinalModelResponseKeepsAuditAndIsNotReexecutedOnRetry() throws Exception {
        PROVIDER.tool("recordEntry", Map.of("entry", entry("EXPENSE", "7.00", "2026-09-27")));
        PROVIDER.failure(CHAT);
        UUID key = UUID.randomUUID();
        var first = text("Registre sete reais.", false, key);
        assertThat(first.status()).isEqualTo(502);
        assertThat(first.body().path("command").path("actions").size()).isEqualTo(1);
        var again = text("Registre sete reais.", false, key);
        assertThat(again.status()).isEqualTo(502);
        assertThat(again.body().path("command").path("replayed").asBoolean()).isTrue();
        assertThat(PROVIDER.count(CHAT)).isEqualTo(2);
        assertThat(entries.count()).isEqualTo(1);
    }

    @Test void malformedAudioNeverReachesProvider() throws Exception {
        assertThat(voice(UUID.randomUUID(), "empty.wav", "audio/wav", new byte[0]).status()).isEqualTo(400);
        assertThat(voice(UUID.randomUUID(), "notes.txt", "text/plain", wav()).status()).isEqualTo(415);
        assertThat(voice(UUID.randomUUID(), "fake.wav", "audio/wav", new byte[44]).status()).isEqualTo(400);
        assertThat(PROVIDER.requests).isEmpty();
    }

    @Test void failedTranscriptionDoesNotInvokeChatOrWrite() throws Exception {
        PROVIDER.failure(STT);
        var response = voice(UUID.randomUUID(), "voice.wav", "audio/wav", wav());
        assertThat(response.status()).isEqualTo(502);
        assertThat(response.body().path("command").path("errorCode").asText()).isEqualTo("TRANSCRIPTION_FAILED");
        assertThat(entries.count()).isZero();
        assertThat(PROVIDER.count(CHAT)).isZero();
    }

    @Test void oversizedMultipartIsRejectedBeforeTranscription() throws Exception {
        var response = voice(UUID.randomUUID(), "large.wav", "audio/wav",
                Arrays.copyOf(wav(), 10 * 1024 * 1024 + 1));
        assertThat(response.status()).isEqualTo(413);
        assertThat(PROVIDER.requests).isEmpty();
    }

    @Test void abandonedCommandExpiresWithoutReplayingActions() {
        UUID key = UUID.randomUUID();
        var claim = store.claim(ACCOUNT, key, "expired-hash", "TEXT");
        jdbc.update("update command_execution set created_at = ? where id = ?",
                OffsetDateTime.now().minusMinutes(11), claim.id());
        var expired = store.view(ACCOUNT, claim.id(), true);
        assertThat(expired.errorCode()).isEqualTo("COMMAND_INTERRUPTED");
        assertThat(expired.status()).isEqualTo(CommandExecution.Status.FAILED);
        assertThatThrownBy(() -> ledger.recordEntry(ACCOUNT, claim.id(),
                new CreateEntry(EntryType.EXPENSE, Category.FOOD, new BigDecimal("4.00"), "Almoço", LocalDate.now())))
                .isInstanceOf(ApiException.class);
        assertThat(entries.count()).isZero();
    }

    @Test void modelCanSetBudgetAndReadAuthoritativeSummary() throws Exception {
        PROVIDER.tool("setBudget", Map.of("budget", Map.of("month", "2026-09", "category", "FOOD", "amount", 500)));
        PROVIDER.tool("getMonthlySummary", Map.of("month", "2026-09"));
        PROVIDER.answer("Limite de alimentação definido em R$ 500,00 para setembro de 2026.");
        var response = text("Defina 500 reais para alimentação em setembro de 2026 e consulte o resumo.", false, UUID.randomUUID());
        assertThat(response.status()).isEqualTo(200);
        assertThat(response.body().path("command").path("actions").get(0).path("kind").asText()).isEqualTo("BUDGET_SET");
        assertThat(ledger.summary(ACCOUNT, "2026-09").categories().get(0).limit()).isEqualByComparingTo("500.00");
        assertThat(PROVIDER.count(CHAT)).isEqualTo(3);
    }

    Reply create(String type, String amount, String date, UUID key) throws Exception {
        return send("POST", "/api/v1/entries", key, entry(type, amount, date));
    }

    Map<String, Object> entry(String type, String amount, String date) {
        return Map.of("type", type, "category", "FOOD", "amount", new BigDecimal(amount),
                "description", "Almoço", "occurredOn", date);
    }

    Reply text(String text, boolean includeAudio, UUID key) throws Exception {
        return send("POST", "/api/v1/assistant/text", key, Map.of("text", text, "includeAudio", includeAudio));
    }

    Reply get(String path) throws Exception {
        var response = client.send(request(path).GET().build(), HttpResponse.BodyHandlers.ofString());
        return new Reply(response.statusCode(), json.readTree(response.body()));
    }

    Reply send(String method, String path, UUID key, Object body) throws Exception {
        var response = client.send(request(path).header("Content-Type", "application/json")
                .header("Idempotency-Key", key.toString())
                .method(method, HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(),
                HttpResponse.BodyHandlers.ofString());
        return new Reply(response.statusCode(), json.readTree(response.body()));
    }

    Reply voice(UUID key, String filename, String type, byte[] bytes) throws Exception {
        String boundary = "budget-test-" + UUID.randomUUID();
        var body = new ByteArrayOutputStream();
        body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\""
                + filename + "\"\r\nContent-Type: " + type + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(bytes);
        body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        var response = client.send(request("/api/v1/assistant/voice").header("Idempotency-Key", key.toString())
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build(), HttpResponse.BodyHandlers.ofString());
        return new Reply(response.statusCode(), json.readTree(response.body()));
    }

    HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(20)).header("Authorization", "Bearer " + TOKEN);
    }
    URI uri(String path) { return URI.create("http://127.0.0.1:" + port + path); }
    byte[] wav() {
        byte[] bytes = new byte[44];
        System.arraycopy("RIFF".getBytes(StandardCharsets.US_ASCII), 0, bytes, 0, 4);
        System.arraycopy("WAVE".getBytes(StandardCharsets.US_ASCII), 0, bytes, 8, 4);
        return bytes;
    }
    record Reply(int status, JsonNode body) {}
}
