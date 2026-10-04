const API_BASE = '/api/v1';

/** Error returned by the API (or a network failure, status 0). */
export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly fields: Record<string, string>;

  constructor(status: number, message: string, code = 'ERROR', fields: Record<string, string> = {}) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.fields = fields;
  }
}

/** The access token lives only in memory; the refresh token is an HttpOnly cookie the page cannot read. */
let accessToken: string | null = null;
let refreshInFlight: Promise<string | null> | null = null;
let onSessionLost: (() => void) | null = null;

export function setAccessToken(token: string | null): void {
  accessToken = token;
}

export function getAccessToken(): string | null {
  return accessToken;
}

/** Registers the callback invoked when a refresh fails, i.e. the user must log in again. */
export function setSessionLostHandler(handler: (() => void) | null): void {
  onSessionLost = handler;
}

interface RequestOptions {
  method?: string;
  body?: unknown;
  /** Skip the automatic refresh-and-retry on 401 (used by the auth endpoints themselves). */
  auth?: boolean;
}

async function parse<T>(response: Response): Promise<T> {
  if (response.status === 204) {
    return undefined as T;
  }
  const text = await response.text();
  const data = text ? JSON.parse(text) : undefined;
  if (!response.ok) {
    throw new ApiError(response.status, data?.error ?? 'Request failed', data?.code, data?.fields);
  }
  return data as T;
}

async function send(path: string, { method = 'GET', body }: RequestOptions): Promise<Response> {
  const headers: Record<string, string> = {};
  if (body !== undefined) {
    headers['Content-Type'] = 'application/json';
  }
  if (accessToken) {
    headers.Authorization = `Bearer ${accessToken}`;
  }
  try {
    return await fetch(API_BASE + path, {
      method,
      headers,
      credentials: 'include',
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch {
    throw new ApiError(0, 'Cannot reach the server. Check your connection and try again.', 'NETWORK');
  }
}

/**
 * Exchanges the refresh cookie for a new access token. Concurrent callers share one request, because the server
 * rotates the refresh token on every use.
 */
export function refreshSession(): Promise<string | null> {
  if (!refreshInFlight) {
    refreshInFlight = (async () => {
      try {
        const response = await send('/auth/refresh', { method: 'POST' });
        if (!response.ok) {
          accessToken = null;
          return null;
        }
        const data = await parse<{ accessToken: string }>(response);
        accessToken = data.accessToken;
        return accessToken;
      } catch {
        return null;
      } finally {
        refreshInFlight = null;
      }
    })();
  }
  return refreshInFlight;
}

export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  let response = await send(path, options);
  if (response.status === 401 && options.auth !== false) {
    const token = await refreshSession();
    if (token) {
      response = await send(path, options);
    } else {
      onSessionLost?.();
    }
  }
  return parse<T>(response);
}

export const api = {
  get: <T>(path: string) => request<T>(path),
  post: <T>(path: string, body?: unknown) => request<T>(path, { method: 'POST', body }),
  put: <T>(path: string, body?: unknown) => request<T>(path, { method: 'PUT', body }),
  delete: <T>(path: string) => request<T>(path, { method: 'DELETE' }),
};

/** Auth endpoints never trigger the refresh-and-retry loop. */
export const authApi = {
  post: <T>(path: string, body?: unknown) => request<T>(path, { method: 'POST', body, auth: false }),
};
