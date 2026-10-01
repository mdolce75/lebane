import { useState } from 'react';
import { useForm, type FieldPath } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { applyServerFieldErrors } from '../../../shared/api/fieldErrors';
import { ErrorMessage } from '../../../shared/components/ErrorMessage';
import { FormField } from '../../../shared/components/FormField';
import { useCrearConsulta } from '../api/queries';
import { consultaSchema, type ConsultaFormValues } from './consultaSchema';

const CAMPOS: FieldPath<ConsultaFormValues>[] = ['nombre', 'email', 'telefono', 'mensaje'];

type Props = { departamentoId: number; disponible: boolean };

export function ConsultaForm({ departamentoId, disponible }: Props) {
  const crear = useCrearConsulta(departamentoId);
  const [enviada, setEnviada] = useState(false);
  const {
    register,
    handleSubmit,
    reset,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<ConsultaFormValues>({
    resolver: zodResolver(consultaSchema),
    defaultValues: { nombre: '', email: '', telefono: '', mensaje: '' },
  });

  if (!disponible) {
    return <p className="muted">Este departamento ya fue vendido y no recibe nuevas consultas.</p>;
  }

  const enviar = handleSubmit(async (values) => {
    setEnviada(false);
    try {
      await crear.mutateAsync({ ...values, telefono: values.telefono || null });
      reset();
      setEnviada(true);
    } catch (error) {
      applyServerFieldErrors(error, setError, CAMPOS);
    }
  });

  return (
    <form className="form form--compact" noValidate onSubmit={enviar} aria-label="Consulta">
      <FormField label="Nombre" htmlFor="c-nombre" error={errors.nombre?.message} required>
        <input id="c-nombre" autoComplete="name" {...register('nombre')} />
      </FormField>
      <FormField label="Email" htmlFor="c-email" error={errors.email?.message} required>
        <input id="c-email" type="email" autoComplete="email" {...register('email')} />
      </FormField>
      <FormField label="Teléfono" htmlFor="c-telefono" error={errors.telefono?.message}>
        <input id="c-telefono" type="tel" autoComplete="tel" {...register('telefono')} />
      </FormField>
      <FormField label="Mensaje" htmlFor="c-mensaje" error={errors.mensaje?.message} required className="form__wide">
        <textarea id="c-mensaje" rows={4} {...register('mensaje')} />
      </FormField>
      {crear.isError && <ErrorMessage error={crear.error} title="No se pudo enviar la consulta" />}
      {enviada && (
        <p className="alert alert--success" role="status">¡Consulta enviada! Te vamos a contactar a la brevedad.</p>
      )}
      <div className="form__actions">
        <button type="submit" className="button" disabled={isSubmitting}>
          {isSubmitting ? 'Enviando…' : 'Enviar consulta'}
        </button>
      </div>
    </form>
  );
}
