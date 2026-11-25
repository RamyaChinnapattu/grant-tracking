import { useEffect, useState, useMemo } from "react";
import { Link } from "react-router-dom";
import {
  ArrowRight,
  BadgeCheck,
  CheckCircle2,
  CircleDollarSign,
  ClipboardList,
  Clock,
  History,
  RefreshCw,
  ShieldAlert,
  TrendingUp,
  Users,
} from "lucide-react";
import { getApplications } from "../services/grantApplicationService";
import { getSchemes } from "../services/schemeService";
import { getBeneficiaries } from "../services/beneficiaryService";
import { getFundUtilization } from "../services/disbursementService";
import { getComplianceReviewQueue } from "../services/complianceService";
import {
  getDisbursementBadgeInfo,
  getDisbursementSummary,
} from "../utils/applicationDisbursement";
import { FINTECH_CHART_COLORS } from "../utils/fintechCharts";
import {
  AlertBanner,
  DonutChart,
  HeroBanner,
  KpiCard,
  ProgressMeter,
  SkeletonKpiRow,
  StatusBadge,
} from "../components/ui/SaaSPrimitives";
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

function getStatusBadge(app) {
  const status = app?.status;
  if (status === "APPROVED") {
    const disbInfo = getDisbursementBadgeInfo(app);
    return (
      <div style={{ display: "flex", flexDirection: "column", gap: "3px", alignItems: "flex-start" }}>
        <StatusBadge status="APPROVED" label="Approved" />
        {disbInfo && (
          <span style={{ fontSize: "0.72rem", color: "var(--color-text-muted)", fontWeight: 550 }}>
            {disbInfo.shortLabel}
          </span>
        )}
      </div>
    );
  }
  switch (status) {
    case "REJECTED":
      return <StatusBadge status="REJECTED" label="Rejected" />;
    case "NEEDS_CORRECTION":
      return <StatusBadge status="NEEDS_CORRECTION" label="Needs Correction" />;
    case "DRAFT":
      return <StatusBadge status="DRAFT" label="Draft" />;
    case "WITHDRAWN":
      return <StatusBadge status="WITHDRAWN" label="Withdrawn" />;
    case "FIELD_VERIFICATION":
    case "SUBMITTED":
    case "UNDER_VERIFICATION":
      return <StatusBadge status="FIELD_VERIFICATION" label="Stage 1: Field Review" />;
    case "DISTRICT_REVIEW":
      return <StatusBadge status="DISTRICT_REVIEW" label="Stage 2: District Review" />;
    case "FINANCE_REVIEW":
      return <StatusBadge status="FINANCE_REVIEW" label="Stage 3: Finance Approval" />;
    default:
      return <StatusBadge status={status || "UNDER_REVIEW"} label="Under Review" />;
  }
}

