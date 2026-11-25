# GRANTORA – COMPLETE PROJECT AUDIT, WORKFLOW ANALYSIS & PROJECT BRIEF COMPLIANCE REPORT

**Project:** Grantora – Government Subsidy/Grant Disbursement Tracking System  
**Project Root:** `C:\Users\Ramya\OneDrive\Desktop\grant-tracking-main\grant-tracking-main`  
**Auditor Role:** Senior Software Architect / Java Spring Boot Developer / Full-Stack Auditor / QA Engineer / Government Grant Management Domain Expert  
**Audit Date:** 25-11-2026  
**Status:** Audit complete — report generated. No source files were modified during this audit.

---

## 1. EXECUTIVE SUMMARY

Grantora is a full-stack Government Subsidy/Grant Disbursement Tracking System consisting of a **Java Spring Boot backend** and a **React (Vite) frontend**, backed by **MySQL**. It implements beneficiary registration/profile management, scheme management with configurable eligibility criteria, automated eligibility scoring, a three-level verification workflow (field → district → finance), staged disbursement planning with compliance milestones and document tracking, compliance submission/review/waiver, stage-wise fund release with payment-reference recording, fund utilization and regional analytics, role-based access control (ADMIN / BENEFICIARY / OFFICER), JWT authentication, audit logging, and in-app notifications.

**Overall assessment:** A functioning, compile-clean codebase with a genuine workflow engine and real database-backed business logic. However, it is **not yet complete** relative to the original project brief. Core requirements partially implemented or missing include external treasury integration, external beneficiary-database integration, robust duplicate-beneficiary deduplication (beyond email), budget-exhaustion queuing/escalation, risk-based differential verification routing, and production-grade security hardening (hardcoded JWT secrets, missing role enforcement on several endpoints, no rate limiting, no password-reset/email-verification).

**Estimated overall completion:** ~55–60% of the project brief (verified against requirements, not file count).  
**Ready for final submission?** No — important workflow, financial-correctness, security, and integration work remains (see Section 34).

---

## 2. PROJECT INTRODUCTION AND OBJECTIVES

The project aims to modernize government subsidy/grant disbursement by replacing manual, fragmented processes with a centralized platform that:

- Automates beneficiary eligibility scoring against configurable scheme criteria.
- Routes applications through multi-level field, district, and finance verification.
- Releases funds in stages tied to compliance milestones.
- Provides scheme-wise and region-wise fund-utilization analytics.
- Enforces role-based access and a complete, attributable audit trail.
- Exposes REST APIs for treasury and beneficiary-database integration.

Intended users: field officers, district officers, finance approvers, administrators, beneficiaries.

---

## 3. ORIGINAL PROJECT BRIEF REQUIREMENTS (EVALUATION STANDARD)

The brief fixes the technology foundation (Java, Spring Boot, REST APIs, enterprise-grade database) and leaves the schema, scoring logic, workflow rules, API contracts, and UI/UX to the team. Mandatory capability areas:

1. Centralized multi-scheme and multi-region grant management
2. Beneficiary profile management
3. Scheme management
4. Configurable eligibility criteria
5. Automated eligibility scoring
6. Multi-level verification (field, district, finance)
7. Automated workflow routing based on eligibility and grant amount
8. Staged disbursement
9. Configurable disbursement milestones
10. Compliance document tracking
11. Compliance reminders and overdue handling
---

## 4. TECHNOLOGY STACK

| Layer | Technology | Version / Evidence |
|---|---|---|
| Backend language | Java | 17 (`pom.xml` `<java.version>17</java.version>`); runtime is JDK 21 |
| Backend framework | Spring Boot | `4.1.2` declared in `pom.xml` parent (note: Spring Boot 4.x is not a generally available release as of this audit; verify intended version) |
| Web layer | Spring Web MVC | `spring-boot-starter-webmvc` |
| Security | Spring Security + JWT | `spring-boot-starter-security`, `jjwt-api/impl/jackson 0.12.6`, `spring-security-crypto` |
| Persistence | Spring Data JPA + Hibernate | `spring-boot-starter-data-jpa`, MySQL dialect |
| Database | MySQL | `mysql-connector-j`, URL `jdbc:mysql://localhost:3306/grantora_new` |
| Validation | Bean Validation | `spring-boot-starter-validation` |
| JSON | Jackson | `jackson-databind`, `jackson-datatype-jsr310` |
| Template engine | Thymeleaf | `spring-boot-starter-thymeleaf` (not used for API responses) |
| Build tool | Apache Maven | `mvnw`, `pom.xml`; compiles cleanly (verified `mvn compile -o`) |
| Frontend | React | `^18.3.1` |
| Frontend build tool | Vite | `^6.0.5` |
| Frontend routing | React Router DOM | `^6.28.1` |
| Frontend HTTP client | Axios | `^1.21.0` (`baseURL: http://localhost:8080/api`) |
| Frontend icons | Lucide React | `^0.468.0` |

