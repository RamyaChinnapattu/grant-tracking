import { useEffect, useState, useMemo } from "react";
import {
  AlertCircle,
  AlertTriangle,
  ArrowRight,
  BarChart3,
  Building2,
  Calendar,
  CalendarCheck,
  CheckCircle2,
  CircleDollarSign,
  Clock,
  Download,
  Eye,
  FileCheck,
  FileText,
  Filter,
  Layers,
  MapPin,
  Plus,
  RefreshCw,
  Search,
  ShieldAlert,
  ShieldCheck,
  Sliders,
  Trash2,
  Upload,
  UserCheck,
  X,
  XCircle,
} from "lucide-react";
import {
  createDisbursementPlan,
  getDisbursementPlans,
  getEligibleApplications,
  getAllMilestones,
  updateMilestoneProgress,
  getAllCompliances,
  submitCompliance,
  verifyCompliance,
  downloadComplianceDocument,
  disburseInstallment,
  deactivateDisbursementPlan,
  getFundUtilization,
  getRegionalAnalytics,
  getReminderConfig,
  updateReminderConfig,
} from "../services/disbursementService";
import "../styles/DisbursementManagement.css";

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

export default function DisbursementManagement() {
  const [activeTab, setActiveTab] = useState("overview");

  // Data states
  const [fundUtilization, setFundUtilization] = useState(null);
  const [plans, setPlans] = useState([]);
  const [eligibleApps, setEligibleApps] = useState([]);
  const [milestones, setMilestones] = useState([]);
  const [compliances, setCompliances] = useState([]);
  const [regionalData, setRegionalData] = useState([]);
  const [reminderConfig, setReminderConfig] = useState(null);

  // UI state
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState("");
  const [successMsg, setSuccessMsg] = useState("");

  // Search & Filter states
  const [planSearch, setPlanSearch] = useState("");
  const [milestoneFilter, setMilestoneFilter] = useState("ALL");
  const [complianceFilter, setComplianceFilter] = useState("ALL");
  const [installmentFilter, setInstallmentFilter] = useState("READY");
  const [regionSearch, setRegionSearch] = useState("");

  // Modal states
  const [showCreatePlanModal, setShowCreatePlanModal] = useState(false);
  const [showPlanDetailsModal, setShowPlanDetailsModal] = useState(null);
  const [showProgressModal, setShowProgressModal] = useState(null);
  const [showSubmitComplianceModal, setShowSubmitComplianceModal] = useState(null);
  const [showVerifyComplianceModal, setShowVerifyComplianceModal] = useState(null);
  const [showDisburseModal, setShowDisburseModal] = useState(null);
  const [showReminderSettingsModal, setShowReminderSettingsModal] = useState(false);

  // Form states
  const [createPlanForm, setCreatePlanForm] = useState({
    applicationId: "",
    notes: "",
    installments: [
      { installmentNumber: 1, title: "Stage 1 - Initial Advance", percentage: 40, amount: "", plannedReleaseDate: "" },
      { installmentNumber: 2, title: "Stage 2 - Mid Milestone", percentage: 30, amount: "", plannedReleaseDate: "" },
      { installmentNumber: 3, title: "Stage 3 - Final Completion", percentage: 30, amount: "", plannedReleaseDate: "" },
    ],
  });

  const [progressForm, setProgressForm] = useState({
    progressPercentage: 0,
    status: "PLANNED",
    remarks: "",
  });

  const [complianceSubmitForm, setComplianceSubmitForm] = useState({
    requirementTitle: "",
    description: "",
    submissionNotes: "",
    file: null,
  });

  const [complianceVerifyForm, setComplianceVerifyForm] = useState({
    approved: true,
    remarks: "",
  });

  const [disburseForm, setDisburseForm] = useState({
    paymentReference: "",
    paymentMethod: "DIRECT_BENEFIT_TRANSFER",
    remarks: "",
  });

  const [reminderConfigForm, setReminderConfigForm] = useState({
    upcomingDaysBefore: 3,
    autoFlagOverdue: true,
    enabled: true,
  });

  const loadAllData = async (isManual = false) => {
    if (isManual) setRefreshing(true);
    else setLoading(true);
    setError("");

    try {
      const [
        utilRes,
        plansRes,
        eligibleRes,
        milestonesRes,
        compliancesRes,
        regionalRes,
        reminderRes,
      ] = await Promise.all([
        getFundUtilization().catch(() => ({ data: null })),
        getDisbursementPlans(false).catch(() => ({ data: [] })),
        getEligibleApplications().catch(() => ({ data: [] })),
        getAllMilestones().catch(() => ({ data: [] })),
        getAllCompliances().catch(() => ({ data: [] })),
        getRegionalAnalytics().catch(() => ({ data: [] })),
        getReminderConfig().catch(() => ({ data: null })),
      ]);

      setFundUtilization(utilRes.data);
      setPlans(plansRes.data || []);
      setEligibleApps(eligibleRes.data || []);
      setMilestones(milestonesRes.data || []);
      setCompliances(compliancesRes.data || []);
      setRegionalData(regionalRes.data || []);
      if (reminderRes.data) {
        setReminderConfig(reminderRes.data);
        setReminderConfigForm({
          upcomingDaysBefore: reminderRes.data.upcomingDaysBefore,
          autoFlagOverdue: reminderRes.data.autoFlagOverdue,
          enabled: reminderRes.data.enabled,
        });
      }
    } catch (err) {
      console.error("Disbursement data fetch error:", err);
      setError("Failed to load disbursement and compliance records.");
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  };

  useEffect(() => {
    loadAllData();
  }, []);

  const showToast = (msg) => {
    setSuccessMsg(msg);
    setTimeout(() => setSuccessMsg(""), 5000);
  };

  // Selected app for plan creation
  const selectedEligibleApp = useMemo(() => {
    if (!createPlanForm.applicationId) return null;
    return eligibleApps.find((a) => a.id === Number(createPlanForm.applicationId)) || null;
  }, [eligibleApps, createPlanForm.applicationId]);

  // Recalculate amounts when app or percentages change
  const handleAppSelectChange = (appId) => {
    const app = eligibleApps.find((a) => a.id === Number(appId));
    if (!app) {
      setCreatePlanForm((prev) => ({ ...prev, applicationId: "", installments: [] }));
      return;
    }
    const approvedAmt = Number(app.requestedAmount) || 0;
    const installments = [
      {
        installmentNumber: 1,
        title: "Stage 1 - Initial Advance",
        calculationType: "PERCENTAGE",
        percentage: 40,
        amount: Math.round(approvedAmt * 0.4),
        plannedReleaseDate: new Date().toISOString().split("T")[0],
        condition: "",
      },
      {
        installmentNumber: 2,
        title: "Stage 2 - Mid Construction / Inspection",
        calculationType: "PERCENTAGE",
        percentage: 30,
        amount: Math.round(approvedAmt * 0.3),
        plannedReleaseDate: "",
        condition: "",
      },
      {
        installmentNumber: 3,
        title: "Stage 3 - Final Commissioning & Handover",
        calculationType: "PERCENTAGE",
        percentage: 30,
        amount: approvedAmt - Math.round(approvedAmt * 0.4) - Math.round(approvedAmt * 0.3),
        plannedReleaseDate: "",
        condition: "",
      },
    ];

    setCreatePlanForm((prev) => ({
      ...prev,
      applicationId: appId,
      installments,
    }));
  };

  const handleInstallmentRowChange = (index, field, value) => {
    setCreatePlanForm((prev) => {
      const nextInst = [...prev.installments];
      nextInst[index] = { ...nextInst[index], [field]: value };

      if (field === "calculationType") {
        nextInst[index].percentage = value === "PERCENTAGE" ? (Number(nextInst[index].percentage) || 0) : "";
        if (value === "PERCENTAGE" && selectedEligibleApp) nextInst[index].amount = Math.round((Number(selectedEligibleApp.requestedAmount) * (Number(nextInst[index].percentage) || 0)) / 100);
      }
      if (field === "percentage" && selectedEligibleApp && nextInst[index].calculationType === "PERCENTAGE") {
        const approvedAmt = Number(selectedEligibleApp.requestedAmount) || 0;
        const pct = Number(value) || 0;
        nextInst[index].amount = Math.round((approvedAmt * pct) / 100);
      }
      return { ...prev, installments: nextInst };
    });
  };

  const addInstallmentStage = () => {
    setCreatePlanForm((prev) => ({
      ...prev,
      installments: [
        ...prev.installments,
        {
          installmentNumber: prev.installments.length + 1,
          title: `Stage ${prev.installments.length + 1} Milestone`,
          calculationType: "FIXED",
          percentage: 0,
          amount: 0,
          plannedReleaseDate: "",
          condition: "",
        },
      ],
    }));
  };

  const removeInstallmentStage = (index) => {
    setCreatePlanForm((prev) => ({
      ...prev,
      installments: prev.installments
        .filter((_, i) => i !== index)
        .map((inst, i) => ({ ...inst, installmentNumber: i + 1 })),
    }));
  };

  const totalScheduledSum = useMemo(() => {
    return createPlanForm.installments.reduce(
      (sum, inst) => sum + (Number(inst.amount) || 0),
      0
    );
  }, [createPlanForm.installments]);

  const handleCreatePlanSubmit = async (e) => {
    e.preventDefault();
    if (!createPlanForm.applicationId) {
      setError("Please select an approved application.");
      return;
    }
    const approvedAmt = Number(selectedEligibleApp?.requestedAmount) || 0;
    if (totalScheduledSum !== approvedAmt) {
      setError(`Installments must total the approved grant (${formatCurrency(approvedAmt)}).`);
      return;
    }

    try {
      await createDisbursementPlan({
        applicationId: Number(createPlanForm.applicationId),
        notes: createPlanForm.notes,
        installments: createPlanForm.installments.map((inst) => ({
          installmentNumber: inst.installmentNumber,
          title: inst.title,
          percentage: inst.calculationType === "PERCENTAGE" ? Number(inst.percentage) : null,
          amount: Number(inst.amount),
          plannedReleaseDate: inst.plannedReleaseDate || null,
          milestones: [{ title: inst.title, description: inst.condition, dueDate: inst.plannedReleaseDate || null, requirementTitles: inst.condition ? [inst.condition] : [] }],
        })),
      });

      showToast("Disbursement plan configured successfully.");
      setShowCreatePlanModal(false);
      loadAllData(true);
    } catch (err) {
      setError(err.response?.data?.message || "Failed to create disbursement plan.");
    }
  };

  const handleDeactivatePlan = async (plan) => {
    if (!window.confirm(`Deactivate the plan for application #${plan.applicationId}?`)) return;
    try {
      await deactivateDisbursementPlan(plan.id);
      showToast("Disbursement plan deactivated.");
      loadAllData(true);
    } catch (err) {
      setError(err.response?.data?.message || "Could not deactivate this plan.");
    }
  };

  // Update milestone progress submit
  const handleProgressSubmit = async (e) => {
    e.preventDefault();
    if (!showProgressModal) return;

    try {
      await updateMilestoneProgress(showProgressModal.id, {
        progressPercentage: Number(progressForm.progressPercentage),
        status: progressForm.status,
        remarks: progressForm.remarks,
      });

      showToast("Milestone progress updated successfully.");
      setShowProgressModal(null);
      loadAllData(true);
    } catch (err) {
      setError(err.response?.data?.message || "Failed to update milestone progress.");
    }
  };

  // Submit compliance evidence
  const handleComplianceEvidenceSubmit = async (e) => {
    e.preventDefault();
    if (!showSubmitComplianceModal) return;

    try {
      const formData = new FormData();
      formData.append("requirementTitle", complianceSubmitForm.requirementTitle);
      formData.append("description", complianceSubmitForm.description);
      formData.append("submissionNotes", complianceSubmitForm.submissionNotes);
      if (complianceSubmitForm.file) {
        formData.append("file", complianceSubmitForm.file);
      }

      await submitCompliance(showSubmitComplianceModal.id, formData);
      showToast("Compliance evidence submitted successfully.");
      setShowSubmitComplianceModal(null);
      setComplianceSubmitForm({ requirementTitle: "", description: "", submissionNotes: "", file: null });
      loadAllData(true);
    } catch (err) {
      setError(err.response?.data?.message || "Failed to submit compliance evidence.");
    }
  };

  // Verify compliance submit
  const handleVerifyComplianceSubmit = async (e) => {
    e.preventDefault();
    if (!showVerifyComplianceModal) return;

    try {
      await verifyCompliance(showVerifyComplianceModal.id, complianceVerifyForm);
      showToast(
        complianceVerifyForm.approved
          ? "Compliance marked as VERIFIED."
          : "Compliance marked as REJECTED. Milestone flagged as non-compliant."
      );
      setShowVerifyComplianceModal(null);
      loadAllData(true);
    } catch (err) {
      setError(err.response?.data?.message || "Failed to verify compliance.");
    }
  };

  // Disburse installment submit
  const handleDisburseSubmit = async (e) => {
    e.preventDefault();
    if (!showDisburseModal) return;

    try {
      await disburseInstallment(showDisburseModal.id, disburseForm);
      showToast(`Installment funds authorized & disbursed under Ref: ${disburseForm.paymentReference}.`);
      setShowDisburseModal(null);
      setDisburseForm({ paymentReference: "", paymentMethod: "DIRECT_BENEFIT_TRANSFER", remarks: "" });
      loadAllData(true);
    } catch (err) {
      setError(err.response?.data?.message || "Failed to disburse installment.");
    }
  };

  // Download compliance file
  const handleDownloadDoc = async (complianceId, fileName) => {
    try {
      const res = await downloadComplianceDocument(complianceId);
      const url = window.URL.createObjectURL(new Blob([res.data]));
      const link = document.createElement("a");
      link.href = url;
      link.setAttribute("download", fileName || "compliance_evidence");
      document.body.appendChild(link);
      link.click();
      link.remove();
    } catch (err) {
      setError("Unable to download compliance document.");
    }
  };

  // Filtered lists
  const filteredPlans = useMemo(() => {
    return plans.filter((p) => {
      const term = planSearch.toLowerCase();
      return (
        String(p.applicationId).includes(term) ||
        p.schemeName?.toLowerCase().includes(term) ||
        p.beneficiaryName?.toLowerCase().includes(term) ||
        p.planStatus?.toLowerCase().includes(term)
      );
    });
  }, [plans, planSearch]);

  const filteredMilestones = useMemo(() => {
    return milestones.filter((m) => {
      if (milestoneFilter === "UPCOMING") {
        return m.status === "PLANNED" || m.status === "PENDING";
      }
      if (milestoneFilter === "OVERDUE") {
        return m.status === "OVERDUE";
      }
      if (milestoneFilter === "NON_COMPLIANT") {
        return m.nonCompliant;
      }
      if (milestoneFilter === "COMPLETED") {
        return m.status === "COMPLETED";
      }
      return true;
    });
  }, [milestones, milestoneFilter]);

  const filteredCompliances = useMemo(() => {
    return compliances.filter((c) => {
      if (complianceFilter === "ALL") return true;
      return c.status === complianceFilter;
    });
  }, [compliances, complianceFilter]);

  // Installments queue aggregated from all plans
  const allInstallments = useMemo(() => {
    return plans.flatMap((p) =>
      (p.installments || []).map((i) => ({
        ...i,
        planId: p.id,
        applicationId: p.applicationId,
        schemeName: p.schemeName,
        beneficiaryName: p.beneficiaryName,
      }))
    );
  }, [plans]);

  const filteredInstallments = useMemo(() => {
    return allInstallments.filter((inst) => {
      if (installmentFilter === "READY") {
        return inst.status === "READY_FOR_DISBURSEMENT";
      }
      if (installmentFilter === "DISBURSED") {
        return inst.status === "DISBURSED";
      }
      if (installmentFilter === "PLANNED") {
        return inst.status === "PLANNED" || inst.status === "ON_HOLD";
      }
      return true;
    });
  }, [allInstallments, installmentFilter]);

  const filteredRegions = useMemo(() => {
    return regionalData.filter((r) => {
      const term = regionSearch.toLowerCase();
      return (
        r.state?.toLowerCase().includes(term) ||
        r.district?.toLowerCase().includes(term)
      );
    });
  }, [regionalData, regionSearch]);

  return (
    <div className="disb-container">
      {/* Page Header */}
      <div className="disb-header">
        <div className="disb-header__title-group">
          <span className="disb-header__eyebrow">Financial Governance & Audit</span>
          <h1 className="disb-header__title">Grant Disbursement & Compliance</h1>
          <p className="disb-header__subtitle">
            Configure staged grant release schedules, track milestone verification, verify compliance documentation, and monitor fund utilization.
          </p>
        </div>
        <div className="disb-header__actions">
          <button
            className="disb-btn disb-btn--secondary disb-btn--sm"
            onClick={() => setShowReminderSettingsModal(true)}
            title="Configure reminder schedule"
          >
            <Sliders size={16} /> Automation Settings
          </button>
          <button
            className="disb-btn disb-btn--secondary disb-btn--sm"
            onClick={() => loadAllData(true)}
            disabled={refreshing}
          >
            <RefreshCw size={16} className={refreshing ? "spin-icon" : ""} /> Refresh
          </button>
          <button
            className="disb-btn disb-btn--primary"
            onClick={() => setShowCreatePlanModal(true)}
          >
            <Plus size={18} /> Configure Disbursement Plan
          </button>
        </div>
      </div>

      {/* Notifications / Alerts */}
      {error && (
        <div className="disb-banner disb-banner--danger">
          <AlertCircle size={20} />
          <div>{error}</div>
          <button
            onClick={() => setError("")}
            style={{ marginLeft: "auto", background: "none", border: "none", cursor: "pointer" }}
          >
            <X size={16} />
          </button>
        </div>
      )}

      {successMsg && (
        <div className="disb-banner disb-banner--success">
          <CheckCircle2 size={20} />
          <div>{successMsg}</div>
          <button
            onClick={() => setSuccessMsg("")}
            style={{ marginLeft: "auto", background: "none", border: "none", cursor: "pointer" }}
          >
            <X size={16} />
          </button>
        </div>
      )}

      {/* Navigation Tabs */}
      <div className="disb-tabs-bar">
        <button
          className={`disb-tab ${activeTab === "overview" ? "disb-tab--active" : ""}`}
          onClick={() => setActiveTab("overview")}
        >
          <BarChart3 size={17} /> Overview & Fund Utilization
        </button>
        <button
          className={`disb-tab ${activeTab === "plans" ? "disb-tab--active" : ""}`}
          onClick={() => setActiveTab("plans")}
        >
          <Layers size={17} /> Disbursement Plans
          <span className="disb-tab__count">{plans.length}</span>
        </button>
        <button
          className={`disb-tab ${activeTab === "milestones" ? "disb-tab--active" : ""}`}
          onClick={() => setActiveTab("milestones")}
        >
          <CalendarCheck size={17} /> Milestone Tracking
          <span className="disb-tab__count">{milestones.length}</span>
        </button>
        <button
          className={`disb-tab ${activeTab === "compliance" ? "disb-tab--active" : ""}`}
          onClick={() => setActiveTab("compliance")}
        >
          <ShieldCheck size={17} /> Compliance Management
          <span className="disb-tab__count">{compliances.length}</span>
        </button>
        <button
          className={`disb-tab ${activeTab === "release" ? "disb-tab--active" : ""}`}
          onClick={() => setActiveTab("release")}
        >
          <CircleDollarSign size={17} /> Disbursement Release
          <span className="disb-tab__count">
            {allInstallments.filter((i) => i.status === "READY_FOR_DISBURSEMENT").length}
          </span>
        </button>
        <button
          className={`disb-tab ${activeTab === "regional" ? "disb-tab--active" : ""}`}
          onClick={() => setActiveTab("regional")}
        >
          <MapPin size={17} /> Regional Analytics
        </button>
      </div>

      {/* TAB 1: OVERVIEW & FUND UTILIZATION */}
      {activeTab === "overview" && (
        <>
          {/* Strict financial distinction banner */}
          <div className="disb-banner disb-banner--info">
            <ShieldAlert size={22} style={{ flexShrink: 0 }} />
            <div>
              <strong>Strict Financial Governance:</strong> An approved grant application does not represent an immediate or automatic payment. All grant disbursements are staged into scheduled installments and released <em>only</em> upon documented verification of milestone compliance and authorized payment confirmation.
            </div>
          </div>

          {/* Key Financial KPIs */}
          <div className="disb-stats-grid">
            <div className="disb-stat-card disb-stat-card--allocated">
              <div className="disb-stat-card__top">
                <span className="disb-stat-card__label">Total Allocated Funds</span>
                <div className="disb-stat-card__icon-wrap"><Building2 size={20} /></div>
              </div>
              <div className="disb-stat-card__value">
                {formatCurrency(fundUtilization?.totalAllocatedFunds)}
              </div>
              <span className="disb-stat-card__desc">Budget allocated across government schemes</span>
            </div>

            <div className="disb-stat-card disb-stat-card--approved">
              <div className="disb-stat-card__top">
                <span className="disb-stat-card__label">Total Approved Grants</span>
                <div className="disb-stat-card__icon-wrap"><CheckCircle2 size={20} /></div>
              </div>
              <div className="disb-stat-card__value">
                {formatCurrency(fundUtilization?.totalApprovedAmount)}
              </div>
              <span className="disb-stat-card__desc">Applications approved by review committees</span>
            </div>

            <div className="disb-stat-card disb-stat-card--scheduled">
              <div className="disb-stat-card__top">
                <span className="disb-stat-card__label">Scheduled for Disbursement</span>
                <div className="disb-stat-card__icon-wrap"><Clock size={20} /></div>
              </div>
              <div className="disb-stat-card__value">
                {formatCurrency(fundUtilization?.totalScheduledAmount)}
              </div>
              <span className="disb-stat-card__desc">Staged across active disbursement plans</span>
            </div>

            <div className="disb-stat-card disb-stat-card--disbursed">
              <div className="disb-stat-card__top">
                <span className="disb-stat-card__label">Actually Disbursed</span>
                <div className="disb-stat-card__icon-wrap"><CircleDollarSign size={20} /></div>
              </div>
              <div className="disb-stat-card__value">
                {formatCurrency(fundUtilization?.totalDisbursedAmount)}
              </div>
              <span className="disb-stat-card__desc">Verified releases with confirmed UTR/PFMS</span>
            </div>

            <div className="disb-stat-card disb-stat-card--remaining">
              <div className="disb-stat-card__top">
                <span className="disb-stat-card__label">Remaining Approved Funds</span>
                <div className="disb-stat-card__icon-wrap"><Layers size={20} /></div>
              </div>
              <div className="disb-stat-card__value">
                {formatCurrency(fundUtilization?.remainingApprovedAmount)}
              </div>
              <span className="disb-stat-card__desc">Pending compliance verification release</span>
            </div>
          </div>

          {/* Scheme-wise Fund Utilization */}
          <div className="disb-card">
            <div className="disb-card__header">
              <div>
                <h2 className="disb-card__title">
                  <Building2 size={20} /> Scheme-wise Fund Utilization
                </h2>
                <span className="disb-card__subtitle">
                  Detailed distribution of allocated scheme budgets, approved grants, scheduled installments, and actual disbursements.
                </span>
              </div>
            </div>

            <div className="disb-table-wrap">
              <table className="disb-table">
                <thead>
                  <tr>
                    <th>Scheme Name</th>
                    <th>Department</th>
                    <th>Allocated Budget</th>
                    <th>Approved Grants</th>
                    <th>Scheduled</th>
                    <th>Actually Disbursed</th>
                    <th>Remaining Budget</th>
                    <th>Utilization Rate</th>
                  </tr>
                </thead>
                <tbody>
                  {fundUtilization?.schemes?.length ? (
                    fundUtilization.schemes.map((s) => (
                      <tr key={s.schemeId}>
                        <td>
                          <strong>{s.schemeName}</strong>
                          <div style={{ fontSize: "0.76rem", color: "var(--color-text-muted)" }}>
                            {s.activePlansCount} active plans
                          </div>
                        </td>
                        <td>{s.department || "General"}</td>
                        <td>{formatCurrency(s.allocatedFunds)}</td>
                        <td>{formatCurrency(s.approvedAmount)}</td>
                        <td>{formatCurrency(s.scheduledAmount)}</td>
                        <td>
                          <span style={{ fontWeight: 700, color: "#059669" }}>
                            {formatCurrency(s.disbursedAmount)}
                          </span>
                        </td>
                        <td>{formatCurrency(s.remainingAmount)}</td>
                        <td>
                          <div className="disb-progress-wrap">
                            <div className="disb-progress-bar">
                              <div
                                className="disb-progress-fill disb-progress-fill--success"
                                style={{ width: `${Math.min(100, Number(s.utilizationPercentage) || 0)}%` }}
                              />
                            </div>
                            <span className="disb-progress-text">{s.utilizationPercentage || 0}%</span>
                          </div>
                        </td>
                      </tr>
                    ))
                  ) : (
                    <tr>
                      <td colSpan="8" style={{ textAlign: "center", padding: "30px" }}>
                        No scheme fund utilization records available.
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
          </div>

          {/* Installment Status Breakdown */}
          <div className="disb-card">
            <div className="disb-card__header">
              <div>
                <h2 className="disb-card__title">
                  <Layers size={20} /> Installment-wise Payment Breakdown
                </h2>
                <span className="disb-card__subtitle">
                  Current distribution of staged installment lifecycle statuses.
                </span>
              </div>
            </div>

            <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(200px, 1fr))", gap: "16px" }}>
              {(fundUtilization?.installmentStatuses || []).map((item) => (
                <div
                  key={item.status}
                  style={{
                    padding: "16px",
                    borderRadius: "10px",
                    border: "1px solid var(--color-outline-variant)",
                    background: "var(--color-surface-low)",
                  }}
                >
                  <div style={{ fontSize: "0.78rem", fontWeight: 700, textTransform: "uppercase", color: "var(--color-text-muted)" }}>
                    {item.status.replace(/_/g, " ")}
                  </div>
                  <div style={{ fontSize: "1.4rem", fontWeight: 800, margin: "6px 0" }}>
                    {formatCurrency(item.totalAmount)}
                  </div>
                  <div style={{ fontSize: "0.82rem", color: "var(--color-text-muted)" }}>
                    {item.count} installment stages
                  </div>
                </div>
              ))}
            </div>
          </div>
        </>
      )}

      {/* TAB 2: DISBURSEMENT PLANS */}
      {activeTab === "plans" && (
        <div className="disb-card">
          <div className="disb-card__header">
            <div>
              <h2 className="disb-card__title">
                <Layers size={20} /> Active Disbursement Plans
              </h2>
              <span className="disb-card__subtitle">
                Approved applications configured with structured, multi-stage installment release schedules.
              </span>
            </div>
            <div className="disb-toolbar">
              <div className="disb-toolbar__search">
                <Search size={16} className="disb-toolbar__search-icon" />
                <input
                  type="text"
                  className="disb-toolbar__input"
                  placeholder="Search by app ID, scheme, beneficiary..."
                  value={planSearch}
                  onChange={(e) => setPlanSearch(e.target.value)}
                />
              </div>
              <button
                className="disb-btn disb-btn--primary disb-btn--sm"
                onClick={() => setShowCreatePlanModal(true)}
              >
                <Plus size={16} /> New Plan
              </button>
            </div>
          </div>

          <div className="disb-table-wrap">
            <table className="disb-table">
              <thead>
                <tr>
                  <th>App ID</th>
                  <th>Beneficiary</th>
                  <th>Scheme</th>
                  <th>Approved Grant</th>
                  <th>Actually Disbursed</th>
                  <th>Stages</th>
                  <th>Status</th>
                  <th>Created</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {filteredPlans.length ? (
                  filteredPlans.map((plan) => (
                    <tr key={plan.id}>
                      <td>
                        <strong>#{plan.applicationId}</strong>
                      </td>
                      <td>
                        <strong>{plan.beneficiaryName}</strong>
                        <div style={{ fontSize: "0.76rem", color: "var(--color-text-muted)" }}>
                          {plan.beneficiaryDistrict ? `${plan.beneficiaryDistrict}, ` : ""}{plan.beneficiaryState || ""}
                        </div>
                      </td>
                      <td>{plan.schemeName}</td>
                      <td><strong>{formatCurrency(plan.totalApprovedAmount)}</strong></td>
                      <td>
                        <span style={{ fontWeight: 700, color: "#059669" }}>
                          {formatCurrency(plan.totalDisbursedAmount)}
                        </span>
                      </td>
                      <td>
                        <span className="disb-badge disb-badge--info">
                          {plan.installments?.length || 0} Stages
                        </span>
                      </td>
                      <td>
                        <span
                          className={`disb-badge ${
                            plan.planStatus === "COMPLETED"
                              ? "disb-badge--success"
                              : plan.planStatus === "ACTIVE"
                              ? "disb-badge--info"
                              : "disb-badge--neutral"
                          }`}
                        >
                          {plan.planStatus}
                        </span>
                      </td>
                      <td>{formatDate(plan.createdAt)}</td>
                      <td>
                        <button
                          className="disb-btn disb-btn--secondary disb-btn--sm"
                          onClick={() => setShowPlanDetailsModal(plan)}
                        >
                          <Eye size={15} /> View Schedule
                        </button>
                        {plan.planStatus === "ACTIVE" && !(plan.installments || []).some((i) => i.status === "DISBURSED") && (
                          <button className="disb-btn disb-btn--secondary disb-btn--sm" onClick={() => handleDeactivatePlan(plan)} style={{ marginTop: 6 }}>Deactivate</button>
                        )}
                      </td>
                    </tr>
                  ))
                ) : (
                  <tr>
                    <td colSpan="9" style={{ textAlign: "center", padding: "36px" }}>
                      No disbursement plans found matching your criteria.
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* TAB 3: MILESTONE TRACKING */}
      {activeTab === "milestones" && (
        <div className="disb-card">
          <div className="disb-card__header">
            <div>
              <h2 className="disb-card__title">
                <CalendarCheck size={20} /> Milestone Scheduling & Tracking
              </h2>
              <span className="disb-card__subtitle">
                Track installment milestones, completion percentages, upcoming due dates, and non-compliance flags.
              </span>
            </div>
            <div className="disb-toolbar">
              <select
                className="disb-toolbar__select"
                value={milestoneFilter}
                onChange={(e) => setMilestoneFilter(e.target.value)}
              >
                <option value="ALL">All Milestones ({milestones.length})</option>
                <option value="UPCOMING">Upcoming / Planned</option>
                <option value="COMPLETED">Completed</option>
                <option value="OVERDUE">Overdue Milestones</option>
                <option value="NON_COMPLIANT">Flagged Non-Compliant</option>
              </select>
            </div>
          </div>

          <div className="disb-table-wrap">
            <table className="disb-table">
              <thead>
                <tr>
                  <th>Milestone Title</th>
                  <th>App & Stage</th>
                  <th>Beneficiary</th>
                  <th>Due Date</th>
                  <th>Amount</th>
                  <th>Progress</th>
                  <th>Status</th>
                  <th>Compliance Flag</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {filteredMilestones.length ? (
                  filteredMilestones.map((m) => (
                    <tr key={m.id}>
                      <td>
                        <strong>{m.title}</strong>
                        {m.description && (
                          <div style={{ fontSize: "0.78rem", color: "var(--color-text-muted)" }}>
                            {m.description}
                          </div>
                        )}
                      </td>
                      <td>
                        <span>App #{m.applicationId}</span>
                        <div style={{ fontSize: "0.76rem", color: "var(--color-text-muted)" }}>
                          Stage {m.installmentNumber}: {m.installmentTitle}
                        </div>
                      </td>
                      <td>{m.beneficiaryName}</td>
                      <td>
                        <strong>{formatDate(m.dueDate)}</strong>
                        {m.status === "OVERDUE" && (
                          <div style={{ fontSize: "0.72rem", color: "#dc2626", fontWeight: 700 }}>
                            OVERDUE
                          </div>
                        )}
                      </td>
                      <td>{formatCurrency(m.amount)}</td>
                      <td style={{ minWidth: "130px" }}>
                        <div className="disb-progress-wrap">
                          <div className="disb-progress-bar">
                            <div
                              className={`disb-progress-fill ${
                                m.progressPercentage >= 100
                                  ? "disb-progress-fill--success"
                                  : m.nonCompliant
                                  ? "disb-progress-fill--danger"
                                  : ""
                              }`}
                              style={{ width: `${m.progressPercentage || 0}%` }}
                            />
                          </div>
                          <span className="disb-progress-text">{m.progressPercentage || 0}%</span>
                        </div>
                      </td>
                      <td>
                        <span
                          className={`disb-badge ${
                            m.status === "COMPLETED"
                              ? "disb-badge--success"
                              : m.status === "OVERDUE"
                              ? "disb-badge--danger"
                              : m.status === "PENDING"
                              ? "disb-badge--warning"
                              : "disb-badge--neutral"
                          }`}
                        >
                          {m.status}
                        </span>
                      </td>
                      <td>
                        {m.nonCompliant ? (
                          <span className="disb-badge disb-badge--danger" title={m.nonComplianceReason}>
                            <AlertTriangle size={12} /> Non-Compliant
                          </span>
                        ) : (
                          <span className="disb-badge disb-badge--success">
                            <ShieldCheck size={12} /> Compliant
                          </span>
                        )}
                      </td>
                      <td>
                        <div style={{ display: "flex", gap: "6px" }}>
                          <button
                            className="disb-btn disb-btn--secondary disb-btn--sm"
                            onClick={() => {
                              setShowProgressModal(m);
                              setProgressForm({
                                progressPercentage: m.progressPercentage || 0,
                                status: m.status,
                                remarks: "",
                              });
                            }}
                            title="Update Progress"
                          >
                            Update
                          </button>
                          <button
                            className="disb-btn disb-btn--secondary disb-btn--sm"
                            onClick={() => {
                              setShowSubmitComplianceModal(m);
                              setComplianceSubmitForm({
                                requirementTitle: `Compliance - ${m.title}`,
                                description: "",
                                submissionNotes: "",
                                file: null,
                              });
                            }}
                            title="Submit Compliance Evidence"
                          >
                            <Upload size={14} /> Evidence
                          </button>
                        </div>
                      </td>
                    </tr>
                  ))
                ) : (
                  <tr>
                    <td colSpan="9" style={{ textAlign: "center", padding: "36px" }}>
                      No milestones found for selected filter.
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* TAB 4: COMPLIANCE MANAGEMENT */}
      {activeTab === "compliance" && (
        <div className="disb-card">
          <div className="disb-card__header">
            <div>
              <h2 className="disb-card__title">
                <ShieldCheck size={20} /> Compliance Tracking & Verification
              </h2>
              <span className="disb-card__subtitle">
                Review submitted physical/financial verification proof and approve or reject compliance requirements.
              </span>
            </div>
            <div className="disb-toolbar">
              <select
                className="disb-toolbar__select"
                value={complianceFilter}
                onChange={(e) => setComplianceFilter(e.target.value)}
              >
                <option value="ALL">All Statuses ({compliances.length})</option>
                <option value="SUBMITTED">Submitted for Review</option>
                <option value="PENDING">Pending Submission</option>
                <option value="VERIFIED">Verified</option>
                <option value="REJECTED">Rejected</option>
              </select>
            </div>
          </div>

          <div className="disb-table-wrap">
            <table className="disb-table">
              <thead>
                <tr>
                  <th>Requirement</th>
                  <th>Milestone</th>
                  <th>Document Evidence</th>
                  <th>Submission Notes</th>
                  <th>Status</th>
                  <th>Verification Info</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {filteredCompliances.length ? (
                  filteredCompliances.map((c) => (
                    <tr key={c.id}>
                      <td>
                        <strong>{c.requirementTitle}</strong>
                        {c.description && (
                          <div style={{ fontSize: "0.78rem", color: "var(--color-text-muted)" }}>
                            {c.description}
                          </div>
                        )}
                      </td>
                      <td>
                        <span>{c.milestoneTitle || `Milestone #${c.milestoneId}`}</span>
                      </td>
                      <td>
                        {c.hasDocument ? (
                          <button
                            className="disb-btn disb-btn--secondary disb-btn--sm"
                            onClick={() => handleDownloadDoc(c.id, c.documentFileName)}
                          >
                            <Download size={14} /> {c.documentFileName || "View File"}
                          </button>
                        ) : (
                          <span style={{ fontSize: "0.8rem", color: "var(--color-text-muted)" }}>
                            No file attached
                          </span>
                        )}
                      </td>
                      <td>
                        <span style={{ fontSize: "0.82rem" }}>
                          {c.submissionNotes || "—"}
                        </span>
                        {c.submittedByName && (
                          <div style={{ fontSize: "0.72rem", color: "var(--color-text-muted)" }}>
                            By {c.submittedByName} on {formatDate(c.submittedAt)}
                          </div>
                        )}
                      </td>
                      <td>
                        <span
                          className={`disb-badge ${
                            c.status === "VERIFIED"
                              ? "disb-badge--success"
                              : c.status === "REJECTED"
                              ? "disb-badge--danger"
                              : c.status === "SUBMITTED"
                              ? "disb-badge--info"
                              : "disb-badge--warning"
                          }`}
                        >
                          {c.status}
                        </span>
                      </td>
                      <td>
                        {c.verifiedByName ? (
                          <div style={{ fontSize: "0.8rem" }}>
                            <strong>{c.verifiedByName}</strong>
                            <div style={{ fontSize: "0.72rem", color: "var(--color-text-muted)" }}>
                              {formatDate(c.verifiedAt)}
                            </div>
                            {c.verificationRemarks && (
                              <div style={{ fontStyle: "italic", marginTop: "2px" }}>
                                "{c.verificationRemarks}"
                              </div>
                            )}
                          </div>
                        ) : (
                          <span style={{ fontSize: "0.78rem", color: "var(--color-text-muted)" }}>
                            Awaiting review
                          </span>
                        )}
                      </td>
                      <td>
                        <div style={{ display: "flex", gap: "6px" }}>
                          <button
                            className="disb-btn disb-btn--secondary disb-btn--sm"
                            onClick={() => {
                              setShowVerifyComplianceModal(c);
                              setComplianceVerifyForm({ approved: true, remarks: "" });
                            }}
                          >
                            <UserCheck size={14} /> Verify
                          </button>
                        </div>
                      </td>
                    </tr>
                  ))
                ) : (
                  <tr>
                    <td colSpan="7" style={{ textAlign: "center", padding: "36px" }}>
                      No compliance records found for selected filter.
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* TAB 5: DISBURSEMENT RELEASE */}
      {activeTab === "release" && (
        <div className="disb-card">
          <div className="disb-card__header">
            <div>
              <h2 className="disb-card__title">
                <CircleDollarSign size={20} /> Staged Disbursement Fund Release
              </h2>
              <span className="disb-card__subtitle">
                Authorize payment releases with verified transaction reference numbers. Duplicate releases are strictly prohibited.
              </span>
            </div>
            <div className="disb-toolbar">
              <select
                className="disb-toolbar__select"
                value={installmentFilter}
                onChange={(e) => setInstallmentFilter(e.target.value)}
              >
                <option value="READY">Ready for Disbursement</option>
                <option value="DISBURSED">Disbursed History</option>
                <option value="PLANNED">Planned / Upcoming</option>
                <option value="ALL">All Installments</option>
              </select>
            </div>
          </div>

          <div className="disb-table-wrap">
            <table className="disb-table">
              <thead>
                <tr>
                  <th>Stage #</th>
                  <th>Application & Beneficiary</th>
                  <th>Amount</th>
                  <th>Planned Release</th>
                  <th>Compliance Status</th>
                  <th>Status</th>
                  <th>Disbursement Record</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {filteredInstallments.length ? (
                  filteredInstallments.map((inst) => (
                    <tr key={inst.id}>
                      <td>
                        <strong>Stage {inst.installmentNumber}</strong>
                        <div style={{ fontSize: "0.78rem", color: "var(--color-text-muted)" }}>
                          {inst.title}
                        </div>
                      </td>
                      <td>
                        <strong>App #{inst.applicationId}</strong>
                        <div style={{ fontSize: "0.78rem", color: "var(--color-text-muted)" }}>
                          {inst.beneficiaryName} ({inst.schemeName})
                        </div>
                      </td>
                      <td>
                        <strong style={{ fontSize: "1rem" }}>{formatCurrency(inst.amount)}</strong>
                        <div style={{ fontSize: "0.72rem", color: "var(--color-text-muted)" }}>
                          {inst.percentage}% of approved
                        </div>
                      </td>
                      <td>{formatDate(inst.plannedReleaseDate)}</td>
                      <td>
                        {inst.allCompliancesVerified ? (
                          <span className="disb-badge disb-badge--success">
                            <ShieldCheck size={12} /> Verified
                          </span>
                        ) : (
                          <span className="disb-badge disb-badge--warning">
                            <AlertTriangle size={12} /> Compliance Incomplete
                          </span>
                        )}
                      </td>
                      <td>
                        <span
                          className={`disb-badge ${
                            inst.status === "DISBURSED"
                              ? "disb-badge--success"
                              : inst.status === "READY_FOR_DISBURSEMENT"
                              ? "disb-badge--info"
                              : "disb-badge--neutral"
                          }`}
                        >
                          {inst.status}
                        </span>
                      </td>
                      <td>
                        {inst.status === "DISBURSED" ? (
                          <div style={{ fontSize: "0.78rem" }}>
                            <strong>Ref: {inst.paymentReference}</strong>
                            <div style={{ color: "var(--color-text-muted)" }}>
                              {inst.paymentMethod} • {formatDate(inst.actualDisbursementDate)}
                            </div>
                            {inst.disbursedByName && <div>By {inst.disbursedByName}</div>}
                          </div>
                        ) : (
                          <span style={{ fontSize: "0.8rem", color: "var(--color-text-muted)" }}>
                            Not released
                          </span>
                        )}
                      </td>
                      <td>
                        {inst.status === "DISBURSED" ? (
                          <span className="disb-badge disb-badge--success">Disbursed</span>
                        ) : (
                          <button
                            className="disb-btn disb-btn--primary disb-btn--sm"
                            disabled={!inst.allCompliancesVerified || inst.status === "ON_HOLD"}
                            onClick={() => {
                              setShowDisburseModal(inst);
                              setDisburseForm({
                                paymentReference: `PFMS-${Date.now().toString().slice(-8)}`,
                                paymentMethod: "DIRECT_BENEFIT_TRANSFER",
                                remarks: `Authorized release for Stage ${inst.installmentNumber}`,
                              });
                            }}
                            title={
                              !inst.allCompliancesVerified
                                ? "Cannot release: All milestone compliance requirements must be VERIFIED first."
                                : "Authorize staged grant payment"
                            }
                          >
                            <CircleDollarSign size={14} /> Disburse Funds
                          </button>
                        )}
                      </td>
                    </tr>
                  ))
                ) : (
                  <tr>
                    <td colSpan="8" style={{ textAlign: "center", padding: "36px" }}>
                      No installments found for the selected status.
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* TAB 6: REGIONAL ANALYTICS */}
      {activeTab === "regional" && (
        <div className="disb-card">
          <div className="disb-card__header">
            <div>
              <h2 className="disb-card__title">
                <MapPin size={20} /> Regional Analytics & Fund Utilization
              </h2>
              <span className="disb-card__subtitle">
                State and district level distribution of grant applications, approved amounts, actual disbursements, and compliance performance.
              </span>
            </div>
            <div className="disb-toolbar">
              <div className="disb-toolbar__search">
                <Search size={16} className="disb-toolbar__search-icon" />
                <input
                  type="text"
                  className="disb-toolbar__input"
                  placeholder="Filter state or district..."
                  value={regionSearch}
                  onChange={(e) => setRegionSearch(e.target.value)}
                />
              </div>
            </div>
          </div>

          <div className="disb-table-wrap">
            <table className="disb-table">
              <thead>
                <tr>
                  <th>State</th>
                  <th>District</th>
                  <th>Total Apps</th>
                  <th>Approved Apps</th>
                  <th>Approved Amount</th>
                  <th>Actually Disbursed</th>
                  <th>Pending Disbursement</th>
                  <th>Compliance Rate</th>
                  <th>Fund Utilization</th>
                </tr>
              </thead>
              <tbody>
                {filteredRegions.length ? (
                  filteredRegions.map((r, idx) => (
                    <tr key={`${r.state}-${r.district}-${idx}`}>
                      <td><strong>{r.state}</strong></td>
                      <td>{r.district}</td>
                      <td>{r.totalApplications}</td>
                      <td>{r.approvedApplications}</td>
                      <td><strong>{formatCurrency(r.totalApprovedAmount)}</strong></td>
                      <td>
                        <span style={{ fontWeight: 700, color: "#059669" }}>
                          {formatCurrency(r.totalDisbursedAmount)}
                        </span>
                      </td>
                      <td>{formatCurrency(r.pendingDisbursementAmount)}</td>
                      <td>
                        <div className="disb-progress-wrap">
                          <div className="disb-progress-bar">
                            <div
                              className="disb-progress-fill disb-progress-fill--success"
                              style={{ width: `${r.complianceRate || 0}%` }}
                            />
                          </div>
                          <span className="disb-progress-text">{r.complianceRate || 0}%</span>
                        </div>
                      </td>
                      <td>
                        <div className="disb-progress-wrap">
                          <div className="disb-progress-bar">
                            <div
                              className="disb-progress-fill"
                              style={{ width: `${r.fundUtilizationRate || 0}%` }}
                            />
                          </div>
                          <span className="disb-progress-text">{r.fundUtilizationRate || 0}%</span>
                        </div>
                      </td>
                    </tr>
                  ))
                ) : (
                  <tr>
                    <td colSpan="9" style={{ textAlign: "center", padding: "36px" }}>
                      No regional analytics data available.
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* ================================================================= */}
      {/* MODAL 1: CONFIGURE DISBURSEMENT PLAN */}
      {/* ================================================================= */}
      {showCreatePlanModal && (
        <div className="disb-modal-overlay">
          <div className="disb-modal disb-modal--wide">
            <div className="disb-modal__header">
              <h3 className="disb-modal__title">Configure Staged Disbursement Plan</h3>
              <button
                className="disb-modal__close-btn"
                onClick={() => setShowCreatePlanModal(false)}
              >
                <X size={20} />
              </button>
            </div>
            <form onSubmit={handleCreatePlanSubmit}>
              <div className="disb-modal__body">
                <div className="disb-form-group">
                  <label className="disb-form-label">Select Approved Application *</label>
                  <select
                    className="disb-form-select"
                    value={createPlanForm.applicationId}
                    onChange={(e) => handleAppSelectChange(e.target.value)}
                    required
                  >
                    <option value="">-- Choose an Approved Application --</option>
                    {eligibleApps.map((a) => (
                      <option key={a.id} value={a.id}>
                        App #{a.id} - {a.beneficiaryName || "Beneficiary"} ({a.schemeName}) — Grant: {formatCurrency(a.requestedAmount)}
                      </option>
                    ))}
                  </select>
                  {eligibleApps.length === 0 && (
                    <span className="disb-form-help" style={{ color: "#d97706" }}>
                      No unassigned approved applications found. Applications must have APPROVED status before planning disbursement.
                    </span>
                  )}
                </div>

                {selectedEligibleApp && (
                  <div
                    style={{
                      padding: "12px 16px",
                      borderRadius: "8px",
                      background: "var(--color-surface-low)",
                      border: "1px solid var(--color-outline-variant)",
                      display: "flex",
                      justifyContent: "space-between",
                      alignItems: "center",
                    }}
                  >
                    <div>
                      <strong>Approved Grant Limit:</strong> {formatCurrency(selectedEligibleApp.requestedAmount)}
                      <div style={{ fontSize: "0.78rem", color: "var(--color-text-muted)" }}>
                        {selectedEligibleApp.schemeName} • {selectedEligibleApp.beneficiaryDetails?.state || ""}
                      </div>
                    </div>
                    <div style={{ textAlign: "right" }}>
                      <div style={{ fontSize: "0.78rem", color: "var(--color-text-muted)" }}>Scheduled Total</div>
                      <strong
                        style={{
                          fontSize: "1.1rem",
                          color:
                            totalScheduledSum !== Number(selectedEligibleApp.requestedAmount)
                              ? "#ef4444"
                              : "#10b981",
                        }}
                      >
                        {formatCurrency(totalScheduledSum)}
                      </strong>
                    </div>
                  </div>
                )}

                <div className="disb-form-group">
                  <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                    <label className="disb-form-label">Disbursement Stages / Installments *</label>
                    <button
                      type="button"
                      className="disb-btn disb-btn--secondary disb-btn--sm"
                      onClick={addInstallmentStage}
                    >
                      <Plus size={14} /> Add Stage
                    </button>
                  </div>

                  <div style={{ display: "flex", flexDirection: "column", gap: "8px", marginTop: "6px" }}>
                    {createPlanForm.installments.map((inst, idx) => (
                      <div key={idx} className="disb-stage-row">
                        <div>
                          <span style={{ fontSize: "0.75rem", fontWeight: 700 }}>Stage {inst.installmentNumber}</span>
                        </div>
                        <div>
                          <input
                            type="text"
                            className="disb-form-input"
                            placeholder="Stage Title"
                            value={inst.title}
                            onChange={(e) => handleInstallmentRowChange(idx, "title", e.target.value)}
                            required
                          />
                        </div>
                        <div>
                          <select className="disb-form-input" value={inst.calculationType || "FIXED"} onChange={(e) => handleInstallmentRowChange(idx, "calculationType", e.target.value)} aria-label={`Stage ${inst.installmentNumber} amount type`}>
                            <option value="FIXED">Fixed amount</option><option value="PERCENTAGE">Percentage</option>
                          </select>
                        </div>
                        <div>
                          <input
                            type="number"
                            className="disb-form-input"
                            placeholder="%"
                            value={inst.percentage}
                            onChange={(e) => handleInstallmentRowChange(idx, "percentage", e.target.value)}
                            min="0"
                            max="100"
                            disabled={inst.calculationType !== "PERCENTAGE"}
                          />
                        </div>
                        <div>
                          <input
                            type="number"
                            className="disb-form-input"
                            placeholder="Amount (₹)"
                            value={inst.amount}
                            onChange={(e) => handleInstallmentRowChange(idx, "amount", e.target.value)}
                            required={inst.calculationType !== "PERCENTAGE"}
                            readOnly={inst.calculationType === "PERCENTAGE"}
                            min="1"
                          />
                        </div>
                        <div>
                          <input type="text" className="disb-form-input" placeholder="Milestone or release condition" value={inst.condition || ""} onChange={(e) => handleInstallmentRowChange(idx, "condition", e.target.value)} />
                        </div>
                        <div>
                          <input
                            type="date"
                            className="disb-form-input"
                            value={inst.plannedReleaseDate || ""}
                            onChange={(e) => handleInstallmentRowChange(idx, "plannedReleaseDate", e.target.value)}
                          />
                        </div>
                        <div>
                          {createPlanForm.installments.length > 1 && (
                            <button
                              type="button"
                              onClick={() => removeInstallmentStage(idx)}
                              style={{
                                background: "none",
                                border: "none",
                                color: "#ef4444",
                                cursor: "pointer",
                              }}
                            >
                              <Trash2 size={16} />
                            </button>
                          )}
                        </div>
                      </div>
                    ))}
                  </div>
                </div>

                <div className="disb-form-group">
                  <label className="disb-form-label">Administrative Notes</label>
                  <textarea
                    className="disb-form-textarea"
                    rows="2"
                    placeholder="Optional notes or audit references..."
                    value={createPlanForm.notes}
                    onChange={(e) => setCreatePlanForm((prev) => ({ ...prev, notes: e.target.value }))}
                  />
                </div>
              </div>
              <div className="disb-modal__footer">
                <button
                  type="button"
                  className="disb-btn disb-btn--secondary"
                  onClick={() => setShowCreatePlanModal(false)}
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="disb-btn disb-btn--primary"
                  disabled={
                    !createPlanForm.applicationId ||
                    !selectedEligibleApp || totalScheduledSum !== Number(selectedEligibleApp.requestedAmount || 0)
                  }
                >
                  Save & Activate Plan
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* ================================================================= */}
      {/* MODAL 2: VIEW PLAN SCHEDULE DETAILS */}
      {/* ================================================================= */}
      {showPlanDetailsModal && (
        <div className="disb-modal-overlay">
          <div className="disb-modal disb-modal--wide">
            <div className="disb-modal__header">
              <div>
                <h3 className="disb-modal__title">
                  Disbursement Plan Details - App #{showPlanDetailsModal.applicationId}
                </h3>
                <span className="disb-card__subtitle">
                  {showPlanDetailsModal.schemeName} • {showPlanDetailsModal.beneficiaryName}
                </span>
              </div>
              <button
                className="disb-modal__close-btn"
                onClick={() => setShowPlanDetailsModal(null)}
              >
                <X size={20} />
              </button>
            </div>
            <div className="disb-modal__body">
              <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(180px, 1fr))", gap: "12px" }}>
                <div style={{ padding: "12px", background: "var(--color-surface-low)", borderRadius: "8px" }}>
                  <span style={{ fontSize: "0.75rem", color: "var(--color-text-muted)" }}>Total Approved</span>
                  <div style={{ fontSize: "1.1rem", fontWeight: 800 }}>
                    {formatCurrency(showPlanDetailsModal.totalApprovedAmount)}
                  </div>
                </div>
                <div style={{ padding: "12px", background: "var(--color-surface-low)", borderRadius: "8px" }}>
                  <span style={{ fontSize: "0.75rem", color: "var(--color-text-muted)" }}>Total Disbursed</span>
                  <div style={{ fontSize: "1.1rem", fontWeight: 800, color: "#10b981" }}>
                    {formatCurrency(showPlanDetailsModal.totalDisbursedAmount)}
                  </div>
                </div>
                <div style={{ padding: "12px", background: "var(--color-surface-low)", borderRadius: "8px" }}>
                  <span style={{ fontSize: "0.75rem", color: "var(--color-text-muted)" }}>Remaining Balance</span>
                  <div style={{ fontSize: "1.1rem", fontWeight: 800, color: "#f59e0b" }}>
                    {formatCurrency(showPlanDetailsModal.remainingAmount)}
                  </div>
                </div>
                <div style={{ padding: "12px", background: "var(--color-surface-low)", borderRadius: "8px" }}>
                  <span style={{ fontSize: "0.75rem", color: "var(--color-text-muted)" }}>Plan Status</span>
                  <div>
                    <span className="disb-badge disb-badge--info">{showPlanDetailsModal.planStatus}</span>
                  </div>
                </div>
              </div>

              <h4 style={{ margin: "12px 0 6px 0", fontSize: "0.95rem" }}>Installment Stages & Verification Schedule</h4>
              <div className="disb-table-wrap">
                <table className="disb-table">
                  <thead>
                    <tr>
                      <th>Stage #</th>
                      <th>Title</th>
                      <th>Percentage</th>
                      <th>Amount</th>
                      <th>Release Date</th>
                      <th>Status</th>
                      <th>Compliance</th>
                    </tr>
                  </thead>
                  <tbody>
                    {(showPlanDetailsModal.installments || []).map((inst) => (
                      <tr key={inst.id}>
                        <td>Stage {inst.installmentNumber}</td>
                        <td>{inst.title}</td>
                        <td>{inst.percentage}%</td>
                        <td><strong>{formatCurrency(inst.amount)}</strong></td>
                        <td>{formatDate(inst.plannedReleaseDate)}</td>
                        <td>
                          <span
                            className={`disb-badge ${
                              inst.status === "DISBURSED"
                                ? "disb-badge--success"
                                : inst.status === "READY_FOR_DISBURSEMENT"
                                ? "disb-badge--info"
                                : "disb-badge--neutral"
                            }`}
                          >
                            {inst.status}
                          </span>
                        </td>
                        <td>
                          {inst.allCompliancesVerified ? (
                            <span className="disb-badge disb-badge--success">All Verified</span>
                          ) : (
                            <span className="disb-badge disb-badge--warning">Pending</span>
                          )}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
            <div className="disb-modal__footer">
              <button
                type="button"
                className="disb-btn disb-btn--secondary"
                onClick={() => setShowPlanDetailsModal(null)}
              >
                Close
              </button>
            </div>
          </div>
        </div>
      )}

      {/* ================================================================= */}
      {/* MODAL 3: UPDATE MILESTONE PROGRESS */}
      {/* ================================================================= */}
      {showProgressModal && (
        <div className="disb-modal-overlay">
          <div className="disb-modal">
            <div className="disb-modal__header">
              <h3 className="disb-modal__title">Update Milestone Progress</h3>
              <button
                className="disb-modal__close-btn"
                onClick={() => setShowProgressModal(null)}
              >
                <X size={20} />
              </button>
            </div>
            <form onSubmit={handleProgressSubmit}>
              <div className="disb-modal__body">
                <div>
                  <strong>{showProgressModal.title}</strong>
                  <div style={{ fontSize: "0.82rem", color: "var(--color-text-muted)" }}>
                    App #{showProgressModal.applicationId} • Due: {formatDate(showProgressModal.dueDate)}
                  </div>
                </div>

                <div className="disb-form-group">
                  <label className="disb-form-label">
                    Progress Percentage: {progressForm.progressPercentage}%
                  </label>
                  <input
                    type="range"
                    min="0"
                    max="100"
                    step="5"
                    className="disb-form-input"
                    value={progressForm.progressPercentage}
                    onChange={(e) =>
                      setProgressForm((prev) => ({
                        ...prev,
                        progressPercentage: Number(e.target.value),
                        status: Number(e.target.value) >= 100 ? "COMPLETED" : Number(e.target.value) > 0 ? "PENDING" : "PLANNED",
                      }))
                    }
                  />
                </div>

                <div className="disb-form-group">
                  <label className="disb-form-label">Milestone Status</label>
                  <select
                    className="disb-form-select"
                    value={progressForm.status}
                    onChange={(e) => setProgressForm((prev) => ({ ...prev, status: e.target.value }))}
                  >
                    <option value="PLANNED">PLANNED</option>
                    <option value="PENDING">PENDING (In Progress)</option>
                    <option value="COMPLETED">COMPLETED (100% Verified)</option>
                    <option value="OVERDUE">OVERDUE</option>
                    <option value="CANCELLED">CANCELLED</option>
                  </select>
                </div>

                <div className="disb-form-group">
                  <label className="disb-form-label">Progress Update Remarks</label>
                  <textarea
                    className="disb-form-textarea"
                    rows="3"
                    placeholder="Enter observation notes, field verification status, or completion summary..."
                    value={progressForm.remarks}
                    onChange={(e) => setProgressForm((prev) => ({ ...prev, remarks: e.target.value }))}
                  />
                </div>
              </div>
              <div className="disb-modal__footer">
                <button
                  type="button"
                  className="disb-btn disb-btn--secondary"
                  onClick={() => setShowProgressModal(null)}
                >
                  Cancel
                </button>
                <button type="submit" className="disb-btn disb-btn--primary">
                  Save Progress
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* ================================================================= */}
      {/* MODAL 4: SUBMIT COMPLIANCE EVIDENCE */}
      {/* ================================================================= */}
      {showSubmitComplianceModal && (
        <div className="disb-modal-overlay">
          <div className="disb-modal">
            <div className="disb-modal__header">
              <h3 className="disb-modal__title">Submit Compliance Evidence</h3>
              <button
                className="disb-modal__close-btn"
                onClick={() => setShowSubmitComplianceModal(null)}
              >
                <X size={20} />
              </button>
            </div>
            <form onSubmit={handleComplianceEvidenceSubmit}>
              <div className="disb-modal__body">
                <div>
                  <strong>Milestone: {showSubmitComplianceModal.title}</strong>
                  <div style={{ fontSize: "0.82rem", color: "var(--color-text-muted)" }}>
                    Upload utilization certificates, vendor invoices, or physical inspection photos.
                  </div>
                </div>

                <div className="disb-form-group">
                  <label className="disb-form-label">Requirement Title *</label>
                  <input
                    type="text"
                    className="disb-form-input"
                    value={complianceSubmitForm.requirementTitle}
                    onChange={(e) => setComplianceSubmitForm((prev) => ({ ...prev, requirementTitle: e.target.value }))}
                    required
                  />
                </div>

                <div className="disb-form-group">
                  <label className="disb-form-label">Description / Requirement Scope</label>
                  <input
                    type="text"
                    className="disb-form-input"
                    placeholder="e.g. GST Invoices, GPS-tagged site photos..."
                    value={complianceSubmitForm.description}
                    onChange={(e) => setComplianceSubmitForm((prev) => ({ ...prev, description: e.target.value }))}
                  />
                </div>

                <div className="disb-form-group">
                  <label className="disb-form-label">Submission Notes / Summary</label>
                  <textarea
                    className="disb-form-textarea"
                    rows="2"
                    placeholder="Details about submitted documents or expenditure..."
                    value={complianceSubmitForm.submissionNotes}
                    onChange={(e) => setComplianceSubmitForm((prev) => ({ ...prev, submissionNotes: e.target.value }))}
                  />
                </div>

                <div className="disb-form-group">
                  <label className="disb-form-label">Upload Evidence Document (PDF, PNG, JPG, max 10MB)</label>
                  <input
                    type="file"
                    className="disb-form-input"
                    accept=".pdf,.png,.jpg,.jpeg"
                    onChange={(e) => setComplianceSubmitForm((prev) => ({ ...prev, file: e.target.files[0] }))}
                  />
                </div>
              </div>
              <div className="disb-modal__footer">
                <button
                  type="button"
                  className="disb-btn disb-btn--secondary"
                  onClick={() => setShowSubmitComplianceModal(null)}
                >
                  Cancel
                </button>
                <button type="submit" className="disb-btn disb-btn--primary">
                  Submit Evidence
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* ================================================================= */}
      {/* MODAL 5: VERIFY / REJECT COMPLIANCE */}
      {/* ================================================================= */}
      {showVerifyComplianceModal && (
        <div className="disb-modal-overlay">
          <div className="disb-modal">
            <div className="disb-modal__header">
              <h3 className="disb-modal__title">Verify Compliance Requirement</h3>
              <button
                className="disb-modal__close-btn"
                onClick={() => setShowVerifyComplianceModal(null)}
              >
                <X size={20} />
              </button>
            </div>
            <form onSubmit={handleVerifyComplianceSubmit}>
              <div className="disb-modal__body">
                <div>
                  <strong>{showVerifyComplianceModal.requirementTitle}</strong>
                  <div style={{ fontSize: "0.82rem", color: "var(--color-text-muted)" }}>
                    Milestone: {showVerifyComplianceModal.milestoneTitle}
                  </div>
                </div>

                {showVerifyComplianceModal.hasDocument && (
                  <div style={{ padding: "10px", background: "var(--color-surface-low)", borderRadius: "8px" }}>
                    <span style={{ fontSize: "0.82rem" }}>Uploaded Evidence:</span>
                    <button
                      type="button"
                      className="disb-btn disb-btn--secondary disb-btn--sm"
                      style={{ marginLeft: "10px" }}
                      onClick={() => handleDownloadDoc(showVerifyComplianceModal.id, showVerifyComplianceModal.documentFileName)}
                    >
                      <Download size={14} /> Download Document
                    </button>
                  </div>
                )}

                <div className="disb-form-group">
                  <label className="disb-form-label">Verification Decision *</label>
                  <div style={{ display: "flex", gap: "16px", marginTop: "4px" }}>
                    <label style={{ display: "flex", alignItems: "center", gap: "6px", cursor: "pointer" }}>
                      <input
                        type="radio"
                        name="verifyDecision"
                        checked={complianceVerifyForm.approved === true}
                        onChange={() => setComplianceVerifyForm((prev) => ({ ...prev, approved: true }))}
                      />
                      <span style={{ color: "#059669", fontWeight: 700 }}>Approve & Verify</span>
                    </label>
                    <label style={{ display: "flex", alignItems: "center", gap: "6px", cursor: "pointer" }}>
                      <input
                        type="radio"
                        name="verifyDecision"
                        checked={complianceVerifyForm.approved === false}
                        onChange={() => setComplianceVerifyForm((prev) => ({ ...prev, approved: false }))}
                      />
                      <span style={{ color: "#dc2626", fontWeight: 700 }}>Reject (Flags Non-Compliance)</span>
                    </label>
                  </div>
                </div>

                <div className="disb-form-group">
                  <label className="disb-form-label">Audit Remarks / Justification *</label>
                  <textarea
                    className="disb-form-textarea"
                    rows="3"
                    placeholder="Enter compliance evaluation findings or rejection reason..."
                    value={complianceVerifyForm.remarks}
                    onChange={(e) => setComplianceVerifyForm((prev) => ({ ...prev, remarks: e.target.value }))}
                    required
                  />
                  {!complianceVerifyForm.approved && (
                    <span className="disb-form-help" style={{ color: "#dc2626" }}>
                      Warning: Rejecting this compliance will flag the milestone as non-compliant and prevent disbursement of funds.
                    </span>
                  )}
                </div>
              </div>
              <div className="disb-modal__footer">
                <button
                  type="button"
                  className="disb-btn disb-btn--secondary"
                  onClick={() => setShowVerifyComplianceModal(null)}
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className={`disb-btn ${complianceVerifyForm.approved ? "disb-btn--success" : "disb-btn--danger"}`}
                >
                  {complianceVerifyForm.approved ? "Confirm Verification" : "Reject Compliance"}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* ================================================================= */}
      {/* MODAL 6: AUTHORIZE & DISBURSE FUNDS */}
      {/* ================================================================= */}
      {showDisburseModal && (
        <div className="disb-modal-overlay">
          <div className="disb-modal">
            <div className="disb-modal__header">
              <h3 className="disb-modal__title">Authorize Grant Disbursement</h3>
              <button
                className="disb-modal__close-btn"
                onClick={() => setShowDisburseModal(null)}
              >
                <X size={20} />
              </button>
            </div>
            <form onSubmit={handleDisburseSubmit}>
              <div className="disb-modal__body">
                <div className="disb-banner disb-banner--warning">
                  <ShieldAlert size={20} style={{ flexShrink: 0 }} />
                  <div>
                    <strong>Financial Safety Confirmation:</strong> Ensure that bank treasury transfer / PFMS execution has been initiated or confirmed. Recording this disbursement updates the audited financial ledger and cannot be undone.
                  </div>
                </div>

                <div style={{ padding: "14px", background: "var(--color-surface-low)", borderRadius: "8px" }}>
                  <div style={{ display: "flex", justifyContent: "space-between" }}>
                    <span>Stage:</span>
                    <strong>Stage {showDisburseModal.installmentNumber} ({showDisburseModal.title})</strong>
                  </div>
                  <div style={{ display: "flex", justifyContent: "space-between", marginTop: "4px" }}>
                    <span>Beneficiary:</span>
                    <strong>{showDisburseModal.beneficiaryName}</strong>
                  </div>
                  <div style={{ display: "flex", justifyContent: "space-between", marginTop: "4px" }}>
                    <span>Disbursement Amount:</span>
                    <strong style={{ fontSize: "1.2rem", color: "#059669" }}>
                      {formatCurrency(showDisburseModal.amount)}
                    </strong>
                  </div>
                </div>

                <div className="disb-form-group">
                  <label className="disb-form-label">Payment Transaction Reference / UTR Number *</label>
                  <input
                    type="text"
                    className="disb-form-input"
                    placeholder="e.g. PFMS2026889900 or UTR12345678"
                    value={disburseForm.paymentReference}
                    onChange={(e) => setDisburseForm((prev) => ({ ...prev, paymentReference: e.target.value }))}
                    required
                  />
                </div>

                <div className="disb-form-group">
                  <label className="disb-form-label">Payment Channel / Method *</label>
                  <select
                    className="disb-form-select"
                    value={disburseForm.paymentMethod}
                    onChange={(e) => setDisburseForm((prev) => ({ ...prev, paymentMethod: e.target.value }))}
                  >
                    <option value="DIRECT_BENEFIT_TRANSFER">Direct Benefit Transfer (DBT)</option>
                    <option value="PFMS">Public Financial Management System (PFMS)</option>
                    <option value="NEFT">NEFT Transfer</option>
                    <option value="RTGS">RTGS Transfer</option>
                    <option value="TREASURY_TRANSFER">State Treasury Direct Warrant</option>
                  </select>
                </div>

                <div className="disb-form-group">
                  <label className="disb-form-label">Voucher / Audit Remarks</label>
                  <textarea
                    className="disb-form-textarea"
                    rows="2"
                    placeholder="Reference payment order number, treasury sanction letter..."
                    value={disburseForm.remarks}
                    onChange={(e) => setDisburseForm((prev) => ({ ...prev, remarks: e.target.value }))}
                  />
                </div>
              </div>
              <div className="disb-modal__footer">
                <button
                  type="button"
                  className="disb-btn disb-btn--secondary"
                  onClick={() => setShowDisburseModal(null)}
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="disb-btn disb-btn--success"
                  disabled={!disburseForm.paymentReference.trim()}
                >
                  <CheckCircle2 size={16} /> Confirm & Record Disbursement
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* ================================================================= */}
      {/* MODAL 7: REMINDER AUTOMATION SETTINGS */}
      {/* ================================================================= */}
      {showReminderSettingsModal && (
        <div className="disb-modal-overlay">
          <div className="disb-modal">
            <div className="disb-modal__header">
              <h3 className="disb-modal__title">Disbursement Automation & Reminders</h3>
              <button
                className="disb-modal__close-btn"
                onClick={() => setShowReminderSettingsModal(false)}
              >
                <X size={20} />
              </button>
            </div>
            <form
              onSubmit={async (e) => {
                e.preventDefault();
                try {
                  await updateReminderConfig(reminderConfigForm);
                  showToast("Automation configuration updated.");
                  setShowReminderSettingsModal(false);
                } catch {
                  setError("Failed to update reminder settings.");
                }
              }}
            >
              <div className="disb-modal__body">
                <div className="disb-form-group">
                  <label className="disb-form-label">Upcoming Reminder Window (Days Before Due Date)</label>
                  <input
                    type="number"
                    min="1"
                    max="30"
                    className="disb-form-input"
                    value={reminderConfigForm.upcomingDaysBefore}
                    onChange={(e) =>
                      setReminderConfigForm((prev) => ({
                        ...prev,
                        upcomingDaysBefore: Number(e.target.value),
                      }))
                    }
                    required
                  />
                  <span className="disb-form-help">
                    Notifications are sent automatically to beneficiaries and assigned officers before configured compliance deadlines.
                  </span>
                </div>

                <div className="disb-form-group">
                  <label style={{ display: "flex", alignItems: "center", gap: "8px", cursor: "pointer" }}>
                    <input
                      type="checkbox"
                      checked={reminderConfigForm.enabled}
                      onChange={(e) =>
                        setReminderConfigForm((prev) => ({
                          ...prev,
                          enabled: e.target.checked,
                        }))
                      }
                    />
                    <span style={{ fontWeight: 600 }}>Enable Automated Reminder Scans</span>
                  </label>
                </div>
              </div>
              <div className="disb-modal__footer">
                <button
                  type="button"
                  className="disb-btn disb-btn--secondary"
                  onClick={() => setShowReminderSettingsModal(false)}
                >
                  Cancel
                </button>
                <button type="submit" className="disb-btn disb-btn--primary">
                  Save Settings
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
