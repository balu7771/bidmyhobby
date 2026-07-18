package com.bidmyhobby.hobbyaibackend.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration class to conditionally enable/disable certain beans
 * based on application properties
 */
@Configuration
@ConditionalOnProperty(
    name = "startup-actions.initializeProfiles",
    havingValue = "true",
    matchIfMissing = false
)
public class ConditionalBeanConfig {
    // This class is used to conditionally enable OpenAI configuration
    // when startup-actions.initializeProfiles=true
}