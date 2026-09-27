
package com.vinsett.budget.ledger;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import static com.vinsett.budget.ledger.LedgerTypes.Category;

@Entity
@Table(name = "budget_limit")
public class BudgetLimit {
    @Id UUID id;
    @Column(nullable = false) UUID accountId;
    @Column(nullable = false) LocalDate monthStart;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) Category category;
    @Column(nullable = false, precision = 15, scale = 2) BigDecimal amount;
    @Version long version;

    protected BudgetLimit() {}

    BudgetLimit(UUID accountId, LocalDate monthStart, Category category, BigDecimal amount) {
        id = UUID.randomUUID();
        this.accountId = accountId;
        this.monthStart = monthStart;
        this.category = category;
        this.amount = amount.setScale(2);
    }
}
