import api from "./api";

export const getAuditLogs = () => {
  return api.get("/audit/logs");
};

export default {
  getAuditLogs,
};
