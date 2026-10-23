interface ProgressBarProps {
  /** Porcentaje (0–100 o más). Lo que pasa de 100 se dibuja lleno. */
  value: number;
  label: string;
  /** 'auto' colorea según el uso: normal, aviso desde el 80 % y rojo al pasarse. */
  tone?: 'auto' | 'brand';
}

export function ProgressBar({ value, label, tone = 'auto' }: ProgressBarProps) {
  const width = Math.max(0, Math.min(value, 100));
  const color =
    tone === 'brand'
      ? 'bg-brand'
      : value >= 100
        ? 'bg-expense'
        : value >= 80
          ? 'bg-warning'
          : 'bg-brand';

  return (
    <div
      role="progressbar"
      aria-label={label}
      aria-valuemin={0}
      aria-valuemax={100}
      aria-valuenow={Math.round(width)}
      className="h-1.5 w-full overflow-hidden rounded-full bg-line"
    >
      <div
        className={`h-full rounded-full ${color} transition-[width]`}
        style={{ width: `${width}%` }}
      />
    </div>
  );
}
