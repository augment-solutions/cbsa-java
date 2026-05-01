package com.augment.cbsa.domain;

import java.util.Arrays;

public enum CreditAgency {

    CRDTAGY1(1, "CRDTAGY1", "CIPA", 1, 3),
    CRDTAGY2(2, "CRDTAGY2", "CIPB", 1, 3),
    CRDTAGY3(3, "CRDTAGY3", "CIPC", 1, 3),
    CRDTAGY4(4, "CRDTAGY4", "CIPD", 1, 3),
    CRDTAGY5(5, "CRDTAGY5", "CIPE", 1, 3);

    private final int agencyNumber;
    private final String programName;
    private final String containerName;
    private final int minimumDelaySeconds;
    private final int maximumDelaySecondsExclusive;

    CreditAgency(
            int agencyNumber,
            String programName,
            String containerName,
            int minimumDelaySeconds,
            int maximumDelaySecondsExclusive
    ) {
        this.agencyNumber = agencyNumber;
        this.programName = programName;
        this.containerName = containerName;
        this.minimumDelaySeconds = minimumDelaySeconds;
        this.maximumDelaySecondsExclusive = maximumDelaySecondsExclusive;
    }

    public int agencyNumber() {
        return agencyNumber;
    }

    public String programName() {
        return programName;
    }

    public String containerName() {
        return containerName;
    }

    public int minimumDelaySeconds() {
        return minimumDelaySeconds;
    }

    public int maximumDelaySecondsExclusive() {
        return maximumDelaySecondsExclusive;
    }

    public static CreditAgency fromAgencyNumber(int agencyNumber) {
        return Arrays.stream(values())
                .filter(agency -> agency.agencyNumber == agencyNumber)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported credit agency number: " + agencyNumber));
    }
}