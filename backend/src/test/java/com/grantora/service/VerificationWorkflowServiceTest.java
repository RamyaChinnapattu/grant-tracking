
package com.grantora.service;

import com.grantora.dto.VerificationDecisionRequest;
import com.grantora.entity.*;
import com.grantora.repository.ApplicationDocumentRepository;
import com.grantora.repository.ApplicationVerificationRepository;
import com.grantora.repository.GrantApplicationRepository;
import com.grantora.repository.UserRepository;
import com.grantora.repository.VerificationRoutingRuleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class VerificationWorkflowServiceTest {

        private GrantApplicationRepository applicationRepository;
        private UserRepository userRepository;
        private ApplicationVerificationRepository historyRepository;
        private ApplicationDocumentRepository documentRepository;
        private VerificationRoutingRuleRepository routingRules;
        private VerificationWorkflowService workflow;

        private GrantApplication application;
        private User fieldOfficer;
        private User districtOfficer;
        private User financeOfficer;

        @BeforeEach
        void setUp() throws Exception {
                applicationRepository = mock(GrantApplicationRepository.class);
                userRepository = mock(UserRepository.class);
                historyRepository = mock(ApplicationVerificationRepository.class);
                documentRepository = mock(ApplicationDocumentRepository.class);
                routingRules = mock(VerificationRoutingRuleRepository.class);

                workflow = new VerificationWorkflowService(
                                applicationRepository,
                                userRepository,
                                historyRepository,
                                documentRepository,
                                routingRules);

                User beneficiaryUser = user(
                                1L, Role.BENEFICIARY, null, null, null);

                Beneficiary beneficiary = new Beneficiary();
                beneficiary.setUser(beneficiaryUser);
                beneficiary.setState("Test State");
                beneficiary.setDistrict("Test District");

                application = new GrantApplication();
                setId(application, 99L);
                application.setBeneficiary(beneficiary);
                application.setRequestedAmount(new BigDecimal("5000.00"));
                application.setStatus(GrantApplicationStatus.FIELD_VERIFICATION);
                application.setWorkflowStage(VerificationStage.FIELD);

                fieldOfficer = user(
                                10L, Role.OFFICER, "FIELD", "Test State", "Test District");

                districtOfficer = user(
                                20L, Role.OFFICER, "DISTRICT", "Test State", "Test District");

                financeOfficer = user(
                                30L, Role.OFFICER, "FINANCE", "Test State", null);

                Scheme scheme = new Scheme();
                scheme.setSchemeName("Education Assistance");
                application.setScheme(scheme);
                application.setAssignedOfficer(fieldOfficer);

                when(applicationRepository.findById(99L))
                                .thenReturn(Optional.of(application));

                when(applicationRepository.findByIdForUpdate(99L))
                                .thenReturn(Optional.of(application));

                when(applicationRepository.save(any(GrantApplication.class)))
                                .thenAnswer(invocation -> invocation.getArgument(0));

                List<ApplicationVerification> savedHistory = new java.util.ArrayList<>();
                when(historyRepository.save(any(ApplicationVerification.class)))
                                .thenAnswer(invocation -> {
                                        ApplicationVerification v = invocation.getArgument(0);
                                        savedHistory.add(v);
                                        return v;
                                });

                when(historyRepository.findByApplication_IdOrderByCreatedAtAsc(anyLong()))
                                .thenAnswer(invocation -> savedHistory);
        }

        @Test
        void applicationAdvancesOnlyThroughFieldDistrictAndFinance() {
                when(userRepository.findByEmail("field@test.local"))
                                .thenReturn(Optional.of(fieldOfficer));

                when(userRepository.findAll())
                                .thenReturn(List.of(districtOfficer));

                when(documentRepository.countByApplication_Id(99L))
                                .thenReturn(1L);

                workflow.decide(
                                99L,
                                "field@test.local",
                                false,
                                new VerificationDecisionRequest(
                                                VerificationDecision.VERIFIED,
                                                "Identity and documents reviewed.",
                                                true, true, true, true));

                assertEquals(VerificationStage.DISTRICT, application.getWorkflowStage());
                assertEquals(GrantApplicationStatus.UNDER_VERIFICATION, application.getStatus());
                assertEquals(districtOfficer, application.getAssignedOfficer());

                when(userRepository.findByEmail("district@test.local"))
                                .thenReturn(Optional.of(districtOfficer));

                when(userRepository.findAll())
                                .thenReturn(List.of(financeOfficer));

                workflow.decide(
                                99L,
                                "district@test.local",
                                false,
                                new VerificationDecisionRequest(
                                                VerificationDecision.APPROVED,
                                                "District review complete.",
                                                false, false, false, false));

                assertEquals(VerificationStage.FINANCE, application.getWorkflowStage());
                assertEquals(GrantApplicationStatus.UNDER_VERIFICATION, application.getStatus());
                assertEquals(financeOfficer, application.getAssignedOfficer());

                when(userRepository.findByEmail("finance@test.local"))
                                .thenReturn(Optional.of(financeOfficer));

                workflow.decide(
                                99L,
                                "finance@test.local",
                                false,
                                new VerificationDecisionRequest(
                                                VerificationDecision.APPROVED,
                                                "Final finance review complete.",
                                                false, false, false, false));

                assertEquals(GrantApplicationStatus.APPROVED, application.getStatus());
                assertEquals(null, application.getAssignedOfficer());

                verify(historyRepository, times(5))
                                .save(any(ApplicationVerification.class));
        }

        @Test
        void submissionAndCorrectionResubmissionKeepTheApplicationInSubmittedStatus() {
                when(userRepository.findByEmail("beneficiary@test.local"))
                                .thenReturn(Optional.of(application.getBeneficiary().getUser()));

                when(userRepository.findAll())
                                .thenReturn(List.of(fieldOfficer));

                application.setStatus(GrantApplicationStatus.DRAFT);

                workflow.submit(application, "beneficiary@test.local");

                assertEquals(GrantApplicationStatus.SUBMITTED, application.getStatus());
                assertEquals(VerificationStage.FIELD, application.getWorkflowStage());
                assertEquals(fieldOfficer, application.getAssignedOfficer());

                application.setStatus(GrantApplicationStatus.NEEDS_CORRECTION);
                application.setTaskDueAt(LocalDateTime.now().minusDays(1));

                LocalDateTime resubmissionStartedAt = LocalDateTime.now();

                workflow.submit(application, "beneficiary@test.local");

                assertEquals(GrantApplicationStatus.SUBMITTED, application.getStatus());
                assertEquals(VerificationStage.FIELD, application.getWorkflowStage());
                assertEquals(fieldOfficer, application.getAssignedOfficer());

                assertTrue(!application.getTaskAssignedAt().isBefore(resubmissionStartedAt));
                assertTrue(application.getTaskDueAt().isAfter(resubmissionStartedAt));

                assertTrue(
                                !application.getTaskDueAt().isAfter(
                                                resubmissionStartedAt.plusDays(5).plusSeconds(1)));

                assertEquals(false, application.isEscalated());

                verify(historyRepository, times(3))
                                .save(any(ApplicationVerification.class));
        }

        @Test
        void fieldOfficerCannotSkipTheDistrictStage() {
                when(userRepository.findByEmail("field@test.local"))
                                .thenReturn(Optional.of(fieldOfficer));

                ResponseStatusException exception = assertThrows(
                                ResponseStatusException.class,
                                () -> workflow.decide(
                                                99L,
                                                "field@test.local",
                                                false,
                                                new VerificationDecisionRequest(
                                                                VerificationDecision.APPROVED,
                                                                "Attempted final approval.",
                                                                true, true, true, true)));

                assertEquals(409, exception.getStatusCode().value());
                assertEquals(
                                GrantApplicationStatus.FIELD_VERIFICATION,
                                application.getStatus());

                verify(historyRepository, never())
                                .save(any(ApplicationVerification.class));
        }

        @Test
        void fieldVerificationRequiresChecklistAndUploadedDocuments() {
                when(userRepository.findByEmail("field@test.local"))
                                .thenReturn(Optional.of(fieldOfficer));

                ResponseStatusException checklistError = assertThrows(
                                ResponseStatusException.class,
                                () -> workflow.decide(
                                                99L,
                                                "field@test.local",
                                                false,
                                                new VerificationDecisionRequest(
                                                                VerificationDecision.VERIFIED,
                                                                "Checked.",
                                                                true, true, false, true)));

                assertEquals(400, checklistError.getStatusCode().value());

                when(documentRepository.countByApplication_Id(99L))
                                .thenReturn(0L);

                ResponseStatusException documentError = assertThrows(
                                ResponseStatusException.class,
                                () -> workflow.decide(
                                                99L,
                                                "field@test.local",
                                                false,
                                                new VerificationDecisionRequest(
                                                                VerificationDecision.VERIFIED,
                                                                "Checked.",
                                                                true, true, true, true)));

                assertEquals(409, documentError.getStatusCode().value());
        }

        @Test
        void officerCannotDecideAnApplicationAssignedToSomeoneElse() throws Exception {
                User otherFieldOfficer = user(
                                11L, Role.OFFICER, "FIELD", "Test State", "Test District");

                when(userRepository.findByEmail("other@test.local"))
                                .thenReturn(Optional.of(otherFieldOfficer));

                ResponseStatusException exception = assertThrows(
                                ResponseStatusException.class,
                                () -> workflow.decide(
                                                99L,
                                                "other@test.local",
                                                false,
                                                new VerificationDecisionRequest(
                                                                VerificationDecision.REJECTED,
                                                                "Not assigned.",
                                                                false, false, false, false)));

                assertEquals(403, exception.getStatusCode().value());
        }

        @Test
        void correctionRequestRetainsCurrentStageAndAssignee() {
                when(userRepository.findByEmail("field@test.local"))
                                .thenReturn(Optional.of(fieldOfficer));

                workflow.decide(
                                99L,
                                "field@test.local",
                                false,
                                new VerificationDecisionRequest(
                                                VerificationDecision.NEEDS_CORRECTION,
                                                "Please provide a current address proof.",
                                                false, false, false, false));

                assertEquals(VerificationStage.FIELD, application.getWorkflowStage());
                assertEquals(GrantApplicationStatus.NEEDS_CORRECTION, application.getStatus());
                assertEquals(fieldOfficer, application.getAssignedOfficer());
        }

        @Test
        void districtApprovalAlwaysAdvancesToFinance() {
                when(userRepository.findByEmail("district@test.local"))
                                .thenReturn(Optional.of(districtOfficer));

                when(userRepository.findAll())
                                .thenReturn(List.of(financeOfficer));

                application.setWorkflowStage(VerificationStage.DISTRICT);
                application.setStatus(GrantApplicationStatus.UNDER_VERIFICATION);
                application.setAssignedOfficer(districtOfficer);

                workflow.decide(
                                99L,
                                "district@test.local",
                                false,
                                new VerificationDecisionRequest(
                                                VerificationDecision.APPROVED,
                                                "District approval complete.",
                                                false, false, false, false));

                assertEquals(VerificationStage.FINANCE, application.getWorkflowStage());
                assertEquals(
                                GrantApplicationStatus.UNDER_VERIFICATION,
                                application.getStatus());

                assertEquals(financeOfficer, application.getAssignedOfficer());
        }

        @Test
        void adminCanAssignOnlyAnActiveOfficerAtTheCurrentStageAndJurisdiction() throws Exception {
                User admin = user(2L, Role.ADMIN, null, null, null);

                User alternateFieldOfficer = user(
                                11L, Role.OFFICER, "FIELD", "Test State", "Test District");

                when(userRepository.findByEmail("admin@test.local"))
                                .thenReturn(Optional.of(admin));

                when(userRepository.findById(11L))
                                .thenReturn(Optional.of(alternateFieldOfficer));

                workflow.assign(99L, 11L, "admin@test.local", true);

                assertEquals(alternateFieldOfficer, application.getAssignedOfficer());

                var assignment = org.mockito.ArgumentCaptor.forClass(
                                ApplicationVerification.class);

                verify(historyRepository).save(assignment.capture());

                assertEquals("ASSIGNED", assignment.getValue().getAction());
                assertEquals(admin, assignment.getValue().getActor());
                assertEquals(alternateFieldOfficer, assignment.getValue().getAssignedOfficer());

                assertEquals(
                                GrantApplicationStatus.FIELD_VERIFICATION,
                                assignment.getValue().getPreviousStatus());

                assertEquals(
                                GrantApplicationStatus.FIELD_VERIFICATION,
                                assignment.getValue().getNewStatus());
        }

        @Test
        void adminCannotAssignAnOfficerOutsideTheApplicationJurisdiction() throws Exception {
                User admin = user(2L, Role.ADMIN, null, null, null);

                User otherJurisdiction = user(
                                11L, Role.OFFICER, "FIELD", "Other State", "Other District");

                when(userRepository.findByEmail("admin@test.local"))
                                .thenReturn(Optional.of(admin));

                when(userRepository.findById(11L))
                                .thenReturn(Optional.of(otherJurisdiction));

                ResponseStatusException exception = assertThrows(
                                ResponseStatusException.class,
                                () -> workflow.assign(
                                                99L, 11L, "admin@test.local", true));

                assertEquals(400, exception.getStatusCode().value());
                assertEquals(fieldOfficer, application.getAssignedOfficer());

                verify(historyRepository, never())
                                .save(any(ApplicationVerification.class));
        }

        @Test
        void nonAdminCannotAssignApplications() {
                ResponseStatusException exception = assertThrows(
                                ResponseStatusException.class,
                                () -> workflow.assign(
                                                99L, 10L, "field@test.local", false));

                assertEquals(403, exception.getStatusCode().value());

                verify(historyRepository, never())
                                .save(any(ApplicationVerification.class));
        }

        @Test
        void fieldOfficerCannotPerformDistrictOrFinanceApproval() {
                application.setWorkflowStage(VerificationStage.DISTRICT);
                application.setStatus(GrantApplicationStatus.DISTRICT_REVIEW);
                application.setAssignedOfficer(fieldOfficer);

                when(userRepository.findByEmail("field@test.local"))
                                .thenReturn(Optional.of(fieldOfficer));

                ResponseStatusException districtException = assertThrows(
                                ResponseStatusException.class,
                                () -> workflow.decide(
                                                99L,
                                                "field@test.local",
                                                false,
                                                new VerificationDecisionRequest(
                                                                VerificationDecision.APPROVED,
                                                                "Trying district review as field officer",
                                                                false, false, false, false)));

                assertEquals(403, districtException.getStatusCode().value());
                assertTrue(districtException.getReason().contains("not authorized for this review level"));

                application.setWorkflowStage(VerificationStage.FINANCE);
                application.setStatus(GrantApplicationStatus.FINANCE_REVIEW);
                application.setAssignedOfficer(fieldOfficer);

                ResponseStatusException financeException = assertThrows(
                                ResponseStatusException.class,
                                () -> workflow.decide(
                                                99L,
                                                "field@test.local",
                                                false,
                                                new VerificationDecisionRequest(
                                                                VerificationDecision.APPROVED,
                                                                "Trying finance review as field officer",
                                                                false, false, false, false)));

                assertEquals(403, financeException.getStatusCode().value());
                assertTrue(financeException.getReason().contains("not authorized for this review level"));
        }

        @Test
        void districtOfficerCannotPerformFinanceApproval() {
                application.setWorkflowStage(VerificationStage.FINANCE);
                application.setStatus(GrantApplicationStatus.FINANCE_REVIEW);
                application.setAssignedOfficer(districtOfficer);

                when(userRepository.findByEmail("district@test.local"))
                                .thenReturn(Optional.of(districtOfficer));

                ResponseStatusException financeException = assertThrows(
                                ResponseStatusException.class,
                                () -> workflow.decide(
                                                99L,
                                                "district@test.local",
                                                false,
                                                new VerificationDecisionRequest(
                                                                VerificationDecision.APPROVED,
                                                                "Trying finance review as district officer",
                                                                false, false, false, false)));

                assertEquals(403, financeException.getStatusCode().value());
                assertTrue(financeException.getReason().contains("not authorized for this review level"));
        }

        @Test
        void skippingVerificationStagesIsBlockedAtFinance() {
                application.setWorkflowStage(VerificationStage.FINANCE);
                application.setStatus(GrantApplicationStatus.FINANCE_REVIEW);
                application.setAssignedOfficer(financeOfficer);

                when(userRepository.findByEmail("finance@test.local"))
                                .thenReturn(Optional.of(financeOfficer));

                // History only has a field submission, not verified and district approved
                ApplicationVerification draftSubmission = new ApplicationVerification();
                draftSubmission.setStage(VerificationStage.FIELD);
                draftSubmission.setAction("SUBMITTED");
                when(historyRepository.findByApplication_IdOrderByCreatedAtAsc(99L))
                                .thenReturn(List.of(draftSubmission));

                ResponseStatusException conflictException = assertThrows(
                                ResponseStatusException.class,
                                () -> workflow.decide(
                                                99L,
                                                "finance@test.local",
                                                false,
                                                new VerificationDecisionRequest(
                                                                VerificationDecision.APPROVED,
                                                                "Approving without prior reviews",
                                                                false, false, false, false)));

                assertEquals(409, conflictException.getStatusCode().value());
                assertTrue(conflictException.getReason().contains("Field verification and District review must be completed first"));
        }

        @Test
        void selfApprovalIsForbidden() throws Exception {
                // Beneficiary user who is also registered as a field officer
                User beneficiaryAsOfficer = user(1L, Role.OFFICER, "FIELD", "Test State", "Test District");
                application.getBeneficiary().setUser(beneficiaryAsOfficer);
                application.setAssignedOfficer(beneficiaryAsOfficer);

                when(userRepository.findByEmail("beneficiary-officer@test.local"))
                                .thenReturn(Optional.of(beneficiaryAsOfficer));

                ResponseStatusException forbiddenException = assertThrows(
                                ResponseStatusException.class,
                                () -> workflow.decide(
                                                99L,
                                                "beneficiary-officer@test.local",
                                                false,
                                                new VerificationDecisionRequest(
                                                                VerificationDecision.VERIFIED,
                                                                "Self verifying",
                                                                true, true, true, true)));

                assertEquals(403, forbiddenException.getStatusCode().value());
                assertTrue(forbiddenException.getReason().contains("Officers cannot verify or approve their own grant applications"));
        }

        @Test
        void rejectionAtFieldOrDistrictOrFinanceTerminatesWorkflow() {
                when(userRepository.findByEmail("field@test.local"))
                                .thenReturn(Optional.of(fieldOfficer));

                workflow.decide(
                                99L,
                                "field@test.local",
                                false,
                                new VerificationDecisionRequest(
                                                VerificationDecision.REJECTED,
                                                "Documents invalid.",
                                                false, false, false, false));

                assertEquals(GrantApplicationStatus.REJECTED, application.getStatus());
                assertEquals(null, application.getAssignedOfficer());
                assertEquals(true, application.getTaskCompletedAt() != null);
        }

        @Test
        void unauthorizedJurisdictionCannotReviewApplication() throws Exception {
                User wrongDistrictOfficer = user(25L, Role.OFFICER, "DISTRICT", "Test State", "Different District");
                application.setWorkflowStage(VerificationStage.DISTRICT);
                application.setStatus(GrantApplicationStatus.DISTRICT_REVIEW);
                application.setAssignedOfficer(wrongDistrictOfficer);

                when(userRepository.findByEmail("wrong-district@test.local"))
                                .thenReturn(Optional.of(wrongDistrictOfficer));

                ResponseStatusException jurisdictionException = assertThrows(
                                ResponseStatusException.class,
                                () -> workflow.decide(
                                                99L,
                                                "wrong-district@test.local",
                                                false,
                                                new VerificationDecisionRequest(
                                                                VerificationDecision.APPROVED,
                                                                "Reviewing across district boundaries",
                                                                false, false, false, false)));

                assertEquals(403, jurisdictionException.getStatusCode().value());
                assertTrue(jurisdictionException.getReason().contains("Application is outside the officer's jurisdiction"));
        }

        private User user(
                        Long id,
                        Role role,
                        String level,
                        String state,
                        String district) throws Exception {

                User user = new User();
                setId(user, id);

                user.setRole(role);
                user.setOfficerLevel(level);
                user.setJurisdictionState(state);
                user.setJurisdictionDistrict(district);
                user.setFullName("Test Officer " + id);
                user.setEmail("test" + id + "@test.local");
                user.setActive(true);

                return user;
        }

        private void setId(Object entity, Long id) throws Exception {
                Field field = entity.getClass().getDeclaredField("id");
                field.setAccessible(true);
                field.set(entity, id);
        }
}
