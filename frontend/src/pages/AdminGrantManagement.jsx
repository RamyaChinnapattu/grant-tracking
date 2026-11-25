import { useCallback, useEffect, useMemo, useState } from "react";
import { useSearchParams } from "react-router-dom";
import {
  AlertCircle,
  AlertTriangle,
  BadgeCheck,
  BarChart3,
  Calendar,
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
import api from "../services/api";
import {
  createDisbursementPlan,
  getDisbursementPlans,
  getEligibleApplications,
  getAllMilestones,
  getAllCompliances,
  disburseInstallment,
  deactivateDisbursementPlan,
  getFundUtilization,
  getRegionalAnalytics,
  downloadComplianceDocument,
} from "../services/disbursementService";
import {
  createComplianceRequirement,
  deactivateComplianceRequirement,
  getComplianceRequirements,
  getComplianceReviewQueue,
  getNonComplianceCases,
  reviewCompliance,
  waiveCompliance,
  reopenCompliance,
  getComplianceDocumentFile,
} from "../services/complianceService";
import { getSchemes } from "../services/schemeService";
import {
  DonutChart,
  HeroBanner,
  ProgressMeter,
} from "../components/ui/SaaSPrimitives";
import { FINTECH_CHART_COLORS } from "../utils/fintechCharts";
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

export default function AdminGrantManagement() {
  const [searchParams, setSearchParams] = useSearchParams();
  const currentTab = searchParams.get("tab") || "overview";
  const [activeTab, setActiveTab] = useState(
    currentTab === "reports" ? "overview" : currentTab
  );

  // Sync tab with URL
  useEffect(() => {
    const tabParam = searchParams.get("tab") || "overview";
    setActiveTab(tabParam === "reports" ? "overview" : tabParam);
  }, [searchParams]);

  const switchTab = (tab) => {
    const resolvedTab = tab === "reports" ? "overview" : tab;
    setActiveTab(resolvedTab);
    setSearchParams({ tab: resolvedTab });
  };

  // Data States
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState("");
  const [successMsg, setSuccessMsg] = useState("");

  const [fundUtilization, setFundUtilization] = useState(null);
  const [plans, setPlans] = useState([]);
  const [eligibleApps, setEligibleApps] = useState([]);
  const [milestones, setMilestones] = useState([]);
  const [compliances, setCompliances] = useState([]);
  const [regionalData, setRegionalData] = useState([]);
  const [requirements, setRequirements] = useState([]);
  const [complianceQueue, setComplianceQueue] = useState([]);
  const [nonComplianceCases, setNonComplianceCases] = useState([]);
  const [schemes, setSchemes] = useState([]);

  // Search and Filter states
  const [planSearch, setPlanSearch] = useState("");
  const [planStatusFilter, setPlanStatusFilter] = useState("ALL");
  const [complianceTab, setComplianceTab] = useState("requirements"); // 'requirements' | 'submissions' | 'cases'
  const [complianceFilter, setComplianceFilter] = useState("ALL");
  const [releaseFilter, setReleaseFilter] = useState("READY"); // 'READY' | 'ALL'

  // Modals
  const [showCreatePlanModal, setShowCreatePlanModal] = useState(false);
  const [showPlanDetailsModal, setShowPlanDetailsModal] = useState(null);
  const [showDisburseModal, setShowDisburseModal] = useState(null);
  const [showAddReqModal, setShowAddReqModal] = useState(false);
  const [showVerifyModal, setShowVerifyModal] = useState(null);

  // Form states for Plan Creation
  const [planAllocationMode, setPlanAllocationMode] = useState("PERCENTAGE");
  const [planSubmitting, setPlanSubmitting] = useState(false);
  const [createPlanForm, setCreatePlanForm] = useState({
    applicationId: "",
    notes: "",
    installments: [
      { installmentNumber: 1, title: "Stage 1 - Initial Advance", percentage: 40, amount: "", plannedReleaseDate: "" },
      { installmentNumber: 2, title: "Stage 2 - Mid Milestone", percentage: 30, amount: "", plannedReleaseDate: "" },
      { installmentNumber: 3, title: "Stage 3 - Final Completion", percentage: 30, amount: "", plannedReleaseDate: "" },
    ],
  });

  // Form states for Requirement Creation
  const [newReqForm, setNewReqForm] = useState({
    schemeId: "",
    targetStage: "",
    milestoneId: "",
    title: "",
    description: "",
    required: true,
    submissionDeadline: "",
    allowedFormats: "pdf,png,jpg,jpeg",
    maxFileSizeBytes: 10485760,
  });

  // Form state for Release Payment
  const [disburseForm, setDisburseForm] = useState({
    paymentReference: "",
    paymentMethod: "DBT_PFMS",
    disbursementDate: new Date().toISOString().split("T")[0],
    disbursementRemarks: "",
  });

  // Form state for Compliance Verification
  const [verifyForm, setVerifyForm] = useState({
    decision: "APPROVED",
    remarks: "",
  });

  // Load all data
  const loadAllData = useCallback(async (isRefresh = false) => {
    if (isRefresh) setRefreshing(true);
    else setLoading(true);
    setError("");

    try {
      const [
        fundRes,
        plansRes,
        eligibleRes,
        milestonesRes,
        compliancesRes,
        regRes,
        reqRes,
        queueRes,
        casesRes,
        schemesRes,
      ] = await Promise.all([
        getFundUtilization().catch(() => ({ data: null })),
        getDisbursementPlans().catch(() => ({ data: [] })),
        getEligibleApplications().catch(() => ({ data: [] })),
        getAllMilestones().catch(() => ({ data: [] })),
        getAllCompliances().catch(() => ({ data: [] })),
        getRegionalAnalytics().catch(() => ({ data: [] })),
        getComplianceRequirements().catch(() => ({ data: [] })),
        getComplianceReviewQueue().catch(() => ({ data: [] })),
        getNonComplianceCases().catch(() => ({ data: [] })),
        getSchemes().catch(() => ({ data: [] })),
      ]);

      setFundUtilization(fundRes.data || null);
      setPlans(plansRes.data || []);
      setEligibleApps(eligibleRes.data || []);
      setMilestones(milestonesRes.data || []);
      setCompliances(compliancesRes.data || []);
      setRegionalData(regRes.data || []);
      setRequirements(reqRes.data || []);
      setComplianceQueue(queueRes.data || []);
      setNonComplianceCases(casesRes.data || []);
      setSchemes(schemesRes.data || []);
    } catch (err) {
      console.error("Failed to load grant management data:", err);
      setError("Failed to load grant data. Please check connection and try again.");
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, []);

  useEffect(() => {
    loadAllData();
  }, [loadAllData]);

  // Prevent background scroll when modals open
  useEffect(() => {
    const isAnyModalOpen =
      showCreatePlanModal ||
      showPlanDetailsModal ||
      showDisburseModal ||
      showAddReqModal ||
      showVerifyModal;
    document.body.style.overflow = isAnyModalOpen ? "hidden" : "";
    return () => {
      document.body.style.overflow = "";
    };
  }, [
    showCreatePlanModal,
    showPlanDetailsModal,
    showDisburseModal,
    showAddReqModal,
    showVerifyModal,
  ]);

  // Selected app for plan creation
  const selectedAppForPlan = useMemo(() => {
    return eligibleApps.find((a) => String(a.id) === String(createPlanForm.applicationId));
  }, [eligibleApps, createPlanForm.applicationId]);

  // Calculate totals for installment configuration
  const installmentAllocationSummary = useMemo(() => {
    const approvedAmount = selectedAppForPlan ? Number(selectedAppForPlan.requestedAmount || 0) : 0;
    const stages = createPlanForm.installments || [];

    if (planAllocationMode === "PERCENTAGE") {
      const totalPct = stages.reduce((sum, s) => sum + (Number(s.percentage) || 0), 0);
      const computedAmount = (approvedAmount * totalPct) / 100;
      return {
        mode: "PERCENTAGE",
        approvedAmount,
        totalPct,
        totalAmount: computedAmount,
        isValid: Math.abs(totalPct - 100) < 0.01,
        diff: 100 - totalPct,
      };
    } else {
      const totalAmt = stages.reduce((sum, s) => sum + (Number(s.amount) || 0), 0);
      const computedPct = approvedAmount > 0 ? (totalAmt / approvedAmount) * 100 : 0;
      return {
        mode: "FIXED",
        approvedAmount,
        totalPct: computedPct,
        totalAmount: totalAmt,
        isValid: Math.abs(totalAmt - approvedAmount) < 0.01,
        diff: approvedAmount - totalAmt,
      };
    }
  }, [selectedAppForPlan, createPlanForm.installments, planAllocationMode]);

  // Handlers for Plan Creation
  const handleAddInstallment = () => {
    const currentStages = createPlanForm.installments;
    const nextNum = currentStages.length + 1;
    setCreatePlanForm({
      ...createPlanForm,
      installments: [
        ...currentStages,
        {
          installmentNumber: nextNum,
          title: `Stage ${nextNum} - Milestone`,
          percentage: 0,
          amount: "",
          plannedReleaseDate: "",
        },
      ],
    });
  };

  const handleRemoveInstallment = (index) => {
    if (createPlanForm.installments.length <= 1) return;
    const updated = createPlanForm.installments
      .filter((_, i) => i !== index)
      .map((stage, i) => ({
        ...stage,
        installmentNumber: i + 1,
      }));
    setCreatePlanForm({ ...createPlanForm, installments: updated });
  };

  const handleStageFieldChange = (index, field, value) => {
    const updated = [...createPlanForm.installments];
    updated[index] = { ...updated[index], [field]: value };
    setCreatePlanForm({ ...createPlanForm, installments: updated });
  };

  const handleCreatePlanSubmit = async (e) => {
    e.preventDefault();
    if (planSubmitting) return;

    if (!createPlanForm.applicationId) {
      setError("Please select an approved grant application.");
      return;
    }

    if (!installmentAllocationSummary.isValid) {
      if (planAllocationMode === "PERCENTAGE") {
        setError(`Total installment percentages must equal exactly 100%. Currently: ${installmentAllocationSummary.totalPct}%.`);
      } else {
        setError(
          `Total installment amounts must equal approved grant (${formatCurrency(
            installmentAllocationSummary.approvedAmount
          )}). Currently: ${formatCurrency(installmentAllocationSummary.totalAmount)}.`
        );
      }
      return;
    }

    setPlanSubmitting(true);
    setError("");
    setSuccessMsg("");

    try {
      const payload = {
        applicationId: Number(createPlanForm.applicationId),
        notes: createPlanForm.notes,
        installments: createPlanForm.installments.map((inst, idx) => ({
          installmentNumber: idx + 1,
          title: inst.title || `Stage ${idx + 1}`,
          percentage:
            planAllocationMode === "PERCENTAGE"
              ? Number(inst.percentage)
              : null,
          amount:
            planAllocationMode === "FIXED" && inst.amount
              ? Number(inst.amount)
              : null,
          plannedReleaseDate: inst.plannedReleaseDate || null,
        })),
      };

      await createDisbursementPlan(payload);
      setSuccessMsg("Disbursement plan successfully created!");
      setShowCreatePlanModal(false);
      setCreatePlanForm({
        applicationId: "",
        notes: "",
        installments: [
          { installmentNumber: 1, title: "Stage 1 - Initial Advance", percentage: 40, amount: "", plannedReleaseDate: "" },
          { installmentNumber: 2, title: "Stage 2 - Mid Milestone", percentage: 30, amount: "", plannedReleaseDate: "" },
          { installmentNumber: 3, title: "Stage 3 - Final Completion", percentage: 30, amount: "", plannedReleaseDate: "" },
        ],
      });
      await loadAllData(true);
    } catch (err) {
      setError(err.response?.data?.message || "Failed to create disbursement plan.");
    } finally {
      setPlanSubmitting(false);
    }
  };

  // Plan cancellation/deactivation
  const handleDeactivatePlan = async (planId) => {
    if (!window.confirm("Are you sure you want to deactivate / cancel this disbursement plan?")) {
      return;
    }
    try {
      await deactivateDisbursementPlan(planId);
      setSuccessMsg("Disbursement plan deactivated.");
      await loadAllData(true);
    } catch (err) {
      setError(err.response?.data?.message || "Failed to deactivate plan.");
    }
  };

  // Release Payment handler
  const handleDisburseSubmit = async (e) => {
    e.preventDefault();
    if (!showDisburseModal) return;

    if (!disburseForm.paymentReference.trim()) {
      setError("Payment Reference (Transaction ID / UTR) is required.");
      return;
    }

    setError("");
    setSuccessMsg("");

    try {
      await disburseInstallment(showDisburseModal.id, {
        paymentReference: disburseForm.paymentReference.trim(),
        paymentMethod: disburseForm.paymentMethod,
        actualDisbursementDate: disburseForm.disbursementDate,
        remarks: disburseForm.disbursementRemarks,
      });
      setSuccessMsg(`Installment ${showDisburseModal.installmentNumber} successfully disbursed!`);
      setShowDisburseModal(null);
      await loadAllData(true);
    } catch (err) {
      setError(err.response?.data?.message || "Failed to release installment payment.");
    }
  };

  // Create Requirement handler
  const handleAddRequirementSubmit = async (e) => {
    e.preventDefault();
    if (!newReqForm.schemeId) {
      setError("Target Scheme is required. Please select a scheme.");
      return;
    }
    const stageNum = Number(newReqForm.targetStage);
    if (!newReqForm.targetStage || ![1, 2, 3].includes(stageNum)) {
      setError("Target Stage is required. Please choose Stage 1, Stage 2, or Stage 3.");
      return;
    }
    if (!newReqForm.title || !newReqForm.title.trim()) {
      setError("Requirement Title is required.");
      return;
    }
    if (!newReqForm.allowedFormats || !newReqForm.allowedFormats.trim()) {
      setError("Allowed File Formats are required (e.g. pdf,png,jpg,jpeg).");
      return;
    }
    const maxBytes = Number(newReqForm.maxFileSizeBytes);
    if (
      newReqForm.maxFileSizeBytes === "" ||
      isNaN(maxBytes) ||
      !Number.isInteger(maxBytes) ||
      maxBytes < 1024 ||
      maxBytes > 10485760
    ) {
      setError("Max File Size must be a whole number between 1,024 bytes (1 KB) and 10,485,760 bytes (10 MB).");
      return;
    }
    setError("");
    setSuccessMsg("");

    try {
      await createComplianceRequirement({
        schemeId: Number(newReqForm.schemeId),
        targetStage: stageNum,
        title: newReqForm.title.trim(),
        description: newReqForm.description ? newReqForm.description.trim() : "",
        required: Boolean(newReqForm.required),
        submissionDeadline: newReqForm.submissionDeadline ? newReqForm.submissionDeadline : null,
        allowedFormats: newReqForm.allowedFormats.trim(),
        maxFileSizeBytes: maxBytes,
        milestoneId: newReqForm.milestoneId ? Number(newReqForm.milestoneId) : null,
      });
      setSuccessMsg(`Compliance requirement configured for Stage ${stageNum}.`);
      setShowAddReqModal(false);
      setNewReqForm({
        schemeId: "",
        targetStage: "",
        milestoneId: "",
        title: "",
        description: "",
        required: true,
        submissionDeadline: "",
        allowedFormats: "pdf,png,jpg,jpeg",
        maxFileSizeBytes: 10485760,
      });
      await loadAllData(true);
    } catch (err) {
      setError(err.response?.data?.message || "Failed to add compliance requirement.");
    }
  };

  // Deactivate Requirement handler
  const handleDeactivateRequirement = async (reqId) => {
    if (!window.confirm("Are you sure you want to deactivate this compliance requirement?")) return;
    try {
      await deactivateComplianceRequirement(reqId);
      setSuccessMsg("Compliance requirement deactivated.");
      await loadAllData(true);
    } catch (err) {
      setError(err.response?.data?.message || "Failed to deactivate requirement.");
    }
  };

  // Document download
  const handleDownloadDoc = async (item) => {
    try {
      const res = await (item.milestoneId
        ? downloadComplianceDocument(item.id)
        : getComplianceDocumentFile(item.id));
      const blob = new Blob([res.data], {
        type: res.headers?.["content-type"] || "application/octet-stream",
      });
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = item.documentFileName || `compliance-doc-${item.id}.pdf`;
      document.body.appendChild(a);
      a.click();
      window.URL.revokeObjectURL(url);
      a.remove();
    } catch (err) {
      setError(err.response?.data?.message || "Failed to download document.");
    }
  };

  // Waive / Reopen Compliance handler (Admin performs justified waivers or explicit reopening of rejected/legacy items)
  const handleVerifySubmit = async (e) => {
    e.preventDefault();
    if (!showVerifyModal) return;

    if (!verifyForm.remarks.trim()) {
      setError("Mandatory audit remarks / reason are required.");
      return;
    }

    setError("");
    setSuccessMsg("");

    try {
      if (verifyForm.decision === "REOPEN") {
        await reopenCompliance(showVerifyModal.id, verifyForm.remarks.trim());
        setSuccessMsg("Compliance submission reopened for a new Field review cycle.");
      } else {
        await waiveCompliance(showVerifyModal.id, verifyForm.remarks.trim());
        setSuccessMsg("Compliance requirement waived.");
      }
      setShowVerifyModal(null);
      await loadAllData(true);
    } catch (err) {
      setError(err.response?.data?.message || "Failed to process compliance action.");
    }
  };

  // Combined list of all compliance items (queue + overall compliances)
  const allComplianceItems = useMemo(() => {
    const map = new Map();
    (complianceQueue || []).forEach((c) => {
      if (c && c.id) map.set(c.id, c);
    });
    (compliances || []).forEach((c) => {
      if (c && c.id && !map.has(c.id)) {
        map.set(c.id, c);
      }
    });
    return Array.from(map.values());
  }, [complianceQueue, compliances]);

  // Displayed compliance items based on sub-tab filter
  const displayedComplianceItems = useMemo(() => {
    if (complianceFilter === "PENDING") {
      return complianceQueue;
    }
    if (complianceFilter === "ALL") {
      return allComplianceItems;
    }
    return allComplianceItems.filter((c) => c.status === complianceFilter);
  }, [complianceFilter, complianceQueue, allComplianceItems]);

  // Flattened installments list for Payment Releases Queue
  const allInstallments = useMemo(() => {
    const list = [];
    (plans || []).forEach((plan) => {
      (plan.installments || []).forEach((inst) => {
        list.push({
          ...inst,
          planId: plan.id,
          applicationId: plan.applicationId,
          schemeName: plan.schemeName,
          beneficiaryName: plan.beneficiaryName,
          state: plan.beneficiaryState || plan.state,
          district: plan.beneficiaryDistrict || plan.district,
          planStatus: plan.planStatus,
        });
      });
    });
    return list;
  }, [plans]);

  // Eligibility evaluation helper for staged installment release
  const getInstallmentEligibility = useCallback(
    (inst) => {
      if (inst.status === "DISBURSED") {
        return { isReady: false, reason: "Already disbursed" };
      }
      if (inst.status === "CANCELLED") {
        return { isReady: false, reason: "Cancelled" };
      }
      if (inst.planStatus && inst.planStatus !== "ACTIVE") {
        return { isReady: false, reason: `Plan is ${inst.planStatus}` };
      }

      // Sequential prerequisite: all prior installments in this plan must be DISBURSED
      const planInsts = allInstallments.filter((i) => i.planId === inst.planId);
      const priorIncomplete = planInsts.find(
        (i) => i.installmentNumber < inst.installmentNumber && i.status !== "DISBURSED"
      );
      if (priorIncomplete) {
        return {
          isReady: false,
          reason: `Awaiting Stage ${priorIncomplete.installmentNumber} release`,
        };
      }

      // Milestone and compliance prerequisites
      const ms = inst.milestones || [];
      if (ms.length === 0) {
        return { isReady: true, reason: "Ready for release (No milestones required)" };
      }

      const allMilestonesReady = ms.every((m) => {
        if (m.nonCompliant) return false;
        const comps = m.compliances || [];
        const compliancesSatisfied =
          comps.length === 0 ||
          comps.every(
            (c) =>
              c.required === false ||
              c.status === "APPROVED" ||
              c.status === "VERIFIED" ||
              c.status === "WAIVED"
          );
        if (!compliancesSatisfied) return false;
        return (
          m.status === "COMPLETED" &&
          (m.progressPercentage == null || Number(m.progressPercentage) === 100)
        );
      });

      if (!allMilestonesReady || !inst.allCompliancesVerified) {
        return { isReady: false, reason: "Milestones / Compliance incomplete" };
      }
      return { isReady: true, reason: "Ready for release" };
    },
    [allInstallments]
  );

  // Installments ready for release
  const readyInstallments = useMemo(() => {
    return allInstallments.filter((inst) => getInstallmentEligibility(inst).isReady);
  }, [allInstallments, getInstallmentEligibility]);

  // Consistent financial totals calculation
  const financialTotals = useMemo(() => {
    const totalAllocated =
      fundUtilization?.totalAllocatedFunds != null
        ? Number(fundUtilization.totalAllocatedFunds)
        : schemes.reduce(
          (sum, s) =>
            sum + (Number(s.allocatedFunds || s.totalBudget || s.budget) || 0),
          0
        );

    const totalCommitted =
      fundUtilization?.totalApprovedAmount != null
        ? Number(fundUtilization.totalApprovedAmount)
        : plans.reduce((sum, p) => sum + (Number(p.totalApprovedAmount) || 0), 0);

    const totalScheduled =
      fundUtilization?.totalScheduledAmount != null
        ? Number(fundUtilization.totalScheduledAmount)
        : plans.reduce((sum, p) => sum + (Number(p.totalScheduledAmount) || 0), 0);

    const totalDisbursed =
      fundUtilization?.totalDisbursedAmount != null
        ? Number(fundUtilization.totalDisbursedAmount)
        : fundUtilization?.totalReleasedAmount != null
          ? Number(fundUtilization.totalReleasedAmount)
          : plans.reduce((sum, p) => sum + (Number(p.totalDisbursedAmount) || 0), 0);

    const remainingBudget =
      fundUtilization?.remainingAllocatedAmount != null
        ? Number(fundUtilization.remainingAllocatedAmount)
        : Math.max(0, totalAllocated - totalCommitted);

    const unconfiguredSchemesCount = schemes.filter((s) => {
      const schemeFund = (fundUtilization?.schemes || []).find(
        (fs) => String(fs.schemeId) === String(s.id)
      );
      const rawBudget = schemeFund ? schemeFund.allocatedFunds : s.allocatedFunds;
      return rawBudget == null;
    }).length;

    return {
      totalAllocated,
      totalCommitted,
      totalScheduled,
      totalDisbursed,
      remainingBudget,
      unconfiguredSchemesCount,
    };
  }, [fundUtilization, schemes, plans]);

  // Filtered plans
  const filteredPlans = useMemo(() => {
    return plans.filter((p) => {
      const matchSearch =
        !planSearch ||
        p.schemeName?.toLowerCase().includes(planSearch.toLowerCase()) ||
        p.beneficiaryName?.toLowerCase().includes(planSearch.toLowerCase()) ||
        String(p.applicationId).includes(planSearch) ||
        String(p.id).includes(planSearch);

      const matchStatus =
        planStatusFilter === "ALL" || p.planStatus === planStatusFilter;

      return matchSearch && matchStatus;
    });
  }, [plans, planSearch, planStatusFilter]);

  return (
    <div className="admin-grant-mgmt saas-page">
      <HeroBanner
        eyebrow="Treasury & Financial Control"
        title="Grant Management"
        subtitle="Consolidated financial administration: scheme budgets, staged disbursement plans, compliance verification, and release queues."
        badge={
          <span className="saas-badge saas-badge--teal">
            {readyInstallments.length} Ready to Release
          </span>
        }
        actions={
          <button
            className="saas-btn saas-btn--hero-ghost"
            type="button"
            onClick={() => loadAllData(true)}
            disabled={refreshing || loading}
          >
            <RefreshCw size={15} className={refreshing ? "spin" : ""} />
            {refreshing ? "Refreshing…" : "Refresh Data"}
          </button>
        }
      />

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

      {/* Primary Tab Navigation */}
      <nav className="mgmt-tabs" aria-label="Grant Management Sections">
        <button
          className={`mgmt-tab-btn ${activeTab === "overview" ? "active" : ""}`}
          onClick={() => switchTab("overview")}
        >
          <BarChart3 size={18} />
          Overview
        </button>
        <button
          className={`mgmt-tab-btn ${activeTab === "plans" ? "active" : ""}`}
          onClick={() => switchTab("plans")}
        >
          <Layers size={18} />
          Disbursement Plans
          {plans.length > 0 && <span className="mgmt-badge">{plans.length}</span>}
        </button>
        <button
          className={`mgmt-tab-btn ${activeTab === "compliance" ? "active" : ""}`}
          onClick={() => switchTab("compliance")}
        >
          <BadgeCheck size={18} />
          Compliance
          {complianceQueue.length > 0 && (
            <span className="mgmt-badge">{complianceQueue.length}</span>
          )}
        </button>
        <button
          className={`mgmt-tab-btn ${activeTab === "releases" ? "active" : ""}`}
          onClick={() => switchTab("releases")}
        >
          <CircleDollarSign size={18} />
          Payment Releases
          {readyInstallments.length > 0 && (
            <span className="mgmt-badge">{readyInstallments.length}</span>
          )}
        </button>
      </nav>

      {/* ========================================================================= */}
      {/* TAB A: OVERVIEW                                                           */}
      {/* ========================================================================= */}
      {activeTab === "overview" && (
        <section className="mgmt-tab-content">
          {/* Financial KPIs */}
          <div className="mgmt-kpi-grid">
            <article className="mgmt-kpi-card">
              <span>
                <CircleDollarSign size={16} /> Total Scheme Budget
              </span>
              <strong>
                {formatCurrency(financialTotals.totalAllocated)}
              </strong>
              <small>
                {financialTotals.unconfiguredSchemesCount > 0
                  ? `Configured schemes (${financialTotals.unconfiguredSchemesCount} not configured)`
                  : "Configured scheme allocations"}
              </small>
            </article>

            <article className="mgmt-kpi-card">
              <span>
                <FileCheck size={16} /> Committed Grant Amount
              </span>
              <strong>
                {formatCurrency(financialTotals.totalCommitted)}
              </strong>
              <small>Approved beneficiary grants</small>
            </article>

            <article className="mgmt-kpi-card">
              <span>
                <Layers size={16} /> Total Scheduled
              </span>
              <strong>
                {formatCurrency(financialTotals.totalScheduled)}
              </strong>
              <small>Configured plan installments</small>
            </article>

            <article className="mgmt-kpi-card">
              <span>
                <CheckCircle2 size={16} /> Total Disbursed
              </span>
              <strong>
                {formatCurrency(financialTotals.totalDisbursed)}
              </strong>
              <small>Recorded released funds</small>
            </article>

            <article className="mgmt-kpi-card">
              <span>
                <CircleDollarSign size={16} /> Remaining Budget
              </span>
              <strong>
                {formatCurrency(financialTotals.remainingBudget)}
              </strong>
              <small>Uncommitted scheme funds</small>
            </article>

            <article className="mgmt-kpi-card">
              <span>
                <Clock size={16} /> Ready for Release
              </span>
              <strong>{readyInstallments.length}</strong>
              <small>Stages with 100% compliance</small>
            </article>
          </div>

          {/* Quick Action Navigation */}
          <div className="mgmt-quick-actions">
            <div>
              <h3>Financial Workflow Shortcuts</h3>
              <p style={{ margin: "3px 0 0", color: "rgba(226, 232, 240, 0.88)", fontSize: "0.85rem" }}>
                Jump directly to the next stage of the grant disbursement cycle.
              </p>
            </div>
            <div className="mgmt-quick-actions__buttons">
              <button
                className="saas-btn saas-btn--accent"
                onClick={() => {
                  switchTab("plans");
                  setShowCreatePlanModal(true);
                }}
              >
                <Plus size={16} /> Setup Disbursement Plan
              </button>
              <button className="saas-btn saas-btn--hero-ghost" onClick={() => switchTab("releases")}>
                <CircleDollarSign size={16} /> Payment Releases ({readyInstallments.length})
              </button>
              <button className="saas-btn saas-btn--hero-ghost" onClick={() => switchTab("compliance")}>
                <BadgeCheck size={16} /> Compliance Queue ({complianceQueue.length})
              </button>
            </div>
          </div>

          {/* Fund Allocation Breakdown Chart */}
          <div className="mgmt-card" style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(280px, 1fr))", gap: "1.5rem", alignItems: "center" }}>
            <div>
              <h2 style={{ margin: "0 0 0.25rem", fontSize: "1.1rem", fontWeight: 750 }}>
                Treasury Allocation Breakdown
              </h2>
              <p style={{ margin: "0 0 1rem", fontSize: "0.84rem", color: "var(--color-text-muted)" }}>
                Disbursed funds vs committed unpaid sanctions vs remaining scheme budget
              </p>
              <DonutChart
                segments={[
                  {
                    label: "Disbursed Funds",
                    value: financialTotals.totalDisbursed,
                    color: FINTECH_CHART_COLORS.emerald,
                  },
                  {
                    label: "Committed (Pending Release)",
                    value: Math.max(0, financialTotals.totalCommitted - financialTotals.totalDisbursed),
                    color: FINTECH_CHART_COLORS.indigo,
                  },
                  {
                    label: "Remaining Uncommitted Budget",
                    value: financialTotals.remainingBudget,
                    color: FINTECH_CHART_COLORS.blue,
                  },
                ]}
                centerLabel="Total Budget"
                centerValue={formatCurrency(financialTotals.totalAllocated)}
                formatValue={(v) => formatCurrency(v)}
                emptyText="Configure scheme budgets or approve grants to visualize fund distribution."
              />
            </div>

            <div style={{ display: "flex", flexDirection: "column", gap: "1rem", padding: "1rem", borderRadius: "14px", background: "var(--color-surface-low)", border: "1px solid var(--color-outline-variant)" }}>
              <ProgressMeter
                label="Sanction Utilization (Committed / Budget)"
                value={financialTotals.totalCommitted}
                max={financialTotals.totalAllocated || 1}
                tone="indigo"
                sublabel={`${formatCurrency(financialTotals.totalCommitted)} committed out of ${formatCurrency(financialTotals.totalAllocated)}`}
              />
              <ProgressMeter
                label="Payout Completion (Disbursed / Committed)"
                value={financialTotals.totalDisbursed}
                max={financialTotals.totalCommitted || 1}
                tone="emerald"
                sublabel={`${formatCurrency(financialTotals.totalDisbursed)} actually transferred to beneficiaries`}
              />
            </div>
          </div>

          {/* Scheme Utilization Summary */}
          <div className="mgmt-card">
            <div className="mgmt-card-header">
              <h2>Scheme Fund Utilization</h2>
              <span style={{ fontSize: "0.85rem", color: "#64748b" }}>
                Allocated vs Disbursed by Scheme
              </span>
            </div>
            {schemes.length === 0 ? (
              <p style={{ color: "#64748b", margin: 0 }}>No schemes configured yet.</p>
            ) : (
              <div className="mgmt-table-wrap">
                <table className="mgmt-table">
                  <thead>
                    <tr>
                      <th>Scheme Name</th>
                      <th>Department</th>
                      <th>Total Budget</th>
                      <th>Committed</th>
                      <th>Disbursed</th>
                      <th>Progress</th>
                    </tr>
                  </thead>
                  <tbody>
                    {schemes.map((s) => {
                      const schemeFund = (fundUtilization?.schemes || []).find(
                        (fs) => String(fs.schemeId) === String(s.id)
                      );
                      const schemePlans = plans.filter((p) => String(p.schemeId) === String(s.id));
                      const rawBudget = schemeFund ? schemeFund.allocatedFunds : s.allocatedFunds;
                      const isBudgetConfigured = rawBudget != null;
                      const budget = isBudgetConfigured ? Number(rawBudget) : 0;
                      const committed = Number(
                        schemeFund?.approvedAmount ??
                        schemePlans.reduce(
                          (sum, p) => sum + (Number(p.totalApprovedAmount) || 0),
                          0
                        )
                      );
                      const released = Number(
                        schemeFund?.disbursedAmount ??
                        schemeFund?.releasedAmount ??
                        schemePlans.reduce(
                          (sum, p) => sum + (Number(p.totalDisbursedAmount) || 0),
                          0
                        )
                      );
                      const pct = committed > 0 ? Math.min(100, (released / committed) * 100) : 0;

                      return (
                        <tr key={s.id}>
                          <td>
                            <strong>{s.schemeName}</strong>
                          </td>
                          <td>{s.department || "General"}</td>
                          <td>
                            {isBudgetConfigured ? (
                              formatCurrency(budget)
                            ) : (
                              <span style={{ color: "#d97706", fontWeight: 600, fontSize: "0.85rem" }}>
                                Not configured
                              </span>
                            )}
                          </td>
                          <td>{formatCurrency(committed)}</td>
                          <td>{formatCurrency(released)}</td>
                          <td style={{ minWidth: "140px" }}>
                            <div style={{ display: "flex", alignItems: "center", gap: "0.5rem" }}>
                              <div className="mgmt-progress-bar" style={{ flex: 1 }}>
                                <div
                                  className={`mgmt-progress-fill ${pct >= 85 ? "success" : pct >= 50 ? "warning" : ""
                                    }`}
                                  style={{ width: `${pct}%` }}
                                />
                              </div>
                              <span style={{ fontSize: "0.75rem", fontWeight: 600 }}>
                                {pct.toFixed(1)}%
                              </span>
                            </div>
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            )}
          </div>

          {regionalData.length > 0 && (
            <div className="mgmt-card">
              <div className="mgmt-card-header">
                <div>
                  <h2>Regional Disbursement &amp; Utilization Analytics</h2>
                  <p style={{ margin: "2px 0 0", fontSize: "0.82rem", color: "#64748b" }}>
                    Live approved vs. disbursed performance across districts
                  </p>
                </div>
              </div>
              <div className="mgmt-table-wrap">
                <table className="mgmt-table">
                  <thead>
                    <tr>
                      <th>Region</th>
                      <th>Applications</th>
                      <th>Approved</th>
                      <th>Disbursed</th>
                      <th>Compliance Rate</th>
                      <th>Utilization</th>
                    </tr>
                  </thead>
                  <tbody>
                    {regionalData.slice(0, 8).map((r, idx) => (
                      <tr key={`${r.state}-${r.district}-${idx}`}>
                        <td>
                          <strong>{r.district || "All Districts"}</strong>
                          <div style={{ fontSize: "0.75rem", color: "#64748b" }}>
                            {r.state || "—"}
                          </div>
                        </td>
                        <td>{r.totalApplications ?? 0}</td>
                        <td>{formatCurrency(r.totalApprovedAmount)}</td>
                        <td>{formatCurrency(r.totalDisbursedAmount)}</td>
                        <td>{Number(r.complianceRate ?? 0).toFixed(1)}%</td>
                        <td>{Number(r.fundUtilizationRate ?? 0).toFixed(1)}%</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}
        </section>
      )}

      {/* ========================================================================= */}
      {/* TAB B: DISBURSEMENT PLANS                                                 */}
      {/* ========================================================================= */}
      {activeTab === "plans" && (
        <section className="mgmt-tab-content">
          {/* Action and Alert Banner */}
          <div
            style={{
              display: "flex",
              justifyContent: "space-between",
              alignItems: "center",
              marginBottom: "1rem",
              flexWrap: "wrap",
              gap: "1rem",
            }}
          >
            <div>
              <h2 style={{ fontSize: "1.25rem", margin: 0, fontWeight: 700 }}>
                Staged Disbursement Plans
              </h2>
              <p style={{ margin: 0, color: "#64748b", fontSize: "0.875rem" }}>
                Schedule installment milestones for approved grant applications.
              </p>
            </div>
            <button
              className="mgmt-btn primary"
              onClick={() => setShowCreatePlanModal(true)}
            >
              <Plus size={16} /> Configure New Plan
            </button>
          </div>

          {/* Pending Applications Notice */}
          {eligibleApps.length > 0 && (
            <div className="mgmt-alert info">
              <AlertCircle size={18} />
              <span>
                <strong>{eligibleApps.length} approved applications</strong> are ready for
                staged disbursement planning.
              </span>
              <button
                className="mgmt-btn primary"
                style={{ marginLeft: "auto", padding: "0.35rem 0.75rem", fontSize: "0.8rem" }}
                onClick={() => {
                  setCreatePlanForm({
                    ...createPlanForm,
                    applicationId: String(eligibleApps[0].id),
                  });
                  setShowCreatePlanModal(true);
                }}
              >
                Setup Next
              </button>
            </div>
          )}

          {/* Filters Bar */}
          <div className="mgmt-filters-bar">
            <div className="mgmt-search-input">
              <Search size={16} color="#94a3b8" />
              <input
                type="text"
                placeholder="Search by Scheme, Beneficiary, or App ID…"
                value={planSearch}
                onChange={(e) => setPlanSearch(e.target.value)}
              />
              {planSearch && (
                <button
                  onClick={() => setPlanSearch("")}
                  style={{ background: "none", border: "none", cursor: "pointer" }}
                >
                  <X size={14} />
                </button>
              )}
            </div>

            <select
              className="mgmt-select"
              value={planStatusFilter}
              onChange={(e) => setPlanStatusFilter(e.target.value)}
            >
              <option value="ALL">All Statuses</option>
              <option value="ACTIVE">ACTIVE</option>
              <option value="COMPLETED">COMPLETED</option>
              <option value="CANCELLED">CANCELLED</option>
            </select>
          </div>

          {/* Plans Table */}
          <div className="mgmt-card">
            {filteredPlans.length === 0 ? (
              <div style={{ textAlign: "center", padding: "2rem", color: "#64748b" }}>
                <Layers size={32} style={{ marginBottom: "0.5rem", opacity: 0.5 }} />
                <p>No disbursement plans match your criteria.</p>
              </div>
            ) : (
              <div className="mgmt-table-wrap">
                <table className="mgmt-table">
                  <thead>
                    <tr>
                      <th>Plan ID</th>
                      <th>App #</th>
                      <th>Beneficiary</th>
                      <th>Scheme</th>
                      <th>Approved Grant</th>
                      <th>Scheduled</th>
                      <th>Disbursed</th>
                      <th>Remaining</th>
                      <th>Status</th>
                      <th>Actions</th>
                    </tr>
                  </thead>
                  <tbody>
                    {filteredPlans.map((plan) => (
                      <tr key={plan.id}>
                        <td>
                          <strong>#{plan.id}</strong>
                        </td>
                        <td>#{plan.applicationId}</td>
                        <td>
                          <div>{plan.beneficiaryName}</div>
                          {(plan.beneficiaryDistrict || plan.district) && (
                            <small style={{ color: "#64748b" }}>
                              {plan.beneficiaryDistrict || plan.district},{" "}
                              {plan.beneficiaryState || plan.state}
                            </small>
                          )}
                        </td>
                        <td>{plan.schemeName}</td>
                        <td>
                          <strong>{formatCurrency(plan.totalApprovedAmount)}</strong>
                        </td>
                        <td>{formatCurrency(plan.totalScheduledAmount)}</td>
                        <td>{formatCurrency(plan.totalDisbursedAmount)}</td>
                        <td>
                          {formatCurrency(
                            plan.remainingAmount ??
                            Math.max(
                              0,
                              Number(plan.totalApprovedAmount || 0) -
                              Number(plan.totalDisbursedAmount || 0)
                            )
                          )}
                        </td>
                        <td>
                          <span
                            className={`mgmt-status-badge status-${plan.planStatus?.toLowerCase()}`}
                          >
                            {plan.planStatus}
                          </span>
                        </td>
                        <td>
                          <div style={{ display: "flex", gap: "0.5rem" }}>
                            <button
                              className="mgmt-btn secondary"
                              style={{ padding: "0.3rem 0.6rem", fontSize: "0.8rem" }}
                              onClick={() => setShowPlanDetailsModal(plan)}
                            >
                              <Eye size={14} /> View Stages
                            </button>
                            {plan.planStatus === "ACTIVE" && (
                              <button
                                className="mgmt-btn danger"
                                style={{ padding: "0.3rem 0.6rem", fontSize: "0.8rem" }}
                                onClick={() => handleDeactivatePlan(plan.id)}
                              >
                                Cancel
                              </button>
                            )}
                          </div>
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

      {/* ========================================================================= */}
      {/* TAB C: COMPLIANCE                                                         */}
      {/* ========================================================================= */}
      {activeTab === "compliance" && (
        <section className="mgmt-tab-content">
          {/* Sub-nav inside Compliance */}
          <div
            style={{
              display: "flex",
              justifyContent: "space-between",
              alignItems: "center",
              marginBottom: "1rem",
              flexWrap: "wrap",
              gap: "1rem",
            }}
          >
            <div style={{ display: "flex", gap: "0.5rem" }}>
              <button
                className={`mgmt-btn ${complianceTab === "requirements" ? "primary" : "secondary"}`}
                onClick={() => setComplianceTab("requirements")}
              >
                Scheme Requirements ({requirements.length})
              </button>
              <button
                className={`mgmt-btn ${complianceTab === "submissions" ? "primary" : "secondary"}`}
                onClick={() => setComplianceTab("submissions")}
              >
                Evidence Review Queue ({complianceQueue.length})
              </button>
              <button
                className={`mgmt-btn ${complianceTab === "cases" ? "primary" : "secondary"}`}
                onClick={() => setComplianceTab("cases")}
              >
                Non-Compliance Cases ({nonComplianceCases.length})
              </button>
            </div>

            {complianceTab === "requirements" && (
              <button className="mgmt-btn primary" onClick={() => setShowAddReqModal(true)}>
                <Plus size={16} /> Add Requirement
              </button>
            )}
          </div>

          {/* Sub-tab 1: Scheme Requirements */}
          {complianceTab === "requirements" && (
            <div className="mgmt-card">
              <div className="mgmt-card-header">
                <h2>Configured Scheme Compliance Requirements</h2>
              </div>
              {requirements.length === 0 ? (
                <p style={{ color: "#64748b" }}>No compliance requirements defined yet.</p>
              ) : (
                <div className="mgmt-table-wrap">
                  <table className="mgmt-table">
                    <thead>
                      <tr>
                        <th>Requirement</th>
                        <th>Scheme</th>
                        <th>Target Stage</th>
                        <th>Mandatory</th>
                        <th>Allowed Formats</th>
                        <th>Max Size</th>
                        <th>Status</th>
                        <th>Actions</th>
                      </tr>
                    </thead>
                    <tbody>
                      {requirements.map((req) => (
                        <tr key={req.id}>
                          <td>
                            <strong>{req.title}</strong>
                            {req.description && (
                              <div style={{ color: "#64748b", fontSize: "0.8rem" }}>
                                {req.description}
                              </div>
                            )}
                          </td>
                          <td>{req.schemeName || `Scheme #${req.schemeId}`}</td>
                          <td>
                            {req.targetStage != null ? (
                              <span
                                className="mgmt-status-badge"
                                style={{ background: "#eff6ff", color: "#1d4ed8" }}
                              >
                                Stage {req.targetStage}
                              </span>
                            ) : req.milestoneId ? (
                              <span>Milestone #{req.milestoneId}</span>
                            ) : (
                              <span style={{ color: "#64748b", fontSize: "0.8rem" }}>
                                Legacy (Unscoped)
                              </span>
                            )}
                          </td>
                          <td>
                            {req.required ? (
                              <span style={{ color: "#dc2626", fontWeight: 600 }}>Required</span>
                            ) : (
                              <span style={{ color: "#64748b" }}>Optional</span>
                            )}
                          </td>
                          <td>{req.allowedFormats || "Any"}</td>
                          <td>{formatFileSize(req.maxFileSizeBytes)}</td>
                          <td>
                            <span
                              className={`mgmt-status-badge status-${req.active ? "approved" : "cancelled"
                                }`}
                            >
                              {req.active ? "ACTIVE" : "INACTIVE"}
                            </span>
                          </td>
                          <td>
                            {req.active && (
                              <button
                                className="mgmt-btn danger"
                                style={{ padding: "0.25rem 0.5rem", fontSize: "0.75rem" }}
                                onClick={() => handleDeactivateRequirement(req.id)}
                              >
                                Deactivate
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
          )}

          {/* Sub-tab 2: Submissions Review Queue */}
          {complianceTab === "submissions" && (
            <div className="mgmt-card">
              <div
                className="mgmt-card-header"
                style={{
                  display: "flex",
                  justifyContent: "space-between",
                  alignItems: "center",
                  flexWrap: "wrap",
                  gap: "0.5rem",
                }}
              >
                <div>
                  <h2 style={{ margin: 0 }}>Evidence Review Queue &amp; Submissions</h2>
                  <span style={{ fontSize: "0.85rem", color: "#64748b" }}>
                    Submitted documents awaiting verification, waiver, or audit remarks
                  </span>
                </div>
                <div style={{ display: "flex", gap: "0.5rem" }}>
                  <button
                    className={`mgmt-btn ${complianceFilter === "PENDING" ? "primary" : "secondary"}`}
                    style={{ padding: "0.3rem 0.6rem", fontSize: "0.75rem" }}
                    onClick={() => setComplianceFilter("PENDING")}
                  >
                    Action Queue ({complianceQueue.length})
                  </button>
                  <button
                    className={`mgmt-btn ${complianceFilter === "ALL" ? "primary" : "secondary"}`}
                    style={{ padding: "0.3rem 0.6rem", fontSize: "0.75rem" }}
                    onClick={() => setComplianceFilter("ALL")}
                  >
                    All Submissions ({allComplianceItems.length})
                  </button>
                </div>
              </div>
              {displayedComplianceItems.length === 0 ? (
                <div style={{ textAlign: "center", padding: "2rem", color: "#64748b" }}>
                  <CheckCircle2 size={32} style={{ marginBottom: "0.5rem", opacity: 0.5 }} />
                  <p>
                    {complianceFilter === "PENDING"
                      ? "All compliance submissions in the action queue have been reviewed."
                      : "No compliance records found."}
                  </p>
                </div>
              ) : (
                <div className="mgmt-table-wrap">
                  <table className="mgmt-table">
                    <thead>
                      <tr>
                        <th>Requirement</th>
                        <th>App #</th>
                        <th>Beneficiary</th>
                        <th>Milestone</th>
                        <th>Status</th>
                        <th>Document</th>
                        <th>Actions</th>
                      </tr>
                    </thead>
                    <tbody>
                      {displayedComplianceItems.map((item) => (
                        <tr key={item.id}>
                          <td>
                            <strong>{item.requirementTitle}</strong>
                            {item.submissionNotes && (
                              <div style={{ fontSize: "0.8rem", color: "#64748b" }}>
                                Note: {item.submissionNotes}
                              </div>
                            )}
                            {item.verificationRemarks && (
                              <div style={{ fontSize: "0.75rem", color: "#0369a1" }}>
                                Remarks: {item.verificationRemarks}
                              </div>
                            )}
                          </td>
                          <td>#{item.applicationId}</td>
                          <td>{item.beneficiaryName || "Beneficiary"}</td>
                          <td>{item.milestoneTitle || "Milestone"}</td>
                          <td>
                            <span
                              className={`mgmt-status-badge status-${item.status?.toLowerCase()}`}
                            >
                              {item.status}
                            </span>
                          </td>
                          <td>
                            {item.hasDocument ? (
                              <button
                                className="mgmt-btn secondary"
                                style={{ padding: "0.25rem 0.5rem", fontSize: "0.75rem" }}
                                onClick={() => handleDownloadDoc(item)}
                              >
                                <Download size={13} /> {item.documentFileName || "View File"}
                              </button>
                            ) : (
                              <span style={{ color: "#94a3b8", fontSize: "0.8rem" }}>
                                No file uploaded
                              </span>
                            )}
                          </td>
                          <td>
                            {["REJECTED", "APPROVED", "VERIFIED"].includes(item.status) ? (
                              <div style={{ display: "flex", gap: "0.35rem", flexWrap: "wrap" }}>
                                <button
                                  className="mgmt-btn secondary"
                                  style={{ padding: "0.3rem 0.6rem", fontSize: "0.8rem" }}
                                  onClick={() => {
                                    setShowVerifyModal(item);
                                    setVerifyForm({ decision: "REOPEN", remarks: "" });
                                  }}
                                >
                                  Reopen
                                </button>
                                <button
                                  className="mgmt-btn secondary"
                                  style={{ padding: "0.3rem 0.6rem", fontSize: "0.8rem" }}
                                  onClick={() => {
                                    setShowVerifyModal(item);
                                    setVerifyForm({ decision: "WAIVED", remarks: "" });
                                  }}
                                >
                                  Waive
                                </button>
                              </div>
                            ) : !["PAYMENT_ELIGIBLE", "WAIVED"].includes(item.status) ? (
                              <button
                                className="mgmt-btn secondary"
                                style={{ padding: "0.3rem 0.6rem", fontSize: "0.8rem" }}
                                onClick={() => {
                                  setShowVerifyModal(item);
                                  setVerifyForm({ decision: "WAIVED", remarks: "" });
                                }}
                              >
                                Waive (Admin)
                              </button>
                            ) : (
                              <span style={{ color: "#64748b", fontSize: "0.8rem" }}>
                                {item.currentReviewStage ? `At ${item.currentReviewStage}` : "Completed"}
                              </span>
                            )}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </div>
          )}

          {/* Sub-tab 3: Non-Compliance Cases */}
          {complianceTab === "cases" && (
            <div className="mgmt-card">
              <div className="mgmt-card-header">
                <h2>Flagged Non-Compliance Cases</h2>
              </div>
              {nonComplianceCases.length === 0 ? (
                <div style={{ textAlign: "center", padding: "2rem", color: "#64748b" }}>
                  <ShieldCheck size={32} style={{ marginBottom: "0.5rem", opacity: 0.5 }} />
                  <p>No active non-compliance cases.</p>
                </div>
              ) : (
                <div className="mgmt-table-wrap">
                  <table className="mgmt-table">
                    <thead>
                      <tr>
                        <th>Case #</th>
                        <th>Milestone</th>
                        <th>Reason</th>
                        <th>Severity</th>
                        <th>Status</th>
                        <th>Created At</th>
                      </tr>
                    </thead>
                    <tbody>
                      {nonComplianceCases.map((c) => (
                        <tr key={c.id}>
                          <td>#{c.id}</td>
                          <td>{c.milestoneTitle || `Milestone #${c.milestoneId}`}</td>
                          <td>{c.reason}</td>
                          <td>{c.severity || "MEDIUM"}</td>
                          <td>
                            <span className="mgmt-status-badge status-non_compliant">
                              {c.status}
                            </span>
                          </td>
                          <td>{formatDate(c.createdAt)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </div>
          )}
        </section>
      )}

      {/* ========================================================================= */}
      {/* TAB D: PAYMENT RELEASES                                                   */}
      {/* ========================================================================= */}
      {activeTab === "releases" && (
        <section className="mgmt-tab-content">
          <div
            style={{
              display: "flex",
              justifyContent: "space-between",
              alignItems: "center",
              marginBottom: "1rem",
              flexWrap: "wrap",
              gap: "1rem",
            }}
          >
            <div>
              <h2 style={{ fontSize: "1.25rem", margin: 0, fontWeight: 700 }}>
                Installment Payment Releases Queue
              </h2>
              <p style={{ margin: 0, color: "#64748b", fontSize: "0.875rem" }}>
                Release scheduled funds to beneficiaries after compliance verification.
              </p>
            </div>

            <div style={{ display: "flex", gap: "0.5rem" }}>
              <button
                className={`mgmt-btn ${releaseFilter === "READY" ? "primary" : "secondary"}`}
                onClick={() => setReleaseFilter("READY")}
              >
                Ready for Release ({readyInstallments.length})
              </button>
              <button
                className={`mgmt-btn ${releaseFilter === "ALL" ? "primary" : "secondary"}`}
                onClick={() => setReleaseFilter("ALL")}
              >
                All Stages ({allInstallments.length})
              </button>
            </div>
          </div>

          <div className="mgmt-card">
            {(releaseFilter === "READY" ? readyInstallments : allInstallments).length === 0 ? (
              <div style={{ textAlign: "center", padding: "2.5rem", color: "#64748b" }}>
                <CircleDollarSign size={36} style={{ marginBottom: "0.5rem", opacity: 0.5 }} />
                <p>
                  {releaseFilter === "READY"
                    ? "No installments are currently ready for release. Ensure all required milestones are 100% completed and compliant."
                    : "No installments found."}
                </p>
              </div>
            ) : (
              <div className="mgmt-table-wrap">
                <table className="mgmt-table">
                  <thead>
                    <tr>
                      <th>Stage #</th>
                      <th>Application &amp; Beneficiary</th>
                      <th>Scheme</th>
                      <th>Amount</th>
                      <th>Planned Date</th>
                      <th>Milestone Compliance</th>
                      <th>Status</th>
                      <th>Action</th>
                    </tr>
                  </thead>
                  <tbody>
                    {(releaseFilter === "READY" ? readyInstallments : allInstallments).map((inst) => {
                      const ms = inst.milestones || [];
                      const eligibility = getInstallmentEligibility(inst);
                      const isReady = eligibility.isReady;

                      return (
                        <tr key={inst.id}>
                          <td>
                            <strong>
                              Stage {inst.installmentNumber}
                            </strong>
                            <div style={{ fontSize: "0.8rem", color: "#64748b" }}>
                              {inst.title}
                            </div>
                          </td>
                          <td>
                            <div>#{inst.applicationId} · {inst.beneficiaryName}</div>
                            {inst.district && (
                              <small style={{ color: "#64748b" }}>
                                {inst.district}, {inst.state}
                              </small>
                            )}
                          </td>
                          <td>{inst.schemeName}</td>
                          <td>
                            <strong style={{ fontSize: "1rem" }}>
                              {formatCurrency(inst.amount)}
                            </strong>
                            <div style={{ fontSize: "0.75rem", color: "#64748b" }}>
                              {inst.percentage}% of grant
                            </div>
                          </td>
                          <td>{formatDate(inst.plannedReleaseDate)}</td>
                          <td>
                            {ms.length === 0 ? (
                              <span style={{ color: "#16a34a", fontSize: "0.8rem" }}>
                                No milestone condition
                              </span>
                            ) : (
                              <div>
                                {ms.map((m) => {
                                  const effectiveProgress =
                                    m.progressPercentage != null
                                      ? Number(m.progressPercentage)
                                      : m.status === "COMPLETED"
                                        ? 100
                                        : 0;
                                  return (
                                    <div key={m.id} style={{ fontSize: "0.8rem" }}>
                                      <span
                                        style={{
                                          color:
                                            effectiveProgress === 100 || m.status === "COMPLETED"
                                              ? "#16a34a"
                                              : "#f59e0b",
                                          fontWeight: 600,
                                        }}
                                      >
                                        {m.title}: {effectiveProgress}%
                                      </span>
                                      {m.nonCompliant && (
                                        <span style={{ color: "#dc2626", marginLeft: "0.3rem" }}>
                                          (Non-Compliant!)
                                        </span>
                                      )}
                                    </div>
                                  );
                                })}
                              </div>
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
                            {inst.status === "DISBURSED" ? (
                              <div style={{ fontSize: "0.75rem", color: "#16a34a" }}>
                                Released on {formatDate(inst.actualDisbursementDate)}
                                {inst.paymentReference && <div>Ref: {inst.paymentReference}</div>}
                              </div>
                            ) : isReady ? (
                              <span
                                style={{
                                  fontSize: "0.78rem",
                                  color: "#0369a1",
                                  fontWeight: 600,
                                }}
                              >
                                Ready for Finance Officer Release
                              </span>
                            ) : (
                              <span
                                style={{
                                  fontSize: "0.75rem",
                                  color: "#94a3b8",
                                  fontStyle: "italic",
                                }}
                              >
                                {eligibility.reason}
                              </span>
                            )}
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        </section>
      )}



      {/* ========================================================================= */}
      {/* MODAL: CREATE DISBURSEMENT PLAN                                           */}
      {/* ========================================================================= */}
      {showCreatePlanModal && (
        <div className="mgmt-modal-overlay" onClick={() => setShowCreatePlanModal(false)}>
          <div
            className="mgmt-modal large"
            onClick={(e) => e.stopPropagation()}
            role="dialog"
            aria-modal="true"
          >
            <div className="mgmt-modal__header">
              <h2>Configure Staged Disbursement Plan</h2>
              <button
                onClick={() => setShowCreatePlanModal(false)}
                style={{ background: "none", border: "none", cursor: "pointer" }}
              >
                <X size={20} />
              </button>
            </div>

            <form
              onSubmit={handleCreatePlanSubmit}
              style={{ display: "flex", flexDirection: "column", flex: 1, minHeight: 0 }}
            >
              <div className="mgmt-modal__body">
                {/* Select Application */}
                <div className="mgmt-form-group">
                  <label>Select Approved Grant Application *</label>
                  <select
                    required
                    value={createPlanForm.applicationId}
                    onChange={(e) =>
                      setCreatePlanForm({ ...createPlanForm, applicationId: e.target.value })
                    }
                  >
                    <option value="">-- Choose an approved application --</option>
                    {eligibleApps.map((app) => (
                      <option key={app.id} value={app.id}>
                        App #{app.id} · {app.beneficiaryName} · {app.schemeName} (Grant:{" "}
                        {formatCurrency(app.requestedAmount)})
                      </option>
                    ))}
                  </select>
                </div>

                {selectedAppForPlan && (
                  <div
                    style={{
                      background: "#eff6ff",
                      border: "1px solid #bfdbfe",
                      borderRadius: "6px",
                      padding: "0.75rem 1rem",
                      marginBottom: "1.25rem",
                    }}
                  >
                    <div>
                      <strong>Beneficiary:</strong> {selectedAppForPlan.beneficiaryName}
                    </div>
                    <div>
                      <strong>Approved Grant Amount:</strong>{" "}
                      {formatCurrency(selectedAppForPlan.requestedAmount)}
                    </div>
                    <div>
                      <strong>Scheme:</strong> {selectedAppForPlan.schemeName}
                    </div>
                  </div>
                )}

                {/* Allocation Mode Selector */}
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
                  <label style={{ fontWeight: 600, fontSize: "0.875rem" }}>
                    Installment Stages
                  </label>
                  <div style={{ display: "flex", gap: "0.5rem" }}>
                    <button
                      type="button"
                      className={`mgmt-btn ${planAllocationMode === "PERCENTAGE" ? "primary" : "secondary"
                        }`}
                      style={{ padding: "0.3rem 0.6rem", fontSize: "0.75rem" }}
                      onClick={() => setPlanAllocationMode("PERCENTAGE")}
                    >
                      Percentage Mode
                    </button>
                    <button
                      type="button"
                      className={`mgmt-btn ${planAllocationMode === "FIXED" ? "primary" : "secondary"
                        }`}
                      style={{ padding: "0.3rem 0.6rem", fontSize: "0.75rem" }}
                      onClick={() => setPlanAllocationMode("FIXED")}
                    >
                      Fixed Amount Mode
                    </button>
                    <button
                      type="button"
                      className="mgmt-btn secondary"
                      style={{ padding: "0.3rem 0.6rem", fontSize: "0.75rem" }}
                      onClick={handleAddInstallment}
                    >
                      <Plus size={14} /> Add Stage
                    </button>
                  </div>
                </div>

                {/* Stages List */}
                {createPlanForm.installments.map((stage, idx) => (
                  <div
                    key={idx}
                    style={{
                      background: "#f8fafc",
                      border: "1px solid #e2e8f0",
                      borderRadius: "8px",
                      padding: "1rem",
                      marginBottom: "0.75rem",
                    }}
                  >
                    <div
                      style={{
                        display: "flex",
                        justifyContent: "space-between",
                        marginBottom: "0.5rem",
                      }}
                    >
                      <strong>Stage {stage.installmentNumber}</strong>
                      {createPlanForm.installments.length > 1 && (
                        <button
                          type="button"
                          onClick={() => handleRemoveInstallment(idx)}
                          style={{
                            background: "none",
                            border: "none",
                            color: "#dc2626",
                            cursor: "pointer",
                            fontSize: "0.8rem",
                          }}
                        >
                          <Trash2 size={14} /> Remove
                        </button>
                      )}
                    </div>

                    <div
                      style={{
                        display: "grid",
                        gridTemplateColumns: "2fr 1fr 1fr",
                        gap: "0.75rem",
                      }}
                    >
                      <div>
                        <label style={{ fontSize: "0.75rem", color: "#64748b" }}>Stage Title</label>
                        <input
                          type="text"
                          required
                          value={stage.title}
                          onChange={(e) => handleStageFieldChange(idx, "title", e.target.value)}
                        />
                      </div>

                      {planAllocationMode === "PERCENTAGE" ? (
                        <div>
                          <label style={{ fontSize: "0.75rem", color: "#64748b" }}>
                            Percentage (%)
                          </label>
                          <input
                            type="number"
                            step="0.01"
                            min="1"
                            max="100"
                            required
                            value={stage.percentage}
                            onChange={(e) =>
                              handleStageFieldChange(idx, "percentage", e.target.value)
                            }
                          />
                        </div>
                      ) : (
                        <div>
                          <label style={{ fontSize: "0.75rem", color: "#64748b" }}>
                            Amount (₹)
                          </label>
                          <input
                            type="number"
                            step="0.01"
                            min="1"
                            required
                            value={stage.amount}
                            onChange={(e) =>
                              handleStageFieldChange(idx, "amount", e.target.value)
                            }
                          />
                        </div>
                      )}

                      <div>
                        <label style={{ fontSize: "0.75rem", color: "#64748b" }}>
                          Planned Date
                        </label>
                        <input
                          type="date"
                          value={stage.plannedReleaseDate}
                          onChange={(e) =>
                            handleStageFieldChange(idx, "plannedReleaseDate", e.target.value)
                          }
                        />
                      </div>
                    </div>
                  </div>
                ))}

                {/* Allocation Summary Card */}
                <div
                  style={{
                    background: installmentAllocationSummary.isValid ? "#f0fdf4" : "#fef2f2",
                    border: `1px solid ${installmentAllocationSummary.isValid ? "#bbf7d0" : "#fecaca"
                      }`,
                    borderRadius: "6px",
                    padding: "0.75rem 1rem",
                    marginBottom: "1rem",
                    fontSize: "0.85rem",
                  }}
                >
                  <div style={{ display: "flex", justifyContent: "space-between" }}>
                    <span>
                      Allocated Total:{" "}
                      <strong>
                        {planAllocationMode === "PERCENTAGE"
                          ? `${installmentAllocationSummary.totalPct}%`
                          : formatCurrency(installmentAllocationSummary.totalAmount)}
                      </strong>
                    </span>
                    <span>
                      Required Total:{" "}
                      <strong>
                        {planAllocationMode === "PERCENTAGE"
                          ? "100%"
                          : formatCurrency(installmentAllocationSummary.approvedAmount)}
                      </strong>
                    </span>
                  </div>
                  {!installmentAllocationSummary.isValid && (
                    <div style={{ color: "#dc2626", marginTop: "0.25rem", fontWeight: 600 }}>
                      {planAllocationMode === "PERCENTAGE"
                        ? `Difference: ${installmentAllocationSummary.diff > 0 ? "+" : ""}${installmentAllocationSummary.diff
                        }% remaining to allocate.`
                        : `Difference: ${formatCurrency(installmentAllocationSummary.diff)}.`}
                    </div>
                  )}
                </div>

                {/* Plan Notes */}
                <div className="mgmt-form-group">
                  <label>Administrative Notes</label>
                  <textarea
                    rows="2"
                    placeholder="Optional administrative guidance or disbursement conditions…"
                    value={createPlanForm.notes}
                    onChange={(e) =>
                      setCreatePlanForm({ ...createPlanForm, notes: e.target.value })
                    }
                  />
                </div>
              </div>

              <div className="mgmt-modal__footer">
                <button
                  type="button"
                  className="mgmt-btn secondary"
                  onClick={() => setShowCreatePlanModal(false)}
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="mgmt-btn primary"
                  disabled={planSubmitting || !installmentAllocationSummary.isValid}
                >
                  {planSubmitting ? "Creating Plan…" : "Create Disbursement Plan"}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* ========================================================================= */}
      {/* MODAL: VIEW PLAN DETAILS                                                  */}
      {/* ========================================================================= */}
      {showPlanDetailsModal && (
        <div className="mgmt-modal-overlay" onClick={() => setShowPlanDetailsModal(null)}>
          <div
            className="mgmt-modal large"
            onClick={(e) => e.stopPropagation()}
            role="dialog"
            aria-modal="true"
          >
            <div className="mgmt-modal__header">
              <h2>Plan Details: #{showPlanDetailsModal.id}</h2>
              <button
                onClick={() => setShowPlanDetailsModal(null)}
                style={{ background: "none", border: "none", cursor: "pointer" }}
              >
                <X size={20} />
              </button>
            </div>

            <div className="mgmt-modal__body">
              <div
                style={{
                  display: "grid",
                  gridTemplateColumns: "1fr 1fr",
                  gap: "1rem",
                  marginBottom: "1.25rem",
                  background: "#f8fafc",
                  padding: "1rem",
                  borderRadius: "8px",
                }}
              >
                <div>
                  <strong>Scheme:</strong> {showPlanDetailsModal.schemeName}
                </div>
                <div>
                  <strong>Beneficiary:</strong> {showPlanDetailsModal.beneficiaryName}
                </div>
                <div>
                  <strong>Approved Grant:</strong>{" "}
                  {formatCurrency(showPlanDetailsModal.totalApprovedAmount)}
                </div>
                <div>
                  <strong>Disbursed So Far:</strong>{" "}
                  {formatCurrency(showPlanDetailsModal.totalDisbursedAmount)}
                </div>
                <div>
                  <strong>Plan Status:</strong> {showPlanDetailsModal.planStatus}
                </div>
                <div>
                  <strong>Created At:</strong> {formatDate(showPlanDetailsModal.createdAt)}
                </div>
              </div>

              <h3>Configured Stages &amp; Installments</h3>
              <div className="mgmt-table-wrap">
                <table className="mgmt-table">
                  <thead>
                    <tr>
                      <th>Stage</th>
                      <th>Share</th>
                      <th>Amount</th>
                      <th>Planned Date</th>
                      <th>Status</th>
                      <th>Milestones</th>
                    </tr>
                  </thead>
                  <tbody>
                    {(showPlanDetailsModal.installments || []).map((inst) => (
                      <tr key={inst.id}>
                        <td>
                          <strong>Stage {inst.installmentNumber}</strong>
                          <div>{inst.title}</div>
                        </td>
                        <td>{inst.percentage}%</td>
                        <td>{formatCurrency(inst.amount)}</td>
                        <td>{formatDate(inst.plannedReleaseDate)}</td>
                        <td>
                          <span
                            className={`mgmt-status-badge status-${inst.status?.toLowerCase()}`}
                          >
                            {inst.status}
                          </span>
                        </td>
                        <td>
                          {(inst.milestones || []).length === 0 ? (
                            <span style={{ color: "#94a3b8" }}>None</span>
                          ) : (
                            (inst.milestones || []).map((m) => (
                              <div key={m.id} style={{ fontSize: "0.8rem" }}>
                                <strong>{m.title}</strong>: {m.progressPercentage}%
                              </div>
                            ))
                          )}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>

            <div className="mgmt-modal__footer">
              <button
                className="mgmt-btn secondary"
                onClick={() => setShowPlanDetailsModal(null)}
              >
                Close
              </button>
            </div>
          </div>
        </div>
      )}

      {/* ========================================================================= */}
      {/* MODAL: RELEASE PAYMENT                                                    */}
      {/* ========================================================================= */}
      {showDisburseModal && (
        <div className="mgmt-modal-overlay" onClick={() => setShowDisburseModal(null)}>
          <div
            className="mgmt-modal"
            onClick={(e) => e.stopPropagation()}
            role="dialog"
            style={{ maxHeight: "90vh", display: "flex", flexDirection: "column", overflow: "hidden" }}
          >
            <div className="mgmt-modal__header" style={{ flexShrink: 0 }}>
              <h2>Release Installment Payment</h2>
              <button
                onClick={() => setShowDisburseModal(null)}
                style={{ background: "none", border: "none", cursor: "pointer" }}
              >
                <X size={20} />
              </button>
            </div>

            <form
              onSubmit={handleDisburseSubmit}
              style={{ display: "flex", flexDirection: "column", flex: 1, minHeight: 0, overflow: "hidden" }}
            >
              <div
                className="mgmt-modal__body"
                style={{ overflowY: "auto", flex: 1, minHeight: 0, padding: "1.5rem" }}
              >
                <div
                  style={{
                    background: "#f0fdf4",
                    border: "1px solid #bbf7d0",
                    padding: "1rem",
                    borderRadius: "6px",
                    marginBottom: "1.25rem",
                  }}
                >
                  <div>
                    <strong>Stage:</strong> Stage {showDisburseModal.installmentNumber} -{" "}
                    {showDisburseModal.title}
                  </div>
                  <div>
                    <strong>Beneficiary:</strong> {showDisburseModal.beneficiaryName}
                  </div>
                  <div>
                    <strong>Release Amount:</strong>{" "}
                    <span style={{ fontSize: "1.2rem", fontWeight: 700, color: "#166534" }}>
                      {formatCurrency(showDisburseModal.amount)}
                    </span>
                  </div>
                </div>

                <div className="mgmt-form-group">
                  <label>Payment Method *</label>
                  <select
                    value={disburseForm.paymentMethod}
                    onChange={(e) =>
                      setDisburseForm({ ...disburseForm, paymentMethod: e.target.value })
                    }
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
                    onChange={(e) =>
                      setDisburseForm({ ...disburseForm, paymentReference: e.target.value })
                    }
                  />
                </div>

                <div className="mgmt-form-group">
                  <label>Disbursement Date *</label>
                  <input
                    type="date"
                    required
                    value={disburseForm.disbursementDate}
                    onChange={(e) =>
                      setDisburseForm({ ...disburseForm, disbursementDate: e.target.value })
                    }
                  />
                </div>

                <div className="mgmt-form-group">
                  <label>Disbursement Remarks</label>
                  <textarea
                    rows="2"
                    placeholder="Optional transaction reference or audit notes…"
                    value={disburseForm.disbursementRemarks}
                    onChange={(e) =>
                      setDisburseForm({
                        ...disburseForm,
                        disbursementRemarks: e.target.value,
                      })
                    }
                  />
                </div>
              </div>

              <div className="mgmt-modal__footer" style={{ flexShrink: 0 }}>
                <button
                  type="button"
                  className="mgmt-btn secondary"
                  onClick={() => setShowDisburseModal(null)}
                >
                  Cancel
                </button>
                <button type="submit" className="mgmt-btn success">
                  Confirm &amp; Disburse Funds
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* ========================================================================= */}
      {/* MODAL: ADD COMPLIANCE REQUIREMENT                                         */}
      {/* ========================================================================= */}
      {showAddReqModal && (
        <div className="mgmt-modal-overlay" onClick={() => setShowAddReqModal(false)}>
          <div
            className="mgmt-modal"
            onClick={(e) => e.stopPropagation()}
            role="dialog"
            style={{ maxHeight: "90vh", display: "flex", flexDirection: "column", overflow: "hidden" }}
          >
            <div className="mgmt-modal__header" style={{ flexShrink: 0 }}>
              <h2>Add Scheme Compliance Requirement</h2>
              <button
                onClick={() => setShowAddReqModal(false)}
                style={{ background: "none", border: "none", cursor: "pointer" }}
              >
                <X size={20} />
              </button>
            </div>

            <form
              onSubmit={handleAddRequirementSubmit}
              style={{ display: "flex", flexDirection: "column", flex: 1, minHeight: 0, overflow: "hidden" }}
            >
              <div
                className="mgmt-modal__body"
                style={{ overflowY: "auto", flex: 1, minHeight: 0, padding: "1.5rem" }}
              >
                <div className="mgmt-form-group">
                  <label>Target Scheme *</label>
                  <select
                    required
                    value={newReqForm.schemeId}
                    onChange={(e) =>
                      setNewReqForm({ ...newReqForm, schemeId: e.target.value })
                    }
                  >
                    <option value="">-- Choose scheme --</option>
                    {schemes.map((s) => (
                      <option key={s.id} value={s.id}>
                        {s.schemeName} ({s.department || "General"})
                      </option>
                    ))}
                  </select>
                </div>

                <div className="mgmt-form-group">
                  <label>Target Stage *</label>
                  <select
                    required
                    value={newReqForm.targetStage}
                    onChange={(e) =>
                      setNewReqForm({ ...newReqForm, targetStage: e.target.value })
                    }
                  >
                    <option value="">-- Choose target stage --</option>
                    <option value="1">Stage 1</option>
                    <option value="2">Stage 2</option>
                    <option value="3">Stage 3</option>
                  </select>
                </div>

                <div className="mgmt-form-group">
                  <label>Requirement Title *</label>
                  <input
                    type="text"
                    required
                    placeholder="e.g. Geo-tagged Construction Site Photo, Utilisation Certificate"
                    value={newReqForm.title}
                    onChange={(e) =>
                      setNewReqForm({ ...newReqForm, title: e.target.value })
                    }
                  />
                </div>

                <div className="mgmt-form-group">
                  <label>Description &amp; Guidelines</label>
                  <textarea
                    rows="2"
                    placeholder="Instructions for the beneficiary regarding acceptable evidence…"
                    value={newReqForm.description}
                    onChange={(e) =>
                      setNewReqForm({ ...newReqForm, description: e.target.value })
                    }
                  />
                </div>

                <div className="mgmt-form-group">
                  <label style={{ display: "flex", alignItems: "center", gap: "0.5rem" }}>
                    <input
                      type="checkbox"
                      checked={newReqForm.required}
                      onChange={(e) =>
                        setNewReqForm({ ...newReqForm, required: e.target.checked })
                      }
                      style={{ width: "auto" }}
                    />
                    Mandatory for milestone completion and payment release
                  </label>
                </div>

                <div className="mgmt-form-group">
                  <label>Submission Deadline (Optional)</label>
                  <input
                    type="date"
                    value={newReqForm.submissionDeadline}
                    onChange={(e) =>
                      setNewReqForm({ ...newReqForm, submissionDeadline: e.target.value })
                    }
                  />
                </div>

                <div
                  style={{
                    display: "grid",
                    gridTemplateColumns: "1fr 1fr",
                    gap: "1rem",
                  }}
                >
                  <div className="mgmt-form-group">
                    <label>Allowed File Formats *</label>
                    <input
                      type="text"
                      required
                      value={newReqForm.allowedFormats}
                      onChange={(e) =>
                        setNewReqForm({ ...newReqForm, allowedFormats: e.target.value })
                      }
                      placeholder="pdf,png,jpg,jpeg"
                    />
                  </div>
                  <div className="mgmt-form-group">
                    <label>Max File Size (Bytes) *</label>
                    <input
                      type="number"
                      required
                      min={1024}
                      max={10485760}
                      value={newReqForm.maxFileSizeBytes}
                      onChange={(e) =>
                        setNewReqForm({
                          ...newReqForm,
                          maxFileSizeBytes: e.target.value,
                        })
                      }
                    />
                  </div>
                </div>
              </div>

              <div className="mgmt-modal__footer" style={{ flexShrink: 0 }}>
                <button
                  type="button"
                  className="mgmt-btn secondary"
                  onClick={() => setShowAddReqModal(false)}
                >
                  Cancel
                </button>
                <button type="submit" className="mgmt-btn primary">
                  Save Requirement
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* ========================================================================= */}
      {/* MODAL: VERIFY / WAIVE COMPLIANCE                                          */}
      {/* ========================================================================= */}
      {showVerifyModal && (
        <div className="mgmt-modal-overlay" onClick={() => setShowVerifyModal(null)}>
          <div
            className="mgmt-modal"
            onClick={(e) => e.stopPropagation()}
            role="dialog"
            style={{ maxHeight: "90vh", display: "flex", flexDirection: "column", overflow: "hidden" }}
          >
            <div className="mgmt-modal__header" style={{ flexShrink: 0 }}>
              <h2>Admin Compliance Waiver</h2>
              <button
                onClick={() => setShowVerifyModal(null)}
                style={{ background: "none", border: "none", cursor: "pointer" }}
              >
                <X size={20} />
              </button>
            </div>

            <form
              onSubmit={handleVerifySubmit}
              style={{ display: "flex", flexDirection: "column", flex: 1, minHeight: 0, overflow: "hidden" }}
            >
              <div
                className="mgmt-modal__body"
                style={{ overflowY: "auto", flex: 1, minHeight: 0, padding: "1.5rem" }}
              >
                <div
                  style={{
                    background: "#f8fafc",
                    padding: "1rem",
                    borderRadius: "6px",
                    marginBottom: "1rem",
                  }}
                >
                  <div>
                    <strong>Requirement:</strong> {showVerifyModal.requirementTitle}
                  </div>
                  <div>
                    <strong>Beneficiary:</strong> {showVerifyModal.beneficiaryName}
                  </div>
                  <div>
                    <strong>Milestone:</strong> {showVerifyModal.milestoneTitle}
                  </div>
                  {showVerifyModal.hasDocument && (
                    <div style={{ marginTop: "0.5rem" }}>
                      <button
                        type="button"
                        className="mgmt-btn secondary"
                        style={{ padding: "0.25rem 0.5rem", fontSize: "0.8rem" }}
                        onClick={() => handleDownloadDoc(showVerifyModal)}
                      >
                        <Download size={14} /> Download Submitted Document
                      </button>
                    </div>
                  )}
                </div>

                <div className="mgmt-form-group">
                  <label>Action</label>
                  <input
                    type="text"
                    readOnly
                    value={
                      verifyForm.decision === "REOPEN"
                        ? "REOPEN — Restart Compliance Review Cycle at Field"
                        : "WAIVED — Administrator Discretionary Waiver"
                    }
                  />
                </div>

                <div className="mgmt-form-group">
                  <label>Justification / Audit Remarks *</label>
                  <textarea
                    rows="3"
                    required
                    placeholder="Enter mandatory justification for waiving this compliance requirement…"
                    value={verifyForm.remarks}
                    onChange={(e) =>
                      setVerifyForm({ ...verifyForm, remarks: e.target.value })
                    }
                  />
                </div>
              </div>

              <div className="mgmt-modal__footer" style={{ flexShrink: 0 }}>
                <button
                  type="button"
                  className="mgmt-btn secondary"
                  onClick={() => setShowVerifyModal(null)}
                >
                  Cancel
                </button>
                <button type="submit" className="mgmt-btn primary">
                  Confirm Waiver
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
