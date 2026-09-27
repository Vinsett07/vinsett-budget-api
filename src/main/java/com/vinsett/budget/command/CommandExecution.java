
package com.vinsett.budget.command;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "command_execution")
public class CommandExecution {
    public enum Status { RUNNING, SUCCEEDED, FAILED }

    @Id UUID id;
    @Column(nullable = false) UUID accountId;
    @Column(nullable = false) UUID requestKey;
    @Column(nullable = false, length = 64) String requestHash;
    @Column(nullable = false, length = 30) String source;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) Status status;
    @Column(columnDefinition = "text") String inputText;
    @Column(columnDefinition = "text") String responseText;
    @Column(length = 80) String errorCode;
    @Column(nullable = false) int httpStatus;
    @Column(nullable = false) Instant createdAt;
    Instant completedAt;

    protected CommandExecution() {}

    CommandExecution(UUID accountId, UUID requestKey, String requestHash, String source, Instant now) {
        this.id = UUID.randomUUID();
        this.accountId = accountId;
        this.requestKey = requestKey;
        this.requestHash = requestHash;
        this.source = source;
        this.status = Status.RUNNING;
        this.httpStatus = 202;
        this.createdAt = now;
    }

    public UUID id() { return id; }
}
