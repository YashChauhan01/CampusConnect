import { useEffect, useState, type FormEvent } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { ChevronRight, Clock, LogOut, MapPin, Navigation, Search, Sparkles } from 'lucide-react';
import { matching, presence, profile as profileApi } from '../api/endpoints';
import type { PresenceStatus, PresenceView, Proficiency } from '../api/types';
import { PROFICIENCIES } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { SuggestionRow, connectBlocker } from '../components/Connect';
import { Modal } from '../components/Modal';
import { Alert, EmptyState, Field, LevelPill, PageHeader, Spinner, levelLabel } from '../components/ui';
import { useAction, useCountdown, useLoad } from '../hooks';
import { useRealtime } from '../realtime/RealtimeContext';

const PINS = [[26, 36], [74, 28], [44, 72], [78, 74], [16, 74], [56, 22], [88, 50], [10, 40]];

export function CampusPage() {
  const { profile } = useAuth();
  const { version } = useRealtime();
  const navigate = useNavigate();
  const presenceVersion = version('presence');
  const matchVersion = version('matches');

  const zones = useLoad(() => presence.zones(), []);
  const mine = useLoad(() => presence.mine(), [presenceVersion]);
  const available = useLoad(() => presence.available(), [presenceVersion]);
  const current = useLoad(() => matching.current(), [matchVersion]);
  const remaining = useCountdown(mine.data?.expiresAt);
  const checkedIn = Boolean(mine.data) && remaining !== null;
  const suggestions = useLoad(() => (checkedIn ? matching.suggestions() : Promise.resolve([])), [presenceVersion, matchVersion, checkedIn]);
  const [zoneFilter, setZoneFilter] = useState<number | undefined>(undefined);
  const [params, setParams] = useSearchParams();
  const query = params.get('q') ?? '';
  const [formOpen, setFormOpen] = useState(false);
  const [sentTo, setSentTo] = useState<string | null>(null);
  const checkOut = useAction();

  async function leave() {
    await checkOut.run(async () => {
      await presence.checkOut();
      mine.reload();
      available.reload();
    });
  }

  const here = mine.data;
  const everyone = (available.data ?? []).filter((p) => p.studentId !== profile?.id);
  const counts = new Map<number, number>();
  everyone.forEach((p) => counts.set(p.zoneId, (counts.get(p.zoneId) ?? 0) + 1));
  const q = query.trim().toLowerCase();
  const people = everyone
    .filter((p) => zoneFilter === undefined || p.zoneId === zoneFilter)
    .filter((p) => !q || p.name.toLowerCase().includes(q) || p.subjects.some((s) => s.name.toLowerCase().includes(q))
      || (p.requirements ?? '').toLowerCase().includes(q));
  const selectedZone = zones.data?.find((z) => z.id === zoneFilter)?.name;
  const vibe = (n: number) => (n >= 30 ? 'High energy' : n >= 15 ? 'Focused' : n > 0 ? 'Quiet' : 'Empty');
  const blocker = connectBlocker(current.data, checkedIn);
  const myZoneId = checkedIn ? here?.zoneId : undefined;
  const onSent = (sent: { partner: { name: string } }) => {
    setSentTo(sent.partner.name);
    current.reload();
  };

  return (
    <>
      <PageHeader
        eyebrow="Campus presence"
        title="Your campus, live."
        subtitle="See who is around you, choose where you want to be found, and get matched with someone to study with."
        actions={
          <>
            {checkedIn && (
              <button className="button secondary" onClick={leave} disabled={checkOut.pending}>
                <LogOut aria-hidden="true" />
                Check out
              </button>
            )}
            <button className="button" onClick={() => setFormOpen(true)}>
              <Sparkles aria-hidden="true" />
              Find a peer
            </button>
          </>
        }
      />
      <Alert>{checkOut.error}</Alert>

      <section className={`card presence-banner ${checkedIn ? 'on' : ''}`} aria-live="polite">
        <span className="banner-icon" aria-hidden="true">
          {checkedIn ? <Navigation /> : <MapPin />}
        </span>
        {checkedIn && here ? (
          <div className="banner-text">
            <span className="eyebrow">You are checked in</span>
            <strong>{here.zone}</strong>
            <span className="muted small">
              {here.status === 'AVAILABLE' ? 'Open to study partners' : 'Busy — not being matched'}
              {here.seeking.length > 0 && ` · working on ${here.seeking.join(', ')}`}
              {here.requirements && ` · “${here.requirements}”`}
            </span>
          </div>
        ) : (
          <div className="banner-text">
            <span className="eyebrow">Not checked in</span>
            <strong>Tell us where you are</strong>
            <span className="muted small">Press “Find a peer” to check in and get matched. Check-ins last 15 minutes; no GPS.</span>
          </div>
        )}
        {checkedIn && (
          <span className="banner-timer" title="Time left on this check-in">
            <Clock aria-hidden="true" /> {remaining}
          </span>
        )}
      </section>

      <div className="split">
        <section className="card">
          <div className="zone-board" role="group" aria-label="Campus map">
            {zones.data?.map((z, i) => {
              const [x, y] = PINS[i % PINS.length];
              const isMine = z.id === myZoneId;
              return (
                <button
                  key={z.id}
                  type="button"
                  className={`zone-pin ${zoneFilter === z.id ? 'on' : ''} ${isMine ? 'mine' : ''}`}
                  style={{ left: `${x}%`, top: `${y}%` }}
                  aria-pressed={zoneFilter === z.id}
                  aria-label={`${z.name}: ${counts.get(z.id) ?? 0} peers${isMine ? ', you are here' : ''}`}
                  onClick={() => setZoneFilter((cur) => (cur === z.id ? undefined : z.id))}
                >
                  {isMine && <span className="you-tag">You</span>}
                  <MapPin aria-hidden="true" />
                  <strong>{counts.get(z.id) ?? 0}</strong>
                  <small>{z.name}</small>
                </button>
              );
            })}
          </div>
          <p className="muted small" style={{ margin: '0.6rem 0 0' }}>
            Live campus zones · select a zone to see the people there{myZoneId ? ' · your zone is highlighted' : ''}
          </p>
        </section>
        <section className="card">
          <div className="card-head">
            <div>
              <span className="eyebrow">Zones</span>
              <h2>Campus activity</h2>
            </div>
            <span className="tag-soft">{everyone.length} active</span>
          </div>
          <div className="zone-list">
            {zones.data?.map((z) => (
              <button
                key={z.id}
                type="button"
                className={`zone-item ${zoneFilter === z.id ? 'on' : ''} ${z.id === myZoneId ? 'mine' : ''}`}
                aria-pressed={zoneFilter === z.id}
                onClick={() => setZoneFilter((cur) => (cur === z.id ? undefined : z.id))}
              >
                <span className="zone-icon">
                  <MapPin aria-hidden="true" />
                </span>
                <div>
                  <strong>
                    {z.name}
                    {z.id === myZoneId && <span className="tag-soft">You are here</span>}
                  </strong>
                  <small>
                    {vibe(counts.get(z.id) ?? 0)} · {counts.get(z.id) ?? 0} peers
                  </small>
                </div>
                <ChevronRight aria-hidden="true" />
              </button>
            ))}
          </div>
        </section>
      </div>

      <section className="card">
        <div className="card-head">
          <div>
            <span className="eyebrow">Recommended</span>
            <h2>Best matches for you</h2>
          </div>
          {blocker && checkedIn && <span className="muted small">{blocker}</span>}
        </div>
        {sentTo && (
          <Alert kind="success">
            Request sent to {sentTo}. Follow it on <Link to="/matches">Matches</Link>.
          </Alert>
        )}
        {!checkedIn ? (
          <EmptyState>Check in with “Find a peer” to see who you are most compatible with.</EmptyState>
        ) : suggestions.loading && !suggestions.data ? (
          <Spinner />
        ) : suggestions.data?.length ? (
          <ul className="suggestion-list">
            {suggestions.data.slice(0, 8).map((s) => (
              <SuggestionRow key={s.studentId} suggestion={s} blocker={blocker} onSent={onSent} />
            ))}
          </ul>
        ) : (
          <EmptyState>No compatible students are available right now.</EmptyState>
        )}
      </section>

      <section className="card">
        <div className="card-head">
          <div>
            <span className="eyebrow">{selectedZone ? 'Selected zone' : 'Everyone on campus'}</span>
            <h2>{selectedZone ? `People in ${selectedZone}` : 'Available on campus now'}</h2>
          </div>
          <span className="tag-soft">{people.length} visible</span>
        </div>
        <div className="row">
          <label className="search-box" style={{ display: 'flex', width: 'auto', flex: 1 }}>
            <Search aria-hidden="true" />
            <input
              type="search"
              placeholder="Filter by name or subject"
              aria-label="Filter people by name or subject"
              value={query}
              onChange={(e) => setParams(e.target.value ? { q: e.target.value } : {}, { replace: true })}
            />
          </label>
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
        ) : people.length ? (
          <ul className="people">
            {people.map((p) => (
              <PersonCard key={p.studentId} person={p} />
            ))}
          </ul>
        ) : (
          <EmptyState>{q ? `Nobody matching “${query}” is available right now.` : 'Nobody is available right now.'}</EmptyState>
        )}
      </section>

      {formOpen && (
        <FindPeerForm
          current={checkedIn ? here : undefined}
          onClose={() => setFormOpen(false)}
          onDone={(found) => {
            setFormOpen(false);
            mine.reload();
            available.reload();
            navigate('/matches', { state: { fresh: found } });
          }}
          onSaved={() => {
            mine.reload();
            available.reload();
          }}
        />
      )}
    </>
  );
}

