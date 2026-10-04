import { useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { hackathons } from '../api/endpoints';
import type { HackathonStatus, HackathonSummary } from '../api/types';
import { Alert, EmptyState, Field, PageHeader, Pill, Spinner } from '../components/ui';
import { useAction, useLoad } from '../hooks';
import { useRealtime } from '../realtime/RealtimeContext';

export const STATUS_LABEL: Record<HackathonStatus, string> = {
  OPEN: 'Registration open',
  CLOSED: 'Registration closed',
  PUBLISHED: 'Teams announced',
};

function HackathonCard({ h }: { h: HackathonSummary }) {
  return (
    <li className="card hackathon-card">
      <div className="row spread">
        <h2>
          <Link to={`/hackathons/${h.id}`}>{h.name}</Link>
        </h2>
        <span className={`status status-${h.status.toLowerCase()}`}>{STATUS_LABEL[h.status]}</span>
      </div>
      {h.description && <p className="muted">{h.description}</p>}
      <div className="chips-inline">
        {h.roles.map((r) => (
          <Pill key={r.id}>{r.name}</Pill>
        ))}
      </div>
      <p className="muted small">
        {h.participants} registered · organised by {h.organizedByMe ? 'you' : h.organizer}
        {h.registered && ' · you are in'}
      </p>
    </li>
  );
}

export function HackathonsPage() {
  const { version } = useRealtime();
  const list = useLoad(() => hackathons.list(), [version('hackathons')]);

  return (
    <>
      <PageHeader
        title="Hackathons"
        subtitle="Register with your preferred roles and we will form balanced teams for you."
        actions={
          <Link className="button" to="/hackathons/new">
            Organise a hackathon
          </Link>
        }
      />
      <Alert>{list.error}</Alert>
      {list.loading && !list.data ? (
        <Spinner />
      ) : list.data?.length ? (
        <ul className="cards">
          {list.data.map((h) => (
            <HackathonCard key={h.id} h={h} />
          ))}
        </ul>
      ) : (
        <section className="card">
          <EmptyState>No hackathons yet. Be the first to organise one.</EmptyState>
        </section>
      )}
    </>
  );
}

interface RoleDraft {
  name: string;
  keywords: string;
}

const DEFAULT_ROLES: RoleDraft[] = [
  { name: 'Backend', keywords: 'java, spring, sql, node' },
  { name: 'Frontend', keywords: 'react, css, typescript' },
  { name: 'Data / ML', keywords: 'python, ml' },
];

export function NewHackathonPage() {
  const navigate = useNavigate();
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [roles, setRoles] = useState<RoleDraft[]>(DEFAULT_ROLES);
  const { pending, error, run } = useAction();

  const update = (index: number, patch: Partial<RoleDraft>) =>
    setRoles((current) => current.map((r, i) => (i === index ? { ...r, ...patch } : r)));

  async function submit(event: FormEvent) {
    event.preventDefault();
    const created = await run(() =>
      hackathons.create({
        name: name.trim(),
        description: description.trim() || undefined,
        roles: roles.map((r) => ({
          name: r.name.trim(),
          keywords: r.keywords.split(',').map((k) => k.trim()).filter(Boolean),
        })),
      }),
    );
    if (created) navigate(`/hackathons/${created.summary.id}`);
  }

  return (
    <>
      <PageHeader title="Organise a hackathon" subtitle="Define the roles every team needs. Each team gets one member per role." />
      <form className="card" onSubmit={submit}>
        <Field label="Name">
          <input value={name} onChange={(e) => setName(e.target.value)} minLength={3} maxLength={120} required />
        </Field>
        <Field label="Description (optional)">
          <textarea value={description} onChange={(e) => setDescription(e.target.value)} maxLength={1000} rows={3} />
        </Field>
        <h2>Roles</h2>
        <p className="muted">
          Keywords are matched against participants’ skills (“react” matches “React Native”). Put the most important
          roles first: smaller teams drop the last ones.
        </p>
        {roles.map((role, index) => (
          <div className="role-row" key={index}>
            <input
              aria-label={`Role ${index + 1} name`}
              placeholder="Role name"
              value={role.name}
              onChange={(e) => update(index, { name: e.target.value })}
              maxLength={60}
              required
            />
            <input
              aria-label={`Role ${index + 1} keywords`}
              placeholder="Skill keywords, comma separated"
              value={role.keywords}
              onChange={(e) => update(index, { keywords: e.target.value })}
            />
            <button
              type="button"
              className="icon-button"
              aria-label={`Remove role ${index + 1}`}
              disabled={roles.length <= 2}
              onClick={() => setRoles((current) => current.filter((_, i) => i !== index))}
            >
              ×
            </button>
          </div>
        ))}
        <div className="row">
          <button
            type="button"
            className="button secondary"
            disabled={roles.length >= 8}
            onClick={() => setRoles((current) => [...current, { name: '', keywords: '' }])}
          >
            Add role
          </button>
        </div>
        <Alert>{error}</Alert>
        <button className="button" disabled={pending}>
          {pending ? 'Creating…' : 'Create hackathon'}
        </button>
      </form>
    </>
  );
}
