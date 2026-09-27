
package com.vinsett.budget.command;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "command_action")
public class CommandAction {
    @Id private UUID id;
    @Column(nullable = false) private UUID accountId;
    @Column(nullable = false) private UUID commandId;
    @Column(nullable = false, length = 64) private String fingerprint;
    @Column(nullable = false, length = 30) private String kind;
    @Column(nullable = false) private UUID resourceId;
    @Column(nullable = false, length = 600) private String message;
    @Column(nullable = false) private Instant createdAt;

    protected CommandAction() {}

    public CommandAction(UUID accountId, UUID commandId, String fingerprint,
                         String kind, UUID resourceId, String message, Instant now) {
        this.id = UUID.randomUUID();
        this.accountId = accountId;
        this.commandId = commandId;
        this.fingerprint = fingerprint;
        this.kind = kind;
        this.resourceId = resourceId;
        this.message = message;
        this.createdAt = now;
    }

    public ActionView view() { return new ActionView(id, kind, resourceId, message, createdAt); }

    public record ActionView(UUID id, String kind, UUID resourceId, String message, Instant createdAt) {}
}
