import { useEffect, useState } from "react";
import { Save } from "lucide-react";
import api from "../services/api";
import { AlertBanner, PageHeader } from "../components/ui/SaaSPrimitives";
import "../styles/DashboardHome.css";
import "../styles/AdminDashboardHome.css";

export default function WorkflowSettings() {
  const [form, setForm] = useState({
    fieldDays: 5,
    districtDays: 5,
    financeDays: 5,
    scanMinutes: 60,
  });
  const [status, setStatus] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    setLoading(true);
    api
      .get("/workflow/deadlines")
      .then(({ data }) => {
        setForm({
          fieldDays: data.fieldDays ?? 5,
          districtDays: data.districtDays ?? 5,
          financeDays: data.financeDays ?? 5,
          scanMinutes: data.scanMinutes ?? 60,
        });
      })
      .catch(() => {
        setError("Could not load current workflow deadline settings.");
      })
      .finally(() => setLoading(false));
  }, []);

  const handleChange = (e) => {
    setForm({ ...form, [e.target.name]: Number(e.target.value) });
  };

  const handleSave = async (e) => {
    e.preventDefault();
    setStatus("");
    setError("");
    setSaving(true);

    try {
      const { data } = await api.put("/workflow/deadlines", form);
      setForm({
        fieldDays: data.fieldDays ?? form.fieldDays,
        districtDays: data.districtDays ?? form.districtDays,
        financeDays: data.financeDays ?? form.financeDays,
        scanMinutes: data.scanMinutes ?? form.scanMinutes,
      });
      setStatus("Workflow deadline settings saved successfully.");
      setTimeout(() => setStatus(""), 4000);
    } catch (err) {
      setError(
        err.response?.data?.message || "Could not save workflow settings."
      );
    } finally {
      setSaving(false);
    }
  };

  const fields = [
    {
      name: "fieldDays",
      label: "Stage 1: Field Officer Deadline (days)",
      help: "Max allowed days for initial field verification before escalation.",
      max: 365,
    },
    {
      name: "districtDays",
      label: "Stage 2: District Officer Deadline (days)",
      help: "Max allowed days for district-level review and quota check.",
      max: 365,
    },
    {
      name: "financeDays",
      label: "Stage 3: Finance Officer Deadline (days)",
      help: "Max allowed days for finance officer approval and fund authorization.",
      max: 365,
    },
    {
      name: "scanMinutes",
      label: "Overdue Escalation Scan Interval (minutes)",
      help: "Background schedule cadence to detect and flag stalled applications.",
      max: 1440,
    },
  ];

  return (
    <div className="saas-page" style={{ maxWidth: "820px" }}>
      <PageHeader
        eyebrow="Workflow Automation"
        title="Workflow Settings & Deadlines"
        subtitle="Configure turn-around SLAs and escalation timers across Field, District, and Finance verification stages."
      />

      <AlertBanner type="success" message={status} onClose={() => setStatus("")} />
      <AlertBanner type="error" message={error} onClose={() => setError("")} />

      <div className="saas-panel">
        <div style={{ marginBottom: "20px" }}>
          <h3 style={{ margin: "0 0 6px", fontSize: "1.15rem" }}>
            Verification Stage Deadlines
          </h3>
          <p style={{ margin: 0, fontSize: "0.85rem", color: "var(--color-text-muted)" }}>
            Deadlines apply when a new verification task is routed to an officer.
            Applications exceeding these intervals are automatically marked as ESCALATED.
          </p>
        </div>

        {loading ? (
          <p style={{ color: "var(--color-text-muted)", fontSize: "0.9rem" }}>
            Loading configuration...
          </p>
        ) : (
          <form onSubmit={handleSave} style={{ display: "flex", flexDirection: "column", gap: "18px" }}>
            {fields.map(({ name, label, help, max }) => (
              <div key={name} className="admin-form-group">
                <label className="admin-form-label" htmlFor={name}>
                  {label}
                </label>
                <input
                  id={name}
                  name={name}
                  type="number"
                  min="1"
                  max={max}
                  required
                  className="admin-form-input"
                  style={{ maxWidth: "200px" }}
                  value={form[name] ?? 5}
                  onChange={handleChange}
                />
                <span className="admin-form-help">{help}</span>
              </div>
            ))}

            <div style={{ marginTop: "12px" }}>
              <button
                type="submit"
                className="admin-dash-btn admin-dash-btn--primary"
                disabled={saving}
              >
                <Save size={15} />
                {saving ? "Saving settings..." : "Save Deadline Settings"}
              </button>
            </div>
          </form>
        )}
      </div>
    </div>
  );
}
