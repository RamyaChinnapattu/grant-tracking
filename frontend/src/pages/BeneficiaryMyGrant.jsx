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
  RefreshCw,
  ShieldCheck,
  X,
} from "lucide-react";
import { getMyDisbursementPlans } from "../services/disbursementService";
import {
  getComplianceMine,
  submitComplianceDocument,
  getComplianceDocumentFile,
} from "../services/complianceService";
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

function formatFileSize(bytes) {
  const n = Number(bytes);
  if (!bytes || isNaN(n) || n <= 0) return "Not configured";
  if (n >= 1024 * 1024) {
    const mb = n / (1024 * 1024);
    return `${mb.toFixed(n % (1024 * 1024) === 0 ? 1 : 2)} MB`;
  }
  if (n >= 1024) return `${Math.round(n / 1024)} KB`;
  return `${n} B`;
}

export default function BeneficiaryMyGrant() {
  const [searchParams, setSearchParams] = useSearchParams();
  const currentTab = searchParams.get("tab") || "schedule";
  const [activeTab, setActiveTab] = useState(
    currentTab === "expenditure" ? "schedule" : currentTab
  );

  useEffect(() => {
    const tabParam = searchParams.get("tab") || "schedule";
    setActiveTab(tabParam === "expenditure" ? "schedule" : tabParam);
  }, [searchParams]);

  const switchTab = (tab) => {
    const resolvedTab = tab === "expenditure" ? "schedule" : tab;
    setActiveTab(resolvedTab);
    setSearchParams({ tab: resolvedTab });
  };

  // Independent loading and error states for the 2 sections
  const [plans, setPlans] = useState([]);
  const [loadingPlans, setLoadingPlans] = useState(true);
  const [errorPlans, setErrorPlans] = useState("");

  const [complianceList, setComplianceList] = useState([]);
  const [loadingCompliance, setLoadingCompliance] = useState(true);
  const [errorCompliance, setErrorCompliance] = useState("");

  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState("");
  const [successMsg, setSuccessMsg] = useState("");

  // Upload state for compliance
  const [uploadFiles, setUploadFiles] = useState({});
  const [uploadNotes, setUploadNotes] = useState({});
  const [submittingDocId, setSubmittingDocId] = useState(null);

  // Independent loaders
  const loadPlans = useCallback(async (isSilent = false) => {
    if (!isSilent) setLoadingPlans(true);
    setErrorPlans("");
    try {
      const res = await getMyDisbursementPlans();
      setPlans(res.data || []);
    } catch (err) {
      console.error("Failed to load disbursement plans:", err);
      const msg =
        err.response?.data?.message || err.message || "Failed to load disbursement plans.";
      setErrorPlans(msg);
      setPlans([]);
    } finally {
      setLoadingPlans(false);
    }
  }, []);

  const loadCompliance = useCallback(async (isSilent = false) => {
    if (!isSilent) setLoadingCompliance(true);
    setErrorCompliance("");
    try {
      const res = await getComplianceMine();
      setComplianceList(res.data || []);
    } catch (err) {
      console.error("Failed to load compliance documents:", err);
      const msg =
        err.response?.data?.message || err.message || "Failed to load compliance documents.";
      setErrorCompliance(msg);
      setComplianceList([]);
    } finally {
      setLoadingCompliance(false);
    }
  }, []);

  const handleRefreshAll = useCallback(async () => {
    setRefreshing(true);
    await Promise.allSettled([
      loadPlans(true),
      loadCompliance(true),
    ]);
    setRefreshing(false);
  }, [loadPlans, loadCompliance]);

  useEffect(() => {
    loadPlans();
    loadCompliance();
  }, [loadPlans, loadCompliance]);

  // Overall financial summary
  const financialSummary = useMemo(() => {
    if (errorPlans) return null;
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
  }, [plans, errorPlans]);

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

    // Client-side validations for file format and file size
    const ext = file.name.includes(".")
      ? file.name.substring(file.name.lastIndexOf(".") + 1).toLowerCase()
      : "";
    const allowed = (item.allowedFormats || "pdf,png,jpg,jpeg")
      .toLowerCase()
      .split(",")
      .map((s) => s.trim())
      .filter(Boolean);
    if (allowed.length > 0 && !allowed.includes(ext)) {
      setError(`File format ".${ext}" is not allowed. Allowed formats: ${allowed.join(", ")}`);
      return;
    }

    const maxBytes = item.maxFileSizeBytes > 0 ? item.maxFileSizeBytes : 10485760;
    if (file.size > maxBytes) {
      setError(
        `File exceeds maximum allowed size of ${formatFileSize(maxBytes)}.`
      );
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
      await Promise.allSettled([loadCompliance(true), loadPlans(true)]);
    } catch (err) {
      setError(err.response?.data?.message || "Failed to submit document.");
    } finally {
      setSubmittingDocId(null);
    }
  };

  return (
    <div className="ben-grant-workspace">
      {/* Header */}
      <header className="ben-header">
        <div>
          <h1>My Grant</h1>
          <p>
            Track your grant payment schedule and upload milestone compliance documents.
          </p>
        </div>
        <div>
          <button
            className="mgmt-btn secondary"
            onClick={handleRefreshAll}
            disabled={refreshing || loadingPlans || loadingCompliance}
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
          <strong>
            {loadingPlans
              ? "Loading…"
              : errorPlans
              ? "—"
              : formatCurrency(financialSummary?.totalApproved)}
          </strong>
          <small>{errorPlans ? "Unable to load plan data" : "Across approved schemes"}</small>
        </article>

        <article className="mgmt-kpi-card">
          <span>
            <CheckCircle2 size={16} /> Total Disbursed / Released
          </span>
          <strong style={{ color: errorPlans ? "inherit" : "#15803d" }}>
            {loadingPlans
              ? "Loading…"
              : errorPlans
              ? "—"
              : formatCurrency(financialSummary?.totalDisbursed)}
          </strong>
          <small>{errorPlans ? "Unable to load plan data" : "Credited installments"}</small>
        </article>

        <article className="mgmt-kpi-card">
          <span>
            <Clock size={16} /> Remaining Balance
          </span>
          <strong>
            {loadingPlans
              ? "Loading…"
              : errorPlans
              ? "—"
              : formatCurrency(financialSummary?.totalRemaining)}
          </strong>
          <small>{errorPlans ? "Unable to load plan data" : "Pending milestone completion"}</small>
        </article>

        <article className="mgmt-kpi-card">
          <span>
            <FileText size={16} /> Pending Documents
          </span>
          <strong
            style={{
              color:
                !loadingCompliance && !errorCompliance && pendingDocsCount > 0
                  ? "#b45309"
                  : "#1e293b",
            }}
          >
            {loadingCompliance ? "Loading…" : errorCompliance ? "—" : pendingDocsCount}
          </strong>
          <small>{errorCompliance ? "Unable to load compliance data" : "Require your action"}</small>
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
          {!loadingPlans && !errorPlans && plans.length > 0 && (
            <span className="ben-badge">{plans.length}</span>
          )}
        </button>
        <button
          className={`ben-tab-btn ${activeTab === "documents" ? "active" : ""}`}
          onClick={() => switchTab("documents")}
        >
          <BadgeCheck size={18} />
          Required Documents
          {!loadingCompliance && !errorCompliance && pendingDocsCount > 0 && (
            <span className="ben-badge">{pendingDocsCount}</span>
          )}
        </button>
      </nav>

      {/* ========================================================================= */}
      {/* TAB 1: PAYMENT SCHEDULE                                                   */}
      {/* ========================================================================= */}
      {activeTab === "schedule" && (
        <section>
          {loadingPlans ? (
            <div className="ben-card" style={{ textAlign: "center", padding: "3rem" }}>
              <RefreshCw size={36} className="spin" style={{ opacity: 0.5, marginBottom: "0.5rem" }} />
              <h3>Loading payment schedules…</h3>
              <p style={{ color: "#64748b" }}>Please wait while your disbursement plans are retrieved.</p>
            </div>
          ) : errorPlans ? (
            <div className="ben-card" style={{ textAlign: "center", padding: "2.5rem" }}>
              <AlertCircle size={40} style={{ color: "#dc2626", marginBottom: "0.5rem" }} />
              <h3 style={{ color: "#991b1b" }}>Failed to Load Payment Schedules</h3>
              <p style={{ color: "#64748b", marginBottom: "1rem" }}>{errorPlans}</p>
              <button
                type="button"
                className="mgmt-btn secondary"
                onClick={() => loadPlans()}
              >
                <RefreshCw size={14} /> Retry Loading Schedules
              </button>
            </div>
          ) : plans.length === 0 ? (
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

            {loadingCompliance ? (
              <div style={{ textAlign: "center", padding: "2.5rem", color: "#64748b" }}>
                <RefreshCw size={32} className="spin" style={{ opacity: 0.5, marginBottom: "0.5rem" }} />
                <p>Loading compliance requirements…</p>
              </div>
            ) : errorCompliance ? (
              <div style={{ textAlign: "center", padding: "2rem" }}>
                <AlertCircle size={36} style={{ color: "#dc2626", marginBottom: "0.5rem" }} />
                <h3 style={{ color: "#991b1b" }}>Failed to Load Compliance Documents</h3>
                <p style={{ color: "#64748b", marginBottom: "1rem" }}>{errorCompliance}</p>
                <button
                  type="button"
                  className="mgmt-btn secondary"
                  onClick={() => loadCompliance()}
                >
                  <RefreshCw size={14} /> Retry Loading Documents
                </button>
              </div>
            ) : complianceList.length === 0 ? (
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
                            {formatFileSize(item.maxFileSizeBytes)}
                          </strong>
                        </span>
                      </div>

                      {/* Attached Document Information & Download */}
                      {item.hasDocument && (
                        <div
                          style={{
                            background: "#f8fafc",
                            border: "1px solid #e2e8f0",
                            borderRadius: "6px",
                            padding: "0.6rem 0.85rem",
                            margin: "0.5rem 0",
                            fontSize: "0.85rem",
                            display: "flex",
                            alignItems: "center",
                            justifyContent: "space-between",
                            flexWrap: "wrap",
                            gap: "0.5rem",
                          }}
                        >
                          <div>
                            <FileCheck
                              size={16}
                              style={{
                                color: "#16a34a",
                                verticalAlign: "middle",
                                marginRight: "0.4rem",
                              }}
                            />
                            <strong>Attached Document:</strong>{" "}
                            {item.documentFileName || "Document on file"}
                            {item.documentFileSize && (
                              <span style={{ color: "#64748b", marginLeft: "0.5rem" }}>
                                ({(item.documentFileSize / 1024).toFixed(1)} KB)
                              </span>
                            )}
                            {item.submittedAt && (
                              <span style={{ color: "#64748b", marginLeft: "0.5rem" }}>
                                · Submitted {formatDate(item.submittedAt)}
                              </span>
                            )}
                          </div>
                          <button
                            type="button"
                            className="mgmt-btn secondary"
                            style={{
                              padding: "0.25rem 0.6rem",
                              fontSize: "0.8rem",
                              display: "inline-flex",
                              alignItems: "center",
                              gap: "0.3rem",
                            }}
                            onClick={() => openDocument(item.id, item.documentFileName)}
                            title="Download and view uploaded document"
                          >
                            <Download size={13} /> Download
                          </button>
                        </div>
                      )}

                      {/* Submitted status banner */}
                      {item.status === "SUBMITTED" && (
                        <div
                          style={{
                            background: "#eff6ff",
                            border: "1px solid #bfdbfe",
                            borderRadius: "6px",
                            padding: "0.6rem 0.85rem",
                            margin: "0.5rem 0",
                            fontSize: "0.85rem",
                            color: "#1e40af",
                            display: "flex",
                            alignItems: "center",
                            gap: "0.4rem",
                          }}
                        >
                          <Clock size={16} />
                          <span>
                            Document submitted and queued for officer verification.
                          </span>
                        </div>
                      )}

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

                      {/* Submission and Review History Accordion */}
                      {item.reviewHistory && item.reviewHistory.length > 0 && (
                        <details
                          style={{
                            margin: "0.5rem 0",
                            padding: "0.5rem 0.75rem",
                            background: "#f8fafc",
                            border: "1px solid #e2e8f0",
                            borderRadius: "6px",
                            fontSize: "0.8rem",
                            color: "#475569",
                          }}
                        >
                          <summary style={{ cursor: "pointer", fontWeight: 600, color: "#1e40af" }}>
                            Review &amp; Submission History ({item.reviewHistory.length})
                          </summary>
                          <div style={{ marginTop: "0.5rem", paddingLeft: "0.5rem", borderLeft: "2px solid #cbd5e1" }}>
                            {item.reviewHistory.map((h, idx) => (
                              <div key={idx} style={{ marginBottom: "0.4rem" }}>
                                <span
                                  className={`mgmt-status-badge status-${h.action?.toLowerCase()}`}
                                  style={{ fontSize: "0.7rem", padding: "0.1rem 0.4rem" }}
                                >
                                  {h.action}
                                </span>
                                <strong style={{ marginLeft: "0.35rem" }}>{h.actorName || "System"}</strong> · {formatDate(h.occurredAt)}
                                {h.remarks && (
                                  <div style={{ color: "#334155", marginLeft: "0.25rem", fontStyle: "italic", marginTop: "0.1rem" }}>
                                    "{h.remarks}"
                                  </div>
                                )}
                                {h.documentFileName && (
                                  <div style={{ color: "#64748b", marginLeft: "0.25rem", fontSize: "0.75rem" }}>
                                    File: {h.documentFileName}
                                  </div>
                                )}
                              </div>
                            ))}
                          </div>
                        </details>
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

    </div>
  );
}
