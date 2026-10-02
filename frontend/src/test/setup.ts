import '@testing-library/jest-dom/vitest';
import { cleanup, configure } from '@testing-library/react';
import { afterEach, beforeEach, vi } from 'vitest';

// Las rutas de alta, detalle y edición son lazy: el primer test de cada archivo espera además que Vitest transforme
// ese módulo (en frío y con las suites en paralelo puede superar el segundo por defecto de findBy/waitFor).
configure({ asyncUtilTimeout: 5000 });

// jsdom reemplaza AbortSignal, pero el Request nativo de Node (que React Router usa al navegar) solo acepta el
// AbortSignal propio de Node. En tests se descarta la señal al construir Request: solo afecta la cancelación de
// navegaciones del router (fetch está mockeado y no usa Request).
const NativeRequest = globalThis.Request;
globalThis.Request = class extends NativeRequest {
  constructor(input: RequestInfo | URL, init?: RequestInit) {
    const { signal: _ignored, ...rest } = init ?? {};
    void _ignored;
    super(input, rest);
  }
} as typeof Request;

// jsdom no implementa object URLs (vistas previas de fotos) ni scrollTo.
let objectUrlSequence = 0;
beforeEach(() => {
  URL.createObjectURL = vi.fn(() => `blob:preview-${++objectUrlSequence}`);
  URL.revokeObjectURL = vi.fn();
  window.scrollTo = vi.fn() as unknown as typeof window.scrollTo;
});

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});
