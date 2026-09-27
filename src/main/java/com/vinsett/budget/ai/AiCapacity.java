
package com.vinsett.budget.ai;

import com.vinsett.budget.config.AppProperties;
import com.vinsett.budget.shared.ApiException;
import org.springframework.stereotype.Component;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

@Component
public class AiCapacity {
    private final Semaphore permits;

    public AiCapacity(AppProperties properties) {
        permits = new Semaphore(properties.maxConcurrentAiRequests());
    }

    public <T> T run(Supplier<T> work) {
        if (!permits.tryAcquire()) throw new ApiException(429, "AI_BUSY", "A IA está ocupada. Tente novamente em instantes.");
        try { return work.get(); }
        finally { permits.release(); }
    }
}
