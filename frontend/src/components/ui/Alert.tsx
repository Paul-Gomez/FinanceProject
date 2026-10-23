import type { ReactNode } from 'react';

type Tone = 'error' | 'info';

const STYLES: Record<Tone, string> = {
  error: 'bg-expense/8 text-expense ring-expense/25',
  info: 'bg-brand-soft text-brand-strong ring-brand/20',
};

export function Alert({ tone = 'error', children }: { tone?: Tone; children: ReactNode }) {
  return (
    <div
      role={tone === 'error' ? 'alert' : 'status'}
      className={`rounded-lg px-3 py-2 text-sm ring-1 ring-inset ${STYLES[tone]}`}
    >
      {children}
    </div>
  );
}
