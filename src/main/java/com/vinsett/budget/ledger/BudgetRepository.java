
package com.vinsett.budget.ledger;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.*;
import static com.vinsett.budget.ledger.LedgerTypes.Category;

public interface BudgetRepository extends JpaRepository<BudgetLimit, UUID> {
    Optional<BudgetLimit> findByAccountIdAndMonthStartAndCategory(UUID accountId, LocalDate monthStart, Category category);
    List<BudgetLimit> findByAccountIdAndMonthStart(UUID accountId, LocalDate monthStart);
}
