import { useEffect, useState } from "react";
import {
  getMyBeneficiaryProfile,
  createBeneficiary,
  updateMyBeneficiaryProfile,
} from "../services/beneficiaryService";
import { PageHeader } from "../components/ui/SaaSPrimitives";
import "../styles/BeneficiaryManagement.css";

const initialForm = {
  fullName: "",
  email: "",
  phone: "",
  dateOfBirth: "",
  annualIncome: "",
  address: "",
  state: "",
  district: "",
  category: "",
};

function BeneficiaryManagement() {
  const [form, setForm] = useState(initialForm);
  const [profileExists, setProfileExists] = useState(false);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");
  const [validationErrors, setValidationErrors] = useState({});

  useEffect(() => {
    loadProfile();
  }, []);

  const loadProfile = async () => {
    try {
      setLoading(true);

      const response = await getMyBeneficiaryProfile();
      const profile = response.data;

      setForm({
        fullName: profile.fullName || "",
        email: profile.email || "",
        phone: profile.phone || "",
        dateOfBirth: profile.dateOfBirth || "",
        annualIncome: profile.annualIncome ?? "",
        address: profile.address || "",
        state: profile.state || "",
        district: profile.district || "",
        category: profile.category || "",
      });

      if (profile.fullName) {
        localStorage.setItem("fullName", profile.fullName);
      }

      setProfileExists(profile.id != null);
    } catch (err) {
      setError(
        err.response?.data?.message ||
          err.response?.data?.error ||
          "Unable to load beneficiary profile."
      );
    } finally {
      setLoading(false);
    }
  };

  const handleChange = (event) => {
    const { name, value } = event.target;

    setForm((prev) => ({
      ...prev,
      [name]: value,
    }));

    if (validationErrors[name]) {
      setValidationErrors((prev) => ({ ...prev, [name]: "" }));
    }
  };

  const validate = () => {
    const errors = {};

    if (!form.fullName.trim() || form.fullName.trim().length < 2) {
      errors.fullName = "Full name must be at least 2 characters long.";
    }

    const phonePattern = /^\+?[0-9]{10,15}$/;
    if (!form.phone.trim() || !phonePattern.test(form.phone.trim().replace(/[-\s]/g, ""))) {
      errors.phone = "Enter a valid phone number (10 to 15 digits).";
    }

    if (!form.dateOfBirth) {
      errors.dateOfBirth = "Date of birth is required.";
    } else {
      const dob = new Date(form.dateOfBirth);
      const today = new Date();
      if (dob >= today) {
        errors.dateOfBirth = "Date of birth must be in the past.";
      }
    }

    if (form.annualIncome === "" || isNaN(Number(form.annualIncome)) || Number(form.annualIncome) < 0) {
      errors.annualIncome = "Please enter a valid non-negative annual income.";
    }

    if (!form.address.trim()) {
      errors.address = "Address is required.";
    }

    if (!form.state.trim()) {
      errors.state = "State is required for officer assignment and jurisdiction.";
    }

    if (!form.district.trim()) {
      errors.district = "District is required for officer assignment and jurisdiction.";
    }

    if (!form.category) {
      errors.category = "Please select an affirmative action or social category.";
    }

    return errors;
  };

  const handleSubmit = async (event) => {
    event.preventDefault();

    setMessage("");
    setError("");

    const errors = validate();
    if (Object.keys(errors).length > 0) {
      setValidationErrors(errors);
      setError("Please fix the highlighted errors before saving.");
      return;
    }

    setSaving(true);

    try {
      const payload = {
        fullName: form.fullName.trim(),
        phone: form.phone.trim(),
        dateOfBirth: form.dateOfBirth,
        annualIncome: Number(form.annualIncome),
        address: form.address.trim(),
        state: form.state.trim(),
        district: form.district.trim(),
        category: form.category,
      };

      const response = profileExists
        ? await updateMyBeneficiaryProfile(payload)
        : await createBeneficiary(payload);

      const profile = response.data;
      setForm({
        fullName: profile.fullName || "",
        email: profile.email || "",
        phone: profile.phone || "",
        dateOfBirth: profile.dateOfBirth || "",
        annualIncome: profile.annualIncome ?? "",
        address: profile.address || "",
        state: profile.state || "",
        district: profile.district || "",
        category: profile.category || "",
      });

      if (profile.fullName) {
        localStorage.setItem("fullName", profile.fullName);
      }

      setProfileExists(profile.id != null);
      setMessage(
        profileExists
          ? "Beneficiary profile updated successfully!"
          : "Beneficiary profile created successfully!"
      );
    } catch (err) {
      setError(
        err.response?.data?.message ||
          err.response?.data?.error ||
          "Failed to save beneficiary profile."
      );
    } finally {
      setSaving(false);
    }
  };

  if (loading) {
    return <p style={{ padding: "24px" }}>Loading beneficiary profile...</p>;
  }

  const coreFields = [
    form.fullName?.trim(),
    form.phone?.trim(),
    form.dateOfBirth,
    form.annualIncome !== "" && form.annualIncome !== null,
    form.address?.trim(),
    form.state?.trim(),
    form.district?.trim(),
    form.category,
  ];
  const filledCount = coreFields.filter(Boolean).length;
  const completionPct = Math.round((filledCount / coreFields.length) * 100);

  return (
    <div style={styles.container} className="saas-page">
      <PageHeader
        eyebrow="Beneficiary Profile"
        title="My Beneficiary Profile"
        subtitle="Maintain accurate identity, contact, income, and geographical jurisdiction details for scheme eligibility and officer review."
      />

      {/* Profile Completion Indicator */}
      <div
        style={{
          margin: "16px 0 20px",
          padding: "16px",
          borderRadius: "10px",
          background: completionPct === 100 ? "#f0fdf4" : "var(--color-surface-low)",
          border: `1px solid ${completionPct === 100 ? "#bbf7d0" : "var(--color-outline-variant)"}`,
        }}
      >
        <div
          style={{
            display: "flex",
            justifyContent: "space-between",
            alignItems: "center",
            marginBottom: "8px",
          }}
        >
          <span style={{ fontSize: "0.86rem", fontWeight: 700, color: "var(--color-on-surface)" }}>
            Profile Completeness
          </span>
          <span
            style={{
              fontSize: "0.84rem",
              fontWeight: 700,
              color: completionPct === 100 ? "#15803d" : "var(--color-primary)",
            }}
          >
            {completionPct}% Complete
          </span>
        </div>
        <div
          style={{
            height: "8px",
            borderRadius: "4px",
            background: "var(--color-surface-lowest)",
            overflow: "hidden",
            border: "1px solid var(--color-outline-variant)",
          }}
        >
          <div
            style={{
              height: "100%",
              width: `${completionPct}%`,
              background: completionPct === 100 ? "#16a34a" : "var(--color-primary)",
              transition: "width 300ms ease",
            }}
          />
        </div>
        <span
          style={{
            display: "block",
            fontSize: "0.78rem",
            color: "var(--color-text-muted)",
            marginTop: "6px",
          }}
        >
          {completionPct === 100
            ? "All mandatory jurisdiction and demographic fields are completed."
            : "Complete all fields to ensure prompt Field and District jurisdiction matching."}
        </span>
      </div>

      {message && <div style={styles.success}>{message}</div>}
      {error && <div style={styles.error}>{error}</div>}

      <form onSubmit={handleSubmit} style={styles.form} noValidate>
        <div>
          <label style={styles.label}>
            Full Name <span style={{ color: "#b91c1c" }}>*</span>
          </label>
          <input
            type="text"
            name="fullName"
            value={form.fullName}
            onChange={handleChange}
            style={validationErrors.fullName ? styles.inputError : styles.input}
            required
            placeholder="Your full name as per official records"
          />
          {validationErrors.fullName && (
            <span style={styles.errorText}>{validationErrors.fullName}</span>
          )}
        </div>

        <div>
          <label style={styles.label}>
            Email Address{" "}
            <span style={{ fontSize: "0.78rem", color: "var(--color-text-muted)" }}>
              (Account login identifier · Read only)
            </span>
          </label>
          <input
            type="email"
            name="email"
            value={form.email}
            readOnly
            style={{ ...styles.input, background: "var(--color-surface-low)", cursor: "not-allowed" }}
          />
        </div>

        <div>
          <label style={styles.label}>
            Phone Number <span style={{ color: "#b91c1c" }}>*</span>
          </label>
          <input
            type="tel"
            name="phone"
            value={form.phone}
            onChange={handleChange}
            style={validationErrors.phone ? styles.inputError : styles.input}
            required
            placeholder="e.g. 9876543210"
          />
          {validationErrors.phone && (
            <span style={styles.errorText}>{validationErrors.phone}</span>
          )}
        </div>

        <div>
          <label style={styles.label}>
            Date of Birth <span style={{ color: "#b91c1c" }}>*</span>
          </label>
          <input
            type="date"
            name="dateOfBirth"
            value={form.dateOfBirth}
            onChange={handleChange}
            style={validationErrors.dateOfBirth ? styles.inputError : styles.input}
            required
          />
          {validationErrors.dateOfBirth && (
            <span style={styles.errorText}>{validationErrors.dateOfBirth}</span>
          )}
        </div>

        <div>
          <label style={styles.label}>
            Annual Income (₹) <span style={{ color: "#b91c1c" }}>*</span>
          </label>
          <input
            type="number"
            name="annualIncome"
            value={form.annualIncome}
            onChange={handleChange}
            min="0"
            step="0.01"
            style={validationErrors.annualIncome ? styles.inputError : styles.input}
            required
            placeholder="Total annual family income"
          />
          {validationErrors.annualIncome && (
            <span style={styles.errorText}>{validationErrors.annualIncome}</span>
          )}
        </div>

        <div>
          <label style={styles.label}>
            Address <span style={{ color: "#b91c1c" }}>*</span>
          </label>
          <textarea
            name="address"
            value={form.address}
            onChange={handleChange}
            rows="3"
            style={validationErrors.address ? styles.inputError : styles.input}
            required
            placeholder="Door number, street, locality, pincode"
          />
          {validationErrors.address && (
            <span style={styles.errorText}>{validationErrors.address}</span>
          )}
        </div>

        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "12px" }}>
          <div>
            <label style={styles.label}>
              State <span style={{ color: "#b91c1c" }}>*</span>
            </label>
            <input
              type="text"
              name="state"
              value={form.state}
              onChange={handleChange}
              style={validationErrors.state ? styles.inputError : styles.input}
              required
              placeholder="e.g. Karnataka"
            />
            {validationErrors.state && (
              <span style={styles.errorText}>{validationErrors.state}</span>
            )}
          </div>

          <div>
            <label style={styles.label}>
              District <span style={{ color: "#b91c1c" }}>*</span>
            </label>
            <input
              type="text"
              name="district"
              value={form.district}
              onChange={handleChange}
              style={validationErrors.district ? styles.inputError : styles.input}
              required
              placeholder="e.g. Bengaluru Urban"
            />
            {validationErrors.district && (
              <span style={styles.errorText}>{validationErrors.district}</span>
            )}
          </div>
        </div>

        <div>
          <label style={styles.label}>
            Social Category <span style={{ color: "#b91c1c" }}>*</span>
          </label>
          <select
            name="category"
            value={form.category}
            onChange={handleChange}
            style={validationErrors.category ? styles.inputError : styles.input}
            required
          >
            <option value="">Select Category</option>
            <option value="General">General</option>
            <option value="OBC">OBC</option>
            <option value="SC">SC</option>
            <option value="ST">ST</option>
            <option value="EWS">EWS</option>
            <option value="Other">Other</option>
          </select>
          {validationErrors.category && (
            <span style={styles.errorText}>{validationErrors.category}</span>
          )}
        </div>

        <button
          type="submit"
          disabled={saving}
          style={{
            marginTop: "16px",
            padding: "12px 24px",
            background: "var(--color-primary)",
            color: "#fff",
            border: "none",
            borderRadius: "8px",
            fontWeight: 600,
            cursor: saving ? "not-allowed" : "pointer",
            fontSize: "0.95rem",
          }}
        >
          {saving
            ? "Saving Profile..."
            : profileExists
              ? "Update Profile"
              : "Create Profile"}
        </button>
      </form>
    </div>
  );
}

