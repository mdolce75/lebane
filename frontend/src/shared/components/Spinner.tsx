type Props = { label?: string };

export function Spinner({ label = 'Cargando…' }: Props) {
  return (
    <div role="status" aria-live="polite" className="spinner">
      <span className="spinner__circle" aria-hidden="true" />
      <span>{label}</span>
    </div>
  );
}
