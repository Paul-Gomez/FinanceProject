// Tipos de las respuestas de la API. Siguen a los records de Java de cada módulo.
// Los importes (BigDecimal) llegan como número JSON; las fechas sin hora
// (LocalDate) como 'YYYY-MM-DD' y los instantes como ISO-8601.

export type Role = 'USER' | 'ADMIN';

export interface User {
  id: string;
  email: string;
  displayName: string | null;
  role: Role;
  emailVerified: boolean;
  baseCurrency: string;
  createdAt: string;
}

export interface TokenResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
}

export interface ApiErrorBody {
  status: number;
  code: string;
  message: string;
  errors?: string[];
}

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export type AccountType = 'BANK' | 'CARD' | 'CASH' | 'SAVINGS' | 'INVESTMENT';
export type AccountStatus = 'ACTIVE' | 'ARCHIVED' | 'CLOSED';

export interface Account {
  id: string;
  name: string;
  type: AccountType;
  currencyCode: string;
  balance: number;
  status: AccountStatus;
  createdAt: string;
}

export type CategoryKind = 'INCOME' | 'EXPENSE';

export interface Category {
  id: string;
  parentId: string | null;
  system: boolean;
  name: string;
  kind: CategoryKind;
  color: string | null;
  icon: string | null;
}

export type TransactionType = 'INCOME' | 'EXPENSE' | 'TRANSFER' | 'REFUND' | 'ADJUSTMENT';
export type TransactionStatus = 'POSTED' | 'VOIDED';

export interface Transaction {
  id: string;
  accountId: string;
  transferId: string | null;
  refundedTransactionId: string | null;
  type: TransactionType;
  status: TransactionStatus;
  amount: number;
  currencyCode: string;
  categoryId: string | null;
  description: string | null;
  date: string;
  reference: string | null;
  createdAt: string;
}

export type BudgetPeriod = 'MONTHLY' | 'YEARLY';

export interface Budget {
  id: string;
  categoryId: string;
  period: BudgetPeriod;
  currencyCode: string;
  periodStart: string;
  periodEnd: string;
  budgeted: number;
  spent: number;
  remaining: number;
  percentageUsed: number;
  exceeded: boolean;
  excess: number;
}

export type PeriodType = 'MONTH' | 'QUARTER' | 'YEAR' | 'CUSTOM';

export interface Dashboard {
  periodType: PeriodType;
  from: string;
  to: string;
  currency: string;
  balances: { currency: string; total: number; accounts: number }[];
  totalBalance: { currency: string; amount: number; missingRates: string[] };
  income: number;
  expenses: number;
  savings: number;
  savingsRate: number | null;
  budgets: {
    budgetId: string;
    categoryId: string;
    categoryName: string;
    currency: string;
    budgeted: number;
    spent: number;
    percentageUsed: number;
    exceeded: boolean;
  }[];
  upcomingPayments: {
    type: string;
    id: string;
    name: string;
    date: string;
    amount: number;
    currency: string;
    overdue: boolean;
  }[];
  goals: {
    goalId: string;
    name: string;
    currency: string;
    targetAmount: number;
    savedAmount: number;
    progressPercentage: number;
    targetDate: string | null;
  }[];
}
