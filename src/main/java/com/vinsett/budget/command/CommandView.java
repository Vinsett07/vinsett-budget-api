
package com.vinsett.budget.command;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CommandView(UUID commandId, UUID requestKey, CommandExecution.Status status,
                          String source, String input, String reply, String errorCode,
                          int httpStatus, boolean replayed, Instant createdAt, Instant completedAt,
                          List<CommandAction.ActionView> actions) {}
