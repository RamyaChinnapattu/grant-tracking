package com.grantora.migration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Migration runner to safely normalize legacy 'STATE' references in existing
 * database records to 'FINANCE' without deleting or corrupting records.
 */
@Component
public class DatabaseStateToFinanceMigration implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DatabaseStateToFinanceMigration.class);

    private final JdbcTemplate jdbcTemplate;

    public DatabaseStateToFinanceMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        try {
            migrateOfficerLevels();
            migrateApplicationWorkflowStages();
            migrateVerificationHistory();
            migrateStatusHistory();
            migrateWorkflowEscalations();
        } catch (Exception e) {
            log.warn("Database migration check encountered a non-critical warning: {}", e.getMessage());
        }
    }

    private void migrateOfficerLevels() {
        try {
            int updated = jdbcTemplate.update(
                    "UPDATE users SET officer_level = 'FINANCE' WHERE officer_level = 'STATE'");
            if (updated > 0) {
                log.info("Migrated {} officer accounts from officer_level='STATE' to 'FINANCE'.", updated);
            }
        } catch (Exception ignored) {
            // Table may not exist yet during initial boot
        }
    }

    private void migrateApplicationWorkflowStages() {
        try {
            int updatedStage = jdbcTemplate.update(
                    "UPDATE grant_applications SET workflow_stage = 'FINANCE' WHERE workflow_stage = 'STATE'");
            int updatedStatus = jdbcTemplate.update(
                    "UPDATE grant_applications SET status = 'FINANCE_REVIEW' WHERE status = 'STATE_REVIEW'");
            if (updatedStage > 0 || updatedStatus > 0) {
                log.info("Migrated grant_applications: {} stages and {} statuses to FINANCE/FINANCE_REVIEW.",
                        updatedStage, updatedStatus);
            }
        } catch (Exception ignored) {
        }
    }

    private void migrateVerificationHistory() {
        try {
            int updatedStage = jdbcTemplate.update(
                    "UPDATE application_verification_history SET stage = 'FINANCE' WHERE stage = 'STATE'");
            int updatedNextStage = jdbcTemplate.update(
                    "UPDATE application_verification_history SET next_stage = 'FINANCE' WHERE next_stage = 'STATE'");
            int updatedPrevStatus = jdbcTemplate.update(
                    "UPDATE application_verification_history SET previous_status = 'FINANCE_REVIEW' WHERE previous_status = 'STATE_REVIEW'");
            int updatedNewStatus = jdbcTemplate.update(
                    "UPDATE application_verification_history SET new_status = 'FINANCE_REVIEW' WHERE new_status = 'STATE_REVIEW'");
            if (updatedStage > 0 || updatedNextStage > 0 || updatedPrevStatus > 0 || updatedNewStatus > 0) {
                log.info("Migrated application_verification_history: {} stages, {} next_stages, {} statuses to FINANCE.",
                        updatedStage, updatedNextStage, updatedPrevStatus + updatedNewStatus);
            }
        } catch (Exception ignored) {
        }
    }

    private void migrateStatusHistory() {
        try {
            int updatedFrom = jdbcTemplate.update(
                    "UPDATE grant_application_status_history SET from_status = 'FINANCE_REVIEW' WHERE from_status = 'STATE_REVIEW'");
            int updatedTo = jdbcTemplate.update(
                    "UPDATE grant_application_status_history SET to_status = 'FINANCE_REVIEW' WHERE to_status = 'STATE_REVIEW'");
            if (updatedFrom > 0 || updatedTo > 0) {
                log.info("Migrated grant_application_status_history: {} from_statuses and {} to_statuses to FINANCE_REVIEW.",
                        updatedFrom, updatedTo);
            }
        } catch (Exception ignored) {
        }
    }

    private void migrateWorkflowEscalations() {
        try {
            int updated = jdbcTemplate.update(
                    "UPDATE workflow_escalations SET stage = 'FINANCE' WHERE stage = 'STATE'");
            if (updated > 0) {
                log.info("Migrated {} workflow_escalations records to stage='FINANCE'.", updated);
            }
        } catch (Exception ignored) {
        }
    }
}
