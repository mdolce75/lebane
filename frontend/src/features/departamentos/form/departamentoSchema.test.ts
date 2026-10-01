import { describe, expect, it } from 'vitest';
import { detalle } from '../../../test/fixtures';
import { departamentoFormSchema, fromDetalle, toPayload, valoresIniciales, type DepartamentoFormValues } from './departamentoSchema';

const valido: DepartamentoFormValues = {
  ...valoresIniciales,
  titulo: '  3 ambientes en Palermo ',
  precio: '185000,50',
  ambientes: '3',
  dormitorios: '2',
  superficieM2: '72.5',
  direccion: { ...valoresIniciales.direccion, calle: 'Gorriti', numero: '4850', ciudad: 'CABA', provincia: 'CABA' },
};

const errores = (values: DepartamentoFormValues) => {
  const result = departamentoFormSchema.safeParse(values);
  return Object.fromEntries((result.error?.issues ?? []).map((i) => [i.path.join('.'), i.message]));
};

describe('departamentoFormSchema', () => {
  it('acepta un departamento válido', () => {
    expect(errores(valido)).toEqual({});
  });

  it('informa cada campo obligatorio con su ruta', () => {
    expect(Object.keys(errores(valoresIniciales))).toEqual(expect.arrayContaining([
      'titulo', 'precio', 'ambientes', 'dormitorios', 'superficieM2',
      'direccion.calle', 'direccion.numero', 'direccion.ciudad', 'direccion.provincia',
    ]));
  });

  it('valida formatos y rangos como el backend', () => {
    expect(errores({ ...valido, precio: '0' }).precio).toBe('Debe ser mayor que 0');
    expect(errores({ ...valido, precio: '100.123' }).precio).toContain('2 decimales');
    expect(errores({ ...valido, ambientes: '21' }).ambientes).toBe('Entre 1 y 20');
    expect(errores({ ...valido, banos: '0' }).banos).toBe('Entre 1 y 10');
    expect(errores({ ...valido, direccion: { ...valido.direccion, codigoPostal: 'C14#' } })['direccion.codigoPostal'])
      .toBe('Solo letras, números y espacios');
  });

  it('dormitorios debe ser menor que ambientes (monoambiente: 0)', () => {
    expect(errores({ ...valido, ambientes: '2', dormitorios: '2' }).dormitorios)
      .toBe('Debe ser menor que la cantidad de ambientes');
    expect(errores({ ...valido, ambientes: '1', dormitorios: '0' })).toEqual({});
  });

  it('latitud y longitud van juntas y en rango', () => {
    const soloLatitud = { ...valido, direccion: { ...valido.direccion, latitud: '-34.5' } };
    expect(errores(soloLatitud)['direccion.longitud']).toBe('Latitud y longitud van juntas');
    const fueraDeRango = { ...valido, direccion: { ...valido.direccion, latitud: '-91', longitud: '10' } };
    expect(errores(fueraDeRango)['direccion.latitud']).toContain('Entre -90 y 90');
  });
});

describe('toPayload / fromDetalle', () => {
  it('convierte textos a números y opcionales vacíos a null', () => {
    const payload = toPayload(valido);
    expect(payload).toMatchObject({
      titulo: '3 ambientes en Palermo', descripcion: null, precio: 185000.5, ambientes: 3, dormitorios: 2,
      superficieM2: 72.5, estado: 'DISPONIBLE',
    });
    expect(payload.direccion).toMatchObject({ piso: null, codigoPostal: null, latitud: null, longitud: null });
  });

  it('un departamento del servidor vuelve al formulario y produce el mismo payload', () => {
    const d = detalle();
    const payload = toPayload(fromDetalle(d));
    expect(payload).toMatchObject({ titulo: d.titulo, precio: d.precio, superficieM2: d.superficieM2, estado: d.estado });
    expect(payload.direccion).toMatchObject({ calle: 'Gorriti', piso: '7', latitud: -34.5889, longitud: -58.4305 });
  });
});
