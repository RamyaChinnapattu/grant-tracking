
import { useEffect, useState } from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import {
  createApplication,
  getApplicationById,
  getApplications,
  getAdminApplications,
  getMyApplications,
  submitApplication,
  withdrawApplication,
  deleteDraftApplication,
  updateApplication,
  uploadApplicationDocument,
  downloadApplicationDocument,
} from "../services/grantApplicationService";
import { getSchemes } from "../services/schemeService";
import {
  formatCurrencyINR,
  getDisbursementBadgeInfo,
  getDisbursementSummary,
  getPendingActionText,
  getStepState,
} from "../utils/applicationDisbursement";
import { PageHeader } from "../components/ui/SaaSPrimitives";
import "../styles/GrantApplicationManagement.css";

const APPLICATION_STATUSES = [
  "DRAFT",
  "SUBMITTED",
  "UNDER_VERIFICATION",
  "FIELD_VERIFICATION",
  "DISTRICT_REVIEW",
  "FINANCE_REVIEW",
  "NEEDS_CORRECTION",
  "UNDER_REVIEW",
  "APPROVED",
  "REJECTED",
  "WITHDRAWN",
];

const ADMIN_PAGE_SIZE = 20;

const emptyForm = {
  schemeId: "",
  applicationDetails: "",
  requestedAmount: "",
  eligibilityAnswers: {},
};

function getEditableEligibilityAnswers(application, scheme) {
  const results =
    application.eligibilityEvaluation?.customQuestionResults || [];
  const usedResults = new Set();

  return Object.fromEntries(
    (scheme?.eligibilityQuestions || []).flatMap((question) => {
      const resultIndex = results.findIndex(
        (result, index) =>
          !usedResults.has(index) &&
          (result.questionId === question.id ||
            result.questionText === question.questionText) &&
          (question.answerOptions || []).some(
            (option) => option.value === result.answerValue
          )
      );

      if (resultIndex === -1) {
        return [];
      }

      usedResults.add(resultIndex);
      return [[question.id, results[resultIndex].answerValue]];
    })
  );
}

function getLatestCorrectionRemarks(app) {
  if (!app) return "";

  const allHistory = [
    ...(app.statusHistory || []).map((h) => ({
      ...h,
      isStatus: true,
    })),
    ...(app.verificationHistory || []).map((h) => ({
      ...h,
      isStatus: false,
    })),
  ].sort(
    (a, b) =>
      new Date(b.createdAt) - new Date(a.createdAt)
  );

  const correctionEntry = allHistory.find(
    (h) =>
      (h.action === "NEEDS_CORRECTION" ||
        h.toStatus === "NEEDS_CORRECTION") &&
      h.remarks
  );

  return correctionEntry?.remarks || "";
}

function formatStatus(status) {
  if (!status) return "—";
  return status.replaceAll("_", " ");
}

