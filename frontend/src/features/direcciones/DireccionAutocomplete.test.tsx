import { describe, expect, it, vi } from 'vitest';
import { act, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { mockApi } from '../../test/mockApi';
import { renderWithProviders } from '../../test/utils';
import { DireccionAutocomplete } from './DireccionAutocomplete';

const URL_AUTOCOMPLETE = '/api/direcciones/autocompletar';
const SUGERENCIA = {
  calle: 'Av Santa Fe', numero: '1860', ciudad: 'Ciudad Autónoma de Buenos Aires', provincia: 'CABA',
  latitud: -34.5958, longitud: -58.3941, placeId: 'georef:1:1860', descripcion: 'AV SANTA FE 1860, CABA',
};

describe('DireccionAutocomplete', () => {
  it('consulta con debounce y devuelve la sugerencia elegida', async () => {
    const api = mockApi().on('GET', URL_AUTOCOMPLETE, { sugerencias: [SUGERENCIA], proveedor: 'georef', degradado: false });
    const onSelect = vi.fn();
    renderWithProviders(<DireccionAutocomplete onSelect={onSelect} />);

    await userEvent.type(screen.getByLabelText('Buscar dirección'), 'Santa Fe 1860');
    await userEvent.click(await screen.findByRole('button', { name: 'AV SANTA FE 1860, CABA' }));

    expect(onSelect).toHaveBeenCalledWith(SUGERENCIA);
    // Debounce: un solo request por el texto final, no uno por tecla.
    const requests = api.requests('GET', URL_AUTOCOMPLETE);
    expect(requests).toHaveLength(1);
    expect(requests[0]?.url.searchParams.get('q')).toBe('Santa Fe 1860');
  });

  it('no consulta con menos de 3 caracteres', async () => {
    const api = mockApi();
    renderWithProviders(<DireccionAutocomplete onSelect={vi.fn()} />);

    await userEvent.type(screen.getByLabelText('Buscar dirección'), 'Sa');
    // Espera más que el debounce (300 ms) dentro de act: el estado se actualiza pero no se consulta.
    await act(() => new Promise((resolve) => setTimeout(resolve, 400)));

    expect(api.requests('GET', URL_AUTOCOMPLETE)).toHaveLength(0);
  });

  it('avisa que se cargue a mano cuando el proveedor está degradado', async () => {
    mockApi().on('GET', URL_AUTOCOMPLETE, {
      sugerencias: [], proveedor: 'georef', degradado: true,
      mensaje: 'El autocompletado de direcciones no está disponible en este momento; ingresá la dirección manualmente.',
    });
    renderWithProviders(<DireccionAutocomplete onSelect={vi.fn()} />);

    await userEvent.type(screen.getByLabelText('Buscar dirección'), 'Gorriti');

    await waitFor(() => expect(screen.getByText(/ingresá la dirección manualmente/)).toBeInTheDocument());
  });
});
