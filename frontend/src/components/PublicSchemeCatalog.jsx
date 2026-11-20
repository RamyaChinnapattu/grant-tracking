import { useEffect, useState } from "react";
import {
  ArrowRight,
  BadgeCheck,
  CircleDollarSign,
  Eye,
  RefreshCw,
  Building2,
  Calendar,
  AlertTriangle,
  Search,
  SlidersHorizontal,
  X,
  FileQuestion,
  Award,
} from "lucide-react";
import { Link, useSearchParams } from "react-router-dom";
import { getSchemes } from "../services/schemeService";

function getAvailability(scheme) {
  const today = new Date().toISOString().slice(0, 10);

  if (scheme.status !== "ACTIVE") {
    return { status: "inactive", label: "Inactive", canApply: false };
  }
  if (scheme.applicationStartDate && today < scheme.applicationStartDate) {
    return {
      status: "upcoming",
      label: `Opens on ${scheme.applicationStartDate}`,
      canApply: false,
    };
  }
  if (scheme.applicationEndDate && today > scheme.applicationEndDate) {
    return {
      status: "closed",
      label: "Applications Closed",
      canApply: false,
    };
  }
  return {
    status: "open",
    label: "Accepting Applications",
    canApply: true,
  };
}

function PublicSchemeCatalog({ limit }) {
  const [schemes, setSchemes] = useState([]);
  const [loading, setLoading] = useState(true);
  const [errorStatus, setErrorStatus] = useState(null);
  const [searchParams, setSearchParams] = useSearchParams();

  // Search & Filter state
  const [searchTerm, setSearchTerm] = useState("");
  const [departmentFilter, setDepartmentFilter] = useState("");
  const [categoryFilter, setCategoryFilter] = useState("");
  const [availabilityFilter, setAvailabilityFilter] = useState("");

  const selectedId = searchParams.get("schemeId");
  const role = localStorage.getItem("role");
  const isBeneficiary = role === "BENEFICIARY";

  const loadSchemes = async () => {
    setLoading(true);
    setErrorStatus(null);

    try {
      const response = await getSchemes();

      setSchemes(
        (Array.isArray(response.data) ? response.data : []).filter(
          (scheme) => scheme.status === "ACTIVE"
        )
      );
    } catch (error) {
      setErrorStatus(error.response?.status || "network");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadSchemes();
  }, []);

  // Collect unique departments for the dropdown
  const departments = Array.from(
    new Set(schemes.map((s) => s.department).filter(Boolean))
  ).sort();

  // Filter schemes
  const filteredSchemes = schemes.filter((scheme) => {
    if (searchTerm.trim()) {
      const q = searchTerm.toLowerCase().trim();
      const nameMatch = (scheme.schemeName || "").toLowerCase().includes(q);
      const deptMatch = (scheme.department || "").toLowerCase().includes(q);
      const descMatch = (scheme.description || "").toLowerCase().includes(q);
      if (!nameMatch && !deptMatch && !descMatch) return false;
    }

    if (departmentFilter && scheme.department !== departmentFilter) {
      return false;
    }

    if (categoryFilter) {
      const categories = (scheme.eligibleCategories || []).map((c) =>
        c.toLowerCase()
      );
      if (
        !categories.includes(categoryFilter.toLowerCase()) &&
        !categories.includes("all") &&
        categories.length > 0
      ) {
        return false;
      }
    }

    if (availabilityFilter) {
      const avail = getAvailability(scheme);
      if (avail.status !== availabilityFilter) {
        return false;
      }
    }

    return true;
  });

  const visibleSchemes = limit ? filteredSchemes.slice(0, limit) : filteredSchemes;

  const resetFilters = () => {
    setSearchTerm("");
    setDepartmentFilter("");
    setCategoryFilter("");
    setAvailabilityFilter("");
  };

  const hasActiveFilters =
    Boolean(searchTerm) ||
    Boolean(departmentFilter) ||
    Boolean(categoryFilter) ||
    Boolean(availabilityFilter);

  if (loading) {
    return (
      <div className="public-schemes__state" role="status">
        Loading available schemes…
      </div>
    );
  }

  if (errorStatus) {
    const needsSignIn = errorStatus === 401 || errorStatus === 403;

    return (
      <div
        className="public-schemes__state public-schemes__state--error"
        role="status"
      >
        <p>
          {needsSignIn
            ? "Scheme listings require sign-in with the current access settings."
            : "Scheme listings are temporarily unavailable. Please try again later."}
        </p>

        <div className="public-schemes__state-actions">
          {needsSignIn && (
            <Link
              className="public-button public-button--primary public-button--small"
              to="/login"
            >
              Login to continue
            </Link>
          )}

          <button
            className="public-button public-button--outline public-button--small"
            type="button"
            onClick={loadSchemes}
          >
            <RefreshCw size={15} aria-hidden="true" />
            Try again
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="public-schemes">
      {/* Search and Filters Bar (shown when not constrained by small limit) */}
      {!limit && (
        <div
          style={{
            display: "flex",
            flexDirection: "column",
            gap: "12px",
            marginBottom: "24px",
            padding: "16px",
            borderRadius: "12px",
            border: "1px solid var(--color-outline-variant)",
            background: "var(--color-surface-lowest)",
            boxShadow: "0 2px 10px var(--color-shadow)",
          }}
        >
          {/* Search Row */}
          <div
            style={{
              display: "flex",
              gap: "10px",
              alignItems: "center",
              flexWrap: "wrap",
            }}
          >
            <div
              style={{
                display: "flex",
                alignItems: "center",
                flex: "1 1 280px",
                position: "relative",
              }}
            >
              <Search
                size={16}
                style={{
                  position: "absolute",
                  left: "12px",
                  color: "var(--color-text-muted)",
                }}
              />
              <input
                type="text"
                placeholder="Search schemes by name, department, or keywords..."
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
                style={{
                  width: "100%",
                  padding: "9px 12px 9px 36px",
                  borderRadius: "8px",
                  border: "1px solid var(--color-outline-variant)",
                  background: "var(--color-surface-low)",
                  color: "var(--color-on-surface)",
                  fontSize: "0.88rem",
                }}
              />
              {searchTerm && (
                <button
                  type="button"
                  onClick={() => setSearchTerm("")}
                  style={{
                    position: "absolute",
                    right: "10px",
                    background: "none",
                    border: "none",
                    cursor: "pointer",
                    color: "var(--color-text-muted)",
                  }}
                  title="Clear search"
                >
                  <X size={15} />
                </button>
              )}
            </div>

            {hasActiveFilters && (
              <button
                type="button"
                onClick={resetFilters}
                style={{
                  display: "inline-flex",
                  alignItems: "center",
                  gap: "4px",
                  padding: "8px 12px",
                  borderRadius: "6px",
                  border: "1px solid var(--color-outline-variant)",
                  background: "none",
                  color: "var(--color-text-muted)",
                  fontSize: "0.82rem",
                  cursor: "pointer",
                }}
              >
                <X size={14} />
                Reset filters
              </button>
            )}
          </div>

          {/* Filter Dropdowns */}
          <div
            style={{
              display: "flex",
              gap: "10px",
              flexWrap: "wrap",
              alignItems: "center",
            }}
          >
            <div style={{ display: "flex", alignItems: "center", gap: "6px" }}>
              <SlidersHorizontal size={14} style={{ color: "var(--color-text-muted)" }} />
              <span style={{ fontSize: "0.8rem", fontWeight: 600, color: "var(--color-text-muted)" }}>
                Filters:
              </span>
            </div>

            {/* Department Filter */}
            <select
              value={departmentFilter}
              onChange={(e) => setDepartmentFilter(e.target.value)}
              style={{
                padding: "6px 12px",
                borderRadius: "6px",
                border: "1px solid var(--color-outline-variant)",
                background: "var(--color-surface-low)",
                color: "var(--color-on-surface)",
                fontSize: "0.82rem",
              }}
            >
              <option value="">All Departments</option>
              {departments.map((dept) => (
                <option key={dept} value={dept}>
                  {dept}
                </option>
              ))}
            </select>

            {/* Category Filter */}
            <select
              value={categoryFilter}
              onChange={(e) => setCategoryFilter(e.target.value)}
              style={{
                padding: "6px 12px",
                borderRadius: "6px",
                border: "1px solid var(--color-outline-variant)",
                background: "var(--color-surface-low)",
                color: "var(--color-on-surface)",
                fontSize: "0.82rem",
              }}
            >
              <option value="">All Social Categories</option>
              <option value="General">General</option>
              <option value="OBC">OBC</option>
              <option value="SC">SC</option>
              <option value="ST">ST</option>
              <option value="EWS">EWS</option>
            </select>

            {/* Availability Filter */}
            <select
              value={availabilityFilter}
              onChange={(e) => setAvailabilityFilter(e.target.value)}
              style={{
                padding: "6px 12px",
                borderRadius: "6px",
                border: "1px solid var(--color-outline-variant)",
                background: "var(--color-surface-low)",
                color: "var(--color-on-surface)",
                fontSize: "0.82rem",
              }}
            >
              <option value="">All Availability</option>
              <option value="open">Accepting Applications</option>
              <option value="upcoming">Upcoming</option>
              <option value="closed">Applications Closed</option>
            </select>

            <span
              style={{
                marginLeft: "auto",
                fontSize: "0.8rem",
                color: "var(--color-text-muted)",
              }}
            >
              Showing {filteredSchemes.length} of {schemes.length} scheme{schemes.length === 1 ? "" : "s"}
            </span>
          </div>
        </div>
      )}

      {/* Zero State if Filtered List is Empty */}
      {visibleSchemes.length === 0 ? (
        <div className="public-schemes__state">
          <p>
            {hasActiveFilters
              ? "No schemes match your selected search or filter criteria."
              : "There are no active schemes to display right now."}
          </p>
          <span>
            {hasActiveFilters ? (
              <button
                type="button"
                className="public-button public-button--outline public-button--small"
                onClick={resetFilters}
                style={{ marginTop: "10px" }}
              >
                Clear all filters
              </button>
            ) : (
              "Check back later or sign in to explore available opportunities."
            )}
          </span>
        </div>
      ) : (
        <div className="public-schemes__grid">
          {visibleSchemes.map((scheme) => {
            const isSelected = String(scheme.id) === selectedId;
            const availability = getAvailability(scheme);
            const questionCount = (scheme.eligibilityQuestions || []).length;

            return (
              <article className="public-scheme-card" key={scheme.id}>
                <div className="public-scheme-card__topline">
                  <span className="public-scheme-card__icon">
                    <BadgeCheck size={20} aria-hidden="true" />
                  </span>

                  <span
                    className={`public-scheme-card__status ${
                      availability.status === "open"
                        ? "public-scheme-card__status--open"
                        : "public-scheme-card__status--closed"
                    }`}
                    style={{
                      fontSize: "0.76rem",
                      fontWeight: 700,
                      padding: "3px 9px",
                      borderRadius: "999px",
                      background:
                        availability.status === "open"
                          ? "#dcfce7"
                          : availability.status === "upcoming"
                            ? "#fef3c7"
                            : "#fee2e2",
                      color:
                        availability.status === "open"
                          ? "#15803d"
                          : availability.status === "upcoming"
                            ? "#b45309"
                            : "#b91c1c",
                    }}
                  >
                    {availability.label}
                  </span>
                </div>

                {scheme.department && (
                  <div
                    style={{
                      display: "inline-flex",
                      alignItems: "center",
                      gap: "5px",
                      fontSize: "0.78rem",
                      color: "var(--color-primary)",
                      fontWeight: 600,
                      margin: "4px 0 2px",
                    }}
                  >
                    <Building2 size={13} />
                    <span>{scheme.department}</span>
                  </div>
                )}

                <h3>{scheme.schemeName}</h3>

                <p>
                  {scheme.description ||
                    "Scheme details and eligibility requirements are available in the expanded view."}
                </p>

                <div className="public-scheme-card__amount">
                  <CircleDollarSign size={17} aria-hidden="true" />
                  <span>
                    {scheme.maximumGrantAmount == null
                      ? "Grant amount not specified"
                      : `Up to ₹${Number(
                          scheme.maximumGrantAmount
                        ).toLocaleString("en-IN")}`}
                  </span>
                </div>

                {(scheme.applicationStartDate || scheme.applicationEndDate) && (
                  <div
                    style={{
                      display: "flex",
                      alignItems: "center",
                      gap: "6px",
                      fontSize: "0.8rem",
                      color: "var(--color-text-muted)",
                      margin: "4px 0",
                    }}
                  >
                    <Calendar size={14} />
                    <span>
                      Window: {scheme.applicationStartDate || "Immediate"} –{" "}
                      {scheme.applicationEndDate || "Open deadline"}
                    </span>
                  </div>
                )}

                <button
                  className="public-scheme-card__view"
                  type="button"
                  aria-expanded={isSelected}
                  onClick={() => {
                    const nextParams = new URLSearchParams(searchParams);

                    if (isSelected) {
                      nextParams.delete("schemeId");
                    } else {
                      nextParams.set("schemeId", String(scheme.id));
                    }

                    setSearchParams(nextParams, { replace: true });
                  }}
                >
                  <Eye size={16} aria-hidden="true" />
                  {isSelected ? "Hide details" : "View Scheme"}

                  <ArrowRight
                    className={isSelected ? "is-rotated" : ""}
                    size={15}
                    aria-hidden="true"
                  />
                </button>

                {isSelected && (
                  <div className="public-scheme-card__details">
                    <h4>Eligibility overview</h4>

                    <p>
                      <strong>Age requirement:</strong>{" "}
                      {scheme.minimumAge ?? "Any"}–
                      {scheme.maximumAge ?? "Any"} years
                    </p>

                    <p>
                      <strong>Maximum annual income:</strong>{" "}
                      {scheme.maximumAnnualIncome == null
                        ? "No income limit"
                        : `₹${Number(
                            scheme.maximumAnnualIncome
                          ).toLocaleString("en-IN")}`}
                    </p>

                    <p>
                      <strong>Eligible categories:</strong>{" "}
                      {(scheme.eligibleCategories || []).join(", ") ||
                        "All categories"}
                    </p>

                    {scheme.eligibilityThreshold != null && (
                      <p>
                        <strong>Custom score threshold:</strong>{" "}
                        {scheme.eligibilityThreshold}%
                      </p>
                    )}

                    {questionCount > 0 && (
                      <div style={{ marginTop: "8px" }}>
                        <p style={{ margin: "0 0 4px", fontWeight: 600 }}>
                          Scheme Questions ({questionCount}):
                        </p>
                        <ul style={{ margin: 0, paddingLeft: "20px", fontSize: "0.85rem", color: "var(--color-text-muted)" }}>
                          {scheme.eligibilityQuestions.map((q) => (
                            <li key={q.id}>
                              {q.questionText} {q.required ? "(Mandatory)" : "(Optional)"}
                            </li>
                          ))}
                        </ul>
                      </div>
                    )}

                    {isBeneficiary ? (
                      <div
                        style={{
                          marginTop: "16px",
                          display: "flex",
                          gap: "10px",
                          flexWrap: "wrap",
                          alignItems: "center",
                        }}
                      >
                        {availability.canApply ? (
                          <Link
                            className="public-button public-button--primary public-button--small scheme-card__apply-btn"
                            to="/beneficiary/apply"
                            state={{ schemeId: scheme.id }}
                            style={{
                              color: "#ffffff",
                              backgroundColor: "var(--color-primary, #004b87)",
                              fontWeight: 700,
                              textDecoration: "none",
                              display: "inline-flex",
                              alignItems: "center",
                              gap: "8px",
                              padding: "10px 20px",
                              borderRadius: "8px",
                            }}
                          >
                            <span style={{ color: "#ffffff", fontWeight: 700, fontSize: "0.88rem" }}>
                              Apply Now
                            </span>
                            <ArrowRight size={15} style={{ color: "#ffffff" }} aria-hidden="true" />
                          </Link>
                        ) : (
                          <span
                            style={{
                              fontSize: "0.85rem",
                              color: "#b91c1c",
                              fontWeight: 600,
                              display: "inline-flex",
                              alignItems: "center",
                              gap: "5px",
                              padding: "4px 8px",
                              background: "#fee2e2",
                              borderRadius: "4px",
                            }}
                          >
                            <AlertTriangle size={15} />
                            {availability.label} — Applications unavailable
                          </span>
                        )}
                      </div>
                    ) : (
                      <div style={{ marginTop: "14px" }}>
                        <Link
                          to="/login"
                          className="public-button public-button--outline public-button--small"
                        >
                          Sign in to apply
                        </Link>
                      </div>
                    )}
                  </div>
                )}
              </article>
            );
          })}
        </div>
      )}

      {limit && filteredSchemes.length > limit && (
        <div className="public-schemes__all">
          <Link
            className="public-button public-button--outline"
            to="/schemes"
          >
            Explore all schemes
            <ArrowRight size={16} aria-hidden="true" />
          </Link>
        </div>
      )}
    </div>
  );
}

export default PublicSchemeCatalog;
