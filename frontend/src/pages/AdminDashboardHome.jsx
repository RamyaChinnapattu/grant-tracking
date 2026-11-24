import { useEffect, useState, useMemo } from "react";
import { Link } from "react-router-dom";
import {
  AlertCircle,
  ArrowRight,
  CheckCircle2,
  ClipboardList,
  Clock,
  Eye,
  FolderKanban,
  History,
  RefreshCw,
  Users,
  XCircle,
} from "lucide-react";
import { getApplications } from "../services/grantApplicationService";
import { getSchemes } from "../services/schemeService";
import { getBeneficiaries } from "../services/beneficiaryService";
import "../styles/DashboardHome.css";
import "../styles/AdminDashboardHome.css";

function formatCurrency(amount) {
  if (amount == null || isNaN(amount)) return "₹0";
  return `₹${Number(amount).toLocaleString("en-IN")}`;
}

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

function formatDateTime(dt) {
  if (!dt) return "—";
  try {
    const d = new Date(dt);
    return isNaN(d.getTime())
      ? "—"
      : `${d.toLocaleDateString("en-IN", {
          month: "short",
          day: "numeric",
        })}, ${d.toLocaleTimeString("en-IN", {
          hour: "2-digit",
          minute: "2-digit",
        })}`;
  } catch {
    return "—";
  }
}

function getStatusBadge(status) {
  switch (status) {
    case "APPROVED":
      return <span className="dash-badge dash-badge--success">Approved</span>;
    case "REJECTED":
      return <span className="dash-badge dash-badge--danger">Rejected</span>;
    case "NEEDS_CORRECTION":
      return (
        <span className="dash-badge dash-badge--warning">Needs Correction</span>
      );
    case "DRAFT":
      return <span className="dash-badge dash-badge--neutral">Draft</span>;
    case "WITHDRAWN":
      return <span className="dash-badge dash-badge--muted">Withdrawn</span>;
    case "FIELD_VERIFICATION":
    case "SUBMITTED":
    case "UNDER_VERIFICATION":
      return (
        <span className="dash-badge dash-badge--info">
          Stage 1: Field Review
        </span>
      );
    case "DISTRICT_REVIEW":
      return (
        <span className="dash-badge dash-badge--info">
          Stage 2: District Review
        </span>
      );
    case "FINANCE_REVIEW":
      return (
        <span className="dash-badge dash-badge--info">
          Stage 3: Finance Approval
        </span>
      );
    default:
      return <span className="dash-badge dash-badge--info">Under Review</span>;
  }
}

