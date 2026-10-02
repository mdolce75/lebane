import type { ReactNode } from 'react';
import { describe, expect, it } from 'vitest';
import { act, renderHook } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { useListadoSearch } from './useListadoSearch';

const wrapper = (initial: string) =>
  function Wrapper({ children }: { children: ReactNode }) {
    return <MemoryRouter initialEntries={[initial]}>{children}</MemoryRouter>;
  };

describe('useListadoSearch', () => {
  it('compone cambios consecutivos aunque el router no haya confirmado el anterior', () => {
    const { result } = renderHook(() => useListadoSearch(), { wrapper: wrapper('/departamentos') });

    // Regresión: aplicar un filtro y cambiar el orden enseguida perdía el filtro.
    act(() => {
      result.current.update({ conImagenes: true });
      result.current.update({ sort: 'precio,desc' });
    });

    expect(result.current.params).toMatchObject({ conImagenes: true, sort: 'precio,desc', page: 0 });
  });

  it('cambiar filtros vuelve a la primera página; paginar no', () => {
    const { result } = renderHook(() => useListadoSearch(), { wrapper: wrapper('/departamentos?page=3&ciudad=Rosario') });

    act(() => result.current.update({ page: 4 }, false));
    expect(result.current.params).toMatchObject({ page: 4, ciudad: 'Rosario' });

    act(() => result.current.update({ ambientesMin: 2 }));
    expect(result.current.params).toMatchObject({ page: 0, ciudad: 'Rosario', ambientesMin: 2 });
  });

  it('limpiar descarta todos los filtros, también los pendientes', () => {
    const { result } = renderHook(() => useListadoSearch(), { wrapper: wrapper('/departamentos?q=balcon&moneda=USD') });

    act(() => {
      result.current.update({ ciudad: 'Rosario' });
      result.current.clear();
      result.current.update({ sort: 'precio,asc' });
    });

    expect(result.current.params).toEqual({ estado: [], page: 0, size: 12, sort: 'precio,asc' });
  });
});
