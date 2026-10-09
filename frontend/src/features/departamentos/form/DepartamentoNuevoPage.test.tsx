import { describe, expect, it } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { detalle } from '../../../test/fixtures';
import { apiError, mockApi, pngFile, textFile, type RecordedCall } from '../../../test/mockApi';
import { jsonResponse, renderRoute } from '../../../test/utils';

const ALTA = '/api/departamentos';
const IMAGENES = '/api/departamentos/1/imagenes';

async function completarFormulario() {
  const user = userEvent.setup();
  await user.type(screen.getByLabelText(/^Título/), 'Nuevo 3 ambientes');
  await user.type(screen.getByLabelText(/^Precio/), '150000');
  await user.type(screen.getByLabelText(/^Ambientes/), '3');
  await user.type(screen.getByLabelText(/^Dormitorios/), '2');
  await user.type(screen.getByLabelText(/^Superficie/), '70');
  await user.click(screen.getByRole('button', { name: /cargarla a mano/ }));
  await user.type(screen.getByLabelText(/^Calle/), 'Gorriti');
  await user.type(screen.getByLabelText(/^Número/), '4850');
  await user.type(screen.getByLabelText(/^Ciudad/), 'CABA');
  await user.type(screen.getByLabelText(/^Provincia/), 'CABA');
  return user;
}

/** El alta es multipart: la parte `departamento` lleva el JSON y `imagenes`, las fotos. */
async function cuerpoAlta(call: RecordedCall | undefined) {
  const form = call?.body as FormData;
  const departamento = form.get('departamento') as Blob;
  expect(departamento.type).toBe('application/json');
  return { datos: JSON.parse(await departamento.text()) as unknown, imagenes: form.getAll('imagenes') as File[] };
}

function mockAlta() {
  return mockApi()
    .on('POST', ALTA, () => jsonResponse(detalle({ imagenes: [] }), { status: 202 }))
    .on('GET', '/api/departamentos/1', detalle())
    .on('POST', IMAGENES, () => jsonResponse({ id: 99, url: 'http://cdn/x.png', contentType: 'image/png', sizeBytes: 2048, posicion: 0 }, { status: 201 }));
}

