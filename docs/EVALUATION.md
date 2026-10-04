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

## Mode 2 — hackathon team synthesis (Hungarian algorithm vs. random teams)

Five roles (backend, frontend, data/ML, design, product); participants rank up to three roles. Fit is the mean role fit (higher is better), preference is the share of participants who got one of their top-3 roles, and σ is the standard deviation of team strength (lower is more balanced).

| Participants | Teams | Synthesis (ms) | Fit: Hungarian / random | Preferences met: Hungarian / random | σ strength: Hungarian / random |
|---:|---:|---:|---:|---:|---:|
| 12 | 3 | 0.7 | 0.568 / 0.286 | 73.9% / 41.9% | 0.0191 / 0.0556 |
| 24 | 5 | 0.6 | 0.604 / 0.271 | 79.3% / 41.4% | 0.0169 / 0.0655 |
| 48 | 10 | 1.4 | 0.613 / 0.272 | 79.4% / 39.0% | 0.0184 / 0.0686 |
| 96 | 20 | 2.1 | 0.615 / 0.281 | 79.0% / 41.4% | 0.0181 / 0.0702 |
| 192 | 39 | 8.3 | 0.618 / 0.281 | 81.6% / 41.1% | 0.0189 / 0.0728 |
| 384 | 77 | 48.2 | 0.614 / 0.275 | 80.9% / 39.9% | 0.0205 / 0.0736 |

## Hungarian algorithm scaling (dense n × n cost matrix)

| n | Time (ms) |
|---:|---:|
| 50 | 0.1 |
| 100 | 0.3 |
| 200 | 2.3 |
| 400 | 9.6 |
| 800 | 43.9 |

