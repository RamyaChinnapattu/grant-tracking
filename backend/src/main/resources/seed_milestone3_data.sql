-- ============================================================================
-- Grantora - Milestone 3 Demonstration Seed Data
-- ============================================================================

-- 1. Approve 2 existing grant applications (Application 1 and Application 5)
UPDATE grant_applications 
SET status = 'APPROVED', workflow_stage = 'FINANCE'
WHERE id IN (1, 5);

-- 2. Clean up prior demo records if re-run for idempotency
DELETE FROM grant_utilization_records WHERE application_id = 5;
DELETE FROM compliance_non_compliance WHERE compliance_id IN (
    SELECT mc.id FROM milestone_compliances mc 
    JOIN disbursement_milestones dm ON mc.milestone_id = dm.id
    JOIN disbursement_installments di ON dm.installment_id = di.id
    JOIN disbursement_plans dp ON di.plan_id = dp.id
    WHERE dp.application_id = 5
);
DELETE FROM milestone_compliances WHERE milestone_id IN (
    SELECT dm.id FROM disbursement_milestones dm 
    JOIN disbursement_installments di ON dm.installment_id = di.id
    JOIN disbursement_plans dp ON di.plan_id = dp.id
    WHERE dp.application_id = 5
);
DELETE FROM compliance_requirements WHERE application_id = 5 OR scheme_id = 3;
DELETE FROM disbursement_milestones WHERE installment_id IN (
    SELECT di.id FROM disbursement_installments di
    JOIN disbursement_plans dp ON di.plan_id = dp.id
    WHERE dp.application_id = 5
);
DELETE FROM disbursement_installments WHERE plan_id IN (
    SELECT id FROM disbursement_plans WHERE application_id = 5
);
DELETE FROM disbursement_plans WHERE application_id = 5;

-- 3. Create active 3-stage disbursement plan for Application 5 (Total ₹1,00,000)
-- 40% initial (₹40,000), 30% stage 2 (₹30,000), 30% stage 3 (₹30,000)
INSERT INTO disbursement_plans (
    application_id, scheme_id, plan_status, total_approved_amount, total_disbursed_amount, 
    created_by_user_id, notes, created_at, updated_at
) VALUES (
    5, 3, 'ACTIVE', 100000.00, 40000.00,
    3, 'Demonstration 3-stage staged disbursement plan for Krishi Unnati Grant', NOW(), NOW()
);

SET @plan_id = LAST_INSERT_ID();

-- 4. Create 3 installments
-- Installment 1: 40% (₹40,000) - DISBURSED
INSERT INTO disbursement_installments (
    plan_id, installment_number, title, percentage, amount, status, 
    planned_release_date, actual_disbursement_date, payment_method, payment_reference, 
    disbursed_by_user_id, disbursement_remarks
) VALUES (
    @plan_id, 1, 'Initial Setup & Procurement Advance', 40.00, 40000.00, 'DISBURSED',
    '2026-10-15', '2026-10-16 10:30:00', 'DIRECT_BENEFIT_TRANSFER', 'PFMS-TXN-2026-883921',
    13, 'First installment released upon initial verification and approval'
);
SET @inst1_id = LAST_INSERT_ID();

-- Installment 2: 30% (₹30,000) - READY_FOR_DISBURSEMENT
INSERT INTO disbursement_installments (
    plan_id, installment_number, title, percentage, amount, status, 
    planned_release_date, actual_disbursement_date, payment_method, payment_reference, 
    disbursed_by_user_id, disbursement_remarks
) VALUES (
    @plan_id, 2, 'Civil Works & Equipment Installation', 30.00, 30000.00, 'READY_FOR_DISBURSEMENT',
    '2026-11-20', NULL, NULL, NULL,
    NULL, 'Scheduled following milestone progress verification'
);
SET @inst2_id = LAST_INSERT_ID();

-- Installment 3: 30% (₹30,000) - PLANNED
INSERT INTO disbursement_installments (
    plan_id, installment_number, title, percentage, amount, status, 
    planned_release_date, actual_disbursement_date, payment_method, payment_reference, 
    disbursed_by_user_id, disbursement_remarks
) VALUES (
    @plan_id, 3, 'Final Operational Testing & Commissioning', 30.00, 30000.00, 'PLANNED',
    '2026-12-20', NULL, NULL, NULL,
    NULL, 'Final retention tranche released after comprehensive audit'
);
SET @inst3_id = LAST_INSERT_ID();

