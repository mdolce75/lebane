import { useState } from 'react';
import { Link, useNavigate } from 'react-router';
import { isHttpError } from '../../../shared/api/errors';
import { useCrearDepartamento } from '../api/queries';
import type { DepartamentoPayload } from '../api/schemas';
import { ImagePicker } from '../imagenes/ImagePicker';
import { MAX_IMAGES } from '../imagenes/imageValidation';
import { useImageSelection } from '../imagenes/useImageSelection';
import { DepartamentoForm } from './DepartamentoForm';
import { ESTADOS_ALTA } from '../estadoReglas';
import { valoresIniciales } from './departamentoSchema';

/**
 * Alta: los datos y las fotos viajan en un solo request (`POST /api/departamentos`, multipart). Es todo o nada: si
 * una foto es rechazada o el storage falla, el departamento no se crea y se puede corregir y volver a enviar.
 */
export function DepartamentoNuevoPage() {
  const navigate = useNavigate();
  const crear = useCrearDepartamento();
  const seleccion = useImageSelection(MAX_IMAGES);
  const [enviando, setEnviando] = useState(false);

  const onSubmit = async (payload: DepartamentoPayload) => {
    setEnviando(true);
    seleccion.items.forEach((item) => seleccion.setUpload(item.id, { status: 'uploading' }));
    try {
      const departamento = await crear.mutateAsync({ payload, imagenes: seleccion.items.map((item) => item.file) });
      navigate(`/departamentos/${departamento.id}`, { state: { aviso: 'Departamento creado' } });
    } catch (error) {
      // El backend informa cada foto rechazada como `imagenes[i]`, en el orden en que se enviaron.
      seleccion.items.forEach((item, index) => {
        const motivo = isHttpError(error) ? error.fieldErrors[`imagenes[${index}]`] : undefined;
        seleccion.setUpload(item.id, motivo
          ? { status: 'error', message: `La foto ${motivo}.`, requestId: isHttpError(error) ? error.requestId : null }
          : { status: 'pending' });
      });
      throw error;
    } finally {
      setEnviando(false);
    }
  };

  return (
    <section>
      <nav className="breadcrumb" aria-label="Ruta">
        <Link to="/departamentos">Departamentos</Link> / Nuevo
      </nav>
      <h1>Nuevo departamento</h1>

      <DepartamentoForm
        estadosPermitidos={ESTADOS_ALTA}
        defaultValues={valoresIniciales}
        submitLabel={seleccion.items.length > 0 ? `Crear y subir ${seleccion.items.length} foto(s)` : 'Crear departamento'}
        onSubmit={onSubmit}
        onCancel={() => navigate('/departamentos')}
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
            disabled={enviando}
          />
          {enviando && seleccion.items.length > 0 && <p className="muted" role="status">Subiendo fotos…</p>}
        </fieldset>
      </DepartamentoForm>
    </section>
  );
}
