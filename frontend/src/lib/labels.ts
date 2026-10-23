import type {
  AccountStatus,
  AccountType,
  BudgetPeriod,
  CategoryKind,
  TransactionType,
} from '../types/api';

// Textos en castellano para los enums que devuelve la API.

export const ACCOUNT_TYPE_LABELS: Record<AccountType, string> = {
  BANK: 'Cuenta bancaria',
  CARD: 'Tarjeta',
  CASH: 'Efectivo',
  SAVINGS: 'Ahorro',
  INVESTMENT: 'Inversión',
};

export const ACCOUNT_STATUS_LABELS: Record<AccountStatus, string> = {
  ACTIVE: 'Activa',
  ARCHIVED: 'Archivada',
  CLOSED: 'Cerrada',
};

export const TRANSACTION_TYPE_LABELS: Record<TransactionType, string> = {
  INCOME: 'Ingreso',
  EXPENSE: 'Gasto',
  TRANSFER: 'Transferencia',
  REFUND: 'Reembolso',
  ADJUSTMENT: 'Ajuste',
};

export const CATEGORY_KIND_LABELS: Record<CategoryKind, string> = {
  INCOME: 'Ingreso',
  EXPENSE: 'Gasto',
};

export const BUDGET_PERIOD_LABELS: Record<BudgetPeriod, string> = {
  MONTHLY: 'Mensual',
  YEARLY: 'Anual',
};

/** Divisas que se ofrecen al crear una cuenta. La API acepta cualquier código ISO-4217. */
export const CURRENCIES = ['EUR', 'USD', 'GBP', 'CHF', 'JPY', 'MXN'];
