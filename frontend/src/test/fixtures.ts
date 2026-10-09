import type { DepartamentoDetalle, DepartamentoItem, Pagina } from '../features/departamentos/api/schemas';

export function item(overrides: Partial<DepartamentoItem> = {}): DepartamentoItem {
  return {
    id: 1,
    codigo: 'SEED-0001',
    titulo: 'Luminoso 3 ambientes con balcón en Palermo',
    precio: 185000,
    moneda: 'USD',
    ambientes: 3,
    dormitorios: 2,
    banos: 1,
    superficieM2: 72.5,
    estado: 'DISPONIBLE',
    ciudad: 'Ciudad Autónoma de Buenos Aires',
    provincia: 'CABA',
    imagenPrincipalUrl: 'http://localhost:9000/lebane-images/departamentos/1/a.png',
    cantidadImagenes: 2,
    cantidadConsultas: 3,
    createdAt: '2026-10-01T12:00:00Z',
    fechaBaja: null,
    ...overrides,
  };
}

export function pagina(items: DepartamentoItem[], page = { number: 0, size: 12, totalElements: items.length, totalPages: 1 }): Pagina {
  return { content: items, page };
}

export function detalle(overrides: Partial<DepartamentoDetalle> = {}): DepartamentoDetalle {
  return {
    id: 1,
    codigo: 'SEED-0001',
    titulo: 'Luminoso 3 ambientes con balcón en Palermo',
    descripcion: 'Frente, piso alto, cocina integrada.',
    precio: 185000,
    moneda: 'USD',
    ambientes: 3,
    dormitorios: 2,
    banos: 1,
    superficieM2: 72.5,
    estado: 'DISPONIBLE',
    direccion: {
      calle: 'Gorriti',
      numero: '4850',
      piso: '7',
      unidad: 'B',
      ciudad: 'Ciudad Autónoma de Buenos Aires',
      provincia: 'CABA',
      codigoPostal: 'C1414',
      latitud: -34.5889,
      longitud: -58.4305,
      placeId: null,
    },
    imagenes: [
      { id: 10, url: 'http://localhost:9000/lebane-images/departamentos/1/a.png', contentType: 'image/png', sizeBytes: 1000, posicion: 0 },
      { id: 11, url: 'http://localhost:9000/lebane-images/departamentos/1/b.png', contentType: 'image/png', sizeBytes: 1000, posicion: 1 },
    ],
    cantidadConsultas: 3,
    consultas: [],
    version: 3,
    createdAt: '2026-10-01T12:00:00Z',
    updatedAt: '2026-10-01T13:00:00Z',
    fechaBaja: null,
    ...overrides,
  };
}