**Configuration file:** `backend/src/main/resources/application.properties`  
- DDL auto: `update` (auto-creates/alters tables — acceptable for dev, risky for production)
- JWT secret default: `dGVzdC1zZWNyZXQta2V5LWZvci1ncmFudG9yYS1kZXYtZW52aXJvbm1lbnQtMjU2Yml0cw==` (hardcoded fallback — security finding)
- JWT expiration: `86400000` ms (24 hours)
- Multipart max file size: 10 MB per file, 15 MB per request
- Bootstrap admin: `admin@grantora.com` / `admin@123456`

---

## 5. SYSTEM ARCHITECTURE

Grantora is a **decoupled client–server REST application**.

### 5.1 Backend (`backend/src/main/java/com/grantora`)

Package `com.grantora` with the following layers:

| Package | Contents | Responsibility |
|---|---|---|
| (root) | `GrantoraApplication.java` | Spring Boot entry point; `@EnableScheduling` enables `@Scheduled` tasks |
| `bootstrap` | `AdminBootstrapRunner.java` | Seeds a default ADMIN account on startup if none exists |
| `controller` | 17 REST controllers | HTTP request handling; delegates to services |
| `dto` | ~40 records/request DTOs | Data transfer objects (requests, responses, projections) |
| `entity` | ~35 JPA entities/enums | Domain model mapped to MySQL tables |
| `exception` | `GlobalExceptionHandler.java`, `ApiErrorResponse.java` | Centralized `@ControllerAdvice` exception handling |
| `repository` | ~20 Spring Data JPA repositories | Data access; custom queries (`findByIdForUpdate` uses pessimistic locking) |
| `security` | `SecurityConfig.java`, `JwtAuthenticationFilter.java` | Filter-chain security, CORS, JWT validation, role-based URL authorization |
| `service` | ~15 services | Business logic: auth, beneficiary, scheme, application, eligibility scoring, verification workflow, disbursement, compliance, utilization, analytics, audit, notifications, workflow automation |

### 5.2 Frontend (`frontend/src`)

| Folder | Contents |
|---|---|
| `pages/` | ~30 page components (Login, Register, dashboards, scheme management, application management, verification workflow, disbursement management, compliance, analytics, audit logs, etc.) |
| `components/` | `DashboardLayout.jsx`, `ProtectedRoute.jsx`, `Navbar.jsx`, `PublicSiteLayout.jsx`, `SaaSPrimitives.jsx`, `LogoutButton.jsx`, `Milestone3ReportExports.jsx`, `dashboardNavigation.js` |
| `services/` | Axios service modules (`api.js`, `authService.js`, `beneficiaryService.js`, `schemeService.js`, `grantApplicationService.js`, `disbursementService.js`, `complianceService.js`, `fundAnalyticsService.js`, `userService.js`, `notificationService.js`, `auditService.js`, `milestone3Service.js`) |
| `utils/` | `applicationDisbursement.js`, `fintechCharts.js` (+ test files) |
| `styles/` | Component-level CSS files |

### 5.3 Communication flow

## 6. MODULE-WISE IMPLEMENTATION

Below is a concise description of each important module and how frontend, backend, and database interact.

### 6.1 Authentication & Authorization (`AuthController`, `AuthService`, `JwtService`, `JwtAuthenticationFilter`, `SecurityConfig`, `UserController`, `UserService`)

- **Registration:** `POST /api/auth/register` → `AuthService.register()`. Only `BENEFICIARY` may self-register; email uniqueness enforced; password hashed with `BCryptPasswordEncoder`; a `User` with `Role.BENEFICIARY` is saved. A separate `Beneficiary` profile is created later via `POST /api/beneficiaries` (registration and profiling are separate processes).
- **Login:** `POST /api/auth/login` → `AuthService.login()` verifies credentials, checks `active`, returns a JWT (subject = email, claims = userId + role, 24h expiry) plus user details.
- **JWT validation:** `JwtAuthenticationFilter` extracts `Bearer` token, looks up user, sets `SecurityContext` if valid and active. Skips `/api/auth/login` and `/api/auth/register`.
- **Authorization:** `SecurityConfig` defines role-based `requestMatchers` (e.g. `/api/users/**` → ADMIN, `/api/audit/**` → ADMIN, `/api/applications/me` → BENEFICIARY). CSRF disabled; stateless sessions; CORS restricted to `http://localhost:5173`.
- **User management:** `UserController` (`/api/users`) — ADMIN-only; `UserService` creates/updates/deletes users and records audit events (`USER_CREATED`, `USER_UPDATED`, `USER_DELETED`). Officer levels FIELD/DISTRICT/FINANCE with jurisdiction state/district are validated.

### 6.2 Beneficiary Management (`BeneficiaryController`, `BeneficiaryService`, `Beneficiary` entity, `User` entity)

