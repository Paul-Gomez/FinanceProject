import { useRef, useState, type KeyboardEvent, type ReactNode } from 'react';
import { Link, useSearchParams } from 'react-router-dom';

import { useAccounts, useCategories, useDashboard, useTransactions } from '../../api/hooks';
import { Amount } from '../../components/Amount';
import { Button } from '../../components/ui/Button';
import { Card } from '../../components/ui/Card';
import { PageHeader } from '../../components/ui/PageHeader';
import { ProgressBar } from '../../components/ui/ProgressBar';
import { ErrorState, LoadingState } from '../../components/ui/States';
import { formatDate, formatMoney, formatPercent } from '../../lib/format';
import { TRANSACTION_TYPE_LABELS } from '../../lib/labels';
import type { Dashboard, PeriodType } from '../../types/api';
import { TransactionModal } from '../transactions/TransactionForms';

type Period = Exclude<PeriodType, 'CUSTOM'>;

const PERIODS: { value: Period; label: string; current: string }[] = [
  { value: 'MONTH', label: 'Mes', current: 'Este mes' },
  { value: 'QUARTER', label: 'Trimestre', current: 'Este trimestre' },
  { value: 'YEAR', label: 'Año', current: 'Este año' },
];

const LINK = 'text-sm text-brand underline-offset-2 hover:underline';

/** El periodo va en la URL (?period=YEAR), como los filtros de movimientos: sobrevive a recargar. */
function usePeriodFromUrl(): [Period, (p: Period) => void] {
  const [params, setParams] = useSearchParams();
  const raw = params.get('period');
  const period = PERIODS.some((p) => p.value === raw) ? (raw as Period) : 'MONTH';
  const setPeriod = (next: Period) =>
    setParams(next === 'MONTH' ? {} : { period: next }, { replace: true });
  return [period, setPeriod];
}

/**
 * Grupo de radio con el patrón del teclado de ARIA: una sola parada de tabulación
 * y las flechas cambian de opción.
 */
function PeriodPicker({ value, onChange }: { value: Period; onChange: (p: Period) => void }) {
  const buttons = useRef<(HTMLButtonElement | null)[]>([]);

  const onKeyDown = (event: KeyboardEvent, index: number) => {
    const step = { ArrowRight: 1, ArrowDown: 1, ArrowLeft: -1, ArrowUp: -1 }[event.key];
    if (!step) return;
    event.preventDefault();
    const next = (index + step + PERIODS.length) % PERIODS.length;
    onChange(PERIODS[next].value);
    buttons.current[next]?.focus();
  };

  return (
    <div
      role="radiogroup"
      aria-label="Periodo"
      className="inline-flex rounded-lg bg-surface p-0.5 ring-1 ring-line"
    >
      {PERIODS.map((period, index) => {
        const checked = value === period.value;
        return (
          <button
            key={period.value}
            ref={(element) => {
              buttons.current[index] = element;
            }}
            type="button"
            role="radio"
            aria-checked={checked}
            tabIndex={checked ? 0 : -1}
            onClick={() => onChange(period.value)}
            onKeyDown={(event) => onKeyDown(event, index)}
            className={`rounded-md px-3 py-1.5 text-sm transition-colors focus-visible:outline-2 focus-visible:outline-brand pointer-coarse:min-h-10 ${
              checked ? 'bg-brand-soft font-medium text-brand-strong' : 'text-muted hover:text-ink'
            }`}
          >
            {period.label}
          </button>
        );
      })}
    </div>
  );
}

function StatementLine({
  label,
  children,
  total = false,
}: {
  label: string;
  children: ReactNode;
  total?: boolean;
}) {
  return (
    <div
      className={`flex items-baseline justify-between gap-4 py-2 ${
        total ? 'border-t border-ink/70 font-semibold' : ''
      }`}
    >
      <dt className={total ? '' : 'text-muted'}>{label}</dt>
      <dd className="tabular text-right">{children}</dd>
    </div>
  );
}

/**
 * Cuánto hay y cómo ha ido el periodo, como en un extracto: el patrimonio a un
 * lado y, al otro, ingresos menos gastos igual a ahorro.
 */
