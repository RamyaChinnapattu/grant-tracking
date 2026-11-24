import { Navigate, Outlet } from "react-router-dom";
import { dashboardHomePaths } from "./dashboardNavigation";

function ProtectedRoute({ allowedRoles }) {
  const token = localStorage.getItem("token");
  const role = localStorage.getItem("role");

  if (!token) {
    return <Navigate to="/login" replace />;
  }

  if (allowedRoles && !allowedRoles.includes(role)) {
    return <Navigate to={dashboardHomePaths[role] || "/login"} replace />;
  }

  return <Outlet />;
}

export default ProtectedRoute;