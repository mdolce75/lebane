import { useState } from 'react';
import { EmptyState } from '../../../shared/components/EmptyState';
import { ErrorMessage } from '../../../shared/components/ErrorMessage';
import { Pagination } from '../../../shared/components/Pagination';
import { Spinner } from '../../../shared/components/Spinner';
import { formatDateTime, plural } from '../../../shared/format/format';
import { useConsultas } from '../api/queries';

type Props = { departamentoId: number };

/** Consultas recibidas por el departamento, paginadas en el servidor (la más reciente primero). */
export function ConsultasRecibidas({ departamentoId }: Props) {
  const [page, setPage] = useState(0);
  const { data, isPending, isError, error, refetch, isFetching } = useConsultas(departamentoId, page);

  if (isPending) return <Spinner label="Cargando consultas…" />;
  if (isError && !data) {
    return <ErrorMessage error={error} title="No se pudieron cargar las consultas" onRetry={() => refetch()} />;
  }
  if (data.page.totalElements === 0) {
    return <EmptyState title="Todavía no hay consultas">Las consultas que envíen los interesados aparecen acá.</EmptyState>;
  }

  return (
    <div className="consultas">
      <p className="muted">{plural(data.page.totalElements, 'consulta recibida', 'consultas recibidas')}</p>
      <ul className="consultas__lista" aria-busy={isFetching}>
        {data.content.map((c) => (
          <li key={c.id} className="consultas__item">
            <div className="consultas__cabecera">
              <strong>{c.nombre}</strong>
              <time className="muted" dateTime={c.createdAt}>{formatDateTime(c.createdAt)}</time>
            </div>
            <p className="consultas__contacto">
              <a href={`mailto:${c.email}`}>{c.email}</a>
              {c.telefono && <> · <a href={`tel:${c.telefono.replace(/[^+\d]/g, '')}`}>{c.telefono}</a></>}
            </p>
            <p className="consultas__mensaje">{c.mensaje}</p>
          </li>
        ))}
      </ul>
      {isError && <ErrorMessage error={error} title="No se pudo cargar la página" onRetry={() => refetch()} />}
      <Pagination page={data.page.number} totalPages={data.page.totalPages} disabled={isFetching} onChange={setPage} />
    </div>
  );
}
