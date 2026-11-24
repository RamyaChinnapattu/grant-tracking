import { useEffect, useState, useMemo } from "react";
import {
  BarChart3,
  Building2,
  CheckCircle2,
  CircleDollarSign,
  FileText,
  MapPin,
  RefreshCw,
} from "lucide-react";
import { getApplications } from "../services/grantApplicationService";
import { getSchemes } from "../services/schemeService";
import {
  getFundUtilization,
  getRegionalAnalytics,
} from "../services/disbursementService";
import Milestone3ReportExports from "../components/Milestone3ReportExports";
import "../styles/DashboardHome.css";
import "../styles/AdminDashboardHome.css";

const REVIEW_STATUSES = [
  "SUBMITTED",
  "UNDER_VERIFICATION",
  "FIELD_VERIFICATION",
  "DISTRICT_REVIEW",
  "FINANCE_REVIEW",
  "UNDER_REVIEW",
];

function formatCurrency(amount) {
  if (amount == null || isNaN(amount)) return "₹0";
  return `₹${Number(amount).toLocaleString("en-IN")}`;
}

function AdminReports() {
  const [applications, setApplications] = useState([]);
  const [schemes, setSchemes] = useState([]);
  const [fundUtilization, setFundUtilization] = useState(null);
  const [districtAnalytics, setDistrictAnalytics] = useState([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState("");

  const loadData = async (isManual = false) => {
    if (isManual) setRefreshing(true);
    else setLoading(true);
    setError("");

    try {
      const [appsRes, schemesRes, fundRes, regRes] = await Promise.all([
        getApplications(),
        getSchemes(),
        getFundUtilization().catch(() => ({ data: null })),
        getRegionalAnalytics().catch(() => ({ data: [] })),
      ]);
      setApplications(appsRes.data || []);
      setSchemes(schemesRes.data || []);
      setFundUtilization(fundRes.data || null);
      setDistrictAnalytics(regRes.data || []);
    } catch (err) {
      console.error("Reports data fetch error:", err);
      setError(
        err.response?.data?.message ||
          "Failed to load analytics and reporting data."
      );
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  };

  useEffect(() => {
    loadData();
  }, []);

  // Scheme-wise Analytics
  const schemeAnalytics = useMemo(() => {
    const fundMap = new Map();
    (fundUtilization?.schemes || []).forEach((fs) => {
      fundMap.set(fs.schemeId, fs);
    });

    return schemes.map((scheme) => {
      const schemeApps = applications.filter((a) => a.schemeId === scheme.id);
      const total = schemeApps.length;
      const approved = schemeApps.filter((a) => a.status === "APPROVED").length;
      const rejected = schemeApps.filter((a) => a.status === "REJECTED").length;
      const underReview = schemeApps.filter((a) =>
        REVIEW_STATUSES.includes(a.status)
      ).length;
      const fundsRequested = schemeApps.reduce(
        (sum, a) => sum + (Number(a.requestedAmount) || 0),
        0
      );
      const fundsApproved = schemeApps
        .filter((a) => a.status === "APPROVED")
        .reduce((sum, a) => sum + (Number(a.requestedAmount) || 0), 0);
      const approvalRate = total > 0 ? Math.round((approved / total) * 100) : 0;
      const rejectionRate = total > 0 ? Math.round((rejected / total) * 100) : 0;

      const fs = fundMap.get(scheme.id);
      const fundsDisbursed = fs ? Number(fs.disbursedAmount || 0) : 0;
      const allocatedFunds = fs ? Number(fs.allocatedFunds || 0) : Number(scheme.allocatedFunds || 0);

      return {
        id: scheme.id,
        name: scheme.schemeName,
        department: scheme.department,
        status: scheme.status,
        total,
        approved,
        approvalRate,
        rejected,
        rejectionRate,
        underReview,
        fundsRequested,
        fundsApproved,
        fundsDisbursed,
        allocatedFunds,
      };
    });
  }, [schemes, applications, fundUtilization]);

  // Region / State-wise Analytics
  const regionAnalytics = useMemo(() => {
    const map = {};
    applications.forEach((app) => {
      const state = app.beneficiaryDetails?.state || "Unassigned Region";
      if (!map[state]) {
        map[state] = {
          state,
          total: 0,
          approved: 0,
          rejected: 0,
          underReview: 0,
          districts: new Set(),
        };
      }
      map[state].total += 1;
      if (app.status === "APPROVED") map[state].approved += 1;
      else if (app.status === "REJECTED") map[state].rejected += 1;
      else if (REVIEW_STATUSES.includes(app.status)) map[state].underReview += 1;

      if (app.beneficiaryDetails?.district) {
        map[state].districts.add(app.beneficiaryDetails.district);
      }
    });

    return Object.values(map).map((r) => ({
      ...r,
      districtCount: r.districts.size,
      approvalRate: r.total > 0 ? Math.round((r.approved / r.total) * 100) : 0,
      rejectionRate: r.total > 0 ? Math.round((r.rejected / r.total) * 100) : 0,
    }));
  }, [applications]);

  return (
    <div className="admin-dash">
      {/* Page Header */}
      <div className="dashboard-home__welcome">
        <div className="dashboard-home__welcome-top">
          <div>
            <span className="dashboard-home__eyebrow">
              Management Intelligence
            </span>
            <h2>Reports &amp; Analytics</h2>
            <p>
              Detailed scheme performance and regional application distribution
              derived from live backend records.
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

      <Milestone3ReportExports />

      {/* Scheme Performance Table */}
      <div className="dash-section">
        <div className="dash-section__header">
          <div>
            <h3 className="dash-section__title">Scheme Performance Summary</h3>
            <span style={{ fontSize: "0.82rem", color: "var(--color-text-muted)" }}>
              Application volumes, approved grants, and disbursement allocations per scheme
            </span>
          </div>
        </div>

        <div className="dash-table-wrap">
          <table className="dash-table">
            <thead>
              <tr>
                <th>Scheme Name</th>
                <th>Department</th>
                <th>Applications</th>
                <th>Approved</th>
                <th>Approval Rate</th>
                <th>Rejected</th>
                <th>Rejection Rate</th>
                <th>Under Review</th>
                <th>Funds Requested</th>
                <th>Approved Grants</th>
                <th>Disbursed</th>
              </tr>
            </thead>
            <tbody>
              {loading ? (
                <tr>
                  <td colSpan="11" style={{ textAlign: "center", padding: "28px" }}>
                    Loading scheme reports...
                  </td>
                </tr>
              ) : schemeAnalytics.length === 0 ? (
                <tr>
                  <td colSpan="11" style={{ textAlign: "center", padding: "28px" }}>
                    No schemes registered.
                  </td>
                </tr>
              ) : (
                schemeAnalytics.map((item) => (
                  <tr key={item.id}>
                    <td>
                      <strong>{item.name}</strong>
                    </td>
                    <td>{item.department || "General"}</td>
                    <td>
                      <strong>{item.total}</strong>
                    </td>
                    <td>
                      <span style={{ color: "#16a34a", fontWeight: 700 }}>
                        {item.approved}
                      </span>
                    </td>
                    <td>
                      <div className="analytics-progress-wrap" style={{ minWidth: "100px" }}>
                        <span style={{ fontSize: "0.78rem", fontWeight: 600 }}>
                          {item.approvalRate}%
                        </span>
                        <div className="analytics-progress-bar">
                          <div
                            className="analytics-progress-fill analytics-progress-fill--success"
                            style={{ width: `${item.approvalRate}%` }}
                          />
                        </div>
                      </div>
                    </td>
                    <td>
                      <span style={{ color: item.rejected > 0 ? "#dc2626" : "inherit", fontWeight: item.rejected > 0 ? 600 : 400 }}>
                        {item.rejected}
                      </span>
                    </td>
                    <td>
                      <span style={{ fontSize: "0.82rem", color: item.rejectionRate > 0 ? "#dc2626" : "var(--color-text-muted)" }}>
                        {item.rejectionRate}%
                      </span>
                    </td>
                    <td>{item.underReview}</td>
                    <td>{formatCurrency(item.fundsRequested)}</td>
                    <td>
                      <strong>{formatCurrency(item.fundsApproved)}</strong>
                    </td>
                    <td>
                      <span style={{ color: "#2563eb", fontWeight: 600 }}>
                        {formatCurrency(item.fundsDisbursed)}
                      </span>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>

      {/* Regional & State Breakdown */}
      <div className="dash-section">
        <div className="dash-section__header">
          <div>
            <h3 className="dash-section__title">Jurisdiction &amp; Regional Breakdown</h3>
            <span style={{ fontSize: "0.82rem", color: "var(--color-text-muted)" }}>
              State-wise distribution, active districts, and regional approval ratios
            </span>
          </div>
        </div>

        <div className="dash-table-wrap">
          <table className="dash-table">
            <thead>
              <tr>
                <th>State / Region</th>
                <th>Districts Active</th>
                <th>Total Applications</th>
                <th>Approved</th>
                <th>Approval Rate</th>
                <th>Rejected</th>
                <th>Rejection Rate</th>
                <th>Under Review</th>
              </tr>
            </thead>
            <tbody>
              {loading ? (
                <tr>
                  <td colSpan="8" style={{ textAlign: "center", padding: "28px" }}>
                    Loading regional data...
                  </td>
                </tr>
              ) : regionAnalytics.length === 0 ? (
                <tr>
                  <td colSpan="8" style={{ textAlign: "center", padding: "28px" }}>
                    No regional application data recorded yet.
                  </td>
                </tr>
              ) : (
                regionAnalytics.map((reg) => (
                  <tr key={reg.state}>
                    <td>
                      <strong>{reg.state}</strong>
                    </td>
                    <td>
                      <span className="dash-badge dash-badge--neutral">
                        {reg.districtCount} districts
                      </span>
                    </td>
                    <td>
                      <strong>{reg.total}</strong>
                    </td>
                    <td>
                      <span style={{ color: "#16a34a", fontWeight: 700 }}>
                        {reg.approved}
                      </span>
                    </td>
                    <td>
                      <div className="analytics-progress-wrap" style={{ minWidth: "100px" }}>
                        <span style={{ fontSize: "0.78rem", fontWeight: 600 }}>
                          {reg.approvalRate}%
                        </span>
                        <div className="analytics-progress-bar">
                          <div
                            className="analytics-progress-fill analytics-progress-fill--info"
                            style={{ width: `${reg.approvalRate}%` }}
                          />
                        </div>
                      </div>
                    </td>
                    <td>
                      <span style={{ color: reg.rejected > 0 ? "#dc2626" : "inherit", fontWeight: reg.rejected > 0 ? 600 : 400 }}>
                        {reg.rejected}
                      </span>
                    </td>
                    <td>
                      <span style={{ fontSize: "0.82rem", color: reg.rejectionRate > 0 ? "#dc2626" : "var(--color-text-muted)" }}>
                        {reg.rejectionRate}%
                      </span>
                    </td>
                    <td>{reg.underReview}</td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>

      {/* District-Level Analytics Breakdown */}
      {districtAnalytics.length > 0 && (
        <div className="dash-section">
          <div className="dash-section__header">
            <div>
              <h3 className="dash-section__title">District-Level Performance &amp; Disbursement</h3>
              <span style={{ fontSize: "0.82rem", color: "var(--color-text-muted)" }}>
                District-wise approvals, rejections, grant disbursements, and milestone compliance
              </span>
            </div>
          </div>

          <div className="dash-table-wrap">
            <table className="dash-table">
              <thead>
                <tr>
                  <th>State</th>
                  <th>District</th>
                  <th>Applications</th>
                  <th>Approved</th>
                  <th>Rejected</th>
                  <th>Approved Funds</th>
                  <th>Disbursed Funds</th>
                  <th>Pending Release</th>
                  <th>Compliance Rate</th>
                </tr>
              </thead>
              <tbody>
                {districtAnalytics.map((dist, idx) => (
                  <tr key={`${dist.state}-${dist.district}-${idx}`}>
                    <td>{dist.state}</td>
                    <td><strong>{dist.district}</strong></td>
                    <td>{dist.totalApplications}</td>
                    <td>
                      <span style={{ color: "#16a34a", fontWeight: 600 }}>
                        {dist.approvedApplications}
                      </span>
                    </td>
                    <td>
                      <span style={{ color: dist.rejectedApplications > 0 ? "#dc2626" : "inherit" }}>
                        {dist.rejectedApplications}
                      </span>
                    </td>
                    <td>{formatCurrency(dist.totalApprovedAmount)}</td>
                    <td>
                      <span style={{ color: "#2563eb", fontWeight: 600 }}>
                        {formatCurrency(dist.totalDisbursedAmount)}
                      </span>
                    </td>
                    <td>{formatCurrency(dist.pendingDisbursementAmount)}</td>
                    <td>
                      <span className="dash-badge dash-badge--neutral">
                        {dist.complianceRate != null ? `${dist.complianceRate}%` : "100%"}
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </div>
  );
}

export default AdminReports;
