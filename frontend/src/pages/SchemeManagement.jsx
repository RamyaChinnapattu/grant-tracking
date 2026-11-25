import { useEffect, useId, useMemo, useRef, useState } from "react";
import {
  AlertCircle,
  AlertTriangle,
  ArrowDown,
  ArrowUp,
  Calendar,
  Check,
  CheckCircle2,
  DollarSign,
  Edit2,
  Eye,
  FileText,
  Filter,
  HelpCircle,
  Layers,
  Plus,
  Power,
  RefreshCw,
  Search,
  Sparkles,
  Trash2,
  Users,
  X,
} from "lucide-react";
import {
  createScheme,
  deleteScheme,
  getSchemeById,
  getSchemes,
  updateScheme,
} from "../services/schemeService";
import { PageHeader } from "../components/ui/SaaSPrimitives";
import "../styles/SchemeManagement.css";

const STANDARD_CATEGORIES = [
  "GENERAL",
  "OBC",
  "SC",
  "ST",
  "EWS",
  "MINORITY",
  "OTHER",
];

const emptyQuestion = (type = "SINGLE_CHOICE", defaultWeight = 20) => ({
  questionText: "",
  questionType: type,
  required: true,
  maximumPoints: String(defaultWeight),
  answerOptions:
    type === "YES_NO"
      ? [
          { value: "YES", label: "Yes", points: String(defaultWeight), meetsEligibilityCondition: true },
          { value: "NO", label: "No", points: "0", meetsEligibilityCondition: false },
        ]
      : [
          { value: "", label: "", points: String(defaultWeight), meetsEligibilityCondition: true },
          { value: "", label: "", points: "0", meetsEligibilityCondition: false },
        ],
});

const defaultInitialQuestions = [
  {
    questionText: "Are you a permanent resident of India?",
    questionType: "YES_NO",
    required: true,
    maximumPoints: "50",
    answerOptions: [
      { value: "YES", label: "Yes", points: "50", meetsEligibilityCondition: true },
      { value: "NO", label: "No", points: "0", meetsEligibilityCondition: false },
    ],
  },
  {
    questionText: "Do you own or legally lease agricultural land or relevant facility?",
    questionType: "YES_NO",
    required: true,
    maximumPoints: "50",
    answerOptions: [
      { value: "YES", label: "Yes", points: "50", meetsEligibilityCondition: true },
      { value: "NO", label: "No", points: "0", meetsEligibilityCondition: false },
    ],
  },
];

const emptyForm = {
  schemeName: "",
  description: "",
  department: "",
  minimumAge: "18",
  maximumAge: "65",
  maximumAnnualIncome: "500000",
  allocatedFunds: "",
  maximumGrantAmount: "",
  eligibilityThreshold: "70",
  eligibilityConfigurationVersion: 1,
  eligibleCategories: ["GENERAL", "OBC", "SC", "ST", "EWS", "MINORITY", "OTHER"],
  eligibilityQuestions: defaultInitialQuestions,
  applicationStartDate: "",
  applicationEndDate: "",
  status: "DRAFT",
};

const numericFields = [
  "minimumAge",
  "maximumAge",
  "maximumAnnualIncome",
  "allocatedFunds",
  "maximumGrantAmount",
  "eligibilityThreshold",
];

function toFormValues(scheme) {
  const questions = scheme.eligibilityQuestions || [];
  return {
    ...emptyForm,
    schemeName: scheme.schemeName || "",
    description: scheme.description || "",
    department: scheme.department || "",
    minimumAge: scheme.minimumAge != null ? String(scheme.minimumAge) : "",
    maximumAge: scheme.maximumAge != null ? String(scheme.maximumAge) : "",
    maximumAnnualIncome:
      scheme.maximumAnnualIncome != null ? String(scheme.maximumAnnualIncome) : "",
    allocatedFunds:
      scheme.allocatedFunds != null ? String(scheme.allocatedFunds) : "",
    maximumGrantAmount:
      scheme.maximumGrantAmount != null ? String(scheme.maximumGrantAmount) : "",
    eligibilityThreshold:
      scheme.eligibilityThreshold != null ? String(scheme.eligibilityThreshold) : "70",
    eligibilityConfigurationVersion:
      scheme.eligibilityConfigurationVersion != null
        ? scheme.eligibilityConfigurationVersion
        : null,
    eligibleCategories: Array.isArray(scheme.eligibleCategories)
      ? scheme.eligibleCategories
      : [],
    applicationStartDate: scheme.applicationStartDate || "",
    applicationEndDate: scheme.applicationEndDate || "",
    status: scheme.status || "DRAFT",
    eligibilityQuestions: questions.map((question) => ({
      id: question.id, // Preserved for existing questions
      questionText: question.questionText || "",
      questionType: question.questionType || "SINGLE_CHOICE",
      required: question.required !== false,
      maximumPoints:
        question.maximumPoints == null ? "" : String(question.maximumPoints),
      answerOptions: (question.answerOptions || []).map((option) => ({
        value: option.value || "",
        label: option.label || "",
        points: option.points == null ? "" : String(option.points),
        meetsEligibilityCondition: Boolean(option.meetsEligibilityCondition),
      })),
    })),
  };
}

function toPayload(form) {
  const payload = {
    schemeName: form.schemeName.trim(),
    description: form.description ? form.description.trim() : null,
    department: form.department ? form.department.trim() : null,
    minimumAge: form.minimumAge === "" ? null : Number(form.minimumAge),
    maximumAge: form.maximumAge === "" ? null : Number(form.maximumAge),
    maximumAnnualIncome:
      form.maximumAnnualIncome === "" ? null : Number(form.maximumAnnualIncome),
    allocatedFunds:
      form.allocatedFunds === "" ? null : Number(form.allocatedFunds),
    maximumGrantAmount:
      form.maximumGrantAmount === "" ? null : Number(form.maximumGrantAmount),
    eligibilityThreshold:
      form.eligibilityThreshold === "" ? 70 : Number(form.eligibilityThreshold),
    eligibilityConfigurationVersion:
      form.eligibilityConfigurationVersion != null && Number(form.eligibilityConfigurationVersion) > 0
        ? Number(form.eligibilityConfigurationVersion)
        : null,
    eligibleCategories: (form.eligibleCategories || [])
      .map((cat) => cat.trim())
      .filter(Boolean),
    applicationStartDate: form.applicationStartDate || null,
    applicationEndDate: form.applicationEndDate || null,
    status: form.status || "DRAFT",
  };

  payload.eligibilityQuestions = (form.eligibilityQuestions || []).map(
    (question) => {
      const q = {
        questionText: question.questionText.trim(),
        questionType: question.questionType,
        required: Boolean(question.required),
        maximumPoints:
          question.maximumPoints === "" ? null : Number(question.maximumPoints),
        answerOptions: (question.answerOptions || []).map((option) => ({
          value: option.value.trim(),
          label: option.label.trim(),
          points: option.points === "" ? null : Number(option.points),
          meetsEligibilityCondition: Boolean(option.meetsEligibilityCondition),
        })),
      };
      if (question.id != null) {
        q.id = question.id;
      }
      return q;
    }
  );

  return payload;
}

