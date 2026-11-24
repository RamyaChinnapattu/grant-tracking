package com.grantora.service;

import com.grantora.GrantoraApplication;
import com.grantora.dto.LoginRequest;
import com.grantora.entity.Role;
import com.grantora.entity.Scheme;
import com.grantora.entity.User;
import com.grantora.init.DemoDataInitializer;
import com.grantora.repository.SchemeRepository;
import com.grantora.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = GrantoraApplication.class)
@ActiveProfiles("test")
public class DemoOfficerAndSchemeSeedingTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SchemeRepository schemeRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AuthService authService;

    @Autowired
    private DemoDataInitializer demoDataInitializer;

    @Test
    @DisplayName("Verify Field Officer Rahul Sharma account exists, is active, BCrypt hashed, and correct role")
    void testFieldOfficerAccount() {
        Optional<User> userOpt = userRepository.findByEmail("field.officer@grantora.demo");
        assertTrue(userOpt.isPresent(), "Field officer account should exist");

        User user = userOpt.get();
        assertEquals("Rahul Sharma", user.getFullName());
        assertEquals("field.officer@grantora.demo", user.getEmail());
        assertEquals(Role.FIELD_OFFICER, user.getRole());
        assertTrue(user.isOfficer(), "isOfficer should be true");
        assertTrue(user.isFieldOfficer(), "isFieldOfficer should be true");
        assertFalse(user.isDistrictOfficer(), "isDistrictOfficer should be false");
        assertFalse(user.isFinanceOfficer(), "isFinanceOfficer should be false");
        assertTrue(user.getActive(), "Account should be active");
        assertEquals("FIELD", user.getOfficerLevel());
        assertEquals("Karnataka", user.getJurisdictionState());
        assertEquals("dakshinakannda", user.getJurisdictionDistrict());

        // Password verification
        assertNotNull(user.getPassword());
        assertTrue(user.getPassword().startsWith("$2a$") || user.getPassword().startsWith("$2b$"),
                "Password must be BCrypt hashed");
        assertTrue(passwordEncoder.matches("Field@Demo2026!", user.getPassword()),
                "BCrypt password must match 'Field@Demo2026!'");
    }

    @Test
    @DisplayName("Verify District Officer Priya Nair account exists, is active, BCrypt hashed, and correct role")
    void testDistrictOfficerAccount() {
        Optional<User> userOpt = userRepository.findByEmail("district.officer@grantora.demo");
        assertTrue(userOpt.isPresent(), "District officer account should exist");

        User user = userOpt.get();
        assertEquals("Priya Nair", user.getFullName());
        assertEquals("district.officer@grantora.demo", user.getEmail());
        assertEquals(Role.DISTRICT_OFFICER, user.getRole());
        assertTrue(user.isOfficer(), "isOfficer should be true");
        assertFalse(user.isFieldOfficer(), "isFieldOfficer should be false");
        assertTrue(user.isDistrictOfficer(), "isDistrictOfficer should be true");
        assertFalse(user.isFinanceOfficer(), "isFinanceOfficer should be false");
        assertTrue(user.getActive(), "Account should be active");
        assertEquals("DISTRICT", user.getOfficerLevel());
        assertEquals("Karnataka", user.getJurisdictionState());
        assertEquals("dakshinakannda", user.getJurisdictionDistrict());

        // Password verification
        assertNotNull(user.getPassword());
        assertTrue(user.getPassword().startsWith("$2a$") || user.getPassword().startsWith("$2b$"),
                "Password must be BCrypt hashed");
        assertTrue(passwordEncoder.matches("District@Demo2026!", user.getPassword()),
                "BCrypt password must match 'District@Demo2026!'");
    }

    @Test
    @DisplayName("Verify Finance Officer Arjun Rao account exists, is active, BCrypt hashed, and correct role")
    void testFinanceOfficerAccount() {
        Optional<User> userOpt = userRepository.findByEmail("finance.officer@grantora.demo");
        assertTrue(userOpt.isPresent(), "Finance officer account should exist");

        User user = userOpt.get();
        assertEquals("Arjun Rao", user.getFullName());
        assertEquals("finance.officer@grantora.demo", user.getEmail());
        assertEquals(Role.FINANCE_OFFICER, user.getRole());
        assertTrue(user.isOfficer(), "isOfficer should be true");
        assertFalse(user.isFieldOfficer(), "isFieldOfficer should be false");
        assertFalse(user.isDistrictOfficer(), "isDistrictOfficer should be false");
        assertTrue(user.isFinanceOfficer(), "isFinanceOfficer should be true");
        assertTrue(user.getActive(), "Account should be active");
        assertEquals("FINANCE", user.getOfficerLevel());
        assertEquals("Karnataka", user.getJurisdictionState());
        assertNull(user.getJurisdictionDistrict(), "Finance officer has jurisdiction over state, no district constraint");

        // Password verification
        assertNotNull(user.getPassword());
        assertTrue(user.getPassword().startsWith("$2a$") || user.getPassword().startsWith("$2b$"),
                "Password must be BCrypt hashed");
        assertTrue(passwordEncoder.matches("Finance@Demo2026!", user.getPassword()),
                "BCrypt password must match 'Finance@Demo2026!'");
    }

    @Test
    @DisplayName("Verify AuthService authenticates all three demo officers with their temporary passwords")
    void testAuthServiceLoginForDemoOfficers() {
        Map<String, Object> fieldRes = authService.login(new LoginRequest("field.officer@grantora.demo", "Field@Demo2026!"));
        assertNotNull(fieldRes);
        assertEquals("FIELD_OFFICER", fieldRes.get("role"));
        assertEquals("Rahul Sharma", fieldRes.get("fullName"));
        assertNotNull(fieldRes.get("token"));

        Map<String, Object> districtRes = authService.login(new LoginRequest("district.officer@grantora.demo", "District@Demo2026!"));
        assertNotNull(districtRes);
        assertEquals("DISTRICT_OFFICER", districtRes.get("role"));
        assertEquals("Priya Nair", districtRes.get("fullName"));
        assertNotNull(districtRes.get("token"));

        Map<String, Object> financeRes = authService.login(new LoginRequest("finance.officer@grantora.demo", "Finance@Demo2026!"));
        assertNotNull(financeRes);
        assertEquals("FINANCE_OFFICER", financeRes.get("role"));
        assertEquals("Arjun Rao", financeRes.get("fullName"));
        assertNotNull(financeRes.get("token"));
    }

    @Test
    @DisplayName("Verify DemoDataInitializer idempotency (re-running causes 0 duplicates and 0 errors)")
    void testSeederIdempotency() throws Exception {
        long userCountBefore = userRepository.count();
        long schemeCountBefore = schemeRepository.count();

        // Run initializer again
        demoDataInitializer.run(new String[0]);

        assertEquals(userCountBefore, userRepository.count(), "User count should not increase on re-run");
        assertEquals(schemeCountBefore, schemeRepository.count(), "Scheme count should not increase on re-run");
    }

    @Test
    @Transactional(readOnly = true)
    @DisplayName("Verify all 5 demo healthcare schemes are persisted with correct configs and scoring questions")
    void testSeededHealthcareSchemes() {
        String[] schemeNames = {
                "Aarogya Seva \u2013 Rural Healthcare Support Grant",
                "Digital Healthcare and Telemedicine Grant",
                "Maternal and Child Healthcare Support Grant",
                "Medical Equipment Modernization Grant",
                "Community Health and Mobile Medical Clinic Grant"
        };

        for (String name : schemeNames) {
            Optional<Scheme> schemeOpt = schemeRepository.findAll().stream()
                    .filter(s -> s.getSchemeName().equalsIgnoreCase(name))
                    .findFirst();

            assertTrue(schemeOpt.isPresent(), "Scheme should exist: " + name);
            Scheme scheme = schemeOpt.get();

            // Status (seeded as DRAFT, or activated by admin)
            assertTrue("DRAFT".equalsIgnoreCase(scheme.getStatus()) || "ACTIVE".equalsIgnoreCase(scheme.getStatus()),
                    "Scheme status must be DRAFT or ACTIVE");
            assertTrue(scheme.getDescription().contains("DEMONSTRATION SCHEME") || scheme.getDescription().contains("fictional"),
                    "Scheme should be identified as demonstration/fictional");

            // Grant configuration
            assertNotNull(scheme.getEligibilityThreshold());
            assertEquals(70, scheme.getEligibilityThreshold().intValue(), "Eligibility threshold should be 70%");
            assertNotNull(scheme.getAllocatedFunds());
            assertNotNull(scheme.getMinimumGrantAmount());
            assertNotNull(scheme.getMaximumGrantAmount());
            assertTrue(scheme.getMaximumGrantAmount().compareTo(scheme.getMinimumGrantAmount()) >= 0);

            // Scoring questions
            assertNotNull(scheme.getEligibilityQuestions());
            assertEquals(5, scheme.getEligibilityQuestions().size(), "Scheme must have exactly 5 scoring questions");
            double totalWeight = scheme.getEligibilityQuestions().stream()
                    .mapToDouble(q -> q.getMaximumPoints() != null ? q.getMaximumPoints().doubleValue() : 0.0)
                    .sum();
            assertEquals(100.0, totalWeight, 0.001, "Scoring questions must sum to exactly 100 points");

            // Document requirements
            assertNotNull(scheme.getRequiredDocuments());
            assertTrue(scheme.getRequiredDocuments().size() >= 6, "Scheme must have at least 6 required documents");
        }
    }
}
