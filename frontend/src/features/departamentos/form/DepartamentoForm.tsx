import { useState, type ReactNode } from 'react';
import { useForm, type FieldPath } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { applyServerFieldErrors } from '../../../shared/api/fieldErrors';
import { ErrorMessage } from '../../../shared/components/ErrorMessage';
import { FormField } from '../../../shared/components/FormField';
import { DireccionAutocomplete } from '../../direcciones/DireccionAutocomplete';
import type { SugerenciaDireccion } from '../../direcciones/direccionesApi';
import { monedas, type DepartamentoPayload, type Estado } from '../api/schemas';
import { ESTADO_LABELS } from '../estadoLabels';
import { departamentoFormSchema, toPayload, type DepartamentoFormValues } from './departamentoSchema';

type Props = {
  defaultValues: DepartamentoFormValues;
  submitLabel: string;
  /** Si rechaza, el error se muestra en el formulario (y sus `fieldErrors`, en cada campo). */
  onSubmit: (payload: DepartamentoPayload) => Promise<void>;
  /** Contenido extra antes de los botones (p. ej. selección de fotos en el alta). */
  children?: ReactNode;
  onCancel?: () => void;
  disabled?: boolean;
  /** Estados que se pueden elegir (el alta no ofrece VENDIDO; la edición, solo las transiciones permitidas). */
  estadosPermitidos: readonly Estado[];
};

const CAMPOS: FieldPath<DepartamentoFormValues>[] = [
  'titulo', 'descripcion', 'precio', 'moneda', 'ambientes', 'dormitorios', 'banos', 'superficieM2', 'estado',
  'direccion.calle', 'direccion.numero', 'direccion.piso', 'direccion.unidad', 'direccion.ciudad',
  'direccion.provincia', 'direccion.codigoPostal', 'direccion.latitud', 'direccion.longitud', 'direccion.placeId',
];

