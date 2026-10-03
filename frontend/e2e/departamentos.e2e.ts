import { expect, test, type Request } from '@playwright/test';
import { crearDepartamento, marca, obtenerDepartamento, PNG_1X1, unidadUnica } from './support';

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
const esListado = (r: Request) => new URL(r.url()).pathname === '/api/v1/departamentos' && r.method() === 'GET';

// nginx envía una Content-Security-Policy estricta: cualquier recurso bloqueado (p. ej. las fotos de MinIO o las
// vistas previas blob:) aparece como error en la consola y hace fallar el test.
let violacionesCsp: string[] = [];
test.beforeEach(({ page }) => {
  violacionesCsp = [];
  page.on('console', (msg) => {
    if (msg.type() === 'error' && /Content Security Policy/i.test(msg.text())) violacionesCsp.push(msg.text());
  });
});
test.afterEach(() => {
  expect(violacionesCsp, 'violaciones de Content-Security-Policy').toEqual([]);
});

test.describe('Listado', () => {
  test('filtra y ordena en el servidor, con el estado en la URL', async ({ page, request }) => {
    const m = marca();
    await crearDepartamento(request, { titulo: `${m} económico`, precio: 90000 });
    await crearDepartamento(request, { titulo: `${m} caro reservado`, precio: 250000, estado: 'RESERVADO' });

    const listados: URL[] = [];
    page.on('request', (r) => esListado(r) && listados.push(new URL(r.url())));

    await page.goto('/departamentos');
    await page.getByLabel('Buscar en el título').fill(m);
    await page.getByRole('button', { name: 'Aplicar filtros' }).click();

    await expect(page.getByRole('article')).toHaveCount(2);
    await expect(page.getByText('2 departamentos')).toBeVisible();
    await expect(page).toHaveURL(new RegExp(`q=${m}`));
    // El filtro viaja al backend: el cliente nunca filtra datos.
    expect(listados.at(-1)?.searchParams.get('q')).toBe(m);

    await page.getByRole('checkbox', { name: 'Reservado' }).check();
    await page.getByRole('button', { name: 'Aplicar filtros' }).click();
    await expect(page.getByRole('article')).toHaveCount(1);
    await expect(page.getByRole('article', { name: `${m} caro reservado` })).toBeVisible();
    expect(listados.at(-1)?.searchParams.getAll('estado')).toEqual(['RESERVADO']);

    // Orden en el servidor y estado recuperable desde la URL (recarga / enlace compartido).
    await page.goto(`/departamentos?q=${m}&sort=precio,desc`);
    await expect(page.getByRole('article').first()).toHaveAccessibleName(`${m} caro reservado`);
    await page.getByLabel(/Ordenar por/).selectOption('precio,asc');
    await expect(page.getByRole('article').first()).toHaveAccessibleName(`${m} económico`);
    await expect(page).toHaveURL(/sort=precio%2Casc|sort=precio,asc/);

    await page.reload();
    await expect(page.getByRole('article').first()).toHaveAccessibleName(`${m} económico`);
    await expect(page.getByLabel('Buscar en el título')).toHaveValue(m);
  });

  test('cada request lleva un X-Request-Id y el backend devuelve el mismo', async ({ page }) => {
    const responsePromise = page.waitForResponse((r) => esListado(r.request()));
    await page.goto('/departamentos');
    const response = await responsePromise;

    const enviado = await response.request().headerValue('x-request-id');
    expect(enviado).toMatch(UUID);
    expect(response.headers()['x-request-id']).toBe(enviado);
  });
});

