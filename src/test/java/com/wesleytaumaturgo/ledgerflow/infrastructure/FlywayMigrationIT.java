package com.wesleytaumaturgo.ledgerflow.infrastructure;

import com.wesleytaumaturgo.ledgerflow.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that Flyway migrations V003 and V004 have been applied correctly.
 *
 * Queries information_schema to assert column names and types.
 * Queries pg_indexes to assert composite indexes exist.
 */
class FlywayMigrationIT extends IntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void account_summary_schema_is_correct() {
        List<String> columns = jdbcTemplate.queryForList(
                """
                SELECT column_name
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name   = 'account_summary'
                ORDER BY ordinal_position
                """,
                String.class
        );

        assertThat(columns).containsExactlyInAnyOrder(
                "account_id",
                "owner_id",
                "current_balance",
                "currency",
                "total_deposited",
                "total_withdrawn",
                "transaction_count",
                "last_event_sequence",
                "last_transaction_at"
        );

        // Verify monetary columns use DECIMAL (numeric in PostgreSQL)
        String balanceType = jdbcTemplate.queryForObject(
                """
                SELECT data_type
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name   = 'account_summary'
                  AND column_name  = 'current_balance'
                """,
                String.class
        );
        assertThat(balanceType).isEqualTo("numeric");

        // Verify account_id is primary key (uuid type)
        String pkType = jdbcTemplate.queryForObject(
                """
                SELECT data_type
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name   = 'account_summary'
                  AND column_name  = 'account_id'
                """,
                String.class
        );
        assertThat(pkType).isEqualTo("uuid");
    }

    @Test
    void transaction_history_schema_is_correct() {
        List<String> columns = jdbcTemplate.queryForList(
                """
                SELECT column_name
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name   = 'transaction_history'
                ORDER BY ordinal_position
                """,
                String.class
        );

        assertThat(columns).containsExactlyInAnyOrder(
                "id",
                "account_id",
                "event_type",
                "amount",
                "currency",
                "description",
                "occurred_at",
                "counterparty_account_id",
                "sequence_number"
        );

        // Verify amount uses DECIMAL (numeric in PostgreSQL)
        String amountType = jdbcTemplate.queryForObject(
                """
                SELECT data_type
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name   = 'transaction_history'
                  AND column_name  = 'amount'
                """,
                String.class
        );
        assertThat(amountType).isEqualTo("numeric");

        // Verify occurred_at is TIMESTAMPTZ (timestamp with time zone)
        String occurredAtType = jdbcTemplate.queryForObject(
                """
                SELECT data_type
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name   = 'transaction_history'
                  AND column_name  = 'occurred_at'
                """,
                String.class
        );
        assertThat(occurredAtType).isEqualTo("timestamp with time zone");
    }

    @Test
    void transaction_history_indexes_exist() {
        List<String> indexes = jdbcTemplate.queryForList(
                """
                SELECT indexname
                FROM pg_indexes
                WHERE schemaname = 'public'
                  AND tablename  = 'transaction_history'
                ORDER BY indexname
                """,
                String.class
        );

        assertThat(indexes).contains(
                "idx_transaction_history_account_occurred",
                "idx_transaction_history_account_type"
        );
    }
}
