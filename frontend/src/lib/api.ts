import type { ApiErrorBody, TokenResponse } from '../types/api';
import { clearTokens, getAccessToken, getRefreshToken, setTokens } from './tokens';

const BASE_URL = import.meta.env.VITE_API_URL ?? '';

/** Se lanza cuando la sesión ya no se puede renovar y hay que volver al login. */
export const SESSION_EXPIRED_EVENT = 'auth:session-expired';

export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  /** Errores por campo de las validaciones de Bean Validation ("amount: debe ser mayor que 0"). */
  readonly fieldErrors: string[];

  constructor(status: number, code: string, message: string, fieldErrors: string[] = []) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.fieldErrors = fieldErrors;
  }
}

type QueryValue = string | number | boolean | undefined | null;

interface RequestOptions {
  method?: string;
  body?: unknown;
  query?: Record<string, QueryValue>;
  headers?: Record<string, string>;
  /** false en login, registro y refresh: no llevan token ni reintentan con un 401. */
  auth?: boolean;
}

export function buildUrl(path: string, query?: Record<string, QueryValue>): string {
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(query ?? {})) {
    if (value !== undefined && value !== null && value !== '') params.set(key, String(value));
  }
  const queryString = params.toString();
  return `${path}${queryString ? `?${queryString}` : ''}`;
}

// El backend rota el refresh token: cada uno sirve una sola vez. Si varias
// peticiones reciben un 401 a la vez y cada una renovara por su cuenta, la
// segunda usaría un token ya gastado y cerraría la sesión. Por eso comparten
// la misma renovación en curso.
let refreshInFlight: Promise<boolean> | null = null;

function refreshTokens(): Promise<boolean> {
  const refreshToken = getRefreshToken();
  if (!refreshToken) return Promise.resolve(false);

  refreshInFlight ??= fetch(`${BASE_URL}/api/v1/auth/refresh`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ refreshToken }),
  })
    .then(async (res) => {
      if (!res.ok) return false;
      const tokens = (await res.json()) as TokenResponse;
      setTokens(tokens.accessToken, tokens.refreshToken);
      return true;
    })
    .catch(() => false)
    .finally(() => {
      refreshInFlight = null;
    });

  return refreshInFlight;
}

async function authorizedFetch(url: string, init: RequestInit, retry = true): Promise<Response> {
  const headers = new Headers(init.headers);
  const token = getAccessToken();
  if (token) headers.set('Authorization', `Bearer ${token}`);

  const res = await fetch(`${BASE_URL}${url}`, { ...init, headers });

  if (res.status === 401 && retry) {
    if (await refreshTokens()) return authorizedFetch(url, init, false);
    clearTokens();
    window.dispatchEvent(new Event(SESSION_EXPIRED_EVENT));
  }
  return res;
}

export async function api<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method = 'GET', body, query, headers = {}, auth = true } = options;
  const url = buildUrl(path, query);
  const init: RequestInit = {
    method,
    headers: body === undefined ? headers : { 'Content-Type': 'application/json', ...headers },
    body: body === undefined ? undefined : JSON.stringify(body),
  };

  const res = auth ? await authorizedFetch(url, init) : await fetch(`${BASE_URL}${url}`, init);

  if (res.status === 204) return undefined as T;

  const content: unknown = await res.json().catch(() => null);

  if (!res.ok) {
    const error = content as Partial<ApiErrorBody> | null;
    throw new ApiError(
      res.status,
      error?.code ?? 'HTTP_' + res.status,
      error?.message ?? res.statusText,
      error?.errors ?? [],
    );
  }
  return content as T;
}
