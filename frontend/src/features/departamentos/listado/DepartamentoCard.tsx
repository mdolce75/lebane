import { Link } from 'react-router';
import { ImageWithFallback } from '../../../shared/components/ImageWithFallback';
import { formatArea, formatPrice, plural } from '../../../shared/format/format';
import type { DepartamentoItem } from '../api/schemas';
import { EstadoBadge } from '../EstadoBadge';

export function DepartamentoCard({ departamento: d }: { departamento: DepartamentoItem }) {
  return (
    <article className="card" aria-labelledby={`dep-${d.id}`}>
      <Link to={`/departamentos/${d.id}`} className="card__media" tabIndex={-1} aria-hidden="true">
        <ImageWithFallback src={d.imagenPrincipalUrl} alt={d.titulo} className="card__image" />
      </Link>
      <div className="card__body">
        <div className="card__top">
          <EstadoBadge estado={d.estado} />
          {d.fechaBaja && <span className="badge badge--baja">Dado de baja</span>}
          <span className="muted card__codigo">{d.codigo}</span>
        </div>
        <h2 id={`dep-${d.id}`} className="card__title">
          <Link to={`/departamentos/${d.id}`}>{d.titulo}</Link>
        </h2>
        <p className="card__price">{formatPrice(d.precio, d.moneda)}</p>
        <p className="muted">
          {d.ciudad}, {d.provincia}
        </p>
        <p className="card__features">
          {plural(d.ambientes, 'amb.', 'amb.')} · {plural(d.dormitorios, 'dorm.', 'dorm.')} ·{' '}
          {plural(d.banos, 'baño')} · {formatArea(d.superficieM2)}
        </p>
        <p className="card__meta muted">
          {plural(d.cantidadImagenes, 'foto')} · {plural(d.cantidadConsultas, 'consulta')}
        </p>
      </div>
    </article>
  );
}