- One-to-one `User` ↔ `Beneficiary`. Beneficiary profile holds dateOfBirth, annualIncome, address, state, district, category.
- `GET /api/beneficiaries/me` (own profile), `POST /api/beneficiaries` (create own profile), `PUT /api/beneficiaries/me` (update), `GET /api/beneficiaries/{id}` and `GET /api/beneficiaries` (ADMIN), `DELETE /api/beneficiaries/{id}` (ADMIN).
- Creating a profile when one already exists **updates** it rather than rejecting a duplicate (one profile per user enforced by the one-to-one mapping).

### 6.3 Scheme Management (`SchemeController`, `SchemeService`, `Scheme`, `SchemeEligibilityQuestion`)

- Administrators create schemes with name, department, age/income limits, maximum grant amount, allocated funds, eligible categories, application dates, eligibility threshold (default 70), and eligibility questions.
- Each question has text, type (`YES_NO` / `SINGLE_CHOICE`), maximum points, and answer options (each option carries points and a `meetsEligibilityCondition` flag).

### 6.5 Eligibility Scoring (`EligibilityScoringService`, `EligibilityEvaluationResult`)

Real scoring logic (not hardcoded):
- **Mandatory criteria:** age (within scheme min/max), annual income (≤ scheme maximum), beneficiary category (in scheme eligible categories). Each yields PASS/FAIL/MISSING.
- **Configurable questions:** points earned vs configured maximum (total must be 100). Score = `(pointsEarned / totalMaxPoints) * 100`.
- **Status:** `INELIGIBLE` if any mandatory criterion fails; `NEEDS_MORE_INFORMATION` if required answers missing or threshold unconfigured; `ELIGIBLE` if score ≥ scheme threshold; otherwise `INELIGIBLE`.

### 6.6 Verification Workflow (`VerificationWorkflowService`, `VerificationRoutingRuleService`, `VerificationRoutingRule`, `ApplicationVerification`)

Automated multi-level routing:
- On submission, the application is assigned to a **Field Officer** (`ROLE.OFFICER`, `officerLevel=FIELD`, matching beneficiary state+district) selected round-robin by application ID. A `VerificationRoutingRule` matched by eligibility score + requested-amount band sets the routing priority.
- Field officer decision (`VERIFIED`/`REJECTED`/`NEEDS_CORRECTION`) → `VERIFIED` advances to **District Officer** (`APPROVED`/`REJECTED`/`NEEDS_CORRECTION`) → advances to **Finance Officer** (`APPROVED`/`REJECTED`). Finance `APPROVED` requires prior field + district history and sets application status `APPROVED`.
- Officers can escalate/reassign within a stage; admin can assign officers. Admin can make final decisions only at the finance stage. Officers cannot act on their own applications or outside their jurisdiction.
- Every decision is recorded in `ApplicationVerification` (previous/new status, next stage, actor, remarks, checklist flags). Status history is tracked in `GrantApplicationStatusHistory`.

### 6.7 Disbursement Management (`DisbursementController`, `DisbursementService`, `DisbursementPlan`, `DisbursementInstallment`, `DisbursementMilestone`)

- Admin creates a disbursement plan for an `APPROVED` application (`POST /api/disbursements/plans`); duplicate plans blocked (unique constraint on `application_id`).
- Plan holds `totalApprovedAmount` (= application requested amount), installments (configurable percentages/amounts that must sum to the approved amount), milestones per installment, and compliance requirements.
- **Budget check:** plan creation verifies `approvedAmount ≤ allocatedFunds − total committed (non-cancelled plans)`; rejects if budget insufficient.
- Installment release (`POST /api/disbursements/installments/{id}/disburse`, ADMIN) requires: prior installments DISBURSED (sequential), over-disbursement cap, all milestones COMPLETED at 100% progress, all required compliances VERIFIED/APPROVED/WAIVED, no non-compliant milestone, and a **payment reference / UTR** (duplicate references blocked).
- On release: installment status → `DISBURSED`, `actualDisbursementDate`, `paymentReference`, `paymentMethod`, `disbursedBy`; plan `totalDisbursedAmount` updated; `DisbursementPlanAudit` entry recorded; next installment set to `READY_FOR_DISBURSEMENT`; if all done, plan → `COMPLETED`. Beneficiary notified.
- **This is a simulated/administrative payment record** — a payment reference is stored, but no actual treasury or bank API is invoked.


### 6.9 Fund Utilization & Analytics (`DisbursementService`, `FundAnalyticsService`, `GrantUtilizationService`, `GrantUtilizationRecord`)

- **Fund utilization summary** (`GET /api/disbursements/fund-utilization`): total allocated, approved, scheduled, disbursed, remaining; scheme-wise breakdown; installment status breakdown. Calculated from real DB records.
- **Regional analytics** (`GET /api/disbursements/regional-analytics`): grouped by beneficiary state/district — total apps, approved/rejected counts, approved/disbursed amounts, pending, compliance rate, fund utilization rate. Real DB records.
- **Fund analytics** (`GET /api/fund-analytics`, ADMIN): filtered summary (scheme, state, district, dates, disbursement status) with scheme-wise and regional rows. Real DB records.
- **Grant utilization reporting:** beneficiary submits utilization report per installment (`POST /api/utilization`), linked to supporting compliance documents; amount cannot exceed released installment; admin/officer reviews (`PATCH /api/utilization/{id}/review`) → APPROVED/REJECTED/CORRECTION_REQUIRED.
- All analytics are computed from live database records — **no static/demo data** was observed.

