
package com.vinsett.budget.api;

import com.vinsett.budget.ai.*;
import com.vinsett.budget.audio.*;
import com.vinsett.budget.command.*;
import com.vinsett.budget.security.AccountPrincipal;
import com.vinsett.budget.shared.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.Base64;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/assistant")
public class AssistantController {
    private final CommandRunner runner;
    private final CommandStore commands;
    private final BudgetAssistant assistant;
    private final AudioGateway audio;
    private final AiCapacity capacity;

    public AssistantController(CommandRunner runner, CommandStore commands, BudgetAssistant assistant,
                               AudioGateway audio, AiCapacity capacity) {
        this.runner = runner;
        this.commands = commands;
        this.assistant = assistant;
        this.audio = audio;
        this.capacity = capacity;
    }

    @PostMapping("/text")
    ResponseEntity<AssistantReply> text(@AuthenticationPrincipal AccountPrincipal principal,
            @RequestHeader("Idempotency-Key") UUID key, @Valid @RequestBody TextCommand request) {
        return capacity.run(() -> response(runner.run(principal.accountId(), key,
                Hashing.sha256("TEXT|" + request.text()), "TEXT", request::text,
                (id, input) -> assistant.reply(principal.accountId(), id, input)), request.includeAudio()));
    }

    @PostMapping(value = "/voice", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<AssistantReply> voice(@AuthenticationPrincipal AccountPrincipal principal,
            @RequestHeader("Idempotency-Key") UUID key, @RequestPart("file") MultipartFile file,
            @RequestParam(defaultValue = "true") boolean includeAudio) {
        var input = AudioInput.from(file);
        return capacity.run(() -> response(runner.run(principal.accountId(), key,
                Hashing.sha256("VOICE|" + input.filename() + "|" + Hashing.sha256(input.bytes())),
                "VOICE", () -> audio.transcribe(input),
                (id, text) -> assistant.reply(principal.accountId(), id, text)), includeAudio));
    }

    @GetMapping("/commands/{id}")
    CommandView command(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable UUID id) {
        return commands.view(principal.accountId(), id, false);
    }

    @PostMapping(value = "/commands/{id}/speech", produces = "audio/mpeg")
    ResponseEntity<byte[]> retrySpeech(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable UUID id) {
        var result = commands.view(principal.accountId(), id, false);
        if (result.status() != CommandExecution.Status.SUCCEEDED) {
            throw new ApiException(409, "COMMAND_NOT_SUCCESSFUL", "O comando ainda não tem uma resposta concluída.");
        }
        return capacity.run(() -> mp3(audio.speak(result.reply())));
    }

    private ResponseEntity<AssistantReply> response(CommandView command, boolean includeAudio) {
        AudioReply output = null;
        String warning = null;
        if (includeAudio && command.status() == CommandExecution.Status.SUCCEEDED) {
            try { output = new AudioReply("audio/mpeg", Base64.getEncoder().encodeToString(audio.speak(command.reply())), true); }
            catch (ApiException exception) {
                warning = "A resposta textual e as ações foram preservadas. Gere a voz novamente pelo endpoint /commands/"
                        + command.commandId() + "/speech.";
            }
        }
        return ResponseEntity.status(command.httpStatus())
                .cacheControl(CacheControl.noStore()).body(new AssistantReply(command, output, warning));
    }

    public static ResponseEntity<byte[]> mp3(byte[] content) {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("audio/mpeg"))
                .header("X-AI-Generated", "true")
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"response.mp3\"")
                .cacheControl(CacheControl.noStore()).body(content);
    }

    public record TextCommand(@NotBlank @Size(max = 4000) String text, boolean includeAudio) {}
    public record AudioReply(String mimeType, String base64, boolean aiGenerated) {}
    public record AssistantReply(CommandView command, AudioReply audio, String audioWarning) {}
}
