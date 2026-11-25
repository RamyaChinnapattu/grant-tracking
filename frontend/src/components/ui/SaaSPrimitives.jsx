import { useState } from "react";
import { Link } from "react-router-dom";
import { AlertCircle, CheckCircle2, Info, X } from "lucide-react";
import { buildDonutSlices, clampPercent } from "../../utils/fintechCharts";

export function PageHeader({
  eyebrow,
  title,
  subtitle,
  actions,
  variant = "default",
  badge,
}) {
  const isHero = variant === "hero";
  return (
    <header
      className={`saas-page-header${isHero ? " saas-page-header--hero" : ""}`}
    >
      <div className="saas-page-header__copy">
        {(eyebrow || badge) && (
          <div className="saas-page-header__top-row">
            {eyebrow && (
              <span className="saas-page-header__eyebrow">{eyebrow}</span>
            )}
            {badge}
          </div>
        )}
        <h1 className="saas-page-header__title">{title}</h1>
        {subtitle && <p className="saas-page-header__subtitle">{subtitle}</p>}
      </div>
      {actions && <div className="saas-page-header__actions">{actions}</div>}
    </header>
  );
}

export function HeroBanner({
  eyebrow,
  title,
  subtitle,
  badge,
  actions,
  metrics = [],
  children,
}) {
  return (
    <section className="fintech-hero">
      <div className="fintech-hero__glow" aria-hidden="true" />
      <div className="fintech-hero__top">
        <div className="fintech-hero__copy">
          {(eyebrow || badge) && (
            <div className="fintech-hero__eyebrow-row">
              {eyebrow && <span className="fintech-hero__eyebrow">{eyebrow}</span>}
              {badge}
            </div>
          )}
          <h2 className="fintech-hero__title">{title}</h2>
          {subtitle && <p className="fintech-hero__subtitle">{subtitle}</p>}
        </div>
        {actions && <div className="fintech-hero__actions">{actions}</div>}
      </div>

      {metrics.length > 0 && (
        <div className="fintech-hero__metrics">
          {metrics.map((metric) => (
            <div key={metric.label} className="fintech-hero__metric">
              <span>{metric.label}</span>
              <strong>{metric.value}</strong>
              {metric.hint && <small>{metric.hint}</small>}
            </div>
          ))}
        </div>
      )}

      {children}
    </section>
  );
}

export function KpiCard({
  to,
  onClick,
  tone = "default",
  icon: Icon,
  label,
  value,
  meta,
  badge,
  className = "",
}) {
  const toneClass = tone && tone !== "default" ? ` fintech-kpi--${tone}` : "";
  const classes = `saas-kpi-card${toneClass}${className ? ` ${className}` : ""}`;

  const inner = (
    <>
      <div className="saas-kpi-card__top">
        <span className="saas-kpi-card__label">{label}</span>
        {Icon && (
          <span className="saas-kpi-card__icon">
            <Icon size={17} />
          </span>
        )}
      </div>
      <div className="saas-kpi-card__value">{value}</div>
      {(meta || badge) && (
        <div className="saas-kpi-card__foot">
          {meta && <span className="saas-kpi-card__meta">{meta}</span>}
          {badge}
        </div>
      )}
    </>
  );

  if (to) {
    return (
      <Link to={to} className={classes}>
        {inner}
      </Link>
    );
  }

  if (onClick) {
    return (
      <button type="button" onClick={onClick} className={classes}>
        {inner}
      </button>
    );
  }

  return <article className={classes}>{inner}</article>;
}

export function ProgressMeter({
  label,
  value = 0,
  max = 100,
  tone = "indigo",
  showPercent = true,
  sublabel,
}) {
  const pct = clampPercent(value, max);
  return (
    <div className="fintech-progress">
      {(label || showPercent) && (
        <div className="fintech-progress__head">
          {label && <span className="fintech-progress__label">{label}</span>}
          {showPercent && (
            <span className="fintech-progress__pct">{pct}%</span>
          )}
        </div>
      )}
      <div
        className="fintech-progress__track"
        role="progressbar"
        aria-valuenow={pct}
        aria-valuemin={0}
        aria-valuemax={100}
      >
        <div
          className={`fintech-progress__fill fintech-progress__fill--${tone}`}
          style={{ width: `${pct}%` }}
        />
      </div>
      {sublabel && <span className="fintech-progress__sub">{sublabel}</span>}
    </div>
  );
}

