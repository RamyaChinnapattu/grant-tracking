import api from "./api";
export const getAdminDisbursementDashboard=(params={})=>api.get("/milestone-3/admin-dashboard",{params});
export const getAssignedMilestones=()=>api.get("/milestone-3/assigned-milestones");
export const downloadMilestone3Report=(type,params={})=>api.get(`/reports/milestone-3/${type}.csv`,{params,responseType:"blob"});
