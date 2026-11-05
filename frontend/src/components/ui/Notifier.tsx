import { useCallback, useEffect, useState, type ReactNode } from 'react';

import { NotifyContext } from './notify';

const VISIBLE_MS = 4000;

/**
 * Confirma las acciones que cierran un formulario: sin esto, guardar un
 * movimiento solo hacía desaparecer el diálogo y no quedaba claro si había ido
 * bien. La región role="status" está siempre en la página para que el lector de
 * pantalla anuncie cada mensaje nuevo.
 */
export function Notifier({ children }: { children: ReactNode }) {
  const [message, setMessage] = useState<{ text: string; id: number } | null>(null);

  const notify = useCallback((text: string) => setMessage({ text, id: Date.now() }), []);

  useEffect(() => {
    if (!message) return;
    const timer = setTimeout(() => setMessage(null), VISIBLE_MS);
    return () => clearTimeout(timer);
  }, [message]);

  return (
    <NotifyContext value={notify}>
      {children}
      <div
        role="status"
        className="pointer-events-none fixed inset-x-0 bottom-24 z-50 flex justify-center px-4 sm:bottom-6"
      >
        {message && (
          <p
            key={message.id}
            className="rounded-lg bg-ink px-4 py-2.5 text-sm font-medium text-paper shadow-[0_6px_20px_-6px_rgb(28_28_26/0.45)] transition-[opacity,translate] duration-200 ease-out starting:opacity-0 motion-safe:starting:translate-y-2"
          >
            {message.text}
          </p>
        )}
      </div>
    </NotifyContext>
  );
}
