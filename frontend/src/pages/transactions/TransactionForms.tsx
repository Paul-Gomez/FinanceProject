import { zodResolver } from '@hookform/resolvers/zod';
import { useState } from 'react';
import { useForm, useWatch } from 'react-hook-form';
import { z } from 'zod';

import {
  newIdempotencyKey,
  useCreateTransaction,
  useCreateTransfer,
  useVoidTransaction,
} from '../../api/hooks';
import { Alert } from '../../components/ui/Alert';
import { Button } from '../../components/ui/Button';
import { Input, Select } from '../../components/ui/Field';
import { Modal } from '../../components/ui/Modal';
import { useNotify } from '../../components/ui/notify';
import { transferDestinations } from '../../lib/accounts';
import { errorMessage } from '../../lib/errors';
import { formatMoney, todayIso } from '../../lib/format';
import type { Account, Category, Transaction } from '../../types/api';

const POSITIVE_AMOUNT = /^\d+([.,]\d{1,2})?$/;
const amountField = z
  .string()
  .trim()
  .regex(POSITIVE_AMOUNT, 'Un importe positivo con hasta 2 decimales, por ejemplo 45,90')
  .refine((v) => Number(v.replace(',', '.')) > 0, 'Tiene que ser mayor que 0');
const toApiAmount = (v: string) => v.replace(',', '.');
/** -45.9 -> '45,90': el formulario pide el importe sin signo y con coma. */
const toFormAmount = (amount: number) => Math.abs(amount).toFixed(2).replace('.', ',');

const accountOption = (a: Account) => `${a.name} · ${formatMoney(a.balance, a.currencyCode)}`;

/**
 * La clave se crea una vez por formulario abierto, no por clic: si el primer
 * envío llegó al servidor pero la respuesta se perdió, el reintento lleva la
 * misma clave y el backend no lo registra dos veces.
 */
function useIdempotencyKey() {
  return useState(newIdempotencyKey)[0];
}

const transactionSchema = z.object({
  type: z.enum(['EXPENSE', 'INCOME']),
  accountId: z.string().min(1, 'Elige una cuenta'),
  amount: amountField,
  categoryId: z.string(),
  description: z.string().trim().max(255, 'Máximo 255 caracteres'),
  date: z.iso.date('Fecha no válida'),
});
type TransactionValues = z.infer<typeof transactionSchema>;

/**
 * Alta de un ingreso o gasto, o corrección de uno existente (`original`, cuya
 * cuenta tiene que estar entre `accounts`: solo las activas admiten movimientos).
 *
 * El historial no se edita: el backend solo permite anular. Corregir guarda la
 * versión buena como movimiento nuevo y después anula el original, que queda en
 * el historial marcado como anulado. Si la anulación falla, reintentar es seguro:
 * la Idempotency-Key hace que el alta no se repita y solo se vuelve a anular.
 */
