
package com.vinsett.budget.api;

import com.vinsett.budget.command.*;
import com.vinsett.budget.ledger.LedgerService;
import com.vinsett.budget.security.AccountPrincipal;
import com.vinsett.budget.shared.Hashing;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
import static com.vinsett.budget.ledger.LedgerTypes.*;

@RestController
@RequestMapping("/api/v1")
public class LedgerController {
    private final LedgerService ledger;
    private final CommandRunner runner;

    public LedgerController(LedgerService ledger, CommandRunner runner) {
        this.ledger = ledger;
        this.runner = runner;
    }

    @PostMapping("/entries")
    ResponseEntity<CommandView> create(@AuthenticationPrincipal AccountPrincipal principal,
            @RequestHeader("Idempotency-Key") UUID key, @Valid @RequestBody CreateEntry request) {
        String input = request.toString();
        var command = runner.run(principal.accountId(), key, Hashing.sha256("REST_ENTRY|" + input),
                "REST_ENTRY", () -> input,
                (id, ignored) -> ledger.recordEntry(principal.accountId(), id, request).message());
        return ResponseEntity.status(command.httpStatus()).body(command);
    }

    @GetMapping("/entries/{id}")
    EntryView get(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable UUID id) {
        return ledger.get(principal.accountId(), id);
    }

    @GetMapping("/entries")
    EntryPage list(@AuthenticationPrincipal AccountPrincipal principal, @RequestParam String month,
                   @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ledger.list(principal.accountId(), month, page, size);
    }

    @GetMapping("/summary")
    MonthlySummary summary(@AuthenticationPrincipal AccountPrincipal principal, @RequestParam String month) {
        return ledger.summary(principal.accountId(), month);
    }

    @PutMapping("/budgets/{month}/{category}")
    ResponseEntity<CommandView> budget(@AuthenticationPrincipal AccountPrincipal principal,
            @RequestHeader("Idempotency-Key") UUID key, @PathVariable String month, @PathVariable Category category,
            @Valid @RequestBody BudgetAmount amount) {
        var request = new SetBudget(month, category, amount.amount());
        ledger.validate(request);
        String input = request.toString();
        var command = runner.run(principal.accountId(), key, Hashing.sha256("REST_BUDGET|" + input),
                "REST_BUDGET", () -> input,
                (id, ignored) -> ledger.setBudget(principal.accountId(), id, request).message());
        return ResponseEntity.status(command.httpStatus()).body(command);
    }
}