-- 5. Create Milestones
-- Milestone 1 (Completed with verified compliance)
INSERT INTO disbursement_milestones (
    installment_id, title, description, progress_percentage, status, 
    due_date, completed_at, non_compliant, non_compliance_reason, amount
) VALUES (
    @inst1_id, 'Procurement Order & Advance Receipt Verification',
    'Submission of vendor purchase orders and advance payment vouchers for drip irrigation pipelines.',
    100, 'COMPLETED', '2026-10-20', '2026-10-19 14:00:00', 0, NULL, 40000.00
);
SET @ms1_id = LAST_INSERT_ID();

-- Milestone 2 (In-progress with pending compliance upload)
INSERT INTO disbursement_milestones (
    installment_id, title, description, progress_percentage, status, 
    due_date, completed_at, non_compliant, non_compliance_reason, amount
) VALUES (
    @inst2_id, 'On-site Field Installation & Pipeline Laying',
    'Installation of solar pumps, filter systems, and field piping over designated agricultural plot.',
    65, 'PENDING', '2026-11-28', NULL, 0, NULL, 30000.00
);
SET @ms2_id = LAST_INSERT_ID();

-- Milestone 3 (Overdue milestone with non-compliance flag)
INSERT INTO disbursement_milestones (
    installment_id, title, description, progress_percentage, status, 
    due_date, completed_at, non_compliant, non_compliance_reason, amount
) VALUES (
    @inst3_id, 'Pre-commissioning Safety & Pressure Audit',
    'Mandatory pre-commissioning flow pressure inspection certificate by certified agricultural engineer.',
    20, 'OVERDUE', '2026-11-15', NULL, 1,
    'Overdue: Safety inspection report not submitted before the milestone deadline.', 30000.00
);
SET @ms3_id = LAST_INSERT_ID();

-- 6. Compliance Requirements
INSERT INTO compliance_requirements (
    scheme_id, application_id, milestone_id, title, description, 
    required, active, max_file_size_bytes, allowed_formats, submission_deadline, 
    created_by_user_id, created_at, updated_at
) VALUES (
    3, 5, @ms1_id, 'Vendor Purchase Invoice & Equipment Quotations',
    'Official tax invoices and delivery challans from registered irrigation suppliers.',
    1, 1, 10485760, 'PDF,JPG,PNG', '2026-10-20', 3, NOW(), NOW()
);
SET @req1_id = LAST_INSERT_ID();

INSERT INTO compliance_requirements (
    scheme_id, application_id, milestone_id, title, description, 
    required, active, max_file_size_bytes, allowed_formats, submission_deadline, 
    created_by_user_id, created_at, updated_at
) VALUES (
    3, 5, @ms2_id, 'Installation Progress Photos with Geotags',
    'Clear photographs showing installed pipes, trench lines, and control valves with GPS metadata.',
    1, 1, 10485760, 'PDF,JPG,PNG', '2026-11-28', 3, NOW(), NOW()
);
SET @req2_id = LAST_INSERT_ID();

INSERT INTO compliance_requirements (
    scheme_id, application_id, milestone_id, title, description, 
    required, active, max_file_size_bytes, allowed_formats, submission_deadline, 
    created_by_user_id, created_at, updated_at
) VALUES (
    3, 5, @ms3_id, 'Safety & Pressure Audit Certificate',
    'Pressure gauge test certificate issued by accredited inspection agency.',
    1, 1, 10485760, 'PDF,JPG,PNG', '2026-11-15', 3, NOW(), NOW()
);
SET @req3_id = LAST_INSERT_ID();

-- 7. Milestone Compliances
-- Compliance 1: VERIFIED
INSERT INTO milestone_compliances (
    milestone_id, requirement_id, requirement_title, description, status,
    required_obligation, submission_deadline, submitted_at, submitted_by_user_id,
    verified_at, verified_by_user_id, verification_remarks, submission_notes,
    document_file_name, document_file_size, document_content_type, document_data
) VALUES (
    @ms1_id, @req1_id, 'Vendor Purchase Invoice & Equipment Quotations',
    'Vendor invoices and payment receipts for pipes and filters.', 'VERIFIED',
    1, '2026-10-20', '2026-10-18 11:00:00', 9,
    '2026-10-19 14:00:00', 11, 'Purchase invoice verified against certified vendor rate cards. Equipment serialized.',
    'Submitted signed invoice from Jain Irrigation Ltd.',
    'Vendor_Invoice_Krishi_772.pdf', 245120, 'application/pdf', UNHEX('255044462D312E340A')
);

