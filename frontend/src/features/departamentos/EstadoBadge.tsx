import type { Estado } from './api/schemas';
import { ESTADO_LABELS } from './estadoLabels';

/**
 * Etiqueta de estado. Un departamento dado de baja muestra solo "Dado de baja": conserva su estado comercial (con ese
 * vuelve al reactivarlo), pero mostrarlo como "Disponible" sería contradictorio.
 */
export function EstadoBadge({ estado, fechaBaja = null }: { estado: Estado; fechaBaja?: string | null }) {
  if (fechaBaja) return <span className="badge badge--baja">Dado de baja</span>;
  return <span className={`badge badge--${estado.toLowerCase()}`}>{ESTADO_LABELS[estado]}</span>;
}
