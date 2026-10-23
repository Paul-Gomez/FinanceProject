import { useQueryClient } from '@tanstack/react-query';
import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';

import { api, SESSION_EXPIRED_EVENT } from '../lib/api';
import { clearTokens, getAccessToken, setTokens } from '../lib/tokens';
import type { TokenResponse, User } from '../types/api';
import { AuthContext } from './auth-context';

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(() => getAccessToken() !== null);

  // Al abrir la app con tokens guardados se recupera el usuario (y, si el access
  // token caducó, api() lo renueva con el refresh token sin que se note).
  useEffect(() => {
    if (!getAccessToken()) return;
    api<User>('/api/v1/users/me')
      .then(setUser)
      .catch(() => clearTokens())
      .finally(() => setLoading(false));
  }, []);

  // Si la sesión no se puede renovar a mitad de uso, se vuelve al login y se
  // olvidan los datos en caché del usuario anterior.
  useEffect(() => {
    const onExpired = () => {
      setUser(null);
      queryClient.clear();
    };
    window.addEventListener(SESSION_EXPIRED_EVENT, onExpired);
    return () => window.removeEventListener(SESSION_EXPIRED_EVENT, onExpired);
  }, [queryClient]);

  const login = useCallback(async (email: string, password: string) => {
    const tokens = await api<TokenResponse>('/api/v1/auth/login', {
      method: 'POST',
      body: { email, password, deviceName: 'Navegador web' },
      auth: false,
    });
    setTokens(tokens.accessToken, tokens.refreshToken);
    setUser(await api<User>('/api/v1/users/me'));
  }, []);

  const logout = useCallback(async () => {
    // Revoca la sesión en el servidor; si falla (sin red, token caducado) se
    // cierra igualmente en local.
    await api('/api/v1/auth/logout', { method: 'POST' }).catch(() => undefined);
    clearTokens();
    queryClient.clear();
    setUser(null);
  }, [queryClient]);

  const value = useMemo(() => ({ user, loading, login, logout }), [user, loading, login, logout]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
