import { useEffect, useState } from "react";
import {
  ArrowRight,
  CheckCircle2,
  Clock,
  AlertCircle,
  XCircle,
  FilePlus2,
  FolderKanban,
  ClipboardList,
  UserRound,
  Bell,
  RefreshCw,
  FileText,
  ChevronRight,
  Info,
  ExternalLink,
} from "lucide-react";
import { Link } from "react-router-dom";
import {
  dashboardNavigation,
  roleLabels,
} from "../components/dashboardNavigation";
import { getMyApplications } from "../services/grantApplicationService";
import { getNotifications } from "../services/notificationService";
import AdminDashboardHome from "./AdminDashboardHome";
import OfficerMilestone3Dashboard from "./OfficerMilestone3Dashboard";
import FieldOfficerDashboard from "./FieldOfficerDashboard";
import DistrictOfficerDashboard from "./DistrictOfficerDashboard";
import FinanceOfficerDashboard from "./FinanceOfficerDashboard";
import "../styles/DashboardHome.css";

function BeneficiaryDashboardHome() {
  const [applications, setApplications] = useState([]);
  const [notifications, setNotifications] = useState([]);
  const [unreadNotifCount, setUnreadNotifCount] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const storedName = localStorage.getItem("fullName")?.trim();
  const firstName = storedName ? storedName.split(/\s+/)[0] : "Beneficiary";

  const loadData = async () => {
    setLoading(true);
    setError("");
    try {
      const [appsRes, notifsRes] = await Promise.all([
        getMyApplications(),
        getNotifications().catch(() => ({ data: { items: [], unreadCount: 0 } })),
      ]);

      const apps = Array.isArray(appsRes.data) ? appsRes.data : [];
      setApplications(apps);

      const notifs = notifsRes.data?.items || [];
      setNotifications(notifs);
      setUnreadNotifCount(
        notifsRes.data?.unreadCount ?? notifs.filter((n) => !n.readAt).length
      );
    } catch (err) {
      setError(
        err.response?.data?.message ||
          "Unable to load application dashboard data. Please try refreshing."
      );
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadData();
  }, []);

  const totalApps = applications.length;
  const approvedApps = applications.filter(
    (a) => a.status === "APPROVED"
  ).length;
  const underReviewApps = applications.filter((a) =>
    [
      "SUBMITTED",
      "UNDER_VERIFICATION",
      "FIELD_VERIFICATION",
      "DISTRICT_REVIEW",
      "FINANCE_REVIEW",
      "UNDER_REVIEW",
    ].includes(a.status)
  ).length;
  const needsCorrectionApps = applications.filter(
    (a) => a.status === "NEEDS_CORRECTION"
  ).length;
  const rejectedApps = applications.filter(
    (a) => a.status === "REJECTED"
  ).length;

  const recentApplications = applications.slice(0, 5);
  const recentNotifications = notifications.slice(0, 3);

  const getStatusBadge = (status, stage) => {
    switch (status) {
      case "APPROVED":
        return <span className="dash-badge dash-badge--success">Approved</span>;
      case "NEEDS_CORRECTION":
        return (
          <span className="dash-badge dash-badge--warning">
            Action Required
          </span>
        );
      case "REJECTED":
        return <span className="dash-badge dash-badge--danger">Rejected</span>;
      case "DRAFT":
        return <span className="dash-badge dash-badge--neutral">Draft</span>;
      case "WITHDRAWN":
        return <span className="dash-badge dash-badge--muted">Withdrawn</span>;
      default: {
        let label = "Under Review";
        if (
          stage === "FIELD" ||
          status === "FIELD_VERIFICATION" ||
          status === "SUBMITTED" ||
          status === "UNDER_VERIFICATION"
        ) {
          label = "Stage 1: Field Review";
        } else if (stage === "DISTRICT" || status === "DISTRICT_REVIEW") {
          label = "Stage 2: District Review";
        } else if (stage === "FINANCE" || status === "FINANCE_REVIEW") {
          label = "Stage 3: Finance Approval";
        }
        return <span className="dash-badge dash-badge--info">{label}</span>;
      }
    }
  };

  return (
    <section className="dashboard-home">
      {/* Welcome Banner */}
      <div className="dashboard-home__welcome">
        <div className="dashboard-home__welcome-top">
          <span className="dashboard-home__eyebrow">
            Beneficiary Portal
          </span>
          <button
            className="dash-refresh-btn"
            type="button"
            onClick={loadData}
            disabled={loading}
            title="Refresh dashboard stats and notifications"
          >
            <RefreshCw size={15} className={loading ? "is-spinning" : ""} />
            <span>{loading ? "Refreshing..." : "Refresh"}</span>
          </button>
        </div>
        <h2>Welcome back, {firstName}</h2>
        <p>
          Track your grant applications, review officer verification decisions, and explore available subsidy programs.
        </p>
      </div>

      {error && (
        <div className="dash-error-banner" role="alert">
          <span>{error}</span>
          <button type="button" onClick={loadData}>
            Try again
          </button>
        </div>
      )}

      {/* 5-Metric Summary Grid */}
      <div className="dash-stats-grid">
        <div className="dash-stat-card">
          <div className="dash-stat-card__icon dash-stat-card__icon--primary">
            <ClipboardList size={22} />
          </div>
          <div className="dash-stat-card__data">
            <span className="dash-stat-card__label">Total Applications</span>
            <strong className="dash-stat-card__value">
              {loading ? "..." : totalApps}
            </strong>
            <span className="dash-stat-card__subtext">All submitted &amp; drafts</span>
          </div>
        </div>

        <div className="dash-stat-card">
          <div className="dash-stat-card__icon dash-stat-card__icon--success">
            <CheckCircle2 size={22} />
          </div>
          <div className="dash-stat-card__data">
            <span className="dash-stat-card__label">Approved Grants</span>
            <strong className="dash-stat-card__value">
              {loading ? "..." : approvedApps}
            </strong>
            <span className="dash-stat-card__subtext">Finance approved</span>
          </div>
        </div>

        <div className="dash-stat-card">
          <div className="dash-stat-card__icon dash-stat-card__icon--info">
            <Clock size={22} />
          </div>
          <div className="dash-stat-card__data">
            <span className="dash-stat-card__label">Under Review</span>
            <strong className="dash-stat-card__value">
              {loading ? "..." : underReviewApps}
            </strong>
            <span className="dash-stat-card__subtext">Field · District · Finance</span>
          </div>
        </div>

        <div
          className={`dash-stat-card ${
            needsCorrectionApps > 0 ? "dash-stat-card--highlight" : ""
          }`}
        >
          <div className="dash-stat-card__icon dash-stat-card__icon--warning">
            <AlertCircle size={22} />
          </div>
          <div className="dash-stat-card__data">
            <span className="dash-stat-card__label">Needs Correction</span>
            <strong className="dash-stat-card__value">
              {loading ? "..." : needsCorrectionApps}
            </strong>
            <span className="dash-stat-card__subtext">
              {needsCorrectionApps > 0 ? "Action required" : "No pending fixes"}
            </span>
          </div>
        </div>

        <div className="dash-stat-card">
          <div className="dash-stat-card__icon dash-stat-card__icon--danger">
            <XCircle size={22} />
          </div>
          <div className="dash-stat-card__data">
            <span className="dash-stat-card__label">Rejected</span>
            <strong className="dash-stat-card__value">
              {loading ? "..." : rejectedApps}
            </strong>
            <span className="dash-stat-card__subtext">Not approved</span>
          </div>
        </div>
      </div>

      {/* Quick Action Shortcuts */}
      <div className="dash-section">
        <h3 className="dash-section__title">Quick Actions</h3>
        <div className="dash-actions-grid">
          <Link to="/beneficiary/schemes" className="dash-action-card">
            <span className="dash-action-card__icon">
              <FolderKanban size={20} />
            </span>
            <div className="dash-action-card__copy">
              <strong>Browse Schemes</strong>
              <span>Explore active schemes and criteria</span>
            </div>
            <ArrowRight size={16} className="dash-action-card__arrow" />
          </Link>

          <Link to="/beneficiary/apply" className="dash-action-card">
            <span className="dash-action-card__icon">
              <FilePlus2 size={20} />
            </span>
            <div className="dash-action-card__copy">
              <strong>Apply for Grant</strong>
              <span>Start new application or complete draft</span>
            </div>
            <ArrowRight size={16} className="dash-action-card__arrow" />
          </Link>

          <Link to="/beneficiary/applications" className="dash-action-card">
            <span className="dash-action-card__icon">
              <ClipboardList size={20} />
            </span>
            <div className="dash-action-card__copy">
              <strong>My Applications</strong>
              <span>Track officer stages and decisions</span>
            </div>
            <ArrowRight size={16} className="dash-action-card__arrow" />
          </Link>

          <Link to="/beneficiary/profile" className="dash-action-card">
            <span className="dash-action-card__icon">
              <UserRound size={20} />
            </span>
            <div className="dash-action-card__copy">
              <strong>My Profile</strong>
              <span>Manage contact and jurisdiction</span>
            </div>
            <ArrowRight size={16} className="dash-action-card__arrow" />
          </Link>

          <Link to="/beneficiary/notifications" className="dash-action-card">
            <span className="dash-action-card__icon">
              <Bell size={20} />
            </span>
            <div className="dash-action-card__copy">
              <strong>Notifications</strong>
              <span>
                {unreadNotifCount > 0
                  ? `${unreadNotifCount} unread update${unreadNotifCount === 1 ? "" : "s"}`
                  : "View all notification history"}
              </span>
            </div>
            <ArrowRight size={16} className="dash-action-card__arrow" />
          </Link>
        </div>
      </div>

      {/* Notifications Preview & Recent Applications Section */}
      <div className="dash-split-section">
        {/* Left / Main: Recent Applications */}
        <div className="dash-section dash-split-section__main">
          <div className="dash-section__header">
            <h3 className="dash-section__title">Recent Applications</h3>
            {applications.length > 0 && (
              <Link
                to="/beneficiary/applications"
                className="dash-section__link"
              >
                View all ({applications.length})
                <ChevronRight size={16} />
              </Link>
            )}
          </div>

          {loading ? (
            <div className="dash-empty-card">
              <p>Loading recent applications...</p>
            </div>
          ) : recentApplications.length === 0 ? (
            <div className="dash-empty-card">
              <div className="dash-empty-card__icon">
                <FileText size={32} />
              </div>
              <h4>No grant applications yet</h4>
              <p>
                Explore government subsidies and grant programs designed for you. Check your eligibility and apply online.
              </p>
              <Link
                to="/beneficiary/schemes"
                className="notif-btn notif-btn--primary"
              >
                Browse Available Schemes
              </Link>
            </div>
          ) : (
            <div className="dash-table-wrap">
              <table className="dash-table">
                <thead>
                  <tr>
                    <th scope="col">Scheme</th>
                    <th scope="col">Amount</th>
                    <th scope="col">Submission Date</th>
                    <th scope="col">Status &amp; Stage</th>
                    <th scope="col">Action</th>
                  </tr>
                </thead>
                <tbody>
                  {recentApplications.map((app) => (
                    <tr key={app.id}>
                      <td>
                        <div className="dash-scheme-cell">
                          <strong>{app.schemeName || "Grant Scheme"}</strong>
                          <span>App #{app.id}</span>
                        </div>
                      </td>
                      <td>₹{Number(app.requestedAmount).toLocaleString("en-IN")}</td>
                      <td>
                        {app.submittedAt
                          ? new Date(app.submittedAt).toLocaleDateString()
                          : "Draft"}
                      </td>
                      <td>{getStatusBadge(app.status, app.workflowStage)}</td>
                      <td>
                        <Link
                          to="/beneficiary/applications"
                          state={{ selectedApplicationId: app.id }}
                          className="dash-row-link"
                          title="View application details"
                        >
                          Details
                          <ArrowRight size={14} />
                        </Link>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>

        {/* Right / Sidebar: Recent Notifications Widget */}
        <div className="dash-section dash-split-section__side">
          <div className="dash-section__header">
            <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
              <h3 className="dash-section__title">Recent Alerts</h3>
              {unreadNotifCount > 0 && (
                <span className="dash-badge dash-badge--warning">
                  {unreadNotifCount} new
                </span>
              )}
            </div>
            <Link
              to="/beneficiary/notifications"
              className="dash-section__link"
            >
              All alerts
              <ChevronRight size={16} />
            </Link>
          </div>

          <div className="dash-notif-preview-card">
            {recentNotifications.length === 0 ? (
              <div className="dash-notif-empty">
                <Bell size={24} style={{ color: "var(--color-text-muted)" }} />
                <p>No recent notifications.</p>
                <span>You will receive alerts as officers verify your applications.</span>
              </div>
            ) : (
              <div className="dash-notif-preview-list">
                {recentNotifications.map((n) => (
                  <div
                    key={n.id}
                    className={`dash-notif-preview-item ${!n.readAt ? "dash-notif-preview-item--unread" : ""}`}
                  >
                    <div className="dash-notif-preview-item__top">
                      <span className="dash-notif-preview-item__time">
                        {n.createdAt
                          ? new Date(n.createdAt).toLocaleDateString(undefined, {
                              month: "short",
                              day: "numeric",
                            })
                          : "Today"}
                      </span>
                      {!n.readAt && (
                        <span className="dash-notif-preview-item__dot" />
                      )}
                    </div>
                    <p className="dash-notif-preview-item__msg">{n.message}</p>
                    {n.applicationId && (
                      <Link
                        to="/beneficiary/applications"
                        state={{ selectedApplicationId: n.applicationId }}
                        className="dash-notif-preview-item__link"
                      >
                        App #{n.applicationId}
                        <ExternalLink size={12} />
                      </Link>
                    )}
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>
      </div>
    </section>
  );
}

function DashboardHome({ role }) {
  if (role === "ADMIN") {
    return <AdminDashboardHome />;
  }

  if (role === "BENEFICIARY") {
    return <BeneficiaryDashboardHome />;
  }

  if (role === "FIELD_OFFICER") {
    return <FieldOfficerDashboard />;
  }

  if (role === "DISTRICT_OFFICER") {
    return <DistrictOfficerDashboard />;
  }

  if (role === "FINANCE_OFFICER") {
    return <FinanceOfficerDashboard />;
  }

  if (role === "OFFICER") {
    return <OfficerMilestone3Dashboard />;
  }

  const name = localStorage.getItem("fullName")?.trim().split(/\s+/)[0];
  const items = (dashboardNavigation[role] || []).filter(
    (item) => item.label !== "Dashboard"
  );

  return (
    <section className="dashboard-home">
      <div className="dashboard-home__welcome">
        <span className="dashboard-home__eyebrow">
          {roleLabels[role]} workspace
        </span>
        <h2>Welcome back{name ? `, ${name}` : ""}</h2>
        <p>Choose a workspace to continue with your Grantora tasks.</p>
      </div>
      <div className="dashboard-home__grid">
        {items.map(({ label, path, icon: Icon }) => (
          <Link className="dashboard-home__card" key={path} to={path}>
            <span className="dashboard-home__icon">
              <Icon size={21} strokeWidth={1.8} />
            </span>
            <span className="dashboard-home__card-copy">
              <strong>{label}</strong>
              <span>Open {label.toLowerCase()}</span>
            </span>
            <ArrowRight
              className="dashboard-home__arrow"
              size={18}
              aria-hidden="true"
            />
          </Link>
        ))}
      </div>
    </section>
  );
}

export default DashboardHome;
