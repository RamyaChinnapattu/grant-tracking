import { useCallback, useEffect, useState } from "react";
import { FileUp, RefreshCw } from "lucide-react";
import { getComplianceMine, submitComplianceDocument } from "../services/complianceService";
import "../styles/Compliance.css";

const dateLabel=(v)=>v?new Date(`${v}T00:00:00`).toLocaleDateString("en-IN"):"No deadline";
export default function BeneficiaryCompliance(){
  const [items,setItems]=useState([]);const [files,setFiles]=useState({});const [notes,setNotes]=useState({});const [error,setError]=useState("");const [busy,setBusy]=useState("");
  const load=useCallback(async()=>{try{setError("");const {data}=await getComplianceMine();setItems(data||[]);}catch{setError("Could not load your compliance requirements.");}},[]);
  useEffect(()=>{load();},[load]);
  const submit=async(item)=>{if(!files[item.id]){setError("Choose a document before submitting.");return;}const form=new FormData();form.append("file",files[item.id]);form.append("submissionNotes",notes[item.id]||"");setBusy(String(item.id));setError("");try{await submitComplianceDocument(item.id,form);await load();setFiles((v)=>({...v,[item.id]:null}));}catch(e){setError(e.response?.data?.message||"Submission failed.");}finally{setBusy("");}};
  return <main className="compliance-page"><header className="compliance-heading"><div><p className="compliance-eyebrow">Grant obligations</p><h1>My compliance</h1><p>Submit the documents required for your approved grant and track review decisions.</p></div><button className="compliance-button secondary" onClick={load}><RefreshCw size={16}/>Refresh</button></header>
    {error&&<div className="compliance-alert" role="alert">{error}</div>}{items.length===0&&!error&&<section className="compliance-card">No compliance requirements are available for your grant plans.</section>}
    <div className="compliance-list">{items.map((item)=><article className="compliance-card" key={item.id}><div className="compliance-card-head"><div><h2>{item.requirementTitle}</h2><p>{item.schemeName} · Application #{item.applicationId} · {item.milestoneTitle}</p></div><span className={`compliance-status status-${item.status?.toLowerCase()}`}>{item.status?.replaceAll("_"," ")}</span></div>
      {item.description&&<p>{item.description}</p>}<div className="compliance-meta"><span>Due: <strong>{dateLabel(item.submissionDeadline)}</strong></span><span>{item.required?"Required":"Optional"}</span><span>Formats: {item.allowedFormats}</span><span>Max: {Math.round(item.maxFileSizeBytes/1048576*10)/10} MB</span></div>
      {item.verificationRemarks&&<div className="compliance-remarks"><strong>Review remarks</strong><p>{item.verificationRemarks}</p>{item.verifiedByName&&<small>{item.verifiedByName} · {item.verifiedAt?new Date(item.verifiedAt).toLocaleString("en-IN"):""}</small>}</div>}
      {item.reviewHistory?.length>0&&<details className="compliance-history"><summary>Submission and review history ({item.reviewHistory.length})</summary>{item.reviewHistory.map((h,i)=><p key={`${h.action}-${i}`}><strong>{h.action.replaceAll("_"," ")}</strong> · {h.actorName||"System"} · {new Date(h.occurredAt).toLocaleString("en-IN")} {h.remarks&&`— ${h.remarks}`}</p>)}</details>}
      {["PENDING","OVERDUE","REJECTED","CORRECTION_REQUIRED"].includes(item.status)&&<div className="compliance-submit"><label>Supporting document<input type="file" accept={item.allowedFormats?.split(",").map((f)=>`.${f}`).join(",")} onChange={(e)=>setFiles((v)=>({...v,[item.id]:e.target.files?.[0]}))}/></label><label>Submission notes<textarea value={notes[item.id]||""} onChange={(e)=>setNotes((v)=>({...v,[item.id]:e.target.value}))} rows="2"/></label><button className="compliance-button" disabled={busy===String(item.id)} onClick={()=>submit(item)}><FileUp size={16}/>{item.status==="PENDING"||item.status==="OVERDUE"?"Submit documents":"Replace and resubmit"}</button></div>}
    </article>)}</div>
  </main>;
}
