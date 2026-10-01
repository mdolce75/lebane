import { isHttpError } from '../api/errors';

type Props = {
  error: unknown;
  title?: string;
  onRetry?: () => void;
};

/**
 * Muestra un error apto para el usuario. Nunca renderiza stack traces ni mensajes técnicos de errores
 * desconocidos; si existe, muestra el requestId para que soporte pueda correlacionarlo con los logs.
 */
export function ErrorMessage({ error, title = 'Algo salió mal', onRetry }: Props) {
  const message = isHttpError(error) ? error.message : 'Ocurrió un error inesperado.';
  const requestId = isHttpError(error) ? error.requestId : null;

  return (
    <div role="alert" className="alert alert--error">
      <strong>{title}</strong>
      <p>{message}</p>
      {requestId && (
        <p className="alert__meta">
          Código de seguimiento: <code>{requestId}</code>
        </p>
      )}
      {onRetry && (
        <button type="button" className="button" onClick={onRetry}>
          Reintentar
        </button>
      )}
    </div>
  );
}