export function TransactionModal({
  accounts,
  categories,
  original,
  onVoid,
  onClose,
}: {
  accounts: Account[];
  categories: Category[];
  original?: Transaction;
  /** Solo al corregir: abre la confirmación para anular el movimiento. */
  onVoid?: () => void;
  onClose: () => void;
}) {
  const create = useCreateTransaction();
  const voidOriginal = useVoidTransaction();
  const notify = useNotify();
  const idempotencyKey = useIdempotencyKey();
  const [failure, setFailure] = useState<string | null>(null);
  const {
    register,
    handleSubmit,
    control,
    formState: { errors, isDirty },
  } = useForm<TransactionValues>({
    resolver: zodResolver(transactionSchema),
    defaultValues: original
      ? {
          type: original.type === 'INCOME' ? 'INCOME' : 'EXPENSE',
          accountId: original.accountId,
          amount: toFormAmount(original.amount),
          categoryId: original.categoryId ?? '',
          description: original.description ?? '',
          date: original.date,
        }
      : {
          type: 'EXPENSE',
          accountId: accounts[0]?.id ?? '',
          amount: '',
          categoryId: '',
          description: '',
          date: todayIso(),
        },
  });

  const type = useWatch({ control, name: 'type' });
  const visibleCategories = categories.filter((c) => c.kind === type);
  const saving = create.isPending || voidOriginal.isPending;

  const onSubmit = handleSubmit(async (values) => {
    setFailure(null);
    const account = accounts.find((a) => a.id === values.accountId)!;
    try {
      await create.mutateAsync({
        idempotencyKey,
        input: {
          accountId: values.accountId,
          type: values.type,
          amount: toApiAmount(values.amount),
          currencyCode: account.currencyCode,
          categoryId: values.categoryId || undefined,
          description: values.description || undefined,
          date: values.date,
        },
      });
    } catch (error) {
      setFailure(errorMessage(error));
      return;
    }
    if (original) {
      try {
        await voidOriginal.mutateAsync(original.id);
      } catch (error) {
        setFailure(
          `La versión corregida se ha guardado, pero el original sigue activo: ${errorMessage(error)} Pulsa Guardar de nuevo para anularlo.`,
        );
        return;
      }
    }
    notify(original ? 'Movimiento corregido' : 'Movimiento guardado');
    onClose();
  });

  return (
    <Modal
      title={original ? 'Corregir movimiento' : 'Nuevo movimiento'}
      description={
        original
          ? 'Se guarda la versión corregida y el original queda anulado en el historial.'
          : undefined
      }
      onClose={onClose}
    >
      <form onSubmit={onSubmit} noValidate className="space-y-4">
        {failure && <Alert>{failure}</Alert>}
        <fieldset>
          <legend className="mb-1 text-sm font-medium">Tipo</legend>
          <div className="grid grid-cols-2 gap-2">
            {(['EXPENSE', 'INCOME'] as const).map((value) => (
              <label
                key={value}
                className="flex cursor-pointer items-center justify-center gap-2 rounded-lg px-3 py-2 text-sm ring-1 ring-line ring-inset has-checked:bg-brand-soft has-checked:font-medium has-checked:text-brand-strong has-checked:ring-brand/40 has-focus-visible:outline-2 has-focus-visible:outline-brand pointer-coarse:min-h-11"
              >
                <input type="radio" value={value} className="sr-only" {...register('type')} />
                {value === 'EXPENSE' ? 'Gasto' : 'Ingreso'}
              </label>
            ))}
          </div>
        </fieldset>
        <div className="grid gap-4 sm:grid-cols-2">
          <Input
            label="Importe"
            inputMode="decimal"
            placeholder="0,00"
            error={errors.amount?.message}
            {...register('amount')}
          />
          <Input label="Fecha" type="date" error={errors.date?.message} {...register('date')} />
        </div>
        <Select label="Cuenta" error={errors.accountId?.message} {...register('accountId')}>
          {accounts.map((a) => (
            <option key={a.id} value={a.id}>
              {accountOption(a)}
            </option>
          ))}
        </Select>
        <Select label="Categoría" optional {...register('categoryId')}>
          <option value="">Sin categoría</option>
          {visibleCategories.map((c) => (
            <option key={c.id} value={c.id}>
              {c.parentId ? '— ' : ''}
              {c.name}
            </option>
          ))}
        </Select>
        <Input
          label="Descripción"
          optional
          placeholder="Mercadona"
          error={errors.description?.message}
          {...register('description')}
        />
        <div className="flex flex-wrap items-center justify-end gap-2 pt-2">
          {onVoid && (
            <Button variant="danger-ghost" className="mr-auto" onClick={onVoid}>
              Anular movimiento
            </Button>
          )}
          <Button variant="secondary" onClick={onClose}>
            Cancelar
          </Button>
          <Button
            type="submit"
            disabled={Boolean(original) && !isDirty && !failure}
            loading={saving}
          >
            Guardar
          </Button>
        </div>
      </form>
    </Modal>
  );
}

