package com.augment.cbsa;

import com.augment.cbsa.repository.AccountRepository;
import com.augment.cbsa.repository.CreaccRepository;
import com.augment.cbsa.repository.CrecustRepository;
import com.augment.cbsa.repository.CustomerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Self-contained smoke test for the bootstrap context wiring.
 * Per translation-rules.md §9, real persistence tests use a Testcontainers
 * CockroachDB and are added per-program. This test deliberately excludes the
 * DataSource/Flyway/jOOQ autoconfigurations so it can run without a database.
 */
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=" +
                "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration," +
                "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration," +
                "org.springframework.boot.autoconfigure.jooq.JooqAutoConfiguration"
})
@ActiveProfiles("test")
class CbsaApplicationTests {

    @MockitoBean
    private AccountRepository accountRepository;

    @MockitoBean
    private CustomerRepository customerRepository;

    @MockitoBean
    private CrecustRepository crecustRepository;

    @MockitoBean
    private CreaccRepository creaccRepository;

    @Test
    void contextLoads() {
    }
}
