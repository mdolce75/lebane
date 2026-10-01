import { Link, useLocation, useParams } from 'react-router';
import { isHttpError } from '../../../shared/api/errors';
import { ErrorMessage } from '../../../shared/components/ErrorMessage';
import { Spinner } from '../../../shared/components/Spinner';
import { formatArea, formatDateTime, formatPrice, plural } from '../../../shared/format/format';
import { useDepartamento } from '../api/queries';
import { ConsultaForm } from '../consultas/ConsultaForm';
import { EstadoBadge } from '../EstadoBadge';
import { Galeria } from './Galeria';
import { NoEncontrado } from './NoEncontrado';

export function DepartamentoDetallePage() {
  const id = Number(useParams().id);
  const aviso = (useLocation().state as { aviso?: string } | null)?.aviso;
  const { data: d, isPending, isError, error, refetch } = useDepartamento(id);

  if (!Number.isInteger(id) || id <= 0) return <NoEncontrado />;
  if (isPending) return <Spinner label="Cargando departamento…" />;
  if (isError) {
    if (isHttpError(error) && error.status === 404) return <NoEncontrado />;
    return <ErrorMessage error={error} title="No se pudo cargar el departamento" onRetry={() => refetch()} />;
  }

  const dir = d.direccion;
  const direccion = [
    `${dir.calle} ${dir.numero}`,
    dir.piso ? `piso ${dir.piso}` : null,
    dir.unidad ? `unidad ${dir.unidad}` : null,
  ].filter(Boolean).join(', ');

  return (
    <article className="detail">
      <nav className="breadcrumb" aria-label="Ruta">
        <Link to="/departamentos">Departamentos</Link> / {d.codigo}
      </nav>
      {aviso && <p className="alert alert--success" role="status">{aviso}.</p>}

      <div className="page-header">
        <div>
          <h1>{d.titulo}</h1>
          <p className="muted">
            {d.codigo} · <EstadoBadge estado={d.estado} />
          </p>
        </div>
        <Link to={`/departamentos/${d.id}/editar`} className="button">Editar</Link>
      </div>

      <div className="detail__layout">
        <Galeria imagenes={d.imagenes} titulo={d.titulo} />

        <div className="detail__info">
          <p className="detail__price">{formatPrice(d.precio, d.moneda)}</p>
          <dl className="facts">
            <div><dt>Ambientes</dt><dd>{d.ambientes}</dd></div>
            <div><dt>Dormitorios</dt><dd>{d.dormitorios}</dd></div>
            <div><dt>Baños</dt><dd>{d.banos}</dd></div>
            <div><dt>Superficie</dt><dd>{formatArea(d.superficieM2)}</dd></div>
            <div><dt>Consultas</dt><dd>{d.cantidadConsultas}</dd></div>
            <div><dt>Fotos</dt><dd>{d.imagenes.length}</dd></div>
          </dl>
          <h2>Dirección</h2>
          <address>
            {direccion}
            <br />
            {dir.ciudad}, {dir.provincia}
            {dir.codigoPostal && ` (${dir.codigoPostal})`}
          </address>
          {dir.latitud !== null && dir.longitud !== null && (
            <a href={`https://www.openstreetmap.org/?mlat=${dir.latitud}&mlon=${dir.longitud}#map=17/${dir.latitud}/${dir.longitud}`}
              target="_blank" rel="noopener noreferrer">
              Ver en el mapa
            </a>
          )}
        </div>
      </div>

      {d.descripcion && (
        <section>
          <h2>Descripción</h2>
          <p className="detail__description">{d.descripcion}</p>
        </section>
      )}

      <section aria-labelledby="consulta-titulo">
        <h2 id="consulta-titulo">Hacer una consulta</h2>
        <ConsultaForm departamentoId={d.id} disponible={d.estado !== 'VENDIDO'} />
      </section>

      <p className="muted detail__meta">
        Publicado el {formatDateTime(d.createdAt)} · Actualizado el {formatDateTime(d.updatedAt)} ·{' '}
        {plural(d.cantidadConsultas, 'consulta')}
      </p>
    </article>
  );
}