### 6.10 Audit Logging (`AuditController`, `AuditService`, `SystemAuditLog`)

- `AuditService.recordAudit()` writes `SystemAuditLog` (actor email/role, action, category, target entity/id, details, timestamp).
- Audit events are recorded for scheme create/update, user create/update/delete, disbursement plan creation/installment release (`DisbursementPlanAudit`), application status changes (`GrantApplicationStatusHistory`), verification decisions (`ApplicationVerification`), and compliance reviews (`ComplianceReviewHistory`).
- `GET /api/audit/logs` (ADMIN) returns a unified audit log combining system logs, status history, verification history, plan audit, and compliance review history.
- The audit trail captures who, what, which record, and when — sufficient as a starting point for complaint investigation, though previous-vs-updated field values are not consistently captured.

### 6.11 Notifications (`WorkflowAutomationService`, `InAppNotification`, `WorkflowAutomationController`)

- In-app notifications stored in `InAppNotification` (user, eventKey, message, applicationId, createdAt, readAt). Deduplicated by `event_key + user_id`.
- Endpoints: `GET /api/workflow/notifications` (unread count + items), `POST /api/workflow/notifications/{id}/read`, `POST /api/workflow/notifications/read-all`.
- Notifications are dispatched on registration outcomes, submission, verification decisions, compliance review, disbursement release, reminders, and overdue escalations.

## 7. DATABASE DESIGN AND RELATIONSHIPS

Key entities and their relationships (tables are auto-generated by Hibernate `ddl-auto=update`):

- **users** — id, fullName, email (unique), password (BCrypt), phone, officerLevel (FIELD/DISTRICT/FINANCE), jurisdictionState, jurisdictionDistrict, role (ADMIN/BENEFICIARY/OFFICER), active, createdAt.
- **beneficiaries** — id, user_id (one-to-one, unique), dateOfBirth, annualIncome, address, state, district, category.
- **schemes** — id, schemeName, description, department, min/max age, maxAnnualIncome, maxGrantAmount, allocatedFunds, eligibilityThreshold (default 70), eligibleCategories (set), status (DRAFT/ACTIVE...), dates.
- **scheme_eligibility_questions** — per scheme: displayOrder, questionText, questionType, maximumPoints, answerOptions (JSON-converted list of value/label/points/meetsEligibilityCondition).
- **grant_applications** — beneficiary, scheme, applicationDetails, requestedAmount, status (DRAFT/SUBMITTED/UNDER_VERIFICATION/FIELD_VERIFICATION/DISTRICT_REVIEW/FINANCE_REVIEW/NEEDS_CORRECTION/UNDER_REVIEW/APPROVED/REJECTED/WITHDRAWN), submittedAt, eligibilityAnswers (JSON), eligibilityEvaluation (JSON), workflowStage (FIELD/DISTRICT/FINANCE), assignedOfficer, routingPriority, escalated, task timestamps. **Unique constraint: (beneficiary_id, scheme_id).**
- **application_documents** — application, uploadedBy, fileName, contentType, fileSize, content (LONGBLOB), uploadedAt.
- **application_verification** — application, stage, previousStatus, newStatus, nextStage, action, actor, assignedOfficer, remarks, identityVerified, addressVerified, documentsVerified, applicationDetailsVerified, createdAt.
- **grant_application_status_history** — application, fromStatus, toStatus, action, actor, remarks, createdAt.
- **disbursement_plans** — application (one-to-one, unique), scheme, totalApprovedAmount, totalDisbursedAmount, planStatus (DRAFT/ACTIVE/COMPLETED/CANCELLED), notes, createdBy, timestamps. Has many installments + plan audit history.
- **disbursement_installments** — plan, installmentNumber, title, percentage, amount, plannedReleaseDate, actualDisbursementDate, status (PLANNED/READY_FOR_DISBURSEMENT/DISBURSED/ON_HOLD/CANCELLED), paymentReference, paymentMethod, disbursedBy, remarks. Has many milestones.
- **disbursement_milestones** — installment, title, description, dueDate, amount, status (PLANNED/PENDING/COMPLETED/OVERDUE/CANCELLED), progressPercentage, nonCompliant, nonComplianceReason, completedAt. Has many compliances.
- **compliance_requirements** — scheme, milestone (optional), application (optional), targetStage, title, description, required, submissionDeadline, allowedFormats, maxFileSizeBytes, active, createdBy, timestamps.
- **milestone_compliances** — milestone, requirement, status (PENDING/SUBMITTED/UNDER_REVIEW/VERIFIED/APPROVED/REJECTED/CORRECTION_REQUIRED/OVERDUE/WAIVED), required, submissionDeadline, requirementTitle, description, submissionNotes, documentFileName/ContentType/FileSize, documentData (LONGBLOB), submittedBy/At, verifiedBy/At, verificationRemarks. Has review history.
- **compliance_review_history** — compliance, actor, action, remarks, documentFileName, occurredAt.
- **compliance_non_compliance** / **non_compliance_history** — non-compliance cases with status/resolution.
- **compliance_reminder_history** — compliance, eventType, eventDate (deduped reminders).
- **grant_utilization_records** — application, installment, submittedBy, reportedAmount, category, description, supportingDocuments (many-to-many to milestone_compliances), verification_status (SUBMITTED/UNDER_REVIEW/VERIFIED/REJECTED/CORRECTION_REQUIRED), reviewedBy/At, remarks, history.
- **utilization_review_history** — utilization, actor, status, remarks, timestamp.
- **disbursement_plan_audit** — plan, actor, action, details, occurredAt.
- **system_audit_log** — actorEmail, actorRole, action, category, targetEntity, targetId, details, timestamp.
- **in_app_notifications** — user, eventKey (unique per user), message, applicationId, createdAt, readAt.
- **verification_routing_rule** — min/max score, min/max amount, priority (used to route applications).
- **workflow_deadline_config** / **workflow_escalation** — per-stage deadlines and overdue escalations.

