# Algorithms

CampusConnect implements the two optimisation problems from the project proposal. Both algorithms are written from
scratch in `edu.campusconnect.matching.algo` (no solver library), use exact integer arithmetic, and are verified
against exhaustive search (see [Verification](#verification)). Measured performance is in
[EVALUATION.md](EVALUATION.md).

## Mode 1 — real-time peer matchmaking

**Problem.** Given the students who are currently checked in as *available*, pair them so that the total academic
compatibility is maximal. This is **maximum-weight matching in a general graph**: vertices are students, an edge
exists between two students who could usefully study together, and the edge weight is their compatibility.

**Edge weight** (`CompatibilityScorer`). For students *u*, *v* let *S* be the subjects both know that at least one of
them wants to work on today (chosen explicitly at check-in, mentioned in the free-text requirement, or — if neither —
all of their subjects). No shared subject means no edge.

| Component | Definition | Default weight |
|---|---|---:|
| knowledge | mean over *S* of f(\|level_u − level_v\|), f = [0.65, 1.0, 0.75] — a one-level gap (peer explains to peer) is best, equal levels work well, a two-level gap is more one-way mentoring | 0.45 |
| reciprocity | 1 if each student is stronger in at least one subject of *S* (they can teach each other) | 0.15 |
| breadth | min(1, \|S\| / 3) | 0.15 |
| proximity | exp(−d / 120 m) between the two check-in zones; 1 within a zone | 0.25 |

The weight is the convex combination, so it lies in [0, 1]; edges below `app.matching.min-edge-weight` (0.30) are
dropped. All weights are configurable under `app.matching.*`.

**Solver** (`MaxWeightMatching`). Edmonds' blossom algorithm with primal–dual updates, O(V³). Weights are scaled to
integers (×10⁶) and doubled internally so that every dual variable stays integral — there is no floating-point
tolerance anywhere in the solver. The matching is *not* forced to be perfect or maximum-cardinality: a pair is only
formed if it adds compatibility.

**Rounds.** A round (scheduled every 20 s, or on demand via "Find a partner") loads the pool, removes students who
already have a live match and pairs that declined each other within the cool-down, solves the matching, and stores
the pairs as *proposals*. A pair becomes a confirmed match only when **both** accept; contact details are revealed
at that point. A decline frees both students and keeps the pair apart for 24 h. Unanswered proposals expire after
5 minutes. Rounds are serialised across instances with a PostgreSQL advisory lock, and a partial unique index
guarantees that a student is in at most one live match.

## Mode 2 — hackathon team synthesis

**Problem.** Assign registered students to balanced, role-diverse teams instead of letting them self-select.

**Setup.** With *n* participants and *R* roles (in priority order) create T = ⌈n / R⌉ teams of size ⌊n / T⌋ or
⌈n / T⌉. A team with fewer than *R* members drops its lowest-priority roles. Role fit
(`RoleFitModel`) is `0.6 · skillMatch + 0.4 · preference`, where skillMatch compares the participant's profile
skills with the role's keywords and preference is 1.0 / 0.7 / 0.4 for first / second / third choice.

**Solver** (`HungarianAlgorithm`, rectangular minimum-cost assignment with potentials, O(n²m); `TeamSynthesizer`).
Two exact assignment stages:

1. *Role allocation* — participants × role slots, cost `1 − fit`. The optimum places every participant in the role
   that minimises total mismatch while guaranteeing one member per role per team.
2. *Team balancing* — role by role, the participants holding that role are assigned to the teams that need it with
   cost `(S_t + s_p)²`, where `S_t` is the strength already in team *t*. Minimising the sum of squares pairs strong
   participants with currently weak teams, so team strengths stay close.

Stage 1 is exactly optimal for total fit; stage 2 is exactly optimal for each role given the earlier ones (a greedy
sequence of optimal steps rather than one global optimum — a deliberate trade-off to keep the cost matrix linear).
Organisers can re-run the synthesis as often as they like; the result stays a private draft until they publish it.

## Verification

* `MaxWeightMatchingTest` compares the blossom solver against an exact bitmask-DP oracle on 3,000 random graphs
  (2–14 vertices, dense and sparse, heavy ties) plus hand-built blossom cases.
* `HungarianAlgorithmTest` compares against brute force on 2,000 random rectangular matrices.
* `TeamSynthesizerTest` proves stage 1 reaches the exhaustive-search optimum and that synthesis beats random teams on
  both fit and balance.
* `MatchingEngineTest` checks that the optimal matching is never lighter than the greedy baseline.

## Operational limits

Blossom matching is cubic in the pool size. Measured on a 12-thread laptop CPU (see EVALUATION.md): about 0.1 s for 400
simultaneous students, 1.7 s for 800, 22 s for 1,600. The default `app.matching.max-pool-size` is therefore 500. For a
larger campus, partition the pool by zone cluster (proximity already makes cross-campus edges light) and match each
partition independently.

## Limitations

* Check-ins are self-reported; there is no GPS or Wi-Fi verification of the zone.
* Proficiency is self-assessed. AI-assisted skill verification (an extension discussed in the proposal) is not
  implemented.
* Zone coordinates are demonstration data (`V3__presence_context.sql`); replace them with the real campus map.
