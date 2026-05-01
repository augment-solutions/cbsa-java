package com.augment.cbsa.config;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "cbsa")
public record CbsaProperties(
        @NotNull
        @Pattern(regexp = "\\d{6}")
        String sortcode
) {
}
