# CampusConnect

**Graph-based real-time peer matchmaking for collaborative learning and hackathon team synthesis.**

Students sign in with a verified college email, describe their subjects and skills, check in to a campus zone, and:

* **Mode 1 – Peer matching.** Everyone who is currently available is pooled into a weighted compatibility graph and
  paired with a **maximum-weight matching** (Edmonds' blossom algorithm). Both students must accept; they are told
  instantly over WebSocket.
* **Mode 2 – Hackathon teams.** Organisers define roles; students register with ranked preferences; the **Hungarian
  algorithm** assigns balanced, role-diverse teams instead of self-selection.

Details, formulas and measurements: [docs/ALGORITHMS.md](docs/ALGORITHMS.md) · [docs/EVALUATION.md](docs/EVALUATION.md).

| Layer | Technology |
|---|---|
| API | Java 21, Spring Boot 3.4 (Web, Security, Data JPA, WebSocket/STOMP, Actuator), Flyway |
| Data | PostgreSQL 16 (single source of truth) |
| Web | React 19, TypeScript, Vite, React Router, STOMP over WebSocket |
| Tests | JUnit 5 on an embedded real PostgreSQL, Vitest + Testing Library |

## Run locally

### Option A – Docker Compose

```bash
cp .env.example .env     # set JWT_SECRET, DB_PASSWORD, APPROVED_DOMAINS
docker compose up --build
```

* App: http://localhost:5173
* Mailpit (catches verification and reset emails): http://localhost:8025
* API: http://localhost:8080/api/v1 (health: `/actuator/health`)

Register with an address on an approved domain, open the link in Mailpit, then log in.

**No demo accounts exist in this mode** (they are created only by the `dev` profile), so logging in as `asha@college.edu` fails until you register. To start with the demo accounts instead:

```bash
SPRING_PROFILES_ACTIVE=dev docker compose up --build   # PowerShell: $env:SPRING_PROFILES_ACTIVE="dev"; docker compose up --build
```

Login problems usually mean: the email domain is not in `APPROVED_DOMAINS` (default `college.edu`), the email was not verified yet (check Mailpit at http://localhost:8025), or too many attempts (login is rate limited to 8 per 15 minutes per email).

### Option B – No Docker (embedded PostgreSQL, 400 demo students)

Nothing but the three tools below is needed: the database is an embedded PostgreSQL that downloads once and then lives
in `target/dev-pg`, so no Docker and no separate PostgreSQL install.

**1. Install once**

| Tool | Version | Check |
|---|---|---|
| JDK | 21 | `java -version` |
| Maven | 3.9+ | `mvn -version` |
| Node.js | 22.12 or newer | `node -v` |

**2. Get the project** (the folder must contain `pom.xml`, `frontend/` and `scripts/seed-accounts.csv`)

```bash
git clone <repository-url> CampusConnect
cd CampusConnect
```

**3. Terminal 1, backend** (the first start downloads dependencies and PostgreSQL, so allow a few minutes; later starts
take about 30 seconds). Leave it running.

```bash
mvn -q test-compile exec:java -Dexec.mainClass=edu.campusconnect.DevServer -Dexec.classpathScope=test
```

Wait for `Fake campus refreshed: ... generated students on campus` in the log. That is the signal that the 8 demo
accounts and the 400 generated students are in the database.

**4. Terminal 2, frontend**

```bash
cd frontend
npm ci
npm run dev
```

**5. Open http://localhost:5173** and log in with `password-123456` as `asha@college.edu` (administrator),
`ravi@college.edu`, or any generated student from `scripts/seed-accounts.csv`. Interactive API docs:
http://localhost:8080/swagger-ui.html.

**Stop** both terminals with Ctrl+C. The data stays in `target/dev-pg`, so the next start picks up where you left off;
delete that folder to start from scratch (the same 400 students are created again).

If something does not start:

* `could not lock target\dev-pg\epg-lock`: a previous run left PostgreSQL behind. End the `postgres.exe` and `java.exe`
  processes in Task Manager (or `taskkill /F /IM postgres.exe`), delete `target\dev-pg\epg-lock`, start again.
* `Port 8080 / 5173 / 54329 is already in use`: another copy of the app is still running; stop it first.
* The page loads but login fails: the backend is not up yet; wait for the log line above.

## Tests

```bash
mvn verify                                  # 156 backend tests (unit + integration on real PostgreSQL)
cd frontend && npm test && npm run lint && npm run build
mvn test -Dtest=EvaluationReport -Deval=true   # regenerate docs/EVALUATION.md
```

## API overview (all under `/api/v1`, bearer token unless noted)

| Area | Endpoints |
|---|---|
| Auth (public) | `POST auth/register`, `verify`, `resend-verification`, `login`, `refresh`, `logout`, `forgot-password`, `reset-password` |
| Profile | `GET/PUT students/me`, `POST students/me/skills`, `POST students/me/subjects`, `DELETE …/{id}`, `GET skills?q=`, `GET subjects?q=` |
| Presence | `GET presence/zones`, `POST presence/check-in`, `POST presence/check-out`, `GET presence/me`, `GET presence/available` |
| Peer matching | `POST matching/find`, `GET matching/suggestions`, `GET matching/matches/current`, `GET matching/matches`, `POST matching/matches/{id}/accept\|decline\|complete` |
| Hackathons | `GET/POST hackathons`, `GET hackathons/{id}`, `PUT/DELETE hackathons/{id}/registration`, `POST …/close\|reopen\|synthesize\|publish`, `GET …/teams` |
| Admin | `GET/POST admin/zones`, `PUT admin/zones/{id}` (administrators only) |
| Skill verification | `GET assessments/status`, `POST assessments`, `POST assessments/{id}/submit` |
| Notifications | `GET notifications`, `POST notifications/{id}/read`, `POST notifications/read-all` |
| Real time | STOMP at `/ws` (token in the CONNECT frame); subscribe to `/user/queue/updates` and `/topic/presence` |

## Configuration

Everything is environment driven (see [.env.example](.env.example) and `application.yml`). The service refuses to start
without a `JWT_SECRET` of at least 32 characters. Matching behaviour (weights, intervals, cool-downs, pool cap) is
under `app.matching.*`.

## Demo data and the matching graph

The dev profile creates 400 verified students with subjects, skills and check-ins the first time it starts, and keeps
them in the database afterwards (`APP_SEED_FAKE_STUDENTS=0` turns it off, a bigger number tops the database up).
Generation is reproducible via `app.seed.random-seed`; every account uses `password-123456`. The account list is the
committed file `scripts/seed-accounts.csv` (rebuilt from the database if it is missing). Check-ins expire after 15
minutes, so the seeder also puts the generated students back on campus at start-up and every five minutes
(`app.seed.keep-alive=false` disables that). Automatic matching rounds run every two minutes in the dev profile
(`APP_MATCHING_ROUND_INTERVAL`), which keeps most students free to receive a request. `python scripts/simulate.py` then drives every flow against the running server and prints
a latency table. Administrators get a **Match graph** page: each available student is a node (coloured by zone), a line
is a compatible pair, and green lines are the pairs chosen by the maximum-weight matching. Click a student to see the
candidates and the score breakdown. The page also compares the optimal matching with a greedy baseline, shows the
timing of recent rounds, and runs an independent optimality check (no re-pairing of two matched pairs, or swap with an
unmatched student, may improve the total weight).

Hackathon organisers (and administrators) get an **Analyse teams** page for every hackathon: the teams the Hungarian
synthesis forms from the current registrations next to random teams of the same sizes (mean of 50 shuffles), a chart
of every team's strength (flat = balanced), how many people got their 1st/2nd/3rd choice, a first-choice-vs-role-given
table, and a searchable grid of all teams where clicking a participant shows their skills and fit for every role. It
also proves the role allocation optimal: no cycle of participants trading roles can raise the total fit (checked
exactly on the role exchange graph). `python scripts/simulate_hackathon.py` registers every seeded student with
skill-based preferences and checks the whole flow end to end. For a big graph on the match-graph page, start the
server with `APP_MATCHING_ROUND_INTERVAL=1h` so the automatic rounds do not pair everyone before you look.

## Administrators and campus zones

Put the administrators' emails in `ADMIN_EMAILS` (comma separated); they receive the role when they next log in and see a
**Zones** page where they can add, rename, move, enable and disable campus zones (coordinates are metres on a campus
plane; closer zones score higher in matching). Zones are disabled rather than deleted so history stays intact. In the dev
profile `asha@college.edu` is an administrator.

## AI skill verification (optional)

Set `AI_ENABLED=true` plus `AI_BASE_URL`, `AI_API_KEY` and `AI_MODEL` for any OpenAI-compatible chat endpoint (a hosted
provider, or a local server such as Ollama). Students then get a **Verify** button next to each skill and subject: they
answer a few generated questions and a model grades them. A check can confirm or **lower** a self-assessed level, never
raise it; matching, presence and team synthesis use the claim capped by the verified level, and editing a claim clears its
old verification. Answers are escaped and fenced against prompt injection, replies are parsed strictly, and retakes are
rate limited with a one-hour cool-down. With the feature off the buttons are hidden.

## Security notes

* Passwords are BCrypt hashed; access tokens are 15-minute JWTs kept in memory; refresh tokens are random, stored only
  as SHA-256 hashes, rotated on every use with replay detection, and delivered in an `HttpOnly`, `SameSite=Strict`
  cookie. Set `SECURE_COOKIES=true` behind HTTPS.
* Registration, resend, login and password reset are rate limited and do not reveal whether an email has an account.
  The limiter is per instance; use a gateway or shared store if you scale the API horizontally.
* Only approved college email domains can register, and accounts must verify their address.

## Known limitations

* Zone check-ins are self-reported (no GPS / Wi-Fi verification) and expire after 15 minutes.
* Proficiency is self-assessed unless the optional AI verification is enabled; grades from a language model are an aid, not proof.
* Zone coordinates in `V3__presence_context.sql` are demonstration data.
* Exact matching is cubic in pool size; the pool is capped at 500 students per round (see EVALUATION.md).
