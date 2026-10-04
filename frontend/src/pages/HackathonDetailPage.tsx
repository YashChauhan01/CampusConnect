import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { hackathons } from '../api/endpoints';
import type { HackathonDetail, TeamsView } from '../api/types';
import { Alert, EmptyState, PageHeader, Pill, Spinner } from '../components/ui';
import { useAction, useLoad } from '../hooks';
import { useRealtime } from '../realtime/RealtimeContext';
import { STATUS_LABEL } from './HackathonsPage';

const MAX_PREFERENCES = 3;

function TeamsPanel({ teams, organizer }: { teams: TeamsView; organizer: boolean }) {
  return (
    <section className="card">
      <h2>{teams.published ? 'Teams' : 'Draft teams'}</h2>
      {!teams.published && organizer && (
        <p className="muted">Only you can see this draft. Regenerate as often as you like, then publish.</p>
      )}
      {organizer && (
        <dl className="quality" aria-label="Team quality">
          <div>
            <dt>Role fit</dt>
            <dd>{teams.quality.averageFit}%</dd>
          </div>
          <div>
            <dt>Got a top-3 role</dt>
            <dd>{teams.quality.preferenceSatisfaction}%</dd>
          </div>
          <div>
            <dt>Strength spread</dt>
            <dd>{teams.quality.strengthSpread.toFixed(2)}</dd>
          </div>
        </dl>
      )}
      <ul className="cards">
        {teams.teams.map((team) => (
          <li key={team.number} className={`team ${team.yours ? 'yours' : ''}`}>
            <h3>
              Team {team.number} {team.yours && <Pill>your team</Pill>}
            </h3>
            <ul>
              {team.members.map((m) => (
                <li key={m.studentId}>
                  <strong>{m.name}</strong>
                  {m.you && ' (you)'}
                  <span className="muted"> · {m.role}</span>
                  {organizer && <span className="muted small"> · fit {m.fit}%</span>}
                </li>
              ))}
            </ul>
          </li>
        ))}
      </ul>
    </section>
  );
}

function Registration({ detail, onChanged }: { detail: HackathonDetail; onChanged: (d: HackathonDetail) => void }) {
  const h = detail.summary;
  const [prefs, setPrefs] = useState<(number | '')[]>(['', '', '']);
  const { pending, error, run } = useAction();

  useEffect(() => {
    const next: (number | '')[] = ['', '', ''];
    detail.myPreferences.slice(0, MAX_PREFERENCES).forEach((id, i) => (next[i] = id));
    setPrefs(next);
  }, [detail.myPreferences]);

  const chosen = prefs.filter((p): p is number => p !== '');
  const open = h.status === 'OPEN';
  const ordinal = ['First choice', 'Second choice', 'Third choice'];

  return (
    <section className="card">
      <h2>{h.registered ? 'You are registered' : 'Register'}</h2>
      {!open && h.status === 'CLOSED' && <p className="muted">Registration is closed.</p>}
      {open && (
        <>
          <p className="muted">
            Rank the roles you would like. We use your profile skills and these preferences to place you; you may be
            assigned outside your picks if the team needs it.
          </p>
          <div className="stack">
            {prefs.map((value, index) => (
              <label className="field" key={index}>
                <span>{ordinal[index]}</span>
                <select
                  value={value}
                  onChange={(e) =>
                    setPrefs((cur) => cur.map((v, i) => (i === index ? (e.target.value ? Number(e.target.value) : '') : v)))
                  }
                >
                  <option value="">—</option>
                  {h.roles.map((r) => (
                    <option key={r.id} value={r.id} disabled={chosen.includes(r.id) && value !== r.id}>
                      {r.name}
                    </option>
                  ))}
                </select>
              </label>
            ))}
          </div>
          <Alert>{error}</Alert>
          <div className="row">
            <button
              className="button"
              disabled={pending}
              onClick={async () => {
                const d = await run(() => hackathons.register(h.id, chosen));
                if (d) onChanged(d);
              }}
            >
              {h.registered ? 'Update preferences' : 'Register'}
            </button>
            {h.registered && (
              <button
                className="button secondary"
                disabled={pending}
                onClick={async () => {
                  const d = await run(() => hackathons.withdraw(h.id));
                  if (d) onChanged(d);
                }}
              >
                Withdraw
              </button>
            )}
          </div>
        </>
      )}
    </section>
  );
}

