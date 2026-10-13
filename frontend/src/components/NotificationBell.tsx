import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Bell, Check, CheckCheck } from 'lucide-react';
import type { NotificationView } from '../api/types';
import { useRealtime } from '../realtime/RealtimeContext';
import { NotificationIcon } from './NotificationIcon';

export function timeAgo(iso: string): string {
  const seconds = Math.max(0, Math.floor((Date.now() - new Date(iso).getTime()) / 1000));
  if (seconds < 60) return 'just now';
  if (seconds < 3600) return `${Math.floor(seconds / 60)} min ago`;
  if (seconds < 86400) return `${Math.floor(seconds / 3600)} h ago`;
  return `${Math.floor(seconds / 86400)} d ago`;
}

/** Bell with the unread notifications: marking one read removes it from this list (it stays on the full page). */
export function NotificationBell() {
  const { inbox, connected, markRead, markAllRead } = useRealtime();
  const [open, setOpen] = useState(false);
  const navigate = useNavigate();
  const container = useRef<HTMLDivElement>(null);
  const unread = inbox.items.filter((n) => !n.read);

  useEffect(() => {
    if (!open) return;
    const close = (event: MouseEvent) => {
      if (!container.current?.contains(event.target as Node)) setOpen(false);
    };
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && setOpen(false);
    document.addEventListener('mousedown', close);
    window.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('mousedown', close);
      window.removeEventListener('keydown', onKey);
    };
  }, [open]);

  const openItem = (n: NotificationView) => {
    void markRead(n.id);
    setOpen(false);
    if (n.link) navigate(n.link);
  };

  return (
    <div className="bell" ref={container}>
      <button
        type="button"
        className="icon-button"
        aria-label={`Notifications${inbox.unread ? `, ${inbox.unread} unread` : ''}`}
        aria-expanded={open}
        onClick={() => setOpen((o) => !o)}
      >
        <Bell aria-hidden="true" />
        {inbox.unread > 0 && <span className="badge">{inbox.unread > 9 ? '9+' : inbox.unread}</span>}
        <span className={`dot ${connected ? 'dot-on' : 'dot-off'}`} title={connected ? 'Live' : 'Reconnecting…'} />
      </button>
      {open && (
        <div className="dropdown" role="dialog" aria-label="Unread notifications">
          <div className="dropdown-head">
            <strong>Unread{inbox.unread ? ` (${inbox.unread})` : ''}</strong>
            {unread.length > 0 && (
              <button type="button" className="link" onClick={() => void markAllRead()}>
                <CheckCheck aria-hidden="true" /> Mark all read
              </button>
            )}
          </div>
          {unread.length === 0 ? (
            <p className="empty">You are all caught up.</p>
          ) : (
            <ul>
              {unread.map((n) => (
                <li key={n.id} className="notif-item unread">
                  <button type="button" className="notif-open" onClick={() => openItem(n)}>
                    <NotificationIcon type={n.type} />
                    <span className="notif-text">
                      <strong>{n.title}</strong>
                      <span>{n.body}</span>
                      <small className="muted">{timeAgo(n.createdAt)}</small>
                    </span>
                  </button>
                  <button
                    type="button"
                    className="icon-button plain"
                    aria-label={`Mark "${n.title}" as read`}
                    title="Mark as read"
                    onClick={() => void markRead(n.id)}
                  >
                    <Check />
                  </button>
                </li>
              ))}
            </ul>
          )}
          <Link to="/notifications" className="dropdown-foot" onClick={() => setOpen(false)}>
            View all notifications
          </Link>
        </div>
      )}
    </div>
  );
}