describe('Alta de departamento', () => {
  it('valida el formulario antes de enviar', async () => {
    const api = mockAlta();
    renderRoute('/departamentos/nuevo');

    await userEvent.click(await screen.findByRole('button', { name: 'Crear departamento' }));

    expect(await screen.findAllByText('Es obligatorio')).not.toHaveLength(0);
    expect(api.requests('POST', ALTA)).toHaveLength(0);

    await userEvent.type(screen.getByLabelText(/^Ambientes/), '2');
    await userEvent.type(screen.getByLabelText(/^Dormitorios/), '2');
    await userEvent.tab();
    expect(await screen.findByText('Debe ser menor que la cantidad de ambientes')).toBeInTheDocument();
  });

  it('crea el departamento con sus fotos en un solo request y navega al detalle', async () => {
    const api = mockAlta();
    renderRoute('/departamentos/nuevo');
    await screen.findByRole('heading', { name: 'Nuevo departamento' });

    const user = await completarFormulario();
    await user.upload(screen.getByLabelText('Agregar fotos'), [pngFile('a.png'), pngFile('b.png')]);
    expect(await screen.findByRole('img', { name: 'Vista previa de a.png' })).toHaveAttribute('src', expect.stringMatching(/^blob:/));
    await user.click(await screen.findByRole('button', { name: 'Crear y subir 2 foto(s)' }));

    expect(await screen.findByText('Departamento creado.')).toBeInTheDocument();
    const altas = api.requests('POST', ALTA);
    expect(altas).toHaveLength(1);
    const { datos, imagenes } = await cuerpoAlta(altas[0]);
    expect(datos).toMatchObject({
      titulo: 'Nuevo 3 ambientes', precio: 150000, moneda: 'USD', ambientes: 3, dormitorios: 2, estado: 'DISPONIBLE',
      direccion: { calle: 'Gorriti', numero: '4850', piso: null, latitud: null },
    });
    expect(imagenes.map((f) => f.name)).toEqual(['a.png', 'b.png']);
    expect(altas[0]?.headers.has('X-Request-Id')).toBe(true);
    expect(api.requests('POST', IMAGENES)).toHaveLength(0);
  });

  it('con el autocompletado guarda la dirección elegida, sin repetir los campos y con coordenadas de muchos decimales', async () => {
    const api = mockAlta().on('GET', '/api/direcciones/autocompletar', {
      proveedor: 'georef',
      degradado: false,
      sugerencias: [{
        calle: 'Av. Santa Fe', numero: '1860', ciudad: 'Ciudad Autónoma de Buenos Aires', provincia: 'Ciudad Autónoma de Buenos Aires',
        latitud: -34.59581734221, longitud: -58.39391185746, placeId: 'georef:1:1860', descripcion: 'AV. SANTA FE 1860, CABA',
      }],
    });
    renderRoute('/departamentos/nuevo');
    await screen.findByRole('heading', { name: 'Nuevo departamento' });
    const user = userEvent.setup();
    await user.type(screen.getByLabelText(/^Título/), 'Nuevo 3 ambientes');
    await user.type(screen.getByLabelText(/^Precio/), '150000');
    await user.type(screen.getByLabelText(/^Ambientes/), '3');
    await user.type(screen.getByLabelText(/^Dormitorios/), '2');
    await user.type(screen.getByLabelText(/^Superficie/), '70');
    // Solo el buscador: calle, número, ciudad y provincia no se piden por separado.
    expect(screen.queryByLabelText(/^Calle/)).not.toBeInTheDocument();
    await user.type(screen.getByLabelText('Buscar dirección'), 'santa fe 1860');
    await user.click(await screen.findByRole('button', { name: 'AV. SANTA FE 1860, CABA' }));
    expect(screen.getByText('Av. Santa Fe 1860')).toBeInTheDocument();
    expect(screen.queryByLabelText(/^Calle/)).not.toBeInTheDocument();
    await user.type(screen.getByLabelText(/^Piso/), '3');
    await user.click(screen.getByRole('button', { name: 'Crear departamento' }));

    expect(await screen.findByText('Departamento creado.')).toBeInTheDocument();
    const { datos } = await cuerpoAlta(api.requests('POST', ALTA)[0]);
    expect(datos).toMatchObject({
      direccion: {
        calle: 'Av. Santa Fe', numero: '1860', piso: '3', ciudad: 'Ciudad Autónoma de Buenos Aires',
        latitud: -34.595817, longitud: -58.393912, placeId: 'georef:1:1860',
      },
    });
  });

  it('las fotos van antes de los botones de guardar y cancelar', async () => {
    mockAlta();
    renderRoute('/departamentos/nuevo');
    const fotos = await screen.findByLabelText('Agregar fotos');
    const guardar = screen.getByRole('button', { name: 'Crear departamento' });
    expect(fotos.compareDocumentPosition(guardar) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
  });

  it('limita a 5 fotos y rechaza archivos que no son imágenes', async () => {
    mockAlta();
    renderRoute('/departamentos/nuevo');
    const input = await screen.findByLabelText('Agregar fotos');

    const archivos = [1, 2, 3, 4, 5, 6].map((n) => pngFile(`f${n}.png`));
    await userEvent.upload(input, [textFile('virus.png'), ...archivos]);

    const seleccionadas = await screen.findByRole('list', { name: 'Fotos seleccionadas' });
    expect(within(seleccionadas).getAllByRole('listitem')).toHaveLength(5);
    const rechazados = screen.getByRole('alert', { name: 'Archivos rechazados' });
    expect(rechazados).toHaveTextContent('virus.png: No es una imagen JPEG, PNG o WebP');
    expect(rechazados).toHaveTextContent('f6.png: Se alcanzó el máximo de fotos');
    expect(screen.getByLabelText('Agregar fotos')).toBeDisabled();
  });

  it('permite quitar fotos antes de enviar', async () => {
    const api = mockAlta();
    renderRoute('/departamentos/nuevo');
    await screen.findByRole('heading', { name: 'Nuevo departamento' });
    const user = await completarFormulario();

    await user.upload(screen.getByLabelText('Agregar fotos'), [pngFile('a.png'), pngFile('b.png')]);
    await user.click(await screen.findByRole('button', { name: 'Quitar a.png' }));
    expect(screen.queryByRole('img', { name: 'Vista previa de a.png' })).not.toBeInTheDocument();
    expect(URL.revokeObjectURL).toHaveBeenCalled();

    await user.click(screen.getByRole('button', { name: 'Crear y subir 1 foto(s)' }));
    await screen.findByText('Departamento creado.');
    const { imagenes } = await cuerpoAlta(api.requests('POST', ALTA)[0]);
    expect(imagenes.map((f) => f.name)).toEqual(['b.png']);
  });

  it('si el servidor rechaza una foto no se crea nada, y la foto queda marcada para corregirla', async () => {
    const api = mockAlta().on('POST', ALTA, () => apiError(400, 'VALIDATION_ERROR', {
      message: 'La solicitud contiene datos inválidos',
      fieldErrors: { 'imagenes[1]': 'debe ser una imagen JPEG, PNG o WebP' },
    }));
    renderRoute('/departamentos/nuevo');
    await screen.findByRole('heading', { name: 'Nuevo departamento' });
    const user = await completarFormulario();
    await user.upload(screen.getByLabelText('Agregar fotos'), [pngFile('ok.png'), pngFile('rara.png')]);
    await user.click(await screen.findByRole('button', { name: 'Crear y subir 2 foto(s)' }));

    expect(await screen.findByText('No se pudo guardar el departamento')).toBeInTheDocument();
    expect(screen.getByText(/La foto debe ser una imagen JPEG, PNG o WebP\./)).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Nuevo departamento' })).toBeInTheDocument();
    expect(api.requests('POST', ALTA)).toHaveLength(1);
    expect(api.requests('POST', IMAGENES)).toHaveLength(0);
    // Se puede quitar la foto rechazada y volver a enviar.
    await user.click(screen.getByRole('button', { name: 'Quitar rara.png' }));
    expect(screen.getByRole('button', { name: 'Crear y subir 1 foto(s)' })).toBeEnabled();
  });

  it('muestra los errores de validación del servidor en cada campo', async () => {
    mockApi().on('POST', ALTA, () => apiError(400, 'VALIDATION_ERROR', {
      message: 'La solicitud contiene datos inválidos',
      fieldErrors: { 'direccion.ciudad': 'no debe estar vacío', titulo: 'ya existe un aviso igual' },
    }));
    renderRoute('/departamentos/nuevo');
    await screen.findByRole('heading', { name: 'Nuevo departamento' });
    const user = await completarFormulario();

    await user.click(screen.getByRole('button', { name: 'Crear departamento' }));

    expect(await screen.findByText('no debe estar vacío')).toBeInTheDocument();
    expect(screen.getByText('ya existe un aviso igual')).toBeInTheDocument();
    expect(screen.getByText('No se pudo guardar el departamento')).toBeInTheDocument();
    await waitFor(() => expect(screen.getByRole('button', { name: 'Crear departamento' })).toBeEnabled());
  });
  it('no ofrece publicar directamente como vendido', async () => {
    mockApi();
    renderRoute('/departamentos/nuevo');

    const estado = await screen.findByLabelText(/^Estado/);
    expect(within(estado).getAllByRole('option').map((o) => o.textContent)).toEqual(['Disponible', 'Reservado']);
  });
});
