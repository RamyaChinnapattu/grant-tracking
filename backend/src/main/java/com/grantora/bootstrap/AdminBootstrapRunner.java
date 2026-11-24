
package com.grantora.bootstrap;

import com.grantora.entity.Role;
import com.grantora.entity.User;
import com.grantora.repository.UserRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class AdminBootstrapRunner implements ApplicationRunner {

    private static final Logger logger =
            LoggerFactory.getLogger(AdminBootstrapRunner.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${grantora.bootstrap.admin.name:}")
    private String adminName;

    @Value("${grantora.bootstrap.admin.email:}")
    private String adminEmail;

    @Value("${grantora.bootstrap.admin.password:}")
    private String adminPassword;

    public AdminBootstrapRunner(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder) {

        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {

        // Check whether an Admin already exists
        boolean adminExists = userRepository.findAll()
                .stream()
                .anyMatch(user -> user.getRole() == Role.ADMIN);

        if (adminExists) {
            logger.info("Admin already exists. Bootstrap skipped.");
            return;
        }

        // Validate configuration
        if (adminName == null || adminName.isBlank()
                || adminEmail == null || adminEmail.isBlank()
                || adminPassword == null
                || adminPassword.length() < 8) {

            logger.warn("Admin configuration is missing or invalid.");
            return;
        }

        String email = adminEmail.trim().toLowerCase();

        // Prevent overwriting an existing account
        if (userRepository.existsByEmail(email)) {
            logger.error("Admin creation stopped: email already exists.");
            return;
        }

        // Create Admin account
        User admin = new User();

        admin.setFullName(adminName.trim());
        admin.setEmail(email);
        admin.setPassword(
                passwordEncoder.encode(adminPassword)
        );
        admin.setRole(Role.ADMIN);
        admin.setActive(true);

        userRepository.save(admin);

        logger.info("Initial Admin account created successfully.");
        logger.info("Admin email: {}", email);
    }
}
