"use client";

import { useEffect, useState } from "react";
import { fetchExplanation, type CandidateExplanation } from "@/lib/types";

interface ExplainModalProps {
  symbol: string;
  onClose: () => void;
}

function ccy(market: "US" | "IN"): string {
  return market === "IN" ? "₹" : "$";
}

export default function ExplainModal({ symbol, onClose }: ExplainModalProps) {
  const [data, setData] = useState<CandidateExplanation | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let alive = true;
    setData(null);
    setError(null);
    fetchExplanation(symbol)
      .then((d) => { if (alive) setData(d); })
      .catch((e: unknown) => { if (alive) setError(e instanceof Error ? e.message : String(e)); });
    return () => { alive = false; };
  }, [symbol]);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => { if (e.key === "Escape") onClose(); };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [onClose]);

  const maxAbs =
    data && data.factors.length > 0
      ? Math.max(...data.factors.map((f) => Math.abs(f.contribution)))
      : 1;

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal" onClick={(e) => e.stopPropagation()} role="dialog" aria-modal="true">
        <button className="modal-close" onClick={onClose} aria-label="Close">×</button>

        <div className="modal-head">
          <span className="sym">{symbol.replace(".NS", "")}</span>
          {data && (
            <div className="badges">
              <span className={`badge ${data.market.toLowerCase()}`}>{data.market}</span>
              {data.fired && (
                <span className={`badge ${data.regimeOn ? "rec" : "off"}`}>
                  regime {data.regimeOn ? "ON" : "OFF"}
                </span>
              )}
              {data.fired && (
                <span className={`badge ${data.fresh ? "rec" : "off"}`}>
                  {data.fresh ? "fresh" : `${data.weeksAgo}w old`}
                </span>
              )}
            </div>
          )}
        </div>

        {error && <div className="note">Could not load breakdown: {error}</div>}
        {!data && !error && <div className="modal-loading">Loading breakdown…</div>}

        {data && (
          <>
            <p className="modal-headline">{data.headline}</p>

            {!data.fired ? (
              <div className="note">{data.reasons[0]}</div>
            ) : (
              <>
                <div className="modal-keyline">
                  <span>{ccy(data.market)}{data.price.toFixed(2)}</span>
                  <span>resistance {ccy(data.market)}{data.resistance.toFixed(2)}</span>
                  <span>base {data.baseYears.toFixed(1)}y</span>
                  <span>vol {data.volSurge.toFixed(1)}×</span>
                  <span>signal {data.signalDate}</span>
                </div>

                <h4 className="modal-h">Why it qualified</h4>
                <ul className="reasons">
                  {data.reasons.map((r, i) => <li key={i}>{r}</li>)}
                </ul>

                <h4 className="modal-h">
                  Score breakdown <span className="modal-sub">contribution of each factor to the conviction score</span>
                </h4>
                <div className="factors">
                  {data.factors
                    .slice()
                    .sort((a, b) => Math.abs(b.contribution) - Math.abs(a.contribution))
                    .map((f) => {
                      const pos = f.contribution >= 0;
                      const w = (Math.abs(f.contribution) / maxAbs) * 50; // half-width max
                      return (
                        <div className="factor" key={f.feature}>
                          <span className="factor-label">{f.label}</span>
                          <div className="factor-track">
                            <span className="factor-mid" />
                            <span
                              className={`factor-fill ${pos ? "pos" : "neg"}`}
                              style={{ width: `${w}%`, [pos ? "left" : "right"]: "50%" } as React.CSSProperties}
                            />
                          </div>
                          <span className={`factor-val ${pos ? "pos" : "neg"}`}>
                            {pos ? "+" : ""}{f.contribution.toFixed(3)}
                          </span>
                        </div>
                      );
                    })}
                </div>

                <h4 className="modal-h">Around the breakout week</h4>
                <table className="ctx">
                  <thead>
                    <tr><th>Week</th><th>Close</th><th>High</th><th>Volume</th></tr>
                  </thead>
                  <tbody>
                    {data.context.map((b) => (
                      <tr key={b.date} className={b.breakoutWeek ? "brk" : ""}>
                        <td>{b.date}{b.breakoutWeek ? " ◄ breakout" : ""}</td>
                        <td>{ccy(data.market)}{b.close.toFixed(2)}</td>
                        <td>{ccy(data.market)}{b.high.toFixed(2)}</td>
                        <td>{b.volume.toLocaleString()}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </>
            )}

            <p className="modal-disc">
              Transparency view · analytics, not advice. The score is a transparent linear model;
              contributions sum (with the base intercept) to the conviction score.
            </p>
          </>
        )}
      </div>
    </div>
  );
}
