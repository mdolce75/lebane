import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ErrorMessage } from './ErrorMessage';
import { HttpError } from '../api/errors';

describe('ErrorMessage', () => {
  it('muestra mensaje y requestId de un HttpError', () => {
    render(<ErrorMessage error={new HttpError({ kind: 'http', status: 404, message: 'No existe', requestId: 'req-9' })} />);

    expect(screen.getByRole('alert')).toHaveTextContent('No existe');
    expect(screen.getByText('req-9')).toBeInTheDocument();
  });

  it('no expone detalles de errores desconocidos', () => {
    render(<ErrorMessage error={new Error('TypeError at Foo.bar (secret/internal.ts:42)')} />);

    expect(screen.getByRole('alert')).toHaveTextContent('Ocurrió un error inesperado.');
    expect(screen.queryByText(/internal\.ts/)).not.toBeInTheDocument();
  });

  it('permite reintentar', async () => {
    const onRetry = vi.fn();
    render(<ErrorMessage error={new Error('x')} onRetry={onRetry} />);

    await userEvent.click(screen.getByRole('button', { name: 'Reintentar' }));

    expect(onRetry).toHaveBeenCalledOnce();
  });
});
