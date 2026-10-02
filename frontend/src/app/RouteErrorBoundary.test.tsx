import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { createMemoryRouter, RouterProvider } from 'react-router';
import { RouteErrorBoundary } from './RouteErrorBoundary';

function Explota(): never {
  throw new Error('NullPointerException at com.lebane.Secreto (detalle interno)');
}

describe('RouteErrorBoundary', () => {
  it('ante un error de render muestra un mensaje genérico, sin detalles técnicos', async () => {
    vi.spyOn(console, 'error').mockImplementation(() => {});
    vi.spyOn(console, 'warn').mockImplementation(() => {});
    const router = createMemoryRouter([{ path: '/', element: <Explota />, errorElement: <RouteErrorBoundary /> }]);

    render(<RouterProvider router={router} />);

    const alerta = await screen.findByRole('alert');
    expect(alerta).toHaveTextContent('Ocurrió un error inesperado.');
    expect(alerta).not.toHaveTextContent(/NullPointer|com\.lebane|interno/);
    expect(screen.getByRole('button', { name: 'Reintentar' })).toBeInTheDocument();
  });
});
