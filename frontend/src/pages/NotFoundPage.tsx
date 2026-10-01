import { Link } from 'react-router';

export function NotFoundPage() {
  return (
    <section>
      <h1>Página no encontrada</h1>
      <p>La dirección que buscás no existe.</p>
      <Link to="/">Volver al inicio</Link>
    </section>
  );
}
