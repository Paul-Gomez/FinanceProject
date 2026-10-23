import { useEffect, useId, useRef, type ReactNode } from 'react';

interface ModalProps {
  title: string;
  description?: string;
  onClose: () => void;
  children: ReactNode;
}

/**
 * Se monta solo mientras está abierto. Usa el <dialog> nativo con showModal():
 * el navegador ya se encarga de atrapar el foco dentro, de cerrar con Escape y
 * de que el resto de la página no se pueda tocar.
 */
export function Modal({ title, description, onClose, children }: ModalProps) {
  const ref = useRef<HTMLDialogElement>(null);
  const titleId = useId();

  useEffect(() => {
    const dialog = ref.current;
    if (!dialog) return;
    // Al cerrar, el foco vuelve al botón que abrió el diálogo.
    const opener = document.activeElement as HTMLElement | null;
    dialog.showModal();
    return () => opener?.focus();
  }, []);

  return (
    <dialog
      ref={ref}
      aria-labelledby={titleId}
      onCancel={(event) => {
        event.preventDefault();
        onClose();
      }}
      onMouseDown={(event) => {
        // Clic en el fondo oscuro (fuera de la caja): cerrar.
        if (event.target === ref.current) onClose();
      }}
      className="m-auto w-[calc(100%-2rem)] max-w-lg rounded-xl bg-surface p-0 text-ink shadow-xl backdrop:bg-ink/40"
    >
      <div className="p-6">
        <h2 id={titleId} className="text-base font-semibold">
          {title}
        </h2>
        {description && <p className="mt-1 text-sm text-muted">{description}</p>}
        <div className="mt-5">{children}</div>
      </div>
    </dialog>
  );
}