test.describe('Alta', () => {
  test('crea un departamento con una foto real que se sirve desde MinIO', async ({ page }) => {
    const titulo = `${marca()} con foto`;
    await page.goto('/departamentos/nuevo');

    await page.getByLabel(/^Título/).fill(titulo);
    await page.getByLabel(/^Precio/).fill('150000');
    await page.getByLabel(/^Ambientes/).fill('3');
    await page.getByLabel(/^Dormitorios/).fill('2');
    await page.getByLabel(/^Superficie/).fill('70');
    await page.getByLabel(/^Calle/).fill('Gorriti');
    await page.getByLabel(/^Número/).fill('4850');
    await page.getByLabel(/^Unidad/).fill(unidadUnica());
    await page.getByLabel(/^Ciudad/).fill('Ciudad Autónoma de Buenos Aires');
    await page.getByLabel(/^Provincia/).fill('CABA');
    await page.getByLabel('Agregar fotos').setInputFiles({ name: 'frente.png', mimeType: 'image/png', buffer: PNG_1X1 });
    await expect(page.getByRole('img', { name: 'Vista previa de frente.png' })).toBeVisible();

    const subida = page.waitForResponse((r) => /\/imagenes$/.test(new URL(r.url()).pathname) && r.request().method() === 'POST');
    await page.getByRole('button', { name: 'Crear y subir 1 foto(s)' }).click();
    expect((await subida).status()).toBe(201);

    await expect(page.getByRole('heading', { level: 1, name: titulo })).toBeVisible();
    await expect(page.getByText('Departamento creado.')).toBeVisible();
    await expect(page).toHaveURL(/\/departamentos\/\d+$/);

    // La foto existe en MinIO y el navegador la pudo descargar y decodificar.
    const foto = page.getByRole('img', { name: /foto 1 de 1/ });
    await expect(foto).toBeVisible();
    await expect.poll(() => foto.evaluate((img: HTMLImageElement) => img.complete && img.naturalWidth)).toBe(1);
  });

  test('rechaza un archivo que no es una imagen aunque tenga extensión .jpg', async ({ page, request }) => {
    await page.goto('/departamentos/nuevo');
    await page.getByLabel('Agregar fotos').setInputFiles({
      name: 'foto.jpg',
      mimeType: 'image/jpeg',
      buffer: Buffer.from('<html>no soy una foto</html>'),
    });
    await expect(page.getByRole('alert', { name: 'Archivos rechazados' })).toContainText('No es una imagen JPEG, PNG o WebP');

    // El backend aplica la misma regla aunque se saltee el navegador.
    const { id } = await crearDepartamento(request);
    const response = await request.post(`/api/v1/departamentos/${id}/imagenes`, {
      multipart: { archivo: { name: 'foto.jpg', mimeType: 'image/jpeg', buffer: Buffer.from('<html>no soy una foto</html>') } },
    });
    expect(response.status()).toBe(400);
    expect(await response.json()).toMatchObject({
      status: 400,
      fieldErrors: { archivo: 'debe ser una imagen JPEG, PNG o WebP' },
      requestId: expect.any(String),
    });
  });
});

test.describe('Detalle', () => {
  test('envía una consulta y el contador del listado la refleja', async ({ page, request }) => {
    const { id, titulo } = await crearDepartamento(request);
    await page.goto(`/departamentos/${id}`);
    await expect(page.getByRole('heading', { level: 1, name: titulo })).toBeVisible();

    const form = page.getByRole('form', { name: 'Consulta' });
    await form.getByLabel(/^Nombre/).fill('Ana Pérez');
    await form.getByLabel(/^Email/).fill('ana@example.com');
    await form.getByLabel(/^Mensaje/).fill('¿Se puede visitar el sábado?');
    await form.getByRole('button', { name: 'Enviar consulta' }).click();
    await expect(page.getByText(/Consulta enviada/)).toBeVisible();

    await page.goto(`/departamentos?q=${encodeURIComponent(titulo.split(' ')[0]!)}`);
    await expect(page.getByRole('article', { name: titulo })).toContainText('1 consulta');
  });

  test('un departamento inexistente y una ruta desconocida muestran su pantalla', async ({ page }) => {
    await page.goto('/departamentos/999999999');
    await expect(page.getByRole('heading', { name: 'Departamento no encontrado' })).toBeVisible();

    await page.goto('/no-existe');
    await expect(page.getByRole('heading', { name: 'Página no encontrada' })).toBeVisible();
    await page.getByRole('link', { name: 'Volver al inicio' }).click();
    await expect(page).toHaveURL(/\/departamentos$/);
  });
});

test.describe('Edición', () => {
  test('no pisa los cambios de otro usuario: 412, recargar y volver a guardar', async ({ page, request }) => {
    const { id, titulo } = await crearDepartamento(request);
    await page.goto(`/departamentos/${id}/editar`);
    await expect(page.getByLabel(/^Título/)).toHaveValue(titulo);

    // Otro usuario modifica el departamento por la API mientras está abierto el formulario.
    const actual = await obtenerDepartamento(request, id);
    const deOtro = `${titulo} (editado por otro)`;
    const campos = ['descripcion', 'precio', 'moneda', 'ambientes', 'dormitorios', 'banos', 'superficieM2', 'estado', 'direccion'];
    const put = await request.put(`/api/v1/departamentos/${id}`, {
      headers: { 'If-Match': actual.etag },
      data: { ...Object.fromEntries(campos.map((c) => [c, actual.body[c]])), titulo: deOtro },
    });
    expect(put.status(), await put.text()).toBe(200);

    await page.getByLabel(/^Precio/).fill('123456');
    await page.getByRole('button', { name: 'Guardar cambios' }).click();
    await expect(page.getByText('Otro usuario modificó este departamento mientras lo editabas.')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Guardar cambios' })).toBeDisabled();

    await page.getByRole('button', { name: 'Recargar datos actuales' }).click();
    await expect(page.getByLabel(/^Título/)).toHaveValue(deOtro);
    await page.getByLabel(/^Precio/).fill('123456');
    await page.getByRole('button', { name: 'Guardar cambios' }).click();
    await expect(page.getByText('Cambios guardados.')).toBeVisible();

    const final = await obtenerDepartamento(request, id);
    expect(final.body).toMatchObject({ titulo: deOtro, precio: 123456 });
  });
});
