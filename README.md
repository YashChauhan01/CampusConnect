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

### Option B – No Docker (embedded PostgreSQL, demo data)

```bash
# terminal 1 – backend with the dev profile, emails printed to the log, 8 demo students
mvn -q test-compile exec:java -Dexec.mainClass=edu.campusconnect.DevServer -Dexec.classpathScope=test
# terminal 2 – frontend with hot reload
cd frontend && npm ci && npm run dev
```

Open http://localhost:5173 and log in as `asha@college.edu` or `ravi@college.edu`, password `password-123456`
(dev profile only). Interactive API docs are at http://localhost:8080/swagger-ui.html in the dev profile.

## Tests

```bash
mvn verify                                  # 138 backend tests (unit + integration on real PostgreSQL)
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
