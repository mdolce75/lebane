import type { RouteObject } from 'react-router';
import { Layout } from './Layout';
import { RouteErrorBoundary } from './RouteErrorBoundary';
import { HomePage } from '../pages/HomePage';
import { DepartamentosPage } from '../pages/DepartamentosPage';
import { NotFoundPage } from '../pages/NotFoundPage';

export const routes: RouteObject[] = [
  {
    path: '/',
    element: <Layout />,
    errorElement: <RouteErrorBoundary />,
    children: [
      { index: true, element: <HomePage /> },
      { path: 'departamentos', element: <DepartamentosPage /> },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
];
