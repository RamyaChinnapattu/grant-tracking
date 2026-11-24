import { useCallback, useEffect, useState } from "react";
import { useSearchParams } from "react-router-dom";
import {
  AlertCircle,
  BadgeCheck,
  Check,
  CheckCircle2,
  CircleDollarSign,
  Clock,
  Download,
  Eye,
  FileCheck,
  FileText,
  Filter,
  RefreshCw,
  Search,
  ShieldAlert,
  ShieldCheck,
  X,
} from "lucide-react";
import {
  getComplianceReviewQueue,
  getNonComplianceCases,
  reviewCompliance,
  getComplianceDocumentFile,
} from "../services/complianceService";
import {
  getUtilizationReviewQueue,
  reviewUtilization,
} from "../services/fundAnalyticsService";
import "../styles/OfficerReviewVerification.css";
import "../styles/AdminGrantManagement.css";

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

export default function OfficerReviewVerification() {
  const [searchParams, setSearchParams] = useSearchParams();
  const currentTab = searchParams.get("tab") || "compliance";
  const [activeTab, setActiveTab] = useState(currentTab);

  useEffect(() => {
    const tabParam = searchParams.get("tab") || "compliance";
    setActiveTab(tabParam);
  }, [searchParams]);

  const switchTab = (tab) => {
    setActiveTab(tab);
    setSearchParams({ tab });
  };

  // State
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState("");
  const [successMsg, setSuccessMsg] = useState("");

  // Compliance items
  const [complianceQueue, setComplianceQueue] = useState([]);
  const [nonComplianceCases, setNonComplianceCases] = useState([]);

  // Expenditure (utilization) items
  const [utilizationQueue, setUtilizationQueue] = useState([]);

  // Review form states
  const [complianceDecisions, setComplianceDecisions] = useState({});
  const [complianceRemarks, setComplianceRemarks] = useState({});
  const [submittingComplianceId, setSubmittingComplianceId] = useState(null);

  const [utilizationDecisions, setUtilizationDecisions] = useState({});
  const [utilizationRemarks, setUtilizationRemarks] = useState({});
  const [submittingUtilId, setSubmittingUtilId] = useState(null);

  // Load data
  const loadData = useCallback(async (isRefresh = false) => {
    if (isRefresh) setRefreshing(true);
    else setLoading(true);
    setError("");

    try {
      const [compRes, caseRes, utilRes] = await Promise.all([
        getComplianceReviewQueue().catch(() => ({ data: [] })),
        getNonComplianceCases().catch(() => ({ data: [] })),
        getUtilizationReviewQueue().catch(() => ({ data: [] })),
      ]);

      setComplianceQueue(compRes.data || []);
      setNonComplianceCases(caseRes.data || []);
      setUtilizationQueue(utilRes.data || []);
    } catch (err) {
      console.error("Failed to load review queue:", err);
      setError("Failed to load verification queue. Please refresh.");
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, []);

  useEffect(() => {
    loadData();
  }, [loadData]);

  // Open / Download evidence document
  const openDocument = async (id, fileName) => {
    try {
      const { data } = await getComplianceDocumentFile(id);
      const url = URL.createObjectURL(data);
      const a = document.createElement("a");
      a.href = url;
      a.download = fileName || `compliance-doc-${id}.pdf`;
      document.body.appendChild(a);
      a.click();
      window.URL.revokeObjectURL(url);
      a.remove();
    } catch (e) {
      setError("Could not download the requested document.");
    }
  };

  // Review Compliance Submission
  const handleReviewCompliance = async (item) => {
    const decision = complianceDecisions[item.id] || "APPROVED";
    const remark = (complianceRemarks[item.id] || "").trim();

    if ((decision === "CORRECTION_REQUIRED" || decision === "REJECTED") && !remark) {
      setError(`Remarks are required when selecting ${decision.replace("_", " ")}.`);
      return;
    }

    setSubmittingComplianceId(item.id);
    setError("");
    setSuccessMsg("");

    try {
      await reviewCompliance(item.id, {
        decision,
        remarks: remark,
      });
      setSuccessMsg(`Compliance submission #${item.id} reviewed as ${decision}.`);
      await loadData(true);
    } catch (err) {
      setError(err.response?.data?.message || "Failed to submit compliance review.");
    } finally {
      setSubmittingComplianceId(null);
    }
  };

  // Review Expenditure Report
  const handleReviewUtilization = async (item) => {
    const decision = utilizationDecisions[item.id] || "APPROVED";
    const remark = (utilizationRemarks[item.id] || "").trim();

    if ((decision === "CORRECTION_REQUIRED" || decision === "REJECTED") && !remark) {
      setError(`Remarks are required when selecting ${decision.replace("_", " ")}.`);
      return;
    }

    setSubmittingUtilId(item.id);
    setError("");
    setSuccessMsg("");

    try {
      await reviewUtilization(item.id, {
        decision,
        remarks: remark,
      });
      setSuccessMsg(`Expenditure report #${item.id} reviewed as ${decision}.`);
      await loadData(true);
    } catch (err) {
      setError(err.response?.data?.message || "Failed to submit expenditure review.");
    } finally {
      setSubmittingUtilId(null);
    }
  };

  return (
    <div className="officer-review-workspace">
      {/* Header */}
      <header className="officer-header">
        <div>
          <h1>Review &amp; Verification</h1>
          <p>
            Field, District, and Finance verification queue for milestone compliance documents and beneficiary grant expenditure.
          </p>
        </div>
        <div>
          <button
            className="mgmt-btn secondary"
            onClick={() => loadData(true)}
            disabled={refreshing || loading}
          >
            <RefreshCw size={16} className={refreshing ? "spin" : ""} />
            {refreshing ? "Refreshing…" : "Refresh"}
          </button>
        </div>
      </header>

      {/* Global Alerts */}
      {error && (
        <div className="mgmt-alert error" role="alert">
          <AlertCircle size={18} />
          <span>{error}</span>
          <button
            onClick={() => setError("")}
            style={{ marginLeft: "auto", background: "none", border: "none", cursor: "pointer" }}
          >
            <X size={16} />
          </button>
        </div>
      )}
      {successMsg && (
        <div className="mgmt-alert success" role="status">
          <CheckCircle2 size={18} />
          <span>{successMsg}</span>
          <button
            onClick={() => setSuccessMsg("")}
            style={{ marginLeft: "auto", background: "none", border: "none", cursor: "pointer" }}
          >
            <X size={16} />
          </button>
        </div>
      )}

      {/* Tabs */}
      <nav className="officer-tabs">
        <button
          className={`officer-tab-btn ${activeTab === "compliance" ? "active" : ""}`}
          onClick={() => switchTab("compliance")}
        >
          <BadgeCheck size={18} />
          Compliance Documents
          {complianceQueue.length > 0 && (
            <span className="officer-badge">{complianceQueue.length}</span>
          )}
        </button>
        <button
          className={`officer-tab-btn ${activeTab === "expenditure" ? "active" : ""}`}
          onClick={() => switchTab("expenditure")}
        >
          <CircleDollarSign size={18} />
          Expenditure Reports
          {utilizationQueue.length > 0 && (
            <span className="officer-badge">{utilizationQueue.length}</span>
          )}
        </button>
      </nav>

      {/* ========================================================================= */}
      {/* TAB 1: COMPLIANCE DOCUMENTS                                               */}
      {/* ========================================================================= */}
      {activeTab === "compliance" && (
        <section>
          <div className="officer-card">
            <div className="officer-card-header">
              <h2>Submitted Milestone Compliance Evidence</h2>
              <span style={{ fontSize: "0.85rem", color: "#64748b" }}>
                Pending verification before installment releases
              </span>
            </div>

            {complianceQueue.length === 0 ? (
              <div style={{ textAlign: "center", padding: "2.5rem", color: "#64748b" }}>
                <CheckCircle2 size={36} style={{ marginBottom: "0.5rem", opacity: 0.5 }} />
                <p>No compliance submissions currently require your review.</p>
              </div>
            ) : (
              <div>
                {complianceQueue.map((item) => {
                  const currentDecision = complianceDecisions[item.id] || "APPROVED";
                  const currentRemarks = complianceRemarks[item.id] || "";
                  const isBusy = submittingComplianceId === item.id;

                  return (
                    <article className="officer-item-card" key={item.id}>
                      <div className="officer-item-header">
                        <div>
                          <h3>{item.requirementTitle}</h3>
                          <div className="officer-item-meta">
                            Application #{item.applicationId} · {item.beneficiaryName} · Scheme:{" "}
                            {item.schemeName} · Milestone: {item.milestoneTitle}
                          </div>
                        </div>
                        <span
                          className={`mgmt-status-badge status-${item.status?.toLowerCase()}`}
                        >
                          {item.status}
                        </span>
                      </div>

                      {item.description && (
                        <p style={{ fontSize: "0.875rem", color: "#475569", margin: "0.5rem 0" }}>
                          {item.description}
                        </p>
                      )}

                      {item.submissionNotes && (
                        <div
                          style={{
                            background: "#f8fafc",
                            padding: "0.5rem 0.75rem",
                            borderRadius: "6px",
                            fontSize: "0.85rem",
                            marginBottom: "0.75rem",
                          }}
                        >
                          <strong>Beneficiary Note:</strong> {item.submissionNotes}
                        </div>
                      )}

                      {/* Supporting document */}
                      <div style={{ marginBottom: "0.75rem" }}>
                        {item.hasDocument ? (
                          <button
                            type="button"
                            className="mgmt-btn secondary"
                            style={{ padding: "0.35rem 0.75rem", fontSize: "0.8rem" }}
                            onClick={() => openDocument(item.id, item.documentFileName)}
                          >
                            <Download size={14} /> Download Document (
                            {item.documentFileName || "Attached File"})
                          </button>
                        ) : (
                          <span style={{ fontSize: "0.8rem", color: "#94a3b8" }}>
                            No file attached
                          </span>
                        )}
                      </div>

                      {/* Review Actions Box */}
                      <div className="officer-review-box">
                        <div style={{ display: "flex", gap: "1rem", flexWrap: "wrap" }}>
                          <div style={{ flex: 1, minWidth: "200px" }}>
                            <label
                              style={{
                                display: "block",
                                fontSize: "0.8rem",
                                fontWeight: 600,
                                marginBottom: "0.25rem",
                              }}
                            >
                              Verification Decision
                            </label>
                            <select
                              className="mgmt-select"
                              style={{ width: "100%" }}
                              value={currentDecision}
                              onChange={(e) =>
                                setComplianceDecisions({
                                  ...complianceDecisions,
                                  [item.id]: e.target.value,
                                })
                              }
                            >
                              <option value="APPROVED">APPROVE - Evidence Validated</option>
                              <option value="CORRECTION_REQUIRED">
                                CORRECTION REQUIRED - Request Re-submission
                              </option>
                              <option value="REJECTED">REJECT - Non-Compliant</option>
                            </select>
                          </div>

                          <div style={{ flex: 2, minWidth: "280px" }}>
                            <label
                              style={{
                                display: "block",
                                fontSize: "0.8rem",
                                fontWeight: 600,
                                marginBottom: "0.25rem",
                              }}
                            >
                              Remarks{" "}
                              {(currentDecision === "REJECTED" ||
                                currentDecision === "CORRECTION_REQUIRED") &&
                                "*"}
                            </label>
                            <input
                              type="text"
                              placeholder="Officer remarks / audit feedback…"
                              style={{
                                width: "100%",
                                padding: "0.45rem 0.75rem",
                                border: "1px solid #cbd5e1",
                                borderRadius: "6px",
                                fontSize: "0.875rem",
                              }}
                              value={currentRemarks}
                              onChange={(e) =>
                                setComplianceRemarks({
                                  ...complianceRemarks,
                                  [item.id]: e.target.value,
                                })
                              }
                            />
                          </div>

                          <div style={{ display: "flex", alignItems: "flex-end" }}>
                            <button
                              type="button"
                              className="mgmt-btn primary"
                              disabled={isBusy}
                              onClick={() => handleReviewCompliance(item)}
                            >
                              <Check size={16} />
                              {isBusy ? "Saving…" : "Submit Review"}
                            </button>
                          </div>
                        </div>
                      </div>
                    </article>
                  );
                })}
              </div>
            )}
          </div>
        </section>
      )}

      {/* ========================================================================= */}
      {/* TAB 2: EXPENDITURE REPORTS                                                */}
      {/* ========================================================================= */}
      {activeTab === "expenditure" && (
        <section>
          <div className="officer-card">
            <div className="officer-card-header">
              <h2>Beneficiary Grant Expenditure Reports</h2>
              <span style={{ fontSize: "0.85rem", color: "#64748b" }}>
                Verify reported utilization against released grant installments
              </span>
            </div>

            {utilizationQueue.length === 0 ? (
              <div style={{ textAlign: "center", padding: "2.5rem", color: "#64748b" }}>
                <CircleDollarSign size={36} style={{ marginBottom: "0.5rem", opacity: 0.5 }} />
                <p>No expenditure reports are waiting for verification.</p>
              </div>
            ) : (
              <div>
                {utilizationQueue.map((r) => {
                  const currentDecision = utilizationDecisions[r.id] || "APPROVED";
                  const currentRemarks = utilizationRemarks[r.id] || "";
                  const isBusy = submittingUtilId === r.id;

                  return (
                    <article className="officer-item-card" key={r.id}>
                      <div className="officer-item-header">
                        <div>
                          <h3>
                            {r.category} · {formatCurrency(r.reportedAmount)}
                          </h3>
                          <div className="officer-item-meta">
                            Application #{r.applicationId} · {r.beneficiaryName} · {r.schemeName} · Stage{" "}
                            {r.installmentNumber} (Released: {formatCurrency(r.installmentAmount)})
                          </div>
                        </div>
                        <span
                          className={`mgmt-status-badge status-${r.status?.toLowerCase()}`}
                        >
                          {r.status}
                        </span>
                      </div>

                      {r.description && (
                        <p style={{ fontSize: "0.875rem", color: "#475569", margin: "0.5rem 0" }}>
                          {r.description}
                        </p>
                      )}

                      {/* Supporting Evidence Documents */}
                      {r.supportingComplianceIds && r.supportingComplianceIds.length > 0 && (
                        <div style={{ margin: "0.75rem 0" }}>
                          <label
                            style={{
                              display: "block",
                              fontSize: "0.8rem",
                              fontWeight: 600,
                              color: "#64748b",
                              marginBottom: "0.35rem",
                            }}
                          >
                            Attached Supporting Evidence:
                          </label>
                          <div style={{ display: "flex", gap: "0.5rem", flexWrap: "wrap" }}>
                            {r.supportingComplianceIds.map((docId, idx) => (
                              <button
                                key={docId}
                                type="button"
                                className="mgmt-btn secondary"
                                style={{ padding: "0.25rem 0.5rem", fontSize: "0.75rem" }}
                                onClick={() =>
                                  openDocument(
                                    docId,
                                    r.supportingDocumentNames?.[idx] || `Evidence-${docId}.pdf`
                                  )
                                }
                              >
                                <Download size={13} />{" "}
                                {r.supportingDocumentNames?.[idx] || `Evidence #${docId}`}
                              </button>
                            ))}
                          </div>
                        </div>
                      )}

                      {/* Review Box */}
                      <div className="officer-review-box">
                        <div style={{ display: "flex", gap: "1rem", flexWrap: "wrap" }}>
                          <div style={{ flex: 1, minWidth: "200px" }}>
                            <label
                              style={{
                                display: "block",
                                fontSize: "0.8rem",
                                fontWeight: 600,
                                marginBottom: "0.25rem",
                              }}
                            >
                              Expenditure Verification
                            </label>
                            <select
                              className="mgmt-select"
                              style={{ width: "100%" }}
                              value={currentDecision}
                              onChange={(e) =>
                                setUtilizationDecisions({
                                  ...utilizationDecisions,
                                  [r.id]: e.target.value,
                                })
                              }
                            >
                              <option value="APPROVED">APPROVE - Utilization Validated</option>
                              <option value="CORRECTION_REQUIRED">
                                CORRECTION REQUIRED - Incomplete Vouchers
                              </option>
                              <option value="REJECTED">REJECT - Invalid Expenditure</option>
                            </select>
                          </div>

                          <div style={{ flex: 2, minWidth: "280px" }}>
                            <label
                              style={{
                                display: "block",
                                fontSize: "0.8rem",
                                fontWeight: 600,
                                marginBottom: "0.25rem",
                              }}
                            >
                              Verification Remarks{" "}
                              {(currentDecision === "REJECTED" ||
                                currentDecision === "CORRECTION_REQUIRED") &&
                                "*"}
                            </label>
                            <input
                              type="text"
                              placeholder="Audit comments / voucher verification notes…"
                              style={{
                                width: "100%",
                                padding: "0.45rem 0.75rem",
                                border: "1px solid #cbd5e1",
                                borderRadius: "6px",
                                fontSize: "0.875rem",
                              }}
                              value={currentRemarks}
                              onChange={(e) =>
                                setUtilizationRemarks({
                                  ...utilizationRemarks,
                                  [r.id]: e.target.value,
                                })
                              }
                            />
                          </div>

                          <div style={{ display: "flex", alignItems: "flex-end" }}>
                            <button
                              type="button"
                              className="mgmt-btn primary"
                              disabled={isBusy}
                              onClick={() => handleReviewUtilization(r)}
                            >
                              <Check size={16} />
                              {isBusy ? "Saving…" : "Submit Review"}
                            </button>
                          </div>
                        </div>
                      </div>
                    </article>
                  );
                })}
              </div>
            )}
          </div>
        </section>
      )}
    </div>
  );
}