const transferSchema = z
  .object({
    fromAccountId: z.string().min(1, 'Elige la cuenta de origen'),
    toAccountId: z.string().min(1, 'Elige la cuenta de destino'),
    amount: amountField,
    description: z.string().trim().max(255, 'Máximo 255 caracteres'),
    date: z.iso.date('Fecha no válida'),
  })
  .refine((v) => v.fromAccountId !== v.toAccountId, {
    path: ['toAccountId'],
    message: 'Tiene que ser una cuenta distinta a la de origen',
  });
type TransferValues = z.infer<typeof transferSchema>;

export function TransferModal({ accounts, onClose }: { accounts: Account[]; onClose: () => void }) {
  const transfer = useCreateTransfer();
  const notify = useNotify();
  const idempotencyKey = useIdempotencyKey();
  // Por defecto, la primera cuenta que tenga a dónde transferir.
  const initialFrom =
    accounts.find((a) => transferDestinations(accounts, a.id).length > 0) ?? accounts[0];
  const {
    register,
    handleSubmit,
    control,
    setValue,
    formState: { errors },
  } = useForm<TransferValues>({
    resolver: zodResolver(transferSchema),
    defaultValues: {
      fromAccountId: initialFrom?.id ?? '',
      toAccountId: initialFrom ? (transferDestinations(accounts, initialFrom.id)[0]?.id ?? '') : '',
      amount: '',
      description: '',
      date: todayIso(),
    },
  });

  const fromAccountId = useWatch({ control, name: 'fromAccountId' });
  const from = accounts.find((a) => a.id === fromAccountId);
  // El backend solo transfiere entre cuentas de la misma divisa.
  const destinations = transferDestinations(accounts, fromAccountId);

  const onSubmit = handleSubmit((values) =>
    transfer.mutate(
      {
        idempotencyKey,
        input: {
          fromAccountId: values.fromAccountId,
          toAccountId: values.toAccountId,
          amount: toApiAmount(values.amount),
          currencyCode: from!.currencyCode,
          description: values.description || undefined,
          date: values.date,
        },
      },
      {
        onSuccess: () => {
          notify('Transferencia hecha');
          onClose();
        },
      },
    ),
  );

  return (
    <Modal
      title="Transferencia entre cuentas"
      description="Mueve dinero entre dos cuentas tuyas de la misma divisa."
      onClose={onClose}
    >
      <form onSubmit={onSubmit} noValidate className="space-y-4">
        {transfer.isError && <Alert>{errorMessage(transfer.error)}</Alert>}
        <Select
          label="Desde"
          error={errors.fromAccountId?.message}
          {...register('fromAccountId', {
            // Al cambiar el origen, el destino anterior puede dejar de valer (otra
            // divisa, o la misma cuenta): se pasa al primero que sí vale.
            onChange: (event) =>
              setValue(
                'toAccountId',
                transferDestinations(accounts, event.target.value)[0]?.id ?? '',
              ),
          })}
        >
          {accounts.map((a) => (
            <option key={a.id} value={a.id}>
              {accountOption(a)}
            </option>
          ))}
        </Select>
        {destinations.length === 0 ? (
          <Alert tone="info">
            No tienes otra cuenta activa en {from?.currencyCode}. Para transferir desde {from?.name}
            , crea antes otra cuenta en esa divisa.
          </Alert>
        ) : (
          <Select label="Hacia" error={errors.toAccountId?.message} {...register('toAccountId')}>
            {destinations.map((a) => (
              <option key={a.id} value={a.id}>
                {accountOption(a)}
              </option>
            ))}
          </Select>
        )}
        <div className="grid gap-4 sm:grid-cols-2">
          <Input
            label="Importe"
            inputMode="decimal"
            placeholder="0,00"
            error={errors.amount?.message}
            {...register('amount')}
          />
          <Input label="Fecha" type="date" error={errors.date?.message} {...register('date')} />
        </div>
        <Input
          label="Concepto"
          optional
          error={errors.description?.message}
          {...register('description')}
        />
        <div className="flex justify-end gap-2 pt-2">
          <Button variant="secondary" onClick={onClose}>
            Cancelar
          </Button>
          <Button type="submit" disabled={destinations.length === 0} loading={transfer.isPending}>
            Transferir
          </Button>
        </div>
      </form>
    </Modal>
  );
}
