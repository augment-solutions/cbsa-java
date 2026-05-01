package com.augment.cbsa.support;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.CockroachContainer;
import org.testcontainers.utility.DockerImageName;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

public abstract class AbstractCockroachIntegrationTest {

    // Singleton container: started once per JVM and shared across every test class
    // that extends this base. @Testcontainers/@Container restarts the container
    // between test classes, which leaves Spring's cached @SpringBootTest context
    // pointing at a dead port whenever a second class is loaded.
    protected static final CockroachContainer COCKROACH =
            new FallbackCockroachContainer(DockerImageName.parse("cockroachdb/cockroach:v24.3.4"));

    static {
        COCKROACH.start();
    }

    @DynamicPropertySource
    static void registerCockroachProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", COCKROACH::getJdbcUrl);
        registry.add("spring.datasource.username", COCKROACH::getUsername);
        registry.add("spring.datasource.password", COCKROACH::getPassword);
    }

    private static final class FallbackCockroachContainer extends CockroachContainer {

        private static final String LOCAL_FALLBACK_OPT_IN = "CBSA_TESTS_USE_LOCAL_COCKROACH";
        private static final String LOCAL_DATABASE = "cbsa_test";
        private static final String LOCAL_ADMIN_URL = "jdbc:postgresql://localhost:26257/defaultdb?sslmode=disable";
        private static final String LOCAL_URL = "jdbc:postgresql://localhost:26257/" + LOCAL_DATABASE + "?sslmode=disable";
        private static final String LOCAL_USERNAME = "root";
        private static final String LOCAL_PASSWORD = "";

        private final boolean dockerAvailable = DockerClientFactory.instance().isDockerAvailable();
        private boolean localFallbackInitialized;

        private FallbackCockroachContainer(DockerImageName dockerImageName) {
            super(dockerImageName);
        }

        @Override
        public void start() {
            if (dockerAvailable) {
                super.start();
                return;
            }

            initializeLocalFallback();
        }

        @Override
        public void stop() {
            if (dockerAvailable) {
                super.stop();
            }
        }

        @Override
        public String getJdbcUrl() {
            if (dockerAvailable) {
                return super.getJdbcUrl();
            }

            initializeLocalFallback();
            return LOCAL_URL;
        }

        @Override
        public String getUsername() {
            if (dockerAvailable) {
                return super.getUsername();
            }

            initializeLocalFallback();
            return LOCAL_USERNAME;
        }

        @Override
        public String getPassword() {
            if (dockerAvailable) {
                return super.getPassword();
            }

            initializeLocalFallback();
            return LOCAL_PASSWORD;
        }

        private synchronized void initializeLocalFallback() {
            if (localFallbackInitialized) {
                return;
            }

            // Protect developer databases: the local Cockroach fallback is opt-in via
            // CBSA_TESTS_USE_LOCAL_COCKROACH=true and always targets the dedicated cbsa_test DB.
            assumeTrue(isLocalFallbackOptedIn(), () -> "Cockroach integration tests require Docker or "
                    + LOCAL_FALLBACK_OPT_IN
                    + "=true to use the local fallback database '"
                    + LOCAL_DATABASE
                    + "'.");
            ensureLocalDatabaseExists();
            localFallbackInitialized = true;
        }

        private boolean isLocalFallbackOptedIn() {
            return Boolean.parseBoolean(System.getProperty(LOCAL_FALLBACK_OPT_IN, System.getenv(LOCAL_FALLBACK_OPT_IN)));
        }

        private void ensureLocalDatabaseExists() {
            try (var connection = DriverManager.getConnection(LOCAL_ADMIN_URL, LOCAL_USERNAME, LOCAL_PASSWORD);
                 Statement statement = connection.createStatement()) {
                statement.execute("CREATE DATABASE IF NOT EXISTS " + LOCAL_DATABASE);
            } catch (SQLException exception) {
                throw new IllegalStateException(
                        "Unable to prepare local Cockroach fallback database '" + LOCAL_DATABASE + "'.",
                        exception
                );
            }
        }
    }
}