function Statement({ data, periodLabel }: { data: Dashboard; periodLabel: string }) {
  const { totalBalance, balances, currency } = data;
  const accounts = balances.reduce((sum, b) => sum + b.accounts, 0);
  // Sin tipo de cambio para alguna divisa no se puede dar un total: se enseña lo
  // que hay en cada una en vez de una cifra inventada.
  const canTotal = totalBalance.amount !== null;
  const showBreakdown = !canTotal || balances.length > 1;

  return (
    <section
      aria-label="Estado de tus cuentas"
      className="grid gap-x-10 gap-y-8 rounded-xl bg-surface p-6 ring-1 ring-line lg:grid-cols-[1.1fr_1fr]"
    >
      <div>
        <h2 className="text-sm font-semibold">Patrimonio</h2>
        {canTotal ? (
          <p className="tabular mt-2 text-4xl font-semibold tracking-tight">
            {formatMoney(totalBalance.amount!, totalBalance.currency)}
          </p>
        ) : (
          <p className="mt-2 text-sm text-muted">
            No se puede sumar en una sola cifra: falta el tipo de cambio de{' '}
            {totalBalance.missingRates.join(', ')} a {totalBalance.currency}.
          </p>
        )}
        {showBreakdown && (
          <ul className="mt-3 space-y-1">
            {balances.map((b) => (
              <li
                key={b.currency}
                className={`tabular flex items-baseline gap-3 ${canTotal ? 'text-sm text-muted' : 'text-2xl font-semibold tracking-tight'}`}
              >
                {formatMoney(b.total, b.currency)}
                <span className="text-xs font-normal tracking-normal text-muted">
                  {b.accounts} {b.accounts === 1 ? 'cuenta' : 'cuentas'} en {b.currency}
                </span>
              </li>
            ))}
          </ul>
        )}
        {!showBreakdown && (
          <p className="mt-1 text-sm text-muted">
            En {accounts} {accounts === 1 ? 'cuenta' : 'cuentas'}.{' '}
            <Link to="/accounts" className={LINK}>
              Ver cuentas
            </Link>
          </p>
        )}
      </div>

      <div>
        <h2 className="text-sm font-semibold">{periodLabel}</h2>
        <dl className="mt-2 text-sm">
          <StatementLine label="Ingresos">
            <Amount amount={data.income} currency={currency} signed />
          </StatementLine>
          <StatementLine label="Gastos">
            <Amount amount={-Math.abs(data.expenses)} currency={currency} signed />
          </StatementLine>
          <StatementLine label="Ahorro" total>
            <Amount amount={data.savings} currency={currency} />
            {typeof data.savingsRate === 'number' && (
              <span className="block text-xs font-normal text-muted">
                {formatPercent(data.savingsRate)} de los ingresos
              </span>
            )}
          </StatementLine>
        </dl>
      </div>
    </section>
  );
}

function RecentTransactions() {
  const { data } = useTransactions({ page: 0 });
  const { data: accounts = [] } = useAccounts();
  const names = new Map(accounts.map((a) => [a.id, a.name]));
  const recent = data?.content.slice(0, 5) ?? [];

  return (
    <Card
      title="Últimos movimientos"
      actions={
        <Link to="/transactions" className={LINK}>
          Ver todos
        </Link>
      }
    >
      {!data ? (
        <LoadingState />
      ) : recent.length === 0 ? (
        <p className="text-sm text-muted">Todavía no has registrado ningún movimiento.</p>
      ) : (
        <ul className="divide-y divide-line">
          {recent.map((tx) => (
            <li key={tx.id} className="flex items-center justify-between gap-3 py-2.5">
              <div className="min-w-0">
                <p
                  className={`truncate text-sm font-medium ${tx.status === 'VOIDED' ? 'text-muted line-through' : ''}`}
                >
                  {tx.description || TRANSACTION_TYPE_LABELS[tx.type]}
                </p>
                <p className="truncate text-xs text-muted">
                  {formatDate(tx.date)} · {names.get(tx.accountId)}
                </p>
              </div>
              <Amount
                amount={tx.amount}
                currency={tx.currencyCode}
                signed={tx.status !== 'VOIDED'}
                className={`text-sm ${tx.status === 'VOIDED' ? 'text-muted line-through' : ''}`}
              />
            </li>
          ))}
        </ul>
      )}
    </Card>
  );
}

