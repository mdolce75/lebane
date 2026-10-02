import { describe, expect, it } from 'vitest';
import { formatArea, formatBytes, formatPrice, plural } from './format';

// Intl puede usar espacios no separables entre el código de moneda y el importe.
const normalize = (value: string) => value.replace(/\s/g, ' ');

describe('format', () => {
  it('formatea precios en formato argentino con su moneda', () => {
    expect(normalize(formatPrice(185000, 'USD'))).toBe('USD 185.000');
    expect(normalize(formatPrice(76000000, 'ARS'))).toBe('ARS 76.000.000');
  });

  it('muestra siempre dos decimales cuando hay centavos', () => {
    expect(normalize(formatPrice(132500.5, 'USD'))).toBe('USD 132.500,50');
    expect(normalize(formatPrice(99.99, 'ARS'))).toBe('ARS 99,99');
  });

  it('formatea superficie, tamaños y plurales', () => {
    expect(formatArea(72.5)).toBe('72,5 m²');
    expect(formatBytes(8600)).toBe('8 KB');
    expect(formatBytes(2.5 * 1024 * 1024)).toBe('2.5 MB');
    expect(plural(1, 'foto')).toBe('1 foto');
    expect(plural(3, 'consulta')).toBe('3 consultas');
  });
});
