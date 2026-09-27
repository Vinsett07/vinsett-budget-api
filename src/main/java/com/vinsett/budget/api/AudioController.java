
package com.vinsett.budget.api;

import com.vinsett.budget.ai.AiCapacity;
import com.vinsett.budget.audio.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/audio")
public class AudioController {
    private final AudioGateway audio;
    private final AiCapacity capacity;

    public AudioController(AudioGateway audio, AiCapacity capacity) {
        this.audio = audio;
        this.capacity = capacity;
    }

    @PostMapping(value = "/transcriptions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    Transcript transcribe(@RequestPart("file") MultipartFile file) {
        var input = AudioInput.from(file);
        return capacity.run(() -> new Transcript(audio.transcribe(input)));
    }

    @PostMapping(value = "/speech", produces = "audio/mpeg")
    ResponseEntity<byte[]> speak(@Valid @RequestBody SpeechRequest request) {
        return capacity.run(() -> AssistantController.mp3(audio.speak(request.text())));
    }

    public record Transcript(String text) {}
    public record SpeechRequest(@NotBlank @Size(max = 4000) String text) {}
}
