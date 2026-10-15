#!/usr/bin/env python3
"""End-to-end test of hackathon team synthesis with every seeded student (default 400).

Usage: python scripts/simulate_hackathon.py [--base http://localhost:8080] [--accounts scripts/seed-accounts.csv]
                                            [--users 400] [--no-publish]

Creates a five-role hackathon as the admin, registers every student with role preferences derived from their own
skills (some rank randomly, some give none, like real people), closes registration and then checks the synthesis:
everyone is placed exactly once, team sizes differ by at most one, no role repeats inside a team, smaller teams drop
the lowest-priority roles, the analysis page's optimality proof passes, Hungarian teams beat random teams on fit,
preferences and balance, and the saved draft is exactly the analysed preview. Finally it publishes and checks that
students see their own team. Prints a timing table; exits non-zero when a check fails. Standard library only.
"""
import argparse
import csv
import os
import math
import random
import sys
import time
from concurrent.futures import ThreadPoolExecutor

from simulate import ADMIN, call, check, failures, login, timings, report

ROLES = [
    {"name": "Backend", "keywords": ["java", "spring", "node", "sql", "postgresql", "mongodb", "go", "rust", "kotlin"]},
    {"name": "Frontend", "keywords": ["react", "typescript", "javascript", "css", "html", "flutter"]},
    {"name": "Data / ML", "keywords": ["python", "ml", "pandas", "tensorflow", "pytorch"]},
    {"name": "DevOps", "keywords": ["docker", "kubernetes", "aws", "linux", "git"]},
    {"name": "Design & Product", "keywords": ["figma", "ux", "pitch"]},
]
LEVEL = {"BEGINNER": 1, "INTERMEDIATE": 2, "ADVANCED": 3}