const styles = {
  container: {
    maxWidth: "720px",
    margin: "20px auto",
    padding: "28px",
    background: "var(--color-surface-lowest)",
    borderRadius: "14px",
    border: "1px solid var(--color-outline-variant)",
    boxShadow: "0 6px 20px var(--color-shadow)",
  },
  form: {
    display: "flex",
    flexDirection: "column",
    gap: "16px",
    marginTop: "20px",
  },
  label: {
    display: "block",
    fontSize: "0.86rem",
    fontWeight: 600,
    color: "var(--color-on-surface)",
    marginBottom: "6px",
  },
  input: {
    width: "100%",
    padding: "10px 14px",
    borderRadius: "8px",
    border: "1px solid var(--color-outline-variant)",
    background: "var(--color-surface-lowest)",
    color: "var(--color-on-surface)",
    fontSize: "0.92rem",
    boxSizing: "border-box",
  },
  inputError: {
    width: "100%",
    padding: "10px 14px",
    borderRadius: "8px",
    border: "1px solid #b91c1c",
    background: "#fff5f5",
    color: "var(--color-on-surface)",
    fontSize: "0.92rem",
    boxSizing: "border-box",
  },
  errorText: {
    display: "block",
    color: "#b91c1c",
    fontSize: "0.78rem",
    marginTop: "4px",
    fontWeight: 500,
  },
  success: {
    padding: "12px 16px",
    background: "var(--color-success-container)",
    color: "var(--color-success)",
    borderRadius: "8px",
    marginTop: "12px",
    fontSize: "0.9rem",
    fontWeight: 500,
  },
  error: {
    padding: "12px 16px",
    background: "#fee2e2",
    color: "#991b1b",
    border: "1px solid #fecaca",
    borderRadius: "8px",
    marginTop: "12px",
    fontSize: "0.9rem",
    fontWeight: 500,
  },
};

export default BeneficiaryManagement;
