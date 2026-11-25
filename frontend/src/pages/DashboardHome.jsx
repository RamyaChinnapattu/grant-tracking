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
import {
  formatCurrencyINR,
  getDisbursementBadgeInfo,
  getDisbursementSummary,
} from "../utils/applicationDisbursement";
import AdminDashboardHome from "./AdminDashboardHome";
import OfficerMilestone3Dashboard from "./OfficerMilestone3Dashboard";
import {
  AlertBanner,
  HeroBanner,
  KpiCard,
  ProgressMeter,
  SkeletonKpiRow,
} from "../components/ui/SaaSPrimitives";
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
  const approvedApplicationsList = applications.filter(
    (a) => a.status === "APPROVED"
  );
  const approvedApps = approvedApplicationsList.length;
  const disbursementTotals = approvedApplicationsList.reduce(
    (acc, app) => {
      const summary = getDisbursementSummary(app);
      if (summary) {
        acc.totalApproved += summary.totalApprovedAmount;
        acc.totalReleased += summary.disbursedAmount;
        acc.totalRemaining += summary.remainingAmount;
        if (summary.disbursementStatus === "DISBURSED") acc.disbursedCount += 1;
        else if (summary.disbursementStatus === "PARTIALLY_DISBURSED") acc.partialCount += 1;
      }
      return acc;
    },
    {
      totalApproved: 0,
      totalReleased: 0,
      totalRemaining: 0,
      disbursedCount: 0,
      partialCount: 0,
    }
  );
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

  const getStatusBadge = (app) => {
    const status = app?.status;
    const stage = app?.workflowStage;
    switch (status) {
      case "APPROVED": {
        const disbInfo = getDisbursementBadgeInfo(app);
        return (
          <span className={`dash-badge ${disbInfo?.badgeClass || "dash-badge--success"}`}>
            {disbInfo?.label || "Approved"}
          </span>
        );
      }
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
    <section className="saas-page">
      <HeroBanner
        eyebrow="Citizen Grant Wallet"
        title={`Welcome back, ${firstName}`}
        subtitle="Track grant approvals, officer verification milestones, compliance documents, and released disbursements in real time."
        badge={
          unreadNotifCount > 0 ? (
            <span className="saas-badge saas-badge--teal">
              {unreadNotifCount} New Alert{unreadNotifCount === 1 ? "" : "s"}
            </span>
          ) : null
        }
        actions={
          <>
            <Link to="/beneficiary/my-grant" className="saas-btn saas-btn--accent">
              My Grant &amp; Payments
            </Link>
            <button
              className="saas-btn saas-btn--hero-ghost"
              type="button"
              onClick={loadData}
              disabled={loading}
              title="Refresh dashboard stats and notifications"
            >
              <RefreshCw size={15} className={loading ? "spin" : ""} />
              <span>{loading ? "Refreshing..." : "Refresh"}</span>
            </button>
          </>
        }
        metrics={[
          {
            label: "Sanctioned Grant Total",
            value: loading ? "..." : formatCurrencyINR(disbursementTotals.totalApproved),
            hint: `${approvedApps} approved grant${approvedApps === 1 ? "" : "s"}`,
          },
          {
            label: "Released to Bank",
            value: loading ? "..." : formatCurrencyINR(disbursementTotals.totalReleased),
            hint: `${disbursementTotals.disbursedCount} fully disbursed`,
          },
          {
            label: "Remaining Balance",
            value: loading ? "..." : formatCurrencyINR(disbursementTotals.totalRemaining),
            hint: `${disbursementTotals.partialCount} partial payout${disbursementTotals.partialCount === 1 ? "" : "s"}`,
          },
        ]}
      >
        {disbursementTotals.totalApproved > 0 && (
          <div style={{ position: "relative", zIndex: 1, background: "rgba(255,255,255,0.08)", padding: "12px 16px", borderRadius: "12px", border: "1px solid rgba(255,255,255,0.14)" }}>
            <ProgressMeter
              label="Disbursement Completion"
              value={disbursementTotals.totalReleased}
              max={disbursementTotals.totalApproved}
              tone="emerald"
              sublabel={`${formatCurrencyINR(disbursementTotals.totalReleased)} received of ${formatCurrencyINR(disbursementTotals.totalApproved)} approved`}
            />
          </div>
        )}
      </HeroBanner>

      <AlertBanner
        type="error"
        message={error}
        action={
          error && (
            <button type="button" className="saas-btn saas-btn--sm saas-btn--secondary" onClick={loadData}>
              Try again
            </button>
          )
        }
      />

      {loading && applications.length === 0 ? (
        <SkeletonKpiRow count={6} />
      ) : (
        <div className="saas-kpi-grid">
          <KpiCard
            to="/beneficiary/applications"
            tone="lavender"
            icon={ClipboardList}
            label="Total Applications"
            value={loading ? "..." : totalApps}
            meta="All submitted & drafts"
          />

          <KpiCard
            to="/beneficiary/my-grant"
            tone="mint"
            icon={CheckCircle2}
            label="Funds Released"
            value={loading ? "..." : formatCurrencyINR(disbursementTotals.totalReleased)}
            meta={
              loading
                ? "..."
                : `Remaining: ${formatCurrencyINR(disbursementTotals.totalRemaining)}`
            }
          />

          <KpiCard
            to="/beneficiary/my-grant"
            tone="blue"
            icon={CheckCircle2}
            label="Approved Grants"
            value={loading ? "..." : approvedApps}
            meta={
              loading
                ? "..."
                : approvedApps > 0
                ? `${disbursementTotals.disbursedCount} disbursed · ${disbursementTotals.partialCount} partial`
                : "Finance approved"
            }
          />

          <KpiCard
            to="/beneficiary/applications"
            tone="default"
            icon={Clock}
            label="Under Review"
            value={loading ? "..." : underReviewApps}
            meta="Field · District · Finance"
          />

          <KpiCard
            to="/beneficiary/applications"
            tone="cream"
            icon={AlertCircle}
            label="Needs Correction"
            value={loading ? "..." : needsCorrectionApps}
            meta={needsCorrectionApps > 0 ? "Action required" : "No pending fixes"}
          />

          <KpiCard
            to="/beneficiary/applications"
            tone="rose"
            icon={XCircle}
            label="Rejected"
            value={loading ? "..." : rejectedApps}
            meta="Not approved"
          />
        </div>
      )}

      {/* Quick Action Shortcuts Inside Secondary Tinted Band */}
      <div className="saas-section-band">
        <div className="dash-section__header">
          <div>
            <h3 className="dash-section__title">Quick Actions</h3>
            <p style={{ margin: "4px 0 0", fontSize: "0.9rem", color: "var(--color-text-muted)" }}>
              Start an application, track approvals, or manage your citizen profile
            </p>
          </div>
        </div>
        <div className="dash-actions-grid">
          <Link to="/beneficiary/schemes" className="dash-action-card">
            <span className="dash-action-card__icon">
              <FolderKanban size={22} />
            </span>
            <div className="dash-action-card__copy">
              <strong>Browse Schemes</strong>
              <span>Explore active schemes and criteria</span>
            </div>
            <ArrowRight size={18} className="dash-action-card__arrow" />
          </Link>

          <Link to="/beneficiary/apply" className="dash-action-card">
            <span className="dash-action-card__icon">
              <FilePlus2 size={22} />
            </span>
            <div className="dash-action-card__copy">
              <strong>Apply for Grant</strong>
              <span>Start new application or complete draft</span>
            </div>
            <ArrowRight size={18} className="dash-action-card__arrow" />
          </Link>

          <Link to="/beneficiary/applications" className="dash-action-card">
            <span className="dash-action-card__icon">
              <ClipboardList size={22} />
            </span>
            <div className="dash-action-card__copy">
              <strong>My Applications</strong>
              <span>Track officer stages and decisions</span>
            </div>
            <ArrowRight size={18} className="dash-action-card__arrow" />
          </Link>

          <Link to="/beneficiary/profile" className="dash-action-card">
            <span className="dash-action-card__icon">
              <UserRound size={22} />
            </span>
            <div className="dash-action-card__copy">
              <strong>My Profile</strong>
              <span>Manage contact and jurisdiction</span>
            </div>
            <ArrowRight size={18} className="dash-action-card__arrow" />
          </Link>

          <Link to="/beneficiary/notifications" className="dash-action-card">
            <span className="dash-action-card__icon">
              <Bell size={22} />
            </span>
            <div className="dash-action-card__copy">
              <strong>Notifications</strong>
              <span>
                {unreadNotifCount > 0
                  ? `${unreadNotifCount} unread update${unreadNotifCount === 1 ? "" : "s"}`
                  : "View all notification history"}
              </span>
            </div>
            <ArrowRight size={18} className="dash-action-card__arrow" />
          </Link>
        </div>
      </div>

      {/* Notifications Preview & Recent Applications Section */}
      <div className="dash-split-section">
        {/* Left / Main: Recent Applications */}
        <section className="saas-panel">
          <div className="saas-panel__header">
            <div>
              <h3 className="saas-panel__title">Recent Applications &amp; Disbursement</h3>
              <p className="saas-panel__subtitle">
                Your latest grant submissions, installment release status, and direct links
              </p>
            </div>
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
                  {recentApplications.map((app) => {
                    const disbSummary = getDisbursementSummary(app);
                    return (
                      <tr key={app.id}>
                        <td>
                          <div className="dash-scheme-cell">
                            <strong>{app.schemeName || "Grant Scheme"}</strong>
                            <span>App #{app.id}</span>
                          </div>
                        </td>
                        <td>
                          <div>{formatCurrencyINR(app.requestedAmount)}</div>
                          {disbSummary && (
                            <div style={{ fontSize: "0.75rem", color: "#475569", marginTop: "2px" }}>
                              Released: {formatCurrencyINR(disbSummary.disbursedAmount)} · Remaining:{" "}
                              {formatCurrencyINR(disbSummary.remainingAmount)}
                            </div>
                          )}
                        </td>
                        <td>
                          {app.submittedAt
                            ? new Date(app.submittedAt).toLocaleDateString()
                            : "Draft"}
                        </td>
                        <td>
                          {getStatusBadge(app)}
                          {disbSummary && disbSummary.installments.length > 0 && (
                            <div style={{ fontSize: "0.73rem", color: "#64748b", marginTop: "4px" }}>
                              {disbSummary.installments
                                .map(
                                  (inst) =>
                                    `S${inst.installmentNumber}: ${inst.status?.replaceAll("_", " ")}`
                                )
                                .join(" · ")}
                            </div>
                          )}
                        </td>
                        <td>
                          <Link
                            to={app.status === "APPROVED" ? "/beneficiary/my-grant" : "/beneficiary/applications"}
                            state={{ selectedApplicationId: app.id }}
                            className="dash-row-link"
                            title={app.status === "APPROVED" ? "View grant disbursements" : "View application details"}
                          >
                            {app.status === "APPROVED" ? "My Grant" : "Details"}
                            <ArrowRight size={14} />
                          </Link>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}
        </section>

        {/* Right / Sidebar: Recent Notifications Widget */}
        <section className="saas-panel saas-panel--tinted-lavender">
          <div className="saas-panel__header">
            <div style={{ display: "flex", alignItems: "center", gap: "8px", flexWrap: "wrap" }}>
              <h3 className="saas-panel__title">Recent Alerts</h3>
              {unreadNotifCount > 0 && (
                <span className="saas-badge saas-badge--warning">
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

          <div>
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
        </section>
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
