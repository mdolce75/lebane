import { describe, expect, it, vi } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { detalle } from '../../../test/fixtures';
import { apiError, mockApi, pngFile } from '../../../test/mockApi';
import { jsonResponse, renderRoute } from '../../../test/utils';

const DETALLE = '/api/v1/departamentos/1';

describe('Edición de departamento', () => {
  it('precarga los datos y guarda con If-Match de la versión leída', async () => {
    const api = mockApi()
      .on('GET', DETALLE, detalle())
      .on('PUT', DETALLE, (call: { body: unknown }) => jsonResponse({ ...detalle(), ...(call.body as object), version: 4 }));
    renderRoute('/departamentos/1/editar');

    const titulo = await screen.findByLabelText(/^Título/);
    expect(titulo).toHaveValue('Luminoso 3 ambientes con balcón en Palermo');
    expect(screen.getByLabelText(/^Piso/)).toHaveValue('7');

    await userEvent.clear(titulo);
    await userEvent.type(titulo, 'Título editado');
    await userEvent.selectOptions(screen.getByLabelText(/^Estado/), 'RESERVADO');
    await userEvent.click(screen.getByRole('button', { name: 'Guardar cambios' }));

    expect(await screen.findByText('Cambios guardados.')).toBeInTheDocument();
    const [put] = api.requests('PUT', DETALLE);
    expect(put?.headers.get('If-Match')).toBe('"3"');
    expect(put?.body).toMatchObject({ titulo: 'Título editado', estado: 'RESERVADO', direccion: { piso: '7' } });
  });

  it('ante un conflicto (412) no pisa cambios y ofrece recargar', async () => {
    let lecturas = 0;
    mockApi()
      .on('GET', DETALLE, () => {
        lecturas++;
        return jsonResponse(lecturas === 1 ? detalle() : detalle({ titulo: 'Editado por otro usuario', version: 7 }));
      })
      .on('PUT', DETALLE, () => apiError(412, 'PRECONDITION_FAILED'));
    renderRoute('/departamentos/1/editar');

    await userEvent.click(await screen.findByRole('button', { name: 'Guardar cambios' }));

    expect(await screen.findByText('Otro usuario modificó este departamento mientras lo editabas.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Guardar cambios' })).toBeDisabled();

    await userEvent.click(screen.getByRole('button', { name: 'Recargar datos actuales' }));

    await waitFor(() => expect(screen.getByLabelText(/^Título/)).toHaveValue('Editado por otro usuario'));
    expect(screen.queryByText(/Otro usuario modificó/)).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Guardar cambios' })).toBeEnabled();
  });

  it('gestiona las fotos: elimina con confirmación y sube nuevas hasta el máximo', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    const api = mockApi()
      .on('GET', DETALLE, detalle())
      .on('DELETE', '/api/v1/departamentos/1/imagenes/11', () => new Response(null, { status: 204 }))
      .on('POST', '/api/v1/departamentos/1/imagenes', () =>
        jsonResponse({ id: 20, url: 'http://cdn/n.png', contentType: 'image/png', sizeBytes: 2048, posicion: 2 }, { status: 201 }));
    renderRoute('/departamentos/1/editar');

    expect(await screen.findByRole('heading', { name: 'Fotos (2 de 5)' })).toBeInTheDocument();
    expect(screen.getByText(/Podés agregar 3 más/)).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Eliminar foto 2' }));
    await waitFor(() => expect(api.requests('DELETE', '/api/v1/departamentos/1/imagenes/11')).toHaveLength(1));
    expect(window.confirm).toHaveBeenCalled();

    await userEvent.upload(screen.getByLabelText('Agregar fotos'), [pngFile('nueva.png')]);
    await userEvent.click(await screen.findByRole('button', { name: 'Subir 1 foto(s)' }));

    await waitFor(() => expect(api.requests('POST', '/api/v1/departamentos/1/imagenes')).toHaveLength(1));
    const actuales = screen.getByRole('list', { name: 'Fotos actuales' });
    expect(within(actuales).getAllByRole('listitem').length).toBeGreaterThan(0);
  });
});
