import { useState } from 'react';
import { Link } from 'react-router-dom';

import { useDashboard } from '../../api/hooks';
import { Amount } from '../../components/Amount';
import { Card } from '../../components/ui/Card';
import { PageHeader } from '../../components/ui/PageHeader';
import { ProgressBar } from '../../components/ui/ProgressBar';
import { ErrorState, LoadingState } from '../../components/ui/States';
import { formatDate, formatMoney, formatPercent } from '../../lib/format';
import type { Dashboard, PeriodType } from '../../types/api';

const PERIODS: { value: Exclude<PeriodType, 'CUSTOM'>; label: string }[] = [
  { value: 'MONTH', label: 'Mes' },
  { value: 'QUARTER', label: 'Trimestre' },
  { value: 'YEAR', label: 'Año' },
];

function PeriodPicker({
  value,
  onChange,
}: {
  value: PeriodType;
  onChange: (p: PeriodType) => void;
}) {
  return (
    <div
      role="radiogroup"
      aria-label="Periodo"
      className="inline-flex rounded-lg bg-surface p-0.5 ring-1 ring-line"
    >
      {PERIODS.map((period) => (
        <button
          key={period.value}
          type="button"
          role="radio"
          aria-checked={value === period.value}
          onClick={() => onChange(period.value)}
          className={`rounded-md px-3 py-1.5 text-sm transition-colors focus-visible:outline-2 focus-visible:outline-brand ${
            value === period.value
              ? 'bg-brand-soft font-medium text-brand-strong'
              : 'text-muted hover:text-ink'
          }`}
        >
          {period.label}
        </button>
      ))}
    </div>
  );
}

function Stat({
  label,
  children,
  note,
}: {
  label: string;
  children: React.ReactNode;
  note?: string;
}) {
  return (
    <div className="rounded-xl bg-surface p-5 ring-1 ring-line">
      <p className="text-sm text-muted">{label}</p>
      <p className="mt-1 text-2xl font-semibold tracking-tight">{children}</p>
      {note && <p className="mt-1 text-xs text-muted">{note}</p>}
    </div>
  );
}

function Summary({ data }: { data: Dashboard }) {
  const { totalBalance, currency } = data;
  const missing = totalBalance.missingRates.length > 0;

  return (
    <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
      <Stat
        label="Saldo total"
        note={
          missing ? `Sin tipo de cambio para ${totalBalance.missingRates.join(', ')}` : undefined
        }
      >
        <Amount amount={totalBalance.amount} currency={totalBalance.currency} />
      </Stat>
      <Stat label="Ingresos">
        <span className="text-income">
          <Amount amount={data.income} currency={currency} />
        </span>
      </Stat>
      <Stat label="Gastos">
        <span className="text-expense">
          <Amount amount={data.expenses} currency={currency} />
        </span>
      </Stat>
      <Stat
        label="Ahorro"
        note={
          typeof data.savingsRate === 'number'
            ? `${formatPercent(data.savingsRate)} de los ingresos`
            : undefined
        }
      >
        <Amount amount={data.savings} currency={currency} />
      </Stat>
    </div>
  );
}

export default function DashboardPage() {
  const [period, setPeriod] = useState<PeriodType>('MONTH');
  const { data, isPending, isError, error, refetch } = useDashboard(period);

  return (
    <>
      <PageHeader
        title="Resumen"
        description={data ? `Del ${formatDate(data.from)} al ${formatDate(data.to)}` : undefined}
        actions={<PeriodPicker value={period} onChange={setPeriod} />}
      />

      {isPending ? (
        <LoadingState />
      ) : isError ? (
        <ErrorState error={error} onRetry={() => void refetch()} />
      ) : (
        <div className="space-y-6">
          <Summary data={data} />

          <div className="grid gap-6 lg:grid-cols-2">
            <Card
              title="Presupuestos"
              actions={
                <Link
                  to="/budgets"
                  className="text-sm text-brand underline-offset-2 hover:underline"
                >
                  Ver todos
                </Link>
              }
            >
              {data.budgets.length === 0 ? (
                <p className="text-sm text-muted">
                  Aún no tienes presupuestos.{' '}
                  <Link to="/budgets" className="text-brand underline-offset-2 hover:underline">
                    Crea uno
                  </Link>{' '}
                  para controlar lo que gastas en cada categoría.
                </p>
              ) : (
                <ul className="space-y-4">
                  {data.budgets.map((budget) => (
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
                    </li>
                  ))}
                </ul>
              )}
            </Card>

            <Card title="Próximos pagos">
              {data.upcomingPayments.length === 0 ? (
                <p className="text-sm text-muted">No hay suscripciones ni facturas pendientes.</p>
              ) : (
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
              )}
            </Card>
          </div>

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
    </>
  );
}
