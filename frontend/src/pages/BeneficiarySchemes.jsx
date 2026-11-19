import PublicSchemeCatalog from "../components/PublicSchemeCatalog";
import "../styles/PublicLanding.css";

function BeneficiarySchemes() {
  return (
    <div className="beneficiary-schemes-page" style={{ padding: "8px 0" }}>
      <div style={{ marginBottom: "24px" }}>
        <h2 style={{ fontSize: "1.4rem", margin: "0 0 6px", color: "var(--color-on-surface)" }}>
          Browse Schemes
        </h2>
        <p style={{ color: "var(--color-text-muted)", margin: 0, fontSize: "0.95rem" }}>
          Browse all active government grant schemes. Review eligibility criteria, check your eligibility, and apply directly.
        </p>
      </div>
      <PublicSchemeCatalog />
    </div>
  );
}

export default BeneficiarySchemes;
