
package com.vinsett.budget.audio;

import com.vinsett.budget.shared.ApiException;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

public record AudioInput(byte[] bytes, String filename) {
    public static final int MAX_BYTES = 10 * 1024 * 1024;
    private static final Set<String> EXTENSIONS = Set.of("wav", "mp3", "mp4", "m4a", "webm", "mpeg", "mpga");

    public static AudioInput from(MultipartFile file) {
        if (file.getSize() > MAX_BYTES) throw new ApiException(413, "AUDIO_TOO_LARGE", "Áudio maior que 10 MB.");
        if (file.isEmpty()) throw new ApiException(400, "EMPTY_AUDIO", "O arquivo de áudio está vazio.");
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String extension = name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        if (!EXTENSIONS.contains(extension)) {
            throw new ApiException(415, "UNSUPPORTED_AUDIO", "Use WAV, MP3, M4A, MP4, MPEG, MPGA ou WEBM.");
        }
        String mime = file.getContentType();
        if (mime != null && !mime.startsWith("audio/") && !mime.equals("video/mp4")
                && !mime.equals("video/webm") && !mime.equals("application/octet-stream")) {
            throw new ApiException(415, "UNSUPPORTED_AUDIO", "O tipo informado não é um áudio suportado.");
        }
        try {
            byte[] bytes = file.getBytes();
            if (bytes.length > MAX_BYTES) throw new ApiException(413, "AUDIO_TOO_LARGE", "Áudio maior que 10 MB.");
            if (!looksLikeAudio(bytes, extension)) {
                throw new ApiException(400, "INVALID_AUDIO", "O conteúdo não corresponde ao formato de áudio informado.");
            }
            // Never propagate a client-controlled path as the provider filename.
            return new AudioInput(bytes, "recording." + extension);
        } catch (IOException exception) {
            throw new ApiException(400, "UNREADABLE_AUDIO", "Não foi possível ler o áudio.");
        }
    }

    private static boolean looksLikeAudio(byte[] bytes, String extension) {
        if (bytes.length < 12) return false;
        return switch (extension) {
            case "wav" -> ascii(bytes, 0, 4).equals("RIFF") && ascii(bytes, 8, 4).equals("WAVE");
            case "m4a", "mp4" -> ascii(bytes, 4, 4).equals("ftyp");
            case "webm" -> (bytes[0] & 255) == 0x1a && (bytes[1] & 255) == 0x45
                    && (bytes[2] & 255) == 0xdf && (bytes[3] & 255) == 0xa3;
            default -> ascii(bytes, 0, 3).equals("ID3")
                    || ((bytes[0] & 255) == 255 && (bytes[1] & 0xe0) == 0xe0);
        };
    }

    private static String ascii(byte[] bytes, int start, int length) {
        return new String(bytes, start, length, StandardCharsets.US_ASCII);
    }
}
