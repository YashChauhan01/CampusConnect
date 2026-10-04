import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { auth as authApi, profile as profileApi } from '../api/endpoints';
import { refreshSession, setAccessToken, setSessionLostHandler } from '../api/client';
import type { Profile } from '../api/types';

type Status = 'loading' | 'anonymous' | 'authenticated';

interface AuthContextValue {
  status: Status;
  profile: Profile | null;
  login: (email: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
  /** Replaces the cached profile, e.g. after the profile page saved changes. */
  setProfile: (profile: Profile) => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [status, setStatus] = useState<Status>('loading');
  const [profile, setProfile] = useState<Profile | null>(null);

  const clear = useCallback(() => {
    setAccessToken(null);
    setProfile(null);
    setStatus('anonymous');
  }, []);

  // Restore the session after a page reload using the HttpOnly refresh cookie.
  useEffect(() => {
    let cancelled = false;
    (async () => {
      const token = await refreshSession();
      if (!token) {
        if (!cancelled) setStatus('anonymous');
        return;
      }
      try {
        const me = await profileApi.me();
        if (!cancelled) {
          setProfile(me);
          setStatus('authenticated');
        }
      } catch {
        if (!cancelled) clear();
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [clear]);

  useEffect(() => {
    setSessionLostHandler(clear);
    return () => setSessionLostHandler(null);
  }, [clear]);

  const login = useCallback(async (email: string, password: string) => {
    const { accessToken } = await authApi.login(email, password);
    setAccessToken(accessToken);
    setProfile(await profileApi.me());
    setStatus('authenticated');
  }, []);

  const logout = useCallback(async () => {
    try {
      await authApi.logout();
    } finally {
      clear();
    }
  }, [clear]);

  const value = useMemo(
    () => ({ status, profile, login, logout, setProfile }),
    [status, profile, login, logout],
  );
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error('useAuth must be used inside <AuthProvider>');
  }
  return ctx;
}
