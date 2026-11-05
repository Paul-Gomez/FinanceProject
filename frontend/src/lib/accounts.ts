import type { Account } from '../types/api';

/**
 * Cuentas a las que se puede transferir desde `fromId`: cualquier otra de la
 * misma divisa (el backend no convierte entre divisas). Nunca la propia cuenta de
 * origen, que antes aparecía en la lista y llevaba a un error al enviar.
 */
export function transferDestinations(accounts: Account[], fromId: string): Account[] {
  const from = accounts.find((a) => a.id === fromId);
  if (!from) return [];
  return accounts.filter((a) => a.id !== from.id && a.currencyCode === from.currencyCode);
}

/** Suma de saldos por divisa, sin las cuentas cerradas. Mezclar divisas daría una cifra falsa. */
export function totalsByCurrency(accounts: Account[]): { currency: string; total: number }[] {
  const totals = new Map<string, number>();
  for (const account of accounts) {
    if (account.status === 'CLOSED') continue;
    totals.set(account.currencyCode, (totals.get(account.currencyCode) ?? 0) + account.balance);
  }
  // Redondeo a céntimos: sumar decimales en coma flotante deja restos como 0,30000000000000004.
  return [...totals].map(([currency, total]) => ({
    currency,
    total: Math.round(total * 100) / 100,
  }));
}
