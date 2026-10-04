import { Link } from 'react-router-dom';
import { hackathons, matching, presence } from '../api/endpoints';
import { useAuth } from '../auth/AuthContext';
import { PageHeader, Pill, ScoreBadge } from '../components/ui';
import { useCountdown, useLoad } from '../hooks';
import { useRealtime } from '../realtime/RealtimeContext';

export function DashboardPage() {
  const { profile } = useAuth();
  const { inbox, version } = useRealtime();
  const mine = useLoad(() => presence.mine(), [version('presence')]);
  const match = useLoad(() => matching.current(), [version('matches')]);
  const events = useLoad(() => hackathons.list(), [version('hackathons')]);
  const remaining = useCountdown(mine.data?.expiresAt);

  if (!profile) return null;
  const profileEmpty = profile.subjects.length === 0 && profile.skills.length === 0;
  const checkedIn = Boolean(mine.data) && remaining !== null;
  const open = events.data?.filter((h) => h.status === 'OPEN') ?? [];

  return (
    <>
      <PageHeader title={`Hi, ${profile.fullName.split(' ')[0]}`} subtitle="Here is what is happening for you right now." />

      {profileEmpty && (
        <section className="card callout">
          <h2>Start with your profile</h2>
          <p>Add the subjects you study and the skills you have. Matching and team formation both depend on them.</p>
          <Link className="button" to="/profile">
            Complete profile
          </Link>
        </section>
      )}

      <div className="grid-3">
        <section className="card">
          <h2>On campus</h2>
          {checkedIn ? (
            <>
              <p>
                <strong>{mine.data?.zone}</strong> · {mine.data?.status === 'AVAILABLE' ? 'available' : 'busy'}
              </p>
              <p className="muted">Expires in {remaining}</p>
            </>
          ) : (
            <p className="muted">You are not checked in.</p>
          )}
          <Link to="/campus">{checkedIn ? 'Manage check-in' : 'Check in'}</Link>
        </section>

        <section className="card">
          <h2>Study partner</h2>
          {match.data ? (
            <div className="row">
              <ScoreBadge score={match.data.score} />
              <div>
                <strong>{match.data.partner.name}</strong>
                <p className="muted">
                  {match.data.status === 'ACCEPTED' ? 'Confirmed' : 'Waiting for a response'}
                </p>
              </div>
            </div>
          ) : (
            <p className="muted">{checkedIn ? 'Looking for someone for you…' : 'Check in to get matched.'}</p>
          )}
          <Link to="/matches">View matches</Link>
        </section>

        <section className="card">
          <h2>Notifications</h2>
          <p>
            <strong>{inbox.unread}</strong> unread
          </p>
          {inbox.items[0] && <p className="muted">Latest: {inbox.items[0].title}</p>}
        </section>
      </div>

      <section className="card">
        <h2>Open hackathons</h2>
        {open.length === 0 ? (
          <p className="muted">
            Nothing open right now. <Link to="/hackathons/new">Organise one</Link>.
          </p>
        ) : (
          <ul className="list">
            {open.map((h) => (
              <li key={h.id}>
                <div>
                  <Link to={`/hackathons/${h.id}`}>
                    <strong>{h.name}</strong>
                  </Link>
                  <span className="muted"> · {h.participants} registered</span>
                  {h.registered && <Pill>you are in</Pill>}
                </div>
              </li>
            ))}
          </ul>
        )}
      </section>
    </>
  );
}
