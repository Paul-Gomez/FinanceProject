import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { api } from '../lib/api';
import type {
  Account,
  AccountStatus,
  AccountType,
  Budget,
  BudgetPeriod,
  Category,
  CategoryKind,
  Dashboard,
  Page,
  PeriodType,
  Transaction,
  TransactionType,
} from '../types/api';

// Un hook por consulta o acción de la API. Después de cada escritura se
// invalidan las consultas que dependen de ella: crear un gasto cambia el saldo
// de la cuenta, el resumen y los presupuestos, no solo la lista de movimientos.

const keys = {
  accounts: ['accounts'] as const,
  categories: ['categories'] as const,
  budgets: ['budgets'] as const,
  dashboard: ['dashboard'] as const,
  transactions: ['transactions'] as const,
};

function useInvalidate() {
  const queryClient = useQueryClient();
  return (...groups: (keyof typeof keys)[]) =>
    Promise.all(groups.map((group) => queryClient.invalidateQueries({ queryKey: keys[group] })));
}

/**
 * Cada envío de un formulario lleva su propia Idempotency-Key: si la red falla
 * y el usuario reintenta, el backend devuelve el resultado guardado en vez de
 * registrar el movimiento dos veces.
 */
export const newIdempotencyKey = () => crypto.randomUUID();

// ---------- Resumen ----------

export function useDashboard(period: PeriodType) {
  return useQuery({
    queryKey: [...keys.dashboard, period],
    queryFn: () => api<Dashboard>('/api/v1/dashboard', { query: { period } }),
  });
}

// ---------- Cuentas ----------

export function useAccounts() {
  return useQuery({
    queryKey: keys.accounts,
    queryFn: () => api<Account[]>('/api/v1/accounts'),
  });
}

export interface AccountInput {
  name: string;
  type: AccountType;
  currencyCode: string;
  initialBalance?: string;
}

export function useCreateAccount() {
  const invalidate = useInvalidate();
  return useMutation({
    mutationFn: (input: AccountInput) =>
      api<Account>('/api/v1/accounts', { method: 'POST', body: input }),
    onSuccess: () => invalidate('accounts', 'dashboard', 'transactions'),
  });
}

export function useRenameAccount() {
  const invalidate = useInvalidate();
  return useMutation({
    mutationFn: ({ id, name }: { id: string; name: string }) =>
      api<Account>(`/api/v1/accounts/${id}`, { method: 'PUT', body: { name } }),
    onSuccess: () => invalidate('accounts'),
  });
}

export function useChangeAccountStatus() {
  const invalidate = useInvalidate();
  return useMutation({
    mutationFn: ({ id, status }: { id: string; status: AccountStatus }) =>
      api<Account>(`/api/v1/accounts/${id}/status`, { method: 'PATCH', body: { status } }),
    onSuccess: () => invalidate('accounts', 'dashboard'),
  });
}

// ---------- Categorías ----------

export function useCategories() {
  return useQuery({
    queryKey: keys.categories,
    queryFn: () => api<Category[]>('/api/v1/categories'),
    staleTime: 5 * 60_000,
  });
}

export interface CategoryInput {
  name: string;
  kind: CategoryKind;
  parentId?: string;
  color?: string;
}

export function useCreateCategory() {
  const invalidate = useInvalidate();
  return useMutation({
    mutationFn: (input: CategoryInput) =>
      api<Category>('/api/v1/categories', { method: 'POST', body: input }),
    onSuccess: () => invalidate('categories'),
  });
}

export function useDeleteCategory() {
  const invalidate = useInvalidate();
  return useMutation({
    mutationFn: (id: string) => api<void>(`/api/v1/categories/${id}`, { method: 'DELETE' }),
    onSuccess: () => invalidate('categories'),
  });
}

// ---------- Movimientos ----------

export interface TransactionFilters {
  accountId?: string;
  categoryId?: string;
  type?: TransactionType;
  from?: string;
  to?: string;
  q?: string;
  includeVoided?: boolean;
  page: number;
}

export function useTransactions(filters: TransactionFilters) {
  return useQuery({
    queryKey: [...keys.transactions, filters],
    queryFn: () =>
      api<Page<Transaction>>('/api/v1/transactions', {
        query: { ...filters, size: 20 },
      }),
    // Al cambiar de página o de filtro se sigue viendo la lista anterior hasta
    // que llega la nueva, en vez de un parpadeo de "Cargando…".
    placeholderData: keepPreviousData,
  });
}

export interface TransactionInput {
  accountId: string;
  type: 'INCOME' | 'EXPENSE';
  amount: string;
  currencyCode: string;
  categoryId?: string;
  description?: string;
  date: string;
}

export function useCreateTransaction() {
  const invalidate = useInvalidate();
  return useMutation({
    mutationFn: ({ input, idempotencyKey }: { input: TransactionInput; idempotencyKey: string }) =>
      api<Transaction>('/api/v1/transactions', {
        method: 'POST',
        body: input,
        headers: { 'Idempotency-Key': idempotencyKey },
      }),
    onSuccess: () => invalidate('transactions', 'accounts', 'dashboard', 'budgets'),
  });
}

export interface TransferInput {
  fromAccountId: string;
  toAccountId: string;
  amount: string;
  currencyCode: string;
  description?: string;
  date: string;
}

export function useCreateTransfer() {
  const invalidate = useInvalidate();
  return useMutation({
    mutationFn: ({ input, idempotencyKey }: { input: TransferInput; idempotencyKey: string }) =>
      api('/api/v1/transfers', {
        method: 'POST',
        body: input,
        headers: { 'Idempotency-Key': idempotencyKey },
      }),
    onSuccess: () => invalidate('transactions', 'accounts', 'dashboard'),
  });
}

/** No borra el movimiento: lo anula y el backend revierte su efecto en el saldo. */
export function useVoidTransaction() {
  const invalidate = useInvalidate();
  return useMutation({
    mutationFn: (id: string) => api<void>(`/api/v1/transactions/${id}`, { method: 'DELETE' }),
    onSuccess: () => invalidate('transactions', 'accounts', 'dashboard', 'budgets'),
  });
}

// ---------- Presupuestos ----------

export function useBudgets() {
  return useQuery({
    queryKey: keys.budgets,
    queryFn: () => api<Budget[]>('/api/v1/budgets'),
  });
}

export interface BudgetInput {
  categoryId: string;
  amount: string;
  currencyCode: string;
  period: BudgetPeriod;
}

export function useCreateBudget() {
  const invalidate = useInvalidate();
  return useMutation({
    mutationFn: (input: BudgetInput) =>
      api<Budget>('/api/v1/budgets', { method: 'POST', body: input }),
    onSuccess: () => invalidate('budgets', 'dashboard'),
  });
}

export function useDeleteBudget() {
  const invalidate = useInvalidate();
  return useMutation({
    mutationFn: (id: string) => api<void>(`/api/v1/budgets/${id}`, { method: 'DELETE' }),
    onSuccess: () => invalidate('budgets', 'dashboard'),
  });
}
