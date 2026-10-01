import { NavLink, Outlet } from 'react-router';
import { BackendStatus } from '../features/system/BackendStatus';

export function Layout() {
  return (
    <div className="layout">
      <header className="header">
        <NavLink to="/departamentos" className="header__brand">
          Lebane
        </NavLink>
        <nav aria-label="Principal" className="header__nav">
          <NavLink to="/departamentos" end>Departamentos</NavLink>
          <NavLink to="/departamentos/nuevo">Nuevo</NavLink>
        </nav>
        <BackendStatus />
      </header>
      <main className="main">
        <Outlet />
      </main>
    </div>
  );
}
