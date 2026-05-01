package com.augment.cbsa.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class CbsaPropertiesValidationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    ConfigurationPropertiesAutoConfiguration.class,
                    ValidationAutoConfiguration.class
            ))
            .withUserConfiguration(TestConfig.class);

    @Test
    void rejectsSortcodesThatAreNotExactlySixDigitsAtStartup() {
        contextRunner.withPropertyValues("cbsa.sortcode=12345").run(context -> {
            assertThat(context).hasFailed();
            Throwable startupFailure = context.getStartupFailure();
            assertThat(startupFailure).hasRootCauseInstanceOf(org.springframework.boot.context.properties.bind.validation.BindValidationException.class);
            assertThat(mostSpecificCause(startupFailure).getMessage()).contains("sortcode");
        });
    }

    private Throwable mostSpecificCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(CbsaProperties.class)
    static class TestConfig {
    }
}
