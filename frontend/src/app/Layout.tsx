import { Link, NavLink, Outlet } from 'react-router';
import { BackendStatus } from '../features/system/BackendStatus';

export function Layout() {
  return (
    <div className="layout">
      <header className="header">
        <div className="header__bar">
          <Link to="/departamentos" className="header__brand" aria-label="Lebane, inicio">
            <svg className="header__mark" viewBox="0 0 24 24" aria-hidden="true">
              <rect width="24" height="24" rx="6" />
              <path d="M8 6v12h9" />
            </svg>
            Lebane
          </Link>
          <nav aria-label="Principal" className="header__nav">
            <NavLink to="/departamentos" end>Departamentos</NavLink>
          </nav>
          <div className="header__actions">
            <BackendStatus />
            <Link to="/departamentos/nuevo" className="button header__cta">Publicar departamento</Link>
          </div>
        </div>
      </header>
      <main className="main">
        <Outlet />
      </main>
    </div>
  );
}
