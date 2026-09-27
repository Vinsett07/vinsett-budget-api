
package com.vinsett.budget.ai;

import com.vinsett.budget.command.CommandAction;
import com.vinsett.budget.ledger.LedgerService;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.*;
import org.springframework.stereotype.Component;
import static com.vinsett.budget.ledger.LedgerTypes.*;

@Component
public class BudgetTools {
    private final LedgerService ledger;

    public BudgetTools(LedgerService ledger) { this.ledger = ledger; }

    @Tool(description = "Record one BRL income or expense explicitly requested by the user. Never invent an amount, description or date. Duplicate identical entries within one command are deduplicated.")
    public CommandAction.ActionView recordEntry(
            @ToolParam(description = "Type INCOME or EXPENSE, category, positive BRL amount, description and ISO date YYYY-MM-DD") CreateEntry entry,
            ToolContext context) {
        var request = RequestContext.from(context);
        return ledger.recordEntry(request.accountId(), request.commandId(), entry);
    }

    @Tool(description = "Set or replace a BRL expense budget limit for a category and month, only when explicitly requested.")
    public CommandAction.ActionView setBudget(
            @ToolParam(description = "Month YYYY-MM, category and positive BRL amount") SetBudget budget,
            ToolContext context) {
        var request = RequestContext.from(context);
        return ledger.setBudget(request.accountId(), request.commandId(), budget);
    }

    @Tool(description = "Get authoritative monthly income, expenses, net, balance through month end, and category limits. Use these totals instead of calculating with listed entries.")
    public MonthlySummary getMonthlySummary(
            @ToolParam(description = "Month in YYYY-MM format") String month, ToolContext context) {
        return ledger.summary(RequestContext.from(context).accountId(), month);
    }

    @Tool(description = "List one page of entries for a month. Results are paginated; never treat this page as the full balance.")
    public EntryPage listEntries(@ToolParam(description = "Month YYYY-MM") String month,
                                 @ToolParam(description = "Page index starting at 0") int page,
                                 ToolContext context) {
        return ledger.list(RequestContext.from(context).accountId(), month, page, 20);
    }
}
