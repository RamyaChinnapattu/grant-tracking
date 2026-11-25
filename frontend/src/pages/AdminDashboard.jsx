import { useEffect, useState, useMemo } from "react";
import {
  Building2,
  CheckCircle2,
  Filter,
  MapPin,
  Phone,
  Plus,
  RefreshCw,
  Search,
  ShieldCheck,
  Trash2,
  UserCheck,
  UserPlus,
  Users,
  X,
} from "lucide-react";
import {
  deleteBeneficiary,
  getBeneficiaries,
  getBeneficiaryById,
} from "../services/beneficiaryService";
import {
  getUsers,
  createUser,
  updateUser,
  deleteUser,
} from "../services/userService";
import { AlertBanner, PageHeader } from "../components/ui/SaaSPrimitives";
import "../styles/AdminDashboard.css";
import "../styles/AdminDashboardHome.css";

function formatDate(dt) {
  if (!dt) return "—";
  try {
    const d = new Date(dt);
    return isNaN(d.getTime())
      ? "—"
      : d.toLocaleDateString("en-IN", {
          year: "numeric",
          month: "short",
          day: "numeric",
        });
  } catch {
    return "—";
  }
}

function AdminDashboard() {
  const [activeRoleFilter, setActiveRoleFilter] = useState("BENEFICIARY");
  const [beneficiaries, setBeneficiaries] = useState([]);
  const [users, setUsers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [searchQuery, setSearchQuery] = useState("");

  // Beneficiary details drawer
  const [selectedBeneficiary, setSelectedBeneficiary] = useState(null);
  const [detailsLoading, setDetailsLoading] = useState(false);
  const [detailsError, setDetailsError] = useState("");

  // Action feedback
  const [deletingId, setDeletingId] = useState(null);
  const [actionError, setActionError] = useState("");
  const [successMessage, setSuccessMessage] = useState("");

  // Add User modal state
  const [showAddUserModal, setShowAddUserModal] = useState(false);
  const [newUserForm, setNewUserForm] = useState({
    fullName: "",
    email: "",
    phone: "",
    password: "",
    role: "OFFICER",
    officerLevel: "FIELD",
    jurisdictionState: "Karnataka",
    jurisdictionDistrict: "Bengaluru Urban",
  });
  const [userFormError, setUserFormError] = useState("");
  const [submittingUser, setSubmittingUser] = useState(false);

  const loadData = async () => {
    setLoading(true);
    setError("");

    try {
      const [benefsRes, usersRes] = await Promise.all([
        getBeneficiaries().catch(() => ({ data: [] })),
        getUsers().catch(() => ({ data: [] })),
      ]);
      setBeneficiaries(benefsRes.data || []);
      setUsers(usersRes.data || []);
    } catch (err) {
      setError(
        err.response?.data?.message ||
          "Unable to load beneficiaries and user accounts."
      );
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadData();
  }, []);

  const handleViewBeneficiaryDetails = async (id) => {
    setSelectedBeneficiary(null);
    setDetailsError("");
    setDetailsLoading(true);

    try {
      const response = await getBeneficiaryById(id);
      setSelectedBeneficiary(response.data);
    } catch (err) {
      setDetailsError(
        err.response?.data?.message ||
          "Unable to load beneficiary details."
      );
    } finally {
      setDetailsLoading(false);
    }
  };

  const handleDeleteBeneficiary = async (beneficiary) => {
    const name = beneficiary.fullName || "this beneficiary";
    if (
      !window.confirm(
        `Delete the profile for ${name}? This action cannot be undone.`
      )
    ) {
      return;
    }

    setDeletingId(beneficiary.id);
    setActionError("");
    setSuccessMessage("");

    try {
      await deleteBeneficiary(beneficiary.id);
      setBeneficiaries((current) =>
        current.filter((item) => item.id !== beneficiary.id)
      );
      if (selectedBeneficiary?.id === beneficiary.id) {
        setSelectedBeneficiary(null);
      }
      setSuccessMessage("Beneficiary profile deleted successfully.");
      setTimeout(() => setSuccessMessage(""), 4000);
    } catch (err) {
      setActionError(
        err.response?.data?.message || "Unable to delete beneficiary profile."
      );
    } finally {
      setDeletingId(null);
    }
  };

  const handleToggleUserActive = async (user) => {
    const nextActive = !user.active;
    try {
      await updateUser(user.id, {
        ...user,
        active: nextActive,
      });
      setUsers((prev) =>
        prev.map((u) => (u.id === user.id ? { ...u, active: nextActive } : u))
      );
      setSuccessMessage(
        `User "${user.fullName}" is now ${nextActive ? "Active" : "Inactive"}.`
      );
      setTimeout(() => setSuccessMessage(""), 4000);
    } catch (err) {
      setActionError(
        err.response?.data?.message || "Failed to update user status."
      );
    }
  };

  const handleDeleteUser = async (user) => {
    if (
      !window.confirm(
        `Delete user account for "${user.fullName}" (${user.email})? This action cannot be undone.`
      )
    ) {
      return;
    }

    try {
      await deleteUser(user.id);
      setUsers((prev) => prev.filter((u) => u.id !== user.id));
      setSuccessMessage(`User "${user.fullName}" was deleted successfully.`);
      setTimeout(() => setSuccessMessage(""), 4000);
    } catch (err) {
      setActionError(err.response?.data?.message || "Failed to delete user.");
    }
  };

  const handleCreateUserSubmit = async (e) => {
    e.preventDefault();
    setUserFormError("");
    setSubmittingUser(true);

    try {
      if (newUserForm.password.length < 8) {
        throw new Error("Password must be at least 8 characters long.");
      }
      if (!newUserForm.fullName.trim()) {
        throw new Error("Full name is required.");
      }
      if (!newUserForm.email.trim()) {
        throw new Error("Email address is required.");
      }

      const payload = {
        fullName: newUserForm.fullName.trim(),
        email: newUserForm.email.trim(),
        phone: newUserForm.phone.trim() || null,
        password: newUserForm.password,
        role: newUserForm.role,
        active: true,
      };

      if (newUserForm.role === "OFFICER") {
        payload.officerLevel = newUserForm.officerLevel;
        payload.jurisdictionState = newUserForm.jurisdictionState.trim();
        if (newUserForm.officerLevel !== "FINANCE") {
          payload.jurisdictionDistrict = newUserForm.jurisdictionDistrict.trim();
        } else {
          payload.jurisdictionDistrict = null;
        }
      }

      const res = await createUser(payload);
      setUsers((prev) => [res.data, ...prev]);
      setShowAddUserModal(false);
      setNewUserForm({
        fullName: "",
        email: "",
        phone: "",
        password: "",
        role: "OFFICER",
        officerLevel: "FIELD",
        jurisdictionState: "Karnataka",
        jurisdictionDistrict: "Bengaluru Urban",
      });
      setSuccessMessage("Officer user created successfully.");
      setTimeout(() => setSuccessMessage(""), 4000);
    } catch (err) {
      setUserFormError(
        err.response?.data?.message ||
          err.message ||
          "Failed to create user. Please verify input requirements."
      );
    } finally {
      setSubmittingUser(false);
    }
  };

  // Filtered beneficiaries
  const filteredBeneficiaries = useMemo(() => {
    if (!searchQuery.trim()) return beneficiaries;
    const q = searchQuery.toLowerCase().trim();
    return beneficiaries.filter((b) => {
      const name = (b.fullName || "").toLowerCase();
      const email = (b.email || "").toLowerCase();
      const phone = (b.phone || "").toLowerCase();
      const state = (b.state || "").toLowerCase();
      const dist = (b.district || "").toLowerCase();
      const cat = (b.category || "").toLowerCase();
      return (
        name.includes(q) ||
        email.includes(q) ||
        phone.includes(q) ||
        state.includes(q) ||
        dist.includes(q) ||
        cat.includes(q)
      );
    });
  }, [beneficiaries, searchQuery]);

  // Filtered officers & users
  const filteredUsers = useMemo(() => {
    return users.filter((u) => {
      if (activeRoleFilter === "FIELD_OFFICER") {
        if (u.role !== "OFFICER" || u.officerLevel !== "FIELD") return false;
      } else if (activeRoleFilter === "DISTRICT_OFFICER") {
        if (u.role !== "OFFICER" || u.officerLevel !== "DISTRICT") return false;
      } else if (activeRoleFilter === "FINANCE_OFFICER") {
        if (u.role !== "OFFICER" || u.officerLevel !== "FINANCE") return false;
      } else if (activeRoleFilter === "ALL_OFFICERS") {
        if (u.role !== "OFFICER") return false;
      } else if (activeRoleFilter === "ADMIN") {
        if (u.role !== "ADMIN") return false;
      }

      if (searchQuery.trim()) {
        const q = searchQuery.toLowerCase().trim();
        const name = (u.fullName || "").toLowerCase();
        const email = (u.email || "").toLowerCase();
        const phone = (u.phone || "").toLowerCase();
        const state = (u.jurisdictionState || "").toLowerCase();
        const dist = (u.jurisdictionDistrict || "").toLowerCase();
        return (
          name.includes(q) ||
          email.includes(q) ||
          phone.includes(q) ||
          state.includes(q) ||
          dist.includes(q)
        );
      }
      return true;
    });
  }, [users, activeRoleFilter, searchQuery]);

  return (
    <div className="saas-page">
      <section aria-labelledby="users-management-heading" style={{ display: "flex", flexDirection: "column", gap: "18px" }}>
        <PageHeader
          eyebrow="Directory & Access Control"
          title="Beneficiary & Officer Management"
          subtitle="Manage registered citizen beneficiaries, verification officers across Field, District, and Finance levels, and administrators."
          actions={
            <>
              <button
                className="saas-btn saas-btn--secondary"
                type="button"
                onClick={loadData}
                disabled={loading}
              >
                <RefreshCw size={15} className={loading ? "spin" : ""} />
                {loading ? "Loading..." : "Refresh"}
              </button>
              {activeRoleFilter !== "BENEFICIARY" && (
                <button
                  type="button"
                  className="saas-btn saas-btn--primary"
                  onClick={() => setShowAddUserModal(true)}
                >
                  <UserPlus size={15} />
                  Add Officer / Staff
                </button>
              )}
            </>
          }
        />

        <AlertBanner
          type="error"
          message={error}
          action={
            error && (
              <button
                className="saas-btn saas-btn--sm saas-btn--secondary"
                type="button"
                onClick={loadData}
                disabled={loading}
              >
                Try again
              </button>
            )
          }
        />
        <AlertBanner type="error" message={actionError} onClose={() => setActionError("")} />
        <AlertBanner type="success" message={successMessage} onClose={() => setSuccessMessage("")} />

          {/* Role Filter Tabs & Search */}
          <div className="admin-dash-toolbar" style={{ marginBottom: "18px" }}>
            <div className="admin-dash-toolbar__search-group">
              <Search size={16} className="admin-dash-toolbar__search-icon" />
              <input
                type="text"
                className="admin-dash-toolbar__input"
                placeholder={
                  activeRoleFilter === "BENEFICIARY"
                    ? "Search citizen by name, email, phone, state, or category..."
                    : "Search officer by name, email, phone, or jurisdiction..."
                }
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
              />
            </div>

            <div className="admin-dash-toolbar__filters">
              <select
                className="admin-dash-toolbar__select"
                value={activeRoleFilter}
                onChange={(e) => setActiveRoleFilter(e.target.value)}
              >
                <option value="BENEFICIARY">
                  Citizens / Beneficiaries ({beneficiaries.length})
                </option>
                <option value="FIELD_OFFICER">
                  Field Officers (Stage 1)
                </option>
                <option value="DISTRICT_OFFICER">
                  District Officers (Stage 2)
                </option>
                <option value="FINANCE_OFFICER">
                  Finance Officers (Stage 3)
                </option>
                <option value="ALL_OFFICERS">All Verification Officers</option>
                <option value="ADMIN">System Administrators</option>
              </select>

              {searchQuery && (
                <button
                  type="button"
                  className="admin-dash-btn admin-dash-btn--outline admin-dash-btn--small"
                  onClick={() => setSearchQuery("")}
                >
                  <X size={14} />
                  Reset
                </button>
              )}
            </div>
          </div>

          {/* TABLE: CITIZEN BENEFICIARIES */}
          {activeRoleFilter === "BENEFICIARY" ? (
            <div className="admin-dashboard__table-wrap">
              <table className="admin-dashboard__table">
                <thead>
                  <tr>
                    <th scope="col">Name</th>
                    <th scope="col">Email</th>
                    <th scope="col">Phone</th>
                    <th scope="col">State</th>
                    <th scope="col">District</th>
                    <th scope="col">Category</th>
                    <th scope="col">Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {loading ? (
                    <tr>
                      <td className="admin-dashboard__table-message" colSpan="7">
                        Loading beneficiaries...
                      </td>
                    </tr>
                  ) : filteredBeneficiaries.length === 0 ? (
                    <tr>
                      <td className="admin-dashboard__table-message" colSpan="7">
                        No registered beneficiaries found.
                      </td>
                    </tr>
                  ) : (
                    filteredBeneficiaries.map((beneficiary) => (
                      <tr key={beneficiary.id}>
                        <td>
                          <strong>{beneficiary.fullName || "—"}</strong>
                        </td>
                        <td>{beneficiary.email || "—"}</td>
                        <td>{beneficiary.phone || "—"}</td>
                        <td>{beneficiary.state || "—"}</td>
                        <td>{beneficiary.district || "—"}</td>
                        <td>{beneficiary.category || "—"}</td>
                        <td>
                          <div className="admin-dashboard__actions">
                            <button
                              className="admin-dashboard__secondary-button"
                              type="button"
                              onClick={() =>
                                handleViewBeneficiaryDetails(beneficiary.id)
                              }
                            >
                              View details
                            </button>
                            <button
                              className="admin-dashboard__delete-button"
                              type="button"
                              onClick={() => handleDeleteBeneficiary(beneficiary)}
                              disabled={deletingId === beneficiary.id}
                            >
                              {deletingId === beneficiary.id
                                ? "Deleting..."
                                : "Delete"}
                            </button>
                          </div>
                        </td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </div>
          ) : (
            /* TABLE: OFFICERS AND SYSTEM USERS */
            <div className="admin-dashboard__table-wrap">
              <table className="admin-dashboard__table">
                <thead>
                  <tr>
                    <th scope="col">Name</th>
                    <th scope="col">Email &amp; Phone</th>
                    <th scope="col">Role</th>
                    <th scope="col">Officer Level</th>
                    <th scope="col">Jurisdiction</th>
                    <th scope="col">Status</th>
                    <th scope="col">Registered</th>
                    <th scope="col">Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {loading ? (
                    <tr>
                      <td className="admin-dashboard__table-message" colSpan="8">
                        Loading users...
                      </td>
                    </tr>
                  ) : filteredUsers.length === 0 ? (
                    <tr>
                      <td className="admin-dashboard__table-message" colSpan="8">
                        No users found in this category.
                      </td>
                    </tr>
                  ) : (
                    filteredUsers.map((u) => (
                      <tr key={u.id}>
                        <td>
                          <strong>{u.fullName}</strong>
                        </td>
                        <td>
                          <div style={{ display: "flex", flexDirection: "column", gap: "2px" }}>
                            <span>{u.email}</span>
                            <span style={{ fontSize: "0.78rem", color: "var(--color-text-muted)" }}>
                              {u.phone || "No phone"}
                            </span>
                          </div>
                        </td>
                        <td>
                          <span className="dash-badge dash-badge--neutral">
                            {u.role}
                          </span>
                        </td>
                        <td>
                          {u.officerLevel ? (
                            <span className="dash-badge dash-badge--info">
                              {u.officerLevel}
                            </span>
                          ) : (
                            "—"
                          )}
                        </td>
                        <td>
                          {u.jurisdictionState ? (
                            <span style={{ fontSize: "0.85rem" }}>
                              {u.jurisdictionDistrict
                                ? `${u.jurisdictionDistrict}, ${u.jurisdictionState}`
                                : u.jurisdictionState}
                            </span>
                          ) : (
                            "—"
                          )}
                        </td>
                        <td>
                          <span
                            className={`dash-badge ${
                              u.active !== false
                                ? "dash-badge--success"
                                : "dash-badge--muted"
                            }`}
                          >
                            {u.active !== false ? "Active" : "Inactive"}
                          </span>
                        </td>
                        <td>
                          <span style={{ fontSize: "0.8rem", color: "var(--color-text-muted)" }}>
                            {formatDate(u.createdAt)}
                          </span>
                        </td>
                        <td>
                          <div className="admin-dashboard__actions">
                            <button
                              className="admin-dashboard__secondary-button"
                              type="button"
                              onClick={() => handleToggleUserActive(u)}
                            >
                              {u.active !== false ? "Deactivate" : "Activate"}
                            </button>
                            <button
                              className="admin-dashboard__delete-button"
                              type="button"
                              onClick={() => handleDeleteUser(u)}
                            >
                              Delete
                            </button>
                          </div>
                        </td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </div>
          )}
        </section>

        {/* BENEFICIARY DETAILS DRAWER */}
        {(detailsLoading || detailsError || selectedBeneficiary) && (
          <section
            className="admin-dashboard__details"
            aria-labelledby="beneficiary-details-heading"
            aria-live="polite"
          >
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
              <h2 id="beneficiary-details-heading">Beneficiary details</h2>
              <button
                type="button"
                className="admin-modal__close-btn"
                onClick={() => setSelectedBeneficiary(null)}
              >
                <X size={18} />
              </button>
            </div>
            {detailsLoading && <p>Loading beneficiary details...</p>}
            {detailsError && (
              <p className="admin-dashboard__notice admin-dashboard__notice--error" role="alert">
                {detailsError}
              </p>
            )}
            {selectedBeneficiary && (
              <dl className="admin-dashboard__details-grid">
                <div>
                  <dt>Name</dt>
                  <dd>{selectedBeneficiary.fullName || "—"}</dd>
                </div>
                <div>
                  <dt>Email</dt>
                  <dd>{selectedBeneficiary.email || "—"}</dd>
                </div>
                <div>
                  <dt>Phone</dt>
                  <dd>{selectedBeneficiary.phone || "—"}</dd>
                </div>
                <div>
                  <dt>Date of birth</dt>
                  <dd>{selectedBeneficiary.dateOfBirth || "—"}</dd>
                </div>
                <div>
                  <dt>Annual income</dt>
                  <dd>
                    {selectedBeneficiary.annualIncome == null
                      ? "—"
                      : `₹${Number(selectedBeneficiary.annualIncome).toLocaleString("en-IN")}`}
                  </dd>
                </div>
                <div>
                  <dt>Category</dt>
                  <dd>{selectedBeneficiary.category || "—"}</dd>
                </div>
                <div>
                  <dt>Address</dt>
                  <dd>{selectedBeneficiary.address || "—"}</dd>
                </div>
                <div>
                  <dt>District</dt>
                  <dd>{selectedBeneficiary.district || "—"}</dd>
                </div>
                <div>
                  <dt>State</dt>
                  <dd>{selectedBeneficiary.state || "—"}</dd>
                </div>
              </dl>
            )}
          </section>
        )}

      {/* ADD OFFICER MODAL */}
      {showAddUserModal && (
        <div
          className="admin-modal-overlay"
          role="dialog"
          aria-modal="true"
          onClick={() => setShowAddUserModal(false)}
        >
          <div className="admin-modal" onClick={(e) => e.stopPropagation()}>
            <div className="admin-modal__header">
              <h3>Create New Verification Officer or Staff</h3>
              <button
                type="button"
                className="admin-modal__close-btn"
                onClick={() => setShowAddUserModal(false)}
              >
                <X size={18} />
              </button>
            </div>

            <form onSubmit={handleCreateUserSubmit}>
              <div className="admin-modal__body">
                {userFormError && (
                  <div
                    style={{
                      padding: "10px 14px",
                      background: "#fee2e2",
                      border: "1px solid #fecaca",
                      color: "#991b1b",
                      borderRadius: "8px",
                      fontSize: "0.85rem",
                    }}
                    role="alert"
                  >
                    {userFormError}
                  </div>
                )}

                <div className="admin-form-grid">
                  <div className="admin-form-group">
                    <label className="admin-form-label">Full Name *</label>
                    <input
                      type="text"
                      className="admin-form-input"
                      placeholder="e.g. Anand Sharma"
                      value={newUserForm.fullName}
                      onChange={(e) =>
                        setNewUserForm({ ...newUserForm, fullName: e.target.value })
                      }
                      required
                    />
                  </div>

                  <div className="admin-form-group">
                    <label className="admin-form-label">Email Address *</label>
                    <input
                      type="email"
                      className="admin-form-input"
                      placeholder="officer@grantora.gov.in"
                      value={newUserForm.email}
                      onChange={(e) =>
                        setNewUserForm({ ...newUserForm, email: e.target.value })
                      }
                      required
                    />
                  </div>

                  <div className="admin-form-group">
                    <label className="admin-form-label">Phone Number</label>
                    <input
                      type="tel"
                      className="admin-form-input"
                      placeholder="10-digit mobile"
                      value={newUserForm.phone}
                      onChange={(e) =>
                        setNewUserForm({ ...newUserForm, phone: e.target.value })
                      }
                    />
                  </div>

                  <div className="admin-form-group">
                    <label className="admin-form-label">Password *</label>
                    <input
                      type="password"
                      className="admin-form-input"
                      placeholder="Min 8 characters"
                      value={newUserForm.password}
                      onChange={(e) =>
                        setNewUserForm({ ...newUserForm, password: e.target.value })
                      }
                      required
                      minLength={8}
                    />
                  </div>

                  <div className="admin-form-group">
                    <label className="admin-form-label">Role *</label>
                    <select
                      className="admin-form-select"
                      value={newUserForm.role}
                      onChange={(e) =>
                        setNewUserForm({ ...newUserForm, role: e.target.value })
                      }
                    >
                      <option value="OFFICER">OFFICER</option>
                      <option value="ADMIN">ADMIN</option>
                    </select>
                  </div>

                  {newUserForm.role === "OFFICER" && (
                    <>
                      <div className="admin-form-group">
                        <label className="admin-form-label">Officer Level *</label>
                        <select
                          className="admin-form-select"
                          value={newUserForm.officerLevel}
                          onChange={(e) =>
                            setNewUserForm({
                              ...newUserForm,
                              officerLevel: e.target.value,
                            })
                          }
                        >
                          <option value="FIELD">FIELD (Stage 1)</option>
                          <option value="DISTRICT">DISTRICT (Stage 2)</option>
                          <option value="FINANCE">FINANCE (Stage 3)</option>
                        </select>
                      </div>

                      <div className="admin-form-group">
                        <label className="admin-form-label">Jurisdiction State *</label>
                        <input
                          type="text"
                          className="admin-form-input"
                          placeholder="State"
                          value={newUserForm.jurisdictionState}
                          onChange={(e) =>
                            setNewUserForm({
                              ...newUserForm,
                              jurisdictionState: e.target.value,
                            })
                          }
                          required
                        />
                      </div>

                      {newUserForm.officerLevel !== "FINANCE" && (
                        <div className="admin-form-group">
                          <label className="admin-form-label">Jurisdiction District *</label>
                          <input
                            type="text"
                            className="admin-form-input"
                            placeholder="District"
                            value={newUserForm.jurisdictionDistrict}
                            onChange={(e) =>
                              setNewUserForm({
                                ...newUserForm,
                                jurisdictionDistrict: e.target.value,
                              })
                            }
                            required
                          />
                        </div>
                      )}
                    </>
                  )}
                </div>
              </div>

              <div className="admin-modal__footer">
                <button
                  type="button"
                  className="admin-dash-btn admin-dash-btn--outline"
                  onClick={() => setShowAddUserModal(false)}
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="admin-dash-btn admin-dash-btn--primary"
                  disabled={submittingUser}
                >
                  {submittingUser ? "Creating..." : "Create Account"}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}

export default AdminDashboard;
