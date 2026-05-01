package com.augment.cbsa.service;

@FunctionalInterface
public interface RandomCustomerNumberGenerator {

    long nextCustomerNumber(long highestCustomerNumber);
}