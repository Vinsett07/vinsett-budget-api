
package com.vinsett.budget.ledger;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class LedgerTypes {
    private LedgerTypes() {}

    public enum EntryType { INCOME, EXPENSE }
    public enum Category { FOOD, HOUSING, TRANSPORT, HEALTH, EDUCATION, LEISURE, SALARY, OTHER }

    public record CreateEntry(
            @NotNull EntryType type, @NotNull Category category,
            @NotNull @DecimalMin("0.01") @Digits(integer = 13, fraction = 2) BigDecimal amount,
            @NotBlank @Size(max = 200) String description,
            @NotNull LocalDate occurredOn) {}

    public record SetBudget(@NotBlank @Pattern(regexp = "\\d{4}-(0[1-9]|1[0-2])") String month,
                            @NotNull Category category,
                            @NotNull @DecimalMin("0.01") @Digits(integer = 13, fraction = 2) BigDecimal amount) {}

    public record BudgetAmount(
            @NotNull @DecimalMin("0.01") @Digits(integer = 13, fraction = 2) BigDecimal amount) {}

    public record EntryView(UUID id, EntryType type, Category category, BigDecimal amount,
                            String currency, String description, LocalDate occurredOn, Instant createdAt) {}

    public record CategoryStatus(Category category, BigDecimal spent, BigDecimal limit,
                                 BigDecimal remaining, boolean exceeded) {}

    public record MonthlySummary(String month, String currency, BigDecimal income, BigDecimal expenses,
                                 BigDecimal net, BigDecimal balanceThroughMonth, List<CategoryStatus> categories) {}

    public record EntryPage(List<EntryView> items, int page, int size, long totalElements, int totalPages) {}
}
