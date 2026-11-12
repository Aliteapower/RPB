/** Distinguish a foreign/missing customer session from a product permission denial. */
export async function customerStoreSessionMissing(storeId: string): Promise<boolean> {
  try {
    const response = await fetch(`/api/v1/public/customer-auth/me?${new URLSearchParams({ storeId })}`, {
      credentials: 'same-origin', cache: 'no-store', headers: { Accept: 'application/json' },
      signal: AbortSignal.timeout(10_000)
    })
    return response.status === 401
  } catch {
    // A failed read cannot establish an authentication result; retain the original error.
    return false
  }
}
