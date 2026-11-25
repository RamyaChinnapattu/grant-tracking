import PublicSchemeCatalog from "../components/PublicSchemeCatalog";
import { PageHeader } from "../components/ui/SaaSPrimitives";
import "../styles/PublicLanding.css";

function BeneficiarySchemes() {
  return (
    <div className="saas-page">
      <PageHeader
        eyebrow="Scheme Catalog"
        title="Browse Schemes"
        subtitle="Explore active government grant schemes, inspect budget caps and criteria, check your eligibility, and apply directly."
      />
      <PublicSchemeCatalog />
    </div>
  );
}

export default BeneficiarySchemes;
