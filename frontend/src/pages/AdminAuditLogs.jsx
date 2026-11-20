import { useEffect, useState, useMemo } from "react";
import { Link } from "react-router-dom";
import {
  Calendar,
  ChevronRight,
  ClipboardList,
  ExternalLink,
  Filter,
  History,
  RefreshCw,
  Search,
  User,
  X,
} from "lucide-react";
import { getApplications } from "../services/grantApplicationService";
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
  const [applications, setApplications] = useState([]);
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
      const res = await getApplications();
      setApplications(res.data || []);
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

  // Aggregated & deduplicated audit events
  const auditEvents = useMemo(() => {
    const events = [];
    const seen = new Set();

    applications.forEach((app) => {
      (app.statusHistory || []).forEach((hist) => {
        const key = `${app.id}-${hist.id || ""}-${hist.action || ""}-${hist.status || ""}-${hist.createdAt || ""}`;
        if (seen.has(key)) return;
        seen.add(key);

        events.push({
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

    events.sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt));

    return events.filter((event) => {
      if (actionFilter && event.action !== actionFilter) {
        return false;
      }
      if (searchQuery.trim()) {
        const q = searchQuery.toLowerCase().trim();
        const idMatch = String(event.applicationId).includes(q);
        const actorMatch = (event.actorName || "").toLowerCase().includes(q);
        const schemeMatch = (event.schemeName || "").toLowerCase().includes(q);
        const citizenMatch = (event.beneficiaryName || "").toLowerCase().includes(q);
        const remarksMatch = (event.remarks || "").toLowerCase().includes(q);
        if (!idMatch && !actorMatch && !schemeMatch && !citizenMatch && !remarksMatch) {
          return false;
        }
      }
      return true;
    });
  }, [applications, actionFilter, searchQuery]);

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
              decisions, status transitions, and administrative actions.
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
            placeholder="Search by Actor Name, Application ID, Scheme, Citizen, or Remarks..."
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
            <option value="REJECT">Rejections</option>
            <option value="WITHDRAW">Withdrawals</option>
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
                    <Link
                      to="/admin/applications"
                      state={{ selectedApplicationId: ev.applicationId }}
                      className="dash-badge dash-badge--neutral"
                      style={{ textDecoration: "none" }}
                    >
                      App #{ev.applicationId}
                      <ExternalLink size={11} style={{ marginLeft: "4px" }} />
                    </Link>
                    {ev.toStatus && (
                      <span
                        className="dash-badge dash-badge--info"
                        style={{ fontSize: "0.72rem" }}
                      >
                        {ev.fromStatus ? `${ev.fromStatus} ➔ ` : ""}
                        {ev.toStatus}
                      </span>
                    )}
                  </div>
                  <span className="audit-item__time">
                    {formatDateTime(ev.createdAt)}
                  </span>
                </div>

                <p className="audit-item__desc">
                  <strong>Authorized Actor:</strong> {ev.actorName} •{" "}
                  <strong>Beneficiary:</strong> {ev.beneficiaryName} •{" "}
                  <strong>Scheme:</strong> {ev.schemeName}
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
