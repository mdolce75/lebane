import { Link } from 'react-router';
import { EmptyState } from '../../../shared/components/EmptyState';
import { ErrorMessage } from '../../../shared/components/ErrorMessage';
import { Pagination } from '../../../shared/components/Pagination';
import { Spinner } from '../../../shared/components/Spinner';
import { plural } from '../../../shared/format/format';
import { useDepartamentos } from '../api/queries';
import { DepartamentoCard } from './DepartamentoCard';
import { FiltrosPanel } from './FiltrosPanel';
import { hasActiveFilters, maxPage, PAGE_SIZES, SORT_OPTIONS } from './listadoParams';
import { useListadoSearch } from './useListadoSearch';

export function DepartamentosListPage() {
  const { params, update, clear } = useListadoSearch();
  const { data, isPending, isError, error, isFetching, isPlaceholderData, refetch } = useDepartamentos(params);

  return (
    <section>
      <div className="page-header">
        <div>
          <span className="eyebrow">Panel de ventas</span>
          <h1>Departamentos</h1>
          <p className="page-header__lead">Publicaciones, fotos y consultas de tus unidades en un solo lugar.</p>
        </div>
        <Link to="/departamentos/nuevo" className="button">Nuevo departamento</Link>
      </div>

      <FiltrosPanel params={params} onApply={(filtros) => update(filtros)} onClear={clear} />

      <div className="toolbar">
        <p className="muted" aria-live="polite">
          {data ? plural(data.page.totalElements, 'departamento') : ' '}
          {isFetching && !isPending && <span className="toolbar__updating"> · actualizando…</span>}
        </p>
        <div className="toolbar__controls">
          <label>
            Ordenar por{' '}
            <select value={params.sort} onChange={(e) => update({ sort: e.target.value })}>
              {SORT_OPTIONS.map((o) => (
                <option key={o.value} value={o.value}>{o.label}</option>
              ))}
            </select>
          </label>
          <label>
            Por página{' '}
            <select value={params.size} onChange={(e) => update({ size: Number(e.target.value) })}>
              {PAGE_SIZES.map((s) => (
                <option key={s} value={s}>{s}</option>
              ))}
            </select>
          </label>
        </div>
      </div>

      {isPending && <Spinner label="Cargando departamentos…" />}

      {isError && !data && (
        <ErrorMessage error={error} title="No se pudo cargar el listado" onRetry={() => refetch()} />
      )}

      {data && data.content.length === 0 && (
        <EmptyState title="No hay departamentos para mostrar">
          {hasActiveFilters(params) ? (
            <p>
              Ningún departamento coincide con los filtros.{' '}
              <button type="button" className="link-button" onClick={clear}>
                Limpiar filtros
              </button>
            </p>
          ) : (
            <p>
              Todavía no hay departamentos cargados. <Link to="/departamentos/nuevo">Cargá el primero</Link>.
            </p>
          )}
        </EmptyState>
      )}

      {data && data.content.length > 0 && (
        <>
          <div className={`grid ${isPlaceholderData ? 'grid--stale' : ''}`} aria-busy={isFetching}>
            {data.content.map((d) => (
              <DepartamentoCard key={d.id} departamento={d} />
            ))}
          </div>
          {isError && <ErrorMessage error={error} title="No se pudo actualizar el listado" onRetry={() => refetch()} />}
          <Pagination
            page={data.page.number}
            totalPages={data.page.totalPages}
            lastNavigablePage={maxPage(params.size)}
            disabled={isFetching}
            onChange={(page) => {
              update({ page }, false);
              window.scrollTo?.({ top: 0, behavior: 'smooth' });
            }}
          />
        </>
      )}
    </section>
  );
}
