package com.augment.cbsa.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.CockroachContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
public abstract class AbstractCockroachIntegrationTest {

    @Container
    protected static final CockroachContainer COCKROACH =
            new FallbackCockroachContainer(DockerImageName.parse("cockroachdb/cockroach:v24.3.4"));

    @DynamicPropertySource
    static void registerCockroachProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", COCKROACH::getJdbcUrl);
        registry.add("spring.datasource.username", COCKROACH::getUsername);
        registry.add("spring.datasource.password", COCKROACH::getPassword);
    }

    private static final class FallbackCockroachContainer extends CockroachContainer {

        private static final String LOCAL_URL = "jdbc:postgresql://localhost:26257/cbsa?sslmode=disable";
        private static final String LOCAL_USERNAME = "root";
        private static final String LOCAL_PASSWORD = "";

        private final boolean dockerAvailable = DockerClientFactory.instance().isDockerAvailable();

        private FallbackCockroachContainer(DockerImageName dockerImageName) {
            super(dockerImageName);
        }

        @Override
        public void start() {
            if (dockerAvailable) {
                super.start();
            }
        }

        @Override
        public void stop() {
            if (dockerAvailable) {
                super.stop();
            }
        }

        @Override
        public String getJdbcUrl() {
            return dockerAvailable ? super.getJdbcUrl() : LOCAL_URL;
        }

        @Override
        public String getUsername() {
            return dockerAvailable ? super.getUsername() : LOCAL_USERNAME;
        }

        @Override
        public String getPassword() {
            return dockerAvailable ? super.getPassword() : LOCAL_PASSWORD;
        }
    }
}