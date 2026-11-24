import { useCallback, useEffect, useMemo, useState } from "react";
import {
  AlertCircle,
  AlertTriangle,
  CheckCircle2,
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
import "../styles/OfficerDashboards.css";

export default function FieldOfficerDashboard() {
  const [queue, setQueue] = useState([]);
  const [history, setHistory] = useState([]);
  const [loading, setLoading] = useState(true);
  const [actionLoading, setActionLoading] = useState(false);
  const [error, setError] = useState("");
  const [successNotice, setSuccessNotice] = useState("");

  // Filters & Search
  const [search, setSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState("ALL");
  const [selectedApp, setSelectedApp] = useState(null);
  const [modalTab, setModalTab] = useState("details"); // 'details' | 'documents' | 'eligibility' | 'actions' | 'history'

  // Verification Form State
  const [checklist, setChecklist] = useState({
    identityVerified: false,
    addressVerified: false,
    documentsVerified: false,
    applicationDetailsVerified: false,
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
      setError(err.response?.data?.message || "Unable to load assigned applications queue.");
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
  const totalAssigned = allApplications.length;
  const pendingField = queue.filter(
    (a) => a.workflowStage === "FIELD" && (a.status === "PENDING_FIELD_VERIFICATION" || a.status === "SUBMITTED")
  ).length;
  const underReview = queue.filter(
    (a) => a.workflowStage === "FIELD" && a.status === "UNDER_VERIFICATION"
  ).length;
  const verifiedCount = allApplications.filter(
    (a) => a.status === "FIELD_VERIFIED" || a.workflowStage === "DISTRICT" || a.workflowStage === "FINANCE" || a.status === "FINANCE_APPROVED"
  ).length;
  const correctionCount = allApplications.filter(
    (a) => a.status === "CORRECTION_REQUIRED" || a.status === "NEEDS_CORRECTION"
  ).length;
  const rejectedCount = allApplications.filter(
    (a) => a.status === "REJECTED" || a.status === "FINANCE_REJECTED"
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
        return app.workflowStage === "FIELD" && (app.status === "PENDING_FIELD_VERIFICATION" || app.status === "SUBMITTED");
      }
      if (statusFilter === "UNDER_REVIEW") {
        return app.status === "UNDER_VERIFICATION";
      }
      if (statusFilter === "VERIFIED") {
        return app.status === "FIELD_VERIFIED" || app.workflowStage === "DISTRICT" || app.workflowStage === "FINANCE" || a.status === "FINANCE_APPROVED";
      }
      if (statusFilter === "CORRECTION") {
        return app.status === "CORRECTION_REQUIRED" || app.status === "NEEDS_CORRECTION";
      }
      if (statusFilter === "REJECTED") {
        return app.status === "REJECTED" || app.status === "FINANCE_REJECTED";
      }
      return true;
    });
  }, [allApplications, search, statusFilter]);

  const openAppDetails = (app) => {
    setSelectedApp(app);
    setModalTab("details");
    setRemarks("");
    setRejectionReason("");
    setCorrectionReason("");
    setChecklist({
      identityVerified: false,
      addressVerified: false,
      documentsVerified: false,
      applicationDetailsVerified: false,
    });
    setError("");
    setSuccessNotice("");
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

    if (decisionType === "VERIFIED") {
      if (
        !checklist.identityVerified ||
        !checklist.addressVerified ||
        !checklist.documentsVerified ||
        !checklist.applicationDetailsVerified
      ) {
        setError("Please complete all four mandatory verification checklist items before verifying.");
        return;
      }
      if (!remarks.trim()) {
        setError("Field inspection findings / verification remarks are mandatory.");
        return;
      }
    }

    if (decisionType === "NEEDS_CORRECTION") {
      const text = correctionReason.trim() || remarks.trim();
      if (!text) {
        setError("Specific correction details / reasons are required.");
        return;
      }
    }

    if (decisionType === "REJECTED") {
      const text = rejectionReason.trim() || remarks.trim();
      if (!text) {
        setError("A mandatory rejection reason is required.");
        return;
      }
    }

    setActionLoading(true);
    try {
      const payload = {
        decision: decisionType,
        remarks: remarks.trim() || correctionReason.trim() || rejectionReason.trim(),
        identityVerified: checklist.identityVerified,
        addressVerified: checklist.addressVerified,
        documentsVerified: checklist.documentsVerified,
        applicationDetailsVerified: checklist.applicationDetailsVerified,
        correctionReason: correctionReason.trim() || remarks.trim() || null,
        rejectionReason: rejectionReason.trim() || remarks.trim() || null,
      };

      await submitVerificationDecision(selectedApp.id, payload);
      setSuccessNotice(
        decisionType === "VERIFIED"
          ? `Application #${selectedApp.id} verified successfully and forwarded to District Officer!`
          : decisionType === "NEEDS_CORRECTION"
          ? `Application #${selectedApp.id} returned for corrections.`
          : `Application #${selectedApp.id} rejected.`
      );
      setSelectedApp(null);
      await loadData();
    } catch (err) {
      setError(err.response?.data?.message || "Failed to submit verification decision.");
    } finally {
      setActionLoading(false);
    }
  };

  const getStatusBadge = (status, stage) => {
    if (status === "FIELD_VERIFIED" || stage === "DISTRICT" || stage === "FINANCE" || status === "FINANCE_APPROVED") {
      return <span className="officer-badge officer-badge--emerald"><CheckCircle2 size={12} /> Field Verified</span>;
    }
    if (status === "CORRECTION_REQUIRED" || status === "NEEDS_CORRECTION") {
      return <span className="officer-badge officer-badge--amber"><AlertTriangle size={12} /> Correction Needed</span>;
    }
    if (status === "REJECTED" || status === "FINANCE_REJECTED") {
      return <span className="officer-badge officer-badge--rose"><ShieldAlert size={12} /> Rejected</span>;
    }
    if (status === "UNDER_VERIFICATION") {
      return <span className="officer-badge officer-badge--purple"><Clock size={12} /> Under Review</span>;
    }
    return <span className="officer-badge officer-badge--blue"><Clock size={12} /> Pending Verification</span>;
  };

  const formatCurrency = (amount) => {
    if (amount == null) return "—";
    return new Intl.NumberFormat("en-IN", {
      style: "currency",
      currency: "INR",
      maximumFractionDigits: 0,
    }).format(amount);
  };

  const isActionable =
    selectedApp &&
    selectedApp.workflowStage === "FIELD" &&
    (selectedApp.status === "PENDING_FIELD_VERIFICATION" ||
      selectedApp.status === "SUBMITTED" ||
      selectedApp.status === "UNDER_VERIFICATION");

  return (
    <div className="officer-dash">
      {/* Header */}
      <header className="officer-dash__header">
        <div>
          <span className="officer-dash__eyebrow officer-dash__eyebrow--field">
            <UserCheck size={14} /> Field Officer Workspace
          </span>
          <h1>Field Verification Dashboard</h1>
          <p>
            Review applications assigned to your field jurisdiction. Inspect applicant credentials, verify physical &
            documentary proofs, and forward eligible applications to the District Officer.
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
          <div className="officer-dash__kpi-icon officer-dash__kpi-icon--blue">
            <Layers size={22} />
          </div>
          <div className="officer-dash__kpi-data">
            <span className="officer-dash__kpi-value">{totalAssigned}</span>
            <span className="officer-dash__kpi-label">Total Assigned</span>
          </div>
        </div>

        <div className="officer-dash__kpi-card">
          <div className="officer-dash__kpi-icon officer-dash__kpi-icon--amber">
            <Clock size={22} />
          </div>
          <div className="officer-dash__kpi-data">
            <span className="officer-dash__kpi-value">{pendingField}</span>
            <span className="officer-dash__kpi-label">Pending Field Review</span>
          </div>
        </div>

        <div className="officer-dash__kpi-card">
          <div className="officer-dash__kpi-icon officer-dash__kpi-icon--violet">
            <FileClock size={22} />
          </div>
          <div className="officer-dash__kpi-data">
            <span className="officer-dash__kpi-value">{underReview}</span>
            <span className="officer-dash__kpi-label">Under Inspection</span>
          </div>
        </div>

        <div className="officer-dash__kpi-card">
          <div className="officer-dash__kpi-icon officer-dash__kpi-icon--emerald">
            <CheckCircle2 size={22} />
          </div>
          <div className="officer-dash__kpi-data">
            <span className="officer-dash__kpi-value">{verifiedCount}</span>
            <span className="officer-dash__kpi-label">Field Verified</span>
          </div>
        </div>

        <div className="officer-dash__kpi-card">
          <div className="officer-dash__kpi-icon officer-dash__kpi-icon--amber">
            <AlertTriangle size={22} />
          </div>
          <div className="officer-dash__kpi-data">
            <span className="officer-dash__kpi-value">{correctionCount}</span>
            <span className="officer-dash__kpi-label">Needs Correction</span>
          </div>
        </div>

        <div className="officer-dash__kpi-card">
          <div className="officer-dash__kpi-icon officer-dash__kpi-icon--rose">
            <ShieldAlert size={22} />
          </div>
          <div className="officer-dash__kpi-data">
            <span className="officer-dash__kpi-value">{rejectedCount}</span>
            <span className="officer-dash__kpi-label">Rejected</span>
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
              <option value="PENDING">Pending Verification ({pendingField})</option>
              <option value="UNDER_REVIEW">Under Review ({underReview})</option>
              <option value="VERIFIED">Field Verified ({verifiedCount})</option>
              <option value="CORRECTION">Needs Correction ({correctionCount})</option>
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
                <th>Jurisdiction</th>
                <th>Requested Amount</th>
                <th>Score</th>
                <th>Status</th>
                <th>Action</th>
              </tr>
            </thead>
            <tbody>
              {filteredApps.length === 0 ? (
                <tr>
                  <td colSpan={8}>
                    <div className="officer-empty">
                      <FileText size={36} />
                      <p>{loading ? "Loading applications..." : "No applications found matching criteria."}</p>
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
                      <small style={{ color: "var(--color-text-muted)" }}>{app.beneficiaryDetails?.category || "—"}</small>
                    </td>
                    <td>
                      <div style={{ maxWidth: 220, whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis" }} title={app.schemeName}>
                        {app.schemeName || "Scheme #" + app.schemeId}
                      </div>
                    </td>
                    <td>
                      <small>
                        {app.beneficiaryDetails?.district || "—"}, {app.beneficiaryDetails?.state || "—"}
                      </small>
                    </td>
                    <td>
                      <strong>{formatCurrency(app.requestedAmount)}</strong>
                    </td>
                    <td>
                      {app.eligibilityEvaluation?.scoreOutOf100 != null ? (
                        <span
                          className={`officer-dash__score-pill ${
                            Number(app.eligibilityEvaluation.scoreOutOf100) >=
                            Number(app.eligibilityThreshold || 70)
                              ? "officer-dash__score-pill--high"
                              : "officer-dash__score-pill--low"
                          }`}
                        >
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
                        <span>{app.workflowStage === "FIELD" ? "Inspect" : "View"}</span>
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
                <h2>Application #{selectedApp.id} Verification</h2>
                <small style={{ color: "var(--color-text-muted)" }}>
                  {selectedApp.schemeName} · Applicant: {selectedApp.beneficiaryName}
                </small>
              </div>
              <button className="officer-modal__close-btn" onClick={() => setSelectedApp(null)}>
                <X size={20} />
              </button>
            </div>

            {/* Modal Tabs */}
            <div className="officer-modal__tabs">
              <button
                className={`officer-modal__tab ${modalTab === "details" ? "officer-modal__tab--active" : ""}`}
                onClick={() => setModalTab("details")}
              >
                <UserRound size={15} /> Applicant Details
              </button>
              <button
                className={`officer-modal__tab ${modalTab === "documents" ? "officer-modal__tab--active" : ""}`}
                onClick={() => setModalTab("documents")}
              >
                <FileCheck2 size={15} /> Documents ({selectedApp.documents?.length || 0})
              </button>
              <button
                className={`officer-modal__tab ${modalTab === "eligibility" ? "officer-modal__tab--active" : ""}`}
                onClick={() => setModalTab("eligibility")}
              >
                <ShieldCheck size={15} /> Eligibility & Score
              </button>
              <button
                className={`officer-modal__tab ${modalTab === "history" ? "officer-modal__tab--active" : ""}`}
                onClick={() => setModalTab("history")}
              >
                <FileClock size={15} /> History & Remarks
              </button>
              {isActionable && (
                <button
                  className={`officer-modal__tab ${modalTab === "actions" ? "officer-modal__tab--active" : ""}`}
                  onClick={() => setModalTab("actions")}
                  style={{ color: "var(--color-primary)", fontWeight: 700 }}
                >
                  <FileCheck2 size={15} /> Verification Actions
                </button>
              )}
            </div>

            {/* Modal Body */}
            <div className="officer-modal__body">
              {modalTab === "details" && (
                <div style={{ display: "flex", flexDirection: "column", gap: 16 }}>
                  <h3>Beneficiary Profile</h3>
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
                      <span className="officer-info-label">Category</span>
                      <span className="officer-info-value">{selectedApp.beneficiaryDetails?.category || "—"}</span>
                    </div>
                    <div className="officer-info-item">
                      <span className="officer-info-label">Date of Birth / Age</span>
                      <span className="officer-info-value">
                        {selectedApp.beneficiaryDetails?.dateOfBirth || "—"} (
                        {selectedApp.beneficiaryDetails?.age != null
                          ? `${selectedApp.beneficiaryDetails.age} years`
                          : "—"}
                        )
                      </span>
                    </div>
                    <div className="officer-info-item">
                      <span className="officer-info-label">Annual Income</span>
                      <span className="officer-info-value">{formatCurrency(selectedApp.beneficiaryDetails?.annualIncome)}</span>
                    </div>
                    <div className="officer-info-item">
                      <span className="officer-info-label">State</span>
                      <span className="officer-info-value">{selectedApp.beneficiaryDetails?.state || "—"}</span>
                    </div>
                    <div className="officer-info-item">
                      <span className="officer-info-label">District</span>
                      <span className="officer-info-value">{selectedApp.beneficiaryDetails?.district || "—"}</span>
                    </div>
                    <div className="officer-info-item" style={{ gridColumn: "1 / -1" }}>
                      <span className="officer-info-label">Address</span>
                      <span className="officer-info-value">{selectedApp.beneficiaryDetails?.address || "—"}</span>
                    </div>
                  </div>

                  <h3>Application Information</h3>
                  <div className="officer-info-grid">
                    <div className="officer-info-item">
                      <span className="officer-info-label">Scheme</span>
                      <span className="officer-info-value">{selectedApp.schemeName || "—"}</span>
                    </div>
                    <div className="officer-info-item">
                      <span className="officer-info-label">Requested Amount</span>
                      <span className="officer-info-value">{formatCurrency(selectedApp.requestedAmount)}</span>
                    </div>
                    <div className="officer-info-item">
                      <span className="officer-info-label">Current Stage</span>
                      <span className="officer-info-value">{selectedApp.workflowStage || "FIELD"}</span>
                    </div>
                    <div className="officer-info-item">
                      <span className="officer-info-label">Priority</span>
                      <span className="officer-info-value">{selectedApp.routingPriority || "NORMAL"}</span>
                    </div>
                    <div className="officer-info-item" style={{ gridColumn: "1 / -1" }}>
                      <span className="officer-info-label">Proposal / Details</span>
                      <span className="officer-info-value">{selectedApp.applicationDetails || "No project details supplied."}</span>
                    </div>
                  </div>
                </div>
              )}

              {modalTab === "documents" && (
                <div style={{ display: "flex", flexDirection: "column", gap: 14 }}>
                  <h3>Submitted Supporting Documents</h3>
                  {!selectedApp.documents || selectedApp.documents.length === 0 ? (
                    <div className="officer-empty">
                      <FileText size={32} />
                      <p>No documents attached to this application.</p>
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

              {modalTab === "eligibility" && (
                <div style={{ display: "flex", flexDirection: "column", gap: 16 }}>
                  <h3>Eligibility Evaluation Score</h3>
                  <div className="officer-info-grid">
                    <div className="officer-info-item">
                      <span className="officer-info-label">Total Score</span>
                      <span className="officer-info-value" style={{ fontSize: "1.3rem", color: "var(--color-primary)" }}>
                        {selectedApp.eligibilityEvaluation?.scoreOutOf100 ?? "—"} / 100
                      </span>
                    </div>
                    <div className="officer-info-item">
                      <span className="officer-info-label">Scheme Passing Threshold</span>
                      <span className="officer-info-value">{selectedApp.eligibilityThreshold ?? 70}%</span>
                    </div>
                    <div className="officer-info-item">
                      <span className="officer-info-label">Eligibility Status</span>
                      <span className="officer-info-value">
                        {selectedApp.eligibilityEvaluation?.eligible ? (
                          <span style={{ color: "#16a34a", fontWeight: 700 }}>Meets Criteria</span>
                        ) : (
                          <span style={{ color: "#dc2626", fontWeight: 700 }}>Does Not Meet Criteria</span>
                        )}
                      </span>
                    </div>
                  </div>

                  {selectedApp.eligibilityEvaluation?.criteriaResults && (
                    <>
                      <h4>Scoring Question Breakdown</h4>
                      <div className="officer-doc-list">
                        {selectedApp.eligibilityEvaluation.criteriaResults.map((crit, idx) => (
                          <div className="officer-doc-item" key={idx}>
                            <div>
                              <strong>{crit.criterionName || `Question #${idx + 1}`}</strong>
                              <div style={{ fontSize: "0.82rem", color: "var(--color-text-muted)" }}>
                                Answer: {crit.actualValue || "—"}
                              </div>
                            </div>
                            <span style={{ fontWeight: 700, color: crit.met ? "#16a34a" : "#dc2626" }}>
                              {crit.pointsAwarded != null ? `${crit.pointsAwarded} pts` : crit.met ? "PASSED" : "FAILED"}
                            </span>
                          </div>
                        ))}
                      </div>
                    </>
                  )}
                </div>
              )}

              {modalTab === "history" && (
                <div style={{ display: "flex", flexDirection: "column", gap: 16 }}>
                  <h3>Verification History & Remarks</h3>
                  {!selectedApp.verificationHistory || selectedApp.verificationHistory.length === 0 ? (
                    <div className="officer-empty">
                      <Clock size={32} />
                      <p>No historical verification actions recorded yet.</p>
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
                    <span className="officer-checklist-title">1. Mandatory Field Verification Checklist</span>
                    <p style={{ margin: 0, fontSize: "0.85rem", color: "var(--color-text-muted)" }}>
                      Confirm the four compliance criteria before verifying and forwarding to District Officer.
                    </p>
                    <label className="officer-checkbox-label">
                      <input
                        type="checkbox"
                        checked={checklist.identityVerified}
                        onChange={(e) => setChecklist({ ...checklist, identityVerified: e.target.checked })}
                      />
                      <span>Identity Proof Verified (Facility / Applicant ID credentials confirmed)</span>
                    </label>
                    <label className="officer-checkbox-label">
                      <input
                        type="checkbox"
                        checked={checklist.addressVerified}
                        onChange={(e) => setChecklist({ ...checklist, addressVerified: e.target.checked })}
                      />
                      <span>Address & Location Verified (Facility is physically in stated jurisdiction)</span>
                    </label>
                    <label className="officer-checkbox-label">
                      <input
                        type="checkbox"
                        checked={checklist.documentsVerified}
                        onChange={(e) => setChecklist({ ...checklist, documentsVerified: e.target.checked })}
                      />
                      <span>Supporting Documents Verified (Registrations, proposals, quotes and bank proofs valid)</span>
                    </label>
                    <label className="officer-checkbox-label">
                      <input
                        type="checkbox"
                        checked={checklist.applicationDetailsVerified}
                        onChange={(e) => setChecklist({ ...checklist, applicationDetailsVerified: e.target.checked })}
                      />
                      <span>Application Details & Eligibility Verified (Answers consistent with field inspection)</span>
                    </label>
                  </div>

                  <div className="officer-remarks-area">
                    <label>2. Field Inspection Findings & Remarks (Mandatory)</label>
                    <textarea
                      placeholder="Enter detailed field inspection findings, site assessment notes, and verification remarks..."
                      value={remarks}
                      onChange={(e) => setRemarks(e.target.value)}
                    />
                  </div>

                  <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14 }}>
                    <div className="officer-remarks-area">
                      <label>Correction Reason (If returning to applicant)</label>
                      <input
                        type="text"
                        placeholder="e.g. Please re-upload legible facility registration certificate"
                        value={correctionReason}
                        onChange={(e) => setCorrectionReason(e.target.value)}
                        style={{ padding: 10, borderRadius: 8, border: "1px solid var(--color-outline-variant)" }}
                      />
                    </div>
                    <div className="officer-remarks-area">
                      <label>Rejection Reason (If rejecting)</label>
                      <input
                        type="text"
                        placeholder="e.g. Facility location is outside eligible rural catchment area"
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
                  <AlertTriangle size={15} /> Request Corrections
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
                  disabled={actionLoading}
                  onClick={() => handleDecision("VERIFIED")}
                >
                  <CheckCircle2 size={15} /> Verify & Forward to District Officer
                </button>
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
