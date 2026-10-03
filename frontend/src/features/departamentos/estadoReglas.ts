import { estados, type Estado } from './api/schemas';

/**
 * Ciclo de vida del aviso, igual que en el backend (EstadoDepartamento): DISPONIBLE ⇄ RESERVADO, ambos → VENDIDO,
 * y VENDIDO es final. El backend es quien lo hace cumplir; acá solo se evita ofrecer opciones que va a rechazar.
 */
const TRANSICIONES: Record<Estado, readonly Estado[]> = {
  DISPONIBLE: ['RESERVADO', 'VENDIDO'],
  RESERVADO: ['DISPONIBLE', 'VENDIDO'],
  VENDIDO: [],
};

/** Un aviso no se publica directamente como vendido. */
export const ESTADOS_ALTA: readonly Estado[] = estados.filter((estado) => estado !== 'VENDIDO');

/** Estados que se pueden elegir al editar: el actual y aquellos a los que puede pasar (en el orden habitual). */
export function estadosParaEdicion(actual: Estado): readonly Estado[] {
  return estados.filter((estado) => estado === actual || TRANSICIONES[actual].includes(estado));
}

/** Un aviso vendido es un registro cerrado: no se editan sus datos ni sus fotos. */
export function esModificable(estado: Estado): boolean {
  return estado !== 'VENDIDO';
}
