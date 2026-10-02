import type { Estado } from './api/schemas';

export const ESTADO_LABELS: Record<Estado, string> = {
  DISPONIBLE: 'Disponible',
  RESERVADO: 'Reservado',
  VENDIDO: 'Vendido',
};
