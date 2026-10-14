package com.fincore.jobs;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Los jobs se pueden apagar (fincore.jobs.enabled=false), por ejemplo en tests o en una réplica. */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "fincore.jobs.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
