export function formatCurrencyINR(amount) {
  return `₹${Number(amount || 0).toLocaleString("en-IN")}`;
}

export function getDisbursementSummary(app) {
  if (!app || app.status !== "APPROVED") {
    return null;
  }

  const raw = app.disbursementSummary;
  if (!raw) {
    const fallbackApproved = Number(app.requestedAmount || 0);
    return {
      disbursementStatus: "AWAITING_DISBURSEMENT",
      planId: null,
      planStatus: null,
      totalApprovedAmount: fallbackApproved,
      disbursedAmount: 0,
      remainingAmount: fallbackApproved,
      releasedInstallmentsCount: 0,
      totalInstallmentsCount: 0,
      installments: [],
    };
  }

  const installments = Array.isArray(raw.installments) ? raw.installments : [];
  const totalApprovedAmount = Number(raw.totalApprovedAmount ?? app.requestedAmount ?? 0);
  const disbursedAmount = Number(raw.disbursedAmount ?? 0);
  const remainingAmount = Number(
    raw.remainingAmount ?? Math.max(0, totalApprovedAmount - disbursedAmount)
  );
  const totalInstallmentsCount = Number(raw.totalInstallmentsCount ?? installments.length ?? 0);
  const releasedInstallmentsCount = Number(
    raw.releasedInstallmentsCount ??
      installments.filter((i) => i.status === "DISBURSED").length
  );

  let disbursementStatus = "AWAITING_DISBURSEMENT";
  if (totalInstallmentsCount > 0 && releasedInstallmentsCount === totalInstallmentsCount) {
    disbursementStatus = "DISBURSED";
  } else if (releasedInstallmentsCount > 0) {
    disbursementStatus = "PARTIALLY_DISBURSED";
  }

  return {
    ...raw,
    disbursementStatus,
    totalApprovedAmount,
    disbursedAmount,
    remainingAmount,
    releasedInstallmentsCount,
    totalInstallmentsCount,
    installments,
  };
}

export function getDisbursementBadgeInfo(app) {
  const summary = getDisbursementSummary(app);
  if (!summary) return null;

  if (summary.disbursementStatus === "DISBURSED") {
    return {
      status: "DISBURSED",
      label: "Disbursed",
      shortLabel: "Disbursed",
      badgeClass: "dash-badge--success",
      summaryText: `${summary.releasedInstallmentsCount}/${summary.totalInstallmentsCount} stages released`,
    };
  }

  if (summary.disbursementStatus === "PARTIALLY_DISBURSED") {
    return {
      status: "PARTIALLY_DISBURSED",
      label: `Partially Disbursed (${summary.releasedInstallmentsCount}/${summary.totalInstallmentsCount})`,
      shortLabel: "Partially Disbursed",
      badgeClass: "dash-badge--info",
      summaryText: `${summary.releasedInstallmentsCount} of ${summary.totalInstallmentsCount} stages released`,
    };
  }

  return {
    status: "AWAITING_DISBURSEMENT",
    label: "Approved · Awaiting Disbursement",
    shortLabel: "Awaiting Disbursement",
    badgeClass: "dash-badge--warning",
    summaryText:
      summary.totalInstallmentsCount > 0
        ? `0 of ${summary.totalInstallmentsCount} stages released`
        : "No installments released yet",
  };
}

