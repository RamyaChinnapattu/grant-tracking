import { useEffect, useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import {
  AlertCircle,
  AlertTriangle,
  ArrowLeft,
  ArrowRight,
  Award,
  Building2,
  Calendar,
  CheckCircle2,
  Clock,
  DollarSign,
  Edit3,
  HelpCircle,
  Layers,
  RefreshCw,
  Search,
  Sparkles,
  User,
  XCircle,
} from "lucide-react";
import { checkSchemeEligibility, getSchemes } from "../services/schemeService";
import {
  createBeneficiary,
  getMyBeneficiaryProfile,
  updateMyBeneficiaryProfile,
} from "../services/beneficiaryService";
import "../styles/BeneficiaryEligibility.css";

const CATEGORY_OPTIONS = [
  "GENERAL",
  "OBC",
  "SC",
  "ST",
  "EWS",
  "MINORITY",
  "OTHER",
];

function formatCurrency(amount) {
  if (amount == null || isNaN(amount)) return "—";
  return new Intl.NumberFormat("en-IN", {
    style: "currency",
    currency: "INR",
    maximumFractionDigits: 0,
  }).format(amount);
}

function formatDate(dateString) {
  if (!dateString) return "—";
  try {
    const date = new Date(dateString);
    if (isNaN(date.getTime())) return dateString;
    return date.toLocaleDateString("en-IN", {
      year: "numeric",
      month: "short",
      day: "numeric",
    });
  } catch {
    return dateString;
  }
}

function calculateAge(dateOfBirth) {
  if (!dateOfBirth) return null;
  const dob = new Date(dateOfBirth);
  if (isNaN(dob.getTime())) return null;
  const today = new Date();
  let age = today.getFullYear() - dob.getFullYear();
  const m = today.getMonth() - dob.getMonth();
  if (m < 0 || (m === 0 && today.getDate() < dob.getDate())) {
    age--;
  }
  return age;
}

function BeneficiaryEligibility() {
  const navigate = useNavigate();
  const location = useLocation();

  // Stepper state: 1 = Scheme, 2 = Profile, 3 = Questionnaire, 4 = Results
  const [currentStep, setCurrentStep] = useState(1);

  // Data states
  const [schemes, setSchemes] = useState([]);
  const [selectedScheme, setSelectedScheme] = useState(null);
  const [profile, setProfile] = useState(null);
  const [profileExists, setProfileExists] = useState(false);

  // Questionnaire answers: { [questionId]: answerValue }
  const [answers, setAnswers] = useState({});

  // Profile edit inline form
  const [isEditingProfile, setIsEditingProfile] = useState(false);
  const [profileForm, setProfileForm] = useState({
    dateOfBirth: "",
    annualIncome: "",
    category: "",
    phone: "",
    address: "",
    state: "",
    district: "",
  });

  // Search & Filter
  const [searchQuery, setSearchQuery] = useState("");
  const [selectedDepartment, setSelectedDepartment] = useState("");

  // Loading & Error states
  const [loading, setLoading] = useState(true);
  const [evaluating, setEvaluating] = useState(false);
  const [savingProfile, setSavingProfile] = useState(false);
  const [apiError, setApiError] = useState("");
  const [profileSuccessMsg, setProfileSuccessMsg] = useState("");
  const [validationErrors, setValidationErrors] = useState([]);

  // Backend evaluation result
  const [evaluationResult, setEvaluationResult] = useState(null);

  // Initial load: Fetch schemes and profile
  useEffect(() => {
    let mounted = true;

    async function loadInitialData() {
      setLoading(true);
      setApiError("");
      try {
        const [schemesRes, profileRes] = await Promise.allSettled([
          getSchemes(),
          getMyBeneficiaryProfile(),
        ]);

        if (mounted) {
          if (schemesRes.status === "fulfilled") {
            const activeOnly = (schemesRes.value.data || []).filter(
              (s) => s.status === "ACTIVE"
            );
            setSchemes(activeOnly);

            // Preselect scheme if passed in navigation state
            if (location.state?.schemeId) {
              const matched = activeOnly.find(
                (s) => String(s.id) === String(location.state.schemeId)
              );
              if (matched) {
                setSelectedScheme(matched);
                // Pre-fill answers if passed
                if (location.state.eligibilityAnswers) {
                  setAnswers(location.state.eligibilityAnswers);
                }
              }
            }
          } else {
            setApiError("Unable to load government schemes. Please try again.");
          }

          if (profileRes.status === "fulfilled" && profileRes.value.data) {
            const p = profileRes.value.data;
            setProfile(p);
            setProfileExists(Boolean(p.id));
            setProfileForm({
              dateOfBirth: p.dateOfBirth || "",
              annualIncome: p.annualIncome != null ? String(p.annualIncome) : "",
              category: p.category || "",
              phone: p.phone || "",
              address: p.address || "",
              state: p.state || "",
              district: p.district || "",
            });
          }
        }
      } catch (err) {
        if (mounted) {
          setApiError(
            err.response?.data?.message ||
              "An unexpected error occurred while loading data."
          );
        }
      } finally {
        if (mounted) setLoading(false);
      }
    }

    loadInitialData();

    return () => {
      mounted = false;
    };
  }, [location.state]);

  // Handle scheme selection
  const handleSelectScheme = (scheme) => {
    setSelectedScheme(scheme);
    setEvaluationResult(null);
    setValidationErrors([]);
    setCurrentStep(2);
    window.scrollTo({ top: 0, behavior: "smooth" });
  };

  // Handle answer change
  const handleAnswerChange = (questionId, value) => {
    setAnswers((prev) => ({
      ...prev,
      [questionId]: value,
    }));
    // Clear validation error for this question if it was marked
    if (validationErrors.length > 0) {
      setValidationErrors((prev) =>
        prev.filter((err) => err.questionId !== questionId)
      );
    }
  };

  // Profile form change
  const handleProfileFormChange = (e) => {
    const { name, value } = e.target;
    setProfileForm((prev) => ({
      ...prev,
      [name]: value,
    }));
  };

  // Save profile updates via beneficiaryService
  const handleSaveProfile = async (e) => {
    e.preventDefault();
    setSavingProfile(true);
    setApiError("");
    setProfileSuccessMsg("");

    try {
      const payload = {
        dateOfBirth: profileForm.dateOfBirth || null,
        annualIncome:
          profileForm.annualIncome !== ""
            ? Number(profileForm.annualIncome)
            : null,
        category: profileForm.category || null,
        phone: profileForm.phone || null,
        address: profileForm.address || null,
        state: profileForm.state || null,
        district: profileForm.district || null,
      };

      const response = profileExists
        ? await updateMyBeneficiaryProfile(payload)
        : await createBeneficiary(payload);

      const updated = response.data;
      setProfile(updated);
      setProfileExists(Boolean(updated.id));
      setIsEditingProfile(false);
      setProfileSuccessMsg("Profile information updated successfully.");
      setTimeout(() => setProfileSuccessMsg(""), 4000);
    } catch (err) {
      setApiError(
        err.response?.data?.message ||
          err.response?.data?.error ||
          "Failed to update profile. Please verify your details."
      );
    } finally {
      setSavingProfile(false);
    }
  };

  // Validate questions before evaluation
  const validateQuestions = () => {
    if (!selectedScheme) return false;
    const questions = selectedScheme.eligibilityQuestions || [];
    const missing = [];

    for (const q of questions) {
      if (q.required) {
        const answer = answers[q.id];
        if (answer === undefined || answer === null || String(answer).trim() === "") {
          missing.push({
            questionId: q.id,
            message: `"${q.questionText}" is required for eligibility evaluation.`,
          });
        }
      }
    }

    setValidationErrors(missing);
    return missing.length === 0;
  };

  // Submit eligibility check to backend scoring engine
  const handleEvaluateEligibility = async () => {
    if (!selectedScheme) return;

    if (!validateQuestions()) {
      window.scrollTo({ top: 200, behavior: "smooth" });
      return;
    }

    setEvaluating(true);
    setApiError("");

    try {
      const answersPayload = Object.entries(answers)
        .filter(([, val]) => val !== "" && val != null)
        .map(([qId, val]) => ({
          questionId: Number(qId),
          answer: String(val),
        }));

      const res = await checkSchemeEligibility(selectedScheme.id, answersPayload);
      setEvaluationResult(res.data);
      setCurrentStep(4);
      window.scrollTo({ top: 0, behavior: "smooth" });
    } catch (err) {
      setApiError(
        err.response?.data?.message ||
          err.response?.data?.error ||
          "Eligibility evaluation failed. Please check your network and try again."
      );
    } finally {
      setEvaluating(false);
    }
  };

  // Handoff to Grant Application
  const handleProceedToApplication = () => {
    if (!selectedScheme) return;
    navigate("/beneficiary/apply", {
      state: {
        schemeId: selectedScheme.id,
        eligibilityAnswers: answers,
        prefilledNotice: `Pre-selected scheme "${selectedScheme.schemeName}" and pre-filled answers from your successful eligibility check.`,
      },
    });
  };

  // Missing profile requirements check
  const missingProfileFields = [];
  if (!profile?.dateOfBirth) missingProfileFields.push("Date of Birth");
  if (profile?.annualIncome == null) missingProfileFields.push("Annual Income");
  if (!profile?.category) missingProfileFields.push("Category");

  // Departments for filtering
  const departments = Array.from(
    new Set(schemes.map((s) => s.department).filter(Boolean))
  );

  // Filtered schemes
  const filteredSchemes = schemes.filter((scheme) => {
    const matchesSearch =
      !searchQuery ||
      scheme.schemeName?.toLowerCase().includes(searchQuery.toLowerCase()) ||
      scheme.description?.toLowerCase().includes(searchQuery.toLowerCase()) ||
      scheme.department?.toLowerCase().includes(searchQuery.toLowerCase());
    const matchesDept =
      !selectedDepartment || scheme.department === selectedDepartment;
    return matchesSearch && matchesDept;
  });

  if (loading) {
    return (
      <div className="eligibility-container">
        <div className="eligibility-loading">
          <RefreshCw className="eligibility-spinner" size={32} />
          <p>Loading available government schemes and beneficiary profile...</p>
        </div>
      </div>
    );
  }

  return (
    <div className="eligibility-container">
      {/* Page Header */}
      <header className="eligibility-header">
        <div className="eligibility-header__title">
          <div className="eligibility-header__icon">
            <Sparkles size={24} />
          </div>
          <div>
            <h1>Government Scheme Eligibility Checker</h1>
            <p>
              Instantly evaluate your eligibility against official government scheme criteria
              and scoring rules before applying.
            </p>
          </div>
        </div>
      </header>

      {/* Global Alerts */}
      {apiError && (
        <div className="eligibility-alert eligibility-alert--error" role="alert">
          <AlertCircle size={20} />
          <span>{apiError}</span>
        </div>
      )}
      {profileSuccessMsg && (
        <div className="eligibility-alert eligibility-alert--success" role="status">
          <CheckCircle2 size={20} />
          <span>{profileSuccessMsg}</span>
        </div>
      )}

      {/* Stepper Progression */}
      <nav className="eligibility-stepper" aria-label="Eligibility Progress">
        <button
          type="button"
          className={`stepper-step ${currentStep === 1 ? "stepper-step--active" : ""} ${currentStep > 1 ? "stepper-step--completed" : ""}`}
          onClick={() => setCurrentStep(1)}
        >
          <span className="stepper-step__num">1</span>
          <span className="stepper-step__label">Select Scheme</span>
        </button>
        <div className="stepper-divider" />
        <button
          type="button"
          className={`stepper-step ${currentStep === 2 ? "stepper-step--active" : ""} ${currentStep > 2 ? "stepper-step--completed" : ""}`}
          onClick={() => selectedScheme && setCurrentStep(2)}
          disabled={!selectedScheme}
        >
          <span className="stepper-step__num">2</span>
          <span className="stepper-step__label">Profile Review</span>
        </button>
        <div className="stepper-divider" />
        <button
          type="button"
          className={`stepper-step ${currentStep === 3 ? "stepper-step--active" : ""} ${currentStep > 3 ? "stepper-step--completed" : ""}`}
          onClick={() => selectedScheme && setCurrentStep(3)}
          disabled={!selectedScheme}
        >
          <span className="stepper-step__num">3</span>
          <span className="stepper-step__label">Questionnaire</span>
        </button>
        <div className="stepper-divider" />
        <button
          type="button"
          className={`stepper-step ${currentStep === 4 ? "stepper-step--active" : ""}`}
          onClick={() => evaluationResult && setCurrentStep(4)}
          disabled={!evaluationResult}
        >
          <span className="stepper-step__num">4</span>
          <span className="stepper-step__label">Results</span>
        </button>
      </nav>

      {/* Selected Scheme Badge Header if past step 1 */}
      {selectedScheme && currentStep > 1 && (
        <div className="selected-scheme-banner">
          <div className="selected-scheme-banner__info">
            <span className="scheme-badge scheme-badge--active">Active Scheme</span>
            <h3>{selectedScheme.schemeName}</h3>
            <p>
              {selectedScheme.department || "Government Department"} • Max Grant:{" "}
              <strong>{formatCurrency(selectedScheme.maximumGrantAmount)}</strong> • Required Threshold:{" "}
              <strong>{selectedScheme.eligibilityThreshold || 70}%</strong>
            </p>
          </div>
          <button
            type="button"
            className="eligibility-btn eligibility-btn--outline"
            onClick={() => setCurrentStep(1)}
          >
            Change Scheme
          </button>
        </div>
      )}

      {/* STEP 1: SCHEME SELECTION */}
      {currentStep === 1 && (
        <section className="eligibility-step-content" aria-labelledby="step1-heading">
          <div className="eligibility-step-header">
            <div>
              <h2 id="step1-heading">Step 1: Choose an Active Scheme</h2>
              <p>Select the government grant or subsidy program you wish to evaluate for.</p>
            </div>
            <div className="scheme-filter-bar">
              <div className="scheme-search-input">
                <Search size={16} />
                <input
                  type="text"
                  placeholder="Search scheme name, department..."
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                />
              </div>
              {departments.length > 0 && (
                <select
                  value={selectedDepartment}
                  onChange={(e) => setSelectedDepartment(e.target.value)}
                  className="scheme-dept-select"
                >
                  <option value="">All Departments</option>
                  {departments.map((dept) => (
                    <option key={dept} value={dept}>
                      {dept}
                    </option>
                  ))}
                </select>
              )}
            </div>
          </div>

          {filteredSchemes.length === 0 ? (
            <div className="eligibility-empty-state">
              <Building2 size={48} />
              <h3>No Active Schemes Found</h3>
              <p>
                {searchQuery || selectedDepartment
                  ? "No active schemes match your current search criteria. Try clearing the filters."
                  : "There are currently no active government schemes open for eligibility evaluation. Please check back later."}
              </p>
              {(searchQuery || selectedDepartment) && (
                <button
                  type="button"
                  className="eligibility-btn eligibility-btn--outline"
                  onClick={() => {
                    setSearchQuery("");
                    setSelectedDepartment("");
                  }}
                >
                  Clear Filters
                </button>
              )}
            </div>
          ) : (
            <div className="scheme-cards-grid">
              {filteredSchemes.map((scheme) => {
                const isSelected = selectedScheme?.id === scheme.id;
                const questionsCount = scheme.eligibilityQuestions?.length || 0;
                return (
                  <article
                    key={scheme.id}
                    className={`scheme-card ${isSelected ? "scheme-card--selected" : ""}`}
                  >
                    <div className="scheme-card__header">
                      <span className="scheme-card__dept">
                        <Building2 size={14} />
                        {scheme.department || "General"}
                      </span>
                      <span className="scheme-badge scheme-badge--active">Active</span>
                    </div>

                    <h3 className="scheme-card__title">{scheme.schemeName}</h3>
                    <p className="scheme-card__desc">
                      {scheme.description || "No description provided."}
                    </p>

                    <div className="scheme-card__stats">
                      <div className="scheme-stat">
                        <span className="scheme-stat__label">Max Grant</span>
                        <span className="scheme-stat__val">
                          {formatCurrency(scheme.maximumGrantAmount)}
                        </span>
                      </div>
                      <div className="scheme-stat">
                        <span className="scheme-stat__label">Threshold</span>
                        <span className="scheme-stat__val">
                          {scheme.eligibilityThreshold != null
                            ? `${scheme.eligibilityThreshold}%`
                            : "70%"}
                        </span>
                      </div>
                      <div className="scheme-stat">
                        <span className="scheme-stat__label">Questions</span>
                        <span className="scheme-stat__val">{questionsCount}</span>
                      </div>
                    </div>

                    <div className="scheme-card__criteria-preview">
                      <h4>Eligibility Summary:</h4>
                      <ul>
                        <li>
                          <strong>Age:</strong>{" "}
                          {scheme.minimumAge != null && scheme.maximumAge != null
                            ? `${scheme.minimumAge} – ${scheme.maximumAge} years`
                            : "Not restricted"}
                        </li>
                        <li>
                          <strong>Annual Income:</strong>{" "}
                          {scheme.maximumAnnualIncome != null
                            ? `Up to ${formatCurrency(scheme.maximumAnnualIncome)}`
                            : "No upper limit"}
                        </li>
                        <li>
                          <strong>Eligible Categories:</strong>{" "}
                          {scheme.eligibleCategories?.length > 0
                            ? Array.from(scheme.eligibleCategories).join(", ")
                            : "All Categories"}
                        </li>
                        {scheme.applicationEndDate && (
                          <li>
                            <strong>Deadline:</strong>{" "}
                            {formatDate(scheme.applicationEndDate)}
                          </li>
                        )}
                      </ul>
                    </div>

                    <div className="scheme-card__footer">
                      <button
                        type="button"
                        className={`eligibility-btn ${isSelected ? "eligibility-btn--primary" : "eligibility-btn--outline"}`}
                        onClick={() => handleSelectScheme(scheme)}
                      >
                        {isSelected ? "Selected ✓ Check Eligibility" : "Select Scheme"}
                      </button>
                    </div>
                  </article>
                );
              })}
            </div>
          )}
        </section>
      )}

      {/* STEP 2: BENEFICIARY PROFILE REVIEW */}
      {currentStep === 2 && selectedScheme && (
        <section className="eligibility-step-content" aria-labelledby="step2-heading">
          <div className="eligibility-step-header">
            <div>
              <h2 id="step2-heading">Step 2: Review Your Beneficiary Profile</h2>
              <p>
                The backend scoring engine verifies mandatory criteria (Age, Income, and Category)
                directly from your profile. Please review and ensure your details are complete.
              </p>
            </div>
            {!isEditingProfile && (
              <button
                type="button"
                className="eligibility-btn eligibility-btn--outline"
                onClick={() => setIsEditingProfile(true)}
              >
                <Edit3 size={16} /> Edit Profile Details
              </button>
            )}
          </div>

          {/* Missing Profile Warning */}
          {missingProfileFields.length > 0 && !isEditingProfile && (
            <div className="eligibility-alert eligibility-alert--warning" role="alert">
              <AlertTriangle size={22} />
              <div>
                <strong>Incomplete Profile Information:</strong>
                <p>
                  Your profile is missing: <strong>{missingProfileFields.join(", ")}</strong>.
                  The backend engine will flag mandatory criteria as missing unless you provide them.
                </p>
                <button
                  type="button"
                  className="eligibility-btn eligibility-btn--warning-sm"
                  onClick={() => setIsEditingProfile(true)}
                >
                  Complete Missing Information Now
                </button>
              </div>
            </div>
          )}

          {/* Profile Details Card */}
          {!isEditingProfile ? (
            <div className="profile-details-card">
              <div className="profile-details-grid">
                <div className="profile-field">
                  <span className="profile-field__label">Full Name</span>
                  <span className="profile-field__val">
                    {profile?.fullName || "Not provided"}
                  </span>
                </div>
                <div className="profile-field">
                  <span className="profile-field__label">Registered Email</span>
                  <span className="profile-field__val">
                    {profile?.email || "Not provided"}
                  </span>
                </div>
                <div className="profile-field">
                  <span className="profile-field__label">Contact Phone</span>
                  <span className="profile-field__val">
                    {profile?.phone || "Not provided"}
                  </span>
                </div>
                <div className="profile-field">
                  <span className="profile-field__label">Date of Birth</span>
                  <span className="profile-field__val">
                    {profile?.dateOfBirth ? (
                      <>
                        {formatDate(profile.dateOfBirth)} (Age: {calculateAge(profile.dateOfBirth)} yrs)
                      </>
                    ) : (
                      <span className="text-missing">Missing</span>
                    )}
                  </span>
                </div>
                <div className="profile-field">
                  <span className="profile-field__label">Annual Income</span>
                  <span className="profile-field__val">
                    {profile?.annualIncome != null ? (
                      formatCurrency(profile.annualIncome)
                    ) : (
                      <span className="text-missing">Missing</span>
                    )}
                  </span>
                </div>
                <div className="profile-field">
                  <span className="profile-field__label">Category</span>
                  <span className="profile-field__val">
                    {profile?.category ? (
                      <span className="category-tag">{profile.category}</span>
                    ) : (
                      <span className="text-missing">Missing</span>
                    )}
                  </span>
                </div>
                <div className="profile-field">
                  <span className="profile-field__label">State</span>
                  <span className="profile-field__val">{profile?.state || "Not provided"}</span>
                </div>
                <div className="profile-field">
                  <span className="profile-field__label">District</span>
                  <span className="profile-field__val">
                    {profile?.district || "Not provided"}
                  </span>
                </div>
                <div className="profile-field profile-field--wide">
                  <span className="profile-field__label">Permanent Address</span>
                  <span className="profile-field__val">{profile?.address || "Not provided"}</span>
                </div>
              </div>
            </div>
          ) : (
            /* Inline Profile Editor */
            <form className="profile-edit-form" onSubmit={handleSaveProfile}>
              <h3>Update Beneficiary Information</h3>
              <p className="form-help">
                Updating your profile will save your official details in the system.
              </p>

              <div className="form-grid">
                <label>
                  <span>Date of Birth *</span>
                  <input
                    type="date"
                    name="dateOfBirth"
                    value={profileForm.dateOfBirth}
                    onChange={handleProfileFormChange}
                    max={new Date().toISOString().split("T")[0]}
                    required
                  />
                </label>

                <label>
                  <span>Annual Income (INR) *</span>
                  <input
                    type="number"
                    name="annualIncome"
                    value={profileForm.annualIncome}
                    onChange={handleProfileFormChange}
                    placeholder="e.g. 250000"
                    min="0"
                    step="1000"
                    required
                  />
                </label>

                <label>
                  <span>Social Category *</span>
                  <select
                    name="category"
                    value={profileForm.category}
                    onChange={handleProfileFormChange}
                    required
                  >
                    <option value="">Select category...</option>
                    {CATEGORY_OPTIONS.map((cat) => (
                      <option key={cat} value={cat}>
                        {cat}
                      </option>
                    ))}
                  </select>
                </label>

                <label>
                  <span>Phone Number</span>
                  <input
                    type="tel"
                    name="phone"
                    value={profileForm.phone}
                    onChange={handleProfileFormChange}
                    placeholder="10-digit mobile number"
                  />
                </label>

                <label>
                  <span>State</span>
                  <input
                    type="text"
                    name="state"
                    value={profileForm.state}
                    onChange={handleProfileFormChange}
                    placeholder="e.g. Karnataka"
                  />
                </label>

                <label>
                  <span>District</span>
                  <input
                    type="text"
                    name="district"
                    value={profileForm.district}
                    onChange={handleProfileFormChange}
                    placeholder="e.g. Bengaluru Urban"
                  />
                </label>

                <label className="form-field--wide">
                  <span>Residential Address</span>
                  <textarea
                    name="address"
                    value={profileForm.address}
                    onChange={handleProfileFormChange}
                    rows={2}
                    placeholder="Street, locality, postal code"
                  />
                </label>
              </div>

              <div className="form-actions">
                <button
                  type="submit"
                  className="eligibility-btn eligibility-btn--primary"
                  disabled={savingProfile}
                >
                  {savingProfile ? (
                    <>
                      <RefreshCw className="eligibility-spinner" size={16} /> Saving Profile...
                    </>
                  ) : (
                    "Save & Apply to Profile"
                  )}
                </button>
                <button
                  type="button"
                  className="eligibility-btn eligibility-btn--ghost"
                  onClick={() => setIsEditingProfile(false)}
                  disabled={savingProfile}
                >
                  Cancel
                </button>
              </div>
            </form>
          )}

          {/* Navigation Controls */}
          <div className="step-actions-footer">
            <button
              type="button"
              className="eligibility-btn eligibility-btn--outline"
              onClick={() => setCurrentStep(1)}
            >
              <ArrowLeft size={16} /> Back to Scheme Selection
            </button>
            <button
              type="button"
              className="eligibility-btn eligibility-btn--primary"
              onClick={() => setCurrentStep(3)}
            >
              Continue to Questionnaire <ArrowRight size={16} />
            </button>
          </div>
        </section>
      )}

      {/* STEP 3: DYNAMIC ELIGIBILITY QUESTIONNAIRE */}
      {currentStep === 3 && selectedScheme && (
        <section className="eligibility-step-content" aria-labelledby="step3-heading">
          <div className="eligibility-step-header">
            <div>
              <h2 id="step3-heading">Step 3: Answer Scheme Eligibility Questions</h2>
              <p>
                Provide accurate responses to the scheme-specific eligibility questions below.
                Required questions are marked with an asterisk (*).
              </p>
            </div>
            <div className="questions-count-badge">
              {selectedScheme.eligibilityQuestions?.length || 0} Questions Configured
            </div>
          </div>

          {/* Validation Errors Notice */}
          {validationErrors.length > 0 && (
            <div className="eligibility-alert eligibility-alert--error" role="alert">
              <AlertCircle size={20} />
              <div>
                <strong>Please complete all required questions:</strong>
                <ul>
                  {validationErrors.map((err, idx) => (
                    <li key={idx}>{err.message}</li>
                  ))}
                </ul>
              </div>
            </div>
          )}

          {(!selectedScheme.eligibilityQuestions ||
            selectedScheme.eligibilityQuestions.length === 0) ? (
            <div className="no-questions-notice">
              <CheckCircle2 size={36} />
              <h3>No Custom Questions for this Scheme</h3>
              <p>
                This scheme evaluates eligibility solely based on mandatory profile criteria
                (Age, Annual Income, and Beneficiary Category). You can proceed straight to
                evaluation.
              </p>
            </div>
          ) : (
            <div className="questions-container">
              {selectedScheme.eligibilityQuestions.map((question, idx) => {
                const currentAnswer = answers[question.id] || "";
                const hasError = validationErrors.some(
                  (e) => e.questionId === question.id
                );

                return (
                  <fieldset
                    key={question.id}
                    className={`question-card ${hasError ? "question-card--error" : ""}`}
                  >
                    <legend className="question-card__legend">
                      <span className="question-number">Q{idx + 1}</span>
                      <span className="question-text">
                        {question.questionText}
                        {question.required && <span className="required-star">*</span>}
                      </span>
                      {question.maximumPoints != null && (
                        <span className="question-points">
                          Max Weight: {question.maximumPoints} pts
                        </span>
                      )}
                    </legend>

                    {/* Question Options */}
                    <div className="question-options">
                      {question.answerOptions?.map((option) => {
                        const isChecked = String(currentAnswer) === String(option.value);
                        return (
                          <label
                            key={option.value}
                            className={`option-label ${isChecked ? "option-label--selected" : ""}`}
                          >
                            <input
                              type="radio"
                              name={`question-${question.id}`}
                              value={option.value}
                              checked={isChecked}
                              onChange={() => handleAnswerChange(question.id, option.value)}
                            />
                            <div className="option-content">
                              <span className="option-title">{option.label}</span>
                              {option.points != null && (
                                <span className="option-subtext">
                                  Score weight: {option.points} pts
                                </span>
                              )}
                            </div>
                          </label>
                        );
                      })}
                    </div>

                    {hasError && (
                      <p className="question-error-text">
                        * Please select an answer for this question.
                      </p>
                    )}
                  </fieldset>
                );
              })}
            </div>
          )}

          {/* Questionnaire Actions */}
          <div className="step-actions-footer">
            <button
              type="button"
              className="eligibility-btn eligibility-btn--outline"
              onClick={() => setCurrentStep(2)}
              disabled={evaluating}
            >
              <ArrowLeft size={16} /> Back to Profile Review
            </button>
            <button
              type="button"
              className="eligibility-btn eligibility-btn--primary"
              onClick={handleEvaluateEligibility}
              disabled={evaluating}
            >
              {evaluating ? (
                <>
                  <RefreshCw className="eligibility-spinner" size={18} />
                  Evaluating Eligibility with Backend Engine...
                </>
              ) : (
                <>
                  Evaluate Eligibility Now <Award size={18} />
                </>
              )}
            </button>
          </div>
        </section>
      )}

      {/* STEP 4: EXPLAINABLE RESULTS */}
      {currentStep === 4 && evaluationResult && selectedScheme && (
        <section className="eligibility-step-content" aria-labelledby="step4-heading">
          {/* Status Result Hero Card */}
          <div
            className={`results-hero-card results-hero-card--${evaluationResult.eligibilityStatus?.toLowerCase()}`}
          >
            <div className="results-hero-card__icon">
              {evaluationResult.eligibilityStatus === "ELIGIBLE" && (
                <CheckCircle2 size={44} />
              )}
              {evaluationResult.eligibilityStatus === "INELIGIBLE" && (
                <XCircle size={44} />
              )}
              {evaluationResult.eligibilityStatus === "NEEDS_MORE_INFORMATION" && (
                <AlertTriangle size={44} />
              )}
            </div>

            <div className="results-hero-card__summary">
              <span className={`status-pill status-pill--${evaluationResult.eligibilityStatus?.toLowerCase()}`}>
                {evaluationResult.eligibilityStatus === "ELIGIBLE" && "Eligible for Scheme"}
                {evaluationResult.eligibilityStatus === "INELIGIBLE" && "Not Eligible"}
                {evaluationResult.eligibilityStatus === "NEEDS_MORE_INFORMATION" &&
                  "More Information Required"}
              </span>
              <h2 id="step4-heading">{evaluationResult.explanation}</h2>
              <p>
                Evaluation for <strong>{selectedScheme.schemeName}</strong> processed on{" "}
                {evaluationResult.evaluatedAt
                  ? formatDate(evaluationResult.evaluatedAt)
                  : "today"}
                .
              </p>
            </div>

            {/* Score Metric Tile */}
            <div className="results-score-tile">
              <span className="results-score-tile__label">Custom Score</span>
              <span className="results-score-tile__value">
                {evaluationResult.scoreOutOf100 != null
                  ? `${evaluationResult.scoreOutOf100} / 100`
                  : "0 / 100"}
              </span>
              <span className="results-score-tile__threshold">
                Required Threshold: {selectedScheme.eligibilityThreshold || 70}%
              </span>
            </div>
          </div>

          {/* Missing Information / Failures Alerts */}
          {evaluationResult.missingInformation?.length > 0 && (
            <div className="results-alert-box results-alert-box--warning">
              <h4>
                <AlertCircle size={18} /> Missing Information Required
              </h4>
              <p>
                The scoring engine could not fully determine eligibility because the following
                details are missing:
              </p>
              <ul>
                {evaluationResult.missingInformation.map((item, i) => (
                  <li key={i}>{item}</li>
                ))}
              </ul>
            </div>
          )}

          {evaluationResult.failedCriteria?.length > 0 && (
            <div className="results-alert-box results-alert-box--error">
              <h4>
                <XCircle size={18} /> Criteria Not Met
              </h4>
              <p>The applicant did not satisfy the following mandatory condition(s):</p>
              <ul>
                {evaluationResult.failedCriteria.map((item, i) => (
                  <li key={i}>{item}</li>
                ))}
              </ul>
            </div>
          )}

          {/* Mandatory Criteria Breakdown */}
          <div className="results-breakdown-section">
            <h3>Mandatory Demographic Criteria</h3>
            <p>
              Statutory requirements enforced independently of the numerical score. All mandatory
              criteria must pass.
            </p>

            <div className="criteria-table-wrapper">
              <table className="criteria-table">
                <thead>
                  <tr>
                    <th>Criterion</th>
                    <th>Status</th>
                    <th>Scheme Requirement</th>
                    <th>Your Profile Value</th>
                    <th>Evaluation Details</th>
                  </tr>
                </thead>
                <tbody>
                  {(evaluationResult.mandatoryCriteriaResults || []).map((crit, idx) => {
                    const statusClass =
                      crit.status === "PASS"
                        ? "status-pass"
                        : crit.status === "FAIL"
                          ? "status-fail"
                          : "status-missing";
                    return (
                      <tr key={idx}>
                        <td>
                          <strong>{crit.criterion}</strong>
                        </td>
                        <td>
                          <span className={`criteria-badge ${statusClass}`}>
                            {crit.status === "PASS" && "✓ Pass"}
                            {crit.status === "FAIL" && "✗ Fail"}
                            {crit.status === "MISSING" && "⚠ Missing"}
                          </span>
                        </td>
                        <td>{crit.expected || "—"}</td>
                        <td>{crit.actual || "—"}</td>
                        <td>{crit.explanation}</td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          </div>

          {/* Custom Question Results Breakdown */}
          {evaluationResult.customQuestionResults?.length > 0 && (
            <div className="results-breakdown-section">
              <h3>Custom Question Evaluation Breakdown</h3>
              <p>Detailed score breakdown computed for each configured eligibility question.</p>

              <div className="criteria-table-wrapper">
                <table className="criteria-table">
                  <thead>
                    <tr>
                      <th>Question</th>
                      <th>Selected Answer</th>
                      <th>Points Earned</th>
                      <th>Condition Met</th>
                      <th>Explanation</th>
                    </tr>
                  </thead>
                  <tbody>
                    {evaluationResult.customQuestionResults.map((qRes, idx) => (
                      <tr key={idx}>
                        <td>
                          <strong>{qRes.questionText}</strong>
                          {qRes.required && (
                            <span className="required-pill">Required</span>
                          )}
                        </td>
                        <td>{qRes.answer || "No answer"}</td>
                        <td>
                          <strong>{qRes.pointsEarned}</strong> / {qRes.maximumAvailablePoints} pts
                        </td>
                        <td>
                          <span
                            className={`criteria-badge ${qRes.meetsEligibilityCondition ? "status-pass" : "status-fail"}`}
                          >
                            {qRes.meetsEligibilityCondition ? "✓ Yes" : "✗ No"}
                          </span>
                        </td>
                        <td>{qRes.explanation}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}

          {/* Result Actions Footer */}
          <div className="results-actions-card">
            {evaluationResult.eligibilityStatus === "ELIGIBLE" && (
              <div className="results-actions-row">
                <div className="results-actions-msg">
                  <h4>Ready to Apply?</h4>
                  <p>
                    Your eligibility evaluation is confirmed. You can now proceed to create your
                    grant application draft with this scheme and answers pre-filled.
                  </p>
                </div>
                <div className="results-actions-buttons">
                  <button
                    type="button"
                    className="eligibility-btn eligibility-btn--primary"
                    onClick={handleProceedToApplication}
                  >
                    Proceed to Application <ArrowRight size={18} />
                  </button>
                  <button
                    type="button"
                    className="eligibility-btn eligibility-btn--outline"
                    onClick={() => setCurrentStep(1)}
                  >
                    Check Another Scheme
                  </button>
                </div>
              </div>
            )}

            {evaluationResult.eligibilityStatus === "INELIGIBLE" && (
              <div className="results-actions-row">
                <div className="results-actions-msg">
                  <h4>Explore Other Opportunities</h4>
                  <p>
                    Although you are not eligible for this scheme, several other government
                    initiatives might match your profile. You may also modify your answers if any
                    information was entered incorrectly.
                  </p>
                </div>
                <div className="results-actions-buttons">
                  <button
                    type="button"
                    className="eligibility-btn eligibility-btn--primary"
                    onClick={() => setCurrentStep(1)}
                  >
                    Explore Other Schemes
                  </button>
                  <button
                    type="button"
                    className="eligibility-btn eligibility-btn--outline"
                    onClick={() => setCurrentStep(3)}
                  >
                    Review / Modify Answers
                  </button>
                </div>
              </div>
            )}

            {evaluationResult.eligibilityStatus === "NEEDS_MORE_INFORMATION" && (
              <div className="results-actions-row">
                <div className="results-actions-msg">
                  <h4>Complete Missing Details</h4>
                  <p>
                    Please update your beneficiary profile or complete all required questions to
                    enable the engine to calculate a conclusive eligibility result.
                  </p>
                </div>
                <div className="results-actions-buttons">
                  <button
                    type="button"
                    className="eligibility-btn eligibility-btn--primary"
                    onClick={() => {
                      setIsEditingProfile(true);
                      setCurrentStep(2);
                    }}
                  >
                    Complete Missing Profile Details
                  </button>
                  <button
                    type="button"
                    className="eligibility-btn eligibility-btn--outline"
                    onClick={() => setCurrentStep(3)}
                  >
                    Review Answers
                  </button>
                </div>
              </div>
            )}
          </div>
        </section>
      )}
    </div>
  );
}

export default BeneficiaryEligibility;
