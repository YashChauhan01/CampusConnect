import { beforeEach, describe, expect, it, vi } from 'vitest';
import { mockApi } from '../test/mockApi';
import { ApiError, api, request, setAccessToken, setSessionLostHandler } from './client';

beforeEach(() => {
  setAccessToken(null);
  setSessionLostHandler(null);
});

describe('api client', () => {
  it('sends the bearer token and parses JSON', async () => {
    setAccessToken('abc');
    const { fetchMock } = mockApi({ 'GET /students/me': { body: { fullName: 'Asha' } } });

    const result = await api.get<{ fullName: string }>('/students/me');

    expect(result.fullName).toBe('Asha');
    const init = fetchMock.mock.calls[0][1] as RequestInit;
    expect((init.headers as Record<string, string>).Authorization).toBe('Bearer abc');
    expect(init.credentials).toBe('include');
  });

  it('turns error responses into ApiError with code and message', async () => {
    mockApi({ 'POST /x': { status: 409, body: { error: 'Taken', code: 'CONFLICT' } } });

    const error = await api.post('/x', {}).catch((e: unknown) => e);

    expect(error).toBeInstanceOf(ApiError);
    expect(error).toMatchObject({ status: 409, code: 'CONFLICT', message: 'Taken' });
  });

  it('returns undefined for 204 responses', async () => {
    mockApi({ 'GET /presence/me': { status: 204 } });
    expect(await api.get('/presence/me')).toBeUndefined();
  });

  it('reports network failures in plain language', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('failed')));
    await expect(api.get('/x')).rejects.toMatchObject({ status: 0, code: 'NETWORK' });
  });

  it('refreshes once on 401 and retries with the new token', async () => {
    setAccessToken('old');
    let calls = 0;
    const { calls: recorded } = mockApi({
      'GET /secure': () => (++calls === 1 ? { status: 401, body: { error: 'no' } } : { body: { ok: true } }),
      'POST /auth/refresh': { body: { accessToken: 'new' } },
    });

    const result = await api.get<{ ok: boolean }>('/secure');

    expect(result.ok).toBe(true);
    expect(recorded.map((c) => c.key)).toEqual(['GET /secure', 'POST /auth/refresh', 'GET /secure']);
  });

  it('shares a single refresh between concurrent requests', async () => {
    setAccessToken('old');
    const seen = new Map<string, number>();
    const { calls } = mockApi({
      'GET /a': () => {
        seen.set('a', (seen.get('a') ?? 0) + 1);
        return seen.get('a') === 1 ? { status: 401, body: {} } : { body: 'a' };
      },
      'GET /b': () => {
        seen.set('b', (seen.get('b') ?? 0) + 1);
        return seen.get('b') === 1 ? { status: 401, body: {} } : { body: 'b' };
      },
      'POST /auth/refresh': { body: { accessToken: 'new' } },
    });

    await Promise.all([api.get('/a'), api.get('/b')]);

    expect(calls.filter((c) => c.key === 'POST /auth/refresh')).toHaveLength(1);
  });

  it('signals session loss when the refresh fails', async () => {
    const lost = vi.fn();
    setSessionLostHandler(lost);
    mockApi({
      'GET /secure': { status: 401, body: { error: 'Authentication required' } },
      'POST /auth/refresh': { status: 401, body: {} },
    });

    await expect(api.get('/secure')).rejects.toBeInstanceOf(ApiError);
    expect(lost).toHaveBeenCalledOnce();
  });

  it('does not try to refresh for auth endpoints', async () => {
    const { calls } = mockApi({ 'POST /auth/login': { status: 401, body: { error: 'Invalid email or password' } } });

    await expect(request('/auth/login', { method: 'POST', body: {}, auth: false })).rejects.toThrow('Invalid email or password');
    expect(calls).toHaveLength(1);
  });
});
