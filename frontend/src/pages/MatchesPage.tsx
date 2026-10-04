import { Link } from 'react-router-dom';
import { matching } from '../api/endpoints';
import type { MatchStatus, MatchView } from '../api/types';
import { Alert, EmptyState, PageHeader, Pill, ScoreBadge, ScoreBreakdown, Spinner } from '../components/ui';
import { useAction, useCountdown, useLoad } from '../hooks';
import { useRealtime } from '../realtime/RealtimeContext';

const STATUS_TEXT: Record<MatchStatus, string> = {
  PROPOSED: 'Waiting for responses',
  ACCEPTED: 'Confirmed',
  DECLINED: 'Declined',
  EXPIRED: 'Expired',
  COMPLETED: 'Finished',
};

function CurrentMatch({ match, onChanged }: { match: MatchView; onChanged: () => void }) {
  const { pending, error, run } = useAction();
  const remaining = useCountdown(match.status === 'PROPOSED' ? match.expiresAt : undefined);

  const act = (call: () => Promise<unknown>) =>
    run(async () => {
      await call();
      onChanged();
    });

  return (
    <section className="card match">
      <div className="row spread">
        <h2>{match.status === 'ACCEPTED' ? 'You are matched!' : 'New study partner'}</h2>
        <ScoreBadge score={match.score} />
      </div>
      <div className="person">
        <div className="avatar large" aria-hidden="true">
          {match.partner.name.charAt(0)}
        </div>
        <div>
          <strong className="big">{match.partner.name}</strong>
          {match.partner.zone && <span className="muted"> · now at {match.partner.zone}</span>}
          {match.partner.bio && <p className="quote">{match.partner.bio}</p>}
          <div className="chips-inline">
            {match.sharedSubjects.map((s) => (
              <Pill key={s}>{s}</Pill>
            ))}
          </div>
        </div>
      </div>
      <ScoreBreakdown breakdown={match.breakdown} />

      {match.status === 'PROPOSED' && (
        <>
          <p className="muted">
            {match.youAccepted
              ? `Waiting for ${match.partner.name} to accept`
              : match.partnerAccepted
                ? `${match.partner.name} already accepted`
                : 'Both of you need to accept before contact details are shared'}
            {remaining && (
              <>
                {' '}
                · expires in <strong>{remaining}</strong>
              </>
            )}
          </p>
          <div className="row">
            {!match.youAccepted && (
              <button className="button" disabled={pending} onClick={() => void act(() => matching.accept(match.id))}>
                Accept
              </button>
            )}
            <button
              className="button secondary"
              disabled={pending}
              onClick={() => void act(() => matching.decline(match.id))}
            >
              Not now
            </button>
          </div>
        </>
      )}
      {match.status === 'ACCEPTED' && (
        <>
          <Alert kind="success">
            Meet at {match.partner.zone ?? 'a spot you both like'}. You can reach {match.partner.name} at{' '}
            <a href={`mailto:${match.partner.email}`}>{match.partner.email}</a>.
          </Alert>
          <button
            className="button secondary"
            disabled={pending}
            onClick={() => void act(() => matching.complete(match.id))}
          >
            We are done studying
          </button>
        </>
      )}
      <Alert>{error}</Alert>
    </section>
  );
}

export function MatchesPage() {
  const { version } = useRealtime();
  const matchVersion = version('matches');
  const current = useLoad(() => matching.current(), [matchVersion]);
  const history = useLoad(() => matching.history(), [matchVersion]);

  const past = (history.data ?? []).filter((m) => m.id !== current.data?.id);

  return (
    <>
      <PageHeader
        title="Study matches"
        subtitle="We pair students who can learn from each other, are close by, and are free right now."
        actions={
          <Link className="button" to="/campus">
            Go to campus
          </Link>
        }
      />
      <Alert>{current.error}</Alert>
      {current.loading && !current.data ? (
        <Spinner />
      ) : current.data ? (
        <CurrentMatch
          match={current.data}
          onChanged={() => {
            current.reload();
            history.reload();
          }}
        />
      ) : (
        <section className="card">
          <EmptyState>
            No active match. Check in as <Link to="/campus">available on campus</Link> and we will find you someone.
          </EmptyState>
        </section>
      )}

      <section className="card">
        <h2>History</h2>
        {past.length === 0 ? (
          <EmptyState>Your past matches will show up here.</EmptyState>
        ) : (
          <ul className="list">
            {past.map((m) => (
              <li key={m.id}>
                <ScoreBadge score={m.score} />
                <div>
                  <strong>{m.partner.name}</strong>
                  <span className="muted">
                    {' '}
                    · {STATUS_TEXT[m.status]} · {new Date(m.createdAt).toLocaleString()}
                  </span>
                  <div className="chips-inline">
                    {m.sharedSubjects.map((s) => (
                      <Pill key={s}>{s}</Pill>
                    ))}
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
