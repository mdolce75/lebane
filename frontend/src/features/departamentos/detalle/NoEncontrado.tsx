import { Link } from 'react-router';

export function NoEncontrado() {
  return (
    <section>
      <h1>Departamento no encontrado</h1>
      <p>El departamento que buscás no existe o fue eliminado.</p>
      <Link to="/departamentos">Volver al listado</Link>
    </section>
  );
}
