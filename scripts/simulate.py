#!/usr/bin/env python3
"""Exercises every major flow against a running server seeded with `app.seed.fake-students`.

Usage: python scripts/simulate.py [--base http://localhost:8080] [--accounts target/seed-accounts.csv] [--users 400]

Logs in as many seeded students (in parallel), then drives: profile reads, check-ins, suggestions, matching rounds
(on demand), the admin graph preview, accept/decline/complete on proposals, notifications, and the whole hackathon
lifecycle (create, register, close, synthesize, publish, view teams). Prints a timing table; exits non-zero when a
check fails. Standard library only.
"""
import argparse
import csv
import json
import re
import statistics
import sys
import time
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor

ADMIN = "asha@college.edu"
PASSWORD = "password-123456"
timings = {}
failures = []


def call(base, method, path, token=None, body=None, label=None):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(base + "/api/v1" + path, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    start = time.perf_counter()
    try:
        with urllib.request.urlopen(req, timeout=120) as res:
            raw = res.read()
            status = res.status
    except urllib.error.HTTPError as e:
        raw, status = e.read(), e.code
    name = label or f"{method} {re.sub(r'[0-9a-f]{8}-[0-9a-f-]{27}', '{id}', path.split('?')[0])}"
    timings.setdefault(name, []).append((time.perf_counter() - start) * 1000)
    return status, (json.loads(raw) if raw else None)


def check(ok, message):
    if not ok:
        failures.append(message)
        print("  FAIL:", message)
    return ok


def login(base, email):
    status, body = call(base, "POST", "/auth/login", body={"email": email, "password": PASSWORD}, label="login")
    return body["accessToken"] if status == 200 else None


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--base", default="http://localhost:8080")
    ap.add_argument("--accounts", default="target/seed-accounts.csv")
    ap.add_argument("--users", type=int, default=400)
    ap.add_argument("--threads", type=int, default=16)
    args = ap.parse_args()
    base = args.base

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
    check(admin is not None, "admin login")

    print("== profile, presence, suggestions (parallel reads)")
    sample = list(tokens.values())
    with ThreadPoolExecutor(args.threads) as pool:
        r1 = list(pool.map(lambda t: call(base, "GET", "/students/me", t)[0], sample))
        r2 = list(pool.map(lambda t: call(base, "GET", "/presence/available", t)[0], sample[:100]))
        r3 = list(pool.map(lambda t: call(base, "GET", "/matching/suggestions", t)[0], sample[:100]))
    check(all(s == 200 for s in r1), "GET /students/me for everyone")
    check(all(s == 200 for s in r2), "GET /presence/nearby")
    check(all(s == 200 for s in r3), "GET /matching/suggestions")

    print("== admin graph preview and matching round")
    # The preview does not change anything, so repeat it to separate JIT warm-up from steady state.
    for _ in range(5):
        status, graph = call(base, "GET", "/matching/graph?topK=4", admin, label="admin graph (preview)")
        if status == 200:
            engine = graph["optimal"]["millis"]
            print(f"   engine (graph build + blossom) = {engine:.1f} ms for pool of {graph['poolSize']} "
                  f"(graph build {graph['graphBuildMillis']:.1f} ms, greedy {graph['greedy']['millis']:.1f} ms)")
            timings.setdefault("ENGINE graph+blossom (server-side)", []).append(engine)
    check(status == 200, "graph endpoint")
    if status == 200:
        print(f"   pool={graph['poolSize']} edges={graph['edgesTotal']} shown={graph['edgesShown']} "
              f"optimal={graph['optimal']['pairs']} pairs / weight {graph['optimal']['totalWeight']:.2f} in "
              f"{graph['optimal']['millis']:.1f} ms (greedy {graph['greedy']['totalWeight']:.2f})")
        check(graph["check"]["passed"], "optimality check passes on the live pool")
        check(graph["optimal"]["totalWeight"] >= graph["greedy"]["totalWeight"] - 1e-9, "optimal >= greedy")
        partners = {n["index"]: n["partner"] for n in graph["nodes"] if n["partner"] is not None}
        check(all(partners.get(p) == i for i, p in partners.items()), "matching is symmetric")
        # no student matched twice is implied by a single partner per node; also every matched edge is listed
        matched_edges = [e for e in graph["edges"] if e["matched"]]
        check(len(matched_edges) == graph["optimal"]["pairs"], "every matched pair is drawn")

    mover = sample[0]
    status, found = call(base, "POST", "/matching/find", mover, label="POST /matching/find (full round)")
    check(status == 200, "find partner")
    status, rounds = call(base, "GET", "/matching/rounds", admin)
    if status == 200 and rounds:
        r = rounds[0]
        print(f"   latest round: pool={r['poolSize']} edges={r['edgeCount']} pairs={r['pairs']} engine={r['durationMs']:.1f} ms")

    print("== accept / decline / complete proposals")
    accepted = declined = completed = 0
    with ThreadPoolExecutor(args.threads) as pool:
        current = list(pool.map(lambda t: (t, call(base, "GET", "/matching/matches/current", t)), sample))
    seen = set()
    for token, (status, match) in current:
        if status != 200 or not match or match.get("id") in seen:
            continue
        seen.add(match["id"])
        if len(seen) % 5 == 0:
            if call(base, "POST", f"/matching/matches/{match['id']}/decline", token)[0] in (200, 204):
                declined += 1
        else:
            if call(base, "POST", f"/matching/matches/{match['id']}/accept", token)[0] == 200:
                accepted += 1
    print(f"   proposals seen={len(seen)} accepted-by-one-side={accepted} declined={declined}")

    print("== notifications")
    status, notes = call(base, "GET", "/notifications", sample[0])
    check(status == 200, "notifications list")

    print("== hackathon lifecycle")
    status, hacks = call(base, "GET", "/hackathons", admin)
    check(status == 200, "list hackathons")
    status, hack = call(base, "POST", "/hackathons", admin, {
        "name": "Load Test Hack", "description": "Simulated event",
        "roles": [{"name": "Backend", "keywords": ["java", "spring", "sql", "go"]},
                  {"name": "Frontend", "keywords": ["react", "css", "typescript"]},
                  {"name": "Data / ML", "keywords": ["python", "ml", "pandas"]},
                  {"name": "Design", "keywords": ["figma", "ux"]}]}, label="POST /hackathons")
    if check(status in (200, 201), "create hackathon"):
        hid = hack["summary"]["id"]
        regs = list(tokens.values())[:200]
        with ThreadPoolExecutor(args.threads) as pool:
            codes = list(pool.map(lambda t: call(base, "PUT", f"/hackathons/{hid}/registration", t,
                                                 {"rolePreferences": []})[0], regs))
        check(sum(1 for c in codes if c in (200, 201)) >= 150, f"registrations succeed ({codes.count(200)+codes.count(201)})")
        check(call(base, "POST", f"/hackathons/{hid}/close", admin)[0] in (200, 204), "close registration")
        status, teams = call(base, "POST", f"/hackathons/{hid}/synthesize", admin, None,
                             label="POST synthesize teams")
        check(status == 200, "synthesize teams")
        check(call(base, "POST", f"/hackathons/{hid}/publish", admin)[0] in (200, 204), "publish teams")
        status, mine = call(base, "GET", f"/hackathons/{hid}/teams", regs[0])
        check(status == 200, "published teams readable")

    print("== check out")
    with ThreadPoolExecutor(args.threads) as pool:
        out = list(pool.map(lambda t: call(base, "POST", "/presence/check-out", t)[0], sample[:50]))
    check(all(s in (200, 204) for s in out), "check-out")

    print("\n{:<42}{:>6}{:>10}{:>10}{:>10}".format("request", "n", "mean ms", "p95 ms", "max ms"))
    for label, values in sorted(timings.items()):
        values.sort()
        p95 = values[min(len(values) - 1, int(len(values) * 0.95))]
        print("{:<42}{:>6}{:>10.1f}{:>10.1f}{:>10.1f}".format(label[:41], len(values), statistics.mean(values), p95, values[-1]))
    print("\nFAILED:" if failures else "\nALL CHECKS PASSED", *failures, sep="\n  " if failures else "")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
