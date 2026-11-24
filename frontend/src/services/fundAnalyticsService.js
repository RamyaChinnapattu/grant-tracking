import api from "./api";
export const getFundAnalytics=(params={})=>api.get("/fund-analytics",{params});
export const getMyUtilization=()=>api.get("/utilization/mine");
export const submitUtilization=(data)=>api.post("/utilization",data);
export const replaceUtilization=(id,data)=>api.put(`/utilization/${id}`,data);
export const getUtilizationReviewQueue=()=>api.get("/utilization/review-queue");
export const reviewUtilization=(id,data)=>api.patch(`/utilization/${id}/review`,data);
