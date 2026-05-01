package com.augment.cbsa.service;

import java.time.Duration;

@FunctionalInterface
public interface CreditAgencyDelayExecutor {

    void delay(Duration duration) throws InterruptedException;
}