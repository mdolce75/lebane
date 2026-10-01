const priceFormatters = new Map<string, Intl.NumberFormat>();

/** Precio con la moneda del aviso (ARS / USD), formato argentino y sin decimales si son cero. */
export function formatPrice(amount: number, currency: string): string {
  let formatter = priceFormatters.get(currency);
  if (!formatter) {
    formatter = new Intl.NumberFormat('es-AR', {
      style: 'currency',
      currency,
      currencyDisplay: 'code',
      minimumFractionDigits: 0,
      maximumFractionDigits: 2,
    });
    priceFormatters.set(currency, formatter);
  }
  return formatter.format(amount);
}

const areaFormatter = new Intl.NumberFormat('es-AR', { maximumFractionDigits: 2 });

export function formatArea(m2: number): string {
  return `${areaFormatter.format(m2)} m²`;
}

const dateFormatter = new Intl.DateTimeFormat('es-AR', { dateStyle: 'medium', timeStyle: 'short' });

export function formatDateTime(iso: string): string {
  const date = new Date(iso);
  return Number.isNaN(date.getTime()) ? '' : dateFormatter.format(date);
}

export function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(0)} KB`;
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}

export function plural(count: number, singular: string, pluralForm = `${singular}s`): string {
  return `${count} ${count === 1 ? singular : pluralForm}`;
}
