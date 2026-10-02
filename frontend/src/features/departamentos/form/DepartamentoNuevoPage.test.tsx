import { describe, expect, it } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { detalle } from '../../../test/fixtures';
import { apiError, mockApi, pngFile, textFile } from '../../../test/mockApi';
import { jsonResponse, renderRoute } from '../../../test/utils';

const ALTA = '/api/v1/departamentos';
const IMAGENES = '/api/v1/departamentos/1/imagenes';

async function completarFormulario() {
  const user = userEvent.setup();
  await user.type(screen.getByLabelText(/^Título/), 'Nuevo 3 ambientes');
  await user.type(screen.getByLabelText(/^Precio/), '150000');
  await user.type(screen.getByLabelText(/^Ambientes/), '3');
  await user.type(screen.getByLabelText(/^Dormitorios/), '2');
  await user.type(screen.getByLabelText(/^Superficie/), '70');
  await user.type(screen.getByLabelText(/^Calle/), 'Gorriti');
  await user.type(screen.getByLabelText(/^Número/), '4850');
  await user.type(screen.getByLabelText(/^Ciudad/), 'CABA');
  await user.type(screen.getByLabelText(/^Provincia/), 'CABA');
  return user;
}

function mockAlta() {
  return mockApi()
    .on('POST', ALTA, () => jsonResponse(detalle({ imagenes: [] }), { status: 201 }))
    .on('GET', '/api/v1/departamentos/1', detalle())
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

  it('crea el departamento, sube las fotos de a una y navega al detalle', async () => {
    const api = mockAlta();
    renderRoute('/departamentos/nuevo');
    await screen.findByRole('heading', { name: 'Nuevo departamento' });

    const user = await completarFormulario();
    await user.upload(screen.getByLabelText('Agregar fotos'), [pngFile('a.png'), pngFile('b.png')]);
    expect(await screen.findByRole('img', { name: 'Vista previa de a.png' })).toHaveAttribute('src', expect.stringMatching(/^blob:/));
    await user.click(screen.getByRole('button', { name: 'Crear y subir 2 foto(s)' }));

    expect(await screen.findByText('Departamento creado.')).toBeInTheDocument();
    const [alta] = api.requests('POST', ALTA);
    expect(alta?.body).toMatchObject({
      titulo: 'Nuevo 3 ambientes', precio: 150000, moneda: 'USD', ambientes: 3, dormitorios: 2, estado: 'DISPONIBLE',
      direccion: { calle: 'Gorriti', numero: '4850', piso: null, latitud: null },
    });
    const subidas = api.requests('POST', IMAGENES);
    expect(subidas).toHaveLength(2);
    expect((subidas[0]?.body as FormData).get('archivo')).toBeInstanceOf(File);
    expect(subidas.every((s) => s.headers.has('X-Request-Id'))).toBe(true);
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
    expect(api.requests('POST', IMAGENES)).toHaveLength(1);
  });

  it('informa errores parciales y reintenta solo las fotos fallidas, sin volver a crear', async () => {
    let intentos = 0;
    const api = mockAlta().on('POST', IMAGENES, () => {
      intentos++;
      return intentos === 2
        ? apiError(503, 'STORAGE_UNAVAILABLE', { message: 'El servicio de imágenes no está disponible' })
        : jsonResponse({ id: 90 + intentos, url: 'http://cdn/x.png', contentType: 'image/png', sizeBytes: 2048, posicion: 0 }, { status: 201 });
    });
    renderRoute('/departamentos/nuevo');
    await screen.findByRole('heading', { name: 'Nuevo departamento' });
    const user = await completarFormulario();
    await user.upload(screen.getByLabelText('Agregar fotos'), [pngFile('ok.png'), pngFile('falla.png')]);
    await user.click(screen.getByRole('button', { name: 'Crear y subir 2 foto(s)' }));

    const aviso = await screen.findByText(/se creó, pero 1 foto\(s\) no se pudieron subir/);
    expect(aviso).toBeInTheDocument();
    expect(screen.getByText(/El servicio de imágenes no está disponible\. Reintentá en unos minutos\./)).toHaveTextContent('req-test-1');
    expect(screen.getByRole('button', { name: 'Crear y subir 2 foto(s)' })).toBeDisabled();

    await user.click(screen.getByRole('button', { name: 'Reintentar fotos fallidas' }));

    expect(await screen.findByText('Departamento creado.')).toBeInTheDocument();
    expect(api.requests('POST', ALTA)).toHaveLength(1);
    expect(api.requests('POST', IMAGENES)).toHaveLength(3);
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
});
