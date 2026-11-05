import { describe, expect, it } from 'vitest';

import type { Account } from '../types/api';
import { totalsByCurrency, transferDestinations } from './accounts';
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
    expect(formatMoney(-20, 'USD').replace(/\s/g, ' ')).toBe('-20,00 US$');
  });

  it('separa los miles también en cifras de cuatro dígitos, como en las de cinco', () => {
    expect(formatMoney(1234.5, 'EUR').replace(/\s/g, ' ')).toBe('1.234,50 €');
    expect(formatMoney(12345, 'EUR').replace(/\s/g, ' ')).toBe('12.345,00 €');
  });

  it('una fecha sin hora se toma en la zona local, no en UTC', () => {
    const date = parseLocalDate('2026-01-31');
    expect([date.getFullYear(), date.getMonth(), date.getDate()]).toEqual([2026, 0, 31]);
  });
});

const account = (id: string, currencyCode: string, balance = 0, status = 'ACTIVE'): Account =>
  ({ id, name: id, type: 'BANK', currencyCode, balance, status, createdAt: '' }) as Account;

describe('transferDestinations', () => {
  const accounts = [account('nomina', 'EUR'), account('ahorro', 'EUR'), account('usd', 'USD')];

  it('ofrece las otras cuentas de la misma divisa, nunca la de origen', () => {
    expect(transferDestinations(accounts, 'nomina').map((a) => a.id)).toEqual(['ahorro']);
  });

  it('sin otra cuenta en la misma divisa no hay destino posible', () => {
    expect(transferDestinations(accounts, 'usd')).toEqual([]);
  });
});

describe('totalsByCurrency', () => {
  it('suma por divisa, sin cuentas cerradas y redondeando a céntimos', () => {
    expect(
      totalsByCurrency([
        account('a', 'EUR', 0.1),
        account('b', 'EUR', 0.2),
        account('c', 'USD', 50),
        account('d', 'EUR', 999, 'CLOSED'),
      ]),
    ).toEqual([
      { currency: 'EUR', total: 0.3 },
      { currency: 'USD', total: 50 },
    ]);
  });
});
