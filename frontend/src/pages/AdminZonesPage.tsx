import { useState, type FormEvent } from 'react';
import { Navigate } from 'react-router-dom';
import { admin } from '../api/endpoints';
import type { AdminZone, ZoneInput } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { Alert, EmptyState, Field, PageHeader, Spinner } from '../components/ui';
import { useAction, useLoad } from '../hooks';

interface Draft {
  name: string;
  x: string;
  y: string;
}

const EMPTY: Draft = { name: '', x: '', y: '' };

function toInput(draft: Draft, enabled?: boolean): ZoneInput {
  const hasX = draft.x.trim() !== '';
  const hasY = draft.y.trim() !== '';
  return {
    name: draft.name.trim(),
    x: hasX ? Number(draft.x) : null,
    y: hasY ? Number(draft.y) : null,
    enabled,
  };
}

/** Plots every placed zone so administrators can sanity-check the layout that proximity scoring relies on. */
function ZoneMap({ zones }: { zones: AdminZone[] }) {
  const placed = zones.filter((z) => z.x !== null && z.y !== null);
  if (placed.length === 0) {
    return <EmptyState>Give zones coordinates to see them on the map.</EmptyState>;
  }
  const xs = placed.map((z) => z.x as number);
  const ys = placed.map((z) => z.y as number);
  const pad = 40;
  const minX = Math.min(...xs) - pad;
  const maxX = Math.max(...xs) + pad;
  const minY = Math.min(...ys) - pad;
  const maxY = Math.max(...ys) + pad;
  const width = Math.max(maxX - minX, 1);
  const height = Math.max(maxY - minY, 1);
  return (
    <svg
      className="zone-map"
      viewBox={`${minX} ${-maxY} ${width} ${height}`}
      role="img"
      aria-label="Map of campus zones"
      preserveAspectRatio="xMidYMid meet"
    >
      {placed.map((z) => {
        const r = Math.max(width, height) / 40;
        return (
          <g key={z.id} opacity={z.enabled ? 1 : 0.35}>
            <circle cx={z.x as number} cy={-(z.y as number)} r={r} fill="var(--primary)" />
            <text x={(z.x as number) + r * 1.6} y={-(z.y as number) + r / 2} fontSize={r * 1.7}>
              {z.name}
            </text>
          </g>
        );
      })}
    </svg>
  );
}

function ZoneRow({ zone, onSaved }: { zone: AdminZone; onSaved: () => void }) {
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState<Draft>({ name: zone.name, x: zone.x?.toString() ?? '', y: zone.y?.toString() ?? '' });
  const { pending, error, run } = useAction();

  const save = (enabled: boolean, next: Draft = draft) =>
    run(async () => {
      await admin.updateZone(zone.id, toInput(next, enabled));
      setEditing(false);
      onSaved();
    });

  if (!editing) {
    return (
      <tr className={zone.enabled ? '' : 'disabled-row'}>
        <td>{zone.name}</td>
        <td>{zone.x !== null && zone.y !== null ? `${zone.x}, ${zone.y}` : <span className="muted">not placed</span>}</td>
        <td>{zone.checkedIn}</td>
        <td>{zone.enabled ? 'Active' : 'Disabled'}</td>
        <td className="actions">
          <button type="button" className="link" onClick={() => setEditing(true)}>
            Edit
          </button>
          <button type="button" className="link" disabled={pending} onClick={() => void save(!zone.enabled)}>
            {zone.enabled ? 'Disable' : 'Enable'}
          </button>
          {error && <span className="error-inline">{error}</span>}
        </td>
      </tr>
    );
  }

  return (
    <tr>
      <td>
        <input aria-label={`Name of ${zone.name}`} value={draft.name} onChange={(e) => setDraft({ ...draft, name: e.target.value })} />
      </td>
      <td>
        <div className="row tight">
          <input aria-label={`X of ${zone.name}`} type="number" step="any" value={draft.x} onChange={(e) => setDraft({ ...draft, x: e.target.value })} />
          <input aria-label={`Y of ${zone.name}`} type="number" step="any" value={draft.y} onChange={(e) => setDraft({ ...draft, y: e.target.value })} />
        </div>
      </td>
      <td>{zone.checkedIn}</td>
      <td>{zone.enabled ? 'Active' : 'Disabled'}</td>
      <td className="actions">
        <button type="button" className="button" disabled={pending} onClick={() => void save(zone.enabled)}>
          Save
        </button>
        <button type="button" className="button secondary" onClick={() => setEditing(false)}>
          Cancel
        </button>
        {error && <span className="error-inline">{error}</span>}
      </td>
    </tr>
  );
}

export function AdminZonesPage() {
  const { profile } = useAuth();
  const zones = useLoad(() => admin.zones(), []);
  const [draft, setDraft] = useState<Draft>(EMPTY);
  const { pending, error, run } = useAction();

  if (profile && !profile.admin) {
    return <Navigate to="/" replace />;
  }

  async function add(event: FormEvent) {
    event.preventDefault();
    const created = await run(() => admin.createZone(toInput(draft)));
    if (created) {
      setDraft(EMPTY);
      zones.reload();
    }
  }

  return (
    <>
      <PageHeader
        title="Campus zones"
        subtitle="Where students can check in. Coordinates are metres on a campus plane (any origin); closer zones score higher in matching."
      />
      <div className="grid-2">
        <section className="card">
          <h2>Zones</h2>
          <Alert>{zones.error}</Alert>
          {zones.loading && !zones.data ? (
            <Spinner />
          ) : (
            <table>
              <thead>
                <tr>
                  <th>Name</th>
                  <th>Position (x, y)</th>
                  <th>Checked in</th>
                  <th>Status</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {zones.data?.map((z) => (
                  <ZoneRow key={`${z.id}-${z.name}-${z.enabled}-${z.x}-${z.y}`} zone={z} onSaved={zones.reload} />
                ))}
              </tbody>
            </table>
          )}
          <p className="muted small">
            Zones are disabled rather than deleted, so past check-ins and matches stay intact. Disabled zones disappear from
            the student check-in list.
          </p>
        </section>
        <div className="stack">
          <section className="card">
            <h2>Add a zone</h2>
            <form onSubmit={add}>
              <Field label="Name">
                <input value={draft.name} onChange={(e) => setDraft({ ...draft, name: e.target.value })} maxLength={100} required />
              </Field>
              <div className="row tight">
                <Field label="X (m)">
                  <input type="number" step="any" value={draft.x} onChange={(e) => setDraft({ ...draft, x: e.target.value })} />
                </Field>
                <Field label="Y (m)">
                  <input type="number" step="any" value={draft.y} onChange={(e) => setDraft({ ...draft, y: e.target.value })} />
                </Field>
              </div>
              <Alert>{error}</Alert>
              <button className="button" disabled={pending}>
                Add zone
              </button>
            </form>
          </section>
          <section className="card">
            <h2>Map</h2>
            {zones.data && <ZoneMap zones={zones.data} />}
          </section>
        </div>
      </div>
    </>
  );
}