function GrantApplicationManagement() {
  const role = localStorage.getItem("role");
  const isBeneficiary = role === "BENEFICIARY";
  const isAdmin = role === "ADMIN";

  const location = useLocation();
  const navigate = useNavigate();

  const isApplyPage =
    isBeneficiary &&
    location.pathname === "/beneficiary/apply";

  const isTrackingPage =
    isBeneficiary &&
    location.pathname === "/beneficiary/applications";

  const [applications, setApplications] = useState([]);
  const [statusFilter, setStatusFilter] = useState("");
  const [page, setPage] = useState(0);

  const [pageInfo, setPageInfo] = useState({
    totalPages: 0,
    totalElements: 0,
  });

  const [schemes, setSchemes] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const [form, setForm] = useState(emptyForm);
  const [editingId, setEditingId] = useState(null);
  const [editingStatus, setEditingStatus] = useState(null);

  const [saving, setSaving] = useState(false);
  const [actionError, setActionError] = useState("");
  const [successMessage, setSuccessMessage] = useState("");

  const [selectedApplication, setSelectedApplication] =
    useState(null);
  const [selectedApplicationId, setSelectedApplicationId] =
    useState(null);

  const [detailsLoading, setDetailsLoading] = useState(false);
  const [detailsError, setDetailsError] = useState("");

  const [documents, setDocuments] = useState([]);
  const [documentPreview, setDocumentPreview] = useState(null);
  const [beneficiaryStatusFilter, setBeneficiaryStatusFilter] = useState("");

  useEffect(() => {
    if (location.state?.schemeId) {
      setForm((current) => ({
        ...current,
        schemeId: String(location.state.schemeId),
        eligibilityAnswers:
          location.state.eligibilityAnswers ||
          current.eligibilityAnswers,
      }));

      if (location.state.prefilledNotice) {
        setSuccessMessage(location.state.prefilledNotice);
      }
    }

    if (location.state?.selectedApplicationId) {
      handleViewDetails(location.state.selectedApplicationId);
    }
  }, [location.state]);

  const loadApplications = async () => {
    setLoading(true);
    setError("");

    try {
      const [applicationsResponse, schemesResponse] =
        await Promise.all([
          isBeneficiary
            ? getMyApplications()
            : isAdmin
              ? getAdminApplications({
                page,
                size: ADMIN_PAGE_SIZE,
                ...(statusFilter
                  ? { status: statusFilter }
                  : {}),
              })
              : getApplications(),

          isBeneficiary
            ? getSchemes()
            : Promise.resolve({ data: [] }),
        ]);

      if (isAdmin) {
        setApplications(applicationsResponse.data.content);

        setPageInfo({
          totalPages: applicationsResponse.data.totalPages,
          totalElements:
            applicationsResponse.data.totalElements,
        });
      } else {
        setApplications(applicationsResponse.data);
      }

      setSchemes(schemesResponse.data);
    } catch (err) {
      setError(
        err.response?.data?.message ||
        err.response?.data?.error ||
        "Unable to load grant applications."
      );
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadApplications();

    const refreshOnFocus = () => loadApplications();

    window.addEventListener("focus", refreshOnFocus);

    return () =>
      window.removeEventListener("focus", refreshOnFocus);
  }, [isAdmin, isBeneficiary, page, statusFilter]);

  useEffect(
    () => () => {
      if (documentPreview?.url) {
        URL.revokeObjectURL(documentPreview.url);
      }
    },
    [documentPreview?.url]
  );

  const availableSchemes = schemes.filter(
    (scheme) => scheme.status === "ACTIVE"
  );

  const selectedScheme = schemes.find(
    (scheme) => String(scheme.id) === form.schemeId
  );

  const handleChange = (event) => {
    const { name, value } = event.target;

    setForm((current) => ({
      ...current,
      [name]: value,
      ...(name === "schemeId"
        ? { eligibilityAnswers: {} }
        : {}),
    }));
  };

  const handleFilesSelected = (event) => {
    const selectedFiles = Array.from(event.target.files || []);
    const validFiles = [];
    const invalidTypes = [];
    const oversizedFiles = [];

    const allowedExtensions = [".pdf", ".png", ".jpg", ".jpeg"];
    const allowedTypes = [
      "application/pdf",
      "image/png",
      "image/jpeg",
      "image/jpg",
    ];

    for (const file of selectedFiles) {
      const ext = file.name.slice(file.name.lastIndexOf(".")).toLowerCase();
      const isValid = allowedExtensions.includes(ext) || allowedTypes.includes(file.type);

      if (!isValid) {
        invalidTypes.push(file.name);
      } else if (file.size > 10 * 1024 * 1024) {
        oversizedFiles.push(`${file.name} (${(file.size / (1024 * 1024)).toFixed(1)} MB)`);
      } else {
        validFiles.push(file);
      }
    }

    if (invalidTypes.length > 0) {
      setActionError(
        `Invalid file format: ${invalidTypes.join(", ")}. Only PDF, PNG, and JPEG documents are permitted.`
      );
    } else if (oversizedFiles.length > 0) {
      setActionError(
        `File exceeds maximum 10 MB limit: ${oversizedFiles.join(", ")}.`
      );
    } else {
      setActionError("");
    }

    setDocuments((current) => [...current, ...validFiles]);
    event.target.value = "";
  };

  const resetForm = () => {
    setForm({
      ...emptyForm,
      eligibilityAnswers: {},
    });

    setEditingId(null);
    setEditingStatus(null);
    setDocuments([]);
  };

  const handleViewDetails = async (id) => {
    setSelectedApplicationId(id);
    setSelectedApplication(null);
    setDetailsError("");
    setDetailsLoading(true);

    try {
      const response = await getApplicationById(id);
      setSelectedApplication(response.data);
    } catch (err) {
      setDetailsError(
        err.response?.data?.message ||
        err.response?.data?.error ||
        "Unable to load application details."
      );
    } finally {
      setDetailsLoading(false);
    }
  };

  const handleEdit = (application) => {
    if (isTrackingPage) {
      navigate("/beneficiary/apply", {
        state: {
          editApplication: application,
        },
      });

      return;
    }

    const scheme = schemes.find(
      (item) => item.id === application.schemeId
    );

    setEditingId(application.id);
    setEditingStatus(application.status);
    setDocuments([]);

    setForm({
      schemeId: String(application.schemeId),
      applicationDetails: application.applicationDetails,
      requestedAmount: String(application.requestedAmount),
      eligibilityAnswers: getEditableEligibilityAnswers(
        application,
        scheme
      ),
    });

    setActionError("");
    setSuccessMessage("");
  };

  useEffect(() => {
    if (
      !isApplyPage ||
      loading ||
      !location.state?.editApplication
    ) {
      return;
    }

    handleEdit(location.state.editApplication);

    navigate("/beneficiary/apply", {
      replace: true,
      state: {
        schemeId:
          location.state.editApplication.schemeId,
      },
    });
  }, [
    isApplyPage,
    loading,
    location.state,
    navigate,
    schemes,
  ]);

  const handleSave = async (event) => {
    event.preventDefault();

    const shouldSubmit =
      event.nativeEvent.submitter?.value === "submit";

    if (
      shouldSubmit &&
      !window.confirm(
        editingStatus === "NEEDS_CORRECTION"
          ? "Resubmit this application with the corrected information?"
          : "Submit this grant application for verification?"
      )
    ) {
      return;
    }

    setSaving(true);
    setActionError("");
    setSuccessMessage("");

    const existingDocsCount = editingId
      ? applications.find((a) => a.id === editingId)
        ?.documents?.length ||
      (selectedApplication?.id === editingId
        ? selectedApplication?.documents?.length
        : 0) ||
      0
      : 0;

    if (
      shouldSubmit &&
      documents.length === 0 &&
      existingDocsCount === 0
    ) {
      setActionError(
        "At least one supporting document is required before submitting your application."
      );

      setSaving(false);
      return;
    }

    if (selectedScheme && selectedScheme.maximumGrantAmount != null) {
      if (Number(form.requestedAmount) > Number(selectedScheme.maximumGrantAmount)) {
        setActionError(
          `Requested amount (₹${Number(form.requestedAmount).toLocaleString("en-IN")}) exceeds maximum allowable grant of ₹${Number(selectedScheme.maximumGrantAmount).toLocaleString("en-IN")} for this scheme.`
        );

        setSaving(false);
        return;
      }
    }

    try {
      const payload = {
        applicationDetails: form.applicationDetails.trim(),
        requestedAmount: Number(form.requestedAmount),

        eligibilityAnswers: Object.entries(
          form.eligibilityAnswers
        )
          .filter(([, answer]) => answer !== "")
          .map(([questionId, answer]) => ({
            questionId: Number(questionId),
            answer,
          })),
      };

      let savedApplication;

      if (editingId) {
        savedApplication = (
          await updateApplication(editingId, payload)
        ).data;
      } else {
        savedApplication = (
          await createApplication({
            ...payload,
            schemeId: Number(form.schemeId),
          })
        ).data;
      }

      for (const document of documents) {
        await uploadApplicationDocument(
          savedApplication.id,
          document
        );
      }

      if (shouldSubmit) {
        try {
          savedApplication = (
            await submitApplication(savedApplication.id)
          ).data;
        } catch (err) {
          setActionError(
            err.response?.data?.message ||
            err.response?.data?.error ||
            "Draft saved, but submission failed."
          );

          await loadApplications();
          await handleViewDetails(savedApplication.id);
          return;
        }
      }

      setSuccessMessage(
        shouldSubmit
          ? editingStatus === "NEEDS_CORRECTION"
            ? "Corrected application resubmitted successfully."
            : "Grant application submitted successfully."
          : editingId
            ? editingStatus === "NEEDS_CORRECTION"
              ? "Corrections saved successfully."
              : "Draft updated successfully."
            : "Application draft saved successfully."
      );

      resetForm();
      await loadApplications();

      if (isApplyPage) {
        navigate("/beneficiary/applications", {
          replace: true,
        });
      }

      if (savedApplication?.id) {
        await handleViewDetails(savedApplication.id);
      }
    } catch (err) {
      setActionError(
        err.response?.data?.message ||
        err.response?.data?.error ||
        "Unable to save grant application."
      );
    } finally {
      setSaving(false);
    }
  };

  const handleSubmitDraft = async (application) => {
    if (application.status === "NEEDS_CORRECTION") {
      handleEdit(application);
      setActionError(
        "Please review the officer's correction remarks, update your details or attach supporting documents in the form below, and resubmit."
      );
      return;
    }

    const docCount = application.documents?.length || 0;

    if (docCount === 0) {
      setActionError(
        "At least one supporting document must be uploaded before submitting this application. Please edit the draft to attach required documents."
      );

      return;
    }

    if (
      !window.confirm(
        application.status === "NEEDS_CORRECTION"
          ? "Resubmit this application with its saved corrected information?"
          : "Submit this grant application for verification?"
      )
    ) {
      return;
    }

    setActionError("");
    setSuccessMessage("");
    setSaving(true);

    try {
      await submitApplication(application.id);

      setSuccessMessage("Grant application submitted successfully.");

      await loadApplications();
      await handleViewDetails(application.id);
    } catch (err) {
      setActionError(
        err.response?.data?.message ||
        err.response?.data?.error ||
        "Unable to submit grant application."
      );
    } finally {
      setSaving(false);
    }
  };

  const handleDeleteDraft = async (application) => {
    if (
      !window.confirm(
        "Permanently delete this draft application?"
      )
    ) {
      return;
    }

    setActionError("");
    setSuccessMessage("");

    try {
      await deleteDraftApplication(application.id);

      setSuccessMessage(
        "Draft application deleted successfully."
      );

      if (selectedApplication?.id === application.id) {
        setSelectedApplication(null);
      }

      await loadApplications();
    } catch (err) {
      setActionError(
        err.response?.data?.message ||
        err.response?.data?.error ||
        "Unable to delete draft application."
      );
    }
  };

  const handleWithdraw = async (application) => {
    if (
      !window.confirm(
        "Withdraw this application? This action cannot be undone."
      )
    ) {
      return;
    }

    setActionError("");
    setSuccessMessage("");

    try {
      await withdrawApplication(application.id);

      setSuccessMessage(
        "Application withdrawn successfully."
      );

      await loadApplications();
      await handleViewDetails(application.id);
    } catch (err) {
      setActionError(
        err.response?.data?.message ||
        err.response?.data?.error ||
        "Unable to withdraw application."
      );
    }
  };

  const handleDocumentDownload = async (
    applicationId,
    document
  ) => {
    try {
      const response = await downloadApplicationDocument(
        applicationId,
        document.id
      );

      const url = URL.createObjectURL(response.data);
      const link = window.document.createElement("a");

      link.href = url;
      link.download = document.fileName;
      link.click();

      window.setTimeout(
        () => URL.revokeObjectURL(url),
        1000
      );
    } catch (err) {
      setDetailsError(
        err.response?.data?.message ||
        "Unable to download the application document."
      );
    }
  };

  const handleDocumentPreview = async (
    applicationId,
    document
  ) => {
    setDetailsError("");

    try {
      const response = await downloadApplicationDocument(
        applicationId,
        document.id
      );

      const url = URL.createObjectURL(response.data);

      setDocumentPreview({
        url,
        contentType: document.contentType,
        fileName: document.fileName,
      });
    } catch (err) {
      setDetailsError(
        err.response?.data?.message ||
        "Unable to open the application document."
      );
    }
  };

  const closeDocumentPreview = () => {
    if (documentPreview?.url) {
      URL.revokeObjectURL(documentPreview.url);
    }

    setDocumentPreview(null);
  };

  const handleRefresh = async () => {
    await loadApplications();

    if (selectedApplication?.id) {
      await handleViewDetails(selectedApplication.id);
    }
  };

  const displayedApplications = isBeneficiary
    ? applications.filter((app) => {
        if (!beneficiaryStatusFilter) return true;
        if (beneficiaryStatusFilter === "UNDER_REVIEW") {
          return [
            "SUBMITTED",
            "UNDER_VERIFICATION",
            "FIELD_VERIFICATION",
            "DISTRICT_REVIEW",
            "FINANCE_REVIEW",
            "UNDER_REVIEW",
          ].includes(app.status);
        }
        return app.status === beneficiaryStatusFilter;
      })
    : applications;

  return (
    <section
      className="grant-applications saas-page"
      aria-labelledby="grant-applications-heading"
    >
      <PageHeader
        eyebrow={isBeneficiary ? "Beneficiary Portal" : "Applications & Verification"}
        title={
          isApplyPage
            ? editingId
              ? editingStatus === "NEEDS_CORRECTION"
                ? "Correct your application"
                : "Edit your draft"
              : "Apply for a Grant"
            : isTrackingPage
              ? "My Applications"
              : "Applications & Verification"
        }
        subtitle={
          isApplyPage
            ? "Complete the form and submit your application for verification."
            : isTrackingPage
              ? "Track application status, verification milestones, disbursement progress, and correction requests."
              : "Review grant applications, inspect details, track verification decisions, and monitor disbursement progress."
        }
        actions={
          <>
            {!isBeneficiary && (
              <Link to="/admin/verification" className="saas-btn saas-btn--secondary">
                Verification Queue
              </Link>
            )}
            <button
              className="saas-btn saas-btn--secondary"
              type="button"
              onClick={handleRefresh}
              disabled={loading}
            >
              {loading ? "Loading..." : "Refresh"}
            </button>
          </>
        }
      />

      {error && (
        <div
          className="grant-applications__notice grant-applications__notice--error"
          role="alert"
        >
          <span>{error}</span>

          <button
            className="grant-applications__button grant-applications__button--secondary"
            type="button"
            onClick={loadApplications}
            disabled={loading}
          >
            Try again
          </button>
        </div>
      )}

      {actionError && (
        <p
          className="grant-applications__notice grant-applications__notice--error"
          role="alert"
        >
          {actionError}
        </p>
      )}

      {successMessage && (
        <p
          className="grant-applications__notice grant-applications__notice--success"
          role="status"
        >
          {successMessage}
        </p>
      )}

      {isAdmin && (
        <div className="grant-applications__filters">
          <label htmlFor="application-status-filter">
            Filter by status
          </label>

          <select
            id="application-status-filter"
            value={statusFilter}
            onChange={(event) => {
              setPage(0);
              setStatusFilter(event.target.value);
            }}
          >
            <option value="">All statuses</option>

            {APPLICATION_STATUSES.map((status) => (
              <option key={status} value={status}>
                {status.replaceAll("_", " ")}
              </option>
            ))}
          </select>

          <span>
            {pageInfo.totalElements} application
            {pageInfo.totalElements === 1 ? "" : "s"}
          </span>
        </div>
      )}

      {isBeneficiary && isTrackingPage && (
        <div className="grant-applications__filters">
          <label htmlFor="beneficiary-status-filter">
            Filter by status
          </label>

          <select
            id="beneficiary-status-filter"
            value={beneficiaryStatusFilter}
            onChange={(event) => setBeneficiaryStatusFilter(event.target.value)}
          >
            <option value="">All statuses ({applications.length})</option>
            <option value="DRAFT">Draft</option>
            <option value="UNDER_REVIEW">Under Review</option>
            <option value="NEEDS_CORRECTION">Needs Correction</option>
            <option value="APPROVED">Approved Grants</option>
            <option value="REJECTED">Rejected</option>
            <option value="WITHDRAWN">Withdrawn</option>
          </select>

          <span>
            {displayedApplications.length} application
            {displayedApplications.length === 1 ? "" : "s"}
          </span>
        </div>
      )}

      {isApplyPage && (
        <form
          className="grant-applications__form"
          onSubmit={handleSave}
        >
          <h3>
            {editingId
              ? editingStatus === "NEEDS_CORRECTION"
                ? "Edit requested corrections"
                : "Edit draft"
              : "Start an application"}
          </h3>

          {editingId && editingStatus === "NEEDS_CORRECTION" && (
            <div className="grant-applications__action-required" style={{ marginBottom: "16px" }}>
              <div className="grant-applications__action-header">
                <span className="grant-applications__action-badge">Action Required</span>
                <h4>Officer Correction Remarks</h4>
              </div>
              <blockquote className="grant-applications__action-remarks">
                {getLatestCorrectionRemarks(
                  applications.find((a) => a.id === editingId) || selectedApplication
                ) || "Please review the requested corrections, update your application details or attach supporting documents, and resubmit."}
              </blockquote>
            </div>
          )}

          <label>
            Grant scheme

            <select
              name="schemeId"
              value={form.schemeId}
              onChange={handleChange}
              required
              disabled={Boolean(editingId)}
            >
              <option value="">
                Select an available scheme
              </option>

              {availableSchemes.map((scheme) => (
                <option key={scheme.id} value={scheme.id}>
                  {scheme.schemeName}
                </option>
              ))}
            </select>
          </label>

          <label>
            Application details

            <textarea
              name="applicationDetails"
              value={form.applicationDetails}
              onChange={handleChange}
              maxLength="10000"
              rows="4"
              required
            />
          </label>

          <label>
            Requested amount (₹)

            <input
              name="requestedAmount"
              type="number"
              min="0.01"
              step="0.01"
              value={form.requestedAmount}
              onChange={handleChange}
              required
            />
          </label>

          <label>
            Supporting documents (PDF, PNG, JPEG; up to 10 MB each)

            <span
              style={{
                color: "var(--color-primary, #1e40af)",
                fontWeight: 500,
                fontSize: "0.85em",
                marginLeft: "6px",
              }}
            >
              * At least 1 document required before submission
            </span>

            <input
              type="file"
              multiple
              accept=".pdf,.png,.jpg,.jpeg,application/pdf,image/png,image/jpeg"
              onChange={handleFilesSelected}
            />
          </label>

          {documents.length > 0 && (
            <div style={{ padding: "8px 12px", background: "var(--color-surface-lowest)", borderRadius: "6px", border: "1px solid var(--color-outline-variant)" }}>
              <span style={{ fontSize: "0.82rem", fontWeight: 600, color: "var(--color-on-surface)" }}>
                Selected Documents ({documents.length}):
              </span>
              <ul style={{ margin: "6px 0 0", paddingLeft: "20px", fontSize: "0.82rem" }}>
                {documents.map((document, idx) => (
                  <li key={idx} style={{ margin: "4px 0" }}>
                    {document.name} ({(document.size / 1024).toFixed(0)} KB){" "}
                    <button
                      type="button"
                      onClick={() => setDocuments((prev) => prev.filter((_, i) => i !== idx))}
                      style={{
                        background: "none",
                        border: "none",
                        color: "#b91c1c",
                        fontSize: "0.8rem",
                        cursor: "pointer",
                        marginLeft: "6px",
                        textDecoration: "underline",
                      }}
                    >
                      Remove
                    </button>
                  </li>
                ))}
              </ul>
            </div>
          )}

          {editingId && (
            (() => {
              const app = applications.find((a) => a.id === editingId) || (selectedApplication?.id === editingId ? selectedApplication : null);
              const existing = app?.documents || [];
              if (existing.length === 0) return null;
              return (
                <div style={{ padding: "8px 12px", background: "var(--color-surface-lowest)", borderRadius: "6px", border: "1px solid var(--color-outline-variant)", marginTop: "6px" }}>
                  <span style={{ fontSize: "0.82rem", fontWeight: 600, color: "var(--color-on-surface)" }}>
                    Preserved Supporting Documents ({existing.length}):
                  </span>
                  <ul style={{ margin: "4px 0 0", paddingLeft: "20px", fontSize: "0.82rem" }}>
                    {existing.map((doc, idx) => (
                      <li key={doc.id || idx}>
                        {doc.fileName || doc.documentType || `Document #${doc.id || idx + 1}`}
                      </li>
                    ))}
                  </ul>
                  <small style={{ color: "var(--color-on-surface-variant)", display: "block", marginTop: "4px" }}>
                    Previously uploaded documents are preserved and attached to your application.
                  </small>
                </div>
              );
            })()
          )}

          {selectedScheme && (
            <section
              className="grant-applications__eligibility"
              aria-labelledby="application-eligibility-heading"
            >
              <h4 id="application-eligibility-heading">
                Eligibility criteria
              </h4>

              <p>
                Mandatory: age{" "}
                {selectedScheme.minimumAge ?? "not configured"}–
                {selectedScheme.maximumAge ?? "not configured"},
                annual income up to ₹
                {selectedScheme.maximumAnnualIncome ??
                  "not configured"},
                and category{" "}
                {(selectedScheme.eligibleCategories || [])
                  .join(", ") || "not configured"}
                . The custom score threshold is{" "}
                {selectedScheme.eligibilityThreshold ??
                  "not configured"}
                %.
              </p>

              {(selectedScheme.eligibilityQuestions || []).map(
                (question) => (
                  <label key={question.id}>
                    {question.questionText}
                    {!question.required && " (optional)"}

                    <select
                      value={
                        form.eligibilityAnswers[question.id] || ""
                      }
                      required={question.required}
                      onChange={(event) =>
                        setForm((current) => ({
                          ...current,
                          eligibilityAnswers: {
                            ...current.eligibilityAnswers,
                            [question.id]: event.target.value,
                          },
                        }))
                      }
                    >
                      <option value="">Select an answer</option>

                      {(question.answerOptions || []).map(
                        (option) => (
                          <option
                            key={option.value}
                            value={option.value}
                          >
                            {option.label}
                            {option.meetsEligibilityCondition
                              ? " (meets eligibility condition)"
                              : ""}
                          </option>
                        )
                      )}
                    </select>
                  </label>
                )
              )}

              <p className="grant-applications__help">
                Eligibility results inform your application only.
                They do not approve or reject the grant.
              </p>
            </section>
          )}

          <div className="grant-applications__form-actions">
            <button
              className="grant-applications__button grant-applications__button--secondary"
              type="submit"
              name="action"
              value="draft"
              disabled={saving}
            >
              {saving
                ? "Saving..."
                : editingStatus === "NEEDS_CORRECTION"
                  ? "Save corrections"
                  : "Save draft"}
            </button>

            <button
              className="grant-applications__button grant-applications__button--primary"
              type="submit"
              name="action"
              value="submit"
              disabled={
                saving || availableSchemes.length === 0
              }
            >
              {saving
                ? "Saving..."
                : editingStatus === "NEEDS_CORRECTION"
                  ? "Resubmit"
                  : "Submit application"}
            </button>

            {editingId && (
              <button
                className="grant-applications__button grant-applications__button--secondary"
                type="button"
                onClick={() =>
                  navigate("/beneficiary/applications")
                }
                disabled={saving}
              >
                Cancel
              </button>
            )}
          </div>

          {availableSchemes.length === 0 && !loading && (
            <p className="grant-applications__help">
              No active schemes are currently available.
            </p>
          )}
        </form>
      )}

      {(!isBeneficiary || isTrackingPage) && (
        <>
          <div className="grant-applications__table-wrap">
            <table className="grant-applications__table">
              <thead>
                <tr>
                  {!isBeneficiary && (
                    <th scope="col">Beneficiary</th>
                  )}

                  <th scope="col">Scheme</th>

                  {(isBeneficiary || isAdmin) && (
                    <th scope="col">Eligibility</th>
                  )}

                  <th scope="col">Requested amount</th>
                  <th scope="col">Submitted</th>
                  <th scope="col">Status</th>
                  <th scope="col">Actions</th>
                </tr>
              </thead>

              <tbody>
                {loading ? (
                  <tr>
                    <td
                      className="grant-applications__table-message"
                      colSpan={isAdmin ? 7 : 6}
                    >
                      Loading applications...
                    </td>
                  </tr>
                ) : error ? (
                  <tr>
                    <td
                      className="grant-applications__table-message"
                      colSpan={isAdmin ? 7 : 6}
                    >
                      Applications could not be loaded.
                    </td>
                  </tr>
                ) : displayedApplications.length === 0 ? (
                  <tr>
                    <td
                      className="grant-applications__table-message"
                      colSpan={isAdmin ? 7 : 6}
                    >
                      {statusFilter || beneficiaryStatusFilter
                        ? `No applications found matching the selected filter.`
                        : "No grant applications found."}
                    </td>
                  </tr>
                ) : (
                  displayedApplications.map((application) => (
                    <tr key={application.id}>
                      {!isBeneficiary && (
                        <td>
                          {application.beneficiaryName || "—"}
                        </td>
                      )}

                      <td>{application.schemeName || "—"}</td>

                      {(isBeneficiary || isAdmin) && (
                        <td>
                          {application.eligibilityEvaluation
                            ? `${application.eligibilityEvaluation.eligibilityStatus} · ${application.eligibilityEvaluation.scoreOutOf100 ?? "—"}%`
                            : "Not evaluated"}
                        </td>
                      )}

                      <td>₹{application.requestedAmount}</td>

                      <td>
                        {application.submittedAt
                          ? new Date(
                            application.submittedAt
                          ).toLocaleDateString()
                          : "—"}
                      </td>

                      <td>
                        <span className="grant-applications__status">
                          {formatStatus(application.status)}
                        </span>
                        {application.status === "APPROVED" && (() => {
                          const disbInfo = getDisbursementBadgeInfo(application);
                          const disbSummary = getDisbursementSummary(application);
                          return disbInfo ? (
                            <div style={{ fontSize: "0.76rem", color: "#475569", marginTop: "4px" }}>
                              <strong>{disbInfo.label}</strong>
                              {disbSummary && (
                                <div>
                                  Released: {formatCurrencyINR(disbSummary.disbursedAmount)} / {formatCurrencyINR(disbSummary.totalApprovedAmount)}
                                </div>
                              )}
                            </div>
                          ) : null;
                        })()}
                      </td>

                      <td>
                        <div className="grant-applications__actions">
                          <button
                            className="grant-applications__button grant-applications__button--secondary"
                            type="button"
                            onClick={() =>
                              handleViewDetails(application.id)
                            }
                          >
                            Details
                          </button>

                          {isBeneficiary && application.status === "DRAFT" && (
                            <>
                              <button
                                className="grant-applications__button grant-applications__button--secondary"
                                type="button"
                                onClick={() =>
                                  handleEdit(application)
                                }
                                disabled={saving}
                              >
                                Edit
                              </button>

                              <button
                                className="grant-applications__button grant-applications__button--primary"
                                type="button"
                                onClick={() =>
                                  handleSubmitDraft(application)
                                }
                                disabled={
                                  editingId === application.id || saving
                                }
                              >
                                Submit
                              </button>

                              <button
                                className="grant-applications__button grant-applications__button--secondary"
                                type="button"
                                onClick={() =>
                                  handleDeleteDraft(application)
                                }
                                disabled={saving}
                              >
                                Delete Draft
                              </button>
                            </>
                          )}

                          {isBeneficiary && application.status === "NEEDS_CORRECTION" && (
                            <button
                              className="grant-applications__button grant-applications__button--primary"
                              type="button"
                              onClick={() =>
                                handleEdit(application)
                              }
                              disabled={
                                editingId === application.id || saving
                              }
                            >
                              Edit and resubmit
                            </button>
                          )}

                          {isBeneficiary &&
                            application.status === "SUBMITTED" &&
                            application.withdrawalAllowed && (
                              <button
                                className="grant-applications__button grant-applications__button--secondary"
                                type="button"
                                onClick={() =>
                                  handleWithdraw(application)
                                }
                              >
                                Withdraw Application
                              </button>
                            )}
                        </div>
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>

          {isAdmin && pageInfo.totalPages > 1 && (
            <nav
              className="grant-applications__pagination"
              aria-label="Application pages"
            >
              <button
                className="grant-applications__button grant-applications__button--secondary"
                type="button"
                onClick={() =>
                  setPage((current) => Math.max(0, current - 1))
                }
                disabled={page === 0 || loading}
              >
                Previous
              </button>

              <span>
                Page {page + 1} of {pageInfo.totalPages}
              </span>

              <button
                className="grant-applications__button grant-applications__button--secondary"
                type="button"
                onClick={() =>
                  setPage((current) =>
                    Math.min(
                      pageInfo.totalPages - 1,
                      current + 1
                    )
                  )
                }
                disabled={
                  page + 1 >= pageInfo.totalPages || loading
                }
              >
                Next
              </button>
            </nav>
          )}
        </>
      )}

      {(!isBeneficiary || isTrackingPage) &&
        (detailsLoading ||
          detailsError ||
          selectedApplication) && (
          <section
            className="grant-applications__details"
            aria-labelledby="application-details-heading"
            aria-live="polite"
          >
            <h3 id="application-details-heading">
              Application details
            </h3>

            {detailsLoading && (
              <p>Loading application details...</p>
            )}

            {detailsError && (
              <div
                className="grant-applications__notice grant-applications__notice--error"
                role="alert"
              >
                {detailsError}
              </div>
            )}

            {selectedApplication && (
              <>
                <h4>{selectedApplication.schemeName}</h4>

                <p>
                  Application ID:{" "}
                  {selectedApplication.id}
                </p>

                <p>
                  Status:{" "}
                  <strong>
                    {formatStatus(selectedApplication.status)}
                  </strong>
                  {selectedApplication.status === "APPROVED" && (() => {
                    const disbInfo = getDisbursementBadgeInfo(selectedApplication);
                    return disbInfo ? (
                      <span style={{ marginLeft: "0.6rem", color: "#0369a1", fontWeight: 600 }}>
                        · {disbInfo.label}
                      </span>
                    ) : null;
                  })()}
                </p>

                {/* 5-Stage Verification Progress Stepper */}
                <div className="grant-stepper" aria-label="Verification Workflow Stages">
                  <div className={`grant-stepper__step ${getStepState(selectedApplication, 1)}`}>
                    <div className="grant-stepper__num">1</div>
                    <div className="grant-stepper__label">Submitted</div>
                  </div>
                  <div className={`grant-stepper__line ${getStepState(selectedApplication, 2) ? "grant-stepper__line--completed" : ""}`} />
                  <div className={`grant-stepper__step ${getStepState(selectedApplication, 2)}`}>
                    <div className="grant-stepper__num">2</div>
                    <div className="grant-stepper__label">Field Verification</div>
                  </div>
                  <div className={`grant-stepper__line ${getStepState(selectedApplication, 3) ? "grant-stepper__line--completed" : ""}`} />
                  <div className={`grant-stepper__step ${getStepState(selectedApplication, 3)}`}>
                    <div className="grant-stepper__num">3</div>
                    <div className="grant-stepper__label">District Review</div>
                  </div>
                  <div className={`grant-stepper__line ${getStepState(selectedApplication, 4) ? "grant-stepper__line--completed" : ""}`} />
                  <div className={`grant-stepper__step ${getStepState(selectedApplication, 4)}`}>
                    <div className="grant-stepper__num">4</div>
                    <div className="grant-stepper__label">Finance Approval</div>
                  </div>
                  <div className={`grant-stepper__line ${getStepState(selectedApplication, 5) ? "grant-stepper__line--completed" : ""}`} />
                  <div className={`grant-stepper__step ${getStepState(selectedApplication, 5)}`}>
                    <div className="grant-stepper__num">5</div>
                    <div className="grant-stepper__label">Disbursement</div>
                  </div>
                </div>

                {isBeneficiary && (
                  <p>
                    {getPendingActionText(selectedApplication)}
                  </p>
                )}

                {isBeneficiary &&
                  selectedApplication.status ===
                  "NEEDS_CORRECTION" && (
                    <div className="grant-applications__action-required">
                      <div className="grant-applications__action-header">
                        <span className="grant-applications__action-badge">
                          ACTION REQUIRED
                        </span>
                      </div>

                      <blockquote className="grant-applications__action-remarks">
                        {getLatestCorrectionRemarks(
                          selectedApplication
                        ) ||
                          "Please review the requested corrections and update your application."}
                      </blockquote>

                      <button
                        className="grant-applications__button grant-applications__button--primary"
                        type="button"
                        onClick={() =>
                          handleEdit(selectedApplication)
                        }
                      >
                        Edit and resubmit
                      </button>
                    </div>
                  )}

                <dl className="grant-applications__details-grid">
                  <div>
                    <dt>Requested amount</dt>
                    <dd>
                      ₹{selectedApplication.requestedAmount}
                    </dd>
                  </div>

                  <div>
                    <dt>Submitted</dt>
                    <dd>
                      {selectedApplication.submittedAt
                        ? new Date(
                          selectedApplication.submittedAt
                        ).toLocaleString()
                        : "Not submitted"}
                    </dd>
                  </div>

                  <div>
                    <dt>Application details</dt>
                    <dd>
                      {selectedApplication.applicationDetails ||
                        "—"}
                    </dd>
                  </div>

                  {selectedApplication.status === "APPROVED" && (() => {
                    const disbSummary = getDisbursementSummary(selectedApplication);
                    const disbInfo = getDisbursementBadgeInfo(selectedApplication);
                    return disbSummary ? (
                      <>
                        <div>
                          <dt>Disbursement status</dt>
                          <dd>{disbInfo?.label || "Awaiting Disbursement"}</dd>
                        </div>
                        <div>
                          <dt>Amount released</dt>
                          <dd>
                            {formatCurrencyINR(disbSummary.disbursedAmount)} of{" "}
                            {formatCurrencyINR(disbSummary.totalApprovedAmount)}
                          </dd>
                        </div>
                        <div>
                          <dt>Remaining balance</dt>
                          <dd>
                            {formatCurrencyINR(disbSummary.remainingAmount)}
                            {disbSummary.totalInstallmentsCount > 0 &&
                              ` (${disbSummary.releasedInstallmentsCount}/${disbSummary.totalInstallmentsCount} stages released)`}
                          </dd>
                        </div>
                      </>
                    ) : null;
                  })()}
                </dl>

                {selectedApplication.eligibilityEvaluation ? (
                  <section className="grant-applications__eligibility-result">
                    <h4>Eligibility evaluation</h4>

                    <p>
                      Result:{" "}
                      <strong>
                        {
                          selectedApplication
                            .eligibilityEvaluation
                            .eligibilityStatus
                        }
                      </strong>
                    </p>

                    <p>
                      Score:{" "}
                      {
                        selectedApplication
                          .eligibilityEvaluation
                          .scoreOutOf100
                      }
                      %
                    </p>

                    <p>
                      Explanation:{" "}
                      {
                        selectedApplication
                          .eligibilityEvaluation
                          .explanation
                      }
                    </p>

                    <h5>Mandatory criteria</h5>

                    <ul>
                      {(
                        selectedApplication
                          .eligibilityEvaluation
                          .mandatoryCriteria || []
                      ).map((criterion) => (
                        <li key={criterion.criterion}>
                          {criterion.criterion}:{" "}
                          {criterion.status} —{" "}
                          {criterion.explanation}
                        </li>
                      ))}
                    </ul>

                    <h5>Custom question results</h5>

                    <ul>
                      {(
                        selectedApplication
                          .eligibilityEvaluation
                          .customQuestionResults || []
                      ).map((result) => (
                        <li key={result.questionId}>
                          {result.questionText}:{" "}
                          {result.answer || "Not answered"} —{" "}
                          {result.pointsEarned} points.{" "}
                          {result.explanation}
                        </li>
                      ))}
                    </ul>

                    {(
                      selectedApplication
                        .eligibilityEvaluation
                        .failedCriteria || []
                    ).length > 0 && (
                        <>
                          <h5>Failed criteria</h5>

                          <ul>
                            {(
                              selectedApplication
                                .eligibilityEvaluation
                                .failedCriteria || []
                            ).map((criterion) => (
                              <li key={criterion}>
                                {criterion}
                              </li>
                            ))}
                          </ul>
                        </>
                      )}

                    {(
                      selectedApplication
                        .eligibilityEvaluation
                        .missingInformation || []
                    ).length > 0 && (
                        <>
                          <h5>Missing information</h5>

                          <ul>
                            {(
                              selectedApplication
                                .eligibilityEvaluation
                                .missingInformation || []
                            ).map((information) => (
                              <li key={information}>
                                {information}
                              </li>
                            ))}
                          </ul>
                        </>
                      )}
                  </section>
                ) : isAdmin ? (
                  <section className="grant-applications__eligibility-result">
                    <h4>Eligibility evaluation</h4>
                    <p>
                      No persisted eligibility evaluation is
                      available for this application.
                    </p>
                  </section>
                ) : null}

                <section className="grant-applications__eligibility-result">
                  <h4>Verification progress</h4>

                  <p>
                    Current status:{" "}
                    <strong>
                      {formatStatus(selectedApplication.status)}
                    </strong>

                    {selectedApplication.workflowStage
                      ? ` · ${selectedApplication.workflowStage ===
                        "FIELD"
                        ? "Stage 1: Field Verification"
                        : selectedApplication.workflowStage ===
                          "DISTRICT"
                          ? "Stage 2: District Review"
                          : "Stage 3: Finance Approval"
                      }`
                      : ""}
                  </p>

                  {!isBeneficiary &&
                    selectedApplication.workflowStage && (
                      <p>
                        Current assignee:{" "}
                        {selectedApplication.assignedOfficerName ||
                          "Awaiting assignment"}
                      </p>
                    )}

                  {selectedApplication.status === "APPROVED" && (() => {
                    const disbSummary = getDisbursementSummary(selectedApplication);
                    const disbInfo = getDisbursementBadgeInfo(selectedApplication);
                    return (
                      <div style={{ marginTop: "0.5rem" }}>
                        <p>
                          <strong>Disbursement Progress:</strong>{" "}
                          {disbInfo?.label || "Awaiting Disbursement"} — Released{" "}
                          <strong>{formatCurrencyINR(disbSummary?.disbursedAmount)}</strong> of{" "}
                          <strong>{formatCurrencyINR(disbSummary?.totalApprovedAmount)}</strong>{" "}
                          (Remaining:{" "}
                          <strong>{formatCurrencyINR(disbSummary?.remainingAmount)}</strong>).
                        </p>
                        {disbSummary?.installments?.length > 0 && (
                          <div className="grant-applications__table-wrap" style={{ marginTop: "0.75rem" }}>
                            <table className="grant-applications__table">
                              <thead>
                                <tr>
                                  <th scope="col">Stage</th>
                                  <th scope="col">Amount</th>
                                  <th scope="col">Status</th>
                                  <th scope="col">Planned Date</th>
                                  <th scope="col">Release Date</th>
                                  <th scope="col">Reference</th>
                                </tr>
                              </thead>
                              <tbody>
                                {disbSummary.installments.map((inst) => (
                                  <tr key={inst.id}>
                                    <td>
                                      Stage {inst.installmentNumber}: {inst.title}
                                    </td>
                                    <td>{formatCurrencyINR(inst.amount)}</td>
                                    <td>{formatStatus(inst.status)}</td>
                                    <td>{inst.plannedReleaseDate || "—"}</td>
                                    <td>
                                      {inst.actualDisbursementDate
                                        ? new Date(inst.actualDisbursementDate).toLocaleDateString("en-IN")
                                        : "—"}
                                    </td>
                                    <td>{inst.paymentReference || "—"}</td>
                                  </tr>
                                ))}
                              </tbody>
                            </table>
                          </div>
                        )}
                      </div>
                    );
                  })()}

                  {selectedApplication.documents?.length > 0 && (
                    <>
                      <h5>Submitted documents</h5>

                      <ul>
                        {selectedApplication.documents.map(
                          (document) => (
                            <li key={document.id}>
                              {document.fileName} · Uploaded by{" "}
                              {document.uploadedBy}{" "}

                              <button
                                className="grant-applications__button grant-applications__button--secondary"
                                type="button"
                                onClick={() =>
                                  handleDocumentPreview(
                                    selectedApplication.id,
                                    document
                                  )
                                }
                              >
                                View
                              </button>

                              <button
                                className="grant-applications__button grant-applications__button--secondary"
                                type="button"
                                onClick={() =>
                                  handleDocumentDownload(
                                    selectedApplication.id,
                                    document
                                  )
                                }
                              >
                                Download
                              </button>
                            </li>
                          )
                        )}
                      </ul>
                    </>
                  )}

                  {documentPreview && (
                    <section
                      className="grant-applications__document-preview"
                      role="dialog"
                      aria-modal="true"
                      aria-labelledby="document-preview-heading"
                    >
                      <div className="grant-applications__document-preview-heading">
                        <h3 id="document-preview-heading">
                          {documentPreview.fileName}
                        </h3>

                        <button
                          className="grant-applications__button grant-applications__button--secondary"
                          type="button"
                          onClick={closeDocumentPreview}
                        >
                          Close
                        </button>
                      </div>

                      {documentPreview.contentType?.startsWith(
                        "image/"
                      ) ? (
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
                    </section>
                  )}

                  <h5>Verification &amp; Decision Milestones</h5>

                  {selectedApplication.verificationHistory?.length ||
                    selectedApplication.statusHistory?.length ? (
                    <ol className="grant-applications__timeline">
                      {[
                        ...(selectedApplication.statusHistory || []).map(
                          (item) => ({
                            ...item,
                            timelineType: "status",
                          })
                        ),

                        ...(selectedApplication.verificationHistory || []).map(
                          (item) => ({
                            ...item,
                            timelineType: "verification",
                          })
                        ),
                      ]
                        .sort(
                          (left, right) =>
                            new Date(left.createdAt) -
                            new Date(right.createdAt)
                        )
                        .map((item) => {
                          const actorDisplay = isBeneficiary
                            ? item.actorName &&
                              (item.actorName
                                .toLowerCase()
                                .includes("officer") ||
                                item.actorName
                                  .toLowerCase()
                                  .includes("admin"))
                              ? "Verification Officer"
                              : item.actorName || "System"
                            : item.actorName || "System";

                          return (
                            <li
                              key={`${item.timelineType}-${item.id}`}
                            >
                              {item.timelineType ===
                                "verification" &&
                                `${item.stage}: `}

                              <strong>
                                {formatStatus(item.action)}
                              </strong>

                              <span>
                                {" "}
                                by {actorDisplay} at{" "}
                                {new Date(
                                  item.createdAt
                                ).toLocaleString()}

                                {!isBeneficiary &&
                                  (item.previousStatus ||
                                    item.newStatus) &&
                                  ` · ${formatStatus(item.previousStatus)} → ${formatStatus(item.newStatus)}`}

                                {!isBeneficiary &&
                                  item.nextStage &&
                                  ` · Next: ${item.nextStage}`}
                              </span>

                              {item.remarks && (
                                <p className="grant-applications__timeline-remarks">
                                  {item.remarks}
                                </p>
                              )}
                            </li>
                          );
                        })}
                    </ol>
                  ) : (
                    <p>
                      Your application has not entered verification
                      yet.
                    </p>
                  )}

                  {[
                    "APPROVED",
                    "REJECTED",
                    "WITHDRAWN",
                  ].includes(selectedApplication.status) && (
                      <>
                        <h5>Final remarks</h5>

                        {selectedApplication.statusHistory?.some(
                          (item) => item.remarks
                        ) ? (
                          <ul>
                            {selectedApplication.statusHistory
                              .filter((item) => item.remarks)
                              .map((item) => (
                                <li key={item.id}>
                                  {formatStatus(item.action)}{" "}
                                  by{" "}
                                  {isBeneficiary
                                    ? "Verification Officer"
                                    : item.actorName}{" "}
                                  at{" "}
                                  {new Date(
                                    item.createdAt
                                  ).toLocaleString()}
                                  : {item.remarks}
                                </li>
                              ))}
                          </ul>
                        ) : selectedApplication.verificationHistory?.some(
                          (item) => item.remarks
                        ) ? (
                          <ul>
                            {selectedApplication.verificationHistory
                              .filter((item) => item.remarks)
                              .map((item) => (
                                <li key={item.id}>
                                  {formatStatus(item.action)}{" "}
                                  by{" "}
                                  {isBeneficiary
                                    ? "Verification Officer"
                                    : item.actorName}{" "}
                                  at{" "}
                                  {new Date(
                                    item.createdAt
                                  ).toLocaleString()}
                                  : {item.remarks}
                                </li>
                              ))}
                          </ul>
                        ) : (
                          <p>No additional remarks were provided.</p>
                        )}
                      </>
                    )}
                </section>
              </>
            )}
          </section>
        )}
    </section>
  );
}

export default GrantApplicationManagement;
