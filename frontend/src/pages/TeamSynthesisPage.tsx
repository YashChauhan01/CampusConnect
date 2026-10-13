import { useMemo, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { hackathons } from '../api/endpoints';
import type { AnalysisMember, SynthesisMethod, TeamAnalysis } from '../api/types';
import { Alert, PageHeader, ScoreBadge, Spinner } from '../components/ui';
import { useLoad } from '../hooks';

/**
 * Categorical role colours in fixed order, defined per theme in styles.css (--role-1..8, validated for colour-vision
 * deficiencies on both surfaces); chips always carry the name too.
 */
const roleColor = (index: number) => `var(--role-${(index % 8) + 1})`;
/** Ordinal ramp for 1st, 2nd, 3rd choice, then neutral grey for an unranked role. */
const RANK_COLORS = ['var(--rank-1)', 'var(--rank-2)', 'var(--rank-3)', 'var(--rank-none)'];
const RANK_TEXT = ['var(--rank-1-text)', 'var(--rank-2-text)', 'var(--rank-3-text)', 'var(--text)'];
const RANK_LABELS = ['1st choice', '2nd choice', '3rd choice', 'Not ranked'];
const LEVELS = ['', 'Beginner', 'Intermediate', 'Advanced'];

const pct = (v: number) => `${Math.round(v * 100)}%`;

function Stat({ label, value, hint }: { label: string; value: string; hint?: string }) {
  return (
    <div className="stat">
      <dt>{label}</dt>
      <dd>{value}</dd>
      {hint && <small className="muted">{hint}</small>}
    </div>
  );
}

function RoleChip({ index, name }: { index: number; name: string }) {
  return (
    <span className="role-chip">
      <span className="swatch" style={{ background: roleColor(index) }} aria-hidden="true" />
      {name}
    </span>
  );
}

const CHART_W = 960;
const CHART_H = 300;
const PAD = { left: 48, right: 12, top: 12, bottom: 38 };

/** Every team's average strength, weakest to strongest: a flat line means balanced teams. */
function BalanceChart({ hungarian, random }: { hungarian: SynthesisMethod; random: SynthesisMethod }) {
  const [hover, setHover] = useState<number | null>(null);
  const n = hungarian.teamStrengths.length;
  const all = [...hungarian.teamStrengths, ...random.teamStrengths];
  const lo = Math.max(0, Math.floor((Math.min(...all) - 0.05) * 10) / 10);
  const hi = Math.min(1, Math.ceil((Math.max(...all) + 0.05) * 10) / 10);
  const x = (i: number) => PAD.left + (n === 1 ? 0.5 : i / (n - 1)) * (CHART_W - PAD.left - PAD.right);
  const y = (v: number) => PAD.top + (1 - (v - lo) / (hi - lo || 1)) * (CHART_H - PAD.top - PAD.bottom);
  const path = (values: number[]) => values.map((v, i) => `${i ? 'L' : 'M'}${x(i).toFixed(1)},${y(v).toFixed(1)}`).join('');
  const ticks = Array.from({ length: Math.round((hi - lo) / 0.1) + 1 }, (_, i) => lo + i * 0.1);

  return (
    <figure className="chart">
      <ul className="legend" aria-label="Legend">
        <li>
          <span className="line-sample main" /> Hungarian synthesis
        </li>
        <li>
          <span className="line-sample dashed" /> Random teams (mean of runs)
        </li>
      </ul>
      <div className="chart-box">
        <svg
          viewBox={`0 0 ${CHART_W} ${CHART_H}`}
          role="img"
          aria-label="Average strength of every team, weakest to strongest, for Hungarian and random teams"
          onMouseLeave={() => setHover(null)}
          onMouseMove={(e) => {
            const rect = e.currentTarget.getBoundingClientRect();
            const sx = ((e.clientX - rect.left) / rect.width) * CHART_W;
            const i = Math.round(((sx - PAD.left) / (CHART_W - PAD.left - PAD.right)) * (n - 1));
            setHover(i >= 0 && i < n ? i : null);
          }}
        >
          {ticks.map((t) => (
            <g key={t}>
              <line x1={PAD.left} x2={CHART_W - PAD.right} y1={y(t)} y2={y(t)} className="grid-line" />
              <text x={PAD.left - 8} y={y(t) + 5} textAnchor="end" className="axis-label">
                {Math.round(t * 100)}
              </text>
            </g>
          ))}
          <text x={(CHART_W + PAD.left) / 2} y={CHART_H - 6} textAnchor="middle" className="axis-label">
            Teams, weakest → strongest ({n})
          </text>
          <path d={path(random.teamStrengths)} className="series baseline" />
          <path d={path(hungarian.teamStrengths)} className="series main" />
          {hover !== null && (
            <g>
              <line x1={x(hover)} x2={x(hover)} y1={PAD.top} y2={CHART_H - PAD.bottom} className="crosshair" />
              <circle cx={x(hover)} cy={y(random.teamStrengths[hover])} r={4.5} className="dot-baseline" strokeWidth={2} />
              <circle cx={x(hover)} cy={y(hungarian.teamStrengths[hover])} r={4.5} className="dot-main" strokeWidth={2} />
            </g>
          )}
        </svg>
        {hover !== null && (
          <div className="chart-tip" style={{ left: `${(x(hover) / CHART_W) * 100}%` }}>
            <strong>Team rank {hover + 1} of {n}</strong>
            <span>Hungarian: {Math.round(hungarian.teamStrengths[hover] * 100)}</span>
            <span>Random: {Math.round(random.teamStrengths[hover] * 100)}</span>
          </div>
        )}
      </div>
      <figcaption className="muted">
        Spread between strongest and weakest team: Hungarian {Math.round(hungarian.strengthSpread * 100)} points, random{' '}
        {Math.round(random.strengthSpread * 100)} points.
      </figcaption>
    </figure>
  );
}

/** Share of participants who got their 1st / 2nd / 3rd choice, as one 100% bar per method. */
function PreferenceBars({ hungarian, random }: { hungarian: SynthesisMethod; random: SynthesisMethod }) {
  const [tip, setTip] = useState<string | null>(null);
  const rows: [string, SynthesisMethod][] = [
    ['Hungarian', hungarian],
    ['Random', random],
  ];
  return (
    <figure className="chart">
      <ul className="legend" aria-label="Legend">
        {RANK_LABELS.map((l, i) => (
          <li key={l}>
            <span className="swatch square" style={{ background: RANK_COLORS[i] }} /> {l}
          </li>
        ))}
      </ul>
      {rows.map(([label, m]) => (
        <div key={label} className="pref-row">
          <span className="pref-label">{label}</span>
          <div className="pref-bar" onMouseLeave={() => setTip(null)}>
            {m.preferenceRanks.map((share, i) =>
              share > 0 ? (
                <div
                  key={i}
                  className="pref-seg"
                  style={{ width: `${share * 100}%`, background: RANK_COLORS[i], color: RANK_TEXT[i] }}
                  onMouseEnter={() => setTip(`${label}: ${RANK_LABELS[i]} ${pct(share)}`)}
                >
                  {share >= 0.08 && <span>{pct(share)}</span>}
                </div>
              ) : null,
            )}
          </div>
        </div>
      ))}
      <p className="muted chart-note" aria-live="polite">
        {tip ?? 'Hover a segment for its share.'}
      </p>
    </figure>
  );
}

/** First-choice role (rows) against the role actually given (columns); the diagonal is "got what they asked for". */
function FlowTable({ analysis }: { analysis: TeamAnalysis }) {
  const max = Math.max(1, ...analysis.flow.flat());
  const rowNames = [...analysis.roles.map((r) => r.name), 'No preference'];
  return (
    <div className="table-scroll">
      <table className="flow">
        <caption className="sr-only">First-choice role against allocated role</caption>
        <thead>
          <tr>
            <th scope="col">First choice ↓ · given →</th>
            {analysis.roles.map((r, j) => (
              <th key={r.id} scope="col">
                <RoleChip index={j} name={r.name} />
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {analysis.flow.map((row, i) => (
            <tr key={rowNames[i]}>
              <th scope="row">{i < analysis.roles.length ? <RoleChip index={i} name={rowNames[i]} /> : rowNames[i]}</th>
              {row.map((count, j) => {
                const t = count / max;
                return (
                  <td
                    key={j}
                    className={i === j ? 'diag' : ''}
                    style={{ background: count ? `rgb(var(--heat) / ${(0.1 + t * 0.75).toFixed(2)})` : undefined, color: t > 0.55 ? '#fff' : undefined }}
                    title={`${count} participants`}
                  >
                    {count || ''}
                  </td>
                );
              })}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function MemberInspector({ analysis, member }: { analysis: TeamAnalysis; member: AnalysisMember }) {
  const roleIndex = analysis.roles.findIndex((r) => r.id === member.roleId);
  const team = analysis.teams.find((t) => t.members.some((m) => m.studentId === member.studentId));
  return (
    <>
      <h2>{member.name}</h2>
      <p className="muted">
        Team {team?.number} · <RoleChip index={roleIndex} name={analysis.roles[roleIndex]?.name ?? '?'} /> ·{' '}
        {member.preferenceRank ? RANK_LABELS[member.preferenceRank - 1] : 'role not ranked'}
      </p>
      <h3>Preferences</h3>
      <p>
        {member.preferences.length
          ? member.preferences.map((id) => analysis.roles.find((r) => r.id === id)?.name).join(' › ')
          : 'No preference given'}
      </p>
      <h3>Skills</h3>
      <ul className="skill-list">
        {member.skills.map((s) => (
          <li key={s.name}>
            {s.name} <small className="muted">{LEVELS[s.level] ?? s.level}</small>
          </li>
        ))}
      </ul>
      <h3>Fit for every role</h3>
      <ul className="fit-list">
        {analysis.roles.map((r, j) => (
          <li key={r.id} className={j === roleIndex ? 'on' : ''}>
            <span className="fit-name">{r.name}</span>
            <span className="fit-track">
              <span style={{ width: `${member.fitByRole[j]}%`, background: roleColor(j) }} />
            </span>
            <span className="fit-value">{member.fitByRole[j]}</span>
          </li>
        ))}
      </ul>
      <p className="muted">
        Fit = 60% skill match with the role's keywords + 40% preference. Strength {member.strength} combines overall skill
        with the fit of the role given.
      </p>
    </>
  );
}

type Sort = 'number' | 'strongest' | 'weakest';

export function TeamSynthesisPage() {
  const { id = '' } = useParams();
  const analysis = useLoad(() => hackathons.analysis(id), [id]);
  const [query, setQuery] = useState('');
  const [role, setRole] = useState<number | ''>('');
  const [sort, setSort] = useState<Sort>('number');
  const [selected, setSelected] = useState<AnalysisMember | null>(null);
  const a = analysis.data;

  const teams = useMemo(() => {
    if (!a) return [];
    const q = query.trim().toLowerCase();
    const list = a.teams
      .map((t) => ({
        ...t,
        members: [...t.members].sort(
          (x, y) => a.roles.findIndex((r) => r.id === x.roleId) - a.roles.findIndex((r) => r.id === y.roleId),
        ),
      }))
      .filter((t) => !q || t.members.some((m) => m.name.toLowerCase().includes(q)));
    if (sort === 'strongest') list.sort((x, y) => y.averageStrength - x.averageStrength);
    if (sort === 'weakest') list.sort((x, y) => x.averageStrength - y.averageStrength);
    return list;
  }, [a, query, sort]);

  const sizes = a ? [...new Set(a.teams.map((t) => t.members.length))].sort().join(' or ') : '';

  return (
    <>
      <PageHeader
        eyebrow="Team synthesis"
        title={a ? `Team synthesis · ${a.name}` : 'Team synthesis'}
        subtitle="The teams the Hungarian algorithm forms from the current registrations, compared with random teams of the same sizes. Nothing is saved here."
        actions={
          <>
            <Link className="button secondary" to={`/hackathons/${id}`}>
              Back to hackathon
            </Link>
            <button className="button secondary" onClick={analysis.reload} disabled={analysis.loading}>
              Re-run
            </button>
          </>
        }
      />
      <Alert>{analysis.error}</Alert>
      {analysis.loading && !a ? (
        <Spinner label="Forming teams…" />
      ) : !a ? null : (
        <>
          <section className="card">
            <dl className="stats">
              <Stat label="Participants" value={String(a.participants)} hint={`${a.roles.length} roles`} />
              <Stat label="Teams" value={String(a.teamCount)} hint={`${sizes} members each`} />
              <Stat label="Synthesis time" value={`${a.hungarian.millis.toFixed(0)} ms`} hint="two Hungarian stages" />
              <Stat
                label="Average role fit"
                value={pct(a.hungarian.averageFit)}
                hint={`random teams ${pct(a.random.averageFit)}`}
              />
              <Stat
                label="Got a top-3 role"
                value={pct(a.hungarian.preferenceSatisfaction)}
                hint={`random teams ${pct(a.random.preferenceSatisfaction)}`}
              />
              <Stat
                label="Team strength σ"
                value={a.hungarian.strengthStdDev.toFixed(3)}
                hint={`random ${a.random.strengthStdDev.toFixed(3)} · lower is fairer`}
              />
            </dl>
            <p className={a.check.passed ? 'check-pass' : 'check-fail'} role="status">
              {a.check.passed ? '✓ ' : '✗ '}
              Optimality proof: {a.check.movesExamined.toLocaleString()} role moves priced, {a.check.improvingCycles}{' '}
              improving cycles found.{' '}
              {a.check.passed
                ? 'No group of participants can trade roles to raise the total fit, so this role allocation is optimal.'
                : 'Some participants could trade roles for a higher total fit.'}{' '}
              <span className="muted">Random baseline: mean of {a.randomRuns} shuffles.</span>
            </p>
          </section>

          <div className="two-col">
            <section className="card">
              <h2>Are the teams balanced?</h2>
              <BalanceChart hungarian={a.hungarian} random={a.random} />
            </section>
            <section className="card">
              <h2>Did people get the role they asked for?</h2>
              <PreferenceBars hungarian={a.hungarian} random={a.random} />
              <h3>First choice vs. role given</h3>
              <FlowTable analysis={a} />
            </section>
          </div>

          <div className="graph-layout">
            <section className="card graph-card">
              <div className="row graph-controls">
                <input
                  type="search"
                  placeholder="Find a participant"
                  aria-label="Find a participant"
                  value={query}
                  onChange={(e) => setQuery(e.target.value)}
                />
                <select aria-label="Highlight role" value={role} onChange={(e) => setRole(e.target.value ? Number(e.target.value) : '')}>
                  <option value="">Highlight a role</option>
                  {a.roles.map((r) => (
                    <option key={r.id} value={r.id}>
                      {r.name} ({r.holders})
                    </option>
                  ))}
                </select>
                <select aria-label="Sort teams" value={sort} onChange={(e) => setSort(e.target.value as Sort)}>
                  <option value="number">Team number</option>
                  <option value="strongest">Strongest first</option>
                  <option value="weakest">Weakest first</option>
                </select>
                <span className="muted">
                  {teams.length} of {a.teamCount} teams
                </span>
              </div>
              <div className="team-grid">
                {teams.map((t) => (
                  <article key={t.number} className="team-card">
                    <header>
                      <strong>Team {t.number}</strong>
                      <span className="muted">strength {t.averageStrength}</span>
                    </header>
                    <ul>
                      {t.members.map((m) => {
                        const j = a.roles.findIndex((r) => r.id === m.roleId);
                        const hit = query && m.name.toLowerCase().includes(query.trim().toLowerCase());
                        return (
                          <li key={m.studentId}>
                            <button
                              className={`member-row ${selected?.studentId === m.studentId ? 'on' : ''} ${hit ? 'hit' : ''} ${role !== '' && m.roleId !== role ? 'dim' : ''}`}
                              onClick={() => setSelected(m)}
                            >
                              <span
                                className="role-bar"
                                style={{ background: roleColor(j) }}
                                aria-hidden="true"
                              />
                              <span className="member-text">
                                <span>{m.name}</span>
                                <small className="muted">
                                  {a.roles[j]?.name}
                                  {m.preferenceRank ? ` · choice ${m.preferenceRank}` : ''}
                                </small>
                              </span>
                              <span className="fit-num" title="Role fit">
                                {m.fit}
                              </span>
                            </button>
                          </li>
                        );
                      })}
                    </ul>
                  </article>
                ))}
              </div>
            </section>
            <aside className="card detail-card">
              {selected ? (
                <MemberInspector analysis={a} member={selected} />
              ) : (
                <>
                  <h2>Inspect a participant</h2>
                  <p className="muted">
                    Click anyone in a team to see their skills, their preferences and how well they fit every role. Each
                    team gets one member per role; the number on the right is the fit for the role they were given.
                  </p>
                  <h3>Roles</h3>
                  <ul className="list compact">
                    {a.roles.map((r, j) => (
                      <li key={r.id} className="role-summary">
                        <RoleChip index={j} name={r.name} />
                        <small className="muted">
                          {r.holders} placed · {r.firstChoices} first choices · {r.keywords.join(', ')}
                        </small>
                      </li>
                    ))}
                  </ul>
                  <h3>Best role fits</h3>
                  <ul className="list compact">
                    {a.teams
                      .flatMap((t) => t.members)
                      .sort((x, y) => y.fit - x.fit)
                      .slice(0, 5)
                      .map((m) => (
                        <li key={m.studentId}>
                          <button className="link-row" onClick={() => setSelected(m)}>
                            <ScoreBadge score={m.fit} />
                            <span>
                              <strong>{m.name}</strong>
                              <small className="muted"> {a.roles.find((r) => r.id === m.roleId)?.name}</small>
                            </span>
                          </button>
                        </li>
                      ))}
                  </ul>
                </>
              )}
            </aside>
          </div>
        </>
      )}
    </>
  );
}
