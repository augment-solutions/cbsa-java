package com.augment.cbsa.domain;

import java.util.Objects;

public record DbcrfunOrigin(
        String applid,
        String userid,
        String facilityName,
        String netwrkId,
        int facilityType,
        String fill0
) {

    public DbcrfunOrigin {
        Objects.requireNonNull(applid, "applid must not be null");
        Objects.requireNonNull(userid, "userid must not be null");
        Objects.requireNonNull(facilityName, "facilityName must not be null");
        Objects.requireNonNull(netwrkId, "netwrkId must not be null");
        Objects.requireNonNull(fill0, "fill0 must not be null");
    }

    public static DbcrfunOrigin blank() {
        return new DbcrfunOrigin("", "", "", "", 0, "");
    }

    public String paymentDescription() {
        String header = padRight(applid, 8) + padRight(userid, 8);
        return header.substring(0, 14);
    }

    private static String padRight(String value, int length) {
        if (value.length() >= length) {
            return value.substring(0, length);
        }
        return value + " ".repeat(length - value.length());
    }
}