function formatCurrency(amount) {
  if (amount == null || amount === "" || isNaN(amount)) return "—";
  return new Intl.NumberFormat("en-IN", {
    style: "currency",
    currency: "INR",
    maximumFractionDigits: 0,
  }).format(amount);
}

function formatDate(dateStr) {
  if (!dateStr) return "—";
  try {
    const d = new Date(dateStr);
    if (isNaN(d.getTime())) return dateStr;
    return d.toLocaleDateString("en-IN", {
      year: "numeric",
      month: "short",
      day: "numeric",
    });
  } catch {
    return dateStr;
  }
}

function SchemeManagement() {
  const formRef = useRef(null);
  const customCatInputRef = useRef(null);

  const [schemes, setSchemes] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  // Form states
  const [form, setForm] = useState(emptyForm);
  const [editingId, setEditingId] = useState(null);
  const [saving, setSaving] = useState(false);
  const [deletingId, setDeletingId] = useState(null);
  const [actionError, setActionError] = useState("");
  const [successMessage, setSuccessMessage] = useState("");

  // Search & Filtering
  const [searchQuery, setSearchQuery] = useState("");
  const [statusFilter, setStatusFilter] = useState("ALL");

  // Details drawer
  const [selectedScheme, setSelectedScheme] = useState(null);
  const [detailsLoading, setDetailsLoading] = useState(false);
  const [detailsError, setDetailsError] = useState("");

  // Custom category text input
  const [customCategory, setCustomCategory] = useState("");

  const loadSchemes = async () => {
    setLoading(true);
    setError("");
    try {
      const response = await getSchemes();
      setSchemes(Array.isArray(response.data) ? response.data : []);
    } catch (err) {
      setError(
        err.response?.data?.message ||
          err.response?.data?.error ||
          "Unable to load schemes. Please ensure the backend server is running."
      );
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadSchemes();
  }, []);

  // Filtered schemes for administrator table
  const filteredSchemes = useMemo(() => {
    return schemes.filter((scheme) => {
      const matchesSearch =
        searchQuery.trim() === "" ||
        (scheme.schemeName &&
          scheme.schemeName.toLowerCase().includes(searchQuery.toLowerCase())) ||
        (scheme.department &&
          scheme.department.toLowerCase().includes(searchQuery.toLowerCase()));

      const matchesStatus =
        statusFilter === "ALL" ||
        (scheme.status && scheme.status.toUpperCase() === statusFilter);

      return matchesSearch && matchesStatus;
    });
  }, [schemes, searchQuery, statusFilter]);

  // Status counts
  const counts = useMemo(() => {
    const active = schemes.filter((s) => s.status === "ACTIVE").length;
    const draft = schemes.filter((s) => s.status === "DRAFT").length;
    const inactive = schemes.filter((s) => s.status === "INACTIVE").length;
    return { all: schemes.length, active, draft, inactive };
  }, [schemes]);

  // Live total maximum weight across questions
  const totalWeight = useMemo(() => {
    return (form.eligibilityQuestions || []).reduce(
      (sum, q) => sum + (Number(q.maximumPoints) || 0),
      0
    );
  }, [form.eligibilityQuestions]);

  const isWeightValid = Math.abs(totalWeight - 100) < 0.001;

  // Live date validation
  const dateError = useMemo(() => {
    if (form.applicationStartDate && form.applicationEndDate) {
      if (form.applicationEndDate < form.applicationStartDate) {
        return "Application end date cannot be earlier than application start date.";
      }
    }
    return "";
  }, [form.applicationStartDate, form.applicationEndDate]);

  // Live budget & grant amount validation
  const allocatedFundsError = useMemo(() => {
    if (form.allocatedFunds === "") return "";
    const num = Number(form.allocatedFunds);
    if (isNaN(num) || num <= 0) {
      return "Total scheme budget must be greater than zero.";
    }
    return "";
  }, [form.allocatedFunds]);

  const grantAmountError = useMemo(() => {
    if (form.maximumGrantAmount !== "" && Number(form.maximumGrantAmount) < 0) {
      return "Maximum grant amount must be zero or greater.";
    }
    return "";
  }, [form.maximumGrantAmount]);

  // Handle standard field changes
  const handleChange = (e) => {
    const { name, value } = e.target;
    setForm((curr) => ({ ...curr, [name]: value }));
  };

  // Toggle standardized category
  const toggleCategory = (cat) => {
    setForm((curr) => {
      const currentCats = curr.eligibleCategories || [];
      if (currentCats.includes(cat)) {
        return {
          ...curr,
          eligibleCategories: currentCats.filter((c) => c !== cat),
        };
      } else {
        return {
          ...curr,
          eligibleCategories: [...currentCats, cat],
        };
      }
    });
  };

  const handleSelectAllCategories = () => {
    setForm((curr) => ({
      ...curr,
      eligibleCategories: [...STANDARD_CATEGORIES],
    }));
  };

  const handleClearCategories = () => {
    setForm((curr) => ({
      ...curr,
      eligibleCategories: [],
    }));
  };

  const handleAddCustomCategory = (e) => {
    e.preventDefault();
    const trimmed = customCategory.trim().toUpperCase();
    if (trimmed && !form.eligibleCategories.includes(trimmed)) {
      setForm((curr) => ({
        ...curr,
        eligibleCategories: [...curr.eligibleCategories, trimmed],
      }));
      setCustomCategory("");
    }
  };

  // Question Management
  const updateQuestion = (qIndex, updates) => {
    setForm((curr) => ({
      ...curr,
      eligibilityQuestions: curr.eligibilityQuestions.map((q, idx) =>
        idx === qIndex ? { ...q, ...updates } : q
      ),
    }));
  };

  const handleQuestionTypeChange = (qIndex, newType) => {
    const targetQ = form.eligibilityQuestions[qIndex];
    const weight = targetQ.maximumPoints || "20";
    updateQuestion(qIndex, {
      questionType: newType,
      answerOptions:
        newType === "YES_NO"
          ? [
              { value: "YES", label: "Yes", points: weight, meetsEligibilityCondition: true },
              { value: "NO", label: "No", points: "0", meetsEligibilityCondition: false },
            ]
          : [
              { value: "", label: "", points: weight, meetsEligibilityCondition: true },
              { value: "", label: "", points: "0", meetsEligibilityCondition: false },
            ],
    });
  };

  const updateAnswerOption = (qIndex, optIndex, field, val) => {
    const targetQ = form.eligibilityQuestions[qIndex];
    const newOptions = targetQ.answerOptions.map((opt, idx) => {
      if (idx !== optIndex) return opt;
      const updated = { ...opt, [field]: val };
      // Auto-populate value from label if value was blank and editing label
      if (
        field === "label" &&
        (!opt.value || opt.value === opt.label.toUpperCase().replace(/\s+/g, "_"))
      ) {
        updated.value = val.trim().toUpperCase().replace(/\s+/g, "_");
      }
      return updated;
    });
    updateQuestion(qIndex, { answerOptions: newOptions });
  };

  const addAnswerOption = (qIndex) => {
    const targetQ = form.eligibilityQuestions[qIndex];
    updateQuestion(qIndex, {
      answerOptions: [
        ...targetQ.answerOptions,
        { value: "", label: "", points: "0", meetsEligibilityCondition: false },
      ],
    });
  };

  const removeAnswerOption = (qIndex, optIndex) => {
    const targetQ = form.eligibilityQuestions[qIndex];
    if (targetQ.answerOptions.length <= 2) return;
    updateQuestion(qIndex, {
      answerOptions: targetQ.answerOptions.filter((_, idx) => idx !== optIndex),
    });
  };

  const addQuestion = () => {
    // Determine remaining points to reach 100
    const remaining = Math.max(0, 100 - totalWeight);
    const weightToUse = remaining > 0 ? remaining : 20;
    setForm((curr) => ({
      ...curr,
      eligibilityQuestions: [
        ...curr.eligibilityQuestions,
        emptyQuestion("SINGLE_CHOICE", weightToUse),
      ],
    }));
  };

  const removeQuestion = (qIndex) => {
    setForm((curr) => {
      if (curr.eligibilityQuestions.length <= 1) {
        alert("A scheme must contain at least one eligibility question.");
        return curr;
      }
      return {
        ...curr,
        eligibilityQuestions: curr.eligibilityQuestions.filter(
          (_, idx) => idx !== qIndex
        ),
      };
    });
  };

  const moveQuestion = (qIndex, direction) => {
    setForm((curr) => {
      const items = [...curr.eligibilityQuestions];
      const targetIdx = qIndex + direction;
      if (targetIdx < 0 || targetIdx >= items.length) return curr;
      [items[qIndex], items[targetIdx]] = [items[targetIdx], items[qIndex]];
      return { ...curr, eligibilityQuestions: items };
    });
  };

  const resetForm = () => {
    setForm(emptyForm);
    setEditingId(null);
    setActionError("");
    setSuccessMessage("");
  };

  // Form Submit (Create or Update)
  const handleSubmit = async (e) => {
    e.preventDefault();
    setActionError("");
    setSuccessMessage("");

    // Client-side validations
    if (!form.schemeName.trim()) {
      setActionError("Please provide a Scheme Name.");
      return;
    }

    if (dateError) {
      setActionError(dateError);
      return;
    }

    if (form.allocatedFunds === "" || form.allocatedFunds == null) {
      setActionError("Please enter a valid positive Total Scheme Budget (₹).");
      return;
    }

    if (allocatedFundsError) {
      setActionError(allocatedFundsError);
      return;
    }

    if (grantAmountError) {
      setActionError(grantAmountError);
      return;
    }

    if (!form.eligibleCategories || form.eligibleCategories.length === 0) {
      setActionError("Please select at least one eligible beneficiary category.");
      return;
    }

    if (!form.eligibilityQuestions || form.eligibilityQuestions.length === 0) {
      setActionError("Please configure at least one eligibility question.");
      return;
    }

    if (!isWeightValid) {
      setActionError(
        `Question maximum weights must total exactly 100 points (currently ${totalWeight} points).`
      );
      return;
    }

    // Check individual option points
    for (let i = 0; i < form.eligibilityQuestions.length; i++) {
      const q = form.eligibilityQuestions[i];
      const qMax = Number(q.maximumPoints) || 0;
      if (!q.questionText.trim()) {
        setActionError(`Question ${i + 1} text cannot be blank.`);
        return;
      }
      for (let j = 0; j < q.answerOptions.length; j++) {
        const opt = q.answerOptions[j];
        if (!opt.value.trim() || !opt.label.trim()) {
          setActionError(`Question ${i + 1}, Option ${j + 1} must have a value and label.`);
          return;
        }
        if (Number(opt.points) > qMax) {
          setActionError(
            `Question ${i + 1}, Option "${opt.label}" has ${opt.points} points, which exceeds the question maximum of ${qMax} points.`
          );
          return;
        }
      }
    }

    setSaving(true);
    try {
      const payload = toPayload(form);
      const response = editingId
        ? await updateScheme(editingId, payload)
        : await createScheme(payload);

      const saved = response.data;
      setSchemes((curr) =>
        editingId
          ? curr.map((s) => (s.id === editingId ? saved : s))
          : [saved, ...curr]
      );
      if (selectedScheme?.id === saved.id) {
        setSelectedScheme(saved);
      }
      setSuccessMessage(
        editingId
          ? `Scheme "${saved.schemeName}" updated successfully.`
          : `Scheme "${saved.schemeName}" created successfully.`
      );
      resetForm();
    } catch (err) {
      setActionError(
        err.response?.data?.message ||
          err.response?.data?.error ||
          `Unable to ${editingId ? "update" : "create"} scheme.`
      );
    } finally {
      setSaving(false);
    }
  };

  // Load scheme for editing
  const handleEdit = (scheme) => {
    setForm(toFormValues(scheme));
    setEditingId(scheme.id);
    setActionError("");
    setSuccessMessage("");
    if (formRef.current) {
      formRef.current.scrollIntoView({ behavior: "smooth", block: "start" });
    }
  };

  // Quick 1-click status toggle (Activate / Deactivate)
  const handleToggleStatus = async (scheme) => {
    const nextStatus = scheme.status === "ACTIVE" ? "INACTIVE" : "ACTIVE";
    setActionError("");
    setSuccessMessage("");

    try {
      const updated = {
        ...scheme,
        status: nextStatus,
      };
      const response = await updateScheme(scheme.id, updated);
      const saved = response.data;
      setSchemes((curr) => curr.map((s) => (s.id === scheme.id ? saved : s)));
      if (selectedScheme?.id === scheme.id) {
        setSelectedScheme(saved);
      }
      setSuccessMessage(
        `Scheme "${scheme.schemeName}" is now ${nextStatus.toLowerCase()}.`
      );
    } catch (err) {
      setActionError(
        err.response?.data?.message ||
          err.response?.data?.error ||
          "Unable to update scheme status."
      );
    }
  };

  // Delete scheme
  const handleDelete = async (scheme) => {
    if (
      !window.confirm(
        `Are you sure you want to delete "${scheme.schemeName}"?\n\nThis action cannot be undone.`
      )
    ) {
      return;
    }

    setDeletingId(scheme.id);
    setActionError("");
    setSuccessMessage("");

    try {
      await deleteScheme(scheme.id);
      setSchemes((curr) => curr.filter((s) => s.id !== scheme.id));
      if (selectedScheme?.id === scheme.id) {
        setSelectedScheme(null);
      }
      if (editingId === scheme.id) {
        resetForm();
      }
      setSuccessMessage("Scheme deleted successfully.");
    } catch (err) {
      const errorMsg =
        err.response?.data?.message ||
        err.response?.data?.error ||
        "Unable to delete scheme.";
      setActionError(errorMsg);
    } finally {
      setDeletingId(null);
    }
  };

  // View Details
  const handleViewDetails = async (id) => {
    setSelectedScheme(null);
    setDetailsError("");
    setDetailsLoading(true);
    try {
      const response = await getSchemeById(id);
      setSelectedScheme(response.data);
    } catch (err) {
      setDetailsError(
        err.response?.data?.message ||
          err.response?.data?.error ||
          "Unable to load scheme details."
      );
    } finally {
      setDetailsLoading(false);
    }
  };

  return (
    <div className="scheme-management saas-page">
      <PageHeader
        eyebrow="Program Catalog"
        title="Scheme Management"
        subtitle="Configure, publish, and maintain government grant schemes, allocated budgets, and eligibility questionnaires."
        actions={
          <button
            className="saas-btn saas-btn--secondary"
            type="button"
            onClick={loadSchemes}
            disabled={loading}
          >
            <RefreshCw size={15} className={loading ? "spin" : ""} aria-hidden="true" />
            {loading ? "Refreshing…" : "Refresh"}
          </button>
        }
      />

      {/* Global Alerts */}
      {error && (
        <div className="scheme-notice scheme-notice--error" role="alert">
          <AlertCircle size={18} aria-hidden="true" />
          <span>{error}</span>
          <button
            type="button"
            className="scheme-management__button scheme-management__button--secondary scheme-management__button--sm"
            onClick={loadSchemes}
          >
            Retry
          </button>
        </div>
      )}

      {actionError && (
        <div className="scheme-notice scheme-notice--error" role="alert">
          <AlertCircle size={18} aria-hidden="true" />
          <div className="scheme-notice__content">
            <strong>Action Failed:</strong> {actionError}
          </div>
          <button
            type="button"
            className="scheme-notice__close"
            onClick={() => setActionError("")}
            aria-label="Dismiss error"
          >
            <X size={16} aria-hidden="true" />
          </button>
        </div>
      )}

      {successMessage && (
        <div className="scheme-notice scheme-notice--success" role="status">
          <CheckCircle2 size={18} aria-hidden="true" />
          <span>{successMessage}</span>
          <button
            type="button"
            className="scheme-notice__close"
            onClick={() => setSuccessMessage("")}
            aria-label="Dismiss notification"
          >
            <X size={16} aria-hidden="true" />
          </button>
        </div>
      )}

      {/* Modern Sectioned Form */}
      <form
        ref={formRef}
        className="scheme-form-card"
        onSubmit={handleSubmit}
        noValidate
      >
        <div className="scheme-form-card__header">
          <div>
            <h3>
              {editingId ? (
                <>
                  <Edit2 size={18} aria-hidden="true" /> Edit Grant Scheme
                </>
              ) : (
                <>
                  <Plus size={18} aria-hidden="true" /> Create New Grant Scheme
                </>
              )}
            </h3>
            <p>
              {editingId
                ? `Modifying scheme ID #${editingId}. Changes will update live criteria.`
                : "Define basic info, eligibility boundaries, and dynamic scoring questions."}
            </p>
          </div>
          {editingId && (
            <button
              type="button"
              className="scheme-management__button scheme-management__button--secondary scheme-management__button--sm"
              onClick={resetForm}
            >
              Cancel Edit
            </button>
          )}
        </div>

        {/* Section 1: Basic Information */}
        <section className="scheme-form-section">
          <div className="scheme-form-section__header">
            <span className="scheme-form-section__badge">1</span>
            <div>
              <h4>Basic Information</h4>
              <p>Identify the grant scheme, nodal authority, and public description.</p>
            </div>
          </div>
          <div className="scheme-form-grid">
            <label className="scheme-field scheme-field--wide">
              <span className="scheme-field__label">
                Scheme Name <span className="scheme-field__required">*</span>
              </span>
              <input
                type="text"
                name="schemeName"
                value={form.schemeName}
                onChange={handleChange}
                placeholder="e.g. PM Surya Ghar Solar Subsidy Scheme 2026"
                maxLength="200"
                required
              />
            </label>

            <label className="scheme-field">
              <span className="scheme-field__label">Nodal Department / Ministry</span>
              <input
                type="text"
                name="department"
                value={form.department}
                onChange={handleChange}
                placeholder="e.g. Ministry of New and Renewable Energy"
                maxLength="150"
              />
            </label>

            <label className="scheme-field scheme-field--wide">
              <span className="scheme-field__label">Public Description</span>
              <textarea
                name="description"
                value={form.description}
                onChange={handleChange}
                rows="3"
                placeholder="Brief summary explaining who this scheme benefits and its core objectives..."
              />
            </label>
          </div>
        </section>

        {/* Section 2: Eligibility Criteria */}
        <section className="scheme-form-section">
          <div className="scheme-form-section__header">
            <span className="scheme-form-section__badge">2</span>
            <div>
              <h4>Eligibility Criteria</h4>
              <p>Age brackets, annual income ceilings, categories, and passing score threshold.</p>
            </div>
          </div>

          <div className="scheme-form-grid">
            <label className="scheme-field">
              <span className="scheme-field__label">
                Minimum Age (Years) <span className="scheme-field__required">*</span>
              </span>
              <input
                type="number"
                name="minimumAge"
                min="0"
                max="120"
                value={form.minimumAge}
                onChange={handleChange}
                required
              />
            </label>

            <label className="scheme-field">
              <span className="scheme-field__label">
                Maximum Age (Years) <span className="scheme-field__required">*</span>
              </span>
              <input
                type="number"
                name="maximumAge"
                min="0"
                max="120"
                value={form.maximumAge}
                onChange={handleChange}
                required
              />
            </label>

            <label className="scheme-field">
              <span className="scheme-field__label">
                Maximum Annual Income (₹) <span className="scheme-field__required">*</span>
              </span>
              <input
                type="number"
                name="maximumAnnualIncome"
                min="0"
                step="1000"
                value={form.maximumAnnualIncome}
                onChange={handleChange}
                placeholder="e.g. 500000"
                required
              />
              <span className="scheme-field__hint">
                {form.maximumAnnualIncome
                  ? `Equivalent to ${formatCurrency(form.maximumAnnualIncome)} per year`
                  : "Applicants earning above this limit are ineligible"}
              </span>
            </label>

            <label className="scheme-field">
              <span className="scheme-field__label">
                Passing Eligibility Threshold (%) <span className="scheme-field__required">*</span>
              </span>
              <input
                type="number"
                name="eligibilityThreshold"
                min="0"
                max="100"
                step="1"
                value={form.eligibilityThreshold}
                onChange={handleChange}
                required
              />
              <span className="scheme-field__hint">
                Minimum percentage score required to qualify for grant approval
              </span>
            </label>

            {/* Standardized Category Selector */}
            <div className="scheme-field scheme-field--wide">
              <div className="scheme-field__header-row">
                <span className="scheme-field__label">
                  Eligible Beneficiary Categories <span className="scheme-field__required">*</span>
                </span>
                <div className="scheme-category-actions">
                  <button
                    type="button"
                    className="scheme-text-btn"
                    onClick={handleSelectAllCategories}
                  >
                    Select All
                  </button>
                  <span className="scheme-text-sep">|</span>
                  <button
                    type="button"
                    className="scheme-text-btn"
                    onClick={handleClearCategories}
                  >
                    Clear All
                  </button>
                </div>
              </div>
              <div className="scheme-category-chips">
                {STANDARD_CATEGORIES.map((cat) => {
                  const isSelected = form.eligibleCategories.includes(cat);
                  return (
                    <button
                      type="button"
                      key={cat}
                      className={`scheme-chip ${isSelected ? "scheme-chip--selected" : ""}`}
                      onClick={() => toggleCategory(cat)}
                      aria-pressed={isSelected}
                    >
                      {isSelected && <Check size={14} aria-hidden="true" />}
                      {cat}
                    </button>
                  );
                })}
                {/* Additional non-standard categories if any were added */}
                {form.eligibleCategories
                  .filter((cat) => !STANDARD_CATEGORIES.includes(cat))
                  .map((customCat) => (
                    <button
                      type="button"
                      key={customCat}
                      className="scheme-chip scheme-chip--selected scheme-chip--custom"
                      onClick={() => toggleCategory(customCat)}
                      title="Click to remove custom category"
                    >
                      <Check size={14} aria-hidden="true" />
                      {customCat}
                      <X size={12} aria-hidden="true" />
                    </button>
                  ))}
              </div>

              {/* Add custom category input */}
              <div className="scheme-custom-category-row">
                <input
                  ref={customCatInputRef}
                  type="text"
                  placeholder="Add custom category (e.g. ARTISAN, EX_SERVICEMAN)"
                  value={customCategory}
                  onChange={(e) => setCustomCategory(e.target.value)}
                  maxLength="50"
                  onKeyDown={(e) => {
                    if (e.key === "Enter") {
                      e.preventDefault();
                      handleAddCustomCategory(e);
                    }
                  }}
                />
                <button
                  type="button"
                  className="scheme-management__button scheme-management__button--secondary scheme-management__button--sm"
                  onClick={handleAddCustomCategory}
                  disabled={!customCategory.trim()}
                >
                  <Plus size={14} aria-hidden="true" /> Add Category
                </button>
              </div>

              {form.eligibleCategories.length === 0 && (
                <span className="scheme-field__error">
                  At least one eligible category is required.
                </span>
              )}
            </div>
          </div>
        </section>

        {/* Section 3: Grant Details & Dates */}
        <section className="scheme-form-section">
          <div className="scheme-form-section__header">
            <span className="scheme-form-section__badge">3</span>
            <div>
              <h4>Grant Details & Application Window</h4>
              <p>Maximum disbursement cap and application acceptance period.</p>
            </div>
          </div>
          <div className="scheme-form-grid">
            <label className="scheme-field">
              <span className="scheme-field__label">
                Total Scheme Budget (₹) <span className="scheme-field__required">*</span>
              </span>
              <input
                type="number"
                name="allocatedFunds"
                min="1"
                step="1000"
                value={form.allocatedFunds}
                onChange={handleChange}
                placeholder="e.g. 10000000"
                required
              />
              {allocatedFundsError && (
                <span className="scheme-field__error">{allocatedFundsError}</span>
              )}
              {form.allocatedFunds && !allocatedFundsError ? (
                <span className="scheme-field__hint">
                  {formatCurrency(form.allocatedFunds)} total pool allocated for the entire scheme
                </span>
              ) : (
                !allocatedFundsError && (
                  <span className="scheme-field__hint">
                    Total scheme pool (separate from per-beneficiary Maximum Grant Amount)
                  </span>
                )
              )}
            </label>

            <label className="scheme-field">
              <span className="scheme-field__label">Maximum Grant Amount (₹)</span>
              <input
                type="number"
                name="maximumGrantAmount"
                min="0"
                step="500"
                value={form.maximumGrantAmount}
                onChange={handleChange}
                placeholder="e.g. 78000"
              />
              {grantAmountError && (
                <span className="scheme-field__error">{grantAmountError}</span>
              )}
              {form.maximumGrantAmount && !grantAmountError && (
                <span className="scheme-field__hint">
                  {formatCurrency(form.maximumGrantAmount)} cap per approved beneficiary
                </span>
              )}
            </label>

            <label className="scheme-field">
              <span className="scheme-field__label">Application Start Date</span>
              <input
                type="date"
                name="applicationStartDate"
                value={form.applicationStartDate}
                onChange={handleChange}
              />
            </label>

            <label className="scheme-field">
              <span className="scheme-field__label">Application End Date</span>
              <input
                type="date"
                name="applicationEndDate"
                value={form.applicationEndDate}
                onChange={handleChange}
              />
              {dateError && (
                <span className="scheme-field__error">{dateError}</span>
              )}
            </label>
          </div>
        </section>

        {/* Section 4: Eligibility Questionnaire Builder */}
        <section className="scheme-form-section">
          <div className="scheme-form-section__header scheme-form-section__header--between">
            <div style={{ display: "flex", gap: "10px", alignItems: "flex-start" }}>
              <span className="scheme-form-section__badge">4</span>
              <div>
                <h4>
                  Dynamic Eligibility Questions ({form.eligibilityQuestions.length})
                </h4>
                <p>
                  Configure dynamic evaluation questions. Total question weights must equal exactly 100 points.
                </p>
              </div>
            </div>
            {/* Weight Total Indicator Badge */}
            <div
              className={`scheme-weight-badge ${
                isWeightValid ? "scheme-weight-badge--valid" : "scheme-weight-badge--invalid"
              }`}
            >
              {isWeightValid ? (
                <CheckCircle2 size={16} aria-hidden="true" />
              ) : (
                <AlertTriangle size={16} aria-hidden="true" />
              )}
              <span>Total Weight: {totalWeight} / 100 pts</span>
            </div>
          </div>

          <div className="scheme-questions-list">
            {form.eligibilityQuestions.map((question, qIdx) => {
              const qMax = Number(question.maximumPoints) || 0;
              const hasExceededOption = question.answerOptions.some(
                (opt) => Number(opt.points) > qMax
              );

              return (
                <article className="scheme-question-card" key={qIdx}>
                  <div className="scheme-question-card__topbar">
                    <div className="scheme-question-card__title">
                      <span className="scheme-question-card__num">Q{qIdx + 1}</span>
                      <strong>
                        {question.questionText || "Untitled Question"}
                      </strong>
                      {question.id != null && (
                        <span className="scheme-question-id-pill" title="Existing question ID preserved in database">
                          ID: #{question.id}
                        </span>
                      )}
                      <span className="scheme-question-card__weight-pill">
                        Weight: {question.maximumPoints || 0} pts
                      </span>
                    </div>

                    <div className="scheme-question-card__tools">
                      <button
                        type="button"
                        className="scheme-tool-btn"
                        onClick={() => moveQuestion(qIdx, -1)}
                        disabled={qIdx === 0}
                        title="Move Up"
                        aria-label={`Move question ${qIdx + 1} up`}
                      >
                        <ArrowUp size={15} aria-hidden="true" />
                      </button>
                      <button
                        type="button"
                        className="scheme-tool-btn"
                        onClick={() => moveQuestion(qIdx, 1)}
                        disabled={qIdx === form.eligibilityQuestions.length - 1}
                        title="Move Down"
                        aria-label={`Move question ${qIdx + 1} down`}
                      >
                        <ArrowDown size={15} aria-hidden="true" />
                      </button>
                      <button
                        type="button"
                        className="scheme-tool-btn scheme-tool-btn--danger"
                        onClick={() => removeQuestion(qIdx)}
                        disabled={form.eligibilityQuestions.length <= 1}
                        title="Remove Question"
                        aria-label={`Remove question ${qIdx + 1}`}
                      >
                        <Trash2 size={15} aria-hidden="true" />
                      </button>
                    </div>
                  </div>

                  <div className="scheme-question-card__fields">
                    <label className="scheme-field scheme-field--wide">
                      <span className="scheme-field__label">
                        Question Text <span className="scheme-field__required">*</span>
                      </span>
                      <input
                        type="text"
                        value={question.questionText}
                        onChange={(e) =>
                          updateQuestion(qIdx, { questionText: e.target.value })
                        }
                        placeholder="e.g. Do you have an active rooftop solar installation?"
                        maxLength="500"
                        required
                      />
                    </label>

                    <label className="scheme-field">
                      <span className="scheme-field__label">Question Type</span>
                      <select
                        value={question.questionType}
                        onChange={(e) => handleQuestionTypeChange(qIdx, e.target.value)}
                      >
                        <option value="YES_NO">Yes / No</option>
                        <option value="SINGLE_CHOICE">Single Choice</option>
                      </select>
                    </label>

                    <label className="scheme-field">
                      <span className="scheme-field__label">
                        Question Weight (Max Points) <span className="scheme-field__required">*</span>
                      </span>
                      <input
                        type="number"
                        min="0.01"
                        max="100"
                        step="0.01"
                        value={question.maximumPoints}
                        onChange={(e) =>
                          updateQuestion(qIdx, { maximumPoints: e.target.value })
                        }
                        required
                      />
                    </label>

                    <label className="scheme-checkbox-field">
                      <input
                        type="checkbox"
                        checked={question.required}
                        onChange={(e) =>
                          updateQuestion(qIdx, { required: e.target.checked })
                        }
                      />
                      <span>Required question (applicant must answer to submit)</span>
                    </label>
                  </div>

                  {hasExceededOption && (
                    <div className="scheme-field__error" style={{ marginBottom: "10px" }}>
                      An option score cannot exceed the question's maximum weight of {qMax} points.
                    </div>
                  )}

                  {/* Options List */}
                  <div className="scheme-options-table-wrap">
                    <table className="scheme-options-table">
                      <thead>
                        <tr>
                          {question.questionType === "SINGLE_CHOICE" ? (
                            <>
                              <th scope="col" style={{ width: "25%" }}>Value Key</th>
                              <th scope="col" style={{ width: "35%" }}>Display Label</th>
                            </>
                          ) : (
                            <th scope="col" style={{ width: "60%" }}>Answer Choice</th>
                          )}
                          <th scope="col" style={{ width: "20%" }}>Score (Points)</th>
                          <th scope="col" style={{ width: "20%" }}>Qualifies Condition</th>
                          {question.questionType !== "YES_NO" && (
                            <th scope="col" style={{ width: "40px" }} aria-label="Actions"></th>
                          )}
                        </tr>
                      </thead>
                      <tbody>
                        {question.answerOptions.map((opt, optIdx) => {
                          const optPoints = Number(opt.points) || 0;
                          const isOptExceeded = optPoints > qMax;
                          return (
                            <tr key={optIdx} className={isOptExceeded ? "is-exceeded" : ""}>
                              {question.questionType === "YES_NO" ? (
                                <td>
                                  <strong>{opt.label}</strong>
                                  <span className="scheme-text-muted" style={{ marginLeft: "8px", fontSize: "0.8rem" }}>
                                    ({opt.value})
                                  </span>
                                </td>
                              ) : (
                                <>
                                  <td>
                                    <input
                                      type="text"
                                      value={opt.value}
                                      onChange={(e) =>
                                        updateAnswerOption(qIdx, optIdx, "value", e.target.value)
                                      }
                                      placeholder="e.g. URBAN"
                                      maxLength="100"
                                      required
                                    />
                                  </td>
                                  <td>
                                    <input
                                      type="text"
                                      value={opt.label}
                                      onChange={(e) =>
                                        updateAnswerOption(qIdx, optIdx, "label", e.target.value)
                                      }
                                      placeholder="e.g. Urban Resident"
                                      maxLength="200"
                                      required
                                    />
                                  </td>
                                </>
                              )}
                              <td>
                                <input
                                  type="number"
                                  min="0"
                                  max={qMax}
                                  step="0.01"
                                  value={opt.points}
                                  onChange={(e) =>
                                    updateAnswerOption(qIdx, optIdx, "points", e.target.value)
                                  }
                                  required
                                />
                                {isOptExceeded && (
                                  <span className="scheme-field__error" style={{ fontSize: "0.75rem" }}>
                                    &gt; {qMax} max
                                  </span>
                                )}
                              </td>
                              <td>
                                <label className="scheme-checkbox-inline">
                                  <input
                                    type="checkbox"
                                    checked={opt.meetsEligibilityCondition}
                                    onChange={(e) =>
                                      updateAnswerOption(
                                        qIdx,
                                        optIdx,
                                        "meetsEligibilityCondition",
                                        e.target.checked
                                      )
                                    }
                                  />
                                  <span>Meets condition</span>
                                </label>
                              </td>
                              {question.questionType !== "YES_NO" && (
                                <td>
                                  <button
                                    type="button"
                                    className="scheme-tool-btn scheme-tool-btn--danger"
                                    onClick={() => removeAnswerOption(qIdx, optIdx)}
                                    disabled={question.answerOptions.length <= 2}
                                    title="Remove answer option"
                                    aria-label="Remove answer option"
                                  >
                                    <X size={14} aria-hidden="true" />
                                  </button>
                                </td>
                              )}
                            </tr>
                          );
                        })}
                      </tbody>
                    </table>
                  </div>

                  {question.questionType !== "YES_NO" && (
                    <button
                      type="button"
                      className="scheme-add-option-btn"
                      onClick={() => addAnswerOption(qIdx)}
                    >
                      <Plus size={14} aria-hidden="true" /> Add Answer Option
                    </button>
                  )}
                </article>
              );
            })}
          </div>

          <div className="scheme-question-footer-bar">
            <button
              type="button"
              className="scheme-management__button scheme-management__button--secondary"
              onClick={addQuestion}
            >
              <Plus size={15} aria-hidden="true" /> Add Another Question
            </button>
            <span
              className={`scheme-question-counter ${
                isWeightValid ? "text-success" : "text-danger"
              }`}
            >
              Total Weight: {totalWeight} / 100 Points {isWeightValid ? "✓ Ready" : "(Must equal 100)"}
            </span>
          </div>
        </section>

        {/* Section 5: Publication Status & Submission */}
        <section className="scheme-form-section">
          <div className="scheme-form-section__header">
            <span className="scheme-form-section__badge">5</span>
            <div>
              <h4>Publication Status & Save</h4>
              <p>Publishing as ACTIVE makes the scheme available to beneficiaries immediately.</p>
            </div>
          </div>

          <div className="scheme-form-grid">
            <label className="scheme-field">
              <span className="scheme-field__label">Scheme Status</span>
              <select name="status" value={form.status} onChange={handleChange}>
                <option value="DRAFT">DRAFT (Internal only, not visible to applicants)</option>
                <option value="ACTIVE">ACTIVE (Published, visible in catalog & accepts applications)</option>
                <option value="INACTIVE">INACTIVE (Archived, closes applications)</option>
              </select>
            </label>
          </div>

          <div className="scheme-form-actions-bar">
            <button
              type="submit"
              className="scheme-management__button scheme-management__button--primary"
              disabled={saving || !isWeightValid || Boolean(dateError) || Boolean(grantAmountError)}
            >
              <Check size={16} aria-hidden="true" />
              {saving
                ? "Saving…"
                : editingId
                ? "Save Changes"
                : "Create & Save Scheme"}
            </button>
            {editingId && (
              <button
                type="button"
                className="scheme-management__button scheme-management__button--secondary"
                onClick={resetForm}
                disabled={saving}
              >
                Cancel
              </button>
            )}
          </div>
        </section>
      </form>

      {/* Scheme Listing & Management Section */}
      <section className="scheme-list-section" aria-labelledby="schemes-list-heading">
        <div className="scheme-list-header">
          <div>
            <h3 id="schemes-list-heading">Configured Grant Schemes</h3>
            <p>
              Showing {filteredSchemes.length} of {schemes.length} total schemes
            </p>
          </div>

          {/* Search Bar */}
          <div className="scheme-search-box">
            <Search size={16} aria-hidden="true" />
            <input
              type="search"
              placeholder="Search by scheme or department…"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              aria-label="Search schemes"
            />
            {searchQuery && (
              <button
                type="button"
                className="scheme-search-clear"
                onClick={() => setSearchQuery("")}
                aria-label="Clear search"
              >
                <X size={14} aria-hidden="true" />
              </button>
            )}
          </div>
        </div>

        {/* Status Filter Tabs */}
        <div className="scheme-filter-tabs" role="tablist" aria-label="Filter schemes by status">
          <button
            type="button"
            className={`scheme-filter-tab ${statusFilter === "ALL" ? "is-active" : ""}`}
            onClick={() => setStatusFilter("ALL")}
            role="tab"
            aria-selected={statusFilter === "ALL"}
          >
            All <span className="scheme-filter-count">{counts.all}</span>
          </button>
          <button
            type="button"
            className={`scheme-filter-tab ${statusFilter === "ACTIVE" ? "is-active" : ""}`}
            onClick={() => setStatusFilter("ACTIVE")}
            role="tab"
            aria-selected={statusFilter === "ACTIVE"}
          >
            Active <span className="scheme-filter-count">{counts.active}</span>
          </button>
          <button
            type="button"
            className={`scheme-filter-tab ${statusFilter === "DRAFT" ? "is-active" : ""}`}
            onClick={() => setStatusFilter("DRAFT")}
            role="tab"
            aria-selected={statusFilter === "DRAFT"}
          >
            Draft <span className="scheme-filter-count">{counts.draft}</span>
          </button>
          <button
            type="button"
            className={`scheme-filter-tab ${statusFilter === "INACTIVE" ? "is-active" : ""}`}
            onClick={() => setStatusFilter("INACTIVE")}
            role="tab"
            aria-selected={statusFilter === "INACTIVE"}
          >
            Inactive <span className="scheme-filter-count">{counts.inactive}</span>
          </button>
        </div>

        {/* Schemes Table */}
        <div className="scheme-table-container">
          <table className="scheme-table">
            <thead>
              <tr>
                <th scope="col">Scheme & Department</th>
                <th scope="col">Total Budget</th>
                <th scope="col">Max Grant</th>
                <th scope="col">Age / Income Cap</th>
                <th scope="col">Application Period</th>
                <th scope="col">Status</th>
                <th scope="col" style={{ textAlign: "right" }}>Actions</th>
              </tr>
            </thead>
            <tbody>
              {loading ? (
                <tr>
                  <td colSpan="7" className="scheme-table__state">
                    <RefreshCw size={20} className="spin-icon" aria-hidden="true" />
                    <span>Loading schemes…</span>
                  </td>
                </tr>
              ) : filteredSchemes.length === 0 ? (
                <tr>
                  <td colSpan="7" className="scheme-table__state">
                    <Filter size={24} aria-hidden="true" />
                    <p>No schemes matched your search or status filter.</p>
                    {searchQuery || statusFilter !== "ALL" ? (
                      <button
                        type="button"
                        className="scheme-management__button scheme-management__button--secondary scheme-management__button--sm"
                        onClick={() => {
                          setSearchQuery("");
                          setStatusFilter("ALL");
                        }}
                      >
                        Reset Filters
                      </button>
                    ) : null}
                  </td>
                </tr>
              ) : (
                filteredSchemes.map((scheme) => {
                  const isActive = scheme.status === "ACTIVE";
                  return (
                    <tr key={scheme.id}>
                      <td>
                        <div className="scheme-cell-name">
                          <strong>{scheme.schemeName}</strong>
                          <span className="scheme-cell-dept">
                            {scheme.department || "General Department"}
                          </span>
                        </div>
                      </td>
                      <td>
                        {scheme.allocatedFunds != null ? (
                          <strong>{formatCurrency(scheme.allocatedFunds)}</strong>
                        ) : (
                          <span style={{ color: "#d97706", fontWeight: 600, fontSize: "0.85rem" }}>
                            Not configured
                          </span>
                        )}
                      </td>
                      <td>
                        <strong>{formatCurrency(scheme.maximumGrantAmount)}</strong>
                      </td>
                      <td>
                        <div className="scheme-cell-criteria">
                          <span>Age: {scheme.minimumAge ?? "—"}–{scheme.maximumAge ?? "—"}</span>
                          <span>Max: {formatCurrency(scheme.maximumAnnualIncome)}</span>
                        </div>
                      </td>
                      <td>
                        <div className="scheme-cell-dates">
                          {scheme.applicationStartDate || scheme.applicationEndDate ? (
                            <>
                              <span>{formatDate(scheme.applicationStartDate)}</span>
                              <span className="scheme-text-muted">to</span>
                              <span>{formatDate(scheme.applicationEndDate)}</span>
                            </>
                          ) : (
                            <span className="scheme-text-muted">Open / Unset</span>
                          )}
                        </div>
                      </td>
                      <td>
                        <span className={`scheme-status-pill scheme-status-pill--${(scheme.status || "DRAFT").toLowerCase()}`}>
                          {scheme.status}
                        </span>
                      </td>
                      <td>
                        <div className="scheme-row-actions">
                          <button
                            type="button"
                            className="scheme-action-btn"
                            onClick={() => handleViewDetails(scheme.id)}
                            title="View Scheme Details"
                          >
                            <Eye size={15} aria-hidden="true" />
                            <span>Details</span>
                          </button>
                          <button
                            type="button"
                            className="scheme-action-btn"
                            onClick={() => handleEdit(scheme)}
                            title="Edit Scheme"
                          >
                            <Edit2 size={15} aria-hidden="true" />
                            <span>Edit</span>
                          </button>
                          <button
                            type="button"
                            className={`scheme-action-btn ${isActive ? "scheme-action-btn--warn" : "scheme-action-btn--success"}`}
                            onClick={() => handleToggleStatus(scheme)}
                            title={isActive ? "Deactivate Scheme" : "Activate Scheme"}
                          >
                            <Power size={14} aria-hidden="true" />
                            <span>{isActive ? "Deactivate" : "Activate"}</span>
                          </button>
                          <button
                            type="button"
                            className="scheme-action-btn scheme-action-btn--danger"
                            onClick={() => handleDelete(scheme)}
                            disabled={deletingId === scheme.id}
                            title="Delete Scheme"
                          >
                            <Trash2 size={15} aria-hidden="true" />
                            <span>{deletingId === scheme.id ? "…" : "Delete"}</span>
                          </button>
                        </div>
                      </td>
                    </tr>
                  );
                })
              )}
            </tbody>
          </table>
        </div>
      </section>

      {/* Scheme Details Drawer / Modal */}
      {(detailsLoading || detailsError || selectedScheme) && (
        <aside
          className="scheme-details-drawer"
          aria-labelledby="scheme-details-title"
          aria-modal="true"
        >
          <div className="scheme-details-card">
            <div className="scheme-details-card__header">
              <div>
                <span className={`scheme-status-pill scheme-status-pill--${(selectedScheme?.status || "draft").toLowerCase()}`}>
                  {selectedScheme?.status}
                </span>
                <h3 id="scheme-details-title">
                  {selectedScheme ? selectedScheme.schemeName : "Scheme Details"}
                </h3>
                {selectedScheme?.department && (
                  <p className="scheme-text-muted">{selectedScheme.department}</p>
                )}
              </div>
              <button
                type="button"
                className="scheme-tool-btn"
                onClick={() => setSelectedScheme(null)}
                aria-label="Close scheme details"
              >
                <X size={18} aria-hidden="true" />
              </button>
            </div>

            {detailsLoading && (
              <div className="scheme-details-loading">
                <RefreshCw size={24} className="spin-icon" aria-hidden="true" />
                <span>Loading full details…</span>
              </div>
            )}

            {detailsError && (
              <div className="scheme-notice scheme-notice--error">
                <AlertCircle size={18} aria-hidden="true" />
                <span>{detailsError}</span>
              </div>
            )}

            {selectedScheme && (
              <div className="scheme-details-content">
                <div className="scheme-details-grid">
                  <div>
                    <span className="scheme-detail-label">Description</span>
                    <p className="scheme-detail-val">
                      {selectedScheme.description || "No public description provided."}
                    </p>
                  </div>

                  <div className="scheme-details-subgrid">
                    <div>
                      <span className="scheme-detail-label">Age Range</span>
                      <span className="scheme-detail-val">
                        {selectedScheme.minimumAge ?? "—"} to {selectedScheme.maximumAge ?? "—"} years
                      </span>
                    </div>
                    <div>
                      <span className="scheme-detail-label">Max Annual Income</span>
                      <span className="scheme-detail-val">
                        {formatCurrency(selectedScheme.maximumAnnualIncome)}
                      </span>
                    </div>
                    <div>
                      <span className="scheme-detail-label">Total Scheme Budget</span>
                      <span className="scheme-detail-val">
                        {selectedScheme.allocatedFunds != null
                          ? formatCurrency(selectedScheme.allocatedFunds)
                          : "Not configured"}
                      </span>
                    </div>
                    <div>
                      <span className="scheme-detail-label">Max Grant Amount</span>
                      <span className="scheme-detail-val">
                        {formatCurrency(selectedScheme.maximumGrantAmount)}
                      </span>
                    </div>
                    <div>
                      <span className="scheme-detail-label">Eligibility Threshold</span>
                      <span className="scheme-detail-val">
                        {selectedScheme.eligibilityThreshold ?? "70"}%
                      </span>
                    </div>
                    <div>
                      <span className="scheme-detail-label">Application Period</span>
                      <span className="scheme-detail-val">
                        {formatDate(selectedScheme.applicationStartDate)} – {formatDate(selectedScheme.applicationEndDate)}
                      </span>
                    </div>
                    <div>
                      <span className="scheme-detail-label">Eligible Categories</span>
                      <div className="scheme-category-chips" style={{ marginTop: "4px" }}>
                        {(selectedScheme.eligibleCategories || []).map((c) => (
                          <span key={c} className="scheme-chip scheme-chip--selected" style={{ cursor: "default" }}>
                            {c}
                          </span>
                        ))}
                      </div>
                    </div>
                  </div>
                </div>

                {/* Questionnaire Breakdown */}
                <div className="scheme-details-questions">
                  <h4>Configured Dynamic Questions ({(selectedScheme.eligibilityQuestions || []).length})</h4>
                  <ol className="scheme-details-q-list">
                    {(selectedScheme.eligibilityQuestions || []).map((q, idx) => (
                      <li key={q.id || idx} className="scheme-details-q-item">
                        <div className="scheme-details-q-header">
                          <strong>{q.questionText}</strong>
                          <span className="scheme-question-card__weight-pill">
                            Max {q.maximumPoints} pts {q.required ? "• Required" : ""}
                          </span>
                        </div>
                        <ul className="scheme-details-opt-list">
                          {(q.answerOptions || []).map((opt, oIdx) => (
                            <li key={oIdx} className="scheme-details-opt-item">
                              <span>
                                {opt.label} ({opt.value}) — <strong>{opt.points} pts</strong>
                              </span>
                              {opt.meetsEligibilityCondition && (
                                <span className="scheme-qualifies-badge">✓ Meets Condition</span>
                              )}
                            </li>
                          ))}
                        </ul>
                      </li>
                    ))}
                  </ol>
                </div>

                <div className="scheme-details-footer">
                  <button
                    type="button"
                    className="scheme-management__button scheme-management__button--primary scheme-management__button--sm"
                    onClick={() => {
                      handleEdit(selectedScheme);
                      setSelectedScheme(null);
                    }}
                  >
                    <Edit2 size={14} aria-hidden="true" /> Edit Scheme
                  </button>
                  <button
                    type="button"
                    className="scheme-management__button scheme-management__button--secondary scheme-management__button--sm"
                    onClick={() => setSelectedScheme(null)}
                  >
                    Close
                  </button>
                </div>
              </div>
            )}
          </div>
        </aside>
      )}
    </div>
  );
}

export default SchemeManagement;
