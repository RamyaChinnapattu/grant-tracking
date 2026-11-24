import { useState } from "react";
import { useNavigate, Link } from "react-router-dom";
import api from "../services/api";
import "../styles/Login.css";

function Login() {
  const navigate = useNavigate();

  const [formData, setFormData] = useState({
    email: "",
    password: "",
  });

  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  const handleChange = (e) => {
    setFormData({
      ...formData,
      [e.target.name]: e.target.value,
    });
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError("");
    setLoading(true);

    try {
      const response = await api.post("/auth/login", formData);
      const user = response.data;

      localStorage.setItem("token", user.token);
      localStorage.setItem("userId", user.userId);
      localStorage.setItem("fullName", user.fullName);
      localStorage.setItem("email", user.email);
      localStorage.setItem("role", user.role);
      if (user.officerLevel) {
        localStorage.setItem("officerLevel", user.officerLevel);
      } else {
        localStorage.removeItem("officerLevel");
      }
      if (user.jurisdictionState) {
        localStorage.setItem("jurisdictionState", user.jurisdictionState);
      } else {
        localStorage.removeItem("jurisdictionState");
      }
      if (user.jurisdictionDistrict) {
        localStorage.setItem("jurisdictionDistrict", user.jurisdictionDistrict);
      } else {
        localStorage.removeItem("jurisdictionDistrict");
      }

      switch (user.role) {
        case "ADMIN":
          navigate("/admin-dashboard");
          break;
        case "FIELD_OFFICER":
          navigate("/field-officer-dashboard");
          break;
        case "DISTRICT_OFFICER":
          navigate("/district-officer-dashboard");
          break;
        case "FINANCE_OFFICER":
          navigate("/finance-officer-dashboard");
          break;
        case "OFFICER":
          navigate("/officer-dashboard");
          break;
        case "BENEFICIARY":
          navigate("/beneficiary-dashboard");
          break;
        default:
          setError("Unknown user role");
      }
    } catch (err) {
      setError(
        err.response?.data?.message ||
          "Invalid email or password. Please try again."
      );
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="login-container">
      <form className="login-form" onSubmit={handleSubmit}>
        <h1>Grantora</h1>
        <p>Sign in to your account</p>

        {error && <div className="error-message">{error}</div>}

        <label>Email</label>
        <input
          type="email"
          name="email"
          placeholder="Enter your email"
          value={formData.email}
          onChange={handleChange}
          required
        />

        <label>Password</label>
        <input
          type="password"
          name="password"
          placeholder="Enter your password"
          value={formData.password}
          onChange={handleChange}
          required
        />

        <button type="submit" disabled={loading}>
          {loading ? "Logging in..." : "Login"}
        </button>

        <p>
          Don't have an account? <Link to="/register">Register</Link>
        </p>
      </form>
    </div>
  );
}

export default Login;