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
import {
  getDisbursementPlans,
  disburseInstallment,
} from "../services/disbursementService";
import { AlertBanner, PageHeader } from "../components/ui/SaaSPrimitives";
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

  // Finance Officer payment releases
  const officerLevel = (localStorage.getItem("officerLevel") || "").toUpperCase();
  const isFinanceOfficer = officerLevel === "FINANCE";
  const [plans, setPlans] = useState([]);
  const [disburseModalInst, setDisburseModalInst] = useState(null);
  const [disburseForm, setDisburseForm] = useState({
    paymentReference: "",
    paymentMethod: "DBT_PFMS",
    remarks: "",
  });
  const [disbursingId, setDisbursingId] = useState(null);

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
      const [compRes, caseRes, utilRes, plansRes] = await Promise.all([
        getComplianceReviewQueue().catch(() => ({ data: [] })),
        getNonComplianceCases().catch(() => ({ data: [] })),
        getUtilizationReviewQueue().catch(() => ({ data: [] })),
        isFinanceOfficer ? getDisbursementPlans().catch(() => ({ data: [] })) : Promise.resolve({ data: [] }),
      ]);

      setComplianceQueue(compRes.data || []);
      setNonComplianceCases(caseRes.data || []);
      setUtilizationQueue(utilRes.data || []);
      setPlans(Array.isArray(plansRes.data) ? plansRes.data : plansRes.data?.content || []);
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
    <div className="officer-review-workspace saas-page">
      <PageHeader
        eyebrow="Officer Verification"
        title="Review & Verification"
        subtitle="Field, District, and Finance verification queue for milestone compliance documents and beneficiary grant expenditure."
        actions={
          <button
            className="saas-btn saas-btn--secondary"
            type="button"
            onClick={() => loadData(true)}
            disabled={refreshing || loading}
          >
            <RefreshCw size={15} className={refreshing ? "spin" : ""} />
            {refreshing ? "Refreshing…" : "Refresh"}
          </button>
        }
      />

      <AlertBanner type="error" message={error} onClose={() => setError("")} />
      <AlertBanner type="success" message={successMsg} onClose={() => setSuccessMsg("")} />

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
        {isFinanceOfficer && (
          <button
            className={`officer-tab-btn ${activeTab === "releases" ? "active" : ""}`}
            onClick={() => switchTab("releases")}
          >
            <ShieldCheck size={18} />
            Payment Releases
          </button>
        )}
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

                      {item.stageReviews && item.stageReviews.length > 0 && (
                        <div
                          style={{
                            background: "#f8fafc",
                            border: "1px solid #e2e8f0",
                            padding: "0.55rem 0.75rem",
                            borderRadius: "6px",
                            fontSize: "0.8rem",
                            marginBottom: "0.75rem",
                          }}
                        >
                          <strong>Prior Stage Reviews (Cycle #{item.currentReviewCycle || 1}):</strong>
                          {item.stageReviews.map((sr, idx) => (
                            <div key={idx} style={{ marginTop: "0.25rem", color: "#334155" }}>
                              Cycle #{sr.reviewCycle} · <strong>{sr.stage}</strong>: {sr.decision} by {sr.reviewerName} ({formatDate(sr.reviewedAt)})
                              {sr.remarks && <span style={{ fontStyle: "italic", color: "#475569" }}> — "{sr.remarks}"</span>}
                            </div>
                          ))}
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

      {/* ========================================================================= */}
      {/* TAB 3: FINANCE OFFICER PAYMENT RELEASES                                   */}
      {/* ========================================================================= */}
      {isFinanceOfficer && activeTab === "releases" && (
        <section>
          <div className="officer-card">
            <div className="officer-card-header">
              <h2>Finance Payment Releases (Simulation)</h2>
              <span style={{ fontSize: "0.85rem", color: "#64748b" }}>
                Record simulated payments for installments whose Field, District, and Finance compliance reviews are all approved.
              </span>
            </div>
            {plans.filter((p) => p.planStatus === "ACTIVE").length === 0 ? (
              <div style={{ textAlign: "center", padding: "2rem", color: "#64748b" }}>
                No active disbursement plans available.
              </div>
            ) : (
              <div className="mgmt-table-wrap">
                <table className="mgmt-table">
                  <thead>
                    <tr>
                      <th>Application</th>
                      <th>Stage</th>
                      <th>Amount</th>
                      <th>Compliance Gate</th>
                      <th>Status</th>
                      <th>Action</th>
                    </tr>
                  </thead>
                  <tbody>
                    {plans
                      .filter((p) => p.planStatus === "ACTIVE")
                      .flatMap((p) =>
                        (p.installments || []).map((inst) => ({
                          ...inst,
                          applicationId: p.applicationId,
                          beneficiaryName: p.beneficiaryName,
                          schemeName: p.schemeName,
                        }))
                      )
                      .map((inst) => (
                        <tr key={inst.id}>
                          <td>
                            <strong>#{inst.applicationId}</strong> · {inst.beneficiaryName}
                            <div style={{ fontSize: "0.75rem", color: "#64748b" }}>{inst.schemeName}</div>
                          </td>
                          <td>
                            <strong>Stage {inst.installmentNumber}</strong> · {inst.title}
                          </td>
                          <td><strong>{formatCurrency(inst.amount)}</strong></td>
                          <td>
                            {inst.allCompliancesVerified ? (
                              <span style={{ color: "#15803d", fontWeight: 600, fontSize: "0.8rem" }}>
                                All 3 Reviews Approved
                              </span>
                            ) : (
                              <span style={{ color: "#b45309", fontSize: "0.8rem" }}>
                                Pending Compliance Reviews
                              </span>
                            )}
                          </td>
                          <td>
                            <span className={`mgmt-status-badge status-${inst.status?.toLowerCase()}`}>
                              {inst.status}
                            </span>
                          </td>
                          <td>
                            {inst.status === "DISBURSED" ? (
                              <span style={{ fontSize: "0.75rem", color: "#15803d" }}>
                                Ref: {inst.paymentReference}
                              </span>
                            ) : inst.allCompliancesVerified ? (
                              <button
                                type="button"
                                className="mgmt-btn success"
                                style={{ padding: "0.3rem 0.65rem", fontSize: "0.8rem" }}
                                onClick={() => {
                                  setDisburseModalInst(inst);
                                  setDisburseForm({ paymentReference: "", paymentMethod: "DBT_PFMS", remarks: "" });
                                }}
                              >
                                Record Payment
                              </button>
                            ) : (
                              <span style={{ fontSize: "0.75rem", color: "#94a3b8" }}>Blocked</span>
                            )}
                          </td>
                        </tr>
                      ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        </section>
      )}

      {disburseModalInst && (
        <div className="mgmt-modal-overlay" onClick={() => setDisburseModalInst(null)}>
          <div className="mgmt-modal" onClick={(e) => e.stopPropagation()} role="dialog">
            <div className="mgmt-modal__header">
              <h2>Record Installment Payment (Stage {disburseModalInst.installmentNumber})</h2>
              <button onClick={() => setDisburseModalInst(null)} style={{ background: "none", border: "none", cursor: "pointer" }}>
                <X size={20} />
              </button>
            </div>
            <form
              onSubmit={async (e) => {
                e.preventDefault();
                if (!disburseForm.paymentReference.trim()) {
                  setError("Payment reference / UTR is required.");
                  return;
                }
                setDisbursingId(disburseModalInst.id);
                setError("");
                setSuccessMsg("");
                try {
                  await disburseInstallment(disburseModalInst.id, {
                    paymentReference: disburseForm.paymentReference.trim(),
                    paymentMethod: disburseForm.paymentMethod,
                    remarks: disburseForm.remarks,
                  });
                  setSuccessMsg(`Stage ${disburseModalInst.installmentNumber} payment recorded successfully.`);
                  setDisburseModalInst(null);
                  await loadData(true);
                } catch (err) {
                  setError(err.response?.data?.message || "Failed to record installment payment.");
                } finally {
                  setDisbursingId(null);
                }
              }}
            >
              <div className="mgmt-modal__body">
                <div className="mgmt-form-group">
                  <label>Payment Method *</label>
                  <select
                    value={disburseForm.paymentMethod}
                    onChange={(e) => setDisburseForm({ ...disburseForm, paymentMethod: e.target.value })}
                  >
                    <option value="DBT_PFMS">Direct Benefit Transfer (DBT / PFMS)</option>
                    <option value="BANK_TRANSFER">Bank NEFT / RTGS</option>
                    <option value="UPI">UPI</option>
                    <option value="CHEQUE">Treasury Cheque</option>
                  </select>
                </div>
                <div className="mgmt-form-group">
                  <label>Payment Reference (UTR / Transaction ID) *</label>
                  <input
                    type="text"
                    required
                    placeholder="e.g. UTR1234567890"
                    value={disburseForm.paymentReference}
                    onChange={(e) => setDisburseForm({ ...disburseForm, paymentReference: e.target.value })}
                  />
                </div>
                <div className="mgmt-form-group">
                  <label>Remarks</label>
                  <textarea
                    rows="2"
                    value={disburseForm.remarks}
                    onChange={(e) => setDisburseForm({ ...disburseForm, remarks: e.target.value })}
                  />
                </div>
              </div>
              <div className="mgmt-modal__footer">
                <button type="button" className="mgmt-btn secondary" onClick={() => setDisburseModalInst(null)}>
                  Cancel
                </button>
                <button type="submit" className="mgmt-btn success" disabled={disbursingId === disburseModalInst.id}>
                  {disbursingId === disburseModalInst.id ? "Recording…" : "Confirm Payment"}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
