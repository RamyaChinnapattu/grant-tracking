import axios from "axios";

const api = axios.create({
  baseURL: "http://localhost:8080/api",
  headers: {
    "Content-Type": "application/json",
  },
});

api.interceptors.request.use((config) => {
  const token = localStorage.getItem("token");

  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }

  return config;
});

let isRedirecting = false;

api.interceptors.response.use(
  (response) => response,
  (error) => {
    const status = error.response?.status;
    const currentPath = window.location.pathname;

    // Handle genuine HTTP 401 Unauthorized for expired or invalidated tokens
    if (status === 401) {
      const url = error.config?.url || "";
      const isAuthEndpoint =
        url.includes("/auth/login") ||
        url.includes("/auth/register");
      const isPublicAuthPage =
        currentPath === "/login" || currentPath === "/register";

      if (!isAuthEndpoint && !isPublicAuthPage && !isRedirecting) {
        isRedirecting = true;
        localStorage.removeItem("token");
        localStorage.removeItem("role");
        localStorage.removeItem("fullName");
        window.location.href = "/login";
      }
    }

    return Promise.reject(error);
  }
);

export default api;