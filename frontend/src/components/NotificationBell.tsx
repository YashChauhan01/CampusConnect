import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useRealtime } from '../realtime/RealtimeContext';

function timeAgo(iso: string): string {
  const seconds = Math.max(0, Math.floor((Date.now() - new Date(iso).getTime()) / 1000));
  if (seconds < 60) return 'just now';
  if (seconds < 3600) return `${Math.floor(seconds / 60)} min ago`;
  if (seconds < 86400) return `${Math.floor(seconds / 3600)} h ago`;
  return `${Math.floor(seconds / 86400)} d ago`;
}

export function NotificationBell() {
  const { inbox, connected, markRead, markAllRead } = useRealtime();
  const [open, setOpen] = useState(false);
  const navigate = useNavigate();
  const container = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;
    const close = (event: MouseEvent) => {
      if (!container.current?.contains(event.target as Node)) setOpen(false);
    };
    document.addEventListener('mousedown', close);
    return () => document.removeEventListener('mousedown', close);
  }, [open]);

  return (
    <div className="bell" ref={container}>
      <button
        type="button"
        className="icon-button"
        aria-label={`Notifications${inbox.unread ? `, ${inbox.unread} unread` : ''}`}
        aria-expanded={open}
        onClick={() => setOpen((o) => !o)}
      >
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true">
          <path d="M18 8a6 6 0 0 0-12 0c0 7-3 9-3 9h18s-3-2-3-9M13.7 21a2 2 0 0 1-3.4 0" />
        </svg>
        {inbox.unread > 0 && <span className="badge">{inbox.unread > 9 ? '9+' : inbox.unread}</span>}
        <span className={`dot ${connected ? 'dot-on' : 'dot-off'}`} title={connected ? 'Live' : 'Reconnecting…'} />
      </button>
      {open && (
        <div className="dropdown" role="dialog" aria-label="Notifications">
          <div className="dropdown-head">
            <strong>Notifications</strong>
            {inbox.unread > 0 && (
              <button type="button" className="link" onClick={() => void markAllRead()}>
                Mark all read
              </button>
            )}
          </div>
          {inbox.items.length === 0 && <p className="empty">Nothing yet.</p>}
          <ul>
            {inbox.items.map((n) => (
              <li key={n.id} className={n.read ? '' : 'unread'}>
                <button
                  type="button"
                  onClick={() => {
                    void markRead(n.id);
                    setOpen(false);
                    if (n.link) navigate(n.link);
                  }}
                >
                  <strong>{n.title}</strong>
                  <span>{n.body}</span>
                  <small className="muted">{timeAgo(n.createdAt)}</small>
                </button>
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}
