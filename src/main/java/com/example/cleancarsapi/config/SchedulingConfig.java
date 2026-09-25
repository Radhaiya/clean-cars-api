package com.example.cleancarsapi.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Enables the {@code @Scheduled} jobs (see {@code SubscriptionExpiryJob}). */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
