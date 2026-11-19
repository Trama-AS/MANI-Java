package com.trama.mani.rules.config;

import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(CategoryRulesProperties.class)
public class RulesConfiguration {

    @Bean
    public RulesCircuitBreaker rulesCircuitBreaker() {
        return new RulesCircuitBreaker("mani-rules-engine", 5, Duration.ofSeconds(10));
    }
}
