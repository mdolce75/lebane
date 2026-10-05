import { describe, expect, it, vi } from 'vitest';
import { fireEvent, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { detalle, item, pagina } from '../../../test/fixtures';
import { apiError, mockApi } from '../../../test/mockApi';
import { jsonResponse, renderRoute } from '../../../test/utils';

const DETALLE = '/api/v1/departamentos/1';
const CONSULTAS = '/api/v1/departamentos/1/consultas';

describe('Detalle de departamento', () => {
  it('muestra todos los datos y la galería', async () => {
    mockApi().on('GET', DETALLE, detalle());

    renderRoute('/departamentos/1');

    expect(await screen.findByRole('heading', { level: 1, name: 'Luminoso 3 ambientes con balcón en Palermo' })).toBeInTheDocument();
    expect(screen.getByText('Frente, piso alto, cocina integrada.')).toBeInTheDocument();
    expect(screen.getByText('Gorriti 4850, piso 7, unidad B', { exact: false })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Ver en el mapa' })).toHaveAttribute('href', expect.stringContaining('mlat=-34.5889'));
    expect(screen.getByRole('link', { name: 'Editar' })).toHaveAttribute('href', '/departamentos/1/editar');
    expect(screen.getByRole('img', { name: /foto 1 de 2/ })).toHaveAttribute('src', expect.stringContaining('/a.png'));

    await userEvent.click(screen.getByRole('button', { name: 'Ver foto 2' }));
    expect(screen.getByRole('img', { name: /foto 2 de 2/ })).toHaveAttribute('src', expect.stringContaining('/b.png'));
  });

  it('muestra un placeholder si la foto principal no carga', async () => {
    mockApi().on('GET', DETALLE, detalle());
    renderRoute('/departamentos/1');

    fireEvent.error(await screen.findByRole('img', { name: /foto 1 de 2/ }));

    expect(screen.getByRole('img', { name: /Imagen no disponible/ })).toBeInTheDocument();
  });

  it('informa "no encontrado" ante un 404', async () => {
    mockApi().on('GET', '/api/v1/departamentos/999', () => apiError(404, 'NOT_FOUND'));

    renderRoute('/departamentos/999');

    expect(await screen.findByRole('heading', { name: 'Departamento no encontrado' })).toBeInTheDocument();
  });

  it('envía una consulta validada y confirma sin mostrar datos técnicos', async () => {
    const api = mockApi()
      .on('GET', DETALLE, detalle())
      .on('POST', CONSULTAS, () => jsonResponse({ id: 5, departamentoId: 1, createdAt: '2026-10-01T12:00:00Z' }, { status: 201 }));
    renderRoute('/departamentos/1');
    const form = await screen.findByRole('form', { name: 'Consulta' });

    await userEvent.click(within(form).getByRole('button', { name: 'Enviar consulta' }));
    expect(await within(form).findAllByText('Es obligatorio')).not.toHaveLength(0);
    await userEvent.type(within(form).getByLabelText(/^Email/), 'no-es-un-email');
    await userEvent.click(within(form).getByRole('button', { name: 'Enviar consulta' }));
    expect(await within(form).findByText('Ingresá un email válido')).toBeInTheDocument();
    expect(api.requests('POST', CONSULTAS)).toHaveLength(0);

    await userEvent.type(within(form).getByLabelText(/^Nombre/), 'Ana Pérez');
    await userEvent.clear(within(form).getByLabelText(/^Email/));
    await userEvent.type(within(form).getByLabelText(/^Email/), 'ana@example.com');
    await userEvent.type(within(form).getByLabelText(/^Mensaje/), '¿Se puede visitar el sábado?');
    await userEvent.click(within(form).getByRole('button', { name: 'Enviar consulta' }));

    expect(await screen.findByText(/Consulta enviada/)).toBeInTheDocument();
    expect(api.requests('POST', CONSULTAS)[0]?.body).toEqual({
      nombre: 'Ana Pérez', email: 'ana@example.com', telefono: null, mensaje: '¿Se puede visitar el sábado?',
    });
  });

  it('no ofrece consultas para un departamento vendido', async () => {
    mockApi().on('GET', DETALLE, detalle({ estado: 'VENDIDO' }));

    renderRoute('/departamentos/1');

    expect(await screen.findByText('Este departamento ya fue vendido y no recibe nuevas consultas.')).toBeInTheDocument();
    expect(screen.queryByRole('form', { name: 'Consulta' })).not.toBeInTheDocument();
    // Registro cerrado: no se ofrece editarlo.
    expect(screen.queryByRole('link', { name: 'Editar' })).not.toBeInTheDocument();
  });

  it('da de baja con confirmación, con la versión leída, y vuelve al listado con un aviso', async () => {
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(true);
    const api = mockApi()
      .on('GET', DETALLE, detalle({ estado: 'VENDIDO' }))
      .on('DELETE', DETALLE, () => new Response(null, { status: 204 }))
      .on('GET', '/api/v1/departamentos', pagina([item({ id: 2 })]));
    renderRoute('/departamentos/1');

    // También un vendido se puede dar de baja (para sacarlo del listado).
    await userEvent.click(await screen.findByRole('button', { name: 'Dar de baja' }));

    expect(await screen.findByText('Departamento SEED-0001 dado de baja.')).toBeInTheDocument();
    expect(screen.getByRole('heading', { level: 1, name: 'Departamentos' })).toBeInTheDocument();
    expect(confirm).toHaveBeenCalledWith(expect.stringContaining('¿Dar de baja SEED-0001?'));
    expect(api.requests('DELETE', DETALLE)[0]?.headers.get('If-Match')).toBe('"3"');
  });

  it('si no se confirma, no envía nada', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(false);
    const api = mockApi().on('GET', DETALLE, detalle());
    renderRoute('/departamentos/1');

    await userEvent.click(await screen.findByRole('button', { name: 'Dar de baja' }));

    expect(api.requests('DELETE', DETALLE)).toHaveLength(0);
    expect(screen.getByRole('heading', { level: 1, name: 'Luminoso 3 ambientes con balcón en Palermo' })).toBeInTheDocument();
  });

  it('ante un 412 avisa que otro usuario lo modificó y permite recargar', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    const api = mockApi()
      .on('GET', DETALLE, detalle())
      .on('DELETE', DETALLE, () => apiError(412, 'PRECONDITION_FAILED'));
    renderRoute('/departamentos/1');

    await userEvent.click(await screen.findByRole('button', { name: 'Dar de baja' }));

    expect(await screen.findByText('Otro usuario modificó este departamento.')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Recargar' }));
    expect(screen.queryByText('Otro usuario modificó este departamento.')).not.toBeInTheDocument();
    expect(api.requests('GET', DETALLE).length).toBeGreaterThan(1);
  });
});
