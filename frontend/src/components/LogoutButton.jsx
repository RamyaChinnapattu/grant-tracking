
import { useNavigate } from "react-router-dom";
import { LogOut } from "lucide-react";

function LogoutButton({ className = "", isCollapsed = false }) {
  const navigate = useNavigate();

  const handleLogout = () => {
    localStorage.removeItem("token");
    localStorage.removeItem("userId");
    localStorage.removeItem("fullName");
    localStorage.removeItem("email");
    localStorage.removeItem("role");
    localStorage.removeItem("officerLevel");
    localStorage.removeItem("jurisdictionState");
    localStorage.removeItem("jurisdictionDistrict");

    navigate("/login", { replace: true });
  };

  return (
    <button
      className={className}
      type="button"
      onClick={handleLogout}
      title={isCollapsed ? "Logout" : undefined}
    >
      <span className="dashboard-sidebar__link-icon" aria-hidden="true">
        <LogOut size={20} strokeWidth={1.8} />
      </span>
      <span className="logout-button__label">Logout</span>
      <span className="dashboard-sidebar__tooltip" role="tooltip">
        Logout
      </span>
    </button>
  );
}

export default LogoutButton;
