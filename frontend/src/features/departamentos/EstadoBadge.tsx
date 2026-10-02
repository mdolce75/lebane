import type { Estado } from './api/schemas';
import { ESTADO_LABELS } from './estadoLabels';

export function EstadoBadge({ estado }: { estado: Estado }) {
  return <span className={`badge badge--${estado.toLowerCase()}`}>{ESTADO_LABELS[estado]}</span>;
}
