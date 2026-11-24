package com.grantora.init;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

@Component
@Order(10)
public class Milestone3DemoDataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(Milestone3DemoDataInitializer.class);

    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;

    public Milestone3DemoDataInitializer(JdbcTemplate jdbcTemplate, DataSource dataSource) {
        this.jdbcTemplate = jdbcTemplate;
        this.dataSource = dataSource;
    }

    @Override
    public void run(String... args) {
        try {
            Integer planCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM disbursement_plans WHERE application_id = 5", Integer.class);

            if (planCount == null || planCount == 0) {
                log.info("Initializing Milestone 3 demonstration seed data...");
                ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
                populator.addScript(new ClassPathResource("seed_milestone3_data.sql"));
                populator.setContinueOnError(true);
                populator.execute(dataSource);
                log.info("Milestone 3 demonstration data initialized successfully.");
            } else {
                log.info("Milestone 3 demonstration data already present. Skipping initialization.");
            }
        } catch (Exception e) {
            log.warn("Notice during Milestone 3 demo data check/seeding: {}", e.getMessage());
        }
    }
}