export function DonutChart({
  segments = [],
  centerLabel = "Total",
  centerValue,
  formatValue = (v) => String(v),
  emptyText = "No records available yet.",
}) {
  const [hoveredIdx, setHoveredIdx] = useState(null);
  const radius = 58;
  const { total, slices } = buildDonutSlices(segments, radius);

  if (total <= 0) {
    return (
      <div className="fintech-donut__empty">
        <p>{emptyText}</p>
      </div>
    );
  }

  const activeSlice =
    hoveredIdx !== null && slices[hoveredIdx] ? slices[hoveredIdx] : null;
  const readoutLabel = activeSlice ? activeSlice.label : centerLabel;
  const readoutValue = activeSlice
    ? formatValue(activeSlice.value)
    : centerValue ?? formatValue(total);

  return (
    <div className="fintech-donut">
      <div className="fintech-donut__visual">
        <svg
          viewBox="0 0 156 156"
          className="fintech-donut__svg"
          role="img"
          aria-label={centerLabel}
        >
          <circle
            cx="78"
            cy="78"
            r={radius}
            fill="none"
            stroke="#EAEEF6"
            strokeWidth="18"
          />
          <g transform="rotate(-90 78 78)">
            {slices.map((slice, idx) => (
              <circle
                key={slice.label}
                cx="78"
                cy="78"
                r={radius}
                fill="none"
                stroke={slice.color}
                strokeWidth={hoveredIdx === idx ? 22 : 18}
                strokeDasharray={slice.dashArray}
                strokeDashoffset={slice.dashOffset}
                strokeLinecap="butt"
                style={{
                  transition: "stroke-width 180ms ease, opacity 180ms ease",
                  opacity: hoveredIdx !== null && hoveredIdx !== idx ? 0.52 : 1,
                  cursor: "pointer",
                }}
                onMouseEnter={() => setHoveredIdx(idx)}
                onMouseLeave={() => setHoveredIdx(null)}
              />
            ))}
          </g>
        </svg>
        <div className="fintech-donut__center">
          <span>{readoutLabel}</span>
          <strong>{readoutValue}</strong>
          {activeSlice && <small>{activeSlice.percentage}%</small>}
        </div>
      </div>

      <ul className="fintech-donut__legend">
        {slices.map((slice, idx) => (
          <li
            key={slice.label}
            className={`fintech-donut__legend-item${
              hoveredIdx === idx ? " is-active" : ""
            }`}
            onMouseEnter={() => setHoveredIdx(idx)}
            onMouseLeave={() => setHoveredIdx(null)}
          >
            <span className="fintech-donut__legend-left">
              <i
                className="fintech-donut__swatch"
                style={{ backgroundColor: slice.color }}
              />
              <span>{slice.label}</span>
            </span>
            <span className="fintech-donut__legend-right">
              <strong>{formatValue(slice.value)}</strong>
              <small>{slice.percentage}%</small>
            </span>
          </li>
        ))}
      </ul>
    </div>
  );
}

export function AlertBanner({ type = "info", message, onClose, action }) {
  if (!message) return null;
  const Icon =
    type === "error" ? AlertCircle : type === "success" ? CheckCircle2 : Info;
  return (
    <div
      className={`saas-alert saas-alert--${type}`}
      role={type === "error" ? "alert" : "status"}
    >
      <div style={{ display: "flex", alignItems: "center", gap: "10px", minWidth: 0 }}>
        <Icon size={18} style={{ flexShrink: 0 }} />
        <span>{message}</span>
      </div>
      <div style={{ display: "flex", alignItems: "center", gap: "8px", flexShrink: 0 }}>
        {action}
        {onClose && (
          <button
            type="button"
            onClick={onClose}
            aria-label="Dismiss message"
            style={{
              background: "none",
              border: "none",
              cursor: "pointer",
              padding: "2px",
              display: "inline-flex",
              color: "inherit",
            }}
          >
            <X size={16} />
          </button>
        )}
      </div>
    </div>
  );
}

export function SkeletonKpiRow({ count = 4 }) {
  return (
    <div className="saas-kpi-grid" aria-hidden="true">
      {Array.from({ length: count }).map((_, idx) => (
        <div key={idx} className="saas-kpi-card">
          <div className="saas-skeleton" style={{ height: 14, width: "60%" }} />
          <div className="saas-skeleton" style={{ height: 28, width: "45%" }} />
          <div className="saas-skeleton" style={{ height: 12, width: "75%" }} />
        </div>
      ))}
    </div>
  );
}

export function EmptyState({ icon: Icon, title, description, action }) {
  return (
    <div className="saas-empty">
      {Icon && (
        <div className="saas-empty__icon">
          <Icon size={26} />
        </div>
      )}
      {title && <h4>{title}</h4>}
      {description && <p>{description}</p>}
      {action}
    </div>
  );
}

export function StatusBadge({ status, label }) {
  const normalized = String(status || "").toUpperCase();
  let variant = "neutral";
  if (
    [
      "APPROVED",
      "DISBURSED",
      "VERIFIED",
      "COMPLETED",
      "ACTIVE",
      "RESOLVED",
    ].includes(normalized)
  ) {
    variant = "success";
  } else if (
    ["PARTIALLY_DISBURSED", "READY_FOR_DISBURSEMENT", "READY"].includes(normalized)
  ) {
    variant = "teal";
  } else if (
    [
      "SUBMITTED",
      "UNDER_REVIEW",
      "UNDER_VERIFICATION",
      "FIELD_VERIFICATION",
      "DISTRICT_REVIEW",
      "FINANCE_REVIEW",
      "PLANNED",
    ].includes(normalized)
  ) {
    variant = "info";
  } else if (
    [
      "PENDING",
      "CORRECTION_REQUIRED",
      "NEEDS_CORRECTION",
      "AWAITING_DISBURSEMENT",
      "OVERDUE",
    ].includes(normalized)
  ) {
    variant = "warning";
  } else if (
    ["REJECTED", "CANCELLED", "NON_COMPLIANT", "OPEN"].includes(normalized)
  ) {
    variant = "danger";
  }

  return (
    <span className={`saas-badge saas-badge--${variant}`}>
      {label || String(status || "—").replaceAll("_", " ")}
    </span>
  );
}
