// Este fichero define rutas, no componentes que se editen con recarga en caliente.
/* eslint-disable react-refresh/only-export-components */
import { lazy, Suspense, type ReactNode } from 'react';
import { createBrowserRouter } from 'react-router-dom';

import { ProtectedRoute } from './auth/ProtectedRoute';
import { LoadingState } from './components/ui/States';
import { Layout } from './layout/Layout';
import { LoginPage } from './pages/auth/LoginPage';
import { RegisterPage } from './pages/auth/RegisterPage';
import { NotFoundPage } from './pages/NotFoundPage';

// Cada sección se descarga al entrar en ella: quien solo mira el resumen no se
// baja el código de presupuestos o categorías.
const DashboardPage = lazy(() => import('./pages/dashboard/DashboardPage'));
const TransactionsPage = lazy(() => import('./pages/transactions/TransactionsPage'));
const AccountsPage = lazy(() => import('./pages/accounts/AccountsPage'));
const BudgetsPage = lazy(() => import('./pages/budgets/BudgetsPage'));
const CategoriesPage = lazy(() => import('./pages/categories/CategoriesPage'));

const page = (element: ReactNode) => <Suspense fallback={<LoadingState />}>{element}</Suspense>;

export const router = createBrowserRouter([
  { path: '/login', element: <LoginPage /> },
  { path: '/register', element: <RegisterPage /> },
  {
    element: <ProtectedRoute />,
    children: [
      {
        element: <Layout />,
        children: [
          { index: true, element: page(<DashboardPage />) },
          { path: 'transactions', element: page(<TransactionsPage />) },
          { path: 'accounts', element: page(<AccountsPage />) },
          { path: 'budgets', element: page(<BudgetsPage />) },
          { path: 'categories', element: page(<CategoriesPage />) },
          { path: '*', element: <NotFoundPage /> },
        ],
      },
    ],
  },
]);