function OrganizerPanel({
  detail,
  teams,
  onDetail,
  onTeams,
}: {
  detail: HackathonDetail;
  teams: TeamsView | undefined;
  onDetail: (d: HackathonDetail) => void;
  onTeams: (t: TeamsView) => void;
}) {
  const h = detail.summary;
  const { pending, error, run } = useAction();
  const published = h.status === 'PUBLISHED';

  return (
    <section className="card">
      <h2>Organiser tools</h2>
      <p className="muted">
        {h.participants} registered. Close registration when you are ready, generate teams, review the quality scores and
        publish. Participants are notified instantly.
      </p>
      <div className="row">
        {h.status === 'OPEN' && (
          <button
            className="button secondary"
            disabled={pending}
            onClick={async () => {
              const d = await run(() => hackathons.close(h.id));
              if (d) onDetail(d);
            }}
          >
            Close registration
          </button>
        )}
        {h.status === 'CLOSED' && (
          <button
            className="button secondary"
            disabled={pending}
            onClick={async () => {
              const d = await run(() => hackathons.reopen(h.id));
              if (d) onDetail(d);
            }}
          >
            Reopen registration
          </button>
        )}
        {!published && (
          <button
            className="button"
            disabled={pending || h.participants < 2}
            onClick={async () => {
              const t = await run(() => hackathons.synthesize(h.id));
              if (t) onTeams(t);
            }}
          >
            {teams?.teams.length ? 'Regenerate teams' : 'Generate teams'}
          </button>
        )}
        {!published && teams?.teams.length ? (
          <button
            className="button"
            disabled={pending}
            onClick={async () => {
              if (!window.confirm('Publish these teams? Everyone will be notified and teams become final.')) return;
              const t = await run(() => hackathons.publish(h.id));
              if (t) {
                onTeams(t);
                onDetail({ ...detail, summary: { ...h, status: 'PUBLISHED' } });
              }
            }}
          >
            Publish teams
          </button>
        ) : null}
      </div>
      <Alert>{error}</Alert>
      {detail.participants.length > 0 && (
        <table>
          <caption className="sr-only">Registered participants</caption>
          <thead>
            <tr>
              <th>Name</th>
              <th>Skills</th>
              <th>Preferred roles</th>
            </tr>
          </thead>
          <tbody>
            {detail.participants.map((p) => (
              <tr key={p.studentId}>
                <td>{p.name}</td>
                <td>{p.skills.join(', ') || '—'}</td>
                <td>{p.preferences.join(' › ') || '—'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </section>
  );
}

export function HackathonDetailPage() {
  const { id = '' } = useParams();
  const { version } = useRealtime();
  const hackathonVersion = version('hackathons');
  const detail = useLoad(() => hackathons.detail(id), [id, hackathonVersion]);
  const [teams, setTeams] = useState<TeamsView | undefined>(undefined);
  const [teamsError, setTeamsError] = useState<string | null>(null);

  const status = detail.data?.summary.status;
  const organizer = detail.data?.summary.organizedByMe ?? false;
  // Organisers always see the current (draft or final) teams; participants only once published.
  const canSeeTeams = organizer || status === 'PUBLISHED';
  useEffect(() => {
    if (!detail.data || !canSeeTeams) {
      setTeams(undefined);
      return;
    }
    let cancelled = false;
    hackathons.teams(id).then(
      (t) => {
        if (!cancelled) {
          setTeams(t);
          setTeamsError(null);
        }
      },
      (e: Error) => !cancelled && setTeamsError(e.message),
    );
    return () => {
      cancelled = true;
    };
  }, [id, status, canSeeTeams, detail.data, hackathonVersion]);

  if (detail.loading && !detail.data) return <Spinner />;
  if (!detail.data) return <Alert>{detail.error ?? 'Not found'}</Alert>;

  const d = detail.data;
  const h = d.summary;

  return (
    <>
      <PageHeader
        title={h.name}
        subtitle={`${STATUS_LABEL[h.status]} · organised by ${h.organizedByMe ? 'you' : h.organizer} · ${h.participants} registered`}
        actions={
          <Link className="button secondary" to="/hackathons">
            All hackathons
          </Link>
        }
      />
      {h.description && <p>{h.description}</p>}
      <section className="card">
        <h2>Roles in every team</h2>
        <ul className="chips-inline">
          {h.roles.map((r) => (
            <li key={r.id}>
              <Pill>
                {r.name}
                {r.keywords.length > 0 && <small>{r.keywords.join(', ')}</small>}
              </Pill>
            </li>
          ))}
        </ul>
      </section>

      {organizer && (
        <OrganizerPanel detail={d} teams={teams} onDetail={detail.setData} onTeams={setTeams} />
      )}
      {h.status !== 'PUBLISHED' && <Registration detail={d} onChanged={detail.setData} />}
      {!organizer && h.status === 'PUBLISHED' && !h.registered && (
        <section className="card">
          <EmptyState>Teams are announced and registration is over.</EmptyState>
        </section>
      )}
      <Alert>{teamsError}</Alert>
      {teams && teams.teams.length > 0 && <TeamsPanel teams={teams} organizer={organizer} />}
    </>
  );
}
