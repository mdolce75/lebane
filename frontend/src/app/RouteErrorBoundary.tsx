import { useRouteError } from 'react-router';
import { ErrorMessage } from '../shared/components/ErrorMessage';

/** Error boundary de rutas: no muestra detalles técnicos al usuario. */
export function RouteErrorBoundary() {
  const error = useRouteError();
  return (
    <main className="main">
      <ErrorMessage error={error} onRetry={() => window.location.reload()} />
    </main>
  );
}
