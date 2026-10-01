import '@testing-library/jest-dom/vitest';
import { cleanup } from '@testing-library/react';
import { afterEach, beforeEach, vi } from 'vitest';

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
