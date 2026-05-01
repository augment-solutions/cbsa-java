package com.augment.cbsa.web.inqcust.dto;

public record InqcustDateDto(int day, int month, int year) {

    public static InqcustDateDto zero() {
        return new InqcustDateDto(0, 0, 0);
    }
}