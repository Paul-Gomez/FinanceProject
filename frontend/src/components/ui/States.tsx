import type { ReactNode } from 'react';

import { errorMessage } from '../../lib/errors';
import { Alert } from './Alert';
import { Button } from './Button';

/** Lista vacía: qué pasa y, si tiene sentido, cómo seguir. */
export function EmptyState({ title, children }: { title: string; children?: ReactNode }) {
  return (
    <div className="rounded-xl border border-dashed border-line px-6 py-12 text-center">
      <p className="font-medium">{title}</p>
      {children && <div className="mt-2 text-sm text-muted">{children}</div>}
    </div>
  );
}

export function LoadingState({ label = 'Cargando…' }: { label?: string }) {
  return (
    <p role="status" className="py-12 text-center text-sm text-muted">
      {label}
    </p>
  );
}

export function ErrorState({ error, onRetry }: { error: unknown; onRetry: () => void }) {
  return (
    <div className="space-y-3">
      <Alert>{errorMessage(error)}</Alert>
      <Button variant="secondary" onClick={onRetry}>
        Reintentar
      </Button>
    </div>
  );
}
