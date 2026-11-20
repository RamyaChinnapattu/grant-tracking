import api from "./api";

const BASE_URL = "/workflow/notifications";

export const getNotifications = () => api.get(BASE_URL);

export const markNotificationRead = (id) =>
  api.post(`${BASE_URL}/${id}/read`);

export const markAllNotificationsRead = () =>
  api.post(`${BASE_URL}/read-all`);
