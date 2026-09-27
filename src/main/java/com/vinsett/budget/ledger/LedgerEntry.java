
package com.vinsett.budget.ledger;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import static com.vinsett.budget.ledger.LedgerTypes.*;

@Entity
@Table(name = "ledger_entry")
public class LedgerEntry {
    @Id UUID id;
    @Column(nullable = false) UUID accountId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 10) EntryType entryType;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) Category category;
    @Column(nullable = false, precision = 15, scale = 2) BigDecimal amount;
    @Column(nullable = false, length = 200) String description;
    @Column(nullable = false) LocalDate occurredOn;
    @Column(nullable = false) Instant createdAt;

    protected LedgerEntry() {}

    LedgerEntry(UUID accountId, CreateEntry request, Instant now) {
        id = UUID.randomUUID();
        this.accountId = accountId;
        entryType = request.type();
        category = request.category();
        amount = request.amount().setScale(2);
        description = request.description().strip();
        occurredOn = request.occurredOn();
        createdAt = now;
    }

    EntryView view() {
        return new EntryView(id, entryType, category, amount, "BRL", description, occurredOn, createdAt);
    }
}
