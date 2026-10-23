const moneyFormatters = new Map<string, Intl.NumberFormat>();

/** 1234.5 + 'EUR' -> '1.234,50 €'. Cada cuenta puede tener su propia divisa. */
export function formatMoney(amount: number, currency: string): string {
  let formatter = moneyFormatters.get(currency);
  if (!formatter) {
    formatter = new Intl.NumberFormat('es-ES', { style: 'currency', currency });
    moneyFormatters.set(currency, formatter);
  }
  return formatter.format(amount);
}

export function formatPercent(value: number): string {
  return `${new Intl.NumberFormat('es-ES', { maximumFractionDigits: 1 }).format(value)} %`;
}

/**
 * Las fechas sin hora ('2026-10-23') se parten a mano: con new Date('2026-10-23')
 * el navegador las toma como medianoche UTC y en América saldría el día anterior.
 */
export function parseLocalDate(isoDate: string): Date {
  const [year, month, day] = isoDate.split('-').map(Number);
  return new Date(year, month - 1, day);
}

const DATE = new Intl.DateTimeFormat('es-ES', { day: 'numeric', month: 'short', year: 'numeric' });
export const formatDate = (isoDate: string): string => DATE.format(parseLocalDate(isoDate));

/** Fecha de hoy en formato 'YYYY-MM-DD', en la zona horaria del usuario. */
export function todayIso(): string {
  const now = new Date();
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`;
}
