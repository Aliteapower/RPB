export function uniqueHeaderCaption(...names: (string | null | undefined)[]): string {
  return [...new Set(names.map(name => name?.trim()).filter(Boolean))].join(' · ')
}