-- Compliance 2: SUBMITTED (pending review)
INSERT INTO milestone_compliances (
    milestone_id, requirement_id, requirement_title, description, status,
    required_obligation, submission_deadline, submitted_at, submitted_by_user_id,
    verified_at, verified_by_user_id, verification_remarks, submission_notes,
    document_file_name, document_file_size, document_content_type, document_data
) VALUES (
    @ms2_id, @req2_id, 'Installation Progress Photos with Geotags',
    'Geotagged site photographs.', 'SUBMITTED',
    1, '2026-11-28', '2026-11-22 16:30:00', 9,
    NULL, NULL, NULL,
    'Preliminary photos of trenches and solar pump mounting.',
    'Geotagged_Installation_Photos.pdf', 512000, 'application/pdf', UNHEX('255044462D312E340A')
);

-- Compliance 3: OVERDUE (triggers non-compliance reminder module)
INSERT INTO milestone_compliances (
    milestone_id, requirement_id, requirement_title, description, status,
    required_obligation, submission_deadline, submitted_at, submitted_by_user_id,
    verified_at, verified_by_user_id, verification_remarks, submission_notes,
    document_file_name, document_file_size, document_content_type, document_data
) VALUES (
    @ms3_id, @req3_id, 'Safety & Pressure Audit Certificate',
    'Mandatory pressure audit certificate.', 'OVERDUE',
    1, '2026-11-15', NULL, NULL,
    NULL, NULL, NULL,
    NULL,
    NULL, NULL, NULL, NULL
);
SET @comp3_id = LAST_INSERT_ID();

-- 8. Non-Compliance Case
INSERT INTO compliance_non_compliance (
    compliance_id, detected_at, detected_by_user_id, reason, severity, status, officer_remarks
) VALUES (
    @comp3_id, '2026-11-16 00:05:00', 3,
    'Deadline missed: Mandatory pressure audit certificate has not been provided within the statutory 7-day milestone window.',
    'HIGH', 'OPEN',
    'Automated reminder dispatched to beneficiary. Verification hold active on subsequent disbursement tranche.'
);

-- 9. In-app Notification for Overdue Reminder
INSERT INTO in_app_notifications (
    user_id, application_id, event_key, message, created_at, read_at
) VALUES (
    9, 5, 'COMPLIANCE_OVERDUE_5_3',
    'URGENT: Milestone compliance "Safety & Pressure Audit Certificate" is overdue for application #5. Please submit immediately to prevent disbursement hold.',
    '2026-11-16 00:05:00', NULL
);

-- 10. Grant Utilization Records (Populating Fund Analytics Charts)
-- Record 1: Equipment Purchase - ₹28,500.00 (VERIFIED)
INSERT INTO grant_utilization_records (
    application_id, installment_id, category, description, reported_amount, 
    verification_status, submitted_at, submitted_by_user_id, verified_at, verified_by_user_id, review_remarks
) VALUES (
    5, @inst1_id, 'EQUIPMENT_PURCHASE',
    'Submersible pump, PVC lateral piping, and micro-emitters from Jain Irrigation Ltd.',
    28500.00, 'VERIFIED', '2026-10-25 09:15:00', 9, '2026-10-27 15:30:00', 13,
    'Original tax invoice #JIN-2026-881 verified with GSTIN and serial numbers.'
);

-- Record 2: Civil Works - ₹10,000.00 (VERIFIED)
INSERT INTO grant_utilization_records (
    application_id, installment_id, category, description, reported_amount, 
    verification_status, submitted_at, submitted_by_user_id, verified_at, verified_by_user_id, review_remarks
) VALUES (
    5, @inst1_id, 'CIVIL_WORKS',
    'Trench excavation, masonry pump shed, and foundation works.',
    10000.00, 'VERIFIED', '2026-10-28 12:00:00', 9, '2026-10-30 11:45:00', 11,
    'Field inspection confirmed completion of shed foundation and pipe trenches.'
);

-- Record 3: Operational Expenses - ₹1,500.00 (UNDER_REVIEW)
INSERT INTO grant_utilization_records (
    application_id, installment_id, category, description, reported_amount, 
    verification_status, submitted_at, submitted_by_user_id, verified_at, verified_by_user_id, review_remarks
) VALUES (
    5, @inst1_id, 'OPERATIONAL_EXPENSES',
    'Freight transportation and unloading charges for pipes and fittings.',
    1500.00, 'UNDER_REVIEW', '2026-11-20 10:00:00', 9, NULL, NULL,
    NULL
);
