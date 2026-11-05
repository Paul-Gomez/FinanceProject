import { zodResolver } from '@hookform/resolvers/zod';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { Link } from 'react-router-dom';
import { z } from 'zod';

import {
  useAccounts,
  useChangeAccountStatus,
  useCreateAccount,
  useRenameAccount,
} from '../../api/hooks';
import { Amount } from '../../components/Amount';
import { Alert } from '../../components/ui/Alert';
import { Button } from '../../components/ui/Button';
import { ConfirmDialog } from '../../components/ui/ConfirmDialog';
import { Input, Select } from '../../components/ui/Field';
import { Modal } from '../../components/ui/Modal';
import { useNotify } from '../../components/ui/notify';
import { PageHeader } from '../../components/ui/PageHeader';
import { EmptyState, ErrorState, LoadingState } from '../../components/ui/States';
import { totalsByCurrency } from '../../lib/accounts';
import { errorMessage } from '../../lib/errors';
import { formatMoney } from '../../lib/format';
import { ACCOUNT_STATUS_LABELS, ACCOUNT_TYPE_LABELS, CURRENCIES } from '../../lib/labels';
import type { Account, AccountStatus, AccountType } from '../../types/api';

const AMOUNT = /^-?\d+([.,]\d{1,2})?$/;

const createSchema = z.object({
  name: z.string().trim().min(1, 'Ponle un nombre').max(100, 'Máximo 100 caracteres'),
  type: z.enum(Object.keys(ACCOUNT_TYPE_LABELS) as [AccountType, ...AccountType[]]),
  currencyCode: z.string().regex(/^[A-Z]{3}$/),
  initialBalance: z
    .string()
    .trim()
    .refine(
      (v) => v === '' || AMOUNT.test(v),
      'Un importe con hasta 2 decimales, por ejemplo 1250,50',
    ),
});
type CreateValues = z.infer<typeof createSchema>;

