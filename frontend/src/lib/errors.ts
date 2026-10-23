import { ApiError } from './api';

/**
 * Textos para los errores que se pueden provocar desde esta interfaz. Los
 * mensajes del backend están pensados para quien usa la API (llevan ids y
 * nombres de estados, como "la cuenta 3f2a... está en estado ARCHIVED"); aquí
 * se cambian por algo que entienda cualquiera. Un código que no esté en la
 * lista muestra el mensaje del backend tal cual.
 */
const MESSAGES: Record<string, string> = {
  ACCOUNT_BALANCE_NOT_ZERO:
    'Solo se puede cerrar una cuenta con saldo cero. Deja el saldo a cero y vuelve a intentarlo.',
  ACCOUNT_NOT_ACTIVE: 'La cuenta no está activa y no admite movimientos.',
  ACCOUNT_NOT_FOUND: 'La cuenta ya no existe.',
  ACCESS_DENIED: 'No tienes permiso para hacer eso.',
  CATEGORY_IN_USE:
    'La categoría tiene movimientos, presupuestos o subcategorías y no se puede eliminar.',
  CATEGORY_READ_ONLY: 'Las categorías del sistema no se pueden modificar ni eliminar.',
  CATEGORY_NOT_FOUND: 'La categoría ya no existe.',
  BUDGET_NOT_FOUND: 'El presupuesto ya no existe.',
  BUDGET_ALREADY_EXISTS: 'Ya tienes un presupuesto para esa categoría y periodo.',
  CURRENCY_MISMATCH: 'Las dos cuentas tienen que ser de la misma divisa.',
  SAME_ACCOUNT_TRANSFER: 'La cuenta de origen y la de destino tienen que ser distintas.',
  INVALID_STATUS_TRANSITION: 'La cuenta no puede pasar a ese estado.',
  ACCOUNT_LOCKED: 'Demasiados intentos fallidos. Espera unos minutos antes de volver a intentarlo.',
};

/** Texto de error para el usuario a partir de lo que lanzó una petición. */
export function errorMessage(error: unknown): string {
  if (!(error instanceof ApiError))
    return 'No se pudo conectar con el servidor. Inténtalo de nuevo.';
  if (error.status >= 500) return 'Ha ocurrido un error en el servidor. Inténtalo de nuevo.';
  const known = MESSAGES[error.code];
  if (known) return known;
  // Algunas respuestas no traen el JSON de error de la API (un 403 de CORS, un
  // 502 del proxy...): sin esto el usuario vería un aviso vacío.
  if (!error.message.trim()) return `No se pudo completar la operación (error ${error.status}).`;
  const message = capitalize(error.message);
  return error.fieldErrors.length > 0 ? `${message}: ${error.fieldErrors.join('; ')}` : message;
}

const capitalize = (text: string) => text.charAt(0).toUpperCase() + text.slice(1);