function AdminDashboardHome() {
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState("");

  const [applications, setApplications] = useState([]);
  const [schemes, setSchemes] = useState([]);
  const [beneficiaries, setBeneficiaries] = useState([]);

  const loadData = async (isManual = false) => {
    if (isManual) setRefreshing(true);
    else setLoading(true);
    setError("");

    try {
      const [appsRes, schemesRes, benefsRes] = await Promise.all([
        getApplications(),
        getSchemes(),
        getBeneficiaries(),
      ]);

      setApplications(appsRes.data || []);
      setSchemes(schemesRes.data || []);
      setBeneficiaries(benefsRes.data || []);
    } catch (err) {
      console.error("Dashboard data sync error:", err);
      setError(
        err.response?.data?.message ||
          "Unable to load administrative dashboard data. Please try again."
      );
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  };

  useEffect(() => {
    loadData();
  }, []);

  // Strict, mutually exclusive metric computations
  const totalSchemes = schemes.length;
  const activeSchemes = useMemo(
    () => schemes.filter((s) => s.status === "ACTIVE").length,
    [schemes]
  );
  const totalBeneficiaries = beneficiaries.length;
  const totalApplications = applications.length;

  // Approved grants strictly count status === 'APPROVED'
  const approvedApplications = useMemo(
    () => applications.filter((a) => a.status === "APPROVED").length,
    [applications]
  );

  // Rejected grants strictly count status === 'REJECTED'
  const rejectedApplications = useMemo(
    () => applications.filter((a) => a.status === "REJECTED").length,
    [applications]
  );

  // Verification stage pending queues:
  // Approved, rejected, withdrawn, and drafts are strictly excluded!
  const pendingFieldApps = useMemo(
    () =>
      applications.filter(
        (a) =>
          a.status === "FIELD_VERIFICATION" ||
          a.status === "SUBMITTED" ||
          a.status === "UNDER_VERIFICATION"
      ),
    [applications]
  );

  const pendingDistrictApps = useMemo(
    () => applications.filter((a) => a.status === "DISTRICT_REVIEW"),
    [applications]
  );

  const pendingFinanceApps = useMemo(
    () => applications.filter((a) => a.status === "FINANCE_REVIEW"),
    [applications]
  );

  // Total pending is the sum of the active review stages
  const pendingApplications =
    pendingFieldApps.length +
    pendingDistrictApps.length +
    pendingFinanceApps.length;

  // Recent applications (latest 5)
  const recentApplications = useMemo(
    () => applications.slice(0, 5),
    [applications]
  );

  // Recent administrative activity (latest 5 deduplicated events)
  const recentActivity = useMemo(() => {
    const list = [];
    const seen = new Set();

    applications.forEach((app) => {
      (app.statusHistory || []).forEach((hist) => {
        const key = `${app.id}-${hist.id || ""}-${hist.action || ""}-${hist.status || ""}-${hist.createdAt || ""}`;
        if (seen.has(key)) return;
        seen.add(key);

        list.push({
          id: key,
          applicationId: app.id,
          schemeName: app.schemeName,
          beneficiaryName: app.beneficiaryName,
          action: hist.action || "STATUS_CHANGE",
          fromStatus: hist.fromStatus,
          toStatus: hist.status || hist.toStatus,
          actorName: hist.actorName || "Reviewing Officer",
          remarks: hist.remarks || "",
          createdAt: hist.createdAt,
        });
      });
    });

    list.sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt));
    return list.slice(0, 5);
  }, [applications]);

  const name = localStorage.getItem("fullName")?.trim().split(/\s+/)[0];

  return (
    <div className="admin-dash">
      {/* Welcome Banner */}
      <div className="dashboard-home__welcome">
        <div className="dashboard-home__welcome-top">
          <div>
            <span className="dashboard-home__eyebrow">
              Administrator Portal
            </span>
            <h2>Welcome back{name ? `, ${name}` : ""}</h2>
            <p>
              Overview of Grantora subsidy schemes, citizen applications, and
              verification workflow progress.
            </p>
          </div>
          <button
            className="dash-refresh-btn"
            type="button"
            onClick={() => loadData(true)}
            disabled={loading || refreshing}
          >
            <RefreshCw
              size={15}
              className={loading || refreshing ? "is-spinning" : ""}
            />
            {refreshing ? "Refreshing..." : "Refresh"}
          </button>
        </div>
      </div>

      {error && (
        <div className="dash-error-banner" role="alert">
          <span>{error}</span>
          <button type="button" onClick={() => loadData(true)}>
            Try again
          </button>
        </div>
      )}

      {/* Summary Cards */}
      <div className="dash-stats-grid">
        <Link
          to="/admin/schemes"
          className="dash-stat-card"
          style={{ textDecoration: "none" }}
        >
          <div className="dash-stat-card__icon dash-stat-card__icon--info">
            <FolderKanban size={22} />
          </div>
          <div className="dash-stat-card__data">
            <span className="dash-stat-card__label">Total Schemes</span>
            <strong className="dash-stat-card__value">
              {loading ? "..." : totalSchemes}
            </strong>
            <span className="dash-stat-card__subtext">
              {loading ? "..." : `${activeSchemes} active programs`}
            </span>
          </div>
        </Link>

        <Link
          to="/admin/schemes"
          className="dash-stat-card"
          style={{ textDecoration: "none" }}
        >
          <div className="dash-stat-card__icon dash-stat-card__icon--success">
            <CheckCircle2 size={22} />
          </div>
          <div className="dash-stat-card__data">
            <span className="dash-stat-card__label">Active Schemes</span>
            <strong className="dash-stat-card__value">
              {loading ? "..." : activeSchemes}
            </strong>
            <span className="dash-stat-card__subtext">Open for enrollment</span>
          </div>
        </Link>

        <Link
          to="/admin/beneficiaries"
          className="dash-stat-card"
          style={{ textDecoration: "none" }}
        >
          <div className="dash-stat-card__icon dash-stat-card__icon--primary">
            <Users size={22} />
          </div>
          <div className="dash-stat-card__data">
            <span className="dash-stat-card__label">Total Beneficiaries</span>
            <strong className="dash-stat-card__value">
              {loading ? "..." : totalBeneficiaries}
            </strong>
            <span className="dash-stat-card__subtext">Registered citizens</span>
          </div>
        </Link>

        <Link
          to="/admin/applications"
          className="dash-stat-card"
          style={{ textDecoration: "none" }}
        >
          <div className="dash-stat-card__icon dash-stat-card__icon--primary">
            <ClipboardList size={22} />
          </div>
          <div className="dash-stat-card__data">
            <span className="dash-stat-card__label">Total Applications</span>
            <strong className="dash-stat-card__value">
              {loading ? "..." : totalApplications}
            </strong>
            <span className="dash-stat-card__subtext">All submissions</span>
          </div>
        </Link>

        <Link
          to="/admin/verification"
          className="dash-stat-card"
          style={{ textDecoration: "none" }}
        >
          <div className="dash-stat-card__icon dash-stat-card__icon--warning">
            <Clock size={22} />
          </div>
          <div className="dash-stat-card__data">
            <span className="dash-stat-card__label">Pending Applications</span>
            <strong className="dash-stat-card__value">
              {loading ? "..." : pendingApplications}
            </strong>
            <span className="dash-stat-card__subtext">Under officer review</span>
          </div>
        </Link>

        <Link
          to="/admin/applications"
          state={{ statusFilter: "APPROVED" }}
          className="dash-stat-card"
          style={{ textDecoration: "none" }}
        >
          <div className="dash-stat-card__icon dash-stat-card__icon--success">
            <CheckCircle2 size={22} />
          </div>
          <div className="dash-stat-card__data">
            <span className="dash-stat-card__label">Approved Applications</span>
            <strong className="dash-stat-card__value">
              {loading ? "..." : approvedApplications}
            </strong>
            <span className="dash-stat-card__subtext">Finance authorized</span>
          </div>
        </Link>

        <Link
          to="/admin/applications"
          state={{ statusFilter: "REJECTED" }}
          className="dash-stat-card"
          style={{ textDecoration: "none" }}
        >
          <div className="dash-stat-card__icon dash-stat-card__icon--danger">
            <XCircle size={22} />
          </div>
          <div className="dash-stat-card__data">
            <span className="dash-stat-card__label">Rejected Applications</span>
            <strong className="dash-stat-card__value">
              {loading ? "..." : rejectedApplications}
            </strong>
            <span className="dash-stat-card__subtext">Declined applications</span>
          </div>
        </Link>
      </div>

      {/* Small Pending Verification Summary for Field, District, Finance */}
      <div className="dash-section">
        <div className="dash-section__header">
          <h3 className="dash-section__title">Pending Verification Summary</h3>
          <Link to="/admin/verification" className="dash-section__link">
            Open verifications
            <ArrowRight size={15} />
          </Link>
        </div>

        <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(220px, 1fr))", gap: "14px" }}>
          <Link
            to="/admin/verification"
            className="pipeline-step-item"
            style={{
              textDecoration: "none",
              background: "var(--color-surface-lowest)",
              borderLeft: "4px solid #2563eb",
              padding: "16px",
            }}
          >
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
              <strong style={{ fontSize: "0.88rem", color: "#2563eb" }}>
                Stage 1: Field Review
              </strong>
              <span className="dash-badge dash-badge--info">
                {pendingFieldApps.length} pending
              </span>
            </div>
            <p style={{ margin: "6px 0 0", fontSize: "0.78rem", color: "var(--color-text-muted)" }}>
              Awaiting physical &amp; document eligibility verification
            </p>
          </Link>

          <Link
            to="/admin/verification"
            className="pipeline-step-item"
            style={{
              textDecoration: "none",
              background: "var(--color-surface-lowest)",
              borderLeft: "4px solid #7c3aed",
              padding: "16px",
            }}
          >
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
              <strong style={{ fontSize: "0.88rem", color: "#7c3aed" }}>
                Stage 2: District Review
              </strong>
              <span className="dash-badge dash-badge--warning">
                {pendingDistrictApps.length} pending
              </span>
            </div>
            <p style={{ margin: "6px 0 0", fontSize: "0.78rem", color: "var(--color-text-muted)" }}>
              Awaiting district quota &amp; eligibility endorsement
            </p>
          </Link>

          <Link
            to="/admin/verification"
            className="pipeline-step-item"
            style={{
              textDecoration: "none",
              background: "var(--color-surface-lowest)",
              borderLeft: "4px solid #d97706",
              padding: "16px",
            }}
          >
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
              <strong style={{ fontSize: "0.88rem", color: "#d97706" }}>
                Stage 3: Finance Approval
              </strong>
              <span className="dash-badge dash-badge--success">
                {pendingFinanceApps.length} pending
              </span>
            </div>
            <p style={{ margin: "6px 0 0", fontSize: "0.78rem", color: "var(--color-text-muted)" }}>
              Awaiting final grant sanction &amp; disbursement authorization
            </p>
          </Link>
        </div>
      </div>

      {/* Two Column Layout: Recent Applications & Recent Administrative Activity */}
      <div className="dash-split-section">
        {/* Left: Recent Applications Table */}
        <div className="dash-section">
          <div className="dash-section__header">
            <h3 className="dash-section__title">Recent Applications</h3>
            <Link to="/admin/applications" className="dash-section__link">
              View all
              <ArrowRight size={15} />
            </Link>
          </div>

          <div className="dash-table-wrap">
            <table className="dash-table">
              <thead>
                <tr>
                  <th>App ID</th>
                  <th>Applicant</th>
                  <th>Scheme</th>
                  <th>Amount</th>
                  <th>Status</th>
                  <th>Date</th>
                </tr>
              </thead>
              <tbody>
                {recentApplications.length === 0 ? (
                  <tr>
                    <td colSpan="6" style={{ textAlign: "center", padding: "24px" }}>
                      No applications recorded yet.
                    </td>
                  </tr>
                ) : (
                  recentApplications.map((app) => (
                    <tr key={app.id}>
                      <td>
                        <strong>#{app.id}</strong>
                      </td>
                      <td>{app.beneficiaryName || "—"}</td>
                      <td>
                        <span style={{ fontWeight: 600 }}>{app.schemeName}</span>
                      </td>
                      <td>{formatCurrency(app.requestedAmount)}</td>
                      <td>{getStatusBadge(app.status)}</td>
                      <td>
                        <span style={{ fontSize: "0.78rem", color: "var(--color-text-muted)" }}>
                          {formatDate(app.submittedAt)}
                        </span>
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        </div>

        {/* Right: Recent Administrative Activity */}
        <div className="dash-section dash-split-section__side">
          <div className="dash-section__header">
            <h3 className="dash-section__title">Recent Activity</h3>
            <Link to="/admin/audit-logs" className="dash-section__link">
              Audit history
              <ArrowRight size={15} />
            </Link>
          </div>

          <div className="dash-notif-preview-card">
            {recentActivity.length === 0 ? (
              <div className="dash-notif-empty">
                <History size={24} style={{ color: "var(--color-text-muted)" }} />
                <p>No recent activity.</p>
                <span>Administrative and review actions will appear here.</span>
              </div>
            ) : (
              <div className="dash-notif-preview-list">
                {recentActivity.map((item) => (
                  <div key={item.id} className="dash-notif-preview-item">
                    <div className="dash-notif-preview-item__top">
                      <span
                        className="dash-badge dash-badge--neutral"
                        style={{ fontSize: "0.7rem", padding: "1px 6px" }}
                      >
                        App #{item.applicationId} • {item.action}
                      </span>
                      <span className="dash-notif-preview-item__time">
                        {formatDateTime(item.createdAt)}
                      </span>
                    </div>
                    <p className="dash-notif-preview-item__msg">
                      <strong>{item.actorName}</strong>: {item.beneficiaryName} ({item.schemeName})
                    </p>
                    {item.remarks && (
                      <span style={{ fontSize: "0.74rem", fontStyle: "italic", color: "var(--color-text-muted)" }}>
                        &ldquo;{item.remarks}&rdquo;
                      </span>
                    )}
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}

export default AdminDashboardHome;
