
package com.vinsett.budget.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.Clock;

@Configuration(proxyBeanMethods = false)
public class ApplicationConfig {
    @Bean
    Clock clock(AppProperties properties) {
        return Clock.system(properties.zone());
    }
}
