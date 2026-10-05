import { describe, expect, it } from 'vitest';
import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { item, pagina } from '../../../test/fixtures';
import { apiError, mockApi, type RecordedCall } from '../../../test/mockApi';
import { jsonResponse, renderRoute } from '../../../test/utils';

const LISTADO = '/api/v1/departamentos';

describe('Listado de departamentos', () => {
  it('muestra la página que devuelve el servidor con sus datos', async () => {
    const api = mockApi().on('GET', LISTADO, pagina([item(), item({ id: 2, codigo: 'SEED-0002', titulo: 'Monoambiente en Belgrano', imagenPrincipalUrl: null, cantidadImagenes: 0 })]));

    renderRoute('/departamentos');

    expect(screen.getByText('Cargando departamentos…')).toBeInTheDocument();
    expect(await screen.findByRole('heading', { name: 'Luminoso 3 ambientes con balcón en Palermo' })).toBeInTheDocument();
    expect(screen.getByText('2 departamentos')).toBeInTheDocument();
    const card = screen.getByRole('article', { name: 'Monoambiente en Belgrano' });
    expect(within(card).getByRole('img', { name: 'Monoambiente en Belgrano: Sin fotos', hidden: true })).toBeInTheDocument();
    expect(within(card).getByText('0 fotos · 3 consultas')).toBeInTheDocument();

    const [request] = api.requests('GET', LISTADO);
    expect(request?.url.searchParams.get('page')).toBe('0');
    expect(request?.url.searchParams.get('size')).toBe('12');
    expect(request?.url.searchParams.get('sort')).toBe('createdAt,desc');
    expect(request?.headers.get('X-Request-Id')).toMatch(/^[0-9a-f-]{36}$/);
  });

  it('pagina en el servidor: cambiar de página es un request nuevo', async () => {
    const api = mockApi().on('GET', LISTADO, (call: { url: URL }) => {
      const page = Number(call.url.searchParams.get('page'));
      return new Response(JSON.stringify(pagina([item({ id: page + 1, titulo: `Depto página ${page + 1}` })],
        { number: page, size: 12, totalElements: 30, totalPages: 3 })), { headers: { 'Content-Type': 'application/json' } });
    });

    renderRoute('/departamentos');
    await screen.findByText('Depto página 1');
    await userEvent.click(screen.getByRole('button', { name: 'Página 2' }));

    expect(await screen.findByText('Depto página 2')).toBeInTheDocument();
    expect(api.requests('GET', LISTADO).map((c) => c.url.searchParams.get('page'))).toEqual(['0', '1']);
  });

  it('envía filtros y orden al servidor (no filtra en el cliente)', async () => {
    const api = mockApi().on('GET', LISTADO, pagina([item()]));
    renderRoute('/departamentos');
    await screen.findByText('1 departamento');

    await userEvent.type(screen.getByLabelText('Buscar en el título'), 'balcón');
    await userEvent.click(screen.getByLabelText('Reservado'));
    await userEvent.selectOptions(screen.getByLabelText('Moneda'), 'USD');
    await userEvent.type(screen.getByLabelText('Precio máximo'), '200000');
    await userEvent.click(screen.getByRole('button', { name: 'Aplicar filtros' }));

    await waitFor(() => expect(api.requests('GET', LISTADO)).toHaveLength(2));
    const filtrado = api.requests('GET', LISTADO)[1]!.url.searchParams;
    expect(filtrado.get('q')).toBe('balcón');
    expect(filtrado.getAll('estado')).toEqual(['RESERVADO']);
    expect(filtrado.get('moneda')).toBe('USD');
    expect(filtrado.get('precioMax')).toBe('200000');

    await userEvent.selectOptions(screen.getByLabelText(/Ordenar por/), 'precio,asc');
    await waitFor(() => expect(api.requests('GET', LISTADO)).toHaveLength(3));
    expect(api.requests('GET', LISTADO)[2]!.url.searchParams.get('sort')).toBe('precio,asc');
  });

  it('valida los filtros antes de consultar', async () => {
    const api = mockApi().on('GET', LISTADO, pagina([item()]));
    renderRoute('/departamentos');
    await screen.findByText('1 departamento');

    await userEvent.type(screen.getByLabelText('Buscar en el título'), 'ab');
    await userEvent.type(screen.getByLabelText('Precio mínimo'), '1000');
    await userEvent.click(screen.getByRole('button', { name: 'Aplicar filtros' }));

    expect(await screen.findByText('Ingresá al menos 3 caracteres')).toBeInTheDocument();
    expect(screen.getByText('Elegí la moneda para filtrar por precio')).toBeInTheDocument();
    expect(api.requests('GET', LISTADO)).toHaveLength(1);
  });

  it('distingue "sin resultados por filtros" de "sin datos"', async () => {
    mockApi().on('GET', LISTADO, pagina([]));

    renderRoute('/departamentos?q=inexistente');

    expect(await screen.findByText('Ningún departamento coincide con los filtros.', { exact: false })).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Limpiar filtros' }));
    expect(await screen.findByText(/Todavía no hay departamentos cargados/)).toBeInTheDocument();
  });

  it('muestra el error con código de seguimiento y permite reintentar', async () => {
    const api = mockApi().on('GET', LISTADO, () => apiError(500, 'INTERNAL_ERROR', { message: 'Ocurrió un error inesperado; si persiste, informá el requestId' }));
    renderRoute('/departamentos');

    const alert = await screen.findByRole('alert');
    expect(within(alert).getByText('No se pudo cargar el listado')).toBeInTheDocument();
    expect(within(alert).getByText('req-test-1')).toBeInTheDocument();

    api.on('GET', LISTADO, pagina([item()]));
    await userEvent.click(within(alert).getByRole('button', { name: 'Reintentar' }));
    expect(await screen.findByText('1 departamento')).toBeInTheDocument();
  });

  it('reemplaza la foto principal rota por un placeholder', async () => {
    mockApi().on('GET', LISTADO, pagina([item()]));
    renderRoute('/departamentos');

    // La foto está dentro de un enlace decorativo (aria-hidden): el enlace accesible es el título.
    const img = await screen.findByRole('img', { name: 'Luminoso 3 ambientes con balcón en Palermo', hidden: true });
    fireEvent.error(img);

    expect(screen.getByRole('img', { name: /Imagen no disponible/, hidden: true })).toBeInTheDocument();
  });

  it('muestra los dados de baja solo con su filtro, marcados en la tarjeta', async () => {
    const api = mockApi().on('GET', LISTADO, (call: RecordedCall) =>
      jsonResponse(call.url.searchParams.get('dadosDeBaja') === 'true'
        ? pagina([item({ id: 9, fechaBaja: '2026-10-05T12:00:00Z' })])
        : pagina([item()])));
    renderRoute('/departamentos');
    await screen.findByText('1 departamento');
    expect(screen.queryByText('Dado de baja')).not.toBeInTheDocument();

    await userEvent.click(screen.getByLabelText('Reservado'));
    await userEvent.click(screen.getByLabelText('Ver solo los dados de baja'));
    // El estado no aplica a los dados de baja: se limpia y no se puede elegir.
    expect(screen.getByLabelText('Reservado')).not.toBeChecked();
    expect(screen.getByLabelText('Reservado')).toBeDisabled();
    await userEvent.click(screen.getByRole('button', { name: 'Aplicar filtros' }));

    const tarjeta = (await screen.findByText('Dado de baja')).closest('article')!;
    // Una sola etiqueta: no se muestra como "Disponible" aunque conserve ese estado para la reactivación.
    expect(within(tarjeta).queryByText('Disponible')).not.toBeInTheDocument();
    expect(api.requests('GET', LISTADO).at(-1)!.url.searchParams.get('dadosDeBaja')).toBe('true');
    expect(api.requests('GET', LISTADO).at(-1)!.url.searchParams.getAll('estado')).toEqual([]);

    // Al quitar el filtro, el estado se vuelve a poder elegir.
    await userEvent.click(screen.getByLabelText('Ver solo los dados de baja'));
    expect(screen.getByLabelText('Reservado')).toBeEnabled();
  });
});
