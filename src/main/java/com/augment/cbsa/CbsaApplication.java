package com.augment.cbsa;

import com.augment.cbsa.service.RandomCustomerNumberGenerator;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CbsaApplication {

    @Bean
    RandomCustomerNumberGenerator randomCustomerNumberGenerator() {
        return highestCustomerNumber -> ThreadLocalRandom.current().nextLong(1, highestCustomerNumber + 1);
    }

    public static void main(String[] args) {
        SpringApplication.run(CbsaApplication.class, args);
    }
}
