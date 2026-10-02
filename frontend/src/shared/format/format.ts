const priceFormatters = new Map<string, Intl.NumberFormat>();

/**
 * Precio con la moneda del aviso (ARS / USD) en formato argentino: sin decimales si es un importe entero y, si
 * tiene centavos, siempre con dos ("132.500,50", no "132.500,5").
 */
export function formatPrice(amount: number, currency: string): string {
  const decimals = Number.isInteger(amount) ? 0 : 2;
  const key = `${currency}:${decimals}`;
  let formatter = priceFormatters.get(key);
  if (!formatter) {
    formatter = new Intl.NumberFormat('es-AR', {
      style: 'currency',
      currency,
      currencyDisplay: 'code',
      minimumFractionDigits: decimals,
      maximumFractionDigits: decimals,
    });
    priceFormatters.set(key, formatter);
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
