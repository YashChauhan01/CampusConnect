import { vi } from 'vitest';

type Handler = (body: unknown, url: string) => { status?: number; body?: unknown };

/**
 * Stubs global fetch with a route table keyed by "METHOD /path" (path relative to /api/v1). Unmatched requests fail
 * the test loudly. Returns the list of recorded calls.
 */
export function mockApi(routes: Record<string, Handler | { status?: number; body?: unknown }>) {
  const calls: { key: string; body: unknown }[] = [];
  const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input).replace('/api/v1', '');
    const key = `${init?.method ?? 'GET'} ${url}`;
    const body = init?.body ? JSON.parse(String(init.body)) : undefined;
    calls.push({ key, body });
    const route = routes[key];
    if (!route) {
      throw new Error(`Unexpected request: ${key}`);
    }
    const result = typeof route === 'function' ? route(body, url) : route;
    const status = result.status ?? 200;
    return new Response(status === 204 || result.body === undefined ? null : JSON.stringify(result.body), { status });
  });
  vi.stubGlobal('fetch', fetchMock);
  return { calls, fetchMock };
}