function AdminDashboardHome() {
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState("");

  const [applications, setApplications] = useState([]);
  const [schemes, setSchemes] = useState([]);
  const [beneficiaries, setBeneficiaries] = useState([]);
  const [fundUtilization, setFundUtilization] = useState(null);
  const [complianceQueue, setComplianceQueue] = useState([]);

  const loadData = async (isManual = false) => {
    if (isManual) setRefreshing(true);
    else setLoading(true);
    setError("");

    try {
      const [appsRes, schemesRes, benefsRes, fundRes, compRes] = await Promise.all([
        getApplications(),
        getSchemes(),
        getBeneficiaries(),
        getFundUtilization().catch(() => ({ data: null })),
        getComplianceReviewQueue().catch(() => ({ data: [] })),
      ]);

      setApplications(appsRes.data || []);
      setSchemes(schemesRes.data || []);
      setBeneficiaries(benefsRes.data || []);
      setFundUtilization(fundRes?.data || null);
      setComplianceQueue(compRes?.data || []);
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

  const totalSchemes = schemes.length;
  const activeSchemes = useMemo(
    () => schemes.filter((s) => s.status === "ACTIVE").length,
    [schemes]
  );
  const totalBeneficiaries = beneficiaries.length;
  const totalApplications = applications.length;

  const approvedApplications = useMemo(
    () => applications.filter((a) => a.status === "APPROVED").length,
    [applications]
  );

  const rejectedApplications = useMemo(
    () => applications.filter((a) => a.status === "REJECTED").length,
    [applications]
  );

  const correctionApplications = useMemo(
    () => applications.filter((a) => a.status === "NEEDS_CORRECTION").length,
    [applications]
  );

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

  const pendingApplications =
    pendingFieldApps.length +
    pendingDistrictApps.length +
    pendingFinanceApps.length;

  const financialMetrics = useMemo(() => {
    const totalBudget =
      fundUtilization?.totalAllocatedFunds != null
        ? Number(fundUtilization.totalAllocatedFunds)
        : schemes.reduce((sum, s) => sum + (Number(s.allocatedFunds) || 0), 0);

    const committedAmount =
      fundUtilization?.totalApprovedAmount != null
        ? Number(fundUtilization.totalApprovedAmount)
        : applications
            .filter((a) => a.status === "APPROVED")
            .reduce((sum, a) => sum + (Number(a.requestedAmount) || 0), 0);

    const disbursedAmount =
      fundUtilization?.totalDisbursedAmount != null
        ? Number(fundUtilization.totalDisbursedAmount)
        : applications.reduce((sum, a) => {
            const ds = getDisbursementSummary(a);
            return sum + (ds ? ds.disbursedAmount : 0);
          }, 0);

    const remainingBudget =
      fundUtilization?.remainingAllocatedAmount != null
        ? Number(fundUtilization.remainingAllocatedAmount)
        : Math.max(0, totalBudget - committedAmount);

    return {
      totalBudget,
      committedAmount,
      disbursedAmount,
      remainingBudget,
    };
  }, [fundUtilization, schemes, applications]);

  const statusDonutSegments = useMemo(
    () => [
      {
        label: "Approved Grants",
        value: approvedApplications,
        color: FINTECH_CHART_COLORS.emerald,
      },
      {
        label: "Stage 1: Field Review",
        value: pendingFieldApps.length,
        color: FINTECH_CHART_COLORS.blue,
      },
      {
        label: "Stage 2: District Review",
        value: pendingDistrictApps.length,
        color: FINTECH_CHART_COLORS.amber,
      },
      {
        label: "Stage 3: Finance Review",
        value: pendingFinanceApps.length,
        color: FINTECH_CHART_COLORS.indigo,
      },
      {
        label: "Needs Correction",
        value: correctionApplications,
        color: FINTECH_CHART_COLORS.teal,
      },
      {
        label: "Rejected",
        value: rejectedApplications,
        color: FINTECH_CHART_COLORS.rose,
      },
    ],
    [
      approvedApplications,
      pendingFieldApps.length,
      pendingDistrictApps.length,
      pendingFinanceApps.length,
      correctionApplications,
      rejectedApplications,
    ]
  );

  const schemeChartRows = useMemo(() => {
    const fundSchemes = fundUtilization?.schemes || [];
    return schemes.slice(0, 6).map((s) => {
      const fs = fundSchemes.find((f) => String(f.schemeId) === String(s.id));
      const budget = Number(fs?.allocatedFunds ?? s.allocatedFunds ?? 0);
      const committed = Number(
        fs?.approvedAmount ??
          applications
            .filter((a) => String(a.schemeId) === String(s.id) && a.status === "APPROVED")
            .reduce((sum, a) => sum + (Number(a.requestedAmount) || 0), 0)
      );
      const disbursed = Number(
        fs?.disbursedAmount ??
          fs?.releasedAmount ??
          applications
            .filter((a) => String(a.schemeId) === String(s.id))
            .reduce((sum, a) => {
              const ds = getDisbursementSummary(a);
              return sum + (ds ? ds.disbursedAmount : 0);
            }, 0)
      );
      const baseline = Math.max(budget, committed, 1);
      return {
        id: s.id,
        name: s.schemeName,
        budget,
        committed,
        disbursed,
        committedPct: Math.min(100, (committed / baseline) * 100),
        disbursedPct: Math.min(100, (disbursed / baseline) * 100),
      };
    });
  }, [schemes, fundUtilization, applications]);

  const recentApplications = useMemo(
    () => applications.slice(0, 5),
    [applications]
  );

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
    <div className="saas-page">
      <HeroBanner
        eyebrow="Administrator Financial Command"
        title={`Welcome back${name ? `, ${name}` : ""}`}
        subtitle="Real-time governance across grant scheme budgets, multi-stage verification queues, compliance checks, and treasury disbursements."
        badge={
          <span className="saas-badge saas-badge--teal">
            {activeSchemes} Active Schemes
          </span>
        }
        actions={
          <>
            <Link to="/admin/grant-management" className="saas-btn saas-btn--accent">
              <CircleDollarSign size={16} />
              Financial Control Center
            </Link>
            <button
              className="saas-btn saas-btn--hero-ghost"
              type="button"
              onClick={() => loadData(true)}
              disabled={loading || refreshing}
            >
              <RefreshCw
                size={15}
                className={loading || refreshing ? "spin" : ""}
              />
              {refreshing ? "Syncing…" : "Refresh Data"}
            </button>
          </>
        }
        metrics={[
          {
            label: "Total Treasury Budget",
            value: formatCurrency(financialMetrics.totalBudget),
            hint: `Remaining: ${formatCurrency(financialMetrics.remainingBudget)}`,
          },
          {
            label: "Committed Sanctions",
            value: formatCurrency(financialMetrics.committedAmount),
            hint: `${approvedApplications} approved grants`,
          },
          {
            label: "Released to Citizens",
            value: formatCurrency(financialMetrics.disbursedAmount),
            hint: `${complianceQueue.length} compliance reviews pending`,
          },
        ]}
      />

      <AlertBanner
        type="error"
        message={error}
        onClose={() => setError("")}
        action={
          error && (
            <button
              type="button"
              className="saas-btn saas-btn--sm saas-btn--secondary"
              onClick={() => loadData(true)}
            >
              Retry
            </button>
          )
        }
      />

      {/* Multi-Tone FinTech KPI Strip (Featured Disbursed + Contrasting Program Metrics) */}
      {loading ? (
        <SkeletonKpiRow count={6} />
      ) : (
        <div className="saas-kpi-grid">
          <KpiCard
            to="/admin/grant-management?tab=overview"
            tone="featured"
            icon={TrendingUp}
            label="Total Disbursed"
            value={formatCurrency(financialMetrics.disbursedAmount)}
            meta={`Of ${formatCurrency(financialMetrics.committedAmount)} committed`}
          />

          <KpiCard
            to="/admin/applications"
            state={{ statusFilter: "APPROVED" }}
            tone="mint"
            icon={CheckCircle2}
            label="Approved Grants"
            value={approvedApplications}
            meta={`${totalApplications} total applications`}
          />

          <KpiCard
            to="/admin/verification"
            tone="cream"
            icon={Clock}
            label="Pending Verification"
            value={pendingApplications}
            meta={`${pendingFieldApps.length} field · ${pendingDistrictApps.length} district · ${pendingFinanceApps.length} finance`}
          />

          <KpiCard
            to="/admin/grant-management?tab=compliance"
            tone="lavender"
            icon={BadgeCheck}
            label="Compliance Queue"
            value={complianceQueue.length}
            meta="Submissions awaiting review"
          />

          <KpiCard
            to="/admin/beneficiaries"
            tone="blue"
            icon={Users}
            label="Total Beneficiaries"
            value={totalBeneficiaries}
            meta={`${activeSchemes} of ${totalSchemes} schemes active`}
          />

          <KpiCard
            to="/admin/applications"
            tone="rose"
            icon={ShieldAlert}
            label="Rejected / Declined"
            value={rejectedApplications}
            meta={`${correctionApplications} awaiting correction`}
          />
        </div>
      )}

      {/* Analytics Row: Interactive Application Donut + Scheme Utilization Bar Chart */}
      <div className="dash-split-section">
        <section className="saas-panel saas-panel--tinted-lavender">
          <div className="saas-panel__header">
            <div>
              <h2 className="saas-panel__title">Application Status Breakdown</h2>
              <p className="saas-panel__subtitle">
                Live pipeline distribution across {totalApplications} submitted applications
              </p>
            </div>
            <Link to="/admin/applications" className="dash-section__link">
              All applications <ArrowRight size={14} />
            </Link>
          </div>

          <DonutChart
            segments={statusDonutSegments}
            centerLabel="Applications"
            centerValue={totalApplications}
            emptyText="Applications submitted by beneficiaries will appear in this chart."
          />
        </section>

        <section className="saas-panel">
          <div className="saas-panel__header">
            <div>
              <h2 className="saas-panel__title">Scheme Budget &amp; Disbursement Utilization</h2>
              <p className="saas-panel__subtitle">
                Allocated scheme ceiling vs committed sanctions and released funds
              </p>
            </div>
            <Link to="/admin/schemes" className="dash-section__link">
              Manage schemes <ArrowRight size={14} />
            </Link>
          </div>

          {schemeChartRows.length === 0 ? (
            <p style={{ color: "var(--color-text-muted)", margin: 0, fontSize: "0.86rem" }}>
              No schemes configured yet.
            </p>
          ) : (
            <>
              <div style={{ marginBottom: "14px" }}>
                <ProgressMeter
                  label="Overall Treasury Release Progress"
                  value={financialMetrics.disbursedAmount}
                  max={financialMetrics.totalBudget || financialMetrics.committedAmount || 1}
                  tone="emerald"
                  sublabel={`${formatCurrency(financialMetrics.disbursedAmount)} disbursed of ${formatCurrency(
                    financialMetrics.totalBudget
                  )} budget`}
                />
              </div>

              <div className="saas-bar-chart">
                {schemeChartRows.map((row) => (
                  <div key={row.id} className="saas-bar-item">
                    <div className="saas-bar-item__head">
                      <span className="saas-bar-item__title">{row.name}</span>
                      <span className="saas-bar-item__meta">
                        Disbursed {formatCurrency(row.disbursed)} · Committed{" "}
                        {formatCurrency(row.committed)}
                        {row.budget > 0 ? ` / ${formatCurrency(row.budget)}` : ""}
                      </span>
                    </div>
                    <div className="saas-bar-track">
                      <div
                        className="saas-bar-fill saas-bar-fill--committed"
                        style={{ width: `${row.committedPct}%` }}
                      />
                      <div
                        className="saas-bar-fill saas-bar-fill--disbursed"
                        style={{ width: `${row.disbursedPct}%` }}
                      />
                    </div>
                  </div>
                ))}
              </div>
              <div
                style={{
                  display: "flex",
                  gap: "16px",
                  marginTop: "14px",
                  fontSize: "0.75rem",
                  color: "var(--color-text-muted)",
                }}
              >
                <span style={{ display: "inline-flex", alignItems: "center", gap: "6px" }}>
                  <i
                    style={{
                      width: 10,
                      height: 10,
                      borderRadius: "50%",
                      background: "var(--color-emerald)",
                      display: "inline-block",
                    }}
                  />
                  Disbursed Funds
                </span>
                <span style={{ display: "inline-flex", alignItems: "center", gap: "6px" }}>
                  <i
                    style={{
                      width: 10,
                      height: 10,
                      borderRadius: "50%",
                      background: "rgba(81, 70, 216, 0.3)",
                      display: "inline-block",
                    }}
                  />
                  Committed Grants
                </span>
              </div>
            </>
          )}
        </section>
      </div>

      {/* Recent Applications & Activity in Contrasting Secondary Band */}
      <div className="saas-section-band">
        <div className="dash-split-section">
          <section className="saas-panel">
            <div className="saas-panel__header">
              <div>
                <h2 className="saas-panel__title">Recent Applications</h2>
                <p className="saas-panel__subtitle">
                  Latest citizen submissions and sanction disbursement progress
                </p>
              </div>
              <Link to="/admin/applications" className="dash-section__link">
                View all ({totalApplications}) <ArrowRight size={15} />
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
                      <td colSpan="6" style={{ textAlign: "center", padding: "28px", color: "var(--color-text-muted)" }}>
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
                          <span style={{ fontWeight: 650 }}>{app.schemeName}</span>
                        </td>
                        <td>{formatCurrency(app.requestedAmount)}</td>
                        <td>{getStatusBadge(app)}</td>
                        <td>
                          <span style={{ fontSize: "0.85rem", color: "var(--color-text-muted)" }}>
                            {formatDate(app.submittedAt)}
                          </span>
                        </td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </div>
          </section>

          <div style={{ display: "flex", flexDirection: "column", gap: "20px", minWidth: 0 }}>
            <section className="saas-panel saas-panel--tinted-blue">
              <div className="saas-panel__header" style={{ marginBottom: "14px" }}>
                <div>
                  <h2 className="saas-panel__title">Verification Pipeline</h2>
                  <p className="saas-panel__subtitle">Live officer stage backlog</p>
                </div>
                <Link to="/admin/verification" className="dash-section__link">
                  Open queue <ArrowRight size={14} />
                </Link>
              </div>

              <div style={{ display: "grid", gap: "12px" }}>
                <Link
                  to="/admin/verification"
                  className="pipeline-step-item"
                  style={{
                    textDecoration: "none",
                    background: "var(--color-tint-blue)",
                    border: "1px solid #C7DCFF",
                    borderLeft: "4px solid #3478F6",
                    padding: "14px 16px",
                    borderRadius: "12px",
                  }}
                >
                  <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: "8px", flexWrap: "wrap" }}>
                    <strong style={{ fontSize: "0.95rem", color: "var(--color-on-surface)" }}>
                      Stage 1: Field Review
                    </strong>
                    <StatusBadge status="FIELD_VERIFICATION" label={`${pendingFieldApps.length} pending`} />
                  </div>
                  <p style={{ margin: "6px 0 0", fontSize: "0.84rem", color: "var(--color-text-muted)" }}>
                    Physical verification &amp; document authenticity check
                  </p>
                </Link>

                <Link
                  to="/admin/verification"
                  className="pipeline-step-item"
                  style={{
                    textDecoration: "none",
                    background: "var(--color-tint-cream)",
                    border: "1px solid #FBD9A4",
                    borderLeft: "4px solid #F59E0B",
                    padding: "14px 16px",
                    borderRadius: "12px",
                  }}
                >
                  <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: "8px", flexWrap: "wrap" }}>
                    <strong style={{ fontSize: "0.95rem", color: "var(--color-on-surface)" }}>
                      Stage 2: District Review
                    </strong>
                    <StatusBadge status="PENDING" label={`${pendingDistrictApps.length} pending`} />
                  </div>
                  <p style={{ margin: "6px 0 0", fontSize: "0.84rem", color: "var(--color-text-muted)" }}>
                    District jurisdiction endorsement &amp; quota check
                  </p>
                </Link>

                <Link
                  to="/admin/verification"
                  className="pipeline-step-item"
                  style={{
                    textDecoration: "none",
                    background: "var(--color-tint-lavender)",
                    border: "1px solid #D6CEFE",
                    borderLeft: "4px solid #5146D8",
                    padding: "14px 16px",
                    borderRadius: "12px",
                  }}
                >
                  <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: "8px", flexWrap: "wrap" }}>
                    <strong style={{ fontSize: "0.95rem", color: "var(--color-on-surface)" }}>
                      Stage 3: Finance Approval
                    </strong>
                    <StatusBadge status="READY" label={`${pendingFinanceApps.length} pending`} />
                  </div>
                  <p style={{ margin: "6px 0 0", fontSize: "0.84rem", color: "var(--color-text-muted)" }}>
                    Final grant sanction &amp; disbursement authorization
                  </p>
                </Link>
              </div>
            </section>

            <section className="saas-panel">
              <div className="saas-panel__header" style={{ marginBottom: "14px" }}>
                <h2 className="saas-panel__title">Recent Activity</h2>
                <Link to="/admin/audit-logs" className="dash-section__link">
                  Audit history <ArrowRight size={15} />
                </Link>
              </div>

              {recentActivity.length === 0 ? (
                <div className="dash-notif-empty">
                  <History size={26} style={{ color: "var(--color-text-muted)" }} />
                  <p>No recent activity.</p>
                  <span>Administrative and review actions will appear here.</span>
                </div>
              ) : (
                <div className="dash-notif-preview-list">
                  {recentActivity.map((item) => (
                    <div key={item.id} className="dash-notif-preview-item">
                      <div className="dash-notif-preview-item__top">
                        <StatusBadge
                          status={item.toStatus || item.action}
                          label={`App #${item.applicationId} · ${item.action}`}
                        />
                        <span className="dash-notif-preview-item__time">
                          {formatDateTime(item.createdAt)}
                        </span>
                      </div>
                      <p className="dash-notif-preview-item__msg">
                        <strong>{item.actorName}</strong>: {item.beneficiaryName} ({item.schemeName})
                      </p>
                      {item.remarks && (
                        <span style={{ fontSize: "0.82rem", fontStyle: "italic", color: "var(--color-text-muted)" }}>
                          &ldquo;{item.remarks}&rdquo;
                        </span>
                      )}
                    </div>
                  ))}
                </div>
              )}
            </section>
          </div>
        </div>
      </div>
    </div>
  );
}

export default AdminDashboardHome;
