import { useCallback, useEffect, useState } from "react";
import { Check, Download, RefreshCw } from "lucide-react";
import { getComplianceDocumentFile } from "../services/complianceService";
import { getUtilizationReviewQueue, reviewUtilization } from "../services/fundAnalyticsService";
import "../styles/FundAnalytics.css";

export default function UtilizationReview() {
  const [items, setItems] = useState([]);
  const [decisions, setDecisions] = useState({});
  const [remarks, setRemarks] = useState({});
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [submittingId, setSubmittingId] = useState(null);
  const [openingDocId, setOpeningDocId] = useState(null);

  const load = useCallback(async () => {
    setBusy(true);
    try {
      setError("");
      const { data } = await getUtilizationReviewQueue();
      setItems(data || []);
    } catch (e) {
      setError(e.response?.data?.message || "Could not load utilization reports.");
    } finally {
      setBusy(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const openDoc = async (id) => {
    setOpeningDocId(id);
    try {
      const { data } = await getComplianceDocumentFile(id);
      const url = URL.createObjectURL(data);
      window.open(url, "_blank", "noopener");
      setTimeout(() => URL.revokeObjectURL(url), 60000);
    } catch (e) {
      setError(e.response?.data?.message || "Could not open evidence document.");
    } finally {
      setOpeningDocId(null);
    }
  };

  const review = async (item) => {
    const selectedDecision = decisions[item.id] || "APPROVED";
    const remarkText = (remarks[item.id] || "").trim();

    if ((selectedDecision === "CORRECTION_REQUIRED" || selectedDecision === "REJECTED") && !remarkText) {
      setError(`Remarks are required when selecting ${selectedDecision.replace("_", " ").toLowerCase()}.`);
      return;
    }

    setSubmittingId(item.id);
    setError("");
    try {
      await reviewUtilization(item.id, {
        decision: selectedDecision,
        remarks: remarkText,
      });
      await load();
    } catch (e) {
      setError(e.response?.data?.message || "Could not save review.");
    } finally {
      setSubmittingId(null);
    }
  };

  return (
    <main className="fund-analytics">
      <header className="fund-analytics__header">
        <div>
          <span>Evidence review</span>
          <h1>Utilization verification</h1>
          <p>Approve expenditure only after checking its supporting compliance documents.</p>
        </div>
        <button className="fund-button secondary" onClick={load} disabled={busy || submittingId !== null}>
          <RefreshCw size={16} />Refresh
        </button>
      </header>
      {error && <div className="fund-error" role="alert">{error}</div>}
      {items.length === 0 && !busy ? (
        <section className="fund-panel fund-empty">No submitted utilization reports are waiting for review.</section>
      ) : (
        <section className="fund-utilization-list">
          {items.map((r) => (
            <article className="fund-panel fund-utilization-record" key={r.id}>
              <div className="fund-record-header">
                <div>
                  <h2>
                    {r.category} · {new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR", maximumFractionDigits: 2 }).format(r.reportedAmount)}
                  </h2>
                  <p>{r.schemeName} · App #{r.applicationId} · {r.beneficiaryName} · Installment {r.installmentNumber}</p>
                </div>
                <span className={`compliance-status status-${r.status.toLowerCase()}`}>
                  {r.status.replaceAll("_", " ")}
                </span>
              </div>
              <p>{r.description}</p>
              <div className="fund-util-evidence">
                {r.supportingComplianceIds?.map((id, i) => (
                  <button
                    className="fund-button secondary"
                    key={id}
                    disabled={openingDocId === id}
                    onClick={() => openDoc(id)}
                  >
                    <Download size={14} />
                    {openingDocId === id ? "Opening..." : (r.supportingDocumentNames?.[i] || `Evidence ${i + 1}`)}
                  </button>
                ))}
              </div>
              <div className="fund-review-row">
                <select
                  value={decisions[r.id] || "APPROVED"}
                  onChange={(e) => setDecisions({ ...decisions, [r.id]: e.target.value })}
                  disabled={submittingId === r.id}
                >
                  <option value="APPROVED">Verify expenditure</option>
                  <option value="CORRECTION_REQUIRED">Request correction</option>
                  <option value="REJECTED">Reject report</option>
                </select>
                <input
                  placeholder="Remarks (required for correction/rejection)"
                  value={remarks[r.id] || ""}
                  onChange={(e) => setRemarks({ ...remarks, [r.id]: e.target.value })}
                  disabled={submittingId === r.id}
                />
                <button
                  className="fund-button"
                  disabled={submittingId === r.id || busy}
                  onClick={() => review(r)}
                >
                  <Check size={15} />
                  {submittingId === r.id ? "Saving..." : "Save review"}
                </button>
              </div>
              <details>
                <summary>Review history ({r.history?.length || 0})</summary>
                {r.history?.map((h, i) => (
                  <p key={i}>
                    {h.status} · {h.actorName || "System"} · {new Date(h.occurredAt).toLocaleString("en-IN")} {h.remarks}
                  </p>
                ))}
              </details>
            </article>
          ))}
        </section>
      )}
    </main>
  );
}
