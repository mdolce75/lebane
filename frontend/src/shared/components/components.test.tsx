import { describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ImageWithFallback } from './ImageWithFallback';
import { Pagination } from './Pagination';
import { pageItems } from './pageItems';

describe('ImageWithFallback', () => {
  it('muestra un placeholder cuando no hay imagen', () => {
    render(<ImageWithFallback src={null} alt="Depto" />);
    expect(screen.getByRole('img', { name: 'Depto: Sin fotos' })).toBeInTheDocument();
  });

  it('reemplaza una imagen rota por un placeholder', () => {
    render(<ImageWithFallback src="http://cdn/rota.png" alt="Depto" />);
    const img = screen.getByRole('img', { name: 'Depto' });
    expect(img).toHaveAttribute('src', 'http://cdn/rota.png');

    fireEvent.error(img);

    expect(screen.getByRole('img', { name: 'Depto: Imagen no disponible' })).toBeInTheDocument();
    expect(screen.queryByRole('img', { name: 'Depto' })).not.toBeInTheDocument();
  });
});

describe('Pagination', () => {
  it('calcula páginas visibles con saltos', () => {
    expect(pageItems(0, 9)).toEqual([0, 1, 'gap', 9]);
    expect(pageItems(5, 9)).toEqual([0, 'gap', 4, 5, 6, 'gap', 9]);
    expect(pageItems(1, 2)).toEqual([0, 1, 2]);
  });

  it('navega y deshabilita los extremos', async () => {
    const onChange = vi.fn();
    render(<Pagination page={0} totalPages={3} onChange={onChange} />);

    expect(screen.getByRole('button', { name: 'Anterior' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Página 1' })).toHaveAttribute('aria-current', 'page');
    await userEvent.click(screen.getByRole('button', { name: 'Página 3' }));
    await userEvent.click(screen.getByRole('button', { name: 'Siguiente' }));

    expect(onChange.mock.calls).toEqual([[2], [1]]);
  });

  it('no permite pasar la última página navegable ni se muestra con una sola página', () => {
    const { rerender } = render(<Pagination page={4} totalPages={100} lastNavigablePage={4} onChange={vi.fn()} />);
    expect(screen.getByRole('button', { name: 'Siguiente' })).toBeDisabled();
    expect(screen.queryByRole('button', { name: 'Página 100' })).not.toBeInTheDocument();

    rerender(<Pagination page={0} totalPages={1} onChange={vi.fn()} />);
    expect(screen.queryByRole('navigation', { name: 'Paginación' })).not.toBeInTheDocument();
  });
});
