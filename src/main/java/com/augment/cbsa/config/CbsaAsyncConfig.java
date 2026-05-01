package com.augment.cbsa.config;

import java.time.Clock;
import java.util.Random;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.VirtualThreadTaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;

@Configuration
@EnableAsync
public class CbsaAsyncConfig {

    @Bean(name = "creditAgencyExecutor")
    VirtualThreadTaskExecutor creditAgencyExecutor() {
        return new VirtualThreadTaskExecutor("credit-agency-");
    }

    @Bean
    Clock systemClock() {
        return Clock.systemUTC();
    }

    @Bean(name = "crecustReviewRandom")
    Random crecustReviewRandom() {
        return new Random();
    }
}
