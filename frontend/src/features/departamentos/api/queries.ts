import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { ListadoParams } from '../listado/listadoParams';
import {
  actualizarDepartamento,
  crearConsulta,
  crearDepartamento,
  darDeBajaDepartamento,
  eliminarImagen,
  listarDepartamentos,
  obtenerDepartamento,
  reactivarDepartamento,
  subirImagen,
} from './departamentosApi';
import type { ConsultaPayload, DepartamentoPayload } from './schemas';

export const departamentosKeys = {
  all: ['departamentos'] as const,
  listas: () => [...departamentosKeys.all, 'lista'] as const,
  lista: (params: ListadoParams) => [...departamentosKeys.listas(), params] as const,
  detalle: (id: number) => [...departamentosKeys.all, 'detalle', id] as const,
};

/**
 * Página del listado. `keepPreviousData`: al cambiar de página o filtro se sigue mostrando la página anterior
 * (atenuada) hasta que llega la nueva, sin saltos a un estado de carga vacío.
 */
export function useDepartamentos(params: ListadoParams) {
  return useQuery({
    queryKey: departamentosKeys.lista(params),
    queryFn: ({ signal }) => listarDepartamentos(params, signal),
    placeholderData: keepPreviousData,
  });
}

export function useDepartamento(id: number) {
  return useQuery({
    queryKey: departamentosKeys.detalle(id),
    queryFn: ({ signal }) => obtenerDepartamento(id, signal),
    enabled: Number.isInteger(id) && id > 0,
  });
}

/** Tras cualquier cambio, el detalle se actualiza y los listados se invalidan (contadores, foto principal). */
function useInvalidarDepartamento() {
  const queryClient = useQueryClient();
  return (id: number) =>
    Promise.all([
      queryClient.invalidateQueries({ queryKey: departamentosKeys.detalle(id) }),
      queryClient.invalidateQueries({ queryKey: departamentosKeys.listas() }),
    ]);
}

export function useCrearDepartamento() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: DepartamentoPayload) => crearDepartamento(payload),
    onSuccess: (creado) => {
      queryClient.setQueryData(departamentosKeys.detalle(creado.id), creado);
      return queryClient.invalidateQueries({ queryKey: departamentosKeys.listas() });
    },
  });
}

export function useActualizarDepartamento(id: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ version, payload }: { version: number; payload: DepartamentoPayload }) =>
      actualizarDepartamento(id, version, payload),
    onSuccess: (actualizado) => {
      queryClient.setQueryData(departamentosKeys.detalle(id), actualizado);
      return queryClient.invalidateQueries({ queryKey: departamentosKeys.listas() });
    },
  });
}

/** Tras la baja, el detalle (ahora con `fechaBaja`) y los listados se recargan. */
export function useDarDeBajaDepartamento(id: number) {
  const invalidar = useInvalidarDepartamento();
  return useMutation({
    mutationFn: (version: number) => darDeBajaDepartamento(id, version),
    onSuccess: () => invalidar(id),
  });
}

export function useReactivarDepartamento(id: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (version: number) => reactivarDepartamento(id, version),
    onSuccess: (reactivado) => {
      queryClient.setQueryData(departamentosKeys.detalle(id), reactivado);
      return queryClient.invalidateQueries({ queryKey: departamentosKeys.listas() });
    },
  });
}

export function useSubirImagen(departamentoId: number) {
  const invalidar = useInvalidarDepartamento();
  return useMutation({
    mutationFn: (archivo: File) => subirImagen(departamentoId, archivo),
    onSettled: () => invalidar(departamentoId),
  });
}

export function useEliminarImagen(departamentoId: number) {
  const invalidar = useInvalidarDepartamento();
  return useMutation({
    mutationFn: (imagenId: number) => eliminarImagen(departamentoId, imagenId),
    onSettled: () => invalidar(departamentoId),
  });
}

export function useCrearConsulta(departamentoId: number) {
  const invalidar = useInvalidarDepartamento();
  return useMutation({
    mutationFn: (payload: ConsultaPayload) => crearConsulta(departamentoId, payload),
    onSuccess: () => invalidar(departamentoId),
  });
}
