import { Link } from 'react-router-dom';

import { usePageTitle } from '../components/ui/page-title';

export function NotFoundPage() {
  usePageTitle('Página no encontrada');
  return (
    <div className="py-16 text-center">
      <h1 className="text-2xl font-semibold tracking-tight">Página no encontrada</h1>
      <p className="mt-2 text-sm text-muted">La dirección no existe o ha cambiado.</p>
      <Link
        to="/"
        className="mt-6 inline-block text-sm font-medium text-brand underline-offset-2 hover:underline"
      >
        Volver al resumen
      </Link>
    </div>
  );
}
