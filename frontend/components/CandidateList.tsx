"use client";

import { useEffect, useState } from "react";
import { API_BASE, type Candidate } from "@/lib/types";
import ExplainModal from "@/components/ExplainModal";

function pct(n: number) { return `${(n * 100).toFixed(1)}%`; }
function years(weeks: number) { return `${(weeks / 52).toFixed(1)}y`; }

type Market = "ALL" | "US" | "IN";

export default function CandidateList() {
  const [data, setData] = useState<Candidate[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [market, setMarket] = useState<Market>("ALL");
  const [selected, setSelected] = useState<string | null>(null);
  const [query, setQuery] = useState("");

  useEffect(() => {
    let alive = true;
    fetch(`${API_BASE}/api/candidates/latest?recommendedOnly=false`, { cache: "no-store" })
      .then((r) => { if (!r.ok) throw new Error(`API ${r.status}`); return r.json(); })
      .then((d: Candidate[]) => { if (alive) setData(d); })
      .catch((e) => { if (alive) setError(String(e.message ?? e)); });
    return () => { alive = false; };
  }, []);

  const sorted = (data ?? [])
    .filter((c) => market === "ALL" || c.market === market)
    .sort((a, b) => b.rankScore - a.rankScore);

  function submitQuery(e: React.FormEvent) {
    e.preventDefault();
    const sym = query.trim().toUpperCase();
    if (sym) setSelected(sym);
  }

  let body: React.ReactNode;
  if (error) {
    body = (
      <div className="note">
        Could not reach the scanner API ({error}). Set <code>NEXT_PUBLIC_API_BASE</code> to your
        deployed backend URL, and confirm a scan has been run
        (<code>POST /api/candidates/scan</code>).
      </div>
    );
  } else if (!data) {
    body = <div className="grid">{Array.from({ length: 6 }).map((_, i) => <div key={i} className="skel" />)}</div>;
  } else if (data.length === 0) {
    body = (
      <div className="note">
        No candidates in the latest scan yet. Trigger one with
        <code> POST {API_BASE}/api/candidates/scan?limit=200</code> — the scanner only surfaces
        stocks that are <em>currently</em> breaking out of a multi-year base in a healthy market regime,
        so an empty list is normal on quiet weeks. You can still <strong>explain any ticker</strong> above.
      </div>
    );
  } else {
    body = (
      <>
        <div className="filter-bar">
          {(["ALL", "US", "IN"] as Market[]).map((m) => (
            <button
              key={m}
              className={`filter-btn${market === m ? " active" : ""}`}
              onClick={() => setMarket(m)}
            >
              {m === "ALL" ? "All markets" : m === "US" ? "🇺🇸 US" : "🇮🇳 India"}
            </button>
          ))}
          <span className="filter-count">{sorted.length} stock{sorted.length !== 1 ? "s" : ""}</span>
        </div>
        <div className="grid">
          {sorted.map((c) => {
            const rec = c.percentileRank >= 0.67;
            return (
              <article
                key={c.id}
                className={`card${rec ? " rec" : ""}`}
                role="button"
                tabIndex={0}
                onClick={() => setSelected(c.symbol)}
                onKeyDown={(e) => { if (e.key === "Enter") setSelected(c.symbol); }}
                title="See full breakdown"
              >
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

                <span className="why-link">Why this? →</span>
              </article>
            );
          })}
        </div>
      </>
    );
  }

  return (
    <>
      <form className="explain-bar" onSubmit={submitQuery}>
        <input
          className="explain-input"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="Explain any ticker — e.g. BEN, NVDA, RELIANCE.NS"
          aria-label="Explain any ticker"
        />
        <button className="explain-btn" type="submit">Explain →</button>
      </form>

      {body}

      {selected && <ExplainModal symbol={selected} onClose={() => setSelected(null)} />}
    </>
  );
}
