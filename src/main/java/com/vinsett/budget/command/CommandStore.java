
package com.vinsett.budget.command;

import com.vinsett.budget.shared.ApiException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

@Service
public class CommandStore {
    private static final Duration DEADLINE = Duration.ofMinutes(10);
    private final CommandRepository commands;
    private final ActionRepository actions;
    private final TransactionTemplate transactions;
    private final Clock clock;

    public CommandStore(CommandRepository commands, ActionRepository actions,
                        PlatformTransactionManager manager, Clock clock) {
        this.commands = commands;
        this.actions = actions;
        this.transactions = new TransactionTemplate(manager);
        this.clock = clock;
    }

    public Claim claim(UUID accountId, UUID key, String hash, String source) {
        var existing = commands.findByAccountIdAndRequestKey(accountId, key);
        if (existing.isPresent()) return check(existing.get(), hash);
        try {
            UUID id = transactions.execute(status -> commands.saveAndFlush(
                    new CommandExecution(accountId, key, hash, source, clock.instant())).id());
            return new Claim(id, true);
        } catch (DataIntegrityViolationException exception) {
            return check(commands.findByAccountIdAndRequestKey(accountId, key)
                    .orElseThrow(() -> exception), hash);
        }
    }

    private Claim check(CommandExecution command, String hash) {
        if (!command.requestHash.equals(hash)) {
            throw new ApiException(409, "IDEMPOTENCY_CONFLICT",
                    "Esta Idempotency-Key já foi usada com outro conteúdo.");
        }
        return new Claim(command.id, false);
    }

    @Transactional
    public void input(UUID accountId, UUID id, String text) {
        active(accountId, id).inputText = text;
    }

    @Transactional
    public void finish(UUID accountId, UUID id, String reply, String code, int httpStatus) {
        var command = commands.lock(accountId, id).orElseThrow(CommandStore::missing);
        if (command.status != CommandExecution.Status.RUNNING) return;
        command.status = code == null ? CommandExecution.Status.SUCCEEDED : CommandExecution.Status.FAILED;
        command.responseText = reply;
        command.errorCode = code;
        command.httpStatus = httpStatus;
        command.completedAt = clock.instant();
    }

    // Call within the same transaction as the financial write and its audit record.
    public CommandExecution active(UUID accountId, UUID id) {
        var command = commands.lock(accountId, id).orElseThrow(CommandStore::missing);
        if (command.status != CommandExecution.Status.RUNNING
                || command.createdAt.plus(DEADLINE).isBefore(clock.instant())) {
            throw new ApiException(409, "COMMAND_CLOSED", "O comando está encerrado ou expirou.");
        }
        return command;
    }

    @Transactional
    public CommandView view(UUID accountId, UUID id, boolean replayed) {
        var command = commands.lock(accountId, id).orElseThrow(CommandStore::missing);
        if (command.status == CommandExecution.Status.RUNNING
                && command.createdAt.plus(DEADLINE).isBefore(clock.instant())) {
            command.status = CommandExecution.Status.FAILED;
            command.httpStatus = 409;
            command.errorCode = "COMMAND_INTERRUPTED";
            command.responseText = "Processamento interrompido. Confira as ações registradas antes de enviar outro comando.";
            command.completedAt = clock.instant();
        }
        return new CommandView(command.id, command.requestKey, command.status, command.source,
                command.inputText, command.responseText, command.errorCode, command.httpStatus, replayed,
                command.createdAt, command.completedAt,
                actions.findByAccountIdAndCommandIdOrderByCreatedAtAscIdAsc(accountId, id).stream()
                        .map(CommandAction::view).toList());
    }

    public static ApiException missing() {
        return new ApiException(404, "COMMAND_NOT_FOUND", "Comando não encontrado.");
    }

    public record Claim(UUID id, boolean created) {}
}
