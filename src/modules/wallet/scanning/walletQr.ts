export type WalletQrPurpose = 'customer' | 'bill'

/** Shape/type checks only. Expiry, ownership and replay remain server decisions. */
export function normalizeWalletQr(value: string, purpose: WalletQrPurpose, origin: string): string | null {
  const text = value.trim()
  if (text.length > 4096) return null
  if (purpose === 'customer') return /^WP\.[A-Za-z0-9_-]{43}$/.test(text) ? text : null
  if (/^WB\.[A-Za-z0-9_-]{43}$/.test(text)) return text
  try {
    const url = new URL(text)
    const code = new URLSearchParams(url.hash.slice(1)).get('bill') ?? ''
    if (url.origin === origin && /^\/wallet\/[0-9a-f-]{36}$/i.test(url.pathname) && /^WB\.[A-Za-z0-9_-]{43}$/.test(code)) return text
  } catch { /* Camera may encounter unrelated QR content. Keep scanning. */ }
  return null
}
