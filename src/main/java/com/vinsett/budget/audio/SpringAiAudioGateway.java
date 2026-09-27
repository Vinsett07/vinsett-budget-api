
package com.vinsett.budget.audio;

import com.vinsett.budget.shared.ApiException;
import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.audio.transcription.TranscriptionModel;
import org.springframework.ai.audio.tts.*;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;

@Service
public class SpringAiAudioGateway implements AudioGateway {
    private final TranscriptionModel transcription;
    private final TextToSpeechModel speech;

    public SpringAiAudioGateway(TranscriptionModel transcription, TextToSpeechModel speech) {
        this.transcription = transcription;
        this.speech = speech;
    }

    @Override
    public String transcribe(AudioInput input) {
        try {
            var resource = new ByteArrayResource(input.bytes()) {
                @Override public String getFilename() { return input.filename(); }
            };
            String result = transcription.call(new AudioTranscriptionPrompt(resource)).getResult().getOutput();
            if (result == null || result.isBlank()) throw new ApiException(422, "NO_SPEECH", "Nenhuma fala foi reconhecida.");
            return result;
        } catch (ApiException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ApiException(502, "TRANSCRIPTION_FAILED", "A transcrição não ficou disponível. Nenhuma ferramenta foi acionada.");
        }
    }

    @Override
    public byte[] speak(String text) {
        if (text == null || text.isBlank() || text.length() > 4000) {
            throw new ApiException(400, "INVALID_SPEECH_TEXT", "O texto deve ter entre 1 e 4000 caracteres.");
        }
        try {
            byte[] result = speech.call(new TextToSpeechPrompt(text)).getResult().getOutput();
            if (result == null || result.length == 0) throw new IllegalStateException("Empty audio");
            return result;
        } catch (RuntimeException exception) {
            throw new ApiException(502, "SPEECH_FAILED", "A resposta em áudio não ficou disponível.");
        }
    }
}
