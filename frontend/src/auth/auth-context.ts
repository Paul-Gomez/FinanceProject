import { createContext, useContext } from 'react';

import type { User } from '../types/api';

export interface AuthContextValue {
  user: User | null;
  /** true mientras se comprueba si hay una sesión guardada al abrir la app. */
  loading: boolean;
  login: (email: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
}

export const AuthContext = createContext<AuthContextValue | null>(null);

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth se usa fuera de <AuthProvider>');
  return context;
}
