import api from "./api";

export const getComplianceRequirements = () => api.get("/compliance/requirements");
export const createComplianceRequirement = (data) => api.post("/compliance/requirements", data);
export const deactivateComplianceRequirement = (id) => api.patch(`/compliance/requirements/${id}/deactivate`);
export const getComplianceMine = () => api.get("/compliance/mine");
export const submitComplianceDocument = (id, form) => api.post(`/compliance/submissions/${id}`, form, { headers: { "Content-Type": "multipart/form-data" } });
export const getComplianceReviewQueue = () => api.get("/compliance/review-queue");
export const reviewCompliance = (id, data) => api.patch(`/compliance/submissions/${id}/review`, data);
export const waiveCompliance = (id, remarks) => api.patch(`/compliance/submissions/${id}/waive`, { remarks });
export const getComplianceDocument = (id) => api.get(`/compliance/submissions/${id}/document`, { responseType: "blob" });
export const getNonComplianceCases = () => api.get("/compliance/non-compliances");
export const updateNonComplianceCase = (id, data) => api.patch(`/compliance/non-compliances/${id}`, data);
