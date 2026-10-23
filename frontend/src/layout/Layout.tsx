import { useState } from 'react';
import { NavLink, Outlet, useLocation } from 'react-router-dom';

import { useAuth } from '../auth/auth-context';
import {
  AccountsIcon,
  BudgetsIcon,
  CategoriesIcon,
  CloseIcon,
  MenuIcon,
  SummaryIcon,
  TransactionsIcon,
} from '../components/icons';

const NAV = [
  { to: '/', label: 'Resumen', Icon: SummaryIcon },
  { to: '/transactions', label: 'Movimientos', Icon: TransactionsIcon },
  { to: '/accounts', label: 'Cuentas', Icon: AccountsIcon },
  { to: '/budgets', label: 'Presupuestos', Icon: BudgetsIcon },
  { to: '/categories', label: 'Categorías', Icon: CategoriesIcon },
];

function Brand() {
  return (
    <span className="flex items-center gap-2 text-[15px] font-semibold tracking-tight">
      <span
        aria-hidden="true"
        className="grid size-7 place-items-center rounded-md bg-brand text-sm text-white"
      >
        F
      </span>
      FinCore
    </span>
  );
}

export function Layout() {
  const { user, logout } = useAuth();
  const location = useLocation();
  // En móvil el menú se cierra solo al navegar: se guarda en qué ruta se abrió y
  // en cuanto la ruta cambia deja de estar abierto, sin un efecto que lo cierre.
  const [menuOpenAt, setMenuOpenAt] = useState<string | null>(null);
  const menuOpen = menuOpenAt === location.pathname;
  const setMenuOpen = (toggle: (open: boolean) => boolean) =>
    setMenuOpenAt(toggle(menuOpen) ? location.pathname : null);

  const nav = (
    <nav aria-label="Principal" className="space-y-0.5">
      {NAV.map(({ to, label, Icon }) => (
        <NavLink
          key={to}
          to={to}
          end={to === '/'}
          className={({ isActive }) =>
            `flex items-center gap-3 rounded-lg px-3 py-2 text-sm transition-colors ${
              isActive
                ? 'bg-brand-soft font-medium text-brand-strong'
                : 'text-muted hover:bg-black/5 hover:text-ink'
            }`
          }
        >
          <Icon />
          {label}
        </NavLink>
      ))}
    </nav>
  );

  const account = (
    <div className="border-t border-line pt-4">
      <p className="truncate text-sm font-medium">{user?.displayName || user?.email}</p>
      {user?.displayName && <p className="truncate text-xs text-muted">{user.email}</p>}
      <button
        type="button"
        onClick={() => void logout()}
        className="mt-2 rounded text-sm text-muted underline-offset-2 hover:text-ink hover:underline focus-visible:outline-2 focus-visible:outline-brand"
      >
        Cerrar sesión
      </button>
    </div>
  );

  return (
    <div className="min-h-screen lg:grid lg:grid-cols-[232px_1fr]">
      <a
        href="#main"
        className="sr-only focus:not-sr-only focus:absolute focus:top-2 focus:left-2 focus:z-50 focus:rounded focus:bg-surface focus:px-3 focus:py-2"
      >
        Saltar al contenido
      </a>

      {/* Escritorio: barra lateral fija. */}
      <aside className="sticky top-0 hidden h-screen flex-col justify-between border-r border-line px-4 py-6 lg:flex">
        <div className="space-y-8">
          <div className="px-3">
            <Brand />
          </div>
          {nav}
        </div>
        {account}
      </aside>

      {/* Móvil y tablet: barra superior con menú desplegable. */}
      <header className="sticky top-0 z-40 border-b border-line bg-paper/95 backdrop-blur lg:hidden">
        <div className="flex h-14 items-center justify-between px-4">
          <Brand />
          <button
            type="button"
            onClick={() => setMenuOpen((open) => !open)}
            aria-expanded={menuOpen}
            aria-controls="mobile-menu"
            aria-label={menuOpen ? 'Cerrar menú' : 'Abrir menú'}
            className="grid size-10 place-items-center rounded-lg text-ink hover:bg-black/5 focus-visible:outline-2 focus-visible:outline-brand"
          >
            {menuOpen ? <CloseIcon /> : <MenuIcon />}
          </button>
        </div>
        {menuOpen && (
          <div id="mobile-menu" className="space-y-4 px-4 pb-4">
            {nav}
            {account}
          </div>
        )}
      </header>

      <main id="main" className="mx-auto w-full max-w-6xl px-4 py-6 sm:px-6 lg:px-10 lg:py-10">
        <Outlet />
      </main>
    </div>
  );
}
