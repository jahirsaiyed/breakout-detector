"use client";

import { useEffect, useState } from "react";
import { API_BASE, type Candidate } from "@/lib/types";

function pct(n: number) { return `${(n * 100).toFixed(1)}%`; }
function years(weeks: number) { return `${(weeks / 52).toFixed(1)}y`; }

export default function CandidateList() {
  const [data, setData] = useState<Candidate[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let alive = true;
    fetch(`${API_BASE}/api/candidates/latest?recommendedOnly=false`, { cache: "no-store" })
      .then((r) => { if (!r.ok) throw new Error(`API ${r.status}`); return r.json(); })
      .then((d: Candidate[]) => { if (alive) setData(d); })
      .catch((e) => { if (alive) setError(String(e.message ?? e)); });
    return () => { alive = false; };
  }, []);

  if (error) {
    return (
      <div className="note">
        Could not reach the scanner API ({error}). Set <code>NEXT_PUBLIC_API_BASE</code> to your
        deployed backend URL, and confirm a scan has been run
        (<code>POST /api/candidates/scan</code>).
      </div>
    );
  }
  if (!data) {
    return <div className="grid">{Array.from({ length: 6 }).map((_, i) => <div key={i} className="skel" />)}</div>;
  }
  if (data.length === 0) {
    return (
      <div className="note">
        No candidates in the latest scan yet. Trigger one with
        <code> POST {API_BASE}/api/candidates/scan?limit=200</code> — the scanner only surfaces
        stocks that are <em>currently</em> breaking out of a multi-year base in a healthy market regime,
        so an empty list is normal on quiet weeks.
      </div>
    );
  }

  const sorted = [...data].sort((a, b) => b.rankScore - a.rankScore);

  return (
    <div className="grid">
      {sorted.map((c) => {
        const rec = c.percentileRank >= 0.67;
        return (
          <article key={c.id} className={`card${rec ? " rec" : ""}`}>
            <div className="rowtop">
              <span className="sym">{c.symbol.replace(".NS", "")}</span>
              <div className="badges">
                <span className={`badge ${c.market.toLowerCase()}`}>{c.market}</span>
                {rec && <span className="badge rec">Top tier</span>}
              </div>
            </div>

            <div className="price">
              {c.market === "IN" ? "₹" : "$"}{c.price.toFixed(2)}
              <span className="since">signal {c.signalDate}</span>
            </div>

            <div className="conv">
              <div className="lab"><span>Conviction</span><span>{(c.percentileRank * 100).toFixed(0)} pctl</span></div>
              <div className="bar"><span style={{ width: `${Math.max(6, c.percentileRank * 100)}%` }} /></div>
            </div>

            <div className="chips">
              <span className="chip">base <b>{years(c.baseWeeks)}</b></span>
              <span className="chip">vol <b>{c.volSurge.toFixed(1)}×</b></span>
              <span className="chip">above resist <b>{pct(c.breakoutMargin)}</b></span>
              <span className="chip">26w mom <b>{pct(c.momentum26w)}</b></span>
              {c.returnSinceSignal != null && (
                <span className="chip">since <b>{pct(c.returnSinceSignal)}</b></span>
              )}
            </div>
          </article>
        );
      })}
    </div>
  );
}
