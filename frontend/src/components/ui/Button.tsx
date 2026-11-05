import type { ButtonHTMLAttributes } from 'react';

type Variant = 'primary' | 'secondary' | 'ghost' | 'danger' | 'danger-ghost';

const STYLES: Record<Variant, string> = {
  primary: 'bg-brand text-white hover:bg-brand-strong',
  secondary: 'bg-surface text-ink ring-1 ring-inset ring-line hover:bg-paper',
  ghost: 'text-muted hover:bg-black/5 hover:text-ink',
  danger: 'bg-expense text-white hover:bg-expense/90',
  // Acción destructiva que no es la principal de la pantalla: se distingue de las
  // demás por el color, sin el peso de un botón relleno.
  'danger-ghost': 'text-expense hover:bg-expense/8',
};

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant;
  loading?: boolean;
}

export function Button({
  variant = 'primary',
  loading = false,
  disabled,
  className = '',
  children,
  type = 'button',
  ...rest
}: ButtonProps) {
  return (
    <button
      type={type}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      className={`inline-flex min-h-9 items-center whitespace-nowrap justify-center gap-2 rounded-lg px-3.5 text-sm font-medium transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand disabled:cursor-not-allowed disabled:opacity-60 pointer-coarse:min-h-11 ${STYLES[variant]} ${className}`}
      {...rest}
    >
      {loading && (
        <span
          aria-hidden="true"
          className="size-3.5 animate-spin rounded-full border-2 border-current border-t-transparent"
        />
      )}
      {children}
    </button>
  );
}
