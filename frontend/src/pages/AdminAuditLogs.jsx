import { useEffect, useState, useMemo } from "react";
import { Link } from "react-router-dom";
import {
  ExternalLink,
  History,
  RefreshCw,
  Search,
  X,
} from "lucide-react";
import { getAuditLogs } from "../services/auditService";
import "../styles/DashboardHome.css";
import "../styles/AdminDashboardHome.css";

function formatDateTime(dt) {
  if (!dt) return "—";
  try {
    const d = new Date(dt);
    return isNaN(d.getTime())
      ? "—"
      : `${d.toLocaleDateString("en-IN", {
          year: "numeric",
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

function AdminAuditLogs() {
  const [logs, setLogs] = useState([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState("");
  const [searchQuery, setSearchQuery] = useState("");
  const [actionFilter, setActionFilter] = useState("");

  const loadData = async (isManual = false) => {
    if (isManual) setRefreshing(true);
    else setLoading(true);
    setError("");

    try {
      const res = await getAuditLogs();
      setLogs(res.data || []);
    } catch (err) {
      setError(
        err.response?.data?.message ||
          "Unable to load administrative audit logs."
      );
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  };

  useEffect(() => {
    loadData();
  }, []);

  const auditEvents = useMemo(() => {
    return logs.filter((event) => {
      if (actionFilter) {
        if (actionFilter === "FINANCIAL") {
          if (event.category !== "FINANCIAL") return false;
        } else if (actionFilter === "COMPLIANCE") {
          if (event.category !== "COMPLIANCE") return false;
        } else if (actionFilter === "ADMIN_MANAGEMENT") {
          if (event.category !== "ADMIN_MANAGEMENT") return false;
        } else if (actionFilter === "SCHEME") {
          if (event.category !== "SCHEME") return false;
        } else if (actionFilter === "FIELD_VERIFICATION") {
          if (event.action !== "FIELD_VERIFICATION" && event.action !== "VERIFIED") return false;
        } else if (actionFilter === "DISTRICT_REVIEW") {
          if (event.action !== "DISTRICT_REVIEW" && !(event.category === "VERIFICATION" && event.action === "APPROVED" && event.actorRole === "DISTRICT")) return false;
        } else if (actionFilter === "FINANCE_REVIEW") {
          if (event.action !== "FINANCE_REVIEW" && !(event.category === "VERIFICATION" && event.action === "APPROVED" && (event.actorRole === "FINANCE" || event.actorRole === "ADMIN"))) return false;
        } else if (event.action !== actionFilter) {
          return false;
        }
      }

      if (searchQuery.trim()) {
        const q = searchQuery.toLowerCase().trim();
        const idMatch = String(event.applicationId || event.targetId || "").includes(q);
        const actorMatch = (event.actorName || "").toLowerCase().includes(q) ||
          (event.actorEmail || "").toLowerCase().includes(q);
        const schemeMatch = (event.schemeName || "").toLowerCase().includes(q);
        const citizenMatch = (event.beneficiaryName || "").toLowerCase().includes(q);
        const remarksMatch = (event.remarks || "").toLowerCase().includes(q);
        const targetMatch = (event.targetEntity || "").toLowerCase().includes(q);
        const actionMatch = (event.action || "").toLowerCase().includes(q);
        const categoryMatch = (event.category || "").toLowerCase().includes(q);

        if (!idMatch && !actorMatch && !schemeMatch && !citizenMatch && !remarksMatch && !targetMatch && !actionMatch && !categoryMatch) {
          return false;
        }
      }

      return true;
    });
  }, [logs, actionFilter, searchQuery]);

  return (
    <div className="admin-dash">
      {/* Header */}
      <div className="dashboard-home__welcome">
        <div className="dashboard-home__welcome-top">
          <div>
            <span className="dashboard-home__eyebrow">
              Compliance &amp; Governance
            </span>
            <h2>Administrative Audit Trail</h2>
            <p>
              Immutable audit history of citizen applications, officer verification
              decisions, financial disbursements, compliance reviews, and administrative actions.
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
            {refreshing ? "Refreshing..." : "Sync Logs"}
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

      {/* Filter Toolbar */}
      <div className="admin-dash-toolbar">
        <div className="admin-dash-toolbar__search-group">
          <Search size={16} className="admin-dash-toolbar__search-icon" />
          <input
            type="text"
            className="admin-dash-toolbar__input"
            placeholder="Search by Actor, Email, Application, Scheme, Citizen, Action, or Details..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
          />
        </div>

        <div className="admin-dash-toolbar__filters">
          <select
            className="admin-dash-toolbar__select"
            value={actionFilter}
            onChange={(e) => setActionFilter(e.target.value)}
          >
            <option value="">All Actions ({auditEvents.length})</option>
            <option value="SUBMIT">Citizen Submissions</option>
            <option value="FIELD_VERIFICATION">Field Review Decisions</option>
            <option value="DISTRICT_REVIEW">District Review Decisions</option>
            <option value="FINANCE_REVIEW">Finance Approvals</option>
            <option value="NEEDS_CORRECTION">Correction Requests</option>
            <option value="ESCALATED">Workflow Escalations</option>
            <option value="REJECT">Rejections</option>
            <option value="WITHDRAWN">Withdrawals</option>
            <option value="FINANCIAL">Financial Plans &amp; Disbursements</option>
            <option value="COMPLIANCE">Compliance Reviews &amp; Milestones</option>
            <option value="ADMIN_MANAGEMENT">Administrative User Management</option>
            <option value="SCHEME">Scheme &amp; Budget Management</option>
          </select>

          {(searchQuery || actionFilter) && (
            <button
              type="button"
              className="admin-dash-btn admin-dash-btn--outline admin-dash-btn--small"
              onClick={() => {
                setSearchQuery("");
                setActionFilter("");
              }}
            >
              <X size={14} />
              Reset
            </button>
          )}
        </div>
      </div>

      {/* Audit Events List */}
      <div className="audit-list">
        {loading ? (
          <div className="dash-empty-card">
            <RefreshCw size={24} className="is-spinning" />
            <h4>Loading Audit History...</h4>
          </div>
        ) : auditEvents.length === 0 ? (
          <div className="dash-empty-card">
            <History size={32} />
            <h4>No Audit Events Recorded</h4>
            <p>
              No administrative actions match your current search or filter criteria.
            </p>
          </div>
        ) : (
          auditEvents.map((ev) => (
            <div key={ev.id} className="audit-item">
              <div className="audit-item__icon">
                <History size={18} />
              </div>
              <div className="audit-item__body">
                <div className="audit-item__header">
                  <div style={{ display: "flex", alignItems: "center", gap: "8px", flexWrap: "wrap" }}>
                    <span className="audit-item__title">
                      Action: {ev.action}
                    </span>
                    {ev.applicationId && (
                      <Link
                        to="/admin/applications"
                        state={{ selectedApplicationId: ev.applicationId }}
                        className="dash-badge dash-badge--neutral"
                        style={{ textDecoration: "none" }}
                      >
                        App #{ev.applicationId}
                        <ExternalLink size={11} style={{ marginLeft: "4px" }} />
                      </Link>
                    )}
                    {ev.targetEntity && !ev.applicationId && (
                      <span className="dash-badge dash-badge--neutral">
                        {ev.targetEntity}
                      </span>
                    )}
                    {ev.toStatus && (
                      <span
                        className="dash-badge dash-badge--info"
                        style={{ fontSize: "0.72rem" }}
                      >
                        {ev.fromStatus ? `${ev.fromStatus} ➔ ` : ""}
                        {ev.toStatus}
                      </span>
                    )}
                    {ev.category && (
                      <span
                        className="dash-badge dash-badge--neutral"
                        style={{ fontSize: "0.68rem", opacity: 0.8 }}
                      >
                        {ev.category}
                      </span>
                    )}
                  </div>
                  <span className="audit-item__time">
                    {formatDateTime(ev.timestamp)}
                  </span>
                </div>

                <p className="audit-item__desc">
                  <strong>Authorized Actor:</strong> {ev.actorName || ev.actorEmail || "System"} ({ev.actorRole || "SYSTEM"})
                  {ev.beneficiaryName ? ` • Beneficiary: ${ev.beneficiaryName}` : ""}
                  {ev.schemeName ? ` • Scheme: ${ev.schemeName}` : ""}
                  {ev.targetEntity && ev.applicationId ? ` • Target: ${ev.targetEntity}` : ""}
                </p>

                {ev.remarks && (
                  <div className="audit-item__remarks">
                    &ldquo;{ev.remarks}&rdquo;
                  </div>
                )}
              </div>
            </div>
          ))
        )}
      </div>
    </div>
  );
}

export default AdminAuditLogs;
