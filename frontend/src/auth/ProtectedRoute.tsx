import { Navigate, Outlet, useLocation } from 'react-router-dom';

import { useAuth } from './auth-context';

export function ProtectedRoute() {
  const { user, loading } = useAuth();
  const location = useLocation();

  if (loading) {
    return (
      <div
        className="flex min-h-screen items-center justify-center text-sm text-muted"
        role="status"
      >
        Cargando…
      </div>
    );
  }

  if (!user) {
    // Se recuerda a dónde iba para volver ahí después del login.
    return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />;
  }

  return <Outlet />;
}
