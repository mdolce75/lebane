import { Navigate, type RouteObject } from 'react-router';
import { Layout } from './Layout';
import { RouteErrorBoundary } from './RouteErrorBoundary';
import { NotFoundPage } from '../pages/NotFoundPage';
import { Spinner } from '../shared/components/Spinner';
import { DepartamentosListPage } from '../features/departamentos/listado/DepartamentosListPage';

/**
 * El listado (pantalla de entrada) va en el bundle inicial; alta, edición y detalle se cargan al navegar a ellas
 * (code splitting por ruta).
 */
export const routes: RouteObject[] = [
  {
    path: '/',
    element: <Layout />,
    errorElement: <RouteErrorBoundary />,
    // Mientras se descarga el código de una ruta lazy en la carga inicial.
    hydrateFallbackElement: <Spinner label="Cargando…" />,
    children: [
      { index: true, element: <Navigate to="/departamentos" replace /> },
      { path: 'departamentos', element: <DepartamentosListPage /> },
      {
        path: 'departamentos/nuevo',
        lazy: () =>
          import('../features/departamentos/form/DepartamentoNuevoPage').then((m) => ({
            Component: m.DepartamentoNuevoPage,
          })),
      },
      {
        path: 'departamentos/:id',
        lazy: () =>
          import('../features/departamentos/detalle/DepartamentoDetallePage').then((m) => ({
            Component: m.DepartamentoDetallePage,
          })),
      },
      {
        path: 'departamentos/:id/editar',
        lazy: () =>
          import('../features/departamentos/form/DepartamentoEditarPage').then((m) => ({
            Component: m.DepartamentoEditarPage,
          })),
      },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
];
