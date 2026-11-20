import api from "./api";

export async function createDisbursementPlan(planData) {
  return api.post("/disbursements/plans", planData);
}

export async function getDisbursementPlans(paged = false, page = 0, size = 20) {
  const params = paged ? { paged: true, page, size } : {};
  return api.get("/disbursements/plans", { params });
}

export async function getDisbursementPlanById(id) {
  return api.get(`/disbursements/plans/${id}`);
}

export async function getDisbursementPlanByApplicationId(applicationId) {
  return api.get(`/disbursements/plans/by-application/${applicationId}`);
}

export async function getEligibleApplications() {
  return api.get("/disbursements/eligible-applications");
}

export async function getAllMilestones() {
  return api.get("/disbursements/milestones");
}

export async function updateMilestoneProgress(milestoneId, progressData) {
  return api.patch(`/disbursements/milestones/${milestoneId}/progress`, progressData);
}

export async function getAllCompliances() {
  return api.get("/disbursements/compliances");
}

export async function getCompliancesByMilestone(milestoneId) {
  return api.get(`/disbursements/milestones/${milestoneId}/compliances`);
}

export async function submitCompliance(milestoneId, formData) {
  return api.post(`/disbursements/milestones/${milestoneId}/compliance`, formData, {
    headers: {
      "Content-Type": "multipart/form-data",
    },
  });
}

export async function verifyCompliance(complianceId, verifyData) {
  return api.post(`/disbursements/compliance/${complianceId}/verify`, verifyData);
}

export async function downloadComplianceDocument(complianceId) {
  return api.get(`/disbursements/compliance/${complianceId}/document`, {
    responseType: "blob",
  });
}

export async function disburseInstallment(installmentId, disburseData) {
  return api.post(`/disbursements/installments/${installmentId}/disburse`, disburseData);
}

export async function getFundUtilization() {
  return api.get("/disbursements/fund-utilization");
}

export async function getRegionalAnalytics() {
  return api.get("/disbursements/regional-analytics");
}

export async function getReminderConfig() {
  return api.get("/disbursements/reminders/config");
}

export async function updateReminderConfig(configData) {
  return api.put("/disbursements/reminders/config", configData);
}

export async function triggerReminderScan() {
  return api.post("/disbursements/reminders/trigger-scan");
}