/** Check-in form in a dialog: where you are, whether you are free, what you want to study. */
function FindPeerForm({ current, onClose, onDone, onSaved }: {
  current: PresenceView | undefined;
  onClose: () => void;
  onDone: (found: boolean) => void;
  onSaved: () => void;
}) {
  const { profile, setProfile } = useAuth();
  const zones = useLoad(() => presence.zones(), []);
  const [zoneId, setZoneId] = useState<number | ''>(current?.zoneId ?? '');
  const [newSubject, setNewSubject] = useState('');
  const [newLevel, setNewLevel] = useState<Proficiency>('INTERMEDIATE');
  const adding = useAction();
  const [status, setStatus] = useState<PresenceStatus>(current?.status ?? 'AVAILABLE');
  const [requirements, setRequirements] = useState(current?.requirements ?? '');
  const subjects = profile?.subjects ?? [];
  const [seeking, setSeeking] = useState<number[]>(() => {
    const byName = new Map(subjects.map((s) => [s.name, s.id]));
    return (current?.seeking ?? []).map((n) => byName.get(n)).filter((id): id is number => id !== undefined);
  });
  const action = useAction();

  useEffect(() => {
    if (zoneId === '' && zones.data?.length) setZoneId(zones.data[0].id);
  }, [zones.data, zoneId]);

  /** Adds a subject to the profile (matching compares levels, so it needs one) and ticks it for today. */
  async function addSubject() {
    const name = newSubject.trim();
    if (!name) return;
    const existing = subjects.find((x) => x.name.toLowerCase() === name.toLowerCase());
    if (existing) {
      setSeeking((cur) => (cur.includes(existing.id) ? cur : [...cur, existing.id]));
      setNewSubject('');
      return;
    }
    const updated = await adding.run(() => profileApi.saveSubject(name, newLevel));
    if (!updated) return;
    setProfile(updated);
    const added = updated.subjects.find((x) => x.name.toLowerCase() === name.toLowerCase());
    if (added) setSeeking((cur) => [...cur, added.id]);
    setNewSubject('');
  }

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (zoneId === '') return;
    const result = await action.run(async () => {
      await presence.checkIn({ zoneId, status, requirements: requirements.trim() || undefined, seekingSubjectIds: seeking });
      if (status !== 'AVAILABLE') return { found: false, matched: false };
      const match = await matching.find();
      return { found: Boolean(match), matched: true };
    });
    if (!result) return;
    if (result.matched) onDone(result.found);
    else {
      onSaved();
      onClose();
    }
  }

  return (
    <Modal title="Find a peer" subtitle="Check in and we will look for the best study partner on campus right now." onClose={onClose}>
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
              <p className="muted small">Add the first subject you want to study below.</p>
            ) : (
              <div className="checks">
                {subjects.map((s) => (
                  <label key={s.id} className="check">
                    <input
                      type="checkbox"
                      checked={seeking.includes(s.id)}
                      onChange={(e) => setSeeking((cur) => (e.target.checked ? [...cur, s.id] : cur.filter((id) => id !== s.id)))}
                    />
                    {s.name}
                  </label>
                ))}
              </div>
            )}
            <div className="add-subject">
              <input
                value={newSubject}
                onChange={(e) => setNewSubject(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter') {
                    e.preventDefault();
                    void addSubject();
                  }
                }}
                placeholder="Add another subject, e.g. Operating Systems"
                aria-label="New subject"
                maxLength={100}
              />
              <select value={newLevel} onChange={(e) => setNewLevel(e.target.value as Proficiency)} aria-label="Your level in the new subject">
                {PROFICIENCIES.map((p) => (
                  <option key={p} value={p}>
                    {levelLabel(p)}
                  </option>
                ))}
              </select>
              <button type="button" className="button secondary small" onClick={() => void addSubject()} disabled={adding.pending || !newSubject.trim()}>
                Add
              </button>
            </div>
            <small className="muted">New subjects are saved to your profile with your level, so we can match you on them.</small>
            <Alert>{adding.error}</Alert>
          </fieldset>
          <Field label="Anything specific? (optional)" hint="e.g. “Revising graph algorithms for tomorrow’s quiz”">
            <textarea value={requirements} onChange={(e) => setRequirements(e.target.value)} maxLength={300} rows={2} />
          </Field>
          <Alert>{action.error}</Alert>
          <div className="row" style={{ justifyContent: 'flex-end' }}>
            <button type="button" className="button secondary" onClick={onClose}>
              Cancel
            </button>
            <button className="button" disabled={action.pending || zoneId === ''}>
              <Sparkles aria-hidden="true" />
              {action.pending ? 'Finding…' : status === 'AVAILABLE' ? (current ? 'Update & find a peer' : 'Check in & find a peer') : 'Save check-in'}
            </button>
          </div>
        </form>
      )}
    </Modal>
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
            <LevelPill key={s.name} name={s.name} level={s.proficiency} verified={s.verified} />
          ))}
        </div>
      </div>
    </li>
  );
}

