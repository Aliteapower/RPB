/** Keep a wallet workspace alive across its child pages, but never across stores or identities. */
export function routeViewKey(route: { path: string; meta: Record<string, unknown>; params: Record<string, unknown> }) {
  const workspace = route.meta.walletWorkspace
  if (workspace === 'staff' || workspace === 'customer') return `wallet:${workspace}:${String(route.params.storeId || '')}`
  return route.path
}
