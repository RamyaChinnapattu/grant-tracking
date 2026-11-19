
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
  RefreshCw,
  Search,
  ShieldCheck,
  UserCheck,
  UserRound,
  Users,
  X,
} from "lucide-react";
import api from "../services/api";
import {
  assignApplicationOfficer,
  downloadApplicationDocument,
  escalateApplication,
  getVerificationHistory,
  getVerificationQueue,
  submitVerificationDecision,
} from "../services/grantApplicationService";
import "../styles/VerificationWorkflow.css";

const emptyOfficer = {
  fullName: "",
  email: "",
  password: "",
  phone: "",
  officerLevel: "FIELD",
  jurisdictionState: "",
  jurisdictionDistrict: "",
};

const emptyRule = {
  minimumScore: "0",
  maximumScore: "100",
  minimumAmount: "0",
  maximumAmount: "",
  priority: "NORMAL",
};

const stageNames = {
  FIELD: "Field Officer",
  DISTRICT: "District Officer",
  FINANCE: "Finance Officer",
};

const stageLabels = {
  FIELD: "Field Verification",
  DISTRICT: "District Review",
  FINANCE: "Finance Review",
};

function messageFrom(error, fallback) {
  return error.response?.data?.message || error.response?.data?.error || fallback;
}

function formatAmount(value) {
  return new Intl.NumberFormat("en-IN", {
    style: "currency",
    currency: "INR",
    maximumFractionDigits: 2,
  }).format(value || 0);
}

function calculateAge(dobString) {
  if (!dobString) return null;
  const birthDate = new Date(dobString);
  if (isNaN(birthDate.getTime())) return null;
  const today = new Date();
  let age = today.getFullYear() - birthDate.getFullYear();
  const m = today.getMonth() - birthDate.getMonth();
  if (m < 0 || (m === 0 && today.getDate() < birthDate.getDate())) {
    age--;
  }
  return age >= 0 ? age : null;
}

