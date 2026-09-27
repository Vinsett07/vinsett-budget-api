
package com.vinsett.budget.ai;

import com.vinsett.budget.shared.ApiException;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.tool.execution.DefaultToolExecutionExceptionProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.List;

@Configuration(proxyBeanMethods = false)
public class AiConfig {
    @Bean
    ChatClient budgetChatClient(ChatClient.Builder builder) {
        var manager = DefaultToolCallingManager.builder()
                .maxTotalToolCalls(6)
                .maxCallsPerTool(3)
                .toolExecutionExceptionProcessor(
                        new DefaultToolExecutionExceptionProcessor(true, List.of(ApiException.class)))
                .build();
        return builder.defaultAdvisors(ToolCallingAdvisor.builder().toolCallingManager(manager).build()).build();
    }
}
