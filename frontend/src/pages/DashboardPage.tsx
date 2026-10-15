import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { ArrowRight, CalendarDays, ChevronRight, HeartHandshake, MapPin, Sparkles, TrendingUp, Trophy, Users } from 'lucide-react';
import { hackathons, matching, presence } from '../api/endpoints';
import { useAuth } from '../auth/AuthContext';
import { ConnectButton, SuggestionRow, connectBlocker } from '../components/Connect';
import { initials } from '../components/Layout';
import { Modal } from '../components/Modal';
import { Alert, EmptyState, PageHeader, Pill, ScoreBadge } from '../components/ui';
import { useAction, useCountdown, useLoad } from '../hooks';
import { checkInRequest } from '../lib-presence';
import { useRealtime } from '../realtime/RealtimeContext';

export function DashboardPage() {
  const { profile } = useAuth();
  const { inbox, version } = useRealtime();
  const navigate = useNavigate();
  const presenceVersion = version('presence');
  const mine = useLoad(() => presence.mine(), [presenceVersion]);
  const zones = useLoad(() => presence.zones(), []);
  const available = useLoad(() => presence.available(), [presenceVersion]);
  const match = useLoad(() => matching.current(), [version('matches')]);
  const events = useLoad(() => hackathons.list(), [version('hackathons')]);
  const remaining = useCountdown(mine.data?.expiresAt);
  const checkedIn = Boolean(mine.data) && remaining !== null;
  const suggestions = useLoad(() => (checkedIn ? matching.suggestions() : Promise.resolve([])), [presenceVersion, version('matches'), checkedIn]);
  const [zoneId, setZoneId] = useState<number | ''>('');
  const [showAll, setShowAll] = useState(false);
  const [sentTo, setSentTo] = useState<string | null>(null);
  const action = useAction();

  // Follow the server's zone only when it really changes. Depending on the whole object would reset the student's own
  // dropdown choice every time anyone on campus checks in (each presence change reloads it).
  const serverZone = mine.data?.zoneId;
  const firstZone = zones.data?.[0]?.id;
  useEffect(() => {
    if (serverZone !== undefined) setZoneId(serverZone);
    else if (firstZone !== undefined) setZoneId((z) => (z === '' ? firstZone : z));
  }, [serverZone, firstZone]);

  if (!profile) return null;
  const open = events.data?.filter((h) => h.status === 'OPEN') ?? [];
  const others = (available.data ?? []).filter((p) => p.studentId !== profile.id);
  const hereCount = mine.data ? others.filter((p) => p.zoneId === mine.data!.zoneId).length : 0;
  const best = suggestions.data?.[0];
  const hour = new Date().getHours();
  const greeting = hour < 5 ? 'Up late' : hour < 12 ? 'Good morning' : hour < 17 ? 'Good afternoon' : 'Good evening';
  const profileEmpty = profile.subjects.length === 0 && profile.skills.length === 0;
  const blocker = connectBlocker(match.data, checkedIn);
  const onSent = (sent: { partner: { name: string } }) => {
    setSentTo(sent.partner.name);
    match.reload();
  };

  async function checkIn() {
    if (zoneId === '') return;
    await action.run(async () => {
      await presence.checkIn(checkInRequest(zoneId, checkedIn ? mine.data : undefined, profile));
      mine.reload();
      available.reload();
    });
  }

  async function checkOut() {
    await action.run(async () => {
      await presence.checkOut();
      mine.reload();
      available.reload();
    });
  }

  async function findPeer() {
    if (!checkedIn) {
      navigate('/campus');
      return;
    }
    const found = await action.run(() => matching.find());
    navigate('/matches', { state: { fresh: Boolean(found) } });
  }

  return (
    <>
      <PageHeader
        eyebrow={`${greeting}, ${profile.fullName.split(' ')[0]}`}
        title="Find your people."
        subtitle="Meaningful study sessions start with shared subjects. See who is on campus right now and get matched in seconds."
        actions={
          <button className="button" onClick={findPeer} disabled={action.pending}>
            <Sparkles aria-hidden="true" />
            Find a new peer
          </button>
        }
      />

      {profileEmpty && (
        <section className="card callout">
          <h2>Start with your profile</h2>
          <p>Add the subjects you study and the skills you have. Matching and team formation both depend on them.</p>
          <Link className="button" to="/profile">
            Complete profile
          </Link>
        </section>
      )}

      <div className="stat-row">
        <Link to="/campus" className="card stat-card">
          <span className="stat-top">
            Students on campus now
            <Users aria-hidden="true" />
          </span>
          <span className="stat-value">{available.data ? others.length : '–'}</span>
          <span className="stat-hint">
            <TrendingUp aria-hidden="true" />
            across {zones.data?.length ?? 0} zones
          </span>
        </Link>
        <Link to={checkedIn ? '/campus' : '/campus'} className="card stat-card">
          <span className="stat-top">
            Best match right now
            <Sparkles aria-hidden="true" />
          </span>
          <span className="stat-value">{best ? `${best.score}%` : '–'}</span>
          <span className={`stat-hint ${best ? '' : 'neutral'}`}>
            {best ? `${best.name} · ${best.zone}` : checkedIn ? 'Nobody compatible yet' : 'Check in to see matches'}
          </span>
        </Link>
        <Link to="/hackathons" className="card stat-card">
          <span className="stat-top">
            Open hackathons
            <Trophy aria-hidden="true" />
          </span>
          <span className="stat-value">{events.data ? open.length : '–'}</span>
          <span className="stat-hint neutral">{inbox.unread} unread notification{inbox.unread === 1 ? '' : 's'}</span>
        </Link>
      </div>

      <div className="split">
        <section className="card">
          <div className="card-head">
            <div>
              <span className="eyebrow">Live presence</span>
              <h2>Where are you studying?</h2>
            </div>
            <span className={`status-chip ${checkedIn ? '' : 'off'}`}>{checkedIn ? 'Checked in' : 'Not checked in'}</span>
          </div>
          <div className="row">
            <select aria-label="Campus zone" value={zoneId} onChange={(e) => setZoneId(Number(e.target.value))} style={{ flex: 1 }}>
              {zones.data?.map((z) => (
                <option key={z.id} value={z.id}>
                  {z.name}
                </option>
              ))}
            </select>
            <button className="button" onClick={checkIn} disabled={action.pending || zoneId === ''}>
              <MapPin aria-hidden="true" />
              {checkedIn ? (mine.data?.zoneId === zoneId ? 'Renew' : 'Move here') : 'Check in'}
            </button>
          </div>
          <Alert>{action.error}</Alert>
          <div className="row spread">
            <span className="muted small">
              <Users aria-hidden="true" style={{ width: 14, height: 14, verticalAlign: '-2px' }} />{' '}
              {checkedIn ? `${hereCount} peer${hereCount === 1 ? '' : 's'} in ${mine.data?.zone} · expires in ${remaining}` : 'Check-ins last 15 minutes and are never tracked by GPS.'}
            </span>
            {checkedIn && (
              <button className="link" onClick={checkOut} disabled={action.pending}>
                Check out
              </button>
            )}
          </div>
        </section>

        <section className="card">
          <div className="card-head">
            <div>
              <span className="eyebrow">Your study partner</span>
              <h2>{match.data ? match.data.partner.name : 'No match yet'}</h2>
            </div>
            <HeartHandshake aria-hidden="true" style={{ color: 'var(--purple)' }} />
          </div>
          {match.data ? (
            <div className="row">
              <ScoreBadge score={match.data.score} />
              <div>
                <p className="muted small" style={{ margin: 0 }}>
                  {match.data.status === 'ACCEPTED' ? 'Confirmed match' : match.data.youAccepted ? 'Waiting for them to accept' : 'Waiting for your answer'}
                </p>
                <div className="chips-inline">
                  {match.data.sharedSubjects.map((s) => (
                    <Pill key={s}>{s}</Pill>
                  ))}
                </div>
              </div>
            </div>
          ) : (
            <p className="muted">{checkedIn ? 'We are looking for someone for you…' : 'Check in as available and we will pair you within seconds.'}</p>
          )}
          <Link to="/matches" className="link">
            View matches <ArrowRight aria-hidden="true" />
          </Link>
        </section>
      </div>

      <section style={{ marginBottom: '1rem' }}>
        <div className="row spread" style={{ marginBottom: '0.8rem' }}>
          <div>
            <h2 style={{ margin: 0, fontSize: '1.3rem' }}>Recommended for you</h2>
            <span className="muted small">Ranked by compatibility with the students on campus right now</span>
          </div>
          {checkedIn && Boolean(suggestions.data?.length) && (
            <button type="button" className="link" onClick={() => setShowAll(true)}>
              View all <ChevronRight aria-hidden="true" />
            </button>
          )}
        </div>
        {sentTo && (
          <Alert kind="success">
            Request sent to {sentTo}. We will notify you the moment they reply — follow it on <Link to="/matches">Matches</Link>.
          </Alert>
        )}
        {!checkedIn ? (
          <div className="card">
            <EmptyState>Check in above to see the students you would study best with.</EmptyState>
          </div>
        ) : suggestions.data?.length ? (
          <div className="person-grid">
            {suggestions.data.slice(0, 6).map((s) => (
              <article key={s.studentId} className="card person-tile" style={{ marginBottom: 0 }}>
                <div className="tile-head">
                  <span className="avatar-badge" aria-hidden="true">
                    {initials(s.name)}
                  </span>
                  <div>
                    <strong>{s.name}</strong>
                    <small>{s.zone}</small>
                  </div>
                  <span className="tile-score">{s.score}% match</span>
                </div>
                <div className="chips-inline">
                  {s.sharedSubjects.map((n) => (
                    <Pill key={n}>{n}</Pill>
                  ))}
                </div>
                <ConnectButton suggestion={s} blocker={blocker} onSent={onSent} />
              </article>
            ))}
          </div>
        ) : (
          <div className="card">
            <EmptyState>Nobody compatible is available right now. We will notify you when someone checks in.</EmptyState>
          </div>
        )}
      </section>

      {showAll && suggestions.data && (
        <Modal
          title="Recommended for you"
          subtitle={`${suggestions.data.length} students on campus, ranked by compatibility${blocker ? ` · ${blocker}` : ''}`}
          onClose={() => setShowAll(false)}
          wide
        >
          <ul className="suggestion-list">
            {suggestions.data.map((s) => (
              <SuggestionRow key={s.studentId} suggestion={s} blocker={blocker} onSent={onSent} />
            ))}
          </ul>
        </Modal>
      )}

      <section className="card">
        <div className="card-head">
          <div>
            <span className="eyebrow">Collaborate &amp; build</span>
            <h2>Open hackathons</h2>
          </div>
          <CalendarDays aria-hidden="true" style={{ color: 'var(--cyan)' }} />
        </div>
        {open.length === 0 ? (
          <p className="muted">
            Nothing open right now. <Link to="/hackathons/new">Organise one</Link>.
          </p>
        ) : (
          <ul className="list">
            {open.map((h) => (
              <li key={h.id}>
                <span className="avatar-badge" aria-hidden="true" style={{ background: 'var(--gradient)' }}>
                  {initials(h.name)}
                </span>
                <div style={{ minWidth: 0 }}>
                  <Link to={`/hackathons/${h.id}`}>
                    <strong>{h.name}</strong>
                  </Link>
                  <div className="muted small">
                    {h.participants} registered · {h.roles.length} roles
                    {h.registered && <span className="tag-soft">you are in</span>}
                  </div>
                </div>
              </li>
            ))}
          </ul>
        )}
      </section>
    </>
  );
}
