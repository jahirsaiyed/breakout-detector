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
