import { useEffect, useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { matching, presence } from '../api/endpoints';
import type { PresenceStatus, PresenceView } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { Alert, EmptyState, Field, LevelPill, PageHeader, Pill, ScoreBadge, Spinner } from '../components/ui';
import { useAction, useCountdown, useLoad } from '../hooks';
import { useRealtime } from '../realtime/RealtimeContext';

export function CampusPage() {
  const { profile } = useAuth();
  const { version } = useRealtime();
  const navigate = useNavigate();
  const presenceVersion = version('presence');
  const matchVersion = version('matches');

  const zones = useLoad(() => presence.zones(), []);
  const mine = useLoad(() => presence.mine(), [presenceVersion]);
  const [zoneFilter, setZoneFilter] = useState<number | undefined>(undefined);
  const available = useLoad(() => presence.available(zoneFilter), [zoneFilter, presenceVersion]);
  const suggestions = useLoad(() => matching.suggestions(), [presenceVersion, matchVersion, mine.data?.expiresAt]);

  const [zoneId, setZoneId] = useState<number | ''>('');
  const [status, setStatus] = useState<PresenceStatus>('AVAILABLE');
  const [requirements, setRequirements] = useState('');
  const [seeking, setSeeking] = useState<number[]>([]);
  const checkIn = useAction();
  const find = useAction();
  const checkOut = useAction();
  const remaining = useCountdown(mine.data?.expiresAt);

  // Pre-fill the form with the current check-in, or the first zone.
  const current = mine.data;
  const firstZone = zones.data?.[0]?.id;
  useEffect(() => {
    if (current) {
      setZoneId(current.zoneId);
      setStatus(current.status);
      setRequirements(current.requirements ?? '');
      const byName = new Map(profile?.subjects.map((s) => [s.name, s.id]));
      setSeeking(current.seeking.map((n) => byName.get(n)).filter((id): id is number => id !== undefined));
    } else if (firstZone !== undefined) {
      setZoneId((z) => (z === '' ? firstZone : z));
    }
  }, [current, firstZone, profile]);

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (zoneId === '') return;
    const result = await checkIn.run(() =>
      presence.checkIn({ zoneId, status, requirements: requirements.trim() || undefined, seekingSubjectIds: seeking }),
    );
    if (result) {
      mine.reload();
      available.reload();
    }
  }

  async function leave() {
    await checkOut.run(async () => {
      await presence.checkOut();
      mine.reload();
      available.reload();
    });
  }

  async function findPartner() {
    const match = await find.run(() => matching.find());
    navigate('/matches', { state: { fresh: Boolean(match) } });
  }

  const subjects = profile?.subjects ?? [];
  const checkedIn = Boolean(current) && remaining !== null;

  return (
    <>
      <PageHeader
        title="Campus"
        subtitle="Tell us where you are, see who is around, and get matched with someone to study with."
      />
      <div className="grid-2">
        <section className="card">
          <h2>{checkedIn ? 'You are on campus' : 'Check in'}</h2>
          {checkedIn && (
            <p className="muted">
              {current?.zone} · {current?.status === 'AVAILABLE' ? 'open to study partners' : 'busy'} · expires in{' '}
              <strong>{remaining}</strong>. Renew before it runs out.
            </p>
          )}
          {!checkedIn && (
            <p className="muted">
              Check-ins last 15 minutes and are self-reported. Renew while you stay, check out when you leave.
            </p>
          )}
          {zones.loading && !zones.data ? (
            <Spinner />
          ) : (
            <form onSubmit={submit}>
              <Field label="Where are you?">
                <select value={zoneId} onChange={(e) => setZoneId(Number(e.target.value))} required>
                  {zones.data?.map((z) => (
                    <option key={z.id} value={z.id}>
                      {z.name}
                    </option>
                  ))}
                </select>
              </Field>
              <Field label="Status">
                <select value={status} onChange={(e) => setStatus(e.target.value as PresenceStatus)}>
                  <option value="AVAILABLE">Available to study with someone</option>
                  <option value="BUSY">Busy — do not match me</option>
                </select>
              </Field>
              <fieldset>
                <legend>What do you want to work on?</legend>
                {subjects.length === 0 ? (
                  <p className="muted">Add subjects to your profile to be matched with classmates.</p>
                ) : (
                  <div className="checks">
                    {subjects.map((s) => (
                      <label key={s.id} className="check">
                        <input
                          type="checkbox"
                          checked={seeking.includes(s.id)}
                          onChange={(e) =>
                            setSeeking((cur) => (e.target.checked ? [...cur, s.id] : cur.filter((id) => id !== s.id)))
                          }
                        />
                        {s.name}
                      </label>
                    ))}
                  </div>
                )}
              </fieldset>
              <Field label="Anything specific? (optional)" hint="e.g. “Revising graph algorithms for tomorrow’s quiz”">
                <textarea value={requirements} onChange={(e) => setRequirements(e.target.value)} maxLength={300} rows={2} />
              </Field>
              <Alert>{checkIn.error}</Alert>
              <div className="row">
                <button className="button" disabled={checkIn.pending}>
                  {checkedIn ? 'Update / renew' : 'Check in'}
                </button>
                {checkedIn && (
                  <button type="button" className="button secondary" onClick={leave} disabled={checkOut.pending}>
                    Check out
                  </button>
                )}
              </div>
              <Alert>{checkOut.error}</Alert>
            </form>
          )}
        </section>

        <section className="card">
          <h2>Best matches for you</h2>
          {!checkedIn ? (
            <EmptyState>Check in as available to see who you are most compatible with.</EmptyState>
          ) : suggestions.loading && !suggestions.data ? (
            <Spinner />
          ) : suggestions.data?.length ? (
            <ul className="list">
              {suggestions.data.map((s) => (
                <li key={s.studentId}>
                  <ScoreBadge score={s.score} />
                  <div>
                    <strong>{s.name}</strong>
                    <span className="muted"> · {s.zone}</span>
                    <div className="chips-inline">
                      {s.sharedSubjects.map((n) => (
                        <Pill key={n}>{n}</Pill>
                      ))}
                    </div>
                  </div>
                </li>
              ))}
            </ul>
          ) : (
            <EmptyState>No compatible students are available right now.</EmptyState>
          )}
          <Alert>{find.error}</Alert>
          <button className="button" onClick={findPartner} disabled={!checkedIn || status !== 'AVAILABLE' || find.pending}>
            {find.pending ? 'Matching…' : 'Find me a study partner'}
          </button>
          <p className="muted small">
            We also match everyone automatically every few seconds — you will get a notification the moment a partner is
            found.
          </p>
        </section>
      </div>

      <section className="card">
        <div className="row spread">
          <h2>Available on campus now</h2>
          <select
            aria-label="Filter by zone"
            value={zoneFilter ?? ''}
            onChange={(e) => setZoneFilter(e.target.value ? Number(e.target.value) : undefined)}
          >
            <option value="">All zones</option>
            {zones.data?.map((z) => (
              <option key={z.id} value={z.id}>
                {z.name}
              </option>
            ))}
          </select>
        </div>
        <Alert>{available.error}</Alert>
        {available.loading && !available.data ? (
          <Spinner />
        ) : available.data?.length ? (
          <ul className="people">
            {available.data.map((p) => (
              <PersonCard key={p.studentId} person={p} />
            ))}
          </ul>
        ) : (
          <EmptyState>Nobody is available right now.</EmptyState>
        )}
      </section>
    </>
  );
}

function PersonCard({ person }: { person: PresenceView }) {
  return (
    <li className="person">
      <div className="avatar" aria-hidden="true">
        {person.name.charAt(0)}
      </div>
      <div>
        <strong>{person.name}</strong>
        <span className="muted"> · {person.zone}</span>
        {person.requirements && <p className="quote">“{person.requirements}”</p>}
        <div className="chips-inline">
          {person.subjects.map((s) => (
            <LevelPill key={s.name} name={s.name} level={s.proficiency} />
          ))}
        </div>
      </div>
    </li>
  );
}
