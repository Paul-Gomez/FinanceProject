import { describe, expect, it } from 'vitest';

import { ApiError, buildUrl } from './api';
import { errorMessage } from './errors';
import { formatMoney, parseLocalDate } from './format';

describe('buildUrl', () => {
  it('omite los filtros vacíos para no mandar ?q=&accountId=', () => {
    expect(
      buildUrl('/api/v1/transactions', { q: '', accountId: undefined, type: null, page: 0 }),
    ).toBe('/api/v1/transactions?page=0');
  });

  it('codifica los valores', () => {
    expect(buildUrl('/x', { q: 'café & pan' })).toBe('/x?q=caf%C3%A9+%26+pan');
  });

  it('sin parámetros no añade "?"', () => {
    expect(buildUrl('/x')).toBe('/x');
  });
});

describe('errorMessage', () => {
  it('un fallo de red no es un ApiError: mensaje de conexión', () => {
    expect(errorMessage(new TypeError('Failed to fetch'))).toMatch(/conectar/);
  });

  it('un 5xx no enseña el mensaje interno del servidor', () => {
    expect(errorMessage(new ApiError(500, 'INTERNAL', 'NullPointerException en X'))).not.toMatch(
      /NullPointer/,
    );
  });

  it('un error de validación incluye los errores por campo', () => {
    const error = new ApiError(400, 'VALIDATION_ERROR', 'datos no válidos', [
      'amount: debe ser mayor que 0',
    ]);
    expect(errorMessage(error)).toBe('Datos no válidos: amount: debe ser mayor que 0');
  });

  it('una respuesta sin cuerpo ni texto no deja el aviso vacío', () => {
    expect(errorMessage(new ApiError(403, 'HTTP_403', ''))).toBe(
      'No se pudo completar la operación (error 403).',
    );
  });

  it('un código conocido usa el texto para el usuario, sin ids', () => {
    const error = new ApiError(
      409,
      'ACCOUNT_BALANCE_NOT_ZERO',
      'No se puede cerrar la cuenta 3f2a… porque…',
    );
    expect(errorMessage(error)).not.toMatch(/3f2a/);
    expect(errorMessage(error)).toMatch(/saldo cero/);
  });

  it('un código desconocido muestra el mensaje del backend con mayúscula inicial', () => {
    expect(errorMessage(new ApiError(409, 'ALGO_NUEVO', 'el movimiento ya está anulado'))).toBe(
      'El movimiento ya está anulado',
    );
  });
});

describe('format', () => {
  it('formatea importes en la divisa de cada cuenta', () => {
    // Intl usa espacios especiales (U+00A0) entre número y símbolo.
    expect(formatMoney(1234.5, 'EUR').replace(/\s/g, ' ')).toBe('1234,50 €');
    expect(formatMoney(-20, 'USD').replace(/\s/g, ' ')).toBe('-20,00 US$');
  });

  it('una fecha sin hora se toma en la zona local, no en UTC', () => {
    const date = parseLocalDate('2026-01-31');
    expect([date.getFullYear(), date.getMonth(), date.getDate()]).toEqual([2026, 0, 31]);
  });
});
