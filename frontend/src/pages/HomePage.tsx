import { Link } from 'react-router';

export function HomePage() {
  return (
    <section>
      <h1>Panel de departamentos</h1>
      <p>Administrá los departamentos en venta: alta, edición, imágenes y consultas.</p>
      <Link to="/departamentos" className="button">
        Ver departamentos
      </Link>
    </section>
  );
}
