import type { ReactNode } from 'react';

interface CardProps {
  title?: string;
  actions?: ReactNode;
  className?: string;
  children: ReactNode;
}

export function Card({ title, actions, className = '', children }: CardProps) {
  return (
    <section className={`rounded-xl bg-surface p-5 ring-1 ring-line ${className}`}>
      {(title || actions) && (
        <div className="mb-4 flex items-center justify-between gap-2">
          {title && <h2 className="text-sm font-semibold">{title}</h2>}
          {actions}
        </div>
      )}
      {children}
    </section>
  );
}