## 8. ROLE-BASED ACCESS CONTROL (RBAC)

Roles: **ADMIN**, **OFFICER** (officerLevel = FIELD/DISTRICT/FINANCE), **BENEFICIARY**.

| Function | Beneficiary | Field Officer (OFFICER/FIELD) | District Officer (OFFICER/DISTRICT) | Finance Officer (OFFICER/FINANCE) | Administrator |
|---|---|---|---|---|---|
| View own profile | Yes | No | No | No | Yes (any) |
| Create/update own profile | Yes | No | No | No | Yes (any via /api/users) |
| View public schemes | Yes | Yes | Yes | Yes | Yes |
| Create/update/delete scheme | No | No | No | No | Yes |
| Create application (draft) | Yes | No | No | No | No |
| Upload application documents | Yes (own) | No | No | No | No |
| Submit application | Yes (own) | No | No | No | No |
| View own applications | Yes | No | No | No | No |
| View assigned verification queue | No | Yes (own stage+jurisdiction) | Yes (own stage+jurisdiction) | Yes (own stage+jurisdiction) | Yes (all) |
| Verify/approve/reject application | No | Yes (FIELD stage, own assignment) | Yes (DISTRICT stage) | Yes (FINANCE stage) | Yes (FINANCE stage only) |
| Escalate/reassign | No | Yes (own) | Yes (own) | Yes (own) | Yes |
| Create disbursement plan | No | No | No | No | Yes |
| Submit compliance evidence | Yes (own) | No | No | No | Yes |
| Review compliance | No | Yes (assigned) | Yes (assigned) | Yes (assigned) | Yes |
| Release installment (disburse) | No | No | No | No | Yes |
| View fund utilization analytics | Yes (mine) | Yes | Yes | Yes | Yes (all) |
| View audit logs | No | No | No | No | Yes |
| Manage users | No | No | No | No | Yes |
| Manage routing rules / deadlines | No | No | No | No | Yes (intended; enforcement gaps exist) |

### Authorization findings
- **Backend enforcement exists** in `SecurityConfig` for most sensitive endpoints and in service-layer role checks.
- **Gaps (no controller-level or SecurityConfig role restriction):**
  - `PUT /api/workflow/deadlines` — no role check anywhere; any authenticated user can change verification deadlines.
  - `GET/POST/PUT /api/verification/routing-rules/*` — no SecurityConfig rule and no visible service role check; any authenticated user could read/modify routing rules.
  - `/api/compliance/*` — no explicit SecurityConfig rule (falls to `.anyRequest().authenticated()`); service-layer checks exist but are inconsistent across endpoints.
  - `GET /api/fund-analytics` — service checks ADMIN, but no SecurityConfig rule.

## 9. REST API INVENTORY

Base URL: `http://localhost:8080/api` (frontend `api.js`). All endpoints require a `Bearer` JWT unless marked public.

