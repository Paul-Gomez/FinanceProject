import { zodResolver } from '@hookform/resolvers/zod';
import { useMemo, useState } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';

import { useBudgets, useCategories, useCreateBudget, useDeleteBudget } from '../../api/hooks';
import { useAuth } from '../../auth/auth-context';
import { Alert } from '../../components/ui/Alert';
import { Button } from '../../components/ui/Button';
import { ConfirmDialog } from '../../components/ui/ConfirmDialog';
import { Input, Select } from '../../components/ui/Field';
import { Modal } from '../../components/ui/Modal';
import { PageHeader } from '../../components/ui/PageHeader';
import { ProgressBar } from '../../components/ui/ProgressBar';
import { EmptyState, ErrorState, LoadingState } from '../../components/ui/States';
import { errorMessage } from '../../lib/errors';
import { formatDate, formatMoney, formatPercent } from '../../lib/format';
import { BUDGET_PERIOD_LABELS, CURRENCIES } from '../../lib/labels';
import type { Budget, Category } from '../../types/api';

const schema = z.object({
  categoryId: z.string().min(1, 'Elige una categoría'),
  amount: z
    .string()
    .trim()
    .regex(/^\d+([.,]\d{1,2})?$/, 'Un importe positivo con hasta 2 decimales')
    .refine((v) => Number(v.replace(',', '.')) > 0, 'Tiene que ser mayor que 0'),
  currencyCode: z.string().regex(/^[A-Z]{3}$/),
  period: z.enum(['MONTHLY', 'YEARLY']),
});
type FormValues = z.infer<typeof schema>;

function CreateBudgetModal({
  categories,
  onClose,
}: {
  categories: Category[];
  onClose: () => void;
}) {
  const { user } = useAuth();
  const create = useCreateBudget();
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      categoryId: '',
      amount: '',
      currencyCode: user?.baseCurrency ?? 'EUR',
      period: 'MONTHLY',
    },
  });

  const onSubmit = handleSubmit((values) =>
    create.mutate({ ...values, amount: values.amount.replace(',', '.') }, { onSuccess: onClose }),
  );

  return (
    <Modal
      title="Nuevo presupuesto"
      description="Te avisaremos al llegar al 80 % y al 100 % de lo previsto."
      onClose={onClose}
    >
      <form onSubmit={onSubmit} noValidate className="space-y-4">
        {create.isError && <Alert>{errorMessage(create.error)}</Alert>}
        <Select
          label="Categoría de gasto"
          error={errors.categoryId?.message}
          {...register('categoryId')}
        >
          <option value="">Elige una categoría</option>
          {categories.map((c) => (
            <option key={c.id} value={c.id}>
              {c.parentId ? '— ' : ''}
              {c.name}
            </option>
          ))}
        </Select>
        <div className="grid gap-4 sm:grid-cols-3">
          <Input
            label="Importe"
            inputMode="decimal"
            placeholder="300,00"
            className="sm:col-span-1"
            error={errors.amount?.message}
            {...register('amount')}
          />
          <Select label="Divisa" {...register('currencyCode')}>
            {CURRENCIES.map((code) => (
              <option key={code}>{code}</option>
            ))}
          </Select>
          <Select label="Periodo" {...register('period')}>
            {Object.entries(BUDGET_PERIOD_LABELS).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </Select>
        </div>
        <div className="flex justify-end gap-2 pt-2">
          <Button variant="secondary" onClick={onClose}>
            Cancelar
          </Button>
          <Button type="submit" loading={create.isPending}>
            Crear presupuesto
          </Button>
        </div>
      </form>
    </Modal>
  );
}

function BudgetItem({
  budget,
  name,
  onDelete,
}: {
  budget: Budget;
  name: string;
  onDelete: () => void;
}) {
  const status = budget.exceeded
    ? `Te has pasado ${formatMoney(budget.excess, budget.currencyCode)}`
    : `Quedan ${formatMoney(budget.remaining, budget.currencyCode)}`;

  return (
    <li className="rounded-xl bg-surface p-5 ring-1 ring-line">
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <h2 className="truncate font-medium">{name}</h2>
          <p className="text-xs text-muted">
            {BUDGET_PERIOD_LABELS[budget.period]} · {formatDate(budget.periodStart)} –{' '}
            {formatDate(budget.periodEnd)}
          </p>
        </div>
        <Button
          variant="ghost"
          aria-label={`Eliminar el presupuesto de ${name}`}
          onClick={onDelete}
        >
          Eliminar
        </Button>
      </div>
      <p className="tabular mt-4 text-lg font-semibold">
        {formatMoney(budget.spent, budget.currencyCode)}
        <span className="text-sm font-normal text-muted">
          {' '}
          de {formatMoney(budget.budgeted, budget.currencyCode)}
        </span>
      </p>
      <div className="mt-2">
        <ProgressBar value={budget.percentageUsed} label={`Presupuesto de ${name}`} />
      </div>
      <p className={`mt-2 text-xs ${budget.exceeded ? 'text-expense' : 'text-muted'}`}>
        {formatPercent(budget.percentageUsed)} usado · {status}
      </p>
    </li>
  );
}

export default function BudgetsPage() {
  const { data: budgets, isPending, isError, error, refetch } = useBudgets();
  const { data: categories = [] } = useCategories();
  const deleteBudget = useDeleteBudget();
  const [creating, setCreating] = useState(false);
  const [toDelete, setToDelete] = useState<{ budget: Budget; name: string } | null>(null);

  const names = useMemo(() => new Map(categories.map((c) => [c.id, c.name])), [categories]);
  const expenseCategories = categories.filter((c) => c.kind === 'EXPENSE');

  return (
    <>
      <PageHeader
        title="Presupuestos"
        description="Cuánto quieres gastar como máximo en cada categoría, por mes o por año."
        actions={<Button onClick={() => setCreating(true)}>Nuevo presupuesto</Button>}
      />

      {isPending ? (
        <LoadingState />
      ) : isError ? (
        <ErrorState error={error} onRetry={() => void refetch()} />
      ) : budgets.length === 0 ? (
        <EmptyState title="Todavía no tienes presupuestos">
          Por ejemplo, 300 € al mes en supermercado.
        </EmptyState>
      ) : (
        <ul className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
          {budgets.map((budget) => {
            const name = names.get(budget.categoryId) ?? 'Categoría';
            return (
              <BudgetItem
                key={budget.id}
                budget={budget}
                name={name}
                onDelete={() => {
                  deleteBudget.reset();
                  setToDelete({ budget, name });
                }}
              />
            );
          })}
        </ul>
      )}

      {creating && (
        <CreateBudgetModal categories={expenseCategories} onClose={() => setCreating(false)} />
      )}
      {toDelete && (
        <ConfirmDialog
          title="Eliminar presupuesto"
          message={`Se elimina el presupuesto de «${toDelete.name}». Tus movimientos no cambian.`}
          confirmText="Eliminar"
          danger
          loading={deleteBudget.isPending}
          error={deleteBudget.isError ? errorMessage(deleteBudget.error) : null}
          onCancel={() => setToDelete(null)}
          onConfirm={() =>
            deleteBudget.mutate(toDelete.budget.id, { onSuccess: () => setToDelete(null) })
          }
        />
      )}
    </>
  );
}
