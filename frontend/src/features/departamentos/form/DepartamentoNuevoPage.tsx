import { useState } from 'react';
import { Link, useNavigate } from 'react-router';
import { useQueryClient } from '@tanstack/react-query';
import { useCrearDepartamento, departamentosKeys } from '../api/queries';
import type { DepartamentoDetalle, DepartamentoPayload } from '../api/schemas';
import { ImagePicker } from '../imagenes/ImagePicker';
import { MAX_IMAGES } from '../imagenes/imageValidation';
import { uploadSequentially } from '../imagenes/uploadSequentially';
import { useImageSelection } from '../imagenes/useImageSelection';
import { DepartamentoForm } from './DepartamentoForm';
import { valoresIniciales } from './departamentoSchema';

/**
 * Alta: crea el departamento y después sube las fotos de a una. Si alguna foto falla, el departamento ya existe:
 * se informa qué fotos fallaron y se pueden reintentar solo esas, sin volver a crear el departamento.
 */
export function DepartamentoNuevoPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const crear = useCrearDepartamento();
  const seleccion = useImageSelection(MAX_IMAGES);
  const [creado, setCreado] = useState<DepartamentoDetalle | null>(null);
  const [subiendo, setSubiendo] = useState(false);
  const [fallidas, setFallidas] = useState(0);

  const subirFotos = async (departamento: DepartamentoDetalle) => {
    setSubiendo(true);
    try {
      const errores = await uploadSequentially(departamento.id, seleccion.items, seleccion.setUpload);
      setFallidas(errores);
      await queryClient.invalidateQueries({ queryKey: departamentosKeys.all });
      if (errores === 0) {
        navigate(`/departamentos/${departamento.id}`, { state: { aviso: 'Departamento creado' } });
      }
    } finally {
      setSubiendo(false);
    }
  };

  const onSubmit = async (payload: DepartamentoPayload) => {
    const departamento = await crear.mutateAsync(payload);
    setCreado(departamento);
    await subirFotos(departamento);
  };

  return (
    <section>
      <nav className="breadcrumb" aria-label="Ruta">
        <Link to="/departamentos">Departamentos</Link> / Nuevo
      </nav>
      <h1>Nuevo departamento</h1>

      {creado && fallidas > 0 && !subiendo && (
        <div className="alert alert--warning" role="alert">
          <strong>El departamento {creado.codigo} se creó, pero {fallidas} foto(s) no se pudieron subir.</strong>
          <p>Revisá el detalle de cada foto abajo. Podés reintentar ahora o agregarlas más tarde desde la edición.</p>
          <div className="alert__actions">
            <button type="button" className="button" onClick={() => subirFotos(creado)}>
              Reintentar fotos fallidas
            </button>
            <Link to={`/departamentos/${creado.id}`} className="button button--ghost">Ir al departamento</Link>
          </div>
        </div>
      )}

      <DepartamentoForm
        defaultValues={valoresIniciales}
        submitLabel={seleccion.items.length > 0 ? `Crear y subir ${seleccion.items.length} foto(s)` : 'Crear departamento'}
        onSubmit={onSubmit}
        onCancel={() => navigate('/departamentos')}
        disabled={creado !== null}
      >
        <fieldset className="form__section form__section--full">
          <legend>Fotos</legend>
          <ImagePicker
            items={seleccion.items}
            rejected={seleccion.rejected}
            remaining={seleccion.remaining}
            total={MAX_IMAGES}
            onAdd={(files) => void seleccion.add(files)}
            onRemove={seleccion.remove}
            disabled={subiendo || creado !== null}
          />
          {subiendo && <p className="muted" role="status">Subiendo fotos…</p>}
        </fieldset>
      </DepartamentoForm>
    </section>
  );
}
