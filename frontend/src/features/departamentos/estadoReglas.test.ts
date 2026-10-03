import { describe, expect, it } from 'vitest';
import { ESTADOS_ALTA, esModificable, estadosParaEdicion } from './estadoReglas';

describe('reglas de estado (iguales al backend)', () => {
  it('el alta no ofrece VENDIDO', () => {
    expect(ESTADOS_ALTA).toEqual(['DISPONIBLE', 'RESERVADO']);
  });

  it('la edición ofrece el estado actual y sus transiciones permitidas', () => {
    expect(estadosParaEdicion('DISPONIBLE')).toEqual(['DISPONIBLE', 'RESERVADO', 'VENDIDO']);
    expect(estadosParaEdicion('RESERVADO')).toEqual(['DISPONIBLE', 'RESERVADO', 'VENDIDO']);
    expect(estadosParaEdicion('VENDIDO')).toEqual(['VENDIDO']);
  });

  it('un vendido no es modificable', () => {
    expect(esModificable('VENDIDO')).toBe(false);
    expect(esModificable('DISPONIBLE')).toBe(true);
    expect(esModificable('RESERVADO')).toBe(true);
  });
});
