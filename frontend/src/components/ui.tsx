import type { ReactNode } from 'react';
import type { Breakdown, Proficiency } from '../api/types';

export function Alert({ kind = 'error', children }: { kind?: 'error' | 'success' | 'info'; children: ReactNode }) {
  if (!children) return null;
  return (
    <p className={`alert alert-${kind}`} role={kind === 'error' ? 'alert' : 'status'}>
      {children}
    </p>
  );
}

export function Spinner({ label = 'Loading…' }: { label?: string }) {
  return (
    <p className="muted" role="status">
      {label}
    </p>
  );
}

export function PageHeader({ title, subtitle, actions }: { title: string; subtitle?: string; actions?: ReactNode }) {
  return (
    <div className="page-header">
      <div>
        <h1>{title}</h1>
        {subtitle && <p className="muted">{subtitle}</p>}
      </div>
      {actions && <div className="page-actions">{actions}</div>}
    </div>
  );
}

export function EmptyState({ children }: { children: ReactNode }) {
  return <p className="empty">{children}</p>;
}

const LEVEL_LABEL: Record<Proficiency, string> = {
  BEGINNER: 'Beginner',
  INTERMEDIATE: 'Intermediate',
  ADVANCED: 'Advanced',
};

export function levelLabel(level: Proficiency): string {
  return LEVEL_LABEL[level];
}

export function LevelPill({ name, level }: { name: string; level: Proficiency }) {
  return (
    <span className={`pill level-${level.toLowerCase()}`} title={levelLabel(level)}>
      {name}
      <small>{levelLabel(level)}</small>
    </span>
  );
}

export function Pill({ children }: { children: ReactNode }) {
  return <span className="pill">{children}</span>;
}

export function Field({ label, children, hint }: { label: string; children: ReactNode; hint?: string }) {
  return (
    <label className="field">
      <span>{label}</span>
      {children}
      {hint && <small className="muted">{hint}</small>}
    </label>
  );
}

/** Circular compatibility score, 0–100. */
export function ScoreBadge({ score }: { score: number }) {
  const tone = score >= 75 ? 'high' : score >= 55 ? 'mid' : 'low';
  return (
    <span className={`score score-${tone}`} aria-label={`${score}% compatible`}>
      {score}
      <small>%</small>
    </span>
  );
}

const BREAKDOWN_LABELS: [keyof Breakdown, string][] = [
  ['knowledge', 'Knowledge fit'],
  ['reciprocity', 'Can teach each other'],
  ['breadth', 'Common ground'],
  ['proximity', 'Nearby'],
];

export function ScoreBreakdown({ breakdown }: { breakdown: Breakdown }) {
  return (
    <ul className="breakdown" aria-label="Why you were matched">
      {BREAKDOWN_LABELS.map(([key, label]) => (
        <li key={key}>
          <span>{label}</span>
          <meter min={0} max={1} value={breakdown[key]} aria-label={label} />
        </li>
      ))}
    </ul>
  );
}
