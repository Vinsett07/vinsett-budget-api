
package com.vinsett.budget.config;

import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import java.time.ZoneId;
import java.util.UUID;

@Validated
@ConfigurationProperties("app")
public record AppProperties(
        @NotBlank @Size(min = 32, max = 256) String apiToken,
        @NotNull UUID accountId,
        @NotNull ZoneId zone,
        @Min(1) @Max(16) int maxConcurrentAiRequests) {
}