export function DepartamentoForm({
  defaultValues, submitLabel, onSubmit, children, onCancel, disabled = false, estadosPermitidos,
}: Props) {
  const {
    register,
    handleSubmit,
    setValue,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<DepartamentoFormValues>({ resolver: zodResolver(departamentoFormSchema), defaultValues, mode: 'onTouched' });
  const [submitError, setSubmitError] = useState<unknown>(null);
  const bloqueado = disabled || isSubmitting;

  const completarDireccion = (s: SugerenciaDireccion) => {
    const opciones = { shouldValidate: true, shouldDirty: true } as const;
    setValue('direccion.calle', s.calle, opciones);
    setValue('direccion.numero', s.numero ?? '', opciones);
    setValue('direccion.ciudad', s.ciudad ?? '', opciones);
    setValue('direccion.provincia', s.provincia ?? '', opciones);
    setValue('direccion.latitud', s.latitud?.toString() ?? '', opciones);
    setValue('direccion.longitud', s.longitud?.toString() ?? '', opciones);
    setValue('direccion.placeId', s.placeId ?? '', opciones);
  };

  const enviar = handleSubmit(async (values) => {
    setSubmitError(null);
    try {
      await onSubmit(toPayload(values));
    } catch (error) {
      applyServerFieldErrors(error, setError, CAMPOS);
      setSubmitError(error);
    }
  });

  const e = errors;
  const d = errors.direccion;

  return (
    <form className="form" noValidate onSubmit={enviar} aria-label="Datos del departamento">
      <fieldset className="form__section" disabled={bloqueado}>
        <legend>Publicación</legend>
        <FormField label="Título" htmlFor="titulo" error={e.titulo?.message} required className="form__wide">
          <input id="titulo" maxLength={120} {...register('titulo')} aria-invalid={Boolean(e.titulo)} />
        </FormField>
        <FormField label="Descripción" htmlFor="descripcion" error={e.descripcion?.message} className="form__wide">
          <textarea id="descripcion" rows={4} maxLength={4000} {...register('descripcion')} />
        </FormField>
        <FormField label="Precio" htmlFor="precio" error={e.precio?.message} required>
          <input id="precio" inputMode="decimal" placeholder="185000" {...register('precio')} aria-invalid={Boolean(e.precio)} />
        </FormField>
        <FormField label="Moneda" htmlFor="moneda" error={e.moneda?.message} required>
          <select id="moneda" {...register('moneda')}>
            {monedas.map((m) => (
              <option key={m} value={m}>{m}</option>
            ))}
          </select>
        </FormField>
        <FormField label="Estado" htmlFor="estado" error={e.estado?.message} required>
          <select id="estado" {...register('estado')}>
            {estadosPermitidos.map((s) => (
              <option key={s} value={s}>{ESTADO_LABELS[s]}</option>
            ))}
          </select>
        </FormField>
      </fieldset>

      <fieldset className="form__section" disabled={bloqueado}>
        <legend>Características</legend>
        <FormField label="Ambientes" htmlFor="ambientes" error={e.ambientes?.message} required>
          <input id="ambientes" inputMode="numeric" {...register('ambientes')} aria-invalid={Boolean(e.ambientes)} />
        </FormField>
        <FormField label="Dormitorios" htmlFor="dormitorios" error={e.dormitorios?.message} required
          hint="Menos que los ambientes (monoambiente: 0)">
          <input id="dormitorios" inputMode="numeric" {...register('dormitorios')} aria-invalid={Boolean(e.dormitorios)} />
        </FormField>
        <FormField label="Baños" htmlFor="banos" error={e.banos?.message} required>
          <input id="banos" inputMode="numeric" {...register('banos')} aria-invalid={Boolean(e.banos)} />
        </FormField>
        <FormField label="Superficie (m²)" htmlFor="superficieM2" error={e.superficieM2?.message} required>
          <input id="superficieM2" inputMode="decimal" {...register('superficieM2')} aria-invalid={Boolean(e.superficieM2)} />
        </FormField>
      </fieldset>

      <fieldset className="form__section" disabled={bloqueado}>
        <legend>Dirección</legend>
        <div className="form__wide">
          <DireccionAutocomplete onSelect={completarDireccion} />
        </div>
        <FormField label="Calle" htmlFor="calle" error={d?.calle?.message} required>
          <input id="calle" {...register('direccion.calle')} aria-invalid={Boolean(d?.calle)} />
        </FormField>
        <FormField label="Número" htmlFor="numero" error={d?.numero?.message} required>
          <input id="numero" {...register('direccion.numero')} aria-invalid={Boolean(d?.numero)} />
        </FormField>
        <FormField label="Piso" htmlFor="piso" error={d?.piso?.message}>
          <input id="piso" {...register('direccion.piso')} />
        </FormField>
        <FormField label="Unidad" htmlFor="unidad" error={d?.unidad?.message}>
          <input id="unidad" {...register('direccion.unidad')} />
        </FormField>
        <FormField label="Ciudad" htmlFor="ciudad" error={d?.ciudad?.message} required>
          <input id="ciudad" {...register('direccion.ciudad')} aria-invalid={Boolean(d?.ciudad)} />
        </FormField>
        <FormField label="Provincia" htmlFor="provincia" error={d?.provincia?.message} required>
          <input id="provincia" {...register('direccion.provincia')} aria-invalid={Boolean(d?.provincia)} />
        </FormField>
        <FormField label="Código postal" htmlFor="codigoPostal" error={d?.codigoPostal?.message}>
          <input id="codigoPostal" {...register('direccion.codigoPostal')} />
        </FormField>
        <FormField label="Latitud" htmlFor="latitud" error={d?.latitud?.message}>
          <input id="latitud" inputMode="decimal" {...register('direccion.latitud')} />
        </FormField>
        <FormField label="Longitud" htmlFor="longitud" error={d?.longitud?.message}>
          <input id="longitud" inputMode="decimal" {...register('direccion.longitud')} />
        </FormField>
      </fieldset>

      {children}

      {submitError !== null && <ErrorMessage error={submitError} title="No se pudo guardar el departamento" />}

      <div className="form__actions">
        <button type="submit" className="button" disabled={bloqueado}>
          {isSubmitting ? 'Guardando…' : submitLabel}
        </button>
        {onCancel && (
          <button type="button" className="button button--ghost" onClick={onCancel} disabled={isSubmitting}>
            Cancelar
          </button>
        )}
      </div>
    </form>
  );
}
