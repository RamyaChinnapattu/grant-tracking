import { useCallback, useEffect, useMemo, useState } from "react";
import { useSearchParams } from "react-router-dom";
import {
  AlertCircle,
  BadgeCheck,
  Calendar,
  CheckCircle2,
  CircleDollarSign,
  Clock,
  Download,
  FileCheck,
  FileText,
  FileUp,
  ReceiptText,
  RefreshCw,
  Send,
  ShieldCheck,
  X,
} from "lucide-react";
import { getMyDisbursementPlans } from "../services/disbursementService";
import {
  getComplianceMine,
  submitComplianceDocument,
  getComplianceDocumentFile,
} from "../services/complianceService";
import {
  getMyUtilization,
  replaceUtilization,
  submitUtilization,
} from "../services/fundAnalyticsService";
import "../styles/BeneficiaryMyGrant.css";
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

const blankExpenditureForm = {
  applicationId: "",
  installmentId: "",
  reportedAmount: "",
  category: "",
  description: "",
  supportingComplianceIds: [],
};

export default function BeneficiaryMyGrant() {
  const [searchParams, setSearchParams] = useSearchParams();
  const currentTab = searchParams.get("tab") || "schedule";
  const [activeTab, setActiveTab] = useState(currentTab);

  useEffect(() => {
    const tabParam = searchParams.get("tab") || "schedule";
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

  // Data
  const [plans, setPlans] = useState([]);
  const [complianceList, setComplianceList] = useState([]);
  const [expenditureReports, setExpenditureReports] = useState([]);

  // Upload state for compliance
  const [uploadFiles, setUploadFiles] = useState({});
  const [uploadNotes, setUploadNotes] = useState({});
  const [submittingDocId, setSubmittingDocId] = useState(null);

  // Expenditure reporting form
  const [expForm, setExpForm] = useState(blankExpenditureForm);
  const [editingExpId, setEditingExpId] = useState(null);
  const [submittingExp, setSubmittingExp] = useState(false);

  // Load all beneficiary grant records
  const loadData = useCallback(async (isRefresh = false) => {
    if (isRefresh) setRefreshing(true);
    else setLoading(true);
    setError("");

    try {
      const [plansRes, compRes, utilRes] = await Promise.all([
        getMyDisbursementPlans().catch(() => ({ data: [] })),
        getComplianceMine().catch(() => ({ data: [] })),
        getMyUtilization().catch(() => ({ data: [] })),
      ]);

      setPlans(plansRes.data || []);
      setComplianceList(compRes.data || []);
      setExpenditureReports(utilRes.data || []);
    } catch (err) {
      console.error("Failed to load beneficiary grant data:", err);
      setError("Failed to load grant information. Please check connection.");
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, []);

  useEffect(() => {
    loadData();
  }, [loadData]);

  // Overall financial summary
  const financialSummary = useMemo(() => {
    const totalApproved = plans.reduce(
      (sum, p) => sum + (Number(p.totalApprovedAmount) || 0),
      0
    );
    const totalDisbursed = plans.reduce(
      (sum, p) => sum + (Number(p.totalDisbursedAmount) || 0),
      0
    );
    const totalRemaining = Math.max(0, totalApproved - totalDisbursed);

    return {
      totalApproved,
      totalDisbursed,
      totalRemaining,
    };
  }, [plans]);

  // Pending compliance documents count
  const pendingDocsCount = useMemo(() => {
    return complianceList.filter((c) =>
      ["PENDING", "OVERDUE", "CORRECTION_REQUIRED", "REJECTED"].includes(c.status)
    ).length;
  }, [complianceList]);

  // Open / Download document
  const openDocument = async (id, fileName) => {
    try {
      const { data } = await getComplianceDocumentFile(id);
      const url = URL.createObjectURL(data);
      const a = document.createElement("a");
      a.href = url;
      a.download = fileName || `evidence-${id}.pdf`;
      document.body.appendChild(a);
      a.click();
      window.URL.revokeObjectURL(url);
      a.remove();
    } catch (e) {
      setError("Could not download the document.");
    }
  };

  // Submit compliance document
  const handleComplianceSubmit = async (item) => {
    const file = uploadFiles[item.id];
    if (!file) {
      setError("Please choose a file to upload.");
      return;
    }

    setSubmittingDocId(item.id);
    setError("");
    setSuccessMsg("");

    const formData = new FormData();
    formData.append("file", file);
    formData.append("submissionNotes", uploadNotes[item.id] || "");

    try {
      await submitComplianceDocument(item.id, formData);
      setSuccessMsg(`Document for "${item.requirementTitle}" submitted successfully!`);
      setUploadFiles((prev) => ({ ...prev, [item.id]: null }));
      setUploadNotes((prev) => ({ ...prev, [item.id]: "" }));
      await loadData(true);
    } catch (err) {
      setError(err.response?.data?.message || "Failed to submit document.");
    } finally {
      setSubmittingDocId(null);
    }
  };

  // Selected plan and available installments for expenditure report
  const selectedPlan = useMemo(() => {
    return plans.find((p) => String(p.applicationId) === String(expForm.applicationId));
  }, [plans, expForm.applicationId]);

  const releasedInstallments = useMemo(() => {
    return (selectedPlan?.installments || []).filter((i) => i.status === "DISBURSED");
  }, [selectedPlan]);

  const selectedInstallment = useMemo(() => {
    return releasedInstallments.find((i) => String(i.id) === String(expForm.installmentId));
  }, [releasedInstallments, expForm.installmentId]);

  // Available compliance docs for chosen installment
  const eligibleEvidenceDocs = useMemo(() => {
    return complianceList.filter(
      (c) =>
        String(c.installmentId) === String(expForm.installmentId) &&
        Boolean(c.hasDocument)
    );
  }, [complianceList, expForm.installmentId]);

  const toggleEvidenceDoc = (docId) => {
    setExpForm((prev) => ({
      ...prev,
      supportingComplianceIds: prev.supportingComplianceIds.includes(docId)
        ? prev.supportingComplianceIds.filter((id) => id !== docId)
        : [...prev.supportingComplianceIds, docId],
    }));
  };

  // Submit expenditure report
  const handleExpenditureSubmit = async (e) => {
    e.preventDefault();
    if (!expForm.applicationId || !expForm.installmentId) {
      setError("Please select an approved application and released installment.");
      return;
    }

    const reportedAmt = Number(expForm.reportedAmount);
    if (!reportedAmt || reportedAmt <= 0) {
      setError("Reported amount must be greater than zero.");
      return;
    }

    if (selectedInstallment && reportedAmt > Number(selectedInstallment.amount)) {
      setError(
        `Reported amount cannot exceed the released installment amount (${formatCurrency(
          selectedInstallment.amount
        )}).`
      );
      return;
    }

    setSubmittingExp(true);
    setError("");
    setSuccessMsg("");

    const payload = {
      applicationId: Number(expForm.applicationId),
      installmentId: Number(expForm.installmentId),
      reportedAmount: reportedAmt,
      category: expForm.category.trim(),
      description: expForm.description.trim(),
      supportingComplianceIds: expForm.supportingComplianceIds,
    };

    try {
      if (editingExpId) {
        await replaceUtilization(editingExpId, payload);
        setSuccessMsg("Corrected expenditure report resubmitted for verification.");
      } else {
        await submitUtilization(payload);
        setSuccessMsg("Expenditure report submitted for review.");
      }

      setExpForm(blankExpenditureForm);
      setEditingExpId(null);
      await loadData(true);
    } catch (err) {
      setError(err.response?.data?.message || "Failed to submit expenditure report.");
    } finally {
      setSubmittingExp(false);
    }
  };

  const handleStartCorrection = (r) => {
    setEditingExpId(r.id);
    setExpForm({
      applicationId: String(r.applicationId),
      installmentId: String(r.installmentId),
      reportedAmount: String(r.reportedAmount),
      category: r.category,
      description: r.description || "",
      supportingComplianceIds: r.supportingComplianceIds || [],
    });
    switchTab("expenditure");
  };

  return (
    <div className="ben-grant-workspace">
      {/* Header */}
      <header className="ben-header">
        <div>
          <h1>My Grant</h1>
          <p>
            Track your grant payment schedule, upload milestone compliance documents, and report grant expenditure.
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

      {/* Financial KPI Summary */}
      <div className="mgmt-kpi-grid">
        <article className="mgmt-kpi-card">
          <span>
            <CircleDollarSign size={16} /> Total Approved Grant
          </span>
          <strong>{formatCurrency(financialSummary.totalApproved)}</strong>
          <small>Across approved schemes</small>
        </article>

        <article className="mgmt-kpi-card">
          <span>
            <CheckCircle2 size={16} /> Total Disbursed / Released
          </span>
          <strong style={{ color: "#15803d" }}>
            {formatCurrency(financialSummary.totalDisbursed)}
          </strong>
          <small>Credited installments</small>
        </article>

        <article className="mgmt-kpi-card">
          <span>
            <Clock size={16} /> Remaining Balance
          </span>
          <strong>{formatCurrency(financialSummary.totalRemaining)}</strong>
          <small>Pending milestone completion</small>
        </article>

        <article className="mgmt-kpi-card">
          <span>
            <FileText size={16} /> Pending Documents
          </span>
          <strong style={{ color: pendingDocsCount > 0 ? "#b45309" : "#1e293b" }}>
            {pendingDocsCount}
          </strong>
          <small>Require your action</small>
        </article>
      </div>

      {/* Tabs */}
      <nav className="ben-tabs">
        <button
          className={`ben-tab-btn ${activeTab === "schedule" ? "active" : ""}`}
          onClick={() => switchTab("schedule")}
        >
          <Calendar size={18} />
          Payment Schedule
          {plans.length > 0 && <span className="ben-badge">{plans.length}</span>}
        </button>
        <button
          className={`ben-tab-btn ${activeTab === "documents" ? "active" : ""}`}
          onClick={() => switchTab("documents")}
        >
          <BadgeCheck size={18} />
          Required Documents
          {pendingDocsCount > 0 && <span className="ben-badge">{pendingDocsCount}</span>}
        </button>
        <button
          className={`ben-tab-btn ${activeTab === "expenditure" ? "active" : ""}`}
          onClick={() => switchTab("expenditure")}
        >
          <ReceiptText size={18} />
          Expenditure Reports
          {expenditureReports.length > 0 && (
            <span className="ben-badge">{expenditureReports.length}</span>
          )}
        </button>
      </nav>

      {/* ========================================================================= */}
      {/* TAB 1: PAYMENT SCHEDULE                                                   */}
      {/* ========================================================================= */}
      {activeTab === "schedule" && (
        <section>
          {plans.length === 0 ? (
            <div className="ben-card" style={{ textAlign: "center", padding: "3rem" }}>
              <CircleDollarSign size={40} style={{ opacity: 0.5, marginBottom: "0.5rem" }} />
              <h3>No disbursement schedules yet</h3>
              <p style={{ color: "#64748b" }}>
                Once your submitted grant application is approved by administrators, your staged payment schedule will appear here.
              </p>
            </div>
          ) : (
            <div>
              {plans.map((plan) => (
                <article className="ben-card" key={plan.id}>
                  <div
                    style={{
                      display: "flex",
                      justifyContent: "space-between",
                      alignItems: "center",
                      marginBottom: "1rem",
                      flexWrap: "wrap",
                      gap: "0.5rem",
                    }}
                  >
                    <div>
                      <h2 style={{ fontSize: "1.2rem", margin: 0 }}>
                        {plan.schemeName} · Application #{plan.applicationId}
                      </h2>
                      <div style={{ color: "#64748b", fontSize: "0.85rem", marginTop: "0.25rem" }}>
                        Total Approved:{" "}
                        <strong style={{ color: "#0f172a" }}>
                          {formatCurrency(plan.totalApprovedAmount)}
                        </strong>{" "}
                        · Released:{" "}
                        <strong style={{ color: "#16a34a" }}>
                          {formatCurrency(plan.totalDisbursedAmount)}
                        </strong>
                      </div>
                    </div>
                    <span
                      className={`mgmt-status-badge status-${plan.planStatus?.toLowerCase()}`}
                    >
                      {plan.planStatus}
                    </span>
                  </div>

                  {/* Installments Table */}
                  <div className="mgmt-table-wrap">
                    <table className="mgmt-table">
                      <thead>
                        <tr>
                          <th>Stage #</th>
                          <th>Stage Title</th>
                          <th>Amount</th>
                          <th>Share</th>
                          <th>Scheduled Date</th>
                          <th>Release Date</th>
                          <th>Status</th>
                          <th>Milestone &amp; Conditions</th>
                        </tr>
                      </thead>
                      <tbody>
                        {(plan.installments || []).map((inst) => (
                          <tr key={inst.id}>
                            <td>
                              <strong>Stage {inst.installmentNumber}</strong>
                            </td>
                            <td>{inst.title}</td>
                            <td>
                              <strong>{formatCurrency(inst.amount)}</strong>
                            </td>
                            <td>{inst.percentage}%</td>
                            <td>{formatDate(inst.plannedReleaseDate)}</td>
                            <td>
                              {inst.actualDisbursementDate ? (
                                <span style={{ color: "#16a34a", fontWeight: 600 }}>
                                  {formatDate(inst.actualDisbursementDate)}
                                </span>
                              ) : (
                                "—"
                              )}
                            </td>
                            <td>
                              <span
                                className={`mgmt-status-badge status-${inst.status?.toLowerCase()}`}
                              >
                                {inst.status}
                              </span>
                            </td>
                            <td>
                              {(inst.milestones || []).length === 0 ? (
                                <span style={{ color: "#64748b", fontSize: "0.8rem" }}>
                                  No prerequisite milestone
                                </span>
                              ) : (
                                (inst.milestones || []).map((m) => (
                                  <div key={m.id} style={{ fontSize: "0.8rem", marginBottom: "0.25rem" }}>
                                    <strong>{m.title}</strong>: {m.progressPercentage}%
                                    {m.compliances?.map((c) => (
                                      <div
                                        key={c.id}
                                        style={{ color: "#64748b", fontSize: "0.75rem" }}
                                      >
                                        • {c.requirementTitle} ({c.status})
                                      </div>
                                    ))}
                                  </div>
                                ))
                              )}
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                </article>
              ))}
            </div>
          )}
        </section>
      )}

      {/* ========================================================================= */}
      {/* TAB 2: REQUIRED DOCUMENTS                                                 */}
      {/* ========================================================================= */}
      {activeTab === "documents" && (
        <section>
          <div className="ben-card">
            <div className="mgmt-card-header">
              <h2>Grant Compliance Documents</h2>
              <span style={{ fontSize: "0.85rem", color: "#64748b" }}>
                Submit required documents to satisfy milestone conditions and unlock installment releases
              </span>
            </div>

            {complianceList.length === 0 ? (
              <div style={{ textAlign: "center", padding: "2.5rem", color: "#64748b" }}>
                <CheckCircle2 size={36} style={{ marginBottom: "0.5rem", opacity: 0.5 }} />
                <p>No compliance requirements are currently attached to your grant plans.</p>
              </div>
            ) : (
              <div>
                {complianceList.map((item) => {
                  const isActionable = [
                    "PENDING",
                    "OVERDUE",
                    "REJECTED",
                    "CORRECTION_REQUIRED",
                  ].includes(item.status);
                  const isBusy = submittingDocId === item.id;

                  return (
                    <article className="ben-doc-card" key={item.id}>
                      <div className="ben-doc-header">
                        <div>
                          <h3>{item.requirementTitle}</h3>
                          <div style={{ fontSize: "0.85rem", color: "#64748b", marginTop: "0.2rem" }}>
                            {item.schemeName} · Application #{item.applicationId} · {item.milestoneTitle}
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

                      <div
                        style={{
                          display: "flex",
                          gap: "1.25rem",
                          fontSize: "0.8rem",
                          color: "#64748b",
                          margin: "0.5rem 0",
                          flexWrap: "wrap",
                        }}
                      >
                        <span>
                          Deadline:{" "}
                          <strong>{item.submissionDeadline ? formatDate(item.submissionDeadline) : "No deadline"}</strong>
                        </span>
                        <span>
                          Type: <strong>{item.required ? "Required" : "Optional"}</strong>
                        </span>
                        <span>
                          Allowed: <strong>{item.allowedFormats || "Any"}</strong>
                        </span>
                        <span>
                          Max Size:{" "}
                          <strong>
                            {item.maxFileSizeBytes
                              ? `${(item.maxFileSizeBytes / (1024 * 1024)).toFixed(1)} MB`
                              : "10 MB"}
                          </strong>
                        </span>
                      </div>

                      {/* Review Remarks */}
                      {item.verificationRemarks && (
                        <div
                          style={{
                            background: "#fef3c7",
                            border: "1px solid #fde68a",
                            padding: "0.75rem",
                            borderRadius: "6px",
                            margin: "0.75rem 0",
                            fontSize: "0.85rem",
                          }}
                        >
                          <strong>Officer Review Remarks:</strong> {item.verificationRemarks}
                          {item.verifiedByName && (
                            <div style={{ fontSize: "0.75rem", color: "#78350f", marginTop: "0.25rem" }}>
                              Verified by: {item.verifiedByName} ({formatDate(item.verifiedAt)})
                            </div>
                          )}
                        </div>
                      )}

                      {/* Upload Box for Actionable Submissions */}
                      {isActionable && (
                        <div className="ben-upload-box">
                          <div>
                            <label
                              style={{
                                display: "block",
                                fontSize: "0.85rem",
                                fontWeight: 600,
                                marginBottom: "0.35rem",
                              }}
                            >
                              Choose Document *
                            </label>
                            <input
                              type="file"
                              accept={item.allowedFormats
                                ?.split(",")
                                .map((ext) => `.${ext.trim()}`)
                                .join(",")}
                              onChange={(e) =>
                                setUploadFiles({
                                  ...uploadFiles,
                                  [item.id]: e.target.files?.[0] || null,
                                })
                              }
                            />
                          </div>

                          <div>
                            <label
                              style={{
                                display: "block",
                                fontSize: "0.85rem",
                                fontWeight: 600,
                                marginBottom: "0.35rem",
                              }}
                            >
                              Submission Notes (Optional)
                            </label>
                            <textarea
                              rows="2"
                              placeholder="Any explanation or details about the document…"
                              style={{
                                width: "100%",
                                padding: "0.5rem",
                                border: "1px solid #cbd5e1",
                                borderRadius: "6px",
                                fontSize: "0.85rem",
                              }}
                              value={uploadNotes[item.id] || ""}
                              onChange={(e) =>
                                setUploadNotes({
                                  ...uploadNotes,
                                  [item.id]: e.target.value,
                                })
                              }
                            />
                          </div>

                          <div>
                            <button
                              type="button"
                              className="mgmt-btn primary"
                              disabled={isBusy || !uploadFiles[item.id]}
                              onClick={() => handleComplianceSubmit(item)}
                            >
                              <FileUp size={16} />
                              {isBusy
                                ? "Uploading…"
                                : item.status === "PENDING" || item.status === "OVERDUE"
                                ? "Upload & Submit Document"
                                : "Replace & Resubmit Document"}
                            </button>
                          </div>
                        </div>
                      )}
                    </article>
                  );
                })}
              </div>
            )}
          </div>
        </section>
      )}

      {/* ========================================================================= */}
      {/* TAB 3: EXPENDITURE REPORTS                                                */}
      {/* ========================================================================= */}
      {activeTab === "expenditure" && (
        <section>
          {/* Submission Form */}
          <div className="ben-card">
            <div className="mgmt-card-header">
              <h2>{editingExpId ? "Correct Expenditure Report" : "Report Grant Expenditure"}</h2>
              <span style={{ fontSize: "0.85rem", color: "#64748b" }}>
                Report utilization against released grant installments with supporting voucher evidence
              </span>
            </div>

            <form onSubmit={handleExpenditureSubmit}>
              <div
                style={{
                  display: "grid",
                  gridTemplateColumns: "1fr 1fr",
                  gap: "1rem",
                  marginBottom: "1rem",
                }}
              >
                <div className="mgmt-form-group">
                  <label>Approved Grant Application *</label>
                  <select
                    required
                    disabled={Boolean(editingExpId)}
                    value={expForm.applicationId}
                    onChange={(e) =>
                      setExpForm({
                        ...expForm,
                        applicationId: e.target.value,
                        installmentId: "",
                        supportingComplianceIds: [],
                      })
                    }
                  >
                    <option value="">-- Choose Application --</option>
                    {plans.map((p) => (
                      <option key={p.id} value={p.applicationId}>
                        App #{p.applicationId} · {p.schemeName}
                      </option>
                    ))}
                  </select>
                </div>

                <div className="mgmt-form-group">
                  <label>Released Installment *</label>
                  <select
                    required
                    disabled={Boolean(editingExpId)}
                    value={expForm.installmentId}
                    onChange={(e) =>
                      setExpForm({
                        ...expForm,
                        installmentId: e.target.value,
                        supportingComplianceIds: [],
                      })
                    }
                  >
                    <option value="">-- Choose Released Stage --</option>
                    {releasedInstallments.map((i) => (
                      <option key={i.id} value={i.id}>
                        Stage {i.installmentNumber} · {i.title} ({formatCurrency(i.amount)})
                      </option>
                    ))}
                  </select>
                </div>
              </div>

              <div
                style={{
                  display: "grid",
                  gridTemplateColumns: "1fr 1fr",
                  gap: "1rem",
                  marginBottom: "1rem",
                }}
              >
                <div className="mgmt-form-group">
                  <label>Reported Expenditure Amount (₹) *</label>
                  <input
                    type="number"
                    step="0.01"
                    min="0.01"
                    required
                    max={selectedInstallment?.amount}
                    placeholder="Enter utilized amount"
                    value={expForm.reportedAmount}
                    onChange={(e) =>
                      setExpForm({ ...expForm, reportedAmount: e.target.value })
                    }
                  />
                  {selectedInstallment && (
                    <small style={{ color: "#64748b" }}>
                      Max allowed: {formatCurrency(selectedInstallment.amount)}
                    </small>
                  )}
                </div>

                <div className="mgmt-form-group">
                  <label>Expenditure Category *</label>
                  <input
                    type="text"
                    required
                    maxLength="100"
                    placeholder="e.g. Equipment, Materials, Construction, Labor"
                    value={expForm.category}
                    onChange={(e) =>
                      setExpForm({ ...expForm, category: e.target.value })
                    }
                  />
                </div>
              </div>

              <div className="mgmt-form-group">
                <label>Description of Expenditure</label>
                <textarea
                  rows="2"
                  placeholder="Details of utilized funds, vendors, or activity accomplished…"
                  value={expForm.description}
                  onChange={(e) =>
                    setExpForm({ ...expForm, description: e.target.value })
                  }
                />
              </div>

              {/* Supporting Compliance Evidence Checkboxes */}
              {eligibleEvidenceDocs.length > 0 && (
                <div className="mgmt-form-group">
                  <label>Attach Supporting Compliance Documents</label>
                  <div
                    style={{
                      background: "#f8fafc",
                      border: "1px solid #e2e8f0",
                      borderRadius: "6px",
                      padding: "0.75rem",
                    }}
                  >
                    {eligibleEvidenceDocs.map((doc) => (
                      <label
                        key={doc.id}
                        style={{
                          display: "flex",
                          alignItems: "center",
                          gap: "0.5rem",
                          marginBottom: "0.35rem",
                          cursor: "pointer",
                          fontSize: "0.85rem",
                        }}
                      >
                        <input
                          type="checkbox"
                          checked={expForm.supportingComplianceIds.includes(doc.id)}
                          onChange={() => toggleEvidenceDoc(doc.id)}
                        />
                        <span>
                          {doc.documentFileName || doc.requirementTitle} ({doc.status})
                        </span>
                      </label>
                    ))}
                  </div>
                </div>
              )}

              <div style={{ display: "flex", gap: "0.75rem" }}>
                <button
                  type="submit"
                  className="mgmt-btn primary"
                  disabled={submittingExp || releasedInstallments.length === 0}
                >
                  <Send size={15} />
                  {submittingExp
                    ? "Submitting…"
                    : editingExpId
                    ? "Resubmit Corrected Report"
                    : "Submit Expenditure Report"}
                </button>
                {editingExpId && (
                  <button
                    type="button"
                    className="mgmt-btn secondary"
                    onClick={() => {
                      setEditingExpId(null);
                      setExpForm(blankExpenditureForm);
                    }}
                  >
                    Cancel Correction
                  </button>
                )}
              </div>
            </form>
          </div>

          {/* Past Reports List */}
          <div className="ben-card">
            <div className="mgmt-card-header">
              <h2>Submitted Expenditure Reports</h2>
            </div>

            {expenditureReports.length === 0 ? (
              <p style={{ color: "#64748b" }}>No expenditure reports submitted yet.</p>
            ) : (
              <div className="mgmt-table-wrap">
                <table className="mgmt-table">
                  <thead>
                    <tr>
                      <th>Report #</th>
                      <th>Category</th>
                      <th>Application &amp; Stage</th>
                      <th>Reported Amount</th>
                      <th>Status</th>
                      <th>Submitted Date</th>
                      <th>Officer Remarks</th>
                      <th>Action</th>
                    </tr>
                  </thead>
                  <tbody>
                    {expenditureReports.map((r) => (
                      <tr key={r.id}>
                        <td>
                          <strong>#{r.id}</strong>
                        </td>
                        <td>{r.category}</td>
                        <td>
                          App #{r.applicationId} · Stage {r.installmentNumber}
                        </td>
                        <td>
                          <strong>{formatCurrency(r.reportedAmount)}</strong>
                        </td>
                        <td>
                          <span
                            className={`mgmt-status-badge status-${r.status?.toLowerCase()}`}
                          >
                            {r.status}
                          </span>
                        </td>
                        <td>{formatDate(r.submittedAt)}</td>
                        <td>
                          {r.reviewRemarks ? (
                            <span style={{ color: "#b45309", fontSize: "0.85rem" }}>
                              {r.reviewRemarks}
                            </span>
                          ) : (
                            "—"
                          )}
                        </td>
                        <td>
                          {["REJECTED", "CORRECTION_REQUIRED"].includes(r.status) && (
                            <button
                              type="button"
                              className="mgmt-btn secondary"
                              style={{ padding: "0.25rem 0.5rem", fontSize: "0.75rem" }}
                              onClick={() => handleStartCorrection(r)}
                            >
                              Correct
                            </button>
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
    </div>
  );
}