function CreateAccountModal({ onClose }: { onClose: () => void }) {
  const createAccount = useCreateAccount();
  const notify = useNotify();
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<CreateValues>({
    resolver: zodResolver(createSchema),
    defaultValues: { type: 'BANK', currencyCode: 'EUR', initialBalance: '' },
  });

  const onSubmit = handleSubmit(({ initialBalance, ...rest }) =>
    createAccount.mutate(
      { ...rest, initialBalance: initialBalance ? initialBalance.replace(',', '.') : undefined },
      {
        onSuccess: (account) => {
          notify(`Cuenta «${account.name}» creada`);
          onClose();
        },
      },
    ),
  );

  return (
    <Modal title="Nueva cuenta" onClose={onClose}>
      <form onSubmit={onSubmit} noValidate className="space-y-4">
        {createAccount.isError && <Alert>{errorMessage(createAccount.error)}</Alert>}
        <Input
          label="Nombre"
          placeholder="Cuenta nómina"
          error={errors.name?.message}
          {...register('name')}
        />
        <div className="grid gap-4 sm:grid-cols-2">
          <Select label="Tipo" {...register('type')}>
            {Object.entries(ACCOUNT_TYPE_LABELS).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </Select>
          <Select label="Divisa" hint="No se puede cambiar después." {...register('currencyCode')}>
            {CURRENCIES.map((code) => (
              <option key={code}>{code}</option>
            ))}
          </Select>
        </div>
        <Input
          label="Saldo inicial"
          optional
          inputMode="decimal"
          placeholder="0,00"
          hint="Se registra como un ajuste, para que el saldo siempre cuadre con los movimientos."
          error={errors.initialBalance?.message}
          {...register('initialBalance')}
        />
        <div className="flex justify-end gap-2 pt-2">
          <Button variant="secondary" onClick={onClose}>
            Cancelar
          </Button>
          <Button type="submit" loading={createAccount.isPending}>
            Crear cuenta
          </Button>
        </div>
      </form>
    </Modal>
  );
}

const renameSchema = z.object({ name: z.string().trim().min(1, 'Ponle un nombre').max(100) });

function RenameAccountModal({ account, onClose }: { account: Account; onClose: () => void }) {
  const rename = useRenameAccount();
  const notify = useNotify();
  const {
    register,
    handleSubmit,
    formState: { errors, isDirty },
  } = useForm<{ name: string }>({
    resolver: zodResolver(renameSchema),
    defaultValues: { name: account.name },
  });

  const onSubmit = handleSubmit(({ name }) =>
    rename.mutate(
      { id: account.id, name },
      {
        onSuccess: () => {
          notify('Cuenta renombrada');
          onClose();
        },
      },
    ),
  );

  return (
    <Modal title="Renombrar cuenta" onClose={onClose}>
      <form onSubmit={onSubmit} noValidate className="space-y-4">
        {rename.isError && <Alert>{errorMessage(rename.error)}</Alert>}
        <Input label="Nombre" error={errors.name?.message} {...register('name')} />
        <div className="flex justify-end gap-2 pt-2">
          <Button variant="secondary" onClick={onClose}>
            Cancelar
          </Button>
          <Button type="submit" disabled={!isDirty} loading={rename.isPending}>
            Guardar
          </Button>
        </div>
      </form>
    </Modal>
  );
}

// Qué se puede hacer con una cuenta según su estado (mismas transiciones que AccountStatus en el backend).
const STATUS_ACTIONS: Record<AccountStatus, { to: AccountStatus; label: string }[]> = {
  ACTIVE: [
    { to: 'ARCHIVED', label: 'Archivar' },
    { to: 'CLOSED', label: 'Cerrar cuenta' },
  ],
  ARCHIVED: [
    { to: 'ACTIVE', label: 'Reactivar' },
    { to: 'CLOSED', label: 'Cerrar cuenta' },
  ],
  CLOSED: [],
};

const STATUS_DONE: Record<AccountStatus, string> = {
  ACTIVE: 'reactivada',
  ARCHIVED: 'archivada',
  CLOSED: 'cerrada',
};

const STATUS_CONFIRM: Partial<Record<AccountStatus, string>> = {
  ARCHIVED:
    'La cuenta deja de aparecer al registrar movimientos, pero conserva su historial. Puedes reactivarla cuando quieras.',
  CLOSED: 'Cerrar una cuenta es definitivo. Solo se puede cerrar si su saldo es cero.',
};

function AccountRow({
  account,
  onRename,
  onStatus,
}: {
  account: Account;
  onRename: () => void;
  onStatus: (to: AccountStatus) => void;
}) {
  const inactive = account.status !== 'ACTIVE';
  return (
    <li className="flex flex-wrap items-center justify-between gap-x-6 gap-y-3 px-5 py-4">
      <div className="min-w-0">
        <Link
          to={`/transactions?accountId=${account.id}`}
          aria-label={`${account.name}: ver sus movimientos`}
          className={`block truncate rounded font-medium underline-offset-2 hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand ${inactive ? 'text-muted' : ''}`}
        >
          {account.name}
        </Link>
        <p className="text-sm text-muted">
          {ACCOUNT_TYPE_LABELS[account.type]} · {account.currencyCode}
          {inactive && ` · ${ACCOUNT_STATUS_LABELS[account.status]}`}
        </p>
      </div>
      <div className="flex flex-wrap items-center gap-x-4 gap-y-2">
        <Amount
          amount={account.balance}
          currency={account.currencyCode}
          className="text-lg font-semibold"
        />
        {account.status !== 'CLOSED' && (
          <div className="flex gap-1">
            <Button variant="ghost" onClick={onRename}>
              Renombrar
            </Button>
            {STATUS_ACTIONS[account.status].map((action) => (
              <Button
                key={action.to}
                variant={action.to === 'CLOSED' ? 'danger-ghost' : 'ghost'}
                onClick={() => onStatus(action.to)}
              >
                {action.label}
              </Button>
            ))}
          </div>
        )}
      </div>
    </li>
  );
}

export default function AccountsPage() {
  const { data: accounts, isPending, isError, error, refetch } = useAccounts();
  const changeStatus = useChangeAccountStatus();
  const notify = useNotify();
  const [creating, setCreating] = useState(false);
  const [renaming, setRenaming] = useState<Account | null>(null);
  const [statusChange, setStatusChange] = useState<{ account: Account; to: AccountStatus } | null>(
    null,
  );

  const applyStatus = (account: Account, to: AccountStatus, onDone?: () => void) =>
    changeStatus.mutate(
      { id: account.id, status: to },
      {
        onSuccess: () => {
          notify(`Cuenta «${account.name}» ${STATUS_DONE[to]}`);
          onDone?.();
        },
      },
    );

  const requestStatus = (account: Account, to: AccountStatus) => {
    // Reactivar no necesita confirmación: se deshace archivando otra vez.
    if (to === 'ACTIVE') applyStatus(account, to);
    else {
      changeStatus.reset();
      setStatusChange({ account, to });
    }
  };

  const totals = accounts ? totalsByCurrency(accounts) : [];

  return (
    <>
      <PageHeader
        title="Cuentas"
        description={
          totals.length > 0 ? (
            <>
              En total{' '}
              {totals.map((t, i) => (
                <span key={t.currency}>
                  {i > 0 && ' y '}
                  <span className="tabular font-medium text-ink">
                    {formatMoney(t.total, t.currency)}
                  </span>
                </span>
              ))}
              , sin contar las cerradas. Cada saldo sale de sus movimientos.
            </>
          ) : (
            'Bancos, tarjetas, efectivo y ahorro. El saldo se calcula a partir de los movimientos.'
          )
        }
        actions={<Button onClick={() => setCreating(true)}>Nueva cuenta</Button>}
      />

      {isPending ? (
        <LoadingState />
      ) : isError ? (
        <ErrorState error={error} onRetry={() => void refetch()} />
      ) : accounts.length === 0 ? (
        <EmptyState title="Todavía no tienes cuentas">
          Crea la primera para empezar a registrar ingresos y gastos.
        </EmptyState>
      ) : (
        <ul className="divide-y divide-line rounded-xl bg-surface ring-1 ring-line">
          {accounts.map((account) => (
            <AccountRow
              key={account.id}
              account={account}
              onRename={() => setRenaming(account)}
              onStatus={(to) => requestStatus(account, to)}
            />
          ))}
        </ul>
      )}

      {creating && <CreateAccountModal onClose={() => setCreating(false)} />}
      {renaming && <RenameAccountModal account={renaming} onClose={() => setRenaming(null)} />}
      {statusChange && (
        <ConfirmDialog
          title={`${statusChange.to === 'CLOSED' ? 'Cerrar' : 'Archivar'} «${statusChange.account.name}»`}
          message={STATUS_CONFIRM[statusChange.to] ?? ''}
          confirmText={statusChange.to === 'CLOSED' ? 'Cerrar cuenta' : 'Archivar'}
          danger={statusChange.to === 'CLOSED'}
          loading={changeStatus.isPending}
          error={changeStatus.isError ? errorMessage(changeStatus.error) : null}
          onCancel={() => setStatusChange(null)}
          onConfirm={() =>
            applyStatus(statusChange.account, statusChange.to, () => setStatusChange(null))
          }
        />
      )}
    </>
  );
}
