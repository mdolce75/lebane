import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router';
import { isHttpError } from '../../../shared/api/errors';
import { ErrorMessage } from '../../../shared/components/ErrorMessage';
import { Spinner } from '../../../shared/components/Spinner';
import { useActualizarDepartamento, useDepartamento } from '../api/queries';
import type { DepartamentoPayload } from '../api/schemas';
import { ImagenesManager } from '../imagenes/ImagenesManager';
import { NoEncontrado } from '../detalle/NoEncontrado';
import { DepartamentoForm } from './DepartamentoForm';
import { esModificable, estadosParaEdicion } from '../estadoReglas';
import { fromDetalle } from './departamentoSchema';

/**
 * Edición con concurrencia optimista: se envía la versión leída (`If-Match`). Si otro usuario guardó cambios
 * mientras tanto (412), no se pisan: se avisa y se ofrece recargar los datos actuales.
 */
export function DepartamentoEditarPage() {
  const id = Number(useParams().id);
  const navigate = useNavigate();
  const { data, isPending, isError, error, refetch, isRefetching } = useDepartamento(id);
  const actualizar = useActualizarDepartamento(id);
  const [conflicto, setConflicto] = useState(false);

  if (!Number.isInteger(id) || id <= 0) return <NoEncontrado />;
  if (isPending) return <Spinner label="Cargando departamento…" />;
  if (isError) {
    if (isHttpError(error) && error.status === 404) return <NoEncontrado />;
    return <ErrorMessage error={error} title="No se pudo cargar el departamento" onRetry={() => refetch()} />;
  }

  const onSubmit = async (payload: DepartamentoPayload) => {
    setConflicto(false);
    try {
      await actualizar.mutateAsync({ version: data.version, payload });
      navigate(`/departamentos/${id}`, { state: { aviso: 'Cambios guardados' } });
    } catch (e) {
      if (isHttpError(e) && (e.status === 412 || e.code === 'CONCURRENT_MODIFICATION')) {
        setConflicto(true);
        return;
      }
      throw e;
    }
  };

  return (
    <section>
      <nav className="breadcrumb" aria-label="Ruta">
        <Link to="/departamentos">Departamentos</Link> / <Link to={`/departamentos/${id}`}>{data.codigo}</Link> / Editar
      </nav>
      <h1>Editar {data.titulo}</h1>

      {!esModificable(data.estado) && (
        <div className="alert alert--warning" role="alert">
          <strong>Este departamento ya fue vendido.</strong>
          <p>Es un registro cerrado: no se pueden modificar sus datos ni sus fotos.</p>
          <div className="alert__actions">
            <Link to={`/departamentos/${id}`} className="button button--ghost">Volver al detalle</Link>
          </div>
        </div>
      )}

      {conflicto && (
        <div className="alert alert--warning" role="alert">
          <strong>Otro usuario modificó este departamento mientras lo editabas.</strong>
          <p>Para no pisar sus cambios, recargá los datos actuales y volvé a aplicar los tuyos.</p>
          <button type="button" className="button" disabled={isRefetching}
            onClick={async () => {
              await refetch();
              setConflicto(false);
            }}>
            {isRefetching ? 'Recargando…' : 'Recargar datos actuales'}
          </button>
        </div>
      )}

      {esModificable(data.estado) && (
        <>
          {/* key: al recargar una versión nueva, el formulario se reinicia con esos datos. */}
          <DepartamentoForm
            key={data.version}
            defaultValues={fromDetalle(data)}
            estadosPermitidos={estadosParaEdicion(data.estado)}
            submitLabel="Guardar cambios"
            onSubmit={onSubmit}
            onCancel={() => navigate(`/departamentos/${id}`)}
            disabled={conflicto}
          />

          <ImagenesManager departamentoId={id} imagenes={data.imagenes} />
        </>
      )}
    </section>
  );
}