| # | Method | Endpoint | Controller | Purpose | Auth / Role |
|---|---|---|---|---|---|
| 1 | POST | /auth/register | AuthController | Beneficiary self-registration | Public |
| 2 | POST | /auth/login | AuthController | Login, returns JWT | Public |
| 3 | GET | / | HealthController | Health check | Public |
| 4 | GET | /schemes | SchemeController | List schemes (public=ACTIVE only; admin=all) | Public / ADMIN |
| 5 | GET | /schemes/{id} | SchemeController | Get scheme | Public / ADMIN |
| 6 | POST | /schemes | SchemeController | Create scheme | ADMIN |
| 7 | PUT | /schemes/{id} | SchemeController | Update scheme | ADMIN |
| 8 | DELETE | /schemes/{id} | SchemeController | Delete scheme (blocked if deps) | ADMIN |
| 9 | POST | /schemes/{id}/check-eligibility, /evaluate-eligibility | SchemeController | Evaluate eligibility | BENEFICIARY/ADMIN/OFFICER |
| 10 | GET | /beneficiaries | BeneficiaryController | List all beneficiaries | ADMIN |
| 11 | GET | /beneficiaries/me | BeneficiaryController | Own profile | BENEFICIARY |
| 12 | POST | /beneficiaries | BeneficiaryController | Create own profile | BENEFICIARY |
| 13 | PUT | /beneficiaries/me | BeneficiaryController | Update own profile | BENEFICIARY |
| 14 | GET | /beneficiaries/{id} | BeneficiaryController | Beneficiary by ID | ADMIN (service) |
| 15 | DELETE | /beneficiaries/{id} | BeneficiaryController | Delete beneficiary | ADMIN |
| 16 | GET | /applications | GrantApplicationController | List applications (role-filtered) | ADMIN/OFFICER |
| 17 | GET | /applications/admin | GrantApplicationController | Paginated admin applications | ADMIN |
| 18 | GET | /applications/me | GrantApplicationController | Own applications | BENEFICIARY |
| 19 | GET | /applications/{id} | GrantApplicationController | Get application | ADMIN/OFFICER/BENEFICIARY |
| 20 | POST | /applications | GrantApplicationController | Create application draft | BENEFICIARY |
| 21 | PUT | /applications/{id} | GrantApplicationController | Update draft | BENEFICIARY |
| 22 | POST | /applications/{id}/submit | GrantApplicationController | Submit draft | BENEFICIARY |
| 23 | POST | /applications/{id}/withdraw | GrantApplicationController | Withdraw | BENEFICIARY |
| 24 | DELETE | /applications/{id} | GrantApplicationController | Delete draft | BENEFICIARY |
| 25 | PATCH | /applications/{id}/status | GrantApplicationController | Direct status change (DISABLED — throws 409) | ADMIN |
| 26 | POST | /applications/{id}/decision | GrantApplicationController | Verification decision | ADMIN/OFFICER |
| 27 | POST | /applications/{id}/escalate | GrantApplicationController | Escalate | ADMIN/OFFICER |
| 28 | POST | /applications/{id}/assignment | GrantApplicationController | Assign officer | ADMIN |
| 29 | POST | /applications/{id}/documents | GrantApplicationController | Upload document | BENEFICIARY |
| 30 | GET | /applications/{id}/documents/{docId} | GrantApplicationController | Download document | ADMIN/OFFICER/BENEFICIARY |
| 31 | GET | /applications/disbursement/eligible | GrantApplicationController | APPROVED apps without plan | ADMIN |

