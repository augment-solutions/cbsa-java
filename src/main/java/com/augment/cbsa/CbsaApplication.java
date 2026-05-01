package com.augment.cbsa;

import com.augment.cbsa.config.CbsaProperties;
import com.augment.cbsa.service.RandomCustomerNumberGenerator;
import java.util.Random;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableConfigurationProperties(CbsaProperties.class)
public class CbsaApplication {

    @Bean
    Random applicationRandom() {
        return new Random();
    }

    @Bean
    RandomCustomerNumberGenerator randomCustomerNumberGenerator(@Qualifier("applicationRandom") Random applicationRandom) {
        return highestCustomerNumber -> {
            if (highestCustomerNumber < 1) {
                throw new IllegalArgumentException("highestCustomerNumber must be positive");
            }
            return applicationRandom.nextLong(highestCustomerNumber) + 1;
        };
    }

    public static void main(String[] args) {
        SpringApplication.run(CbsaApplication.class, args);
    }
}

