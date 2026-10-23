import { QueryClient } from '@tanstack/react-query';

import { ApiError } from './api';

// Un 4xx (validación, permisos, no encontrado) dará lo mismo si se repite: solo
// se reintenta un par de veces ante fallos de red o errores 5xx.
export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      refetchOnWindowFocus: false,
      retry: (failures, error) =>
        !(error instanceof ApiError && error.status < 500) && failures < 2,
    },
  },
});