| 34 | POST | /disbursements/plans | DisbursementController | Create disbursement plan | ADMIN |
| 35 | GET | /disbursements/plans/mine | DisbursementController | Beneficiary plans | BENEFICIARY |
| 36 | GET | /disbursements/plans | DisbursementController | All plans (paged/list) | ADMIN |
| 37 | GET | /disbursements/plans/{id} | DisbursementController | Plan by ID | ADMIN (service) |
| 38 | GET | /disbursements/plans/by-application/{id} | DisbursementController | Plan by application | ADMIN (service) |
| 39 | PATCH | /disbursements/plans/{id}/deactivate | DisbursementController | Deactivate plan | ADMIN |
| 40 | GET | /disbursements/eligible-applications | DisbursementController | Apps eligible for disbursement | ADMIN |
| 41 | GET | /disbursements/milestones | DisbursementController | All milestones | ADMIN |
| 42 | PATCH | /disbursements/milestones/{id}/progress | DisbursementController | Update milestone progress | ADMIN/OFFICER |
| 43 | GET | /disbursements/milestones/{id}/compliances | DisbursementController | Compliances by milestone | ADMIN |
| 44 | POST | /disbursements/milestones/{id}/compliance | DisbursementController | Submit compliance (SecurityConfig: ADMIN) | ADMIN* |
| 45 | GET | /disbursements/compliance/{id}/document | DisbursementController | Download compliance doc | ADMIN/OFFICER/BENEFICIARY |
| 46 | POST | /disbursements/compliance/{id}/verify | DisbursementController | Verify compliance | ADMIN/OFFICER |
| 47 | POST | /disbursements/installments/{id}/disburse | DisbursementController | Release installment | ADMIN |
| 48 | GET | /disbursements/fund-utilization | DisbursementController | Fund utilization summary | ADMIN/OFFICER |
| 49 | GET | /disbursements/regional-analytics | DisbursementController | Regional analytics | ADMIN/OFFICER |
| 50 | GET/PUT | /disbursements/reminders/config | DisbursementController | Reminder config | ADMIN |
| 51 | GET | /compliance/requirements | ComplianceController | List requirements | ADMIN (service) |
| 52 | POST | /compliance/requirements | ComplianceController | Create requirement | ADMIN (service) |
| 53 | PATCH | /compliance/requirements/{id}/deactivate | ComplianceController | Deactivate requirement | ADMIN (service) |
| 54 | GET | /compliance/mine | ComplianceController | Beneficiary compliances | BENEFICIARY (service) |
| 55 | POST | /compliance/submissions/{id} | ComplianceController | Submit compliance evidence | BENEFICIARY/ADMIN (service) |
| 56 | PATCH | /compliance/submissions/{id}/review | ComplianceController | Review compliance | ADMIN/OFFICER (service) |
| 57 | PATCH | /compliance/submissions/{id}/waive | ComplianceController | Waive compliance | ADMIN (service) |
| 58 | GET | /compliance/submissions/{id}/document | ComplianceController | Download compliance doc | ADMIN/OFFICER/BENEFICIARY (service) |
| 59 | GET | /compliance/non-compliances | ComplianceController | Non-compliance cases | ADMIN/OFFICER (service) |
| 60 | PATCH | /compliance/non-compliances/{id} | ComplianceController | Resolve non-compliance | ADMIN/OFFICER (service) |
| 61 | GET | /utilization/mine | GrantUtilizationController | Beneficiary utilization | BENEFICIARY |
| 62 | POST | /utilization | GrantUtilizationController | Submit utilization report | BENEFICIARY |
| 63 | PUT | /utilization/{id} | GrantUtilizationController | Replace utilization report | BENEFICIARY |
| 64 | GET | /utilization/review-queue | GrantUtilizationController | Utilization review queue | ADMIN/OFFICER |
| 65 | PATCH | /utilization/{id}/review | GrantUtilizationController | Review utilization | ADMIN/OFFICER |
| 66 | GET | /fund-analytics | FundAnalyticsController | Fund analytics summary | ADMIN (service) |
| 67 | GET | /audit/logs | AuditController | Unified audit logs | ADMIN |
| 68 | GET | /users | UserController | List users | ADMIN |
| 69 | GET/POST/PUT/DELETE | /users/{id} | UserController | User CRUD | ADMIN |
| 70 | GET | /verification/routing-rules | VerificationRoutingRuleController | Routing rules | authenticated* |
| 71 | POST/PUT/DELETE | /verification/routing-rules/{id} | VerificationRoutingRuleController | Routing rules CRUD | authenticated* |
| 72 | GET/PUT | /workflow/deadlines | WorkflowAutomationController | Workflow deadlines | authenticated* (NO role check) |
| 73 | GET | /workflow/escalations | WorkflowAutomationController | Escalations | ADMIN/OFFICER |
| 74 | POST | /workflow/escalations/{id}/resolve | WorkflowAutomationController | Resolve escalation | ADMIN |
| 75 | GET | /workflow/notifications | WorkflowAutomationController | Notifications + unread count | authenticated |
| 76 | POST | /workflow/notifications/{id}/read | WorkflowAutomationController | Mark read | authenticated |
| 77 | POST | /workflow/notifications/read-all | WorkflowAutomationController | Mark all read | authenticated |
| 78 | GET | /milestone-3/admin-dashboard | Milestone3DashboardController | Admin disbursement dashboard | authenticated |
| 79 | GET | /milestone-3/assigned-milestones | Milestone3DashboardController | Assigned milestones | authenticated |

**\* marked `authenticated*`** = no explicit role enforcement found in `SecurityConfig` or controller — a potential authorization gap (see Section 10).

**External integration status:**
- **Treasury integration:** NOT implemented. Installment release records a `paymentReference`/`paymentMethod` and updates status to `DISBURSED`, but no external treasury/bank API is called. It is an administrative simulation.
- **Beneficiary-database integration:** NOT implemented. Beneficiaries are managed entirely within Grantora's own `users`/`beneficiaries` tables; no external beneficiary-database sync or lookup API exists.
- REST controllers themselves are present (satisfying "REST API support"), but they are internal APIs, not external-system adapters.

---
| 32 | GET | /applications/verification/assigned | GrantApplicationController | Verification queue | ADMIN/OFFICER |
| 33 | GET | /applications/verification/history | GrantApplicationController | Verification history | ADMIN/OFFICER |

---
  - `GET /api/beneficiaries/{id}` and `GET /api/beneficiaries` (list) — only `POST /api/beneficiaries` and `GET /api/beneficiaries/me` are explicitly role-restricted; the list/by-ID endpoints appear to rely on `.anyRequest().authenticated()` plus service logic (which is ADMIN for list). Needs verification.
- **Frontend route protection** (`ProtectedRoute` with `allowedRoles`) mirrors roles but hiding a route/button is not security; backend must enforce (mostly does).
- **No privilege escalation via JWT** observed (token contains role; filter re-derives authorities from DB user role). Token theft would grant access until expiry (24h); no refresh-token rotation or revocation list.

---
- **scheme_document_requirement**, **scheme_eligibility_rule** — referenced in scheme deletion checks (exist via EntityManager queries).

