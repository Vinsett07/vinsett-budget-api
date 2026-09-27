
package com.vinsett.budget.shared;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiErrors {
    private static final Logger log = LoggerFactory.getLogger(ApiErrors.class);

    @ExceptionHandler(ApiException.class)
    ProblemDetail domain(ApiException exception) {
        return problem(exception.status(), exception.code(), exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail invalidBody(MethodArgumentNotValidException exception) {
        var detail = problem(400, "VALIDATION_ERROR", "Verifique os campos enviados.");
        detail.setProperty("fields", exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage()).toList());
        return detail;
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            ConstraintViolationException.class})
    ProblemDetail invalidInput(Exception exception) {
        return problem(400, "INVALID_INPUT", "JSON, valor, identificador ou data inválidos.");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ProblemDetail tooLarge() {
        return problem(413, "AUDIO_TOO_LARGE", "O áudio deve ter no máximo 10 MB.");
    }

    @ExceptionHandler(MultipartException.class)
    ProblemDetail invalidMultipart() {
        return problem(400, "INVALID_MULTIPART", "Envie multipart/form-data com o campo file.");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail conflict() {
        return problem(409, "CONCURRENT_UPDATE", "Conflito de atualização; consulte o resultado antes de repetir.");
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception exception) {
        if (exception instanceof ErrorResponse response) {
            return response.getBody();
        }
        // Provider payloads, prompts, credentials and financial data must not enter logs.
        log.error("Unhandled request error: {}", exception.getClass().getSimpleName());
        return problem(500, "INTERNAL_ERROR", "Não foi possível concluir a solicitação.");
    }

    private ProblemDetail problem(int status, String code, String message) {
        var detail = ProblemDetail.forStatus(status);
        detail.setDetail(message);
        detail.setProperty("code", code);
        return detail;
    }
}
