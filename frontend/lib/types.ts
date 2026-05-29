export interface Candidate {
  id: number;
  scanDate: string;
  symbol: string;
  market: "US" | "IN";
  signalDate: string;
  price: number;
  resistanceLevel: number;
  baseWeeks: number;
  volSurge: number;
  breakoutMargin: number;
  momentum26w: number;
  rankScore: number;
  percentileRank: number;
  regimeOn: boolean;
  returnSinceSignal: number | null;
  lastEvaluated: string | null;
}

export const API_BASE =
  process.env.NEXT_PUBLIC_API_BASE ?? "http://localhost:8080/breakout-detector";

export interface FactorContribution {
  feature: string;
  label: string;
  value: number;
  zScore: number;
  contribution: number;
}

export interface ContextBar {
  date: string;
  close: number;
  high: number;
  volume: number;
  breakoutWeek: boolean;
}

export interface CandidateExplanation {
  symbol: string;
  market: "US" | "IN";
  fired: boolean;
  signalDate: string | null;
  price: number;
  resistance: number;
  baseWeeks: number;
  baseYears: number;
  volSurge: number;
  breakoutMarginPct: number;
  momentum26wPct: number;
  rankScore: number;
  regimeOn: boolean;
  fresh: boolean;
  weeksAgo: number;
  headline: string;
  reasons: string[];
  factors: FactorContribution[];
  context: ContextBar[];
}

export async function fetchExplanation(symbol: string): Promise<CandidateExplanation> {
  const res = await fetch(
    `${API_BASE}/api/candidates/explain/${encodeURIComponent(symbol)}`,
    { cache: "no-store" }
  );
  if (!res.ok) {
    const body = (await res.json().catch(() => null)) as { error?: string } | null;
    throw new Error(body?.error ?? `API ${res.status}`);
  }
  return res.json();
}
