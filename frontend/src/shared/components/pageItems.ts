/** Números de página a mostrar: primera, última y vecinas de la actual, con "…" en los saltos. */
export function pageItems(page: number, last: number): (number | 'gap')[] {
  const pages = new Set([0, last, page - 1, page, page + 1].filter((p) => p >= 0 && p <= last));
  const sorted = [...pages].sort((a, b) => a - b);
  const items: (number | 'gap')[] = [];
  sorted.forEach((p, index) => {
    const previous = sorted[index - 1];
    if (index > 0 && previous !== undefined && p - previous > 1) items.push('gap');
    items.push(p);
  });
  return items;
}
