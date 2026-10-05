import { useState } from 'react';
import { isHttpError } from '../../../shared/api/errors';
import { ErrorMessage } from '../../../shared/components/ErrorMessage';
import { formatDateTime } from '../../../shared/format/format';
import { useReactivarDepartamento } from '../api/queries';

type Props = { id: number; version: number; fechaBaja: string; onReactivado: () => void; onRecargar: () => void };

/**
 * Aviso de un departamento dado de baja, con la opción de reactivarlo. Envía la versión mostrada (412 si otro usuario
 * lo cambió). Si mientras tanto se publicó otro aviso en la misma dirección, el backend responde 409 y se muestra su
 * mensaje.
 */
export function ReactivarDepartamento({ id, version, fechaBaja, onReactivado, onRecargar }: Props) {
  const reactivar = useReactivarDepartamento(id);
  const [conflicto, setConflicto] = useState(false);

  const onReactivar = async () => {
    setConflicto(false);
    try {
      await reactivar.mutateAsync(version);
      onReactivado();
    } catch (e) {
      if (isHttpError(e) && e.status === 412) setConflicto(true);
    }
  };

  return (
    <div className="alert alert--warning" role="alert">
      <strong>Dado de baja el {formatDateTime(fechaBaja)}.</strong>
      <p>No aparece en el listado ni admite cambios. Sus fotos y consultas se conservan.</p>
      {conflicto && <p>Otro usuario lo modificó. Recargalo antes de reactivarlo.</p>}
      <div className="alert__actions">
        {conflicto ? (
          <button type="button" className="button button--ghost" onClick={() => { setConflicto(false); onRecargar(); }}>
            Recargar
          </button>
        ) : (
          <button type="button" className="button" onClick={onReactivar} disabled={reactivar.isPending}>
            {reactivar.isPending ? 'Reactivando…' : 'Reactivar'}
          </button>
        )}
      </div>
      {reactivar.isError && !conflicto && (
        <ErrorMessage error={reactivar.error} title="No se pudo reactivar el departamento" />
      )}
    </div>
  );
}
