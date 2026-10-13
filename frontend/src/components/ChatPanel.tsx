import { useEffect, useRef, useState, type FormEvent, type KeyboardEvent } from 'react';
import { MessageCircle, SendHorizontal } from 'lucide-react';
import { matching } from '../api/endpoints';
import type { ChatMessage, MatchView } from '../api/types';
import { messageOf, useLoad } from '../hooks';
import { useRealtime } from '../realtime/RealtimeContext';
import { initials } from './Layout';

const POLL_MS = 15000;

function time(iso: string): string {
  return new Date(iso).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
}

/** Chat between the two students of a confirmed match; new messages arrive live over the WebSocket. */
export function ChatPanel({ match }: { match: MatchView }) {
  const { version } = useRealtime();
  const chatVersion = version('chat');
  const messages = useLoad(() => matching.messages(match.id), [match.id, chatVersion]);
  const [draft, setDraft] = useState('');
  const [sending, setSending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [extra, setExtra] = useState<ChatMessage[]>([]);
  const list = useRef<HTMLDivElement>(null);
  const open = match.status === 'ACCEPTED';

  // Fallback in case a live update was missed (e.g. the socket reconnected).
  const { reload } = messages;
  useEffect(() => {
    if (!open) return;
    const timer = window.setInterval(reload, POLL_MS);
    return () => window.clearInterval(timer);
  }, [open, reload]);

  const loaded = messages.data ?? [];
  const all = [...loaded, ...extra.filter((m) => !loaded.some((x) => x.id === m.id))];

  useEffect(() => {
    const el = list.current;
    if (el) el.scrollTop = el.scrollHeight; // smooth via CSS scroll-behavior
  }, [all.length]);

  async function send(text: string) {
    const body = text.trim();
    if (!body || sending) return;
    setSending(true);
    setError(null);
    try {
      const sent = await matching.send(match.id, body);
      setExtra((cur) => [...cur, sent]);
      setDraft('');
    } catch (e) {
      setError(messageOf(e));
    } finally {
      setSending(false);
    }
  }

  const onSubmit = (e: FormEvent) => {
    e.preventDefault();
    void send(draft);
  };
  const onKey = (e: KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      void send(draft);
    }
  };

  const quick = [
    match.partner.zone ? `Meet at ${match.partner.zone}?` : 'Where should we meet?',
    'On my way!',
    'Running 5 minutes late',
    'Which topic should we start with?',
  ];

  return (
    <section className="chat" aria-label={`Chat with ${match.partner.name}`}>
      <header className="chat-head">
        <span className="avatar-badge" aria-hidden="true">
          {initials(match.partner.name)}
        </span>
        <div>
          <strong>{match.partner.name}</strong>
          <small className="muted">{match.partner.zone ? `Now at ${match.partner.zone}` : 'Plan where and when to meet'}</small>
        </div>
        <MessageCircle aria-hidden="true" className="chat-head-icon" />
      </header>
      <div className="chat-log" ref={list} role="log" aria-live="polite">
        {messages.loading && !messages.data ? (
          <p className="muted small chat-empty">Loading messages…</p>
        ) : all.length === 0 ? (
          <p className="muted small chat-empty">Say hello and agree where to meet. Only the two of you can see this chat.</p>
        ) : (
          all.map((m) => (
            <div key={m.id} className={`bubble ${m.mine ? 'mine' : 'theirs'}`}>
              <span>{m.body}</span>
              <time dateTime={m.createdAt}>{time(m.createdAt)}</time>
            </div>
          ))
        )}
      </div>
      {open ? (
        <>
          <div className="quick-replies" aria-label="Quick replies">
            {quick.map((q) => (
              <button key={q} type="button" className="pill quick" onClick={() => void send(q)} disabled={sending}>
                {q}
              </button>
            ))}
          </div>
          <form className="chat-input" onSubmit={onSubmit}>
            <textarea
              rows={1}
              value={draft}
              onChange={(e) => setDraft(e.target.value)}
              onKeyDown={onKey}
              placeholder={`Message ${match.partner.name.split(' ')[0]}…`}
              aria-label="Message"
              maxLength={1000}
            />
            <button className="button" disabled={sending || !draft.trim()} aria-label="Send message">
              <SendHorizontal aria-hidden="true" />
            </button>
          </form>
        </>
      ) : (
        <p className="muted small chat-empty">This session has ended; the chat is read-only.</p>
      )}
      {error && <p className="error-inline" role="alert">{error}</p>}
    </section>
  );
}
