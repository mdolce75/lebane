import { useEffect } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { FormField } from '../../../shared/components/FormField';
import { estados, monedas } from '../api/schemas';
import { ESTADO_LABELS } from '../estadoLabels';
import { filtrosFromParams, filtrosSchema, paramsFromFiltros, type FiltrosForm } from './filtrosSchema';
import type { ListadoParams } from './listadoParams';

type Props = {
  params: ListadoParams;
  onApply: (filtros: Partial<ListadoParams>) => void;
  onClear: () => void;
};

/**
 * Filtros del listado. Se aplican al enviar (no en cada tecla): cada aplicación es un request al servidor, que
 * filtra en PostgreSQL. El cliente nunca filtra resultados.
 */
export function FiltrosPanel({ params, onApply, onClear }: Props) {
  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<FiltrosForm>({ resolver: zodResolver(filtrosSchema), defaultValues: filtrosFromParams(params) });

  // Si la URL cambia desde afuera (atrás/adelante, "limpiar"), el formulario la refleja.
  useEffect(() => {
    reset(filtrosFromParams(params));
  }, [params, reset]);

  return (
    <form className="filters" aria-label="Filtros" noValidate onSubmit={handleSubmit((form) => onApply(paramsFromFiltros(form)))}>
      <div className="filters__grid">
        <FormField label="Buscar en el título" error={errors.q?.message} htmlFor="f-q">
          <input id="f-q" type="search" placeholder="Ej.: balcón" {...register('q')} />
        </FormField>
        <FormField label="Ciudad" error={errors.ciudad?.message} htmlFor="f-ciudad">
          <input id="f-ciudad" placeholder="Ej.: Rosario" {...register('ciudad')} />
        </FormField>
        <FormField label="Moneda" error={errors.moneda?.message} htmlFor="f-moneda">
          <select id="f-moneda" {...register('moneda')}>
            <option value="">Cualquiera</option>
            {monedas.map((m) => (
              <option key={m} value={m}>{m}</option>
            ))}
          </select>
        </FormField>
        <FormField label="Precio mínimo" error={errors.precioMin?.message} htmlFor="f-precio-min">
          <input id="f-precio-min" inputMode="decimal" {...register('precioMin')} />
        </FormField>
        <FormField label="Precio máximo" error={errors.precioMax?.message} htmlFor="f-precio-max">
          <input id="f-precio-max" inputMode="decimal" {...register('precioMax')} />
        </FormField>
        <FormField label="Ambientes (mín.)" error={errors.ambientesMin?.message} htmlFor="f-ambientes">
          <input id="f-ambientes" inputMode="numeric" {...register('ambientesMin')} />
        </FormField>
        <FormField label="Fotos" htmlFor="f-fotos">
          <select id="f-fotos" {...register('conImagenes')}>
            <option value="">Indistinto</option>
            <option value="true">Con fotos</option>
            <option value="false">Sin fotos</option>
          </select>
        </FormField>
        <fieldset className="field">
          <legend>Estado</legend>
          <div className="checkbox-group">
            {estados.map((estado) => (
              <label key={estado} className="checkbox">
                <input type="checkbox" value={estado} {...register('estado')} />
                {ESTADO_LABELS[estado]}
              </label>
            ))}
          </div>
        </fieldset>
      </div>
      <div className="filters__actions">
        <button type="submit" className="button">Aplicar filtros</button>
        <button type="button" className="button button--ghost" onClick={onClear}>Limpiar</button>
      </div>
    </form>
  );
}

