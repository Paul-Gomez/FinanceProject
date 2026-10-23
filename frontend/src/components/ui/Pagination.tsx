import { Button } from './Button';

interface PaginationProps {
  /** Página actual, empezando en 0 como en Spring Data. */
  page: number;
  totalPages: number;
  totalElements: number;
  onChange: (page: number) => void;
}

export function Pagination({ page, totalPages, totalElements, onChange }: PaginationProps) {
  return (
    <nav
      aria-label="Paginación"
      className="mt-4 flex flex-wrap items-center justify-between gap-3 text-sm text-muted"
    >
      <p>
        {totalElements} {totalElements === 1 ? 'movimiento' : 'movimientos'}
      </p>
      {totalPages > 1 && (
        <div className="flex items-center gap-3">
          <Button variant="secondary" disabled={page <= 0} onClick={() => onChange(page - 1)}>
            Anterior
          </Button>
          <span className="tabular">
            {page + 1} / {totalPages}
          </span>
          <Button
            variant="secondary"
            disabled={page >= totalPages - 1}
            onClick={() => onChange(page + 1)}
          >
            Siguiente
          </Button>
        </div>
      )}
    </nav>
  );
}
