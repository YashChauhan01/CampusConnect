import { Client } from '@stomp/stompjs';
import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { getAccessToken, refreshSession } from '../api/client';
import { notifications as notificationApi } from '../api/endpoints';
import type { Inbox, NotificationView, Push } from '../api/types';

type Topic = Push['topic'];

export interface Toast {
  id: string;
  title: string;
  body: string;
  link: string | null;
}

interface RealtimeValue {
  connected: boolean;
  inbox: Inbox;
  toasts: Toast[];
  dismissToast: (id: string) => void;
  markRead: (id: string) => Promise<void>;
  markAllRead: () => Promise<void>;
  /** Counter that changes whenever the server says data on {@code topic} changed; use it as an effect dependency. */
  version: (topic: Topic) => number;
}

const EMPTY_INBOX: Inbox = { items: [], unread: 0 };
const TOAST_MS = 7000;
const MAX_ITEMS = 50;

const RealtimeContext = createContext<RealtimeValue | null>(null);

function brokerUrl(): string {
  const scheme = window.location.protocol === 'https:' ? 'wss' : 'ws';
  return `${scheme}://${window.location.host}/ws`;
}

/**
 * Holds the WebSocket connection, the notification inbox and per-topic change counters. Mount it only while a user
 * is logged in.
 */
export function RealtimeProvider({ children }: { children: ReactNode }) {
  const [connected, setConnected] = useState(false);
  const [inbox, setInbox] = useState<Inbox>(EMPTY_INBOX);
  const [toasts, setToasts] = useState<Toast[]>([]);
  const [versions, setVersions] = useState<Record<string, number>>({});
  const timers = useRef(new Map<string, number>());

  const dismissToast = useCallback((id: string) => {
    setToasts((current) => current.filter((t) => t.id !== id));
    const timer = timers.current.get(id);
    if (timer) {
      window.clearTimeout(timer);
      timers.current.delete(id);
    }
  }, []);

  const handlePush = useCallback(
    (push: Push) => {
      setVersions((v) => ({ ...v, [push.topic]: (v[push.topic] ?? 0) + 1 }));
      const n: NotificationView | null = push.notification;
      if (push.kind === 'NOTIFICATION' && n) {
        setInbox((current) => ({
          items: [n, ...current.items.filter((x) => x.id !== n.id)].slice(0, MAX_ITEMS),
          unread: current.unread + 1,
        }));
        setToasts((current) => [...current, { id: n.id, title: n.title, body: n.body, link: n.link }]);
        timers.current.set(n.id, window.setTimeout(() => dismissToast(n.id), TOAST_MS));
      }
    },
    [dismissToast],
  );

  useEffect(() => {
    let cancelled = false;
    notificationApi
      .inbox()
      .then((loaded) => {
        if (!cancelled) setInbox(loaded);
      })
      .catch(() => undefined);

    const client = new Client({
      brokerURL: brokerUrl(),
      reconnectDelay: 5000,
      heartbeatIncoming: 20000,
      heartbeatOutgoing: 20000,
      // Access tokens are short lived: always connect with a fresh one.
      beforeConnect: async () => {
        const token = getAccessToken() ?? (await refreshSession());
        client.connectHeaders = token ? { Authorization: `Bearer ${token}` } : {};
      },
      onConnect: () => {
        setConnected(true);
        const parse = (body: string) => handlePush(JSON.parse(body) as Push);
        client.subscribe('/user/queue/updates', (message) => parse(message.body));
        client.subscribe('/topic/presence', (message) => parse(message.body));
        // Anything that changed while we were disconnected.
        setVersions((v) => ({ ...v, matches: (v.matches ?? 0) + 1, presence: (v.presence ?? 0) + 1 }));
      },
      onWebSocketClose: () => setConnected(false),
      onStompError: () => setConnected(false),
    });
    client.activate();

    const pendingTimers = timers.current;
    return () => {
      cancelled = true;
      void client.deactivate();
      pendingTimers.forEach((t) => window.clearTimeout(t));
      pendingTimers.clear();
    };
  }, [handlePush]);

  const markRead = useCallback(async (id: string) => {
    await notificationApi.markRead(id);
    setInbox((current) => {
      const target = current.items.find((n) => n.id === id);
      if (!target || target.read) return current;
      return {
        items: current.items.map((n) => (n.id === id ? { ...n, read: true } : n)),
        unread: Math.max(0, current.unread - 1),
      };
    });
  }, []);

  const markAllRead = useCallback(async () => {
    await notificationApi.markAllRead();
    setInbox((current) => ({ items: current.items.map((n) => ({ ...n, read: true })), unread: 0 }));
  }, []);

  const version = useCallback((topic: Topic) => versions[topic] ?? 0, [versions]);

  const value = useMemo(
    () => ({ connected, inbox, toasts, dismissToast, markRead, markAllRead, version }),
    [connected, inbox, toasts, dismissToast, markRead, markAllRead, version],
  );
  return <RealtimeContext.Provider value={value}>{children}</RealtimeContext.Provider>;
}

export function useRealtime(): RealtimeValue {
  const ctx = useContext(RealtimeContext);
  if (!ctx) {
    throw new Error('useRealtime must be used inside <RealtimeProvider>');
  }
  return ctx;
}
