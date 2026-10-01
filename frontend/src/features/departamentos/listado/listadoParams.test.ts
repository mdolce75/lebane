import { describe, expect, it } from 'vitest';
import { filtrosSchema, paramsFromFiltros } from './filtrosSchema';
import { DEFAULT_SIZE, DEFAULT_SORT, maxPage, parseListadoParams, toSearchParams } from './listadoParams';

const parse = (query: string) => parseListadoParams(new URLSearchParams(query));

describe('parseListadoParams', () => {
  it('usa defaults sin parámetros', () => {
    expect(parse('')).toEqual({ estado: [], page: 0, size: DEFAULT_SIZE, sort: DEFAULT_SORT });
  });

  it('lee filtros válidos, incluido estado repetido', () => {
    const params = parse('q=balcón&ciudad=Rosario&estado=DISPONIBLE&estado=RESERVADO&moneda=USD&precioMin=100&precioMax=200&ambientesMin=2&conImagenes=true&page=3&size=24&sort=precio,asc');
    expect(params).toMatchObject({
      q: 'balcón', ciudad: 'Rosario', estado: ['DISPONIBLE', 'RESERVADO'], moneda: 'USD',
      precioMin: 100, precioMax: 200, ambientesMin: 2, conImagenes: true, page: 3, size: 24, sort: 'precio,asc',
    });
  });

  it('descarta valores inválidos de la URL en lugar de enviarlos al backend', () => {
    const params = parse('q=ab&estado=ALQUILADO&moneda=EUR&ambientesMin=0&page=-1&size=500&sort=titulo,asc&conImagenes=quizas');
    expect(params).toEqual({ estado: [], page: 0, size: DEFAULT_SIZE, sort: DEFAULT_SORT });
  });

  it('ignora el precio sin moneda y un rango invertido', () => {
    expect(parse('precioMin=100').precioMin).toBeUndefined();
    const invertido = parse('moneda=ARS&precioMin=500&precioMax=100');
    expect(invertido.precioMin).toBe(500);
    expect(invertido.precioMax).toBeUndefined();
  });

  it('acota la página a la ventana máxima de resultados', () => {
    expect(maxPage(12)).toBe(832);
    expect(parse('page=5000&size=12').page).toBe(832);
  });

  it('serializa a la URL omitiendo defaults', () => {
    const search = toSearchParams({ q: 'balcón', estado: ['VENDIDO'], page: 0, size: DEFAULT_SIZE, sort: DEFAULT_SORT });
    expect(search.toString()).toBe('q=balc%C3%B3n&estado=VENDIDO');
    expect(parseListadoParams(search)).toMatchObject({ q: 'balcón', estado: ['VENDIDO'] });
  });
});

describe('filtrosSchema', () => {
  const vacio = { q: '', ciudad: '', estado: [], moneda: '', precioMin: '', precioMax: '', ambientesMin: '', conImagenes: '' } as const;

  it('acepta el formulario vacío y lo convierte en "sin filtros"', () => {
    const form = filtrosSchema.parse(vacio);
    expect(paramsFromFiltros(form)).toMatchObject({ q: undefined, moneda: undefined, precioMin: undefined, conImagenes: undefined });
  });

  it('exige 3 caracteres en la búsqueda y moneda para filtrar por precio', () => {
    const result = filtrosSchema.safeParse({ ...vacio, q: 'ab', precioMax: '1000' });
    expect(result.success).toBe(false);
    const paths = result.error?.issues.map((i) => i.path.join('.'));
    expect(paths).toEqual(expect.arrayContaining(['q', 'moneda']));
  });

  it('rechaza un rango de precio invertido', () => {
    const result = filtrosSchema.safeParse({ ...vacio, moneda: 'USD', precioMin: '200', precioMax: '100' });
    expect(result.error?.issues[0]?.path).toEqual(['precioMax']);
  });

  it('acepta decimales con coma', () => {
    const form = filtrosSchema.parse({ ...vacio, moneda: 'ARS', precioMin: '1500,50' });
    expect(paramsFromFiltros(form).precioMin).toBe(1500.5);
  });
});
