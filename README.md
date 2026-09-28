# CampusConnect — Features 1–3

A new, standalone project implementing college email authentication, student skills/subjects, and expiring campus-zone availability. The approved academic matching algorithms and Neo4j integration are **not** included yet.

## Run locally

Requires Docker Compose. Copy `.env.example` to `.env` and set a strong JWT secret, database password and your real approved college email domain(s). Then run:

```bash
docker compose -f compose.yml up --build
```

- React: http://localhost:5173
- Mailpit (development email inbox): http://localhost:8025
- Spring Boot API: http://localhost:8080/api/v1

Register with an email in `APPROVED_DOMAINS`, find the verification email in Mailpit, follow the link, then log in. In Profile, add skills and subjects. In Campus presence, check in to a zone and browse students currently available. Check-ins expire after 15 minutes and must be manually renewed. **A zone check-in is self-reported, not GPS-verified**.

## API

| Method | Path | Purpose |
|---|---|---|
| POST | `/api/v1/auth/register` | Create account, send verification email |
| POST | `/api/v1/auth/verify` | Verify email with `{token}` |
| POST | `/api/v1/auth/login` | Access token + refresh cookie |
| POST | `/api/v1/auth/refresh` | Rotate refresh cookie, issue access token |
| POST | `/api/v1/auth/logout` | Revoke refresh token |
| GET/PUT | `/api/v1/students/me` | Read/edit profile |
| POST | `/api/v1/students/me/skills` | Add a skill with proficiency |
| POST | `/api/v1/students/me/subjects` | Add a subject with proficiency |
| GET | `/api/v1/presence/zones` | Campus zones |
| POST | `/api/v1/presence/check-in` | 15-minute presence |
| POST | `/api/v1/presence/check-out` | Remove presence |
| GET | `/api/v1/presence/available` | Currently available students |

Protected endpoints require `Authorization: Bearer <accessToken>`. Refresh tokens are stored as SHA-256 hashes in PostgreSQL and delivered via HttpOnly cookies. Access tokens are kept in browser memory. PostgreSQL owns student data; Redis stores expiring presence keys.

## Tests

Two JWT unit tests are included. Full backend/frontend integration tests and Docker startup remain to be run in a development environment with Maven, Docker and npm registry access.

## Local tests

```bash
mvn test
cd frontend && npm install && npm run build && npm test
```

## Known limitations before deployment

- This is a development prototype, **not production-hardened**. Add login/registration rate limiting, email resend, password reset, refresh-token replay-family invalidation, CSRF protection for cookie-authenticated endpoints, audit logging and stronger operational monitoring before deployment.
- The React dev proxy is configured for Docker's `backend` service. For running Vite directly on your host, change its proxy target to `http://localhost:8080`.
- Email delivery and Redis are required for registration and check-ins, respectively.
- Campus zones are demonstration data; replace them with actual campus zones.
- No GPS/location verification, Neo4j, matching algorithms, SQS, or AI agent is implemented.
