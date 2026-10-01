import { useBackendHealth } from './useBackendHealth';

/** Indicador compacto de disponibilidad del backend (readiness). */
export function BackendStatus() {
  const { data, isPending, isError } = useBackendHealth();

  let state: 'checking' | 'up' | 'down' = 'checking';
  if (!isPending) state = !isError && data?.status === 'UP' ? 'up' : 'down';

  const label = { checking: 'Verificando API…', up: 'API disponible', down: 'API no disponible' }[state];

  return (
    <span className={`status status--${state}`} data-testid="backend-status" aria-live="polite">
      <span className="status__dot" aria-hidden="true" />
      {label}
    </span>
  );
}
