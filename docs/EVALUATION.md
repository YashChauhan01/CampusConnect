# Performance evaluation

Generated 2026-10-04 on Windows 11, Java 21.0.10, 12 logical CPUs. All data is synthetic: 30 subjects with skewed popularity, 2–5 subjects per student, three proficiency levels and four campus zones. Timings are the median of 5 runs after warm-up; quality figures are means over 30 random instances.

## Mode 1 — peer matching (maximum-weight matching vs. greedy)

Each round builds the compatibility graph of all available students and pairs them. "Optimal" is the exact blossom algorithm used in production; "greedy" repeatedly takes the heaviest remaining edge. Weight ratio = greedy total weight ÷ optimal total weight.

| Pool | Edges | Optimal (ms) | Greedy (ms) | Optimal weight | Greedy weight | Greedy / optimal | Students matched (opt / greedy) |
|---:|---:|---:|---:|---:|---:|---:|---:|
| 50 | 691 | 5.1 | 2.7 | 19.14 | 17.74 | 0.927 | 100.0% / 92.0% |
| 100 | 2233 | 12.9 | 7.4 | 40.02 | 38.29 | 0.957 | 100.0% / 96.0% |
| 200 | 9853 | 46.3 | 15.7 | 82.53 | 80.34 | 0.973 | 100.0% / 99.0% |
| 400 | 39361 | 106.8 | 45.0 | 171.97 | 166.20 | 0.966 | 100.0% / 98.5% |
| 800 | 160667 | 1699.6 | 178.9 | 353.67 | 345.11 | 0.976 | 100.0% / 99.8% |
| 1600 | 655681 | 22189.6 | 749.5 | 725.05 | 706.81 | 0.975 | 100.0% / 99.9% |

Over 100 further random pools of 100 students, greedy achieved on average 95.8% of the optimal total weight (worst case 93.0%).

### End-to-end check with 400 generated students (2026-10-08)

The dev profile can generate a synthetic campus (`APP_SEED_FAKE_STUDENTS=400`, see README); `scripts/simulate.py` then
logs all 400 students in and exercises every flow through the HTTP API. Result: all checks passed (400 logins, profile
and availability reads, a live matching round, the admin graph with its optimality check, 136 accepts and 34 declines,
a 200-person hackathon registered, closed, synthesized and published).

The live pool had 343 available students and 24,928 compatible pairs (42% of all pairs, a much denser graph than the
synthetic benchmark above, because on a single campus almost any two people share some subject). Server-side time for
graph construction plus the exact blossom solve was **about 200-250 ms in steady state** (graph build about 70 ms, solve
about 150 ms; first call after start-up 660 ms before the JIT warms up), on a laptop that was also running the
database, the browser and the build tools. A full HTTP round trip for "find me a partner" (pool load from PostgreSQL,
solve, persist proposals) was about 0.5 s. The 0.1 s figure in the table above holds for sparser pools (about 40k edges
at 400 students); on dense real-looking data expect a few hundred milliseconds, still well inside the 20 s round interval.
Greedy reached 97.8% of the optimal weight here (141.47 vs 144.73).

## Mode 2 — hackathon team synthesis (Hungarian algorithm vs. random teams)

Five roles (backend, frontend, data/ML, design, product); participants rank up to three roles. Fit is the mean role fit (higher is better), preference is the share of participants who got one of their top-3 roles, and σ is the standard deviation of team strength (lower is more balanced).

| Participants | Teams | Synthesis (ms) | Fit: Hungarian / random | Preferences met: Hungarian / random | σ strength: Hungarian / random |
|---:|---:|---:|---:|---:|---:|
| 12 | 3 | 1.1 | 0.568 / 0.286 | 73.9% / 41.9% | 0.0191 / 0.0556 |
| 24 | 5 | 1.5 | 0.604 / 0.271 | 79.3% / 41.4% | 0.0169 / 0.0655 |
| 48 | 10 | 3.1 | 0.613 / 0.272 | 79.4% / 39.0% | 0.0184 / 0.0686 |
| 96 | 20 | 5.0 | 0.615 / 0.281 | 79.0% / 41.4% | 0.0181 / 0.0702 |
| 192 | 39 | 13.2 | 0.618 / 0.281 | 81.6% / 41.1% | 0.0189 / 0.0728 |
| 384 | 77 | 55.6 | 0.614 / 0.275 | 80.9% / 39.9% | 0.0205 / 0.0736 |

Regenerated 2026-10-11 after role-keyword matching switched from substrings to whole tokens (see `RoleFitModel`). The quality figures are unchanged because this synthetic skill pool has no short-name collisions such as "c" in "css" or "go" in "mongodb"; only the timings moved.

### End-to-end check with 400 generated students (2026-10-11)

`scripts/simulate_hackathon.py` registered all 400 seeded students for a five-role hackathon (Backend, Frontend,
Data / ML, DevOps, Design & Product) with preferences derived from their own skills: 70% ranked the roles their skills
fit, 15% ranked at random and 15% gave none (347 gave preferences, 53 none). Result: all checks passed (everyone
placed once, 80 teams of 5, one of each role per team, saved draft identical to the analysed preview, every student
sees exactly one team after publishing).

| | Hungarian | Random (mean of 50) |
|---|---:|---:|
| Average role fit | 0.752 | 0.316 |
| Got a top-3 role | 83.2% | 32.3% |
| σ of team strength | 0.0174 | 0.0822 |
| Strongest − weakest team | 0.089 | 0.387 |

Synthesis took about 180-190 ms server-side once warm (388 ms on the first call), on the same laptop as above. The
optimality proof priced all 1,600 alternative role moves and found no improving cycle. Backend was over-subscribed
(120 first choices for 80 places), so 44 of those 120 were given another role; this is
the trade-off the optimum makes, not a fault. Figures are after the switch to whole-token skill matching; with the
old substring matching (where "C" matched "css" and "Go" matched "mongodb") fit was inflated to 0.776 / 0.377.

## Hungarian algorithm scaling (dense n × n cost matrix)

| n | Time (ms) |
|---:|---:|
| 50 | 0.1 |
| 100 | 0.3 |
| 200 | 2.3 |
| 400 | 9.6 |
| 800 | 43.9 |