export function getStepState(app, step) {
  if (!app) return "";
  const { status, workflowStage } = app;

  if (status === "REJECTED") {
    if (step === 2 && (workflowStage === "FIELD" || status === "FIELD_VERIFICATION")) return "grant-stepper__step--rejected";
    if (step === 3 && (workflowStage === "DISTRICT" || status === "DISTRICT_REVIEW")) return "grant-stepper__step--rejected";
    if (step === 4 && (workflowStage === "FINANCE" || status === "FINANCE_REVIEW")) return "grant-stepper__step--rejected";
  }

  if (status === "NEEDS_CORRECTION") {
    if (step === 2 && (workflowStage === "FIELD" || !workflowStage)) return "grant-stepper__step--warning";
    if (step === 3 && workflowStage === "DISTRICT") return "grant-stepper__step--warning";
    if (step === 4 && workflowStage === "FINANCE") return "grant-stepper__step--warning";
  }

  if (step === 1) {
    return status === "DRAFT" ? "grant-stepper__step--active" : "grant-stepper__step--completed";
  }

  if (step === 2) {
    if (["DISTRICT", "FINANCE"].includes(workflowStage) || ["DISTRICT_REVIEW", "FINANCE_REVIEW", "APPROVED"].includes(status)) {
      return "grant-stepper__step--completed";
    }
    if (workflowStage === "FIELD" || ["SUBMITTED", "UNDER_VERIFICATION", "FIELD_VERIFICATION"].includes(status)) {
      return "grant-stepper__step--active";
    }
    return "";
  }

  if (status === "APPROVED") {
    if (step <= 4) return "grant-stepper__step--completed";
    const summary = getDisbursementSummary(app);
    return summary?.disbursementStatus === "DISBURSED"
      ? "grant-stepper__step--completed"
      : "grant-stepper__step--active";
  }

  if (step === 3) {
    if (workflowStage === "FINANCE" || status === "FINANCE_REVIEW") {
      return "grant-stepper__step--completed";
    }
    if (workflowStage === "DISTRICT" || status === "DISTRICT_REVIEW") {
      return "grant-stepper__step--active";
    }
    return "";
  }

  if (step === 4) {
    if (workflowStage === "FINANCE" || status === "FINANCE_REVIEW") {
      return "grant-stepper__step--active";
    }
    return "";
  }

  if (step === 5) {
    return "";
  }

  return "";
}

export function getPendingActionText(app) {
  if (!app) return "";

  if (app.status === "APPROVED") {
    const summary = getDisbursementSummary(app);
    if (summary?.disbursementStatus === "DISBURSED") {
      return `All scheduled grant installments (${formatCurrencyINR(summary.disbursedAmount)} across ${summary.releasedInstallmentsCount} stage${summary.releasedInstallmentsCount === 1 ? "" : "s"}) have been fully disbursed.`;
    }
    if (summary?.disbursementStatus === "PARTIALLY_DISBURSED") {
      return `Your grant is partially disbursed: ${formatCurrencyINR(summary.disbursedAmount)} of ${formatCurrencyINR(summary.totalApprovedAmount)} released (${summary.releasedInstallmentsCount} of ${summary.totalInstallmentsCount} installments). Remaining ${formatCurrencyINR(summary.remainingAmount)} awaits subsequent stage verification and release.`;
    }
    return "Congratulations! Your application has received all required verifications and final Finance approval. It is awaiting disbursement.";
  }

  if (app.status === "REJECTED") {
    return "Your application was not approved. Please see the official rejection remarks below for details.";
  }

  if (app.status === "WITHDRAWN") {
    return "This application was withdrawn by you.";
  }

  if (app.status === "DRAFT") {
    return "Your application is saved as a draft. Review your answers, upload any required supporting documents, and click Submit when ready.";
  }

  if (app.status === "NEEDS_CORRECTION") {
    return "Action Required: The reviewing officer has requested corrections. Please review the remarks below, update your details or attach required documents, and resubmit.";
  }

  if (
    app.status === "FINANCE_REVIEW" ||
    app.workflowStage === "FINANCE"
  ) {
    return "District review was successfully completed. Your application is currently under Stage 3: Finance Officer Approval. No action is required from you at this time.";
  }

  if (app.status === "DISTRICT_REVIEW" || app.workflowStage === "DISTRICT") {
    return "Field verification was successfully completed. Your application is currently under Stage 2: District Officer Review. No action is required from you at this time.";
  }

  if (
    app.status === "SUBMITTED" ||
    app.status === "UNDER_VERIFICATION" ||
    app.status === "FIELD_VERIFICATION" ||
    app.workflowStage === "FIELD"
  ) {
    return "Your application has been received and is undergoing Stage 1: Field Officer Verification. No action is required from you at this time.";
  }

  return "Your application is currently being processed by the administration.";
}
