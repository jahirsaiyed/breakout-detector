# Breakout Detector → "StaircaseAlerts" — PRD & Technical Architecture (US MVP)

> Status: draft v1 · Owner: jsaiyed · Date: 2026-05-29
> Companion to the edge-validation harness (`src/main/java/com/breakoutdetector/research/EdgeValidationRunner.java`).

---

## 0. Context & the validation result that shapes this PRD

An honest point-in-time event study (53 US names, 10y adjusted data, 5,731 stock-month
observations) showed:

- The **binary staircase pattern** has a *small* edge: higher win-rate (71–79% vs 62–70%)
  and a positive mean-return spread (+1.2% to +2.0%), with a real lift in 12-month
  big-winners (23% vs 18% gained >50%).
- The **numeric multibagger score is broken**: 91% of observations score `VERY_HIGH`,
  and the level ordering is *inverted* (LOW outperforms HIGH/VERY_HIGH).

**Product implication:** we cannot ship a "rank stocks by multibagger score" product on the
current score. **Sprint 0 of the MVP is signal redesign + re-validation.** Until the signal
demonstrably ranks forward returns monotonically, marketing language must be
"higher-probability technical setups," NOT "the next multibagger."

---

## 1. Problem & opportunity

Retail investors want an *edge in discovery* — a short, trustworthy daily list of stocks
showing the early accumulation pattern that precedes large multi-month moves, with the
evidence and a track record behind it. Existing tools (TradingView, Finviz, Chartink,
Trendlyne) sell raw screening/charting; **none package a single validated "early compounder"
signal as a ranked daily list with a published, honest backtest.** That transparency is the wedge.

## 2. Goals / non-goals

**Goals (MVP)**
- A redesigned, validated staircase/accumulation signal that *ranks* forward returns.
- A nightly scan of a real US universe (S&P 500 + liquid mid-caps) producing a ranked list.
- Web dashboard: today's ranked candidates, each with chart + "why it's here" evidence.
- Email/Telegram alerts on new candidates.
- Accounts + Stripe subscriptions (free / Pro).
- A public, honest track-record page (the marketing engine).

**Non-goals (MVP)**
- India market (Sprint N+1 — needs licensed data + SEBI-reviewed framing).
- Intraday/real-time data (EOD only).
- Brokerage integration / order execution (compliance landmine; never auto-trade).
- Personalized advice (would trigger RIA/RA registration).

## 3. Target users & willingness to pay

- US retail swing/position traders and self-directed investors.
- Comparable paid tools: Finviz Elite $40/mo, Trendlyne, Chartink Pro. Target Pro at **$19/mo**
  (annual discount), free tier for acquisition + SEO.

## 4. Product surface (MVP scope)

| Feature | Free | Pro |
|---|---|---|
| Daily ranked candidate list | Top 3, 1-day delayed | Full list, same-day (post-close) |
| Per-stock evidence + chart | ✓ | ✓ |
| Watchlist | 5 symbols | Unlimited |
| Email / Telegram alerts | — | ✓ |
| On-demand backtest of a symbol | — | ✓ |
| CSV export | — | ✓ |
| Public track-record page | ✓ (marketing) | ✓ |

## 5. The signal redesign (Sprint 0 — gating)

Replace `getMultibaggerScore` with a **calibrated, rank-based score**:
1. Compute component features point-in-time (progressive-breakout count, breakout
   spacing/regularity, pullback discipline, volume confirmation, trend persistence /
   distance above rising MAs, base depth).
2. **Normalize each feature cross-sectionally** (percentile rank within the universe each day)
   — kills the saturation that made 91% `VERY_HIGH`.
3. Combine via a weighted rank or a simple fitted model; **validate monotonicity**: top
   decile must beat the base rate on mean AND median at 6m/12m on out-of-sample data.
4. Re-run `EdgeValidationRunner` (extended to decile buckets + a delisted-inclusive universe)
   as the acceptance gate. **No ship until top decile shows a clear positive spread.**

Acceptance metric: top-decile 12-month **median** spread > base rate by a margin that
survives a survivorship-adjusted universe.

## 6. Data (make-or-break)

- **MVP vendor:** Polygon.io or Tiingo EOD (US), licensed for commercial use (~$30–100/mo).
  Replaces the Yahoo scraper (no commercial license) and the broken NSE/Screener.in fetchers.
- **Must use split/dividend-ADJUSTED prices end-to-end.** Production code currently uses raw
  `close`, which fabricates breakouts/breakdowns around splits — a correctness bug
  (the harness already adjusts; port that logic into ingestion).
