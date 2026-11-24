import { useCallback, useEffect, useMemo, useState } from "react";
import {
  AlertCircle,
  AlertTriangle,
  BadgeCheck,
  CheckCircle2,
  CircleDollarSign,
  Clock,
  Download,
  Eye,
  FileCheck2,
  FileClock,
  FileText,
  Filter,
  Layers,
  MapPin,
  RefreshCw,
  Search,
  ShieldAlert,
  ShieldCheck,
  UserCheck,
  UserRound,
  X,
} from "lucide-react";
import {
  downloadApplicationDocument,
  getVerificationHistory,
  getVerificationQueue,
  submitVerificationDecision,
} from "../services/grantApplicationService";
import { getSchemeById } from "../services/schemeService";
import "../styles/OfficerDashboards.css";

export default function FinanceOfficerDashboard() {
  const [queue, setQueue] = useState([]);
  const [history, setHistory] = useState([]);
  const [schemesMap, setSchemesMap] = useState({});
  const [loading, setLoading] = useState(true);
  const [actionLoading, setActionLoading] = useState(false);
  const [error, setError] = useState("");
  const [successNotice, setSuccessNotice] = useState("");

  // Filters & Search
  const [search, setSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState("ALL");
  const [selectedApp, setSelectedApp] = useState(null);
  const [selectedScheme, setSelectedScheme] = useState(null);
  const [schemeLoading, setSchemeLoading] = useState(false);
  const [modalTab, setModalTab] = useState("budgetCheck"); // 'budgetCheck' | 'details' | 'documents' | 'history' | 'actions'

  // Financial Form State
  const [financialChecklist, setFinancialChecklist] = useState({
    financialComplianceVerified: true,
    budgetAvailable: true,
  });
  const [remarks, setRemarks] = useState("");
  const [rejectionReason, setRejectionReason] = useState("");
  const [correctionReason, setCorrectionReason] = useState("");

  const loadData = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const [queueRes, historyRes] = await Promise.all([
        getVerificationQueue(),
        getVerificationHistory().catch(() => ({ data: [] })),
      ]);
      setQueue(Array.isArray(queueRes.data) ? queueRes.data : []);
      setHistory(Array.isArray(historyRes.data) ? historyRes.data : []);
    } catch (err) {
      setError(err.response?.data?.message || "Unable to load finance verification queue.");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadData();
  }, [loadData]);

  // Merge unique applications for display
  const allApplications = useMemo(() => {
    const map = new Map();
    queue.forEach((app) => map.set(app.id, app));
    history.forEach((app) => {
      if (!map.has(app.id)) map.set(app.id, app);
    });
    return Array.from(map.values()).sort((a, b) => b.id - a.id);
  }, [queue, history]);

  // Metrics
  const totalQueue = allApplications.length;
  const pendingFinance = queue.filter(
    (a) => a.workflowStage === "FINANCE" && (a.status === "PENDING_FINANCE_VERIFICATION" || a.status === "DISTRICT_VERIFIED")
  ).length;
  const underReview = queue.filter(
    (a) => a.workflowStage === "FINANCE" && a.status === "FINANCE_REVIEW"
  ).length;
  const financeApprovedCount = allApplications.filter(
    (a) => a.status === "FINANCE_APPROVED" || a.status === "APPROVED"
  ).length;
  const correctionCount = allApplications.filter(
    (a) => a.status === "CORRECTION_REQUIRED" || a.status === "NEEDS_CORRECTION"
  ).length;
  const rejectedCount = allApplications.filter(
    (a) => a.status === "FINANCE_REJECTED" || a.status === "REJECTED"
  ).length;

  // Filtered applications
  const filteredApps = useMemo(() => {
    return allApplications.filter((app) => {
      const term = search.trim().toLowerCase();
      const matchesSearch =
        !term ||
        String(app.id).includes(term) ||
        (app.beneficiaryName && app.beneficiaryName.toLowerCase().includes(term)) ||
        (app.schemeName && app.schemeName.toLowerCase().includes(term)) ||
        (app.beneficiaryDetails?.district && app.beneficiaryDetails.district.toLowerCase().includes(term));

      if (!matchesSearch) return false;

      if (statusFilter === "ALL") return true;
      if (statusFilter === "PENDING") {
        return (
          app.workflowStage === "FINANCE" &&
          (app.status === "PENDING_FINANCE_VERIFICATION" || app.status === "DISTRICT_VERIFIED")
        );
      }
      if (statusFilter === "UNDER_REVIEW") {
        return app.status === "FINANCE_REVIEW";
      }
      if (statusFilter === "APPROVED") {
        return app.status === "FINANCE_APPROVED" || app.status === "APPROVED";
      }
      if (statusFilter === "CORRECTION") {
        return app.status === "CORRECTION_REQUIRED" || app.status === "NEEDS_CORRECTION";
      }
      if (statusFilter === "REJECTED") {
        return app.status === "FINANCE_REJECTED" || app.status === "REJECTED";
      }
      return true;
    });
  }, [allApplications, search, statusFilter]);

  const openAppDetails = async (app) => {
    setSelectedApp(app);
    setModalTab("budgetCheck");
    setRemarks("");
    setRejectionReason("");
    setCorrectionReason("");
    setFinancialChecklist({
      financialComplianceVerified: true,
      budgetAvailable: true,
    });
    setError("");
    setSuccessNotice("");

    // Fetch actual persisted scheme data
    if (app.schemeId) {
      if (schemesMap[app.schemeId]) {
        setSelectedScheme(schemesMap[app.schemeId]);
      } else {
        setSchemeLoading(true);
        try {
          const res = await getSchemeById(app.schemeId);
          setSelectedScheme(res.data);
          setSchemesMap((prev) => ({ ...prev, [app.schemeId]: res.data }));
        } catch (err) {
          console.warn("Could not load full scheme metadata", err);
          setSelectedScheme(null);
        } finally {
          setSchemeLoading(false);
        }
      }
    } else {
      setSelectedScheme(null);
    }
  };

  const handleDocumentDownload = async (docId, fileName) => {
    try {
      const res = await downloadApplicationDocument(selectedApp.id, docId);
      const url = window.URL.createObjectURL(new Blob([res.data]));
      const link = document.createElement("a");
      link.href = url;
      link.setAttribute("download", fileName || `document-${docId}.pdf`);
      document.body.appendChild(link);
      link.click();
      link.remove();
    } catch (err) {
      setError(err.response?.data?.message || "Failed to download document.");
    }
  };

  const handleDecision = async (decisionType) => {
    if (!selectedApp) return;
    setError("");
    setSuccessNotice("");

    if (decisionType === "APPROVED") {
      if (!financialChecklist.financialComplianceVerified || !financialChecklist.budgetAvailable) {
        setError("Both Financial Compliance and Budget Availability must be verified prior to approval.");
        return;
      }
      if (
        selectedScheme?.maximumGrantAmount &&
        selectedApp.requestedAmount &&
        Number(selectedApp.requestedAmount) > Number(selectedScheme.maximumGrantAmount)
      ) {
        setError(
          `Requested amount (${formatCurrency(selectedApp.requestedAmount)}) exceeds scheme maximum limit (${formatCurrency(
            selectedScheme.maximumGrantAmount
          )}). Cannot approve above scheme limit.`
        );
        return;
      }
      if (!remarks.trim()) {
        setError("Financial assessment remarks are required for grant sanction approval.");
        return;
      }
    }

    if (decisionType === "NEEDS_CORRECTION") {
      const text = correctionReason.trim() || remarks.trim();
      if (!text) {
        setError("Mandatory financial correction reason is required.");
        return;
      }
    }

    if (decisionType === "REJECTED") {
      const text = rejectionReason.trim() || remarks.trim();
      if (!text) {
        setError("Mandatory rejection reason is required.");
        return;
      }
    }

    setActionLoading(true);
    try {
      const payload = {
        decision: decisionType,
        remarks: remarks.trim() || correctionReason.trim() || rejectionReason.trim(),
        financialComplianceVerified: financialChecklist.financialComplianceVerified,
        budgetAvailable: financialChecklist.budgetAvailable,
        correctionReason: correctionReason.trim() || remarks.trim() || null,
        rejectionReason: rejectionReason.trim() || remarks.trim() || null,
      };

      await submitVerificationDecision(selectedApp.id, payload);
      setSuccessNotice(
        decisionType === "APPROVED"
          ? `Application #${selectedApp.id} approved! Queued for Sanctioning & Disbursement schedule.`
          : decisionType === "NEEDS_CORRECTION"
          ? `Application #${selectedApp.id} returned for financial corrections.`
          : `Application #${selectedApp.id} rejected.`
      );
      setSelectedApp(null);
      await loadData();
    } catch (err) {
      setError(err.response?.data?.message || "Failed to submit financial approval decision.");
    } finally {
      setActionLoading(false);
    }
  };

  const formatCurrency = (amount) => {
    if (amount == null) return "—";
    return new Intl.NumberFormat("en-IN", {
      style: "currency",
      currency: "INR",
      maximumFractionDigits: 0,
    }).format(amount);
  };

  const getStatusBadge = (status, stage) => {
    if (status === "FINANCE_APPROVED" || status === "APPROVED") {
      return <span className="officer-badge officer-badge--emerald"><CheckCircle2 size={12} /> Finance Approved</span>;
    }
    if (status === "CORRECTION_REQUIRED" || status === "NEEDS_CORRECTION") {
      return <span className="officer-badge officer-badge--amber"><AlertTriangle size={12} /> Financial Correction</span>;
    }
    if (status === "FINANCE_REJECTED" || status === "REJECTED") {
      return <span className="officer-badge officer-badge--rose"><ShieldAlert size={12} /> Finance Rejected</span>;
    }
    if (status === "FINANCE_REVIEW") {
      return <span className="officer-badge officer-badge--purple"><Clock size={12} /> Under Review</span>;
    }
    return <span className="officer-badge officer-badge--blue"><Clock size={12} /> Pending Financial Review</span>;
  };

  const isActionable =
    selectedApp &&
    selectedApp.workflowStage === "FINANCE" &&
    (selectedApp.status === "PENDING_FINANCE_VERIFICATION" ||
      selectedApp.status === "DISTRICT_VERIFIED" ||
      selectedApp.status === "FINANCE_REVIEW");

  const requestedNum = Number(selectedApp?.requestedAmount || 0);
  const maxGrantNum = Number(selectedScheme?.maximumGrantAmount || Infinity);
  const isWithinLimit = requestedNum <= maxGrantNum;

  return (
    <div className="officer-dash">
      {/* Header */}
      <header className="officer-dash__header">
        <div>
          <span className="officer-dash__eyebrow officer-dash__eyebrow--finance">
            <CircleDollarSign size={14} /> Finance Officer Workspace
          </span>
          <h1>Financial Review & Sanction Approval Dashboard</h1>
          <p>
            Conduct final financial verification on applications forwarded from the District Officer. Check requested grant
            amounts against scheme limits and available budget allocations before sanctioning applications for disbursement.
          </p>
        </div>
        <div className="officer-dash__actions">
          <button className="officer-dash__refresh-btn" onClick={loadData} disabled={loading}>
            <RefreshCw size={15} className={loading ? "is-spinning" : ""} />
            <span>{loading ? "Refreshing..." : "Refresh Queue"}</span>
          </button>
        </div>
      </header>

      {error && (
        <div className="officer-alert officer-alert--error" role="alert">
          <AlertCircle size={18} /> <span>{error}</span>
        </div>
      )}

      {successNotice && (
        <div className="officer-alert officer-alert--success" role="status">
          <CheckCircle2 size={18} /> <span>{successNotice}</span>
        </div>
      )}

      {/* KPI Cards */}
      <section className="officer-dash__kpis">
        <div className="officer-dash__kpi-card">
          <div className="officer-dash__kpi-icon officer-dash__kpi-icon--emerald">
            <Layers size={22} />
          </div>
          <div className="officer-dash__kpi-data">
            <span className="officer-dash__kpi-value">{totalQueue}</span>
            <span className="officer-dash__kpi-label">Total Financial Queue</span>
          </div>
        </div>

        <div className="officer-dash__kpi-card">
          <div className="officer-dash__kpi-icon officer-dash__kpi-icon--blue">
            <Clock size={22} />
          </div>
          <div className="officer-dash__kpi-data">
            <span className="officer-dash__kpi-value">{pendingFinance}</span>
            <span className="officer-dash__kpi-label">Pending Financial Review</span>
          </div>
        </div>

        <div className="officer-dash__kpi-card">
          <div className="officer-dash__kpi-icon officer-dash__kpi-icon--violet">
            <FileClock size={22} />
          </div>
          <div className="officer-dash__kpi-data">
            <span className="officer-dash__kpi-value">{underReview}</span>
            <span className="officer-dash__kpi-label">Under Financial Review</span>
          </div>
        </div>

        <div className="officer-dash__kpi-card">
          <div className="officer-dash__kpi-icon officer-dash__kpi-icon--emerald">
            <CheckCircle2 size={22} />
          </div>
          <div className="officer-dash__kpi-data">
            <span className="officer-dash__kpi-value">{financeApprovedCount}</span>
            <span className="officer-dash__kpi-label">Finance Approved</span>
          </div>
        </div>

        <div className="officer-dash__kpi-card">
          <div className="officer-dash__kpi-icon officer-dash__kpi-icon--amber">
            <AlertTriangle size={22} />
          </div>
          <div className="officer-dash__kpi-data">
            <span className="officer-dash__kpi-value">{correctionCount}</span>
            <span className="officer-dash__kpi-label">Financial Corrections</span>
          </div>
        </div>

        <div className="officer-dash__kpi-card">
          <div className="officer-dash__kpi-icon officer-dash__kpi-icon--rose">
            <ShieldAlert size={22} />
          </div>
          <div className="officer-dash__kpi-data">
            <span className="officer-dash__kpi-value">{rejectedCount}</span>
            <span className="officer-dash__kpi-label">Finance Rejected</span>
          </div>
        </div>
      </section>

      {/* Main Table Section */}
      <section className="officer-dash__section">
        <div className="officer-dash__toolbar">
          <div className="officer-dash__search-box">
            <Search size={16} />
            <input
              type="text"
              placeholder="Search by ID, applicant name, scheme or district..."
              value={search}
              onChange={(e) => setSearch(e.target.value)}
            />
            {search && (
              <button
                type="button"
                onClick={() => setSearch("")}
                style={{ background: "transparent", border: "none", cursor: "pointer" }}
              >
                <X size={14} />
              </button>
            )}
          </div>

          <div className="officer-dash__filters">
            <select
              className="officer-dash__select"
              value={statusFilter}
              onChange={(e) => setStatusFilter(e.target.value)}
            >
              <option value="ALL">All Applications ({allApplications.length})</option>
              <option value="PENDING">Pending Finance Review ({pendingFinance})</option>
              <option value="UNDER_REVIEW">Under Review ({underReview})</option>
              <option value="APPROVED">Finance Approved ({financeApprovedCount})</option>
              <option value="CORRECTION">Corrections ({correctionCount})</option>
              <option value="REJECTED">Rejected ({rejectedCount})</option>
            </select>
          </div>
        </div>

        {/* Applications Table */}
        <div className="officer-dash__table-wrapper">
          <table className="officer-dash__table">
            <thead>
              <tr>
                <th>App ID</th>
                <th>Beneficiary</th>
                <th>Scheme</th>
                <th>Requested Amount</th>
                <th>Score</th>
                <th>Status</th>
                <th>Action</th>
              </tr>
            </thead>
            <tbody>
              {filteredApps.length === 0 ? (
                <tr>
                  <td colSpan={7}>
                    <div className="officer-empty">
                      <FileText size={36} />
                      <p>{loading ? "Loading financial queue..." : "No applications found in finance queue."}</p>
                    </div>
                  </td>
                </tr>
              ) : (
                filteredApps.map((app) => (
                  <tr key={app.id}>
                    <td>
                      <span className="officer-dash__app-id">#{app.id}</span>
                    </td>
                    <td>
                      <div style={{ fontWeight: 650 }}>{app.beneficiaryName || "Applicant #" + app.beneficiaryId}</div>
                      <small style={{ color: "var(--color-text-muted)" }}>
                        {app.beneficiaryDetails?.district || "—"}, {app.beneficiaryDetails?.state || "—"}
                      </small>
                    </td>
                    <td>
                      <div style={{ maxWidth: 220, whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis" }} title={app.schemeName}>
                        {app.schemeName || "Scheme #" + app.schemeId}
                      </div>
                    </td>
                    <td>
                      <strong style={{ color: "var(--color-primary)" }}>{formatCurrency(app.requestedAmount)}</strong>
                    </td>
                    <td>
                      {app.eligibilityEvaluation?.scoreOutOf100 != null ? (
                        <span className="officer-dash__score-pill officer-dash__score-pill--high">
                          {app.eligibilityEvaluation.scoreOutOf100}%
                        </span>
                      ) : (
                        "—"
                      )}
                    </td>
                    <td>{getStatusBadge(app.status, app.workflowStage)}</td>
                    <td>
                      <button
                        className="officer-dash__btn-review"
                        onClick={() => openAppDetails(app)}
                      >
                        <Eye size={14} />
                        <span>{app.workflowStage === "FINANCE" ? "Verify Budget" : "View"}</span>
                      </button>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </section>

      {/* Detail / Action Modal */}
      {selectedApp && (
        <div className="officer-modal-backdrop" onClick={() => setSelectedApp(null)}>
          <div className="officer-modal" onClick={(e) => e.stopPropagation()}>
            <div className="officer-modal__header">
              <div>
                <h2>Financial Review: Application #{selectedApp.id}</h2>
                <small style={{ color: "var(--color-text-muted)" }}>
                  {selectedApp.schemeName} · Requested: {formatCurrency(selectedApp.requestedAmount)}
                </small>
              </div>
              <button className="officer-modal__close-btn" onClick={() => setSelectedApp(null)}>
                <X size={20} />
              </button>
            </div>

            {/* Modal Tabs */}
            <div className="officer-modal__tabs">
              <button
                className={`officer-modal__tab ${modalTab === "budgetCheck" ? "officer-modal__tab--active" : ""}`}
                onClick={() => setModalTab("budgetCheck")}
              >
                <CircleDollarSign size={15} /> Scheme Budget & Allocation Check
              </button>
              <button
                className={`officer-modal__tab ${modalTab === "details" ? "officer-modal__tab--active" : ""}`}
                onClick={() => setModalTab("details")}
              >
                <UserRound size={15} /> Applicant & Proposal
              </button>
              <button
                className={`officer-modal__tab ${modalTab === "documents" ? "officer-modal__tab--active" : ""}`}
                onClick={() => setModalTab("documents")}
              >
                <FileCheck2 size={15} /> Financial Proofs ({selectedApp.documents?.length || 0})
              </button>
              <button
                className={`officer-modal__tab ${modalTab === "history" ? "officer-modal__tab--active" : ""}`}
                onClick={() => setModalTab("history")}
              >
                <FileClock size={15} /> Verification History
              </button>
              {isActionable && (
                <button
                  className={`officer-modal__tab ${modalTab === "actions" ? "officer-modal__tab--active" : ""}`}
                  onClick={() => setModalTab("actions")}
                  style={{ color: "var(--color-primary)", fontWeight: 700 }}
                >
                  <FileCheck2 size={15} /> Sanction Approval Actions
                </button>
              )}
            </div>

            {/* Modal Body */}
            <div className="officer-modal__body">
              {modalTab === "budgetCheck" && (
                <div style={{ display: "flex", flexDirection: "column", gap: 16 }}>
                  <h3>Persisted Scheme Allocation & Grant Limits</h3>
                  <div className="officer-info-grid">
                    <div className="officer-info-item">
                      <span className="officer-info-label">Scheme Name</span>
                      <span className="officer-info-value">{selectedScheme?.schemeName || selectedApp.schemeName}</span>
                    </div>
                    <div className="officer-info-item">
                      <span className="officer-info-label">Total Allocated Scheme Budget</span>
                      <span className="officer-info-value" style={{ fontSize: "1.15rem", color: "#16a34a" }}>
                        {formatCurrency(selectedScheme?.allocatedFunds)}
                      </span>
                    </div>
                    <div className="officer-info-item">
                      <span className="officer-info-label">Grant Boundary Limits</span>
                      <span className="officer-info-value">
                        Min: {formatCurrency(selectedScheme?.minimumGrantAmount)} · Max: {formatCurrency(selectedScheme?.maximumGrantAmount)}
                      </span>
                    </div>
                    <div className="officer-info-item">
                      <span className="officer-info-label">Requested Grant Amount</span>
                      <span className="officer-info-value" style={{ fontSize: "1.2rem", color: "var(--color-primary)" }}>
                        {formatCurrency(selectedApp.requestedAmount)}
                      </span>
                    </div>
                    <div className="officer-info-item">
                      <span className="officer-info-label">Recommended Grant Amount</span>
                      <span className="officer-info-value" style={{ fontSize: "1.1rem" }}>
                        {formatCurrency(selectedApp.requestedAmount)}
                      </span>
                    </div>
                    <div className="officer-info-item">
                      <span className="officer-info-label">Financial Ceiling Compliance</span>
                      <span className="officer-info-value">
                        {isWithinLimit ? (
                          <span className="officer-badge officer-badge--emerald">
                            <CheckCircle2 size={12} /> Compliant within Scheme Limit
                          </span>
                        ) : (
                          <span className="officer-badge officer-badge--rose">
                            <AlertCircle size={12} /> Exceeds Maximum Scheme Cap
                          </span>
                        )}
                      </span>
                    </div>
                  </div>

                  <div
                    style={{
                      padding: 14,
                      borderRadius: 10,
                      background: "var(--color-surface)",
                      border: "1px solid var(--color-outline-variant)",
                      fontSize: "0.88rem",
                      lineHeight: 1.5,
                      color: "var(--color-text-muted)",
                    }}
                  >
                    <strong>Disbursement Notice:</strong> Finance approval sanctions the grant under the configured scheme
                    budget. Release of funds is performed in stages according to the disbursement milestones workflow.
                  </div>
                </div>
              )}

              {modalTab === "details" && (
                <div style={{ display: "flex", flexDirection: "column", gap: 16 }}>
                  <h3>Beneficiary Information</h3>
                  <div className="officer-info-grid">
                    <div className="officer-info-item">
                      <span className="officer-info-label">Full Name</span>
                      <span className="officer-info-value">{selectedApp.beneficiaryName || "—"}</span>
                    </div>
                    <div className="officer-info-item">
                      <span className="officer-info-label">Email</span>
                      <span className="officer-info-value">{selectedApp.beneficiaryDetails?.email || "—"}</span>
                    </div>
                    <div className="officer-info-item">
                      <span className="officer-info-label">Phone</span>
                      <span className="officer-info-value">{selectedApp.beneficiaryDetails?.phone || "—"}</span>
                    </div>
                    <div className="officer-info-item">
                      <span className="officer-info-label">Annual Income</span>
                      <span className="officer-info-value">{formatCurrency(selectedApp.beneficiaryDetails?.annualIncome)}</span>
                    </div>
                    <div className="officer-info-item">
                      <span className="officer-info-label">Category</span>
                      <span className="officer-info-value">{selectedApp.beneficiaryDetails?.category || "—"}</span>
                    </div>
                    <div className="officer-info-item">
                      <span className="officer-info-label">State / District</span>
                      <span className="officer-info-value">
                        {selectedApp.beneficiaryDetails?.district || "—"}, {selectedApp.beneficiaryDetails?.state || "—"}
                      </span>
                    </div>
                    <div className="officer-info-item" style={{ gridColumn: "1 / -1" }}>
                      <span className="officer-info-label">Proposal Details</span>
                      <span className="officer-info-value">{selectedApp.applicationDetails || "No project proposal details."}</span>
                    </div>
                  </div>
                </div>
              )}

              {modalTab === "documents" && (
                <div style={{ display: "flex", flexDirection: "column", gap: 14 }}>
                  <h3>Submitted Financial & Supporting Documents</h3>
                  {!selectedApp.documents || selectedApp.documents.length === 0 ? (
                    <div className="officer-empty">
                      <FileText size={32} />
                      <p>No documents attached.</p>
                    </div>
                  ) : (
                    <div className="officer-doc-list">
                      {selectedApp.documents.map((doc) => (
                        <div className="officer-doc-item" key={doc.id}>
                          <div className="officer-doc-info">
                            <FileText size={20} color="var(--color-primary)" />
                            <div>
                              <strong>{doc.title || doc.fileName}</strong>
                              <div style={{ fontSize: "0.8rem", color: "var(--color-text-muted)" }}>
                                {doc.fileName} · {(doc.fileSize / 1024).toFixed(1)} KB · {doc.contentType}
                              </div>
                            </div>
                          </div>
                          <button
                            className="officer-doc-btn"
                            onClick={() => handleDocumentDownload(doc.id, doc.fileName)}
                          >
                            <Download size={14} /> Download / View
                          </button>
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              )}

              {modalTab === "history" && (
                <div style={{ display: "flex", flexDirection: "column", gap: 16 }}>
                  <h3>Multi-Stage Verification Audit History</h3>
                  {!selectedApp.verificationHistory || selectedApp.verificationHistory.length === 0 ? (
                    <div className="officer-empty">
                      <Clock size={32} />
                      <p>No verification history recorded.</p>
                    </div>
                  ) : (
                    <div className="officer-timeline">
                      {selectedApp.verificationHistory.map((entry, idx) => (
                        <div className="officer-timeline-item" key={idx}>
                          <div className="officer-timeline-header">
                            <span>{entry.stage} REVIEW</span>
                            <span className="officer-badge officer-badge--blue">{entry.action}</span>
                            <small style={{ color: "var(--color-text-muted)", marginLeft: "auto" }}>
                              {entry.createdAt ? new Date(entry.createdAt).toLocaleString("en-IN") : ""}
                            </small>
                          </div>
                          <div style={{ fontSize: "0.84rem", color: "var(--color-text-muted)" }}>
                            By: {entry.actorName || "System"} ({entry.actorRole || "OFFICER"})
                          </div>
                          {entry.remarks && (
                            <div className="officer-timeline-remarks">
                              <strong>Remarks:</strong> {entry.remarks}
                            </div>
                          )}
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              )}

              {modalTab === "actions" && isActionable && (
                <div style={{ display: "flex", flexDirection: "column", gap: 16 }}>
                  <div className="officer-checklist-card">
                    <span className="officer-checklist-title">1. Financial Compliance Checklist</span>
                    <label className="officer-checkbox-label">
                      <input
                        type="checkbox"
                        checked={financialChecklist.financialComplianceVerified}
                        onChange={(e) =>
                          setFinancialChecklist({
                            ...financialChecklist,
                            financialComplianceVerified: e.target.checked,
                          })
                        }
                      />
                      <span>Financial Compliance Verified (Requested amount is verified and within budget boundaries)</span>
                    </label>
                    <label className="officer-checkbox-label">
                      <input
                        type="checkbox"
                        checked={financialChecklist.budgetAvailable}
                        onChange={(e) =>
                          setFinancialChecklist({
                            ...financialChecklist,
                            budgetAvailable: e.target.checked,
                          })
                        }
                      />
                      <span>Budget Available in Scheme Allocation ({formatCurrency(selectedScheme?.allocatedFunds)})</span>
                    </label>
                  </div>

                  <div className="officer-remarks-area">
                    <label>2. Financial Officer Sanction Remarks (Mandatory)</label>
                    <textarea
                      placeholder="Record financial findings, budget compliance certification, and sanction remarks..."
                      value={remarks}
                      onChange={(e) => setRemarks(e.target.value)}
                    />
                  </div>

                  <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14 }}>
                    <div className="officer-remarks-area">
                      <label>Financial Correction Reason (If returning to applicant)</label>
                      <input
                        type="text"
                        placeholder="e.g. Please re-submit itemized quotation with GST invoices"
                        value={correctionReason}
                        onChange={(e) => setCorrectionReason(e.target.value)}
                        style={{ padding: 10, borderRadius: 8, border: "1px solid var(--color-outline-variant)" }}
                      />
                    </div>
                    <div className="officer-remarks-area">
                      <label>Financial Rejection Reason (If rejecting)</label>
                      <input
                        type="text"
                        placeholder="e.g. Requested amount exceeds scheme allocation ceiling"
                        value={rejectionReason}
                        onChange={(e) => setRejectionReason(e.target.value)}
                        style={{ padding: 10, borderRadius: 8, border: "1px solid var(--color-outline-variant)" }}
                      />
                    </div>
                  </div>
                </div>
              )}
            </div>

            {/* Modal Actions Footer */}
            {isActionable && modalTab === "actions" && (
              <div className="officer-modal__actions">
                <button
                  type="button"
                  className="officer-btn officer-btn--warning"
                  disabled={actionLoading}
                  onClick={() => handleDecision("NEEDS_CORRECTION")}
                >
                  <AlertTriangle size={15} /> Request Financial Corrections
                </button>
                <button
                  type="button"
                  className="officer-btn officer-btn--danger"
                  disabled={actionLoading}
                  onClick={() => handleDecision("REJECTED")}
                >
                  <ShieldAlert size={15} /> Reject Application
                </button>
                <button
                  type="button"
                  className="officer-btn officer-btn--primary"
                  disabled={actionLoading || !isWithinLimit}
                  onClick={() => handleDecision("APPROVED")}
                  title={!isWithinLimit ? "Requested amount exceeds maximum grant limit" : "Approve grant"}
                >
                  <CheckCircle2 size={15} /> Approve & Queue for Disbursement
                </button>
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
