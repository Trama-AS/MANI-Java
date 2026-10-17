package com.trama.mani.rules.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(CategoryRulesProperties.class)
public class RulesConfiguration {
}