- Nightly ingestion job; store adjusted OHLCV in Postgres (TimescaleDB optional later).
- Universe membership stored as data (avoid hardcoded arrays); track point-in-time membership
  to fight survivorship bias in backtests.

## 7. Compliance (must clear before charging)

- Position as an **analytics/screening tool**, never "buy" recommendations.
- Prominent disclaimer; "for educational/informational purposes," publisher's exemption posture.
- US-first keeps it simpler; **India requires SEBI Research-Analyst review of framing before
  any paid Indian offering.** Do not skip legal review.
- No personalized advice, no auto-trading, no performance guarantees.

## 8. Technical architecture

### 8.1 Current state (to be refactored)
- Spring Boot 3.2 / Java 17, single module, in-memory H2 (data lost on restart),
  no auth, `CORS *`, secrets hardcoded (`YOUR_API_KEY`), mock `ScreenerService`
  (random data — **delete**), 1 static Thymeleaf page, zero tests, single-symbol synchronous API.

### 8.2 Target MVP architecture
```
                         ┌──────────────────────────┐
  Data vendor (Polygon)→ │ Ingestion job (nightly)   │→ Postgres (adjusted OHLCV,
                         │  - fetch adjusted EOD      │   universe, signals, users,
                         │  - upsert universe         │   watchlists, subscriptions)
                         └──────────────────────────┘
                                      │
                         ┌──────────────────────────┐
                         │ Scan job (nightly)        │  reuse BreakoutDetectionService +
                         │  - per symbol, point-in-  │  NEW CalibratedScoreService
                         │    time slice → signals   │→ persist ranked candidates per day
                         │  - rank cross-sectionally │
                         └──────────────────────────┘
                                      │
   Browser ── REST/JSON ─→ Spring Boot API ─→ services ─→ Postgres
        (SPA: Next.js or            │
         server-rendered)           ├─ Auth (Spring Security + JWT)
                                    ├─ Stripe webhooks (subscription state)
                                    └─ Alert dispatcher (email / Telegram) on new candidates
```

### 8.3 Key changes from current code
| Area | Now | MVP |
|---|---|---|
| DB | H2 in-memory | Postgres + Flyway migrations |
| Prices | raw close | split/div adjusted (port from harness) |
| Universe scan | single symbol, on request | nightly batch over full universe, results cached |
| Score | broken `getMultibaggerScore` | `CalibratedScoreService` (rank-normalized, validated) |
| Screener | mock random data | **deleted** (or real vendor screen) |
| Auth | none | Spring Security + JWT |
| Billing | none | Stripe subscriptions + webhooks |
| Secrets | hardcoded | env vars / secrets manager |
| Frontend | 1 static page | dashboard SPA + charts (Lightweight-Charts) |
| Tests | none | unit tests on detection math + scoring (fixtures), API tests |
| CORS | `*` | locked to app origin |

### 8.4 Reusable assets (keep)
- `BreakoutDetectionService` (the 12 detectors) — sound, but **must be run on point-in-time
  slices** and add unit tests pinning known outputs.
- `StaircasePatternService` pattern-gate logic (the binary gate has edge) — keep the gate,
  replace the scorer.
- `EdgeValidationRunner` — promote to the permanent **signal-research / acceptance harness**.

## 9. Milestones

- **Sprint 0 (1–2 wks):** signal redesign + re-validation gate. *Go/no-go for the whole SaaS.*
- **Sprint 1 (2–3 wks):** licensed data ingestion + Postgres + nightly universe scan.
- **Sprint 2 (2 wks):** dashboard (ranked list + chart + evidence) + accounts.
- **Sprint 3 (1–2 wks):** Stripe + alerts + public track-record page.
- **Beta:** free tier live, publish honest track record, gather feedback.
- **Sprint N+1:** India (licensed data + SEBI-reviewed framing).

## 10. Top risks

1. **Signal has no real edge after honest, survivorship-adjusted re-test** → the product has
   no reason to exist. *Mitigation:* Sprint 0 gate; do not build SaaS until passed.
2. Data licensing cost/complexity (esp. India). *Mitigation:* US-first.
3. Compliance (RA/RIA). *Mitigation:* tool framing + legal review before charging.
4. Overclaiming → reputational/legal. *Mitigation:* transparent backtest, tempered language.

## 11. Success metrics

- Signal: top-decile 12m median spread positive on out-of-sample, survivorship-adjusted data.
- Product: free→Pro conversion ≥ 3–5%; Pro 30-day retention ≥ 70%.
- Trust: public track record updated monthly, including misses.