def preferences(skills, role_ids, rng):
    """What a student would pick: roles their skills fit best (70%), random roles (15%) or nothing (15%)."""
    roll = rng.random()
    if roll < 0.15:
        return []
    if roll < 0.30:
        return rng.sample(role_ids, rng.randint(1, 3))
    scored = []
    for role, rid in zip(ROLES, role_ids):
        score = sum(LEVEL.get(s["proficiency"], 1) for s in skills if s["name"].lower() in role["keywords"])
        scored.append((score + rng.random() * 0.5, rid))
    scored.sort(reverse=True)
    return [rid for score, rid in scored if score >= 1][: rng.randint(1, 3)] or [scored[0][1]]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--base", default="http://localhost:8080")
    ap.add_argument("--accounts", default=os.path.join(os.path.dirname(os.path.abspath(__file__)), "seed-accounts.csv"))
    ap.add_argument("--users", type=int, default=400)
    ap.add_argument("--threads", type=int, default=16)
    ap.add_argument("--seed", type=int, default=2026)
    ap.add_argument("--no-publish", action="store_true", help="leave the teams as a draft so the page can re-run")
    args = ap.parse_args()
    base = args.base
    rng = random.Random(args.seed)

    with open(args.accounts, newline="", encoding="utf-8") as f:
        emails = [r["email"] for r in csv.DictReader(f)][: args.users]
    print(f"== {len(emails)} seeded accounts")

    print("== login (parallel)")
    t0 = time.perf_counter()
    with ThreadPoolExecutor(args.threads) as pool:
        tokens = dict(zip(emails, pool.map(lambda e: login(base, e), emails)))
    tokens = {e: t for e, t in tokens.items() if t}
    check(len(tokens) == len(emails), f"all logins succeed ({len(tokens)}/{len(emails)})")
    print(f"   {len(tokens)} logged in in {time.perf_counter() - t0:.1f}s")
    admin = login(base, ADMIN)
    if not check(admin is not None, "admin login"):
        return report()

    print("== create hackathon")
    status, hack = call(base, "POST", "/hackathons", admin, {
        "name": f"Synthesis Test {time.strftime('%H:%M:%S')}", "description": "400-student team synthesis test",
        "roles": ROLES}, label="POST /hackathons")
    if not check(status == 201, f"create hackathon ({status})"):
        return report()
    hid = hack["summary"]["id"]
    role_ids = [r["id"] for r in hack["summary"]["roles"]]
    role_name = {r["id"]: r["name"] for r in hack["summary"]["roles"]}

    print("== register everyone with skill-based preferences")
    with ThreadPoolExecutor(args.threads) as pool:
        profiles = list(pool.map(lambda t: call(base, "GET", "/students/me", t), tokens.values()))
    prefs = {}
    for token, (status, me) in zip(tokens.values(), profiles):
        if status == 200:
            prefs[token] = (me["id"], preferences(me["skills"], role_ids, rng))
    with ThreadPoolExecutor(args.threads) as pool:
        codes = list(pool.map(lambda kv: call(base, "PUT", f"/hackathons/{hid}/registration", kv[0],
                                              {"rolePreferences": kv[1][1]})[0], prefs.items()))
    registered = sum(1 for c in codes if c == 200)
    check(registered == len(tokens), f"every student registers ({registered}/{len(tokens)})")
    given = [p for _, p in prefs.values()]
    print(f"   {registered} registered; {sum(1 for p in given if p)} gave preferences, "
          f"{sum(1 for p in given if not p)} gave none")
    check(call(base, "POST", f"/hackathons/{hid}/close", admin)[0] == 200, "close registration")

    print("== analysis page (preview, repeated to separate JIT warm-up)")
    for _ in range(3):
        status, a = call(base, "GET", f"/hackathons/{hid}/analysis", admin, label="GET analysis (preview)")
        if status != 200:
            break
        timings.setdefault("SYNTHESIS two Hungarian stages (server)", []).append(a["hungarian"]["millis"])
        print(f"   synthesis {a['hungarian']['millis']:.1f} ms for {a['participants']} participants")
    if not check(status == 200, f"analysis endpoint ({status})"):
        return report()

    n, roles = a["participants"], len(role_ids)
    check(n == registered, f"analysis sees every registration ({n})")
    check(a["teamCount"] == math.ceil(n / roles), f"team count is ceil(n / roles) = {a['teamCount']}")
    placed = [m["studentId"] for t in a["teams"] for m in t["members"]]
    check(len(placed) == n and len(set(placed)) == n, "every participant placed exactly once")
    sizes = [len(t["members"]) for t in a["teams"]]
    check(max(sizes) - min(sizes) <= 1, f"team sizes differ by at most one ({min(sizes)}-{max(sizes)})")
    for t in a["teams"]:
        got = sorted(role_ids.index(m["roleId"]) for m in t["members"])
        if not check(got == list(range(len(t["members"]))),
                     f"team {t['number']} has one of each top-{len(t['members'])} role ({got})"):
            break
    check(sum(map(sum, a["flow"])) == n, "role flow table accounts for everyone")
    for i, r in enumerate(a["roles"]):
        check(r["holders"] == sum(row[i] for row in a["flow"]), f"holders of {r['name']} match the flow table")
    c = a["check"]
    check(c["passed"] and c["improvingCycles"] == 0, "optimality proof passes")
    check(c["movesExamined"] == n * (roles - 1), f"proof priced every move ({c['movesExamined']})")

    h, r = a["hungarian"], a["random"]
    check(h["averageFit"] > r["averageFit"], "Hungarian fit beats random")
    check(h["preferenceSatisfaction"] > r["preferenceSatisfaction"], "Hungarian meets more preferences than random")
    check(h["strengthStdDev"] < r["strengthStdDev"], "Hungarian teams are more balanced than random")
    with_prefs = sum(1 for p in given if p)
    print(f"   teams={a['teamCount']} sizes={min(sizes)}-{max(sizes)} roles={roles}")
    print(f"   average fit        Hungarian {h['averageFit']:.3f}  random {r['averageFit']:.3f}")
    print(f"   top-3 role given   Hungarian {h['preferenceSatisfaction']:.1%}  random {r['preferenceSatisfaction']:.1%}"
          f"  (only {with_prefs / n:.0%} gave any preference)")
    print(f"   strength sigma     Hungarian {h['strengthStdDev']:.4f}  random {r['strengthStdDev']:.4f}")
    print(f"   strength spread    Hungarian {h['strengthSpread']:.3f}  random {r['strengthSpread']:.3f}")
    print(f"   proof: {c['movesExamined']} moves priced, {c['improvingCycles']} improving cycles, "
          f"total fit {c['totalFit']:.2f}")
    print("   first choice -> role given:")
    print("   " + " " * 18 + "".join(f"{role_name[x][:10]:>11}" for x in role_ids))
    for i, row in enumerate(a["flow"]):
        label = role_name[role_ids[i]] if i < roles else "no preference"
        print(f"   {label[:18]:<18}" + "".join(f"{v:>11}" for v in row))

    print("== generate (saved draft) and compare with the preview")
    status, draft = call(base, "POST", f"/hackathons/{hid}/synthesize", admin, label="POST synthesize teams")
    if check(status == 200, "synthesize"):
        stored = {(t["number"], m["studentId"]) for t in draft["teams"] for m in t["members"]}
        preview = {(t["number"], m["studentId"]) for t in a["teams"] for m in t["members"]}
        check(stored == preview, "saved draft is exactly the analysed preview")

    if not args.no_publish:
        print("== publish and read back as students")
        check(call(base, "POST", f"/hackathons/{hid}/publish", admin)[0] == 200, "publish teams")
        sample = list(tokens.values())[:60]
        with ThreadPoolExecutor(args.threads) as pool:
            views = list(pool.map(lambda t: call(base, "GET", f"/hackathons/{hid}/teams", t), sample))
        own = [sum(1 for t in v["teams"] if t["yours"]) for s, v in views if s == 200]
        check(len(own) == len(sample) and all(x == 1 for x in own), "each student sees exactly one team as theirs")

    print(f"\nAnalysis page: http://localhost:5173/hackathons/{hid}/analysis")
    return report()


if __name__ == "__main__":
    sys.exit(main())
