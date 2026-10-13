import { useState } from 'react';
import { Check, Send } from 'lucide-react';
import { matching } from '../api/endpoints';
import type { MatchView, Suggestion } from '../api/types';
import { messageOf } from '../hooks';
import { initials } from './Layout';
import { Pill } from './ui';

/**
 * Whether the student can send a direct request right now, and why not. A student can only have one live match or
 * request at a time.
 */
export function connectBlocker(current: MatchView | undefined, checkedIn: boolean): string | null {
  if (!checkedIn) return 'Check in on campus to send requests';
  if (current?.status === 'ACCEPTED') return `You are matched with ${current.partner.name}`;
  if (current?.status === 'PROPOSED') {
    return current.requestedByYou ? `Request pending with ${current.partner.name}` : 'Answer your pending match first';
  }
  return null;
}

/** "Connect" button that sends a study request to a recommended classmate. */
export function ConnectButton({ suggestion, blocker, onSent, compact = false }: {
  suggestion: Suggestion;
  blocker: string | null;
  onSent: (match: MatchView) => void;
  compact?: boolean;
}) {
  const [state, setState] = useState<'idle' | 'sending' | 'sent'>('idle');
  const [error, setError] = useState<string | null>(null);

  async function send() {
    setState('sending');
    setError(null);
    try {
      const match = await matching.request(suggestion.studentId);
      setState('sent');
      onSent(match);
    } catch (e) {
      setState('idle');
      setError(messageOf(e));
    }
  }

  if (state === 'sent') {
    return (
      <span className="sent-chip" role="status">
        <Check aria-hidden="true" /> Request sent
      </span>
    );
  }
  return (
    <span className="connect-wrap">
      <button
        type="button"
        className={`button ${compact ? 'small' : ''}`}
        onClick={send}
        disabled={state === 'sending' || Boolean(blocker)}
        title={blocker ?? `Ask ${suggestion.name} to study together`}
        aria-label={`Connect with ${suggestion.name}`}
      >
        <Send aria-hidden="true" />
        {state === 'sending' ? 'Sending…' : 'Connect'}
      </button>
      {error && <small className="error-inline">{error}</small>}
    </span>
  );
}

/** One recommended classmate as a row (used in lists and the "view all" dialog). */
export function SuggestionRow({ suggestion, blocker, onSent }: {
  suggestion: Suggestion;
  blocker: string | null;
  onSent: (match: MatchView) => void;
}) {
  return (
    <li className="suggestion-row">
      <span className="avatar-badge" aria-hidden="true">
        {initials(suggestion.name)}
      </span>
      <div className="suggestion-text">
        <strong>{suggestion.name}</strong>
        <small className="muted">
          {suggestion.zone} · {suggestion.score}% match
        </small>
        <div className="chips-inline">
          {suggestion.sharedSubjects.map((n) => (
            <Pill key={n}>{n}</Pill>
          ))}
        </div>
      </div>
      <ConnectButton suggestion={suggestion} blocker={blocker} onSent={onSent} compact />
    </li>
  );
}
