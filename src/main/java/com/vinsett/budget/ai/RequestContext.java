
package com.vinsett.budget.ai;

import org.springframework.ai.chat.model.ToolContext;
import java.util.UUID;

public record RequestContext(UUID accountId, UUID commandId) {
    public static RequestContext from(ToolContext context) {
        Object value = context.getContext().get("request");
        if (!(value instanceof RequestContext request)) throw new IllegalStateException("Missing trusted request context");
        return request;
    }
}
