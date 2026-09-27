
package com.vinsett.budget.ledger;

import com.vinsett.budget.command.*;
import com.vinsett.budget.shared.*;
import jakarta.validation.Validator;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeParseException;
import java.util.*;
import static com.vinsett.budget.ledger.LedgerTypes.*;

@Service
@Transactional(readOnly = true)
public class LedgerService {
    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private final EntryRepository entries;
    private final BudgetRepository budgets;
    private final ActionRepository actions;
    private final CommandStore commands;
    private final Validator validator;
    private final Clock clock;

    public LedgerService(EntryRepository entries, BudgetRepository budgets, ActionRepository actions,
                         CommandStore commands, Validator validator, Clock clock) {
        this.entries = entries;
        this.budgets = budgets;
        this.actions = actions;
        this.commands = commands;
        this.validator = validator;
        this.clock = clock;
    }

    @Transactional
    public CommandAction.ActionView recordEntry(UUID accountId, UUID commandId, CreateEntry request) {
        validate(request);
        commands.active(accountId, commandId);
        String fingerprint = Hashing.sha256("ENTRY|" + request.type() + "|" + request.category() + "|"
                + request.amount().setScale(2) + "|" + request.occurredOn() + "|" + request.description().strip());
        var previous = actions.findByCommandIdAndFingerprint(commandId, fingerprint);
        if (previous.isPresent()) return previous.get().view();
        var entry = entries.save(new LedgerEntry(accountId, request, clock.instant()));
        String message = (request.type() == EntryType.INCOME ? "Receita" : "Despesa")
                + " de R$ " + request.amount().setScale(2).toPlainString().replace('.', ',')
                + " registrada em " + request.occurredOn() + ": " + request.description().strip() + ".";
        return actions.save(new CommandAction(accountId, commandId, fingerprint, "ENTRY_CREATED",
                entry.id, message, clock.instant())).view();
    }

    @Transactional
    public CommandAction.ActionView setBudget(UUID accountId, UUID commandId, SetBudget request) {
        validate(request);
        var month = month(request.month());
        commands.active(accountId, commandId);
        String fingerprint = Hashing.sha256("BUDGET|" + month + "|" + request.category() + "|" + request.amount().setScale(2));
        var previous = actions.findByCommandIdAndFingerprint(commandId, fingerprint);
        if (previous.isPresent()) return previous.get().view();
        var budget = budgets.findByAccountIdAndMonthStartAndCategory(accountId, month.atDay(1), request.category())
                .orElseGet(() -> new BudgetLimit(accountId, month.atDay(1), request.category(), request.amount()));
        budget.amount = request.amount().setScale(2);
        budgets.saveAndFlush(budget);
        String message = "Limite de " + request.category() + " em " + month + " definido em R$ "
                + budget.amount.toPlainString().replace('.', ',') + ".";
        return actions.save(new CommandAction(accountId, commandId, fingerprint, "BUDGET_SET",
                budget.id, message, clock.instant())).view();
    }

    public EntryView get(UUID accountId, UUID id) {
        return entries.findByAccountIdAndId(accountId, id)
                .orElseThrow(() -> new ApiException(404, "ENTRY_NOT_FOUND", "Lançamento não encontrado.")).view();
    }

    public EntryPage list(UUID accountId, String month, int page, int size) {
        var period = month(month);
        if (page < 0 || size < 1 || size > 100) {
            throw new ApiException(400, "INVALID_PAGE", "Use page >= 0 e size entre 1 e 100.");
        }
        var data = entries.findByAccountIdAndOccurredOnBetween(accountId, period.atDay(1), period.atEndOfMonth(),
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "occurredOn", "createdAt", "id")));
        return new EntryPage(data.getContent().stream().map(LedgerEntry::view).toList(),
                page, size, data.getTotalElements(), data.getTotalPages());
    }

    public MonthlySummary summary(UUID accountId, String month) {
        var period = month(month);
        var totals = entries.totals(accountId, period.atDay(1), period.atEndOfMonth());
        BigDecimal income = ZERO;
        BigDecimal expenses = ZERO;
        var spent = new EnumMap<Category, BigDecimal>(Category.class);
        for (var total : totals) {
            if (total.getType() == EntryType.INCOME) income = income.add(total.getTotal());
            else {
                expenses = expenses.add(total.getTotal());
                spent.put(total.getCategory(), total.getTotal());
            }
        }
        var limits = new EnumMap<Category, BigDecimal>(Category.class);
        budgets.findByAccountIdAndMonthStart(accountId, period.atDay(1))
                .forEach(budget -> limits.put(budget.category, budget.amount));
        var categories = Arrays.stream(Category.values()).map(category -> {
            var value = spent.getOrDefault(category, ZERO);
            var limit = limits.get(category);
            return new CategoryStatus(category, value, limit, limit == null ? null : limit.subtract(value),
                    limit != null && value.compareTo(limit) > 0);
        }).toList();
        return new MonthlySummary(period.toString(), "BRL", income, expenses, income.subtract(expenses),
                entries.balance(accountId, period.atEndOfMonth(), EntryType.INCOME).setScale(2), categories);
    }

    public void validate(Object request) {
        if (request == null || !validator.validate(request).isEmpty()) {
            throw new ApiException(400, "INVALID_FINANCIAL_DATA",
                    "Informe tipo, categoria, data, descrição e valor positivo com até duas casas decimais.");
        }
    }

    public static YearMonth month(String value) {
        try {
            if (value == null || !value.matches("\\d{4}-(0[1-9]|1[0-2])")) throw new DateTimeParseException("month", "", 0);
            return YearMonth.parse(value);
        } catch (DateTimeParseException exception) {
            throw new ApiException(400, "INVALID_MONTH", "Use o mês no formato AAAA-MM.");
        }
    }
}
