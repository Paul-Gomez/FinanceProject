import { useEffect, useRef, type ReactNode } from 'react';

import { usePageTitle } from './page-title';

interface PageHeaderProps {
  title: string;
  description?: ReactNode;
  /** Controles que acompañan al título (por ejemplo, el periodo del resumen). */
  controls?: ReactNode;
  /** Acciones principales. En el móvil van en una barra fija abajo, al alcance del pulgar. */
  actions?: ReactNode;
}

// La primera página que se carga no mueve el foco; las siguientes sí (ver abajo).
let firstPage = true;

export function PageHeader({ title, description, controls, actions }: PageHeaderProps) {
  const heading = useRef<HTMLHeadingElement>(null);
  usePageTitle(title);

  // Al navegar dentro de la app el navegador no anuncia nada ni mueve el foco:
  // se lleva al título para que el lector de pantalla lea dónde se ha llegado y
  // el teclado siga desde el principio del contenido.
  useEffect(() => {
    if (firstPage) firstPage = false;
    else heading.current?.focus({ preventScroll: true });
  }, []);

  return (
    <header className="mb-6 flex flex-wrap items-end justify-between gap-4">
      <div className="min-w-0">
        <h1
          ref={heading}
          tabIndex={-1}
          className="text-2xl font-semibold tracking-tight focus:outline-none"
        >
          {title}
        </h1>
        {description && <p className="mt-1 text-sm text-muted">{description}</p>}
      </div>
      {controls}
      {actions && (
        <div className="fixed inset-x-0 bottom-0 z-30 flex justify-end gap-2 border-t border-line bg-paper/95 px-4 py-3 pb-[max(0.75rem,env(safe-area-inset-bottom))] backdrop-blur sm:static sm:z-auto sm:flex-wrap sm:items-center sm:border-0 sm:bg-transparent sm:p-0 sm:backdrop-blur-none [&>*]:flex-1 sm:[&>*]:flex-none">
          {actions}
        </div>
      )}
    </header>
  );
}
