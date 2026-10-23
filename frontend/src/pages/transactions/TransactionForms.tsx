import { zodResolver } from '@hookform/resolvers/zod';
import { useState } from 'react';
import { useForm, useWatch } from 'react-hook-form';
import { z } from 'zod';

import { newIdempotencyKey, useCreateTransaction, useCreateTransfer } from '../../api/hooks';
import { Alert } from '../../components/ui/Alert';
import { Button } from '../../components/ui/Button';
import { Input, Select } from '../../components/ui/Field';
import { Modal } from '../../components/ui/Modal';
import { errorMessage } from '../../lib/errors';
import { todayIso } from '../../lib/format';
import type { Account, Category } from '../../types/api';

const POSITIVE_AMOUNT = /^\d+([.,]\d{1,2})?$/;
const amountField = z
  .string()
  .trim()
  .regex(POSITIVE_AMOUNT, 'Un importe positivo con hasta 2 decimales, por ejemplo 45,90')
  .refine((v) => Number(v.replace(',', '.')) > 0, 'Tiene que ser mayor que 0');
const toApiAmount = (v: string) => v.replace(',', '.');

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

export function TransactionModal({
  accounts,
  categories,
  onClose,
}: {
  accounts: Account[];
  categories: Category[];
  onClose: () => void;
}) {
  const create = useCreateTransaction();
  const idempotencyKey = useIdempotencyKey();
  const {
    register,
    handleSubmit,
    control,
    formState: { errors },
  } = useForm<TransactionValues>({
    resolver: zodResolver(transactionSchema),
    defaultValues: {
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

  const onSubmit = handleSubmit((values) => {
    const account = accounts.find((a) => a.id === values.accountId)!;
    create.mutate(
      {
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
      },
      { onSuccess: onClose },
    );
  });

  return (
    <Modal title="Nuevo movimiento" onClose={onClose}>
      <form onSubmit={onSubmit} noValidate className="space-y-4">
        {create.isError && <Alert>{errorMessage(create.error)}</Alert>}
        <fieldset>
          <legend className="mb-1 text-sm font-medium">Tipo</legend>
          <div className="grid grid-cols-2 gap-2">
            {(['EXPENSE', 'INCOME'] as const).map((value) => (
              <label
                key={value}
                className="flex cursor-pointer items-center justify-center gap-2 rounded-lg px-3 py-2 text-sm ring-1 ring-line ring-inset has-checked:bg-brand-soft has-checked:font-medium has-checked:text-brand-strong has-checked:ring-brand/40 has-focus-visible:outline-2 has-focus-visible:outline-brand"
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
              {a.name} ({a.currencyCode})
            </option>
          ))}
        </Select>
        <Select
          label="Categoría"
          optional
          hint="Si la dejas vacía, se aplican tus reglas de categorización."
          {...register('categoryId')}
        >
          <option value="">Sin categoría</option>
          {visibleCategories.map((c) => (
            <option key={c.id} value={c.id}>
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
        <div className="flex justify-end gap-2 pt-2">
          <Button variant="secondary" onClick={onClose}>
            Cancelar
          </Button>
          <Button type="submit" loading={create.isPending}>
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
  const idempotencyKey = useIdempotencyKey();
  const {
    register,
    handleSubmit,
    control,
    formState: { errors },
  } = useForm<TransferValues>({
    resolver: zodResolver(transferSchema),
    defaultValues: {
      fromAccountId: accounts[0]?.id ?? '',
      toAccountId: accounts[1]?.id ?? '',
      amount: '',
      description: '',
      date: todayIso(),
    },
  });

  const fromAccountId = useWatch({ control, name: 'fromAccountId' });
  const from = accounts.find((a) => a.id === fromAccountId);
  // El backend solo transfiere entre cuentas de la misma divisa.
  const destinations = accounts.filter((a) => a.currencyCode === from?.currencyCode);

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
      { onSuccess: onClose },
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
        <div className="grid gap-4 sm:grid-cols-2">
          <Select
            label="Desde"
            error={errors.fromAccountId?.message}
            {...register('fromAccountId')}
          >
            {accounts.map((a) => (
              <option key={a.id} value={a.id}>
                {a.name} ({a.currencyCode})
              </option>
            ))}
          </Select>
          <Select label="Hacia" error={errors.toAccountId?.message} {...register('toAccountId')}>
            <option value="">Elige una cuenta</option>
            {destinations.map((a) => (
              <option key={a.id} value={a.id}>
                {a.name} ({a.currencyCode})
              </option>
            ))}
          </Select>
        </div>
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
          <Button type="submit" loading={transfer.isPending}>
            Transferir
          </Button>
        </div>
      </form>
    </Modal>
  );
}
