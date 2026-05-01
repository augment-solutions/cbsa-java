package com.augment.cbsa.config;

import com.augment.cbsa.service.CreditAgencyDelayExecutor;
import com.augment.cbsa.service.CreditAgencyDelayGenerator;
import com.augment.cbsa.service.CreditAgencyScoreGenerator;
import java.time.Clock;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
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
    CreditAgencyDelayGenerator creditAgencyDelayGenerator() {
        return agency -> java.time.Duration.ofSeconds(ThreadLocalRandom.current().nextLong(
                agency.minimumDelaySeconds(),
                agency.maximumDelaySecondsExclusive()
        ));
    }

    @Bean
    CreditAgencyScoreGenerator creditAgencyScoreGenerator() {
        // Mirror the COBOL COMPUTE into an integer PIC 999 field: nextInt(1, 999)
        // yields 1..998, matching the source program's effective upper bound once
        // the fractional RANDOM result is truncated into the receiving integer.
        return (agency, request) -> ThreadLocalRandom.current().nextInt(1, 999);
    }

    @Bean
    CreditAgencyDelayExecutor creditAgencyDelayExecutor() {
        return duration -> Thread.sleep(duration);
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