**Data-integrity observations:** Foreign keys exist via JPA `@JoinColumn` but no explicit DB-level `ON DELETE` constraints beyond JPA cascade/orphan-removal (application-level enforcement). Documents stored as `LONGBLOB` in MySQL — functional but can bloat the database; no virus/malware scanning, no content-type enforcement beyond the header string. Monetary fields use `BigDecimal` with precision/scale — good practice. No `@DecimalMin`/`@DecimalMax` constraints observed on entities (validation is mostly service-level). No database-level check constraints for business rules (e.g., installment percentages summing to 100) — enforced in Java services.

---
- No email/SMS integration — notifications are in-app only.

### 6.12 Workflow Automation & Scheduling (`WorkflowAutomationService`, `WorkflowAutomationController`, `WorkflowDeadlineConfig`, `WorkflowEscalation`)

- Configurable per-stage task deadlines (`WorkflowDeadlineConfig`).
- A scheduled scan (invoked via `ComplianceOperationsService.scheduledComplianceScan` / `WorkflowAutomationService`) detects overdue verification tasks, creates `WorkflowEscalation` records, and notifies all ADMINs. Escalations can be resolved by ADMIN.
- Endpoints: `GET/PUT /api/workflow/deadlines`, `GET /api/workflow/escalations`, `POST /api/workflow/escalations/{id}/resolve`.

---
### 6.8 Compliance Management (`ComplianceController`, `ComplianceOperationsService`, `DisbursementService.submitCompliance`, `ComplianceRequirement`, `MilestoneCompliance`, `ComplianceReviewHistory`)

- Admins configure compliance requirements per scheme/milestone/target-stage (`POST /api/compliance/requirements`), with title, deadline, allowed formats, max file size, required flag. Duplicate active requirements blocked; deactivation supported.
- Beneficiaries submit compliance evidence (`POST /api/compliance/submissions/{id}`, multipart) linked to a milestone; status → `SUBMITTED`; review history recorded.
- Officers/Admins review (`PATCH /api/compliance/submissions/{id}/review`): `APPROVED` (requires a document), `REJECTED`, or `CORRECTION_REQUIRED` (remarks required for rejection/correction). 3+ rejections/corrections auto-creates a `ComplianceNonCompliance` case (HIGH severity). Beneficiary notified.
- Waiver: ADMIN-only (`PATCH /api/compliance/submissions/{id}/waive`) → status `WAIVED`.
- **Overdue handling:** `ComplianceOperationsService.scanDeadlines()` is `@Scheduled(fixedDelayString="${grantora.compliance.scan-ms:3600000}")` (≈1 hour). It flags `OVERDUE` requirements past their deadline, creates non-compliance cases, and sends reminders `UPCOMING`/`DEADLINE_DAY`/`OVERDUE` to beneficiary and assigned officer, recorded in `ComplianceReminderHistory` (deduplicated per compliance/event/date).

---
- **Validation:** question maximum weights must total exactly 100 points; threshold must be 0–100; allocated funds and max grant amount must be > 0; dates must be ordered. Scheme deletion is blocked if applications, disbursement plans, compliance requirements, document requirements, or eligibility rules reference it.
- Public endpoints `GET /api/schemes` and `GET /api/schemes/{id}` return only `ACTIVE` schemes to non-admins.

### 6.4 Grant Application (`GrantApplicationController`, `GrantApplicationService`, `GrantApplication`, `ApplicationDocument`, `ApplicationVerification`, `GrantApplicationStatusHistory`)

- Beneficiary creates a draft (`POST /api/applications`), optionally updates it, uploads supporting documents (`POST /api/applications/{id}/documents`, multipart, ≤10 MB, max 20), then submits (`POST /api/applications/{id}/submit`).
- Submission validates details, amount ≤ scheme maximum, scheme still active, ≥1 document, and all required eligibility questions answered.
- Duplicate applications are blocked (unique constraint on `beneficiary_id + scheme_id`; plus explicit check `existsByBeneficiary_IdAndScheme_Id`).
- Eligibility scoring runs at draft creation and at submission; if not `ELIGIBLE` (and not `NEEDS_MORE_INFORMATION`), submission is rejected with `422`.

---

```
Browser (React, localhost:5173)
   HTTPS/HTTP (axios, JWT Bearer token in Authorization header)
   CORS: only http://localhost:5173 allowed
         v
Spring Boot Backend (localhost:8080)
   Controllers (@RestController, /api/*)
   Services (@Transactional business logic)
   JPA Repositories -> MySQL (grantora_new)
```

All state is persisted in MySQL; uploaded documents are stored as `LONGBLOB` byte arrays inside the database (not on a filesystem or object store). JWT is stateless; sessions are `STATELESS`.

---
12. Fund utilization tracking
13. Scheme-wise and region-wise analytics
14. Approval turnaround analysis
15. Role-based access control
16. Comprehensive audit trails
17. REST API support
18. Treasury integration
19. Beneficiary-database integration
20. Duplicate beneficiary prevention
21. Duplicate application prevention
22. Budget exhaustion handling
23. Rejection and escalation workflows
24. Final payment and utilization reporting

A requirement-by-requirement compliance matrix with evidence and status is provided in Section 27.

---