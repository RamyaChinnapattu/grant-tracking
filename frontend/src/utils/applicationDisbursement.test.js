import test from "node:test";
import assert from "node:assert/strict";
import {
  getDisbursementBadgeInfo,
  getDisbursementSummary,
  getPendingActionText,
  getStepState,
} from "./applicationDisbursement.js";

test("approved application before any release shows Awaiting Disbursement and keeps step 5 active", () => {
  const app = {
    id: 5,
    status: "APPROVED",
    requestedAmount: 20000,
    disbursementSummary: {
      disbursementStatus: "AWAITING_DISBURSEMENT",
      totalApprovedAmount: 20000,
      disbursedAmount: 0,
      remainingAmount: 20000,
      releasedInstallmentsCount: 0,
      totalInstallmentsCount: 3,
      installments: [],
    },
  };

  const summary = getDisbursementSummary(app);
  assert.equal(summary.disbursementStatus, "AWAITING_DISBURSEMENT");
  assert.equal(summary.disbursedAmount, 0);
  assert.equal(summary.remainingAmount, 20000);

  const badge = getDisbursementBadgeInfo(app);
  assert.equal(badge.label, "Approved · Awaiting Disbursement");

  assert.equal(getStepState(app, 4), "grant-stepper__step--completed");
  assert.equal(getStepState(app, 5), "grant-stepper__step--active");
});

test("partial disbursement shows Partially Disbursed and does NOT mark step 5 completed", () => {
  const app = {
    id: 5,
    status: "APPROVED",
    requestedAmount: 20000,
    disbursementSummary: {
      disbursementStatus: "PARTIALLY_DISBURSED",
      totalApprovedAmount: 20000,
      disbursedAmount: 8000,
      remainingAmount: 12000,
      releasedInstallmentsCount: 1,
      totalInstallmentsCount: 3,
      installments: [
        { id: 1, installmentNumber: 1, status: "DISBURSED", amount: 8000 },
        { id: 2, installmentNumber: 2, status: "READY_FOR_DISBURSEMENT", amount: 6000 },
        { id: 3, installmentNumber: 3, status: "PLANNED", amount: 6000 },
      ],
    },
  };

  const summary = getDisbursementSummary(app);
  assert.equal(summary.disbursementStatus, "PARTIALLY_DISBURSED");
  assert.equal(summary.disbursedAmount, 8000);
  assert.equal(summary.remainingAmount, 12000);

  const badge = getDisbursementBadgeInfo(app);
  assert.equal(badge.label, "Partially Disbursed (1/3)");

  assert.equal(getStepState(app, 5), "grant-stepper__step--active");
  assert.match(getPendingActionText(app), /partially disbursed/i);
  assert.match(getPendingActionText(app), /8,000/);
  assert.match(getPendingActionText(app), /12,000/);
});

test("full disbursement marks step 5 completed and reports Disbursed", () => {
  const app = {
    id: 5,
    status: "APPROVED",
    requestedAmount: 20000,
    disbursementSummary: {
      disbursementStatus: "DISBURSED",
      totalApprovedAmount: 20000,
      disbursedAmount: 20000,
      remainingAmount: 0,
      releasedInstallmentsCount: 3,
      totalInstallmentsCount: 3,
      installments: [
        { id: 1, installmentNumber: 1, status: "DISBURSED", amount: 8000 },
        { id: 2, installmentNumber: 2, status: "DISBURSED", amount: 6000 },
        { id: 3, installmentNumber: 3, status: "DISBURSED", amount: 6000 },
      ],
    },
  };

  const badge = getDisbursementBadgeInfo(app);
  assert.equal(badge.label, "Disbursed");
  assert.equal(getStepState(app, 4), "grant-stepper__step--completed");
  assert.equal(getStepState(app, 5), "grant-stepper__step--completed");
  assert.match(getPendingActionText(app), /fully disbursed/i);
});
