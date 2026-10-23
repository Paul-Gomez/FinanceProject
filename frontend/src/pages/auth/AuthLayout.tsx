import type { ReactNode } from 'react';

/** Marco común de login y registro: una columna centrada, sin la navegación de la app. */
export function AuthLayout({
  title,
  subtitle,
  children,
}: {
  title: string;
  subtitle: string;
  children: ReactNode;
}) {
  return (
    <main className="flex min-h-screen items-center justify-center px-4 py-10">
      <div className="w-full max-w-sm">
        <div className="mb-8 text-center">
          <span
            aria-hidden="true"
            className="mx-auto mb-4 grid size-10 place-items-center rounded-lg bg-brand text-base font-semibold text-white"
          >
            F
          </span>
          <h1 className="text-2xl font-semibold tracking-tight">{title}</h1>
          <p className="mt-1 text-sm text-muted">{subtitle}</p>
        </div>
        <div className="rounded-xl bg-surface p-6 ring-1 ring-line">{children}</div>
      </div>
    </main>
  );
}
