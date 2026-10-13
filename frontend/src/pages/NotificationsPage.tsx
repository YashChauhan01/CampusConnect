import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Check, CheckCheck } from 'lucide-react';
import { timeAgo } from '../components/NotificationBell';
import { NotificationIcon } from '../components/NotificationIcon';
import { EmptyState, PageHeader } from '../components/ui';
import { useRealtime } from '../realtime/RealtimeContext';

type Filter = 'all' | 'unread';

/** Every notification of the last 30 days, newest first, with read state. */
export function NotificationsPage() {
  const { inbox, markRead, markAllRead } = useRealtime();
  const [filter, setFilter] = useState<Filter>('all');
  const navigate = useNavigate();
  const items = filter === 'unread' ? inbox.items.filter((n) => !n.read) : inbox.items;

  return (
    <>
      <PageHeader
        eyebrow="Inbox"
        title="Notifications"
        subtitle="Match proposals, study requests, messages and hackathon news from the last 30 days."
        actions={
          inbox.unread > 0 ? (
            <button className="button secondary" onClick={() => void markAllRead()}>
              <CheckCheck aria-hidden="true" />
              Mark all read
            </button>
          ) : undefined
        }
      />
      <section className="card">
        <div className="row spread" style={{ marginTop: 0 }}>
          <div className="segmented" role="group" aria-label="Show">
            <button className={filter === 'all' ? 'on' : ''} onClick={() => setFilter('all')} aria-pressed={filter === 'all'}>
              All ({inbox.items.length})
            </button>
            <button className={filter === 'unread' ? 'on' : ''} onClick={() => setFilter('unread')} aria-pressed={filter === 'unread'}>
              Unread ({inbox.unread})
            </button>
          </div>
        </div>
        {items.length === 0 ? (
          <EmptyState>{filter === 'unread' ? 'You are all caught up.' : 'No notifications yet.'}</EmptyState>
        ) : (
          <ul className="notif-list">
            {items.map((n) => (
              <li key={n.id} className={`notif-item ${n.read ? 'read' : 'unread'}`}>
                <button
                  type="button"
                  className="notif-open"
                  onClick={() => {
                    if (!n.read) void markRead(n.id);
                    if (n.link) navigate(n.link);
                  }}
                >
                  <NotificationIcon type={n.type} />
                  <span className="notif-text">
                    <strong>{n.title}</strong>
                    <span>{n.body}</span>
                    <small className="muted">
                      {timeAgo(n.createdAt)} · {new Date(n.createdAt).toLocaleString()}
                    </small>
                  </span>
                </button>
                {n.read ? (
                  <span className="muted small notif-state">Read</span>
                ) : (
                  <button type="button" className="button secondary small" onClick={() => void markRead(n.id)}>
                    <Check aria-hidden="true" />
                    Mark read
                  </button>
                )}
              </li>
            ))}
          </ul>
        )}
      </section>
    </>
  );
}