function Budgets({ budgets }: { budgets: Dashboard['budgets'] }) {
  return (
    <Card
      title="Presupuestos en curso"
      actions={
        <Link to="/budgets" className={LINK}>
          Ver todos
        </Link>
      }
    >
      {budgets.length === 0 ? (
        <p className="text-sm text-muted">
          Aún no tienes presupuestos.{' '}
          <Link to="/budgets" className={LINK}>
            Crea uno
          </Link>{' '}
          para controlar lo que gastas en cada categoría.
        </p>
      ) : (
        <ul className="space-y-4">
          {budgets.map((budget) => {
            const left = budget.budgeted - budget.spent;
            return (
              <li key={budget.budgetId}>
                <div className="mb-1.5 flex items-baseline justify-between gap-3 text-sm">
                  <span className="truncate font-medium">{budget.categoryName}</span>
                  <span className="tabular shrink-0 text-muted">
                    {formatMoney(budget.spent, budget.currency)} de{' '}
                    {formatMoney(budget.budgeted, budget.currency)}
                  </span>
                </div>
                <ProgressBar
                  value={budget.percentageUsed}
                  label={`Presupuesto de ${budget.categoryName}`}
                />
                {/* El color de la barra no basta: el estado se dice también con texto. */}
                <p
                  className={`tabular mt-1 text-xs ${budget.exceeded ? 'font-medium text-expense' : 'text-muted'}`}
                >
                  {budget.exceeded
                    ? `Te has pasado ${formatMoney(-left, budget.currency)}`
                    : `Quedan ${formatMoney(left, budget.currency)} · ${formatPercent(budget.percentageUsed)} usado`}
                </p>
              </li>
            );
          })}
        </ul>
      )}
    </Card>
  );
}

export default function DashboardPage() {
  const [period, setPeriod] = usePeriodFromUrl();
  const { data, isPending, isError, error, refetch, isPlaceholderData } = useDashboard(period);
  const { data: accounts = [] } = useAccounts();
  const { data: categories = [] } = useCategories();
  const [adding, setAdding] = useState(false);
  const activeAccounts = accounts.filter((a) => a.status === 'ACTIVE');
  const periodLabel = PERIODS.find((p) => p.value === period)!.current;

  return (
    <>
      <PageHeader
        title="Resumen"
        description={data ? `Del ${formatDate(data.from)} al ${formatDate(data.to)}` : undefined}
        controls={<PeriodPicker value={period} onChange={setPeriod} />}
        actions={
          <Button disabled={activeAccounts.length === 0} onClick={() => setAdding(true)}>
            Nuevo movimiento
          </Button>
        }
      />

      {isPending ? (
        <LoadingState />
      ) : isError ? (
        <ErrorState error={error} onRetry={() => void refetch()} />
      ) : (
        <div
          className={`space-y-6 transition-opacity ${isPlaceholderData ? 'opacity-60' : ''}`}
          aria-busy={isPlaceholderData}
        >
          <Statement data={data} periodLabel={periodLabel} />

          <div className="grid gap-6 lg:grid-cols-2">
            <RecentTransactions />
            <Budgets budgets={data.budgets} />
          </div>

          {data.upcomingPayments.length > 0 && (
            <Card title="Próximos pagos">
              <ul className="divide-y divide-line">
                {data.upcomingPayments.map((payment) => (
                  <li
                    key={`${payment.type}-${payment.id}`}
                    className="flex items-center justify-between gap-3 py-2.5"
                  >
                    <div className="min-w-0">
                      <p className="truncate text-sm font-medium">{payment.name}</p>
                      <p className={`text-xs ${payment.overdue ? 'text-expense' : 'text-muted'}`}>
                        {payment.overdue ? 'Vencido el ' : ''}
                        {formatDate(payment.date)}
                      </p>
                    </div>
                    <Amount
                      amount={payment.amount}
                      currency={payment.currency}
                      className="text-sm"
                    />
                  </li>
                ))}
              </ul>
            </Card>
          )}

          {data.goals.length > 0 && (
            <Card title="Objetivos de ahorro">
              <ul className="grid gap-5 sm:grid-cols-2">
                {data.goals.map((goal) => (
                  <li key={goal.goalId}>
                    <div className="mb-1.5 flex items-baseline justify-between gap-3 text-sm">
                      <span className="truncate font-medium">{goal.name}</span>
                      <span className="tabular shrink-0 text-muted">
                        {formatPercent(goal.progressPercentage)}
                      </span>
                    </div>
                    <ProgressBar
                      value={goal.progressPercentage}
                      tone="brand"
                      label={`Progreso de ${goal.name}`}
                    />
                    <p className="tabular mt-1.5 text-xs text-muted">
                      {formatMoney(goal.savedAmount, goal.currency)} de{' '}
                      {formatMoney(goal.targetAmount, goal.currency)}
                      {goal.targetDate && ` · para el ${formatDate(goal.targetDate)}`}
                    </p>
                  </li>
                ))}
              </ul>
            </Card>
          )}
        </div>
      )}

      {adding && (
        <TransactionModal
          accounts={activeAccounts}
          categories={categories}
          onClose={() => setAdding(false)}
        />
      )}
    </>
  );
}
