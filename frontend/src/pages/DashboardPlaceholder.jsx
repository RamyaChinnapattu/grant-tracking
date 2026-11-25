import { useCallback, useEffect, useState } from "react";
import { Construction, Mail, MapPin, ShieldCheck, UserRound } from "lucide-react";
import { Link } from "react-router-dom";
import { getDisbursementEligibleApplications } from "../services/grantApplicationService";
import { dashboardHomePaths, roleLabels } from "../components/dashboardNavigation";
import "../styles/DashboardHome.css";

function DashboardPlaceholder({ title }) {
  const isDisbursement =
    title === "Grant Disbursement" || title === "Grant Disbursement & Compliance";
  const [applications, setApplications] = useState([]);
  const [page, setPage] = useState(0);
  const [pageInfo, setPageInfo] = useState({ totalPages: 0 });
  const [loading, setLoading] = useState(isDisbursement);
  const [error, setError] = useState("");

  const loadEligibleApplications = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const response = await getDisbursementEligibleApplications({
        page,
        size: 20,
      });
      setApplications(response.data.content);
      setPageInfo({ totalPages: response.data.totalPages });
    } catch (requestError) {
      setError(
        requestError.response?.data?.message ||
          requestError.response?.data?.error ||
          "Unable to load applications approved for disbursement."
      );
    } finally {
      setLoading(false);
    }
  }, [page]);

  useEffect(() => {
    if (isDisbursement) loadEligibleApplications();
  }, [isDisbursement, loadEligibleApplications]);

  if (title === "Profile") {
    const fullName = localStorage.getItem("fullName") || "Grantora User";
    const email = localStorage.getItem("email") || "—";
    const role = localStorage.getItem("role") || "ADMIN";
    const officerLevel = localStorage.getItem("officerLevel") || "";
    const jurisdictionState = localStorage.getItem("jurisdictionState") || "";
    const jurisdictionDistrict = localStorage.getItem("jurisdictionDistrict") || "";
    const homePath = dashboardHomePaths[role] || "/";

    return (
      <div className="saas-page" style={{ maxWidth: "820px" }}>
        <div className="saas-page-header">
          <div>
            <span className="saas-page-header__eyebrow">Account Settings</span>
            <h1 className="saas-page-header__title">My Account Profile</h1>
            <p className="saas-page-header__subtitle">
              Authenticated identity, role assignment, and jurisdiction details for your active session.
            </p>
          </div>
          <Link to={homePath} className="saas-btn saas-btn--secondary">
            Back to Dashboard
          </Link>
        </div>

        <section className="saas-panel" style={{ display: "grid", gap: "20px" }}>
          <div style={{ display: "flex", alignItems: "center", gap: "16px", flexWrap: "wrap" }}>
            <div
              style={{
                width: 56,
                height: 56,
                borderRadius: "14px",
                background: "var(--color-primary)",
                color: "#fff",
                display: "grid",
                placeItems: "center",
                fontSize: "1.25rem",
                fontWeight: 750,
              }}
            >
              {fullName
                .split(/\s+/)
                .filter(Boolean)
                .slice(0, 2)
                .map((p) => p[0].toUpperCase())
                .join("") || "G"}
            </div>
            <div>
              <h2 style={{ margin: 0, fontSize: "1.2rem" }}>{fullName}</h2>
              <div style={{ display: "flex", gap: "8px", alignItems: "center", marginTop: "6px", flexWrap: "wrap" }}>
                <span className="saas-badge saas-badge--info">
                  <ShieldCheck size={13} />
                  {roleLabels[role] || role}
                </span>
                {officerLevel && (
                  <span className="saas-badge saas-badge--teal">
                    {officerLevel} Officer
                  </span>
                )}
              </div>
            </div>
          </div>

          <div
            style={{
              display: "grid",
              gridTemplateColumns: "repeat(auto-fit, minmax(220px, 1fr))",
              gap: "14px",
              paddingTop: "14px",
              borderTop: "1px solid var(--color-outline-variant)",
            }}
          >
            <div>
              <div style={{ fontSize: "0.75rem", fontWeight: 600, color: "var(--color-text-muted)", display: "flex", alignItems: "center", gap: "6px" }}>
                <UserRound size={14} /> Full Name
              </div>
              <div style={{ marginTop: "4px", fontWeight: 600 }}>{fullName}</div>
            </div>
            <div>
              <div style={{ fontSize: "0.75rem", fontWeight: 600, color: "var(--color-text-muted)", display: "flex", alignItems: "center", gap: "6px" }}>
                <Mail size={14} /> Email Address
              </div>
              <div style={{ marginTop: "4px", fontWeight: 600 }}>{email}</div>
            </div>
            {(jurisdictionDistrict || jurisdictionState) && (
              <div>
                <div style={{ fontSize: "0.75rem", fontWeight: 600, color: "var(--color-text-muted)", display: "flex", alignItems: "center", gap: "6px" }}>
                  <MapPin size={14} /> Assigned Jurisdiction
                </div>
                <div style={{ marginTop: "4px", fontWeight: 600 }}>
                  {[jurisdictionDistrict, jurisdictionState].filter(Boolean).join(", ")}
                </div>
              </div>
            )}
          </div>
        </section>
      </div>
    );
  }

  if (isDisbursement) {
    return (
      <section className="dashboard-placeholder" aria-labelledby="disbursement-heading">
        <div>
          <h2 id="disbursement-heading">{title}</h2>
          <p>Approved grant applications eligible for disbursement and compliance auditing.</p>
        </div>
        <button type="button" onClick={loadEligibleApplications} disabled={loading}>
          {loading ? "Refreshing…" : "Refresh"}
        </button>
        {error && (
          <p role="alert">
            {error}{" "}
            <button type="button" onClick={loadEligibleApplications} disabled={loading}>
              Try again
            </button>
          </p>
        )}
        {loading ? (
          <p>Loading approved applications…</p>
        ) : applications.length === 0 ? (
          <p>No applications are currently approved for disbursement.</p>
        ) : (
          <>
            <div className="grant-applications__table-wrap">
              <table className="grant-applications__table">
                <thead>
                  <tr>
                    <th>Application</th>
                    <th>Beneficiary</th>
                    <th>Scheme</th>
                    <th>Approved amount</th>
                    <th>Eligibility</th>
                    <th>Final remarks</th>
                  </tr>
                </thead>
                <tbody>
                  {applications.map((application) => {
                    const approval = [...(application.statusHistory || [])]
                      .reverse()
                      .find((item) => item.toStatus === "APPROVED");
                    return (
                      <tr key={application.id}>
                        <td>#{application.id}</td>
                        <td>{application.beneficiaryName}</td>
                        <td>{application.schemeName}</td>
                        <td>
                          {new Intl.NumberFormat("en-IN", {
                            style: "currency",
                            currency: "INR",
                          }).format(application.requestedAmount || 0)}
                        </td>
                        <td>
                          {application.eligibilityEvaluation?.eligibilityStatus || "—"} ·{" "}
                          {application.eligibilityEvaluation?.scoreOutOf100 ?? "—"}%
                        </td>
                        <td>{approval?.remarks || "—"}</td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
            {pageInfo.totalPages > 1 && (
              <nav aria-label="Disbursement application pages">
                <button
                  type="button"
                  onClick={() => setPage((current) => Math.max(0, current - 1))}
                  disabled={page === 0 || loading}
                >
                  Previous
                </button>
                <span>
                  Page {page + 1} of {pageInfo.totalPages}
                </span>
                <button
                  type="button"
                  onClick={() =>
                    setPage((current) => Math.min(pageInfo.totalPages - 1, current + 1))
                  }
                  disabled={page + 1 >= pageInfo.totalPages || loading}
                >
                  Next
                </button>
              </nav>
            )}
          </>
        )}
      </section>
    );
  }

  return (
    <section className="dashboard-placeholder">
      <span className="dashboard-placeholder__icon" aria-hidden="true">
        <Construction size={24} />
      </span>
      <div>
        <h2>{title}</h2>
        <p>
          This workspace is not available yet. It will appear here when the
          feature is implemented.
        </p>
      </div>
    </section>
  );
}

export default DashboardPlaceholder;
