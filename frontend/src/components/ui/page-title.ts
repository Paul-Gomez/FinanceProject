import { useEffect } from 'react';

/** Pone el título de la pestaña: así el historial y el lector de pantalla saben dónde se está. */
export function usePageTitle(title: string) {
  useEffect(() => {
    document.title = `${title} · FinCore`;
  }, [title]);
}
