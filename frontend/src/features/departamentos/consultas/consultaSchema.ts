import { z } from 'zod';

/** Mismas reglas que ConsultaRequest del backend. */
export const consultaSchema = z.object({
  nombre: z.string().trim().min(1, 'Es obligatorio').max(100, 'Máximo 100 caracteres'),
  email: z.string().trim().min(1, 'Es obligatorio').max(254).email('Ingresá un email válido'),
  telefono: z
    .string()
    .trim()
    .refine((v) => v === '' || /^[+0-9 ()-]{6,30}$/.test(v), 'Ingresá un teléfono válido'),
  mensaje: z.string().trim().min(10, 'Al menos 10 caracteres').max(2000, 'Máximo 2000 caracteres'),
});

export type ConsultaFormValues = z.infer<typeof consultaSchema>;