function VerificationWorkflow({ historyOnly = false }) {
  const role = localStorage.getItem("role");
  const isAdmin = role === "ADMIN";
  const userId = Number(localStorage.getItem("userId") || 0);
  const officerLevel = localStorage.getItem("officerLevel") || (isAdmin ? "ADMIN" : "OFFICER");

  const [activeTab, setActiveTab] = useState(historyOnly ? "history" : "queue");
  const [queueApplications, setQueueApplications] = useState([]);
  const [historyApplications, setHistoryApplications] = useState([]);
  const [officers, setOfficers] = useState([]);
  const [rules, setRules] = useState([]);

  // Filters
  const [searchQuery, setSearchQuery] = useState("");
  const [stageFilter, setStageFilter] = useState("ALL");
  const [priorityFilter, setPriorityFilter] = useState("ALL");

  // Selection and actions
  const [selected, setSelected] = useState(null);
  const [decision, setDecision] = useState("");
  const [remarks, setRemarks] = useState("");
  const [checklist, setChecklist] = useState({
    identityVerified: false,
    addressVerified: false,
    documentsVerified: false,
    applicationDetailsVerified: false,
  });
  const [formValidationWarning, setFormValidationWarning] = useState("");

  // Admin forms
  const [officerForm, setOfficerForm] = useState(emptyOfficer);
  const [ruleForm, setRuleForm] = useState(emptyRule);
  const [editingOfficer, setEditingOfficer] = useState(null);
  const [editingRule, setEditingRule] = useState(null);
  const [assignmentOfficerId, setAssignmentOfficerId] = useState("");

  // Modals and states
  const [documentPreview, setDocumentPreview] = useState(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  const loadData = useCallback(async () => {
    setLoading(true);
    setError("");

    try {
      const queuePromise = getVerificationQueue();
      const historyPromise = getVerificationHistory();
      const tasks = [queuePromise, historyPromise];

      if (isAdmin) {
        tasks.push(api.get("/users"), api.get("/verification/routing-rules"));
      }

      const results = await Promise.all(tasks);
      const queueRes = results[0];
      const historyRes = results[1];

      setQueueApplications(Array.isArray(queueRes.data) ? queueRes.data : []);
      setHistoryApplications(Array.isArray(historyRes.data) ? historyRes.data : []);

      if (isAdmin && results[2] && results[3]) {
        const usersRes = results[2];
        const rulesRes = results[3];

        setOfficers(
          (usersRes.data || []).filter((user) => user.role === "OFFICER")
        );
        setRules(rulesRes.data || []);
      }
    } catch (requestError) {
      setError(messageFrom(requestError, "Unable to load verification workflow data."));
    } finally {
      setLoading(false);
    }
  }, [isAdmin]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  useEffect(() => {
    if (historyOnly) {
      setActiveTab("history");
    }
  }, [historyOnly]);

  useEffect(
    () => () => {
      if (documentPreview?.url) URL.revokeObjectURL(documentPreview.url);
    },
    [documentPreview?.url]
  );

  // Active list based on tab
  const currentApplications =
    activeTab === "history" ? historyApplications : queueApplications;

  // Filtered applications
  const filteredApplications = useMemo(() => {
    return currentApplications.filter((app) => {
      if (stageFilter !== "ALL" && app.workflowStage !== stageFilter) {
        return false;
      }

      if (priorityFilter === "ESCALATED" && !app.escalated) {
        return false;
      }

      if (
        priorityFilter !== "ALL" &&
        priorityFilter !== "ESCALATED" &&
        (app.routingPriority || "NORMAL") !== priorityFilter
      ) {
        return false;
      }

      if (searchQuery.trim()) {
        const query = searchQuery.trim().toLowerCase();
        const matchesId = String(app.id).includes(query);
        const matchesScheme = (app.schemeName || "").toLowerCase().includes(query);
        const matchesBeneficiary = (app.beneficiaryName || "").toLowerCase().includes(query);
        const matchesDistrict = (app.beneficiaryDetails?.district || "").toLowerCase().includes(query);
        const matchesState = (app.beneficiaryDetails?.state || "").toLowerCase().includes(query);

        return (
          matchesId ||
          matchesScheme ||
          matchesBeneficiary ||
          matchesDistrict ||
          matchesState
        );
      }

      return true;
    });
  }, [currentApplications, stageFilter, priorityFilter, searchQuery]);

  // Statistics
  const stats = useMemo(() => {
    const queue = queueApplications;

    return {
      totalQueue: queue.length,
      fieldStage: queue.filter((a) => a.workflowStage === "FIELD").length,
      districtStage: queue.filter((a) => a.workflowStage === "DISTRICT").length,
      financeStage: queue.filter((a) => a.workflowStage === "FINANCE").length,
      escalated: queue.filter((a) => a.escalated).length,
      totalHistory: historyApplications.length,
    };
  }, [queueApplications, historyApplications]);

  const selectedStage = selected?.workflowStage;

  const canDecide = useMemo(() => {
    if (!selected || activeTab === "history") return false;

    if (isAdmin) {
      return selectedStage === "FINANCE";
    }

    return selected.assignedOfficerId === userId;
  }, [selected, activeTab, isAdmin, selectedStage, userId]);

  const assignableOfficers = useMemo(() => {
    if (!selected || !isAdmin) return [];

    return officers.filter(
      (officer) =>
        officer.active !== false &&
        officer.officerLevel === selected.workflowStage &&
        (officer.jurisdictionState || "").trim().toLowerCase() ===
        (selected.beneficiaryDetails?.state || "").trim().toLowerCase() &&
        (selected.workflowStage === "FINANCE" ||
          (officer.jurisdictionDistrict || "").trim().toLowerCase() ===
          (selected.beneficiaryDetails?.district || "").trim().toLowerCase())
    );
  }, [selected, isAdmin, officers]);

  const decisions = useMemo(() => {
    if (selectedStage === "FIELD") {
      return [
        {
          value: "VERIFIED",
          label: "Approve Verification (Advance to District Review)",
        },
        {
          value: "NEEDS_CORRECTION",
          label: "Request Corrections from Beneficiary",
        },
        {
          value: "REJECTED",
          label: "Reject Application",
        },
      ];
    }

    if (selectedStage === "DISTRICT") {
      return [
        {
          value: "APPROVED",
          label: "Approve District Review",
        },
        {
          value: "NEEDS_CORRECTION",
          label: "Return Application for Correction",
        },
        {
          value: "REJECTED",
          label: "Reject Application",
        },
      ];
    }

    if (selectedStage === "FINANCE") {
      return [
        {
          value: "APPROVED",
          label: "Grant Final Approval (Ready for Disbursement)",
        },
        {
          value: "REJECTED",
          label: "Reject Application",
        },
      ];
    }

    return [];
  }, [selectedStage]);

  // Previous decisions breakdown for District and Finance reviews
  const previousStageSummary = useMemo(() => {
    if (!selected || !selected.verificationHistory) return null;

    const history = selected.verificationHistory;
    const fieldRecord = [...history].reverse().find((h) => h.stage === "FIELD");
    const districtRecord = [...history].reverse().find((h) => h.stage === "DISTRICT");

    return {
      field: fieldRecord || null,
      district: districtRecord || null,
    };
  }, [selected]);

  const handleDecision = async (event) => {
    event.preventDefault();
    if (!selected || !decision) return;

    // Validate Field Checklist if approving Field stage
    if (selectedStage === "FIELD" && decision === "VERIFIED") {
      const allChecked =
        checklist.identityVerified &&
        checklist.addressVerified &&
        checklist.documentsVerified &&
        checklist.applicationDetailsVerified;

      if (!allChecked) {
        setFormValidationWarning(
          "All four checklist items (identity, address, documents, and application details) must be confirmed to approve field verification."
        );
        return;
      }

      if (!selected.documents || selected.documents.length === 0) {
        setFormValidationWarning(
          "At least one supporting document must be uploaded and reviewed before field verification approval."
        );
        return;
      }
    }

    // Validate mandatory remarks for Rejection or Correction
    if (["REJECTED", "NEEDS_CORRECTION"].includes(decision) && !remarks.trim()) {
      setFormValidationWarning(
        `A clear explanation is required in remarks when selecting ${decision.replaceAll("_", " ")}.`
      );
      return;
    }

    setFormValidationWarning("");

    if (
      !window.confirm(
        `Confirm recording decision "${decision.replaceAll("_", " ")}" for Application #${selected.id}?`
      )
    ) {
      return;
    }

    setSaving(true);
    setError("");
    setNotice("");

    try {
      await submitVerificationDecision(selected.id, {
        decision,
        remarks: remarks.trim(),
        ...(selectedStage === "FIELD" ? checklist : {}),
      });

      setNotice(
        `Application #${selected.id} successfully updated with decision: ${decision.replaceAll("_", " ")}.`
      );

      setSelected(null);
      setDecision("");
      setRemarks("");
      setChecklist({
        identityVerified: false,
        addressVerified: false,
        documentsVerified: false,
        applicationDetailsVerified: false,
      });

      await loadData();
    } catch (requestError) {
      setError(messageFrom(requestError, "Unable to record the verification decision."));
    } finally {
      setSaving(false);
    }
  };

  const handleEscalation = async (application) => {
    if (
      !window.confirm(
        `Escalate Application #${application.id} to another reviewer within its current jurisdiction?`
      )
    ) {
      return;
    }

    setSaving(true);
    setError("");
    setNotice("");

    try {
      await escalateApplication(application.id);
      setNotice(`Application #${application.id} escalated and reassigned successfully.`);
      setSelected(null);
      await loadData();
    } catch (requestError) {
      setError(messageFrom(requestError, "Unable to escalate this application."));
    } finally {
      setSaving(false);
    }
  };

  const handleAssignment = async (event) => {
    event.preventDefault();
    if (!selected || !assignmentOfficerId) return;

    setSaving(true);
    setError("");
    setNotice("");

    try {
      await assignApplicationOfficer(selected.id, Number(assignmentOfficerId));
      setNotice(`Application #${selected.id} assigned to selected officer successfully.`);
      setSelected(null);
      await loadData();
    } catch (requestError) {
      setError(messageFrom(requestError, "Unable to assign this application."));
    } finally {
      setSaving(false);
    }
  };

  const handleDownload = async (application, document) => {
    setError("");

    try {
      const response = await downloadApplicationDocument(application.id, document.id);
      const url = URL.createObjectURL(response.data);
      const link = window.document.createElement("a");

      link.href = url;
      link.download = document.fileName;
      link.click();

      URL.revokeObjectURL(url);
    } catch (requestError) {
      setError(messageFrom(requestError, "Unable to download this document."));
    }
  };

  const handlePreview = async (application, document) => {
    setError("");

    try {
      const response = await downloadApplicationDocument(application.id, document.id);

      setDocumentPreview({
        url: URL.createObjectURL(response.data),
        contentType: document.contentType,
        fileName: document.fileName,
      });
    } catch (requestError) {
      setError(messageFrom(requestError, "Unable to view this document."));
    }
  };

  const handleOfficerCreate = async (event) => {
    event.preventDefault();
    setSaving(true);
    setError("");
    setNotice("");

    try {
      await api.post("/users", {
        ...officerForm,
        role: "OFFICER",
        active: true,
        jurisdictionDistrict:
          officerForm.officerLevel === "FINANCE"
            ? null
            : officerForm.jurisdictionDistrict,
      });

      setOfficerForm(emptyOfficer);
      setNotice("Verification officer account created and assigned to jurisdiction.");
      await loadData();
    } catch (requestError) {
      setError(messageFrom(requestError, "Unable to create the officer."));
    } finally {
      setSaving(false);
    }
  };

  const handleOfficerUpdate = async (event) => {
    event.preventDefault();
    setSaving(true);
    setError("");

    try {
      await api.put(`/users/${editingOfficer.id}`, {
        ...editingOfficer,
        role: "OFFICER",
        password: undefined,
        jurisdictionDistrict:
          editingOfficer.officerLevel === "FINANCE"
            ? null
            : editingOfficer.jurisdictionDistrict,
      });

      setEditingOfficer(null);
      setNotice("Officer jurisdiction and scope updated.");
      await loadData();
    } catch (requestError) {
      setError(messageFrom(requestError, "Unable to update officer assignment."));
    } finally {
      setSaving(false);
    }
  };

  const handleRuleCreate = async (event) => {
    event.preventDefault();
    setSaving(true);
    setError("");

    try {
      const payload = {
        ...ruleForm,
        minimumScore: Number(ruleForm.minimumScore),
        maximumScore: Number(ruleForm.maximumScore),
        minimumAmount: Number(ruleForm.minimumAmount),
        maximumAmount:
          ruleForm.maximumAmount === "" ? null : Number(ruleForm.maximumAmount),
      };

      if (editingRule) {
        await api.put(`/verification/routing-rules/${editingRule.id}`, payload);
      } else {
        await api.post("/verification/routing-rules", payload);
      }

      setRuleForm(emptyRule);
      setEditingRule(null);
      setNotice(editingRule ? "Workflow routing rule updated." : "Workflow routing rule saved.");
      await loadData();
    } catch (requestError) {
      setError(messageFrom(requestError, "Unable to save routing rule."));
    } finally {
      setSaving(false);
    }
  };

  const handleRuleDelete = async (ruleId) => {
    if (
      !window.confirm(
        "Delete this routing rule? Existing applications will retain their priority."
      )
    ) {
      return;
    }

    setSaving(true);
    setError("");

    try {
      await api.delete(`/verification/routing-rules/${ruleId}`);
      setNotice("Routing rule deleted successfully.");
      await loadData();
    } catch (requestError) {
      setError(messageFrom(requestError, "Unable to delete routing rule."));
    } finally {
      setSaving(false);
    }
  };

  // Header content based on level
  const portalHeading = useMemo(() => {
    if (isAdmin) {
      return {
        eyebrow: "ADMINISTRATIVE WORKFLOW MANAGEMENT",
        title: "Unified Verifications & Approvals",
        subtitle:
          "Monitor applications across Field, District, and Finance stages. Manage officer assignments and escalation queues.",
      };
    }

    if (officerLevel === "FIELD") {
      return {
        eyebrow: "FIELD VERIFICATION OFFICER PORTAL",
        title: "Field Verification Queue",
        subtitle:
          "Conduct in-person identity, address, and document verification. Verify eligibility information, record checklist confirmations, and recommend actions.",
      };
    }

    if (officerLevel === "DISTRICT") {
      return {
        eyebrow: "DISTRICT VERIFICATION OFFICER PORTAL",
        title: "District Verification & Review",
        subtitle:
          "Review field officer verification reports and recommendations. Approve valid applications, return for correction, or reject with reasons.",
      };
    }

    if (officerLevel === "FINANCE") {
      return {
        eyebrow: "FINANCE APPROVAL OFFICER PORTAL",
        title: "Finance Review & Final Approval",
        subtitle:
          "Review previous verification history and grant criteria. Record the final finance decision for each application.",
      };
    }

    return {
      eyebrow: "VERIFICATION WORKFLOW",
      title: "Verifications and Approvals",
      subtitle:
        "Review assigned applications, verify supporting documents, and record official stage decisions.",
    };
  }, [isAdmin, officerLevel]);

  return (
    <div className="verification-workflow">
      {/* Top Header */}
      <header className="verification-workflow__header">
        <div>
          <span className="verification-workflow__eyebrow">
            {portalHeading.eyebrow}
          </span>
          <h2>{portalHeading.title}</h2>
          <p>{portalHeading.subtitle}</p>
        </div>

        <button
          type="button"
          className="verification-button"
          onClick={loadData}
          disabled={loading}
          title="Refresh queue and status"
        >
          <RefreshCw
            size={16}
            className={loading ? "spin" : ""}
            aria-hidden="true"
          />
          {loading ? "Refreshing…" : "Refresh"}
        </button>
      </header>

      {/* Metric Cards Row */}
      <div className="verification-stats">
        <div className="verification-stat-card">
          <span className="verification-stat-card__icon verification-stat-card__icon--primary">
            <Clock size={20} />
          </span>
          <div className="verification-stat-card__content">
            <span className="verification-stat-card__value">
              {stats.totalQueue}
            </span>
            <span className="verification-stat-card__label">Active Queue</span>
          </div>
        </div>

        <div className="verification-stat-card">
          <span className="verification-stat-card__icon verification-stat-card__icon--field">
            <UserCheck size={20} />
          </span>
          <div className="verification-stat-card__content">
            <span className="verification-stat-card__value">
              {stats.fieldStage}
            </span>
            <span className="verification-stat-card__label">Field Stage</span>
          </div>
        </div>

        <div className="verification-stat-card">
          <span className="verification-stat-card__icon verification-stat-card__icon--district">
            <FileCheck2 size={20} />
          </span>
          <div className="verification-stat-card__content">
            <span className="verification-stat-card__value">
              {stats.districtStage}
            </span>
            <span className="verification-stat-card__label">District Review</span>
          </div>
        </div>

        <div className="verification-stat-card">
          <span className="verification-stat-card__icon verification-stat-card__icon--finance">
            <ShieldCheck size={20} />
          </span>
          <div className="verification-stat-card__content">
            <span className="verification-stat-card__value">
              {stats.financeStage}
            </span>
            <span className="verification-stat-card__label">Finance Review</span>
          </div>
        </div>

        <div className="verification-stat-card">
          <span className="verification-stat-card__icon verification-stat-card__icon--escalated">
            <AlertTriangle size={20} />
          </span>
          <div className="verification-stat-card__content">
            <span className="verification-stat-card__value">
              {stats.escalated}
            </span>
            <span className="verification-stat-card__label">Escalated</span>
          </div>
        </div>
      </div>

      {/* Navigation Tabs */}
      <nav className="verification-tabs" aria-label="Workflow Sections">
        <button
          type="button"
          className={`verification-tab ${activeTab === "queue" ? "verification-tab--active" : ""}`}
          onClick={() => {
            setActiveTab("queue");
            setSelected(null);
          }}
        >
          <Clock size={16} aria-hidden="true" />
          <span>Pending Verification Queue</span>
          <span className="verification-tab__badge">{stats.totalQueue}</span>
        </button>

        <button
          type="button"
          className={`verification-tab ${activeTab === "history" ? "verification-tab--active" : ""}`}
          onClick={() => {
            setActiveTab("history");
            setSelected(null);
          }}
        >
          <FileClock size={16} aria-hidden="true" />
          <span>Verification History</span>
          <span className="verification-tab__badge">{stats.totalHistory}</span>
        </button>

        {isAdmin && (
          <>
            <button
              type="button"
              className={`verification-tab ${activeTab === "officers" ? "verification-tab--active" : ""}`}
              onClick={() => {
                setActiveTab("officers");
                setSelected(null);
              }}
            >
              <Users size={16} aria-hidden="true" />
              <span>Officer Management</span>
            </button>

            <button
              type="button"
              className={`verification-tab ${activeTab === "rules" ? "verification-tab--active" : ""}`}
              onClick={() => {
                setActiveTab("rules");
                setSelected(null);
              }}
            >
              <Layers size={16} aria-hidden="true" />
              <span>Workflow Routing Rules</span>
            </button>
          </>
        )}
      </nav>

      {/* Global Alerts */}
      {error && (
        <div className="verification-alert verification-alert--error" role="alert">
          <AlertCircle size={18} aria-hidden="true" />
          <span>{error}</span>
        </div>
      )}

      {notice && (
        <div className="verification-alert verification-alert--success" role="status">
          <CheckCircle2 size={18} aria-hidden="true" />
          <span>{notice}</span>
        </div>
      )}

      {/* MAIN QUEUE OR HISTORY VIEW */}
      {(activeTab === "queue" || activeTab === "history") && (
        <>
          <div className="verification-controls">
            <div className="verification-search">
              <Search
                size={18}
                className="verification-search__icon"
                aria-hidden="true"
              />

              <input
                type="text"
                placeholder="Search by ID, applicant name, scheme, district or state…"
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                aria-label="Search verification applications"
              />

              {searchQuery && (
                <button
                  type="button"
                  className="verification-search__clear"
                  onClick={() => setSearchQuery("")}
                  aria-label="Clear search"
                >
                  <X size={15} />
                </button>
              )}
            </div>

            <div className="verification-filters">
              {isAdmin && (
                <div className="verification-filter-group">
                  <Filter size={15} aria-hidden="true" />
                  <select
                    value={stageFilter}
                    onChange={(e) => setStageFilter(e.target.value)}
                    aria-label="Filter by verification stage"
                  >
                    <option value="ALL">All Stages</option>
                    <option value="FIELD">Field Verification</option>
                    <option value="DISTRICT">District Review</option>
                    <option value="FINANCE">Finance Review</option>
                  </select>
                </div>
              )}

              <div className="verification-filter-group">
                <select
                  value={priorityFilter}
                  onChange={(e) => setPriorityFilter(e.target.value)}
                  aria-label="Filter by priority"
                >
                  <option value="ALL">All Priorities</option>
                  <option value="NORMAL">Normal</option>
                  <option value="PRIORITY">Priority</option>
                  <option value="URGENT">Urgent</option>
                  <option value="ESCALATED">Escalated Only</option>
                </select>
              </div>
            </div>
          </div>

          {/* Applications List */}
          <div className="verification-list">
            {loading ? (
              <div className="verification-empty">
                <RefreshCw size={24} className="spin" />
                <p>Loading applications…</p>
              </div>
            ) : filteredApplications.length === 0 ? (
              <div className="verification-empty">
                <FileText size={32} />
                <h4>No applications found</h4>
                <p>
                  {activeTab === "history"
                    ? "No completed verification history matches your criteria."
                    : "No applications are currently awaiting your verification in this queue."}
                </p>
              </div>
            ) : (
              filteredApplications.map((application) => {
                const isAssignedToCurrent =
                  application.assignedOfficerId === userId;

                return (
                  <article
                    className={`verification-card ${selected?.id === application.id ? "verification-card--selected" : ""}`}
                    key={application.id}
                  >
                    <div className="verification-card__main">
                      <div className="verification-card__title">
                        <h3>{application.schemeName}</h3>

                        <span className="verification-tag verification-tag--id">
                          #{application.id}
                        </span>

                        <span
                          className={`verification-tag verification-tag--stage verification-tag--stage-${(application.workflowStage || "").toLowerCase()}`}
                        >
                          {stageLabels[application.workflowStage] ||
                            application.workflowStage ||
                            "Unassigned"}
                        </span>

                        <span
                          className={`verification-tag verification-tag--priority verification-tag--${(application.routingPriority || "NORMAL").toLowerCase()}`}
                        >
                          {application.routingPriority || "NORMAL"}
                        </span>

                        {application.escalated && (
                          <span className="verification-tag verification-tag--escalated">
                            <AlertTriangle size={12} /> Escalated
                          </span>
                        )}
                      </div>

                      <div className="verification-card__meta">
                        <span>
                          <strong>Beneficiary:</strong> {application.beneficiaryName}
                          {application.beneficiaryDetails?.district
                            ? ` (${application.beneficiaryDetails.district}, ${application.beneficiaryDetails.state})`
                            : ""}
                        </span>

                        <span>
                          <strong>Requested:</strong>{" "}
                          {formatAmount(application.requestedAmount)}
                        </span>

                        <span>
                          <strong>Status:</strong>{" "}
                          {application.status.replaceAll("_", " ")}
                        </span>
                      </div>

                      <div className="verification-card__assignee">
                        {isAssignedToCurrent ? (
                          <span className="verification-assignee-badge verification-assignee-badge--me">
                            <UserCheck size={14} /> Assigned to You
                          </span>
                        ) : application.assignedOfficerName ? (
                          <span className="verification-assignee-badge">
                            <UserRound size={14} /> Assigned to{" "}
                            {application.assignedOfficerName}
                          </span>
                        ) : (
                          <span className="verification-assignee-badge verification-assignee-badge--none">
                            Awaiting Officer Assignment
                          </span>
                        )}

                        {application.submittedAt && (
                          <span className="verification-card__date">
                            Submitted:{" "}
                            {new Date(application.submittedAt).toLocaleDateString()}
                          </span>
                        )}
                      </div>
                    </div>

                    <div className="verification-card__actions">
                      <button
                        type="button"
                        className="verification-button verification-button--primary"
                        onClick={() => {
                          setSelected(application);
                          setAssignmentOfficerId(
                            String(application.assignedOfficerId || "")
                          );
                          setDecision("");
                          setRemarks("");
                          setFormValidationWarning("");
                          setChecklist({
                            identityVerified: false,
                            addressVerified: false,
                            documentsVerified: false,
                            applicationDetailsVerified: false,
                          });
                        }}
                      >
                        <Eye size={15} />
                        {canDecide ? "Review & Decide" : "View Details"}
                      </button>
                    </div>
                  </article>
                );
              })
            )}
          </div>
        </>
      )}

      {/* SELECTED APPLICATION REVIEW DRAWER / MODAL */}
      {selected && (
        <section
          className="verification-detail"
          aria-labelledby="verification-detail-title"
        >
          <div className="verification-detail__heading">
            <div>
              <div className="verification-detail__tags">
                <span className="verification-tag verification-tag--id">
                  Application #{selected.id}
                </span>

                <span
                  className={`verification-tag verification-tag--stage verification-tag--stage-${(selected.workflowStage || "").toLowerCase()}`}
                >
                  {stageLabels[selected.workflowStage] || selected.workflowStage}
                </span>

                <span className="verification-tag verification-tag--status">
                  {selected.status.replaceAll("_", " ")}
                </span>

                {selected.escalated && (
                  <span className="verification-tag verification-tag--escalated">
                    <AlertTriangle size={13} /> Escalated
                  </span>
                )}
              </div>

              <h3 id="verification-detail-title">{selected.schemeName}</h3>
            </div>

            <button
              type="button"
              className="verification-button"
              onClick={() => setSelected(null)}
              aria-label="Close details"
            >
              <X size={16} /> Close
            </button>
          </div>

          <div className="verification-detail__grid">
            {/* 1. Beneficiary Information */}
            <section className="verification-section">
              <h4>
                <UserCheck size={18} aria-hidden="true" />
                Beneficiary Identification & Location
              </h4>

              <dl>
                <dt>Full Name</dt>
                <dd>{selected.beneficiaryName}</dd>

                <dt>Email</dt>
                <dd>{selected.beneficiaryDetails?.email || "—"}</dd>

                <dt>Phone</dt>
                <dd>{selected.beneficiaryDetails?.phone || "—"}</dd>

                <dt>Date of Birth</dt>
                <dd>
                  {selected.beneficiaryDetails?.dateOfBirth || "—"}
                  {calculateAge(selected.beneficiaryDetails?.dateOfBirth) != null
                    ? ` (Age: ${calculateAge(selected.beneficiaryDetails?.dateOfBirth)} yrs)`
                    : ""}
                </dd>

                <dt>Address</dt>
                <dd>{selected.beneficiaryDetails?.address || "—"}</dd>

                <dt>District / State</dt>
                <dd>
                  {[
                    selected.beneficiaryDetails?.district,
                    selected.beneficiaryDetails?.state,
                  ]
                    .filter(Boolean)
                    .join(", ") || "—"}
                </dd>

                <dt>Category</dt>
                <dd>{selected.beneficiaryDetails?.category || "—"}</dd>

                <dt>Annual Income</dt>
                <dd>
                  {selected.beneficiaryDetails?.annualIncome != null
                    ? formatAmount(selected.beneficiaryDetails.annualIncome)
                    : "—"}
                </dd>
              </dl>
            </section>

            {/* 2. Grant Application & Criteria */}
            <section className="verification-section">
              <h4>
                <FileText size={18} aria-hidden="true" />
                Grant Details & Evaluation
              </h4>

              <div className="verification-grant-details">
                <p>
                  <strong>Requested Amount:</strong>{" "}
                  <span className="verification-amount">
                    {formatAmount(selected.requestedAmount)}
                  </span>
                </p>

                <p>
                  <strong>Application Statement:</strong>
                </p>

                <blockquote className="verification-statement">
                  {selected.applicationDetails}
                </blockquote>

                <div className="verification-eligibility-summary">
                  <p>
                    <strong>Eligibility Status:</strong>{" "}
                    <span
                      className={`verification-tag ${selected.eligibilityEvaluation?.eligibilityStatus === "ELIGIBLE"
                          ? "verification-tag--success"
                          : "verification-tag--warning"
                        }`}
                    >
                      {selected.eligibilityEvaluation?.eligibilityStatus ||
                        "Not Evaluated"}
                    </span>
                  </p>

                  <p>
                    <strong>Score:</strong>{" "}
                    <strong>
                      {selected.eligibilityEvaluation?.scoreOutOf100 ?? "—"} / 100
                    </strong>{" "}
                    (Threshold: {selected.eligibilityThreshold ?? "—"}%)
                  </p>

                  {selected.eligibilityEvaluation?.explanation && (
                    <p className="verification-explanation">
                      {selected.eligibilityEvaluation.explanation}
                    </p>
                  )}

                  <p>
                    <strong>Approval Workflow:</strong>{" "}
                    Field verification → District review → Finance final approval
                  </p>
                </div>
              </div>
            </section>

            {/* 3. Supporting Documents */}
            <section className="verification-section verification-detail__wide">
              <h4>
                <FileCheck2 size={18} aria-hidden="true" />
                Submitted Supporting Documents ({selected.documents?.length || 0})
              </h4>

              {selected.documents?.length ? (
                <ul className="verification-documents">
                  {selected.documents.map((document) => (
                    <li key={document.id}>
                      <div className="verification-document__meta">
                        <FileText size={18} aria-hidden="true" />

                        <div>
                          <strong>{document.fileName}</strong>
                          <span>
                            {(document.fileSize / 1024).toFixed(1)} KB · Uploaded by{" "}
                            {document.uploadedBy} on{" "}
                            {document.uploadedAt
                              ? new Date(document.uploadedAt).toLocaleString()
                              : "—"}
                          </span>
                        </div>
                      </div>

                      <div className="verification-document__actions">
                        <button
                          type="button"
                          className="verification-button"
                          onClick={() => handlePreview(selected, document)}
                        >
                          <Eye size={14} /> View
                        </button>

                        <button
                          type="button"
                          className="verification-button"
                          onClick={() => handleDownload(selected, document)}
                        >
                          <Download size={14} /> Download
                        </button>
                      </div>
                    </li>
                  ))}
                </ul>
              ) : (
                <p className="verification-hint">
                  No supporting documents have been uploaded for this application.
                </p>
              )}
            </section>

            {/* 4. Previous Review Decisions */}
            {(previousStageSummary?.field || previousStageSummary?.district) && (
              <section className="verification-section verification-detail__wide verification-previous-decisions">
                <h4>
                  <ShieldCheck size={18} aria-hidden="true" />
                  Previous Stage Verifications
                </h4>

                {previousStageSummary.field && (
                  <div className="verification-previous-card">
                    <div className="verification-previous-card__header">
                      <span className="verification-tag verification-tag--stage verification-tag--stage-field">
                        Field Verification
                      </span>

                      <strong>
                        Decision:{" "}
                        {previousStageSummary.field.action.replaceAll("_", " ")}
                      </strong>

                      <span>
                        by {previousStageSummary.field.actorName || "Field Officer"} on{" "}
                        {new Date(previousStageSummary.field.createdAt).toLocaleString()}
                      </span>
                    </div>

                    <div className="verification-previous-checklist">
                      <span
                        className={
                          previousStageSummary.field.identityVerified
                            ? "checked"
                            : "unchecked"
                        }
                      >
                        {previousStageSummary.field.identityVerified ? "✓" : "✗"} Identity Verified
                      </span>

                      <span
                        className={
                          previousStageSummary.field.addressVerified
                            ? "checked"
                            : "unchecked"
                        }
                      >
                        {previousStageSummary.field.addressVerified ? "✓" : "✗"} Address Verified
                      </span>

                      <span
                        className={
                          previousStageSummary.field.documentsVerified
                            ? "checked"
                            : "unchecked"
                        }
                      >
                        {previousStageSummary.field.documentsVerified ? "✓" : "✗"} Documents Verified
                      </span>

                      <span
                        className={
                          previousStageSummary.field.applicationDetailsVerified
                            ? "checked"
                            : "unchecked"
                        }
                      >
                        {previousStageSummary.field.applicationDetailsVerified ? "✓" : "✗"} Details Verified
                      </span>
                    </div>

                    {previousStageSummary.field.remarks && (
                      <p className="verification-previous-remarks">
                        <strong>Field Remarks:</strong>{" "}
                        &ldquo;{previousStageSummary.field.remarks}&rdquo;
                      </p>
                    )}
                  </div>
                )}

                {previousStageSummary.district && (
                  <div className="verification-previous-card">
                    <div className="verification-previous-card__header">
                      <span className="verification-tag verification-tag--stage verification-tag--stage-district">
                        District Review
                      </span>

                      <strong>
                        Decision:{" "}
                        {previousStageSummary.district.action.replaceAll("_", " ")}
                      </strong>

                      <span>
                        by {previousStageSummary.district.actorName || "District Officer"} on{" "}
                        {new Date(previousStageSummary.district.createdAt).toLocaleString()}
                      </span>
                    </div>

                    {previousStageSummary.district.remarks && (
                      <p className="verification-previous-remarks">
                        <strong>District Remarks:</strong>{" "}
                        &ldquo;{previousStageSummary.district.remarks}&rdquo;
                      </p>
                    )}
                  </div>
                )}
              </section>
            )}

            {/* 5. Complete Audit Timeline */}
            <section className="verification-section verification-detail__wide">
              <h4>
                <Clock size={18} aria-hidden="true" />
                Complete Verification & Status History
              </h4>

              {selected.verificationHistory?.length ||
                selected.statusHistory?.length ? (
                <ol className="verification-timeline">
                  {[
                    ...(selected.statusHistory || []).map((item) => ({
                      ...item,
                      timelineType: "status",
                    })),
                    ...(selected.verificationHistory || []).map((item) => ({
                      ...item,
                      timelineType: "verification",
                    })),
                  ]
                    .sort(
                      (left, right) =>
                        new Date(left.createdAt) - new Date(right.createdAt)
                    )
                    .map((item) => (
                      <li key={`${item.timelineType}-${item.id}`}>
                        <strong>
                          {item.timelineType === "verification"
                            ? `${stageNames[item.stage] || item.stage}: ${item.action.replaceAll("_", " ")}`
                            : item.action.replaceAll("_", " ")}
                        </strong>

                        <span>
                          {item.actorName || "System"} ·{" "}
                          {new Date(item.createdAt).toLocaleString()}

                          {item.previousStatus || item.newStatus
                            ? ` · ${item.previousStatus || "—"} → ${item.newStatus || "—"}`
                            : ""}

                          {item.nextStage
                            ? ` · Next: ${stageNames[item.nextStage] || item.nextStage}`
                            : ""}

                          {item.assignedOfficerName
                            ? ` · Assigned to ${item.assignedOfficerName}`
                            : ""}
                        </span>

                        {item.remarks && (
                          <p className="verification-timeline__remarks">
                            {item.remarks}
                          </p>
                        )}
                      </li>
                    ))}
                </ol>
              ) : (
                <p className="verification-hint">
                  No review actions recorded yet.
                </p>
              )}
            </section>
          </div>

          {/* Action Warnings */}
          {formValidationWarning && (
            <div className="verification-alert verification-alert--error" role="alert">
              <AlertCircle size={18} />
              <span>{formValidationWarning}</span>
            </div>
          )}

          {/* DECISION FORM */}
          {canDecide ? (
            <form
              className="verification-decision-panel"
              onSubmit={handleDecision}
            >
              <h4>
                <ShieldCheck size={20} aria-hidden="true" />
                Record Verification Decision ({stageLabels[selected.workflowStage]})
              </h4>

              {/* Field Officer Checklist */}
              {selected.workflowStage === "FIELD" && (
                <fieldset className="verification-checklist">
                  <legend>Mandatory Field Verification Checklist</legend>

                  <p className="verification-checklist__hint">
                    Inspect physical beneficiary identity, residence, and records.
                    All four checks must be confirmed to approve field verification.
                  </p>

                  <div className="verification-checklist__grid">
                    <label>
                      <input
                        type="checkbox"
                        checked={checklist.identityVerified}
                        onChange={(e) =>
                          setChecklist((cur) => ({
                            ...cur,
                            identityVerified: e.target.checked,
                          }))
                        }
                      />
                      <span>Beneficiary Identity Verified in Person</span>
                    </label>

                    <label>
                      <input
                        type="checkbox"
                        checked={checklist.addressVerified}
                        onChange={(e) =>
                          setChecklist((cur) => ({
                            ...cur,
                            addressVerified: e.target.checked,
                          }))
                        }
                      />
                      <span>Physical Residential Address Verified</span>
                    </label>

                    <label>
                      <input
                        type="checkbox"
                        checked={checklist.documentsVerified}
                        onChange={(e) =>
                          setChecklist((cur) => ({
                            ...cur,
                            documentsVerified: e.target.checked,
                          }))
                        }
                      />
                      <span>Supporting Documents Inspected &amp; Validated</span>
                    </label>

                    <label>
                      <input
                        type="checkbox"
                        checked={checklist.applicationDetailsVerified}
                        onChange={(e) =>
                          setChecklist((cur) => ({
                            ...cur,
                            applicationDetailsVerified: e.target.checked,
                          }))
                        }
                      />
                      <span>Application Details &amp; Claims Confirmed</span>
                    </label>
                  </div>
                </fieldset>
              )}

              <div className="verification-decision-fields">
                <label>
                  Official Decision <span className="required">*</span>

                  <select
                    required
                    value={decision}
                    onChange={(e) => {
                      setDecision(e.target.value);
                      setFormValidationWarning("");
                    }}
                  >
                    <option value="">Select a decision</option>

                    {decisions.map((opt) => (
                      <option value={opt.value} key={opt.value}>
                        {opt.label}
                      </option>
                    ))}
                  </select>
                </label>

                <label>
                  Official Remarks / Explanation{" "}
                  {["REJECTED", "NEEDS_CORRECTION"].includes(decision) ? (
                    <span className="required">
                      (Mandatory for Rejection / Corrections)
                    </span>
                  ) : (
                    <span>(Optional)</span>
                  )}

                  <textarea
                    required={["REJECTED", "NEEDS_CORRECTION"].includes(decision)}
                    value={remarks}
                    onChange={(e) => {
                      setRemarks(e.target.value);
                      setFormValidationWarning("");
                    }}
                    placeholder={
                      decision === "NEEDS_CORRECTION"
                        ? "Specify exactly what corrections or documents are required from the beneficiary…"
                        : decision === "REJECTED"
                          ? "State the official reason for rejecting this application…"
                          : "Enter optional verification remarks or recommendations…"
                    }
                    maxLength={2000}
                    rows={3}
                  />
                </label>
              </div>

              <div className="verification-decision-actions">
                <button
                  type="submit"
                  className="verification-button verification-button--primary"
                  disabled={saving || !decision}
                >
                  <ShieldCheck size={16} />
                  {saving ? "Saving Decision…" : "Record Decision"}
                </button>

                <button
                  type="button"
                  className="verification-button"
                  disabled={saving}
                  onClick={() => handleEscalation(selected)}
                  title="Reassign to another officer in this jurisdiction"
                >
                  <AlertTriangle size={15} />
                  Escalate within Level
                </button>
              </div>
            </form>
          ) : (
            <div className="verification-notice-box">
              {isAdmin && selected.workflowStage !== "FINANCE" ? (
                <div>
                  <p>
                    <strong>Administrative Oversight Note:</strong> This application
                    is currently at the{" "}
                    <strong>{stageLabels[selected.workflowStage]}</strong> stage and
                    assigned to{" "}
                    <strong>
                      {selected.assignedOfficerName || "an officer in jurisdiction"}
                    </strong>.
                  </p>

                  <p>
                    Per governance rules, stage verification must be recorded by
                    the designated stage officer. You may reassign or escalate below.
                  </p>
                </div>
              ) : activeTab === "history" ? (
                <p>
                  This application is in the verification history and its stage
                  decision has already been recorded.
                </p>
              ) : (
                <p>
                  You are viewing this application in read-only mode. Only the
                  assigned officer may record decisions.
                </p>
              )}
            </div>
          )}

          {/* Admin Reassignment Form */}
          {isAdmin && activeTab !== "history" && (
            <form
              className="verification-reassign-form"
              onSubmit={handleAssignment}
            >
              <h4>
                <Users size={18} aria-hidden="true" />
                Assign or Reassign Officer ({stageLabels[selected.workflowStage]})
              </h4>

              <div className="verification-reassign-fields">
                <select
                  required
                  value={assignmentOfficerId}
                  onChange={(e) => setAssignmentOfficerId(e.target.value)}
                >
                  <option value="">Select an active officer in jurisdiction</option>

                  {assignableOfficers.map((officer) => (
                    <option key={officer.id} value={officer.id}>
                      {officer.fullName} · {officer.email} (
                      {officer.jurisdictionDistrict || officer.jurisdictionState})
                    </option>
                  ))}
                </select>

                <button
                  type="submit"
                  className="verification-button verification-button--primary"
                  disabled={saving || !assignmentOfficerId}
                >
                  Save Assignment
                </button>

                <button
                  type="button"
                  className="verification-button"
                  disabled={saving}
                  onClick={() => handleEscalation(selected)}
                >
                  Escalate
                </button>
              </div>

              {assignableOfficers.length === 0 && (
                <p className="verification-hint">
                  No active officer is currently configured matching this stage
                  and jurisdiction ({selected.beneficiaryDetails?.district},{" "}
                  {selected.beneficiaryDetails?.state}). Add an officer in the
                  Officer Management tab.
                </p>
              )}
            </form>
          )}
        </section>
      )}

      {/* DOCUMENT PREVIEW MODAL */}
      {documentPreview && (
        <div
          className="verification-preview-backdrop"
          onClick={() => setDocumentPreview(null)}
          role="presentation"
        >
          <div
            className="verification-preview-modal"
            role="dialog"
            aria-modal="true"
            aria-labelledby="document-preview-title"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="verification-preview-modal__header">
              <h4 id="document-preview-title">{documentPreview.fileName}</h4>

              <button
                type="button"
                className="verification-button"
                onClick={() => setDocumentPreview(null)}
              >
                <X size={16} /> Close
              </button>
            </div>

            <div className="verification-preview-modal__body">
              {documentPreview.contentType?.startsWith("image/") ? (
                <img
                  src={documentPreview.url}
                  alt={documentPreview.fileName}
                />
              ) : (
                <iframe
                  src={documentPreview.url}
                  title={documentPreview.fileName}
                />
              )}
            </div>
          </div>
        </div>
      )}

      {/* ADMIN OFFICERS MANAGEMENT TAB */}
      {isAdmin && activeTab === "officers" && (
        <section className="verification-admin-panel">
          <h3>Officer Account Management</h3>
          <p>
            Create officer accounts and assign their review level and geographic
            jurisdiction.
          </p>

          <form
            className="verification-form-grid"
            onSubmit={handleOfficerCreate}
          >
            <label>
              Full Name <span className="required">*</span>
              <input
                required
                value={officerForm.fullName}
                onChange={(e) =>
                  setOfficerForm({
                    ...officerForm,
                    fullName: e.target.value,
                  })
                }
                placeholder="e.g. Rajesh Kumar"
              />
            </label>

            <label>
              Email <span className="required">*</span>
              <input
                type="email"
                required
                value={officerForm.email}
                onChange={(e) =>
                  setOfficerForm({
                    ...officerForm,
                    email: e.target.value,
                  })
                }
                placeholder="officer@grantora.gov.in"
              />
            </label>

            <label>
              Password <span className="required">*</span>
              <input
                type="password"
                required
                minLength={8}
                value={officerForm.password}
                onChange={(e) =>
                  setOfficerForm({
                    ...officerForm,
                    password: e.target.value,
                  })
                }
                placeholder="Min 8 characters"
              />
            </label>

            <label>
              Phone
              <input
                value={officerForm.phone}
                onChange={(e) =>
                  setOfficerForm({
                    ...officerForm,
                    phone: e.target.value,
                  })
                }
                placeholder="+91..."
              />
            </label>

            <label>
              Review Level <span className="required">*</span>
              <select
                value={officerForm.officerLevel}
                onChange={(e) =>
                  setOfficerForm({
                    ...officerForm,
                    officerLevel: e.target.value,
                    jurisdictionDistrict:
                      e.target.value === "FINANCE"
                        ? ""
                        : officerForm.jurisdictionDistrict,
                  })
                }
              >
                <option value="FIELD">Field Officer</option>
                <option value="DISTRICT">District Officer</option>
                <option value="FINANCE">Finance Officer</option>
              </select>
            </label>

            <label>
              State <span className="required">*</span>
              <input
                required
                value={officerForm.jurisdictionState}
                onChange={(e) =>
                  setOfficerForm({
                    ...officerForm,
                    jurisdictionState: e.target.value,
                  })
                }
                placeholder="e.g. Karnataka"
              />
            </label>

            {officerForm.officerLevel !== "FINANCE" && (
              <label>
                District <span className="required">*</span>
                <input
                  required
                  value={officerForm.jurisdictionDistrict}
                  onChange={(e) =>
                    setOfficerForm({
                      ...officerForm,
                      jurisdictionDistrict: e.target.value,
                    })
                  }
                  placeholder="e.g. Bangalore Urban"
                />
              </label>
            )}

            <div className="verification-form-grid__actions">
              <button
                className="verification-button verification-button--primary"
                type="submit"
                disabled={saving}
              >
                <UserCheck size={16} /> Add Officer
              </button>
            </div>
          </form>

          <div className="verification-table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Name</th>
                  <th>Email</th>
                  <th>Level</th>
                  <th>Jurisdiction</th>
                  <th>Actions</th>
                </tr>
              </thead>

              <tbody>
                {officers.map((officer) => (
                  <tr key={officer.id}>
                    <td>
                      <strong>{officer.fullName}</strong>
                    </td>
                    <td>{officer.email}</td>
                    <td>
                      <span
                        className={`verification-tag verification-tag--stage verification-tag--stage-${(officer.officerLevel || "").toLowerCase()}`}
                      >
                        {stageNames[officer.officerLevel] ||
                          officer.officerLevel ||
                          "Unassigned"}
                      </span>
                    </td>
                    <td>
                      {[officer.jurisdictionDistrict, officer.jurisdictionState]
                        .filter(Boolean)
                        .join(", ") || "All"}
                    </td>
                    <td>
                      <button
                        className="verification-button"
                        type="button"
                        onClick={() =>
                          setEditingOfficer({
                            ...officer,
                            officerLevel: officer.officerLevel || "FIELD",
                          })
                        }
                      >
                        Edit Scope
                      </button>
                    </td>
                  </tr>
                ))}

                {officers.length === 0 && (
                  <tr>
                    <td colSpan="5">
                      No verification officers configured yet.
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>

          {editingOfficer && (
            <form
              className="verification-form-grid verification-form--edit"
              onSubmit={handleOfficerUpdate}
            >
              <h4>Edit Scope for {editingOfficer.fullName}</h4>

              <label>
                Review Level
                <select
                  value={editingOfficer.officerLevel}
                  onChange={(e) =>
                    setEditingOfficer({
                      ...editingOfficer,
                      officerLevel: e.target.value,
                      jurisdictionDistrict:
                        e.target.value === "FINANCE"
                          ? ""
                          : editingOfficer.jurisdictionDistrict,
                    })
                  }
                >
                  <option value="FIELD">Field Officer</option>
                  <option value="DISTRICT">District Officer</option>
                  <option value="FINANCE">Finance Officer</option>
                </select>
              </label>

              <label>
                State
                <input
                  required
                  value={editingOfficer.jurisdictionState || ""}
                  onChange={(e) =>
                    setEditingOfficer({
                      ...editingOfficer,
                      jurisdictionState: e.target.value,
                    })
                  }
                />
              </label>

              {editingOfficer.officerLevel !== "FINANCE" && (
                <label>
                  District
                  <input
                    required
                    value={editingOfficer.jurisdictionDistrict || ""}
                    onChange={(e) =>
                      setEditingOfficer({
                        ...editingOfficer,
                        jurisdictionDistrict: e.target.value,
                      })
                    }
                  />
                </label>
              )}

              <div className="verification-form-grid__actions">
                <button
                  className="verification-button verification-button--primary"
                  type="submit"
                  disabled={saving}
                >
                  Save Changes
                </button>

                <button
                  className="verification-button"
                  type="button"
                  onClick={() => setEditingOfficer(null)}
                >
                  Cancel
                </button>
              </div>
            </form>
          )}
        </section>
      )}

      {/* ADMIN ROUTING RULES TAB */}
      {isAdmin && activeTab === "rules" && (
        <section className="verification-admin-panel">
          <h3>Score, Grant Amount, and Approval Routing Rules</h3>

          <p>
            Every application follows Field verification → District review →
            Finance final approval. Configure score and grant-amount ranges to
            determine queue priority.
          </p>

          <form
            className="verification-form-grid"
            onSubmit={handleRuleCreate}
          >
            <label>
              Minimum Score (0–100) <span className="required">*</span>
              <input
                type="number"
                min="0"
                max="100"
                step="0.01"
                required
                value={ruleForm.minimumScore}
                onChange={(e) =>
                  setRuleForm({
                    ...ruleForm,
                    minimumScore: e.target.value,
                  })
                }
              />
            </label>

            <label>
              Maximum Score (0–100) <span className="required">*</span>
              <input
                type="number"
                min="0"
                max="100"
                step="0.01"
                required
                value={ruleForm.maximumScore}
                onChange={(e) =>
                  setRuleForm({
                    ...ruleForm,
                    maximumScore: e.target.value,
                  })
                }
              />
            </label>

            <label>
              Minimum Grant Amount (₹) <span className="required">*</span>
              <input
                type="number"
                min="0"
                step="0.01"
                required
                value={ruleForm.minimumAmount}
                onChange={(e) =>
                  setRuleForm({
                    ...ruleForm,
                    minimumAmount: e.target.value,
                  })
                }
              />
            </label>

            <label>
              Maximum Grant Amount (₹ optional)
              <input
                type="number"
                min="0"
                step="0.01"
                value={ruleForm.maximumAmount}
                onChange={(e) =>
                  setRuleForm({
                    ...ruleForm,
                    maximumAmount: e.target.value,
                  })
                }
                placeholder="Leave blank for no upper limit"
              />
            </label>

            <label>
              Queue Priority <span className="required">*</span>
              <select
                value={ruleForm.priority}
                onChange={(e) =>
                  setRuleForm({
                    ...ruleForm,
                    priority: e.target.value,
                  })
                }
              >
                <option value="NORMAL">Normal</option>
                <option value="PRIORITY">Priority</option>
                <option value="URGENT">Urgent</option>
              </select>
            </label>

            <div className="verification-form-grid__actions">
              <button
                className="verification-button verification-button--primary"
                type="submit"
                disabled={saving}
              >
                {editingRule ? "Update Routing Rule" : "Add Routing Rule"}
              </button>

              {editingRule && (
                <button
                  className="verification-button"
                  type="button"
                  onClick={() => {
                    setEditingRule(null);
                    setRuleForm(emptyRule);
                  }}
                >
                  Cancel
                </button>
              )}
            </div>
          </form>

          <div className="verification-table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Score Range</th>
                  <th>Grant Amount Range</th>
                  <th>Priority</th>
                  <th>Actions</th>
                </tr>
              </thead>

              <tbody>
                {rules.map((rule) => (
                  <tr key={rule.id}>
                    <td>
                      {rule.minimumScore} – {rule.maximumScore}%
                    </td>

                    <td>
                      {formatAmount(rule.minimumAmount)} –{" "}
                      {rule.maximumAmount == null
                        ? "No upper limit"
                        : formatAmount(rule.maximumAmount)}
                    </td>

                    <td>
                      <span
                        className={`verification-tag verification-tag--${(rule.priority || "NORMAL").toLowerCase()}`}
                      >
                        {rule.priority}
                      </span>
                    </td>

                    <td>
                      <button
                        className="verification-button"
                        type="button"
                        onClick={() => {
                          setEditingRule(rule);
                          setRuleForm({
                            minimumScore: String(rule.minimumScore),
                            maximumScore: String(rule.maximumScore),
                            minimumAmount: String(rule.minimumAmount),
                            maximumAmount:
                              rule.maximumAmount == null
                                ? ""
                                : String(rule.maximumAmount),
                            priority: rule.priority,
                          });
                        }}
                      >
                        Edit
                      </button>{" "}

                      <button
                        className="verification-button"
                        type="button"
                        disabled={saving}
                        onClick={() => handleRuleDelete(rule.id)}
                      >
                        Delete
                      </button>
                    </td>
                  </tr>
                ))}

                {rules.length === 0 && (
                  <tr>
                    <td colSpan="4">
                      No custom routing rules configured. Applications default
                      to NORMAL priority and still follow all three review stages.
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </section>
      )}
    </div>
  );
}

export default VerificationWorkflow;
