import { useState } from 'react';
import { isHttpError } from '../../../shared/api/errors';
import { ErrorMessage } from '../../../shared/components/ErrorMessage';
import { useDarDeBajaDepartamento, useDepartamento } from '../api/queries';

type Props = { id: number; version: number; codigo: string; onBaja: () => void };

/**
 * Botón de baja lógica con confirmación. Envía la versión mostrada: si otro usuario modificó el departamento desde
 * entonces, el backend responde 412 y se ofrece recargar antes de decidir.
 */
export function BajaDepartamento({ id, version, codigo, onBaja }: Props) {
  const baja = useDarDeBajaDepartamento(id);
  const { refetch } = useDepartamento(id);
  const [conflicto, setConflicto] = useState(false);

  const darDeBaja = async () => {
    const confirmado = window.confirm(
      `¿Dar de baja ${codigo}? Deja de publicarse y no se puede volver a publicar. ` +
        'Sus fotos y consultas se conservan como historial.',
    );
    if (!confirmado) return;
    setConflicto(false);
    try {
      await baja.mutateAsync(version);
      onBaja();
    } catch (e) {
      if (isHttpError(e) && e.status === 412) setConflicto(true);
    }
  };

  return (
    <>
      <button type="button" className="button button--danger" onClick={darDeBaja} disabled={baja.isPending}>
        {baja.isPending ? 'Dando de baja…' : 'Dar de baja'}
      </button>
      {conflicto && (
        <div className="alert alert--warning page-header__alert" role="alert">
          <strong>Otro usuario modificó este departamento.</strong>
          <p>Recargalo para ver los cambios antes de darlo de baja.</p>
          <div className="alert__actions">
            <button type="button" className="button button--ghost" onClick={() => { setConflicto(false); void refetch(); }}>
              Recargar
            </button>
          </div>
        </div>
      )}
      {baja.isError && !conflicto && (
        <div className="page-header__alert">
          <ErrorMessage error={baja.error} title="No se pudo dar de baja el departamento" />
        </div>
      )}
    </>
  );
}
