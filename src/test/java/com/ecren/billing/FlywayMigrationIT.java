package com.ecren.billing;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/*
 * @SpringBootTest boots the full application context.
 * @Import(TestcontainersConfiguration.class) brings in the PostgreSQL container bean
 * which is annotated with @ServiceConnection — Spring Boot auto-wires its JDBC URL
 * into the DataSource without any manual property setting.
 *
 * We use a real PostgreSQL container (not H2) because our schema uses
 * PostgreSQL-specific SQL: partial unique index, gen_random_uuid(), ON CONFLICT.
 * H2 would silently accept or reject these differently, making tests unreliable.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class FlywayMigrationIT {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void allExpectedTablesExist() {
        List<String> tables = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'",
                String.class
        );

        assertThat(tables).containsExactlyInAnyOrder(
                "tenants",
                "plans",
                "plan_metric_limits",
                "subscriptions",
                "usage_records",
                "invoices",
                "invoice_line_items",
                "ledger_entries",
                "payments",
                "shedlock",
                "flyway_schema_history"
        );
    }

    @Test
    void partialUniqueIndexOnSubscriptionsExists() {
        // A plain unique index wouldn't have a WHERE clause.
        // We check pg_indexes to confirm the partial index was created correctly.
        Integer count = jdbc.queryForObject(
                """
                SELECT COUNT(*) FROM pg_indexes
                WHERE tablename  = 'subscriptions'
                  AND indexname  = 'subscriptions_one_active_per_tenant'
                """,
                Integer.class
        );

        assertThat(count).isEqualTo(1);
    }
}
