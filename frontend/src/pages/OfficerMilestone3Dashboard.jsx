import { useCallback, useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import {
  AlertTriangle,
  ArrowRight,
  BadgeCheck,
  CheckCircle2,
  ClipboardCheck,
  ClipboardList,
  FileClock,
  RefreshCw,
  ShieldAlert,
} from "lucide-react";
import {
  getComplianceReviewQueue,
  getNonComplianceCases,
} from "../services/complianceService";
import { getUtilizationReviewQueue } from "../services/fundAnalyticsService";
import { getAssignedMilestones } from "../services/milestone3Service";
import { getVerificationQueue } from "../services/grantApplicationService";
import { FINTECH_CHART_COLORS } from "../utils/fintechCharts";
import {
  AlertBanner,
  DonutChart,
  EmptyState,
  HeroBanner,
  KpiCard,
  SkeletonKpiRow,
  StatusBadge,
} from "../components/ui/SaaSPrimitives";
import "../styles/DashboardHome.css";

export default function OfficerMilestone3Dashboard() {
  const [data, setData] = useState({
    applications: [],
    compliance: [],
    utilization: [],
    cases: [],
    milestones: [],
  });
  const [busy, setBusy] = useState(true);
  const [error, setError] = useState("");

  const officerLevel = localStorage.getItem("officerLevel") || "";
  const fullName = localStorage.getItem("fullName")?.trim().split(/\s+/)[0] || "Officer";

  const load = useCallback(async () => {
    setBusy(true);
    setError("");
    try {
      const results = await Promise.allSettled([
        getComplianceReviewQueue(),
        getUtilizationReviewQueue(),
        getNonComplianceCases(),
        getAssignedMilestones(),
        getVerificationQueue(),
      ]);
      const coreFailures = results.slice(0, 4).filter((r) => r.status === "rejected");
      if (coreFailures.length === 4) throw coreFailures[0].reason;

      setData({
        compliance: results[0].status === "fulfilled" ? results[0].value.data || [] : [],
        utilization: results[1].status === "fulfilled" ? results[1].value.data || [] : [],
        cases: results[2].status === "fulfilled" ? results[2].value.data || [] : [],
        milestones: results[3].status === "fulfilled" ? results[3].value.data || [] : [],
        applications: results[4].status === "fulfilled" ? results[4].value.data || [] : [],
      });
      if (coreFailures.length) {
        setError("Some review summaries could not load. Available queues are shown below.");
      }
    } catch (e) {
      setError(e.response?.data?.message || "Could not load your assigned work queues.");
    } finally {
      setBusy(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const openCases = useMemo(
    () => data.cases.filter((c) => ["OPEN", "UNDER_REVIEW"].includes(c.status)),
    [data.cases]
  );
  const milestones = useMemo(
    () =>
      data.milestones
        .filter((m) => m.status !== "COMPLETED" && m.status !== "CANCELLED")
        .sort((a, b) => (a.dueDate || "9999").localeCompare(b.dueDate || "9999")),
    [data.milestones]
  );

  const workloadSegments = useMemo(
    () => [
      {
        label: "Applications Queue",
        value: data.applications.length,
        color: FINTECH_CHART_COLORS.blue,
      },
      {
        label: "Compliance Docs",
        value: data.compliance.length,
        color: FINTECH_CHART_COLORS.emerald,
      },
      {
        label: "Expenditure Reports",
        value: data.utilization.length,
        color: FINTECH_CHART_COLORS.amber,
      },
      {
        label: "Pending Milestones",
        value: milestones.length,
        color: FINTECH_CHART_COLORS.indigo,
      },
      {
        label: "Non-Compliance Cases",
        value: openCases.length,
        color: FINTECH_CHART_COLORS.rose,
      },
    ],
    [
      data.applications.length,
      data.compliance.length,
      data.utilization.length,
      milestones.length,
      openCases.length,
    ]
  );

  const totalQueueItems =
    data.applications.length +
    data.compliance.length +
    data.utilization.length +
    openCases.length +
    milestones.length;

  return (
    <div className="saas-page">
      <HeroBanner
        eyebrow={`${officerLevel ? `${officerLevel} ` : ""}Officer Verification Command`}
        title={`Assigned Review Queue · ${fullName}`}
        subtitle="Prioritized work queues and verification actions limited to grant applications and compliance cases assigned to your desk."
        badge={
          <span className="saas-badge saas-badge--teal">
            {totalQueueItems} Open Queue Items
          </span>
        }
        actions={
          <>
            <Link to="/officer/applications" className="saas-btn saas-btn--accent">
              <ClipboardList size={15} />
              My Applications
            </Link>
            <button
              className="saas-btn saas-btn--hero-ghost"
              type="button"
              onClick={load}
              disabled={busy}
            >
              <RefreshCw size={15} className={busy ? "spin" : ""} />
              {busy ? "Refreshing…" : "Refresh Queue"}
            </button>
          </>
        }
        metrics={[
          {
            label: "Application Reviews",
            value: data.applications.length,
            hint: "Awaiting stage decision",
          },
          {
            label: "Compliance Evidence",
            value: data.compliance.length,
            hint: "Documents pending sign-off",
          },
          {
            label: "Escalations Open",
            value: openCases.length,
            hint: `${milestones.length} active milestones due`,
          },
        ]}
      />

      <AlertBanner type="error" message={error} onClose={() => setError("")} />

      {busy && !data.compliance.length && !data.applications.length ? (
        <SkeletonKpiRow count={5} />
      ) : (
        <div className="saas-section-band">
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", flexWrap: "wrap", gap: "8px" }}>
            <h2 className="saas-panel__title">Priority Operations Queue</h2>
            <span style={{ fontSize: "0.875rem", color: "var(--color-text-muted)" }}>
              Click any metric tile to open that desk queue directly
            </span>
          </div>
          <div className="saas-kpi-grid">
            <KpiCard
              to="/officer/applications"
              tone="featured"
              icon={ClipboardList}
              label="Assigned Applications"
              value={data.applications.length}
              meta="Open application verification queue"
            />

            <KpiCard
              to="/officer/compliance"
              tone="mint"
              icon={BadgeCheck}
              label="Compliance Documents"
              value={data.compliance.length}
              meta="Documents awaiting verification"
            />

            <KpiCard
              to="/officer/utilization"
              tone="cream"
              icon={ClipboardCheck}
              label="Expenditure Reports"
              value={data.utilization.length}
              meta="Utilization reports awaiting review"
            />

            <KpiCard
              to="/officer/compliance"
              tone="rose"
              icon={ShieldAlert}
              label="Non-Compliance Cases"
              value={openCases.length}
              meta="Open compliance escalations"
            />

            <KpiCard
              to="/officer/applications"
              tone="lavender"
              icon={AlertTriangle}
              label="Pending Milestones"
              value={milestones.length}
              meta="Milestones requiring attention"
            />
          </div>
        </div>
      )}

      {/* Workload Breakdown Chart + Assigned Applications Table */}
      <div className="dash-split-section">
        <section className="saas-panel">
          <div className="saas-panel__header">
            <div>
              <h2 className="saas-panel__title">Applications Assigned For Review</h2>
              <p className="saas-panel__subtitle">
                Active verification tasks assigned to your review level
              </p>
            </div>
            <div style={{ display: "flex", gap: "12px", alignItems: "center" }}>
              <Link to="/officer/history" className="dash-section__link">
                <FileClock size={14} /> Verification History
              </Link>
              <Link to="/officer/applications" className="dash-section__link">
                Open All <ArrowRight size={14} />
              </Link>
            </div>
          </div>

          {data.applications.length === 0 ? (
            <EmptyState
              icon={CheckCircle2}
              title={busy ? "Loading assigned applications…" : "No applications pending review"}
              description="You have no active applications waiting in your verification queue."
            />
          ) : (
            <div className="dash-table-wrap">
              <table className="dash-table">
                <thead>
                  <tr>
                    <th>Application</th>
                    <th>Applicant</th>
                    <th>Scheme</th>
                    <th>Requested Amount</th>
                    <th>Stage &amp; Status</th>
                    <th>Action</th>
                  </tr>
                </thead>
                <tbody>
                  {data.applications.slice(0, 6).map((app) => (
                    <tr key={app.id}>
                      <td>
                        <strong>#{app.id}</strong>
                      </td>
                      <td>{app.beneficiaryName || "—"}</td>
                      <td>{app.schemeName || "—"}</td>
                      <td>₹{Number(app.requestedAmount || 0).toLocaleString("en-IN")}</td>
                      <td>
                        <StatusBadge status={app.status} />
                      </td>
                      <td>
                        <Link to="/officer/applications" className="dash-row-link">
                          Review <ArrowRight size={14} />
                        </Link>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>

        <section className="saas-panel saas-panel--tinted-lavender">
          <div className="saas-panel__header">
            <div>
              <h2 className="saas-panel__title">Desk Workload Mix</h2>
              <p className="saas-panel__subtitle">
                Proportion of pending items across review queues
              </p>
            </div>
          </div>
          <DonutChart
            segments={workloadSegments}
            centerLabel="Queue Items"
            centerValue={totalQueueItems}
            emptyText="All verification queues are currently clear."
          />
        </section>
      </div>

      {/* Milestones & Non-Compliance Two-Column Work Queues */}
      <div className="dash-split-section">
        <section className="saas-panel saas-panel--tinted-blue">
          <div className="saas-panel__header">
            <div>
              <h2 className="saas-panel__title">Milestones Requiring Verification</h2>
              <p className="saas-panel__subtitle">
                Assigned milestones sorted by nearest due date
              </p>
            </div>
            <Link to="/officer/compliance" className="dash-section__link">
              Review queue <ArrowRight size={14} />
            </Link>
          </div>

          {milestones.length === 0 ? (
            <p style={{ color: "var(--color-text-muted)", fontSize: "0.86rem", margin: 0 }}>
              {busy ? "Loading assigned milestones…" : "No assigned milestones need attention."}
            </p>
          ) : (
            <div style={{ display: "grid", gap: "10px" }}>
              {milestones.slice(0, 8).map((m) => (
                <div
                  key={m.id}
                  style={{
                    display: "flex",
                    justifyContent: "space-between",
                    alignItems: "center",
                    gap: "12px",
                    padding: "12px 14px",
                    borderRadius: "10px",
                    background: "#ffffff",
                    border: "1px solid #dbe5f7",
                    borderLeft: "4px solid #3478F6",
                  }}
                >
                  <div style={{ minWidth: 0, flex: 1 }}>
                    <div style={{ fontSize: "0.94rem", fontWeight: 650, overflowWrap: "anywhere" }}>
                      <Link to="/officer/applications">APP-{m.applicationId}</Link> · {m.title}
                    </div>
                    <div style={{ fontSize: "0.84rem", color: "var(--color-text-muted)", marginTop: "3px" }}>
                      Due: {m.dueDate || "No due date"}
                    </div>
                  </div>
                  <StatusBadge status={m.status} />
                </div>
              ))}
            </div>
          )}
        </section>

        <section className="saas-panel saas-panel--tinted-warm">
          <div className="saas-panel__header">
            <div>
              <h2 className="saas-panel__title">Open Non-Compliance Cases</h2>
              <p className="saas-panel__subtitle">
                Flagged issues connected to your assigned applications
              </p>
            </div>
            <Link to="/officer/compliance" className="dash-section__link">
              Inspect <ArrowRight size={14} />
            </Link>
          </div>

          {openCases.length === 0 ? (
            <p style={{ color: "var(--color-text-muted)", fontSize: "0.94rem", margin: 0 }}>
              {busy ? "Loading assigned cases…" : "No open non-compliance cases."}
            </p>
          ) : (
            <div style={{ display: "grid", gap: "12px" }}>
              {openCases.slice(0, 8).map((c) => (
                <div
                  key={c.id}
                  style={{
                    display: "flex",
                    justifyContent: "space-between",
                    alignItems: "center",
                    gap: "12px",
                    padding: "14px 16px",
                    borderRadius: "12px",
                    background: "#FFFFFF",
                    border: "1px solid #F5C3CB",
                    borderLeft: "4px solid #D93848",
                  }}
                >
                  <div style={{ minWidth: 0, flex: 1 }}>
                    <div style={{ fontSize: "0.94rem", fontWeight: 650, overflowWrap: "anywhere" }}>
                      <Link to="/officer/compliance">APP-{c.applicationId}</Link> · {c.reason}
                    </div>
                    <div style={{ fontSize: "0.84rem", color: "var(--color-text-muted)", marginTop: "3px" }}>
                      Severity: {c.severity || "MEDIUM"}
                    </div>
                  </div>
                  <StatusBadge status={c.status} />
                </div>
              ))}
            </div>
          )}
        </section>
      </div>
    </div>
  );
}
