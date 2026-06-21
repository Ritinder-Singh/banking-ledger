package com.banking.ledger;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
    "spring.autoconfigure.exclude=" +
        "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration," +
        "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration," +
        "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration"
})
class LedgerApplicationTests {

    @Test
    void contextLoads() {
        // Smoke test for the skeleton. Real integration tests will replace this
        // with a Testcontainers-backed Postgres in Phase 1.
    }
}
