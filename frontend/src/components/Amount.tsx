import { formatMoney } from '../lib/format';

interface AmountProps {
  /** Con signo, como lo guarda la API: los gastos y las salidas son negativos. */
  amount: number;
  currency: string;
  /** Muestra el "+" en positivos y colorea entradas y salidas. */
  signed?: boolean;
  className?: string;
}

export function Amount({ amount, currency, signed = false, className = '' }: AmountProps) {
  const color = !signed || amount === 0 ? '' : amount > 0 ? 'text-income' : 'text-expense';
  const prefix = signed && amount > 0 ? '+' : '';

  return (
    <span className={`tabular whitespace-nowrap ${color} ${className}`}>
      {prefix}
      {formatMoney(amount, currency)}
    </span>
  );
}
