package com.grantora.init;

import com.grantora.entity.Role;
import com.grantora.entity.Scheme;
import com.grantora.entity.SchemeDocumentRequirement;
import com.grantora.entity.SchemeEligibilityQuestion;
import com.grantora.entity.User;
import com.grantora.repository.SchemeRepository;
import com.grantora.repository.UserRepository;
import com.grantora.service.SchemeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
@Order(1)
public class DemoDataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataInitializer.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SchemeRepository schemeRepository;
    private final SchemeService schemeService;

    public DemoDataInitializer(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            SchemeRepository schemeRepository,
            SchemeService schemeService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.schemeRepository = schemeRepository;
        this.schemeService = schemeService;
    }

    @Override
    public void run(String... args) {
        seedDemoOfficers();
        seedDemoHealthcareSchemes();
    }

    private void seedDemoOfficers() {
        seedOfficer(
                "Rahul Sharma",
                "field.officer@grantora.demo",
                "Field@Demo2026!",
                Role.FIELD_OFFICER,
                "FIELD",
                "Karnataka",
                "dakshinakannda",
                "9876543210"
        );

        seedOfficer(
                "Priya Nair",
                "district.officer@grantora.demo",
                "District@Demo2026!",
                Role.DISTRICT_OFFICER,
                "DISTRICT",
                "Karnataka",
                "dakshinakannda",
                "9876543211"
        );

        seedOfficer(
                "Arjun Rao",
                "finance.officer@grantora.demo",
                "Finance@Demo2026!",
                Role.FINANCE_OFFICER,
                "FINANCE",
                "Karnataka",
                null,
                "9876543212"
        );
    }

    private void seedOfficer(
            String fullName,
            String email,
            String tempPassword,
            Role role,
            String officerLevel,
            String jurisdictionState,
            String jurisdictionDistrict,
            String phone) {

        String normalizedEmail = email.trim().toLowerCase();
        if (userRepository.findByEmail(normalizedEmail).isPresent()) {
            log.info("Demo officer account already exists: {}", normalizedEmail);
            return;
        }

        User user = new User();
        user.setFullName(fullName);
        user.setEmail(normalizedEmail);
        user.setPassword(passwordEncoder.encode(tempPassword));
        user.setRole(role);
        user.setOfficerLevel(officerLevel);
        user.setJurisdictionState(jurisdictionState);
        user.setJurisdictionDistrict(jurisdictionDistrict);
        user.setPhone(phone);
        user.setActive(true);

        userRepository.save(user);
        log.info("Created demo officer: {} ({}) with role {}", fullName, normalizedEmail, role);
    }

    private void seedDemoHealthcareSchemes() {
        // SCHEME 1
        seedSchemeIfNotExists(
                "Aarogya Seva – Rural Healthcare Support Grant",
                "Financial assistance to eligible healthcare facilities and organizations to improve access to essential healthcare services in rural and underserved communities. [DEMONSTRATION SCHEME - Not an official government notification]",
                "Improve rural healthcare infrastructure and access to primary healthcare.",
                "Primary health center support, essential medicines, medical outreach in underserved rural settlements.",
                "Ministry of Health & Family Welfare",
                18,
                65,
                new BigDecimal("800000.00"),
                new BigDecimal("50000.00"),
                new BigDecimal("1000000.00"),
                new BigDecimal("10000000.00"),
                new BigDecimal("70.00"),
                List.of(
                        q("Does the facility serve a rural or underserved community?", 25),
                        q("Does the project improve access to essential healthcare?", 25),
                        q("Is there a detailed implementation plan?", 20),
                        q("Is the proposed budget realistic and itemized?", 15),
                        q("Does the applicant have relevant healthcare experience?", 15)
                ),
                List.of(
                        "Facility registration",
                        "Identity proof",
                        "Address proof",
                        "Project proposal",
                        "Estimated budget",
                        "Bank proof"
                )
        );

        // SCHEME 2
        seedSchemeIfNotExists(
                "Digital Healthcare and Telemedicine Grant",
                "Financial support for healthcare organizations implementing telemedicine services and digital healthcare infrastructure. [DEMONSTRATION SCHEME - Not an official government notification]",
                "Improve access to remote medical consultations and digital health services.",
                "Remote telemedicine consults, diagnostic data integration, cloud medical connectivity for rural clinics.",
                "Department of Digital Health Initiatives",
                18,
                65,
                new BigDecimal("1000000.00"),
                new BigDecimal("100000.00"),
                new BigDecimal("1500000.00"),
                new BigDecimal("15000000.00"),
                new BigDecimal("70.00"),
                List.of(
                        q("Does the project provide telemedicine access to underserved communities?", 25),
                        q("Is the proposed digital healthcare infrastructure clearly defined?", 25),
                        q("Is the implementation plan feasible?", 20),
                        q("Is the budget properly justified?", 15),
                        q("Does the organization have relevant technical or healthcare experience?", 15)
                ),
                List.of(
                        "Organization registration",
                        "Facility registration where applicable",
                        "Digital healthcare project proposal",
                        "Technical equipment quotation",
                        "Budget estimate",
                        "Bank proof"
                )
        );

        // SCHEME 3
        seedSchemeIfNotExists(
                "Maternal and Child Healthcare Support Grant",
                "Financial assistance for eligible healthcare organizations improving maternal, newborn and child healthcare services. [DEMONSTRATION SCHEME - Not an official government notification]",
                "Strengthen maternal healthcare, newborn care and essential child health services.",
                "Nutritional support, neonatal emergency care, prenatal and postnatal health monitoring.",
                "Ministry of Health & Family Welfare",
                18,
                65,
                new BigDecimal("800000.00"),
                new BigDecimal("50000.00"),
                new BigDecimal("800000.00"),
                new BigDecimal("8000000.00"),
                new BigDecimal("70.00"),
                List.of(
                        q("Does the project improve maternal healthcare access?", 25),
                        q("Does the project support newborn or child healthcare?", 25),
                        q("Is the proposed healthcare service accessible to underserved communities?", 20),
                        q("Is the implementation budget justified?", 15),
                        q("Does the applicant have relevant healthcare experience?", 15)
                ),
                List.of(
                        "Organization registration",
                        "Healthcare facility registration",
                        "Project proposal",
                        "Implementation budget",
                        "Relevant staff details",
                        "Bank proof"
                )
        );

        // SCHEME 4
        seedSchemeIfNotExists(
                "Medical Equipment Modernization Grant",
                "Financial assistance for eligible healthcare facilities to purchase or upgrade essential diagnostic and medical equipment. [DEMONSTRATION SCHEME - Not an official government notification]",
                "Improve diagnostic facilities and healthcare service quality through modern medical equipment.",
                "Advanced ultrasound, modern digital X-ray, hematology analyzers, diagnostic precision tools.",
                "Department of Medical Equipment Modernization",
                18,
                65,
                new BigDecimal("1000000.00"),
                new BigDecimal("100000.00"),
                new BigDecimal("2000000.00"),
                new BigDecimal("20000000.00"),
                new BigDecimal("70.00"),
                List.of(
                        q("Does the proposed equipment address an existing healthcare service gap?", 25),
                        q("Will the equipment benefit a significant number of patients?", 25),
                        q("Is the equipment specification and procurement plan clear?", 20),
                        q("Is the estimated cost reasonable?", 15),
                        q("Does the facility have the infrastructure to operate the equipment?", 15)
                ),
                List.of(
                        "Facility registration",
                        "Equipment quotations",
                        "Technical specifications",
                        "Project proposal",
                        "Estimated budget",
                        "Bank proof"
                )
        );

        // SCHEME 5
        seedSchemeIfNotExists(
                "Community Health and Mobile Medical Clinic Grant",
                "Financial support for eligible organizations operating mobile medical clinics and community healthcare outreach services. [DEMONSTRATION SCHEME - Not an official government notification]",
                "Expand healthcare outreach in remote villages and underserved communities.",
                "Mobile clinic vans, on-site diagnostics, essential medicine distribution, preventive medical camps.",
                "Ministry of Health & Family Welfare",
                18,
                65,
                new BigDecimal("800000.00"),
                new BigDecimal("100000.00"),
                new BigDecimal("1200000.00"),
                new BigDecimal("12000000.00"),
                new BigDecimal("70.00"),
                List.of(
                        q("Does the project serve remote or underserved communities?", 25),
                        q("Is the mobile clinic or outreach model clearly explained?", 25),
                        q("Is the proposed implementation schedule realistic?", 20),
                        q("Is the vehicle, equipment and operational budget justified?", 15),
                        q("Does the applicant have healthcare outreach experience?", 15)
                ),
                List.of(
                        "Organization registration",
                        "Mobile clinic proposal",
                        "Vehicle or equipment quotations",
                        "Operational budget",
                        "Staff details",
                        "Bank proof"
                )
        );
    }

    private void seedSchemeIfNotExists(
            String schemeName,
            String description,
            String objectives,
            String benefits,
            String department,
            int minAge,
            int maxAge,
            BigDecimal maxIncome,
            BigDecimal minGrant,
            BigDecimal maxGrant,
            BigDecimal totalAllocation,
            BigDecimal passingScore,
            List<SchemeEligibilityQuestion> questions,
            List<String> documentTitles) {

        boolean exists = schemeRepository.findAll().stream()
                .anyMatch(s -> s.getSchemeName() != null && s.getSchemeName().equalsIgnoreCase(schemeName.trim()));

        if (exists) {
            log.info("Demo scheme already exists: {}", schemeName);
            return;
        }

        Scheme scheme = new Scheme();
        scheme.setSchemeName(schemeName);
        scheme.setDescription(description);
        scheme.setObjectives(objectives);
        scheme.setBenefits(benefits);
        scheme.setDepartment(department);
        scheme.setMinimumAge(minAge);
        scheme.setMaximumAge(maxAge);
        scheme.setMaximumAnnualIncome(maxIncome);
        scheme.setMinimumGrantAmount(minGrant);
        scheme.setMaximumGrantAmount(maxGrant);
        scheme.setAllocatedFunds(totalAllocation);
        scheme.setEligibilityThreshold(passingScore);
        scheme.setStatus("DRAFT");
        scheme.setApplicationStartDate(LocalDate.now());
        scheme.setApplicationEndDate(LocalDate.now().plusMonths(6));
        scheme.setEligibleCategories(Set.of("GENERAL", "OBC", "SC", "ST", "EWS", "MINORITY", "OTHER"));

        // Assign questions
        for (int i = 0; i < questions.size(); i++) {
            questions.get(i).setScheme(scheme);
            questions.get(i).setDisplayOrder(i);
        }
        scheme.setEligibilityQuestions(questions);

        // Assign document requirements
        List<SchemeDocumentRequirement> docRequirements = new ArrayList<>();
        for (String title : documentTitles) {
            SchemeDocumentRequirement doc = new SchemeDocumentRequirement();
            doc.setScheme(scheme);
            doc.setTitle(title.trim());
            doc.setDescription("Mandatory document: " + title.trim());
            doc.setRequired(true);
            doc.setAllowedFormats("pdf,png,jpg,jpeg");
            doc.setMaxFileSizeBytes(10485760L);
            doc.setActive(true);
            docRequirements.add(doc);
        }
        scheme.setRequiredDocuments(docRequirements);

        schemeService.createScheme(scheme);
        log.info("Created demo scheme: {} (Status: DRAFT)", schemeName);
    }

    private SchemeEligibilityQuestion q(String text, int points) {
        SchemeEligibilityQuestion question = new SchemeEligibilityQuestion();
        question.setQuestionText(text);
        question.setQuestionType("YES_NO");
        question.setMaximumPoints(BigDecimal.valueOf(points));
        question.setRequired(true);
        question.setAnswerOptions(List.of(
                new SchemeEligibilityQuestion.AnswerOption("YES", "Yes", BigDecimal.valueOf(points), true),
                new SchemeEligibilityQuestion.AnswerOption("NO", "No", BigDecimal.ZERO, false)
        ));
        return question;
    }
}
