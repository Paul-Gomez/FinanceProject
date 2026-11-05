import { useMemo, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';

import {
  useAccounts,
  useCategories,
  useTransactions,
  useVoidTransaction,
  type TransactionFilters,
} from '../../api/hooks';
import { Amount } from '../../components/Amount';
import { Button } from '../../components/ui/Button';
import { ConfirmDialog } from '../../components/ui/ConfirmDialog';
import { Input, Select } from '../../components/ui/Field';
import { useNotify } from '../../components/ui/notify';
import { PageHeader } from '../../components/ui/PageHeader';
import { Pagination } from '../../components/ui/Pagination';
import { EmptyState, ErrorState, LoadingState } from '../../components/ui/States';
import { errorMessage } from '../../lib/errors';
import { formatDate, formatMoney } from '../../lib/format';
import { TRANSACTION_TYPE_LABELS } from '../../lib/labels';
import type { Category, Transaction, TransactionType } from '../../types/api';
import { TransactionModal, TransferModal } from './TransactionForms';

const NO_FILTERS = {
  accountId: '',
  categoryId: '',
  type: '',
  from: '',
  to: '',
  q: '',
  includeVoided: '',
};

/**
 * Los filtros viven en la URL (?accountId=...&page=2): al recargar o compartir el
 * enlace se ve la misma lista, y el botón "atrás" deshace el último filtro.
 */
function useFiltersFromUrl(): [
  TransactionFilters,
  (changes: Partial<Record<string, string>>) => void,
] {
  const [params, setParams] = useSearchParams();

  const filters: TransactionFilters = {
    accountId: params.get('accountId') ?? undefined,
    categoryId: params.get('categoryId') ?? undefined,
    type: (params.get('type') as TransactionType | null) ?? undefined,
    from: params.get('from') ?? undefined,
    to: params.get('to') ?? undefined,
    q: params.get('q') ?? undefined,
    includeVoided: params.get('includeVoided') === 'true' || undefined,
    page: Number(params.get('page') ?? 0) || 0,
  };

  const update = (changes: Partial<Record<string, string>>) =>
    setParams((current) => {
      const next = new URLSearchParams(current);
      for (const [key, value] of Object.entries(changes)) {
        if (value) next.set(key, value);
        else next.delete(key);
      }
      // Cambiar un filtro vuelve a la primera página.
      if (!('page' in changes)) next.delete('page');
      return next;
    });

  return [filters, update];
}

/** Qué se puede hacer con un movimiento desde la lista. */
function actionFor(tx: Transaction, activeAccountIds: Set<string>): 'correct' | 'void' | null {
  if (tx.status === 'VOIDED' || tx.type === 'ADJUSTMENT') return null;
  // Corregir crea un movimiento nuevo, así que la cuenta tiene que admitirlos.
  if ((tx.type === 'INCOME' || tx.type === 'EXPENSE') && activeAccountIds.has(tx.accountId))
    return 'correct';
  return 'void';
}

function CategoryOptions({ categories }: { categories: Category[] }) {
  return (
    <>
      {(['EXPENSE', 'INCOME'] as const).map((kind) => (
        <optgroup key={kind} label={kind === 'EXPENSE' ? 'Gastos' : 'Ingresos'}>
          {categories
            .filter((c) => c.kind === kind)
            .map((c) => (
              <option key={c.id} value={c.id}>
                {c.parentId ? '— ' : ''}
                {c.name}
              </option>
            ))}
        </optgroup>
      ))}
    </>
  );
}

function Filters({
  filters,
  update,
}: {
  filters: TransactionFilters;
  update: (c: Partial<Record<string, string>>) => void;
}) {
  const { data: accounts = [] } = useAccounts();
  const { data: categories = [] } = useCategories();
  const [search, setSearch] = useState(filters.q ?? '');
  const active = [
    filters.accountId,
    filters.categoryId,
    filters.from,
    filters.to,
    filters.type,
    filters.includeVoided,
  ].filter(Boolean).length;
  // En móvil los seis filtros ocupaban toda la pantalla y la lista quedaba
  // debajo: ahí solo se ve la búsqueda y el resto se despliega con un botón.
  const [open, setOpen] = useState(active > 0);

  return (
    <div className="mb-4 grid gap-3 rounded-xl bg-surface p-4 ring-1 ring-line sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-6">
      <form
        className="sm:col-span-2 lg:col-span-3 xl:col-span-2"
        role="search"
        onSubmit={(event) => {
          event.preventDefault();
          update({ q: search.trim() });
        }}
      >
        <Input
          label="Buscar"
          type="search"
          placeholder="Descripción o referencia"
          value={search}
          onChange={(event) => setSearch(event.target.value)}
          onBlur={() => search.trim() !== (filters.q ?? '') && update({ q: search.trim() })}
        />
      </form>
      <Button
        variant="secondary"
        className="sm:hidden"
        aria-expanded={open}
        aria-controls="more-filters"
        onClick={() => setOpen((value) => !value)}
      >
        {open ? 'Ocultar filtros' : 'Filtros'}
        {active > 0 && ` (${active})`}
      </Button>
      <div id="more-filters" className={`${open ? 'contents' : 'hidden'} sm:contents`}>
        <Select
          label="Cuenta"
          value={filters.accountId ?? ''}
          onChange={(e) => update({ accountId: e.target.value })}
        >
          <option value="">Todas</option>
          {accounts.map((a) => (
            <option key={a.id} value={a.id}>
              {a.name}
            </option>
          ))}
        </Select>
        <Select
          label="Categoría"
          value={filters.categoryId ?? ''}
          onChange={(e) => update({ categoryId: e.target.value })}
        >
          <option value="">Todas</option>
          <CategoryOptions categories={categories} />
        </Select>
        <Input
          label="Desde"
          type="date"
          value={filters.from ?? ''}
          onChange={(e) => update({ from: e.target.value })}
        />
        <Input
          label="Hasta"
          type="date"
          value={filters.to ?? ''}
          onChange={(e) => update({ to: e.target.value })}
        />
        {/* Reembolsos y ajustes no se crean desde aquí, pero existen (la API crea
            reembolsos y cada saldo inicial es un ajuste): se pueden buscar. */}
        <Select
          label="Tipo"
          value={filters.type ?? ''}
          onChange={(e) => update({ type: e.target.value })}
        >
          <option value="">Todos</option>
          {Object.entries(TRANSACTION_TYPE_LABELS).map(([value, label]) => (
            <option key={value} value={value}>
              {label}
            </option>
          ))}
        </Select>
        <div className="flex flex-wrap items-center justify-between gap-x-4 gap-y-2 self-end sm:col-span-2">
          <label className="flex items-center gap-2 py-2 text-sm text-muted">
            <input
              type="checkbox"
              checked={filters.includeVoided ?? false}
              onChange={(e) => update({ includeVoided: e.target.checked ? 'true' : '' })}
              className="size-4 accent-brand"
            />
            Mostrar anulados
          </label>
          {(active > 0 || filters.q) && (
            <Button variant="ghost" onClick={() => update(NO_FILTERS)}>
              Quitar filtros
            </Button>
          )}
        </div>
      </div>
    </div>
  );
}

export default function TransactionsPage() {
  const [filters, update] = useFiltersFromUrl();
  const { data, isPending, isError, error, refetch, isPlaceholderData } = useTransactions(filters);
  const { data: accounts = [] } = useAccounts();
  const { data: categories = [] } = useCategories();
  const voidTransaction = useVoidTransaction();
  const notify = useNotify();
  const [modal, setModal] = useState<'transaction' | 'transfer' | null>(null);
  const [correcting, setCorrecting] = useState<Transaction | null>(null);
  const [toVoid, setToVoid] = useState<Transaction | null>(null);

  const accountNames = useMemo(() => new Map(accounts.map((a) => [a.id, a.name])), [accounts]);
  const categoryNames = useMemo(() => new Map(categories.map((c) => [c.id, c.name])), [categories]);
  const activeAccounts = accounts.filter((a) => a.status === 'ACTIVE');
  const activeIds = new Set(activeAccounts.map((a) => a.id));
  const hasFilters = Object.entries(filters).some(
    ([key, value]) => key !== 'page' && value !== undefined,
  );

  // Las dos partes de una transferencia comparten transferId: si ambas están en
  // la página, cada fila dice a qué cuenta fue o de cuál vino el dinero.
  const counterpart = (tx: Transaction): string | undefined => {
    if (!tx.transferId || !data) return undefined;
    const other = data.content.find((t) => t.transferId === tx.transferId && t.id !== tx.id);
    const name = other && accountNames.get(other.accountId);
    if (!name) return undefined;
    return tx.amount < 0 ? `a ${name}` : `desde ${name}`;
  };

  /** Lo que va bajo la descripción: el tipo si no es un ingreso o gasto normal, y si está anulado. */
  const details = (tx: Transaction): string[] => {
    const parts: string[] = [];
    if (tx.type === 'TRANSFER')
      parts.push(['Transferencia', counterpart(tx)].filter(Boolean).join(' '));
    else if (tx.type !== 'INCOME' && tx.type !== 'EXPENSE')
      parts.push(TRANSACTION_TYPE_LABELS[tx.type]);
    if (tx.status === 'VOIDED') parts.push('anulado');
    return parts;
  };

  const openRow = (tx: Transaction, action: 'correct' | 'void') => {
    voidTransaction.reset();
    if (action === 'correct') setCorrecting(tx);
    else setToVoid(tx);
  };

  return (
    <>
      <PageHeader
        title="Movimientos"
        actions={
          <>
            <Button
              variant="secondary"
              disabled={activeAccounts.length < 2}
              onClick={() => setModal('transfer')}
            >
              Transferencia
            </Button>
            <Button disabled={activeAccounts.length === 0} onClick={() => setModal('transaction')}>
              Nuevo movimiento
            </Button>
          </>
        }
      />

      <Filters key={filters.q ?? ''} filters={filters} update={update} />

      {isPending ? (
        <LoadingState />
      ) : isError ? (
        <ErrorState error={error} onRetry={() => void refetch()} />
      ) : data.content.length === 0 ? (
        hasFilters ? (
          <EmptyState title="Ningún movimiento coincide con los filtros">
            <Button variant="ghost" onClick={() => update(NO_FILTERS)}>
              Quitar filtros
            </Button>
          </EmptyState>
        ) : activeAccounts.length === 0 ? (
          <EmptyState title="Aún no hay movimientos">
            Primero{' '}
            <Link to="/accounts" className="text-brand underline-offset-2 hover:underline">
              crea una cuenta
            </Link>
            .
          </EmptyState>
        ) : (
          <EmptyState title="Aún no hay movimientos">
            Registra tu primer ingreso o gasto con «Nuevo movimiento».
          </EmptyState>
        )
      ) : (
        <>
          <div
            className={`overflow-x-auto rounded-xl bg-surface ring-1 ring-line transition-opacity ${isPlaceholderData ? 'opacity-60' : ''}`}
            aria-busy={isPlaceholderData}
          >
            <table className="w-full text-sm sm:min-w-[640px]">
              <caption className="sr-only">
                Movimientos. Pulsa la descripción para corregir o anular un movimiento.
              </caption>
              <thead className="border-b border-line text-left text-xs text-muted">
                <tr>
                  <th scope="col" className="hidden px-4 py-3 font-medium sm:table-cell">
                    Fecha
                  </th>
                  <th scope="col" className="px-4 py-3 font-medium">
                    Descripción
                  </th>
                  <th scope="col" className="hidden px-4 py-3 font-medium sm:table-cell">
                    Cuenta
                  </th>
                  <th scope="col" className="hidden px-4 py-3 font-medium md:table-cell">
                    Categoría
                  </th>
                  <th scope="col" className="px-4 py-3 text-right font-medium">
                    Importe
                  </th>
                </tr>
              </thead>
              <tbody className="divide-y divide-line">
                {data.content.map((tx) => {
                  const voided = tx.status === 'VOIDED';
                  const action = actionFor(tx, activeIds);
                  const title = tx.description || TRANSACTION_TYPE_LABELS[tx.type];
                  const category = tx.categoryId ? categoryNames.get(tx.categoryId) : undefined;
                  return (
                    <tr
                      key={tx.id}
                      className={`group relative ${voided ? 'text-muted' : ''} ${action ? 'cursor-pointer hover:bg-paper' : ''}`}
                    >
                      <td className="hidden px-4 py-3 whitespace-nowrap sm:table-cell">
                        {formatDate(tx.date)}
                      </td>
                      <td className="w-full max-w-0 px-4 py-3 sm:w-auto sm:max-w-64">
                        {action ? (
                          <button
                            type="button"
                            onClick={() => openRow(tx, action)}
                            aria-label={`${action === 'correct' ? 'Corregir o anular' : 'Anular'}: ${title}, ${formatDate(tx.date)}`}
                            // El ::after cubre toda la fila: se puede pulsar en cualquier
                            // parte de ella, no solo sobre el texto.
                            className="block max-w-full truncate rounded text-left font-medium underline-offset-2 group-hover:underline after:absolute after:inset-0 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand"
                          >
                            {title}
                          </button>
                        ) : (
                          <p className={`truncate ${voided ? 'line-through' : ''}`}>{title}</p>
                        )}
                        <p className="truncate text-xs text-muted">
                          {/* En móvil no caben las columnas de fecha, cuenta y categoría: van aquí. */}
                          <span className="sm:hidden">
                            {[
                              formatDate(tx.date),
                              accountNames.get(tx.accountId),
                              category,
                              ...details(tx),
                            ]
                              .filter(Boolean)
                              .join(' · ')}
                          </span>
                          <span className="hidden sm:inline md:hidden">
                            {[category, ...details(tx)].filter(Boolean).join(' · ')}
                          </span>
                          <span className="hidden md:inline">{details(tx).join(' · ')}</span>
                        </p>
                      </td>
                      <td className="hidden px-4 py-3 whitespace-nowrap text-muted sm:table-cell">
                        {accountNames.get(tx.accountId) ?? '—'}
                      </td>
                      <td className="hidden px-4 py-3 whitespace-nowrap text-muted md:table-cell">
                        {category ?? (
                          <span>
                            —<span className="sr-only">Sin categoría</span>
                          </span>
                        )}
                      </td>
                      <td className="px-4 py-3 text-right">
                        <Amount
                          amount={tx.amount}
                          currency={tx.currencyCode}
                          signed={!voided}
                          className={voided ? 'line-through' : ''}
                        />
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
          <Pagination
            page={data.page}
            totalPages={data.totalPages}
            totalElements={data.totalElements}
            onChange={(page) => update({ page: String(page) })}
          />
        </>
      )}

      {modal === 'transaction' && (
        <TransactionModal
          accounts={activeAccounts}
          categories={categories}
          onClose={() => setModal(null)}
        />
      )}
      {modal === 'transfer' && (
        <TransferModal accounts={activeAccounts} onClose={() => setModal(null)} />
      )}
      {correcting && (
        <TransactionModal
          accounts={activeAccounts}
          categories={categories}
          original={correcting}
          onVoid={() => {
            setToVoid(correcting);
            setCorrecting(null);
          }}
          onClose={() => setCorrecting(null)}
        />
      )}
      {toVoid && (
        <ConfirmDialog
          title={`Anular «${toVoid.description || TRANSACTION_TYPE_LABELS[toVoid.type]}»`}
          message={`${formatMoney(toVoid.amount, toVoid.currencyCode)} del ${formatDate(toVoid.date)}. ${
            toVoid.type === 'TRANSFER'
              ? 'Se anulan las dos partes de la transferencia y los saldos vuelven a como estaban.'
              : 'El saldo de la cuenta vuelve a como estaba.'
          } El movimiento sigue en el historial, marcado como anulado.`}
          confirmText={toVoid.type === 'TRANSFER' ? 'Anular transferencia' : 'Anular movimiento'}
          danger
          loading={voidTransaction.isPending}
          error={voidTransaction.isError ? errorMessage(voidTransaction.error) : null}
          onCancel={() => setToVoid(null)}
          onConfirm={() =>
            voidTransaction.mutate(toVoid.id, {
              onSuccess: () => {
                notify('Movimiento anulado');
                setToVoid(null);
              },
            })
          }
        />
      )}
    </>
  );
}
