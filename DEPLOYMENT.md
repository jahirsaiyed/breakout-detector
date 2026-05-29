# Deployment — Supabase (DB) + Render (backend) + Vercel (frontend)

Architecture: **Vercel** (Next.js UI) → calls → **Render** (Spring Boot API) → **Supabase** (Postgres).

Everything in this repo is deploy-ready and builds locally (frontend `next build` ✓, backend
Docker image ✓). The steps below are the parts that need **your accounts and secrets** — none of
which are committed to the repo.

> Security: never commit secrets. Set them only in the Render / Vercel dashboards. `.gitignore`
> already excludes `.env`, `data/`, and `research-cache/`.

---

## 0. Push to GitHub (Render & Vercel deploy from a Git repo)

```bash
cd D:/Garage/BreakoutDetector
git init && git add . && git commit -m "feat: long-base breakout scanner + UI, deploy-ready"
git branch -M main
git remote add origin https://github.com/<you>/breakout-detector.git
git push -u origin main
```

---

## 1. Supabase — Postgres

1. Create a project at supabase.com. Set a strong **database password** (save it).
2. Project → **Connect** → **Session pooler** (NOT "Direct connection"). Render is IPv4-only and
   Supabase's direct host is IPv6-only, so you **must** use the pooler. Copy its host/port/user.
3. You'll build the JDBC URL for Render in step 2. The table (`candidates`) is created
   automatically by Hibernate (`ddl-auto=update`) on first boot.

---

## 2. Render — backend API (Docker)

1. New → **Web Service** → connect your GitHub repo. Render detects `render.yaml` (Docker).
2. Set these environment variables (Dashboard → Environment) — values from Supabase:

   | Key | Value |
   |---|---|
   | `SPRING_DATASOURCE_URL` | `jdbc:postgresql://<pooler-host>:5432/postgres?sslmode=require` |
   | `SPRING_DATASOURCE_USERNAME` | `postgres.<project-ref>` (the pooler username) |
   | `SPRING_DATASOURCE_PASSWORD` | your Supabase DB password |
   | `APP_CORS_ALLOWED_ORIGINS` | your Vercel URL (fill after step 3, e.g. `https://your-app.vercel.app`) |
   | `H2_CONSOLE_ENABLED` | `false` |

3. Deploy. Health check path is `/breakout-detector/actuator/health` (already in `render.yaml`).
4. Note the service URL, e.g. `https://breakout-detector-api.onrender.com`.
   API base = that URL **plus** `/breakout-detector`.
5. Run a first scan (free tier sleeps; first call wakes it):
   ```
   curl -X POST "https://breakout-detector-api.onrender.com/breakout-detector/api/candidates/scan?limit=200"
   ```

> ⚠️ Data-source caveat: the scanner pulls EOD prices from Yahoo. From a datacenter IP Yahoo may
> rate-limit/refuse. For production reliability, move to a licensed EOD vendor (Polygon/Tiingo) —
> see `docs/PRD_and_Architecture.md`. For demos, run scans with a `limit` and retry.

---

## 3. Vercel — frontend

1. New Project → import the same repo → set **Root Directory = `frontend`** (Vercel auto-detects Next.js).
2. Environment variable:

   | Key | Value |
   |---|---|
   | `NEXT_PUBLIC_API_BASE` | `https://breakout-detector-api.onrender.com/breakout-detector` |

3. Deploy → you get `https://your-app.vercel.app`.
4. Go back to **Render** and set `APP_CORS_ALLOWED_ORIGINS` to that Vercel URL; redeploy the backend.

---

## 4. Verify

- `https://your-app.vercel.app` loads, hero renders, and the "Latest scan" grid populates
  (after a scan has run in step 2.5). An empty grid is handled gracefully.
- CORS: browser console shows no CORS errors (means `APP_CORS_ALLOWED_ORIGINS` matches the Vercel origin).

## Local dev

```bash
# backend (H2, no external deps)
java -cp "target/classes;$(cat cp.txt)" com.breakoutdetector.BreakoutDetectorApplication
# frontend
cd frontend && cp .env.local.example .env.local && npm install && npm run dev
```

## CI note
The Docker build runs `mvn -DskipTests package`. Run the unit tests in CI separately:
`mvn test` (they use synthetic data, no network).
