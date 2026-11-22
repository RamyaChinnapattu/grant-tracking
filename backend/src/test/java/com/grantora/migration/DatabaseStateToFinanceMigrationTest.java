package com.grantora.migration;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class DatabaseStateToFinanceMigrationTest {

    @Test
    void migrationExecutesExpectedIdempotentUpdates() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.update(anyString())).thenReturn(1);

        DatabaseStateToFinanceMigration migration = new DatabaseStateToFinanceMigration(jdbcTemplate);
        migration.run();

        // Verifies each migration update was triggered
        verify(jdbcTemplate).update("UPDATE users SET officer_level = 'FINANCE' WHERE officer_level = 'STATE'");
        verify(jdbcTemplate).update("UPDATE grant_applications SET workflow_stage = 'FINANCE' WHERE workflow_stage = 'STATE'");
        verify(jdbcTemplate).update("UPDATE grant_applications SET status = 'FINANCE_REVIEW' WHERE status = 'STATE_REVIEW'");
        verify(jdbcTemplate).update("UPDATE application_verification_history SET stage = 'FINANCE' WHERE stage = 'STATE'");
        verify(jdbcTemplate).update("UPDATE application_verification_history SET next_stage = 'FINANCE' WHERE next_stage = 'STATE'");
        verify(jdbcTemplate).update("UPDATE application_verification_history SET previous_status = 'FINANCE_REVIEW' WHERE previous_status = 'STATE_REVIEW'");
        verify(jdbcTemplate).update("UPDATE application_verification_history SET new_status = 'FINANCE_REVIEW' WHERE new_status = 'STATE_REVIEW'");
        verify(jdbcTemplate).update("UPDATE grant_application_status_history SET from_status = 'FINANCE_REVIEW' WHERE from_status = 'STATE_REVIEW'");
        verify(jdbcTemplate).update("UPDATE grant_application_status_history SET to_status = 'FINANCE_REVIEW' WHERE to_status = 'STATE_REVIEW'");
        verify(jdbcTemplate).update("UPDATE workflow_escalations SET stage = 'FINANCE' WHERE stage = 'STATE'");
    }

    @Test
    void migrationHandlesExceptionsGracefully() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.update(anyString())).thenThrow(new RuntimeException("Table not initialized yet"));

        DatabaseStateToFinanceMigration migration = new DatabaseStateToFinanceMigration(jdbcTemplate);
        // Must not throw or crash application startup
        migration.run();
    }
}
