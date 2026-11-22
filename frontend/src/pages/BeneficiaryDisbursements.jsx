import { useEffect, useState } from "react";
import { getMyDisbursementPlans } from "../services/disbursementService";
import "../styles/DisbursementManagement.css";

const money = (value) => new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR", maximumFractionDigits: 2 }).format(Number(value || 0));

export default function BeneficiaryDisbursements() {
  const [plans, setPlans] = useState([]);
  const [error, setError] = useState("");
  useEffect(() => { getMyDisbursementPlans().then(({ data }) => setPlans(data || [])).catch(() => setError("Could not load your disbursement schedules.")); }, []);
  return <section className="disb-page"><header className="disb-page__header"><div><h1>My Disbursement Schedule</h1><p>Read-only schedule for your approved grant applications.</p></div></header>
    {error && <p role="alert">{error}</p>}
    {plans.length === 0 && !error && <div className="disb-card">No approved disbursement schedules are available yet.</div>}
    {plans.map((plan) => <article className="disb-card" key={plan.id}><h2>{plan.schemeName} · Application #{plan.applicationId}</h2><p>Approved grant: <strong>{money(plan.totalApprovedAmount)}</strong> · Plan status: {plan.planStatus}</p><div className="disb-table-wrap"><table className="disb-table"><thead><tr><th>Installment</th><th>Amount</th><th>Share</th><th>Planned release</th><th>Actual release</th><th>Status</th><th>Milestone / condition</th></tr></thead><tbody>{(plan.installments || []).map((i) => <tr key={i.id}><td>{i.installmentNumber}. {i.title}</td><td>{money(i.amount)}</td><td>{i.percentage}%</td><td>{i.plannedReleaseDate || "—"}</td><td>{i.actualDisbursementDate || "—"}</td><td>{i.status}</td><td>{i.milestones?.map((m) => <div key={m.id}><strong>{m.title}</strong>{m.description && <div>{m.description}</div>}{m.compliances?.map((c) => <small key={c.id}>{c.requirementTitle}</small>)}</div>)}</td></tr>)}</tbody></table></div></article>)}
  </section>;
}
