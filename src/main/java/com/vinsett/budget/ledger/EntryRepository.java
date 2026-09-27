
package com.vinsett.budget.ledger;

import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static com.vinsett.budget.ledger.LedgerTypes.*;

public interface EntryRepository extends JpaRepository<LedgerEntry, UUID> {
    Optional<LedgerEntry> findByAccountIdAndId(UUID accountId, UUID id);
    Page<LedgerEntry> findByAccountIdAndOccurredOnBetween(UUID accountId, LocalDate from, LocalDate to, Pageable page);

    @Query("""
            select e.entryType as type, e.category as category, sum(e.amount) as total
            from LedgerEntry e where e.accountId = :accountId
            and e.occurredOn between :from and :to group by e.entryType, e.category
            """)
    List<Total> totals(UUID accountId, LocalDate from, LocalDate to);

    @Query("""
            select coalesce(sum(case when e.entryType = :incomeType
                then e.amount else -e.amount end), 0)
            from LedgerEntry e where e.accountId = :accountId and e.occurredOn <= :through
            """)
    BigDecimal balance(UUID accountId, LocalDate through, EntryType incomeType);

    interface Total {
        EntryType getType();
        Category getCategory();
        BigDecimal getTotal();
    }
}
