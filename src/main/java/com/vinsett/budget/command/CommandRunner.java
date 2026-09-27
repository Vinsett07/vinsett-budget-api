
package com.vinsett.budget.command;

import com.vinsett.budget.shared.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Supplier;

@Service
public class CommandRunner {
    private static final Logger log = LoggerFactory.getLogger(CommandRunner.class);
    private final CommandStore store;

    public CommandRunner(CommandStore store) { this.store = store; }

    // External AI calls deliberately run outside database transactions.
    public CommandView run(UUID accountId, UUID key, String hash, String source,
                           Supplier<String> input, BiFunction<UUID, String, String> work) {
        var claim = store.claim(accountId, key, hash, source);
        if (!claim.created()) {
            var previous = store.view(accountId, claim.id(), true);
            if (previous.status() == CommandExecution.Status.RUNNING) {
                throw new ApiException(409, "COMMAND_RUNNING",
                        "Comando em processamento: " + claim.id() + ". Repita a mesma requisição depois.");
            }
            return previous;
        }
        try {
            String text = input.get();
            if (text == null || text.isBlank() || text.length() > 4000) {
                throw new ApiException(400, "INVALID_TRANSCRIPT", "O comando deve conter entre 1 e 4000 caracteres.");
            }
            store.input(accountId, claim.id(), text);
            String reply = work.apply(claim.id(), text);
            if (reply == null || reply.isBlank()) throw new IllegalStateException("Empty model response");
            if (reply.length() > 4000) reply = reply.substring(0, 3997) + "...";
            store.finish(accountId, claim.id(), reply, null, 200);
        } catch (ApiException exception) {
            store.finish(accountId, claim.id(), exception.getMessage(), exception.code(), exception.status());
        } catch (ConcurrencyFailureException | DataIntegrityViolationException exception) {
            store.finish(accountId, claim.id(), "Conflito de atualização. Confira as ações registradas.",
                    "CONCURRENT_UPDATE", 409);
        } catch (RuntimeException exception) {
            log.warn("Command {} failed: {}", claim.id(), exception.getClass().getSimpleName());
            store.finish(accountId, claim.id(),
                    "Não foi possível concluir a resposta. Confira as ações registradas antes de enviar outro comando.",
                    "PROCESSING_FAILED", 502);
        }
        return store.view(accountId, claim.id(), false);
    }
}
