import { useCallback, useEffect, useRef, useState } from 'react';
import { ApiError } from './api/client';

export function messageOf(error: unknown): string {
  if (error instanceof ApiError) return error.message;
  if (error instanceof Error) return error.message;
  return 'Something went wrong';
}

interface LoadState<T> {
  data: T | undefined;
  error: string | null;
  loading: boolean;
}

/**
 * Runs an async loader on mount and whenever {@code deps} change, keeping the previous data on screen while
 * reloading. Results of superseded requests are ignored.
 */
export function useLoad<T>(loader: () => Promise<T>, deps: unknown[]) {
  const [state, setState] = useState<LoadState<T>>({ data: undefined, error: null, loading: true });
  const [tick, setTick] = useState(0);
  const loaderRef = useRef(loader);
  loaderRef.current = loader;

  useEffect(() => {
    let cancelled = false;
    setState((s) => ({ ...s, loading: true }));
    loaderRef.current().then(
      (data) => {
        if (!cancelled) setState({ data, error: null, loading: false });
      },
      (error: unknown) => {
        if (!cancelled) setState((s) => ({ ...s, error: messageOf(error), loading: false }));
      },
    );
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, tick]);

  const reload = useCallback(() => setTick((t) => t + 1), []);
  const setData = useCallback((data: T) => setState({ data, error: null, loading: false }), []);
  return { ...state, reload, setData };
}

/** Remaining time until {@code iso} as {@code m:ss}, ticking every second; null once expired. */
export function useCountdown(iso: string | undefined): string | null {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, []);
  if (!iso) return null;
  const remaining = Math.max(0, Math.floor((new Date(iso).getTime() - now) / 1000));
  if (remaining === 0) return null;
  const minutes = Math.floor(remaining / 60);
  const seconds = String(remaining % 60).padStart(2, '0');
  return `${minutes}:${seconds}`;
}

/** Runs an async action, exposing pending/error state for the buttons and messages that trigger it. */
export function useAction() {
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const run = useCallback(async <T,>(action: () => Promise<T>): Promise<T | undefined> => {
    setPending(true);
    setError(null);
    try {
      return await action();
    } catch (e) {
      setError(messageOf(e));
      return undefined;
    } finally {
      setPending(false);
    }
  }, []);
  return { pending, error, run, clearError: () => setError(null) };
}
