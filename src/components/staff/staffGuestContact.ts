export const SINGAPORE_PHONE_PREFIX = '+65'
export const LOCAL_SINGAPORE_PHONE_PATTERN = /^[0-9]{8}$/

export function sanitizeSingaporeLocalPhone(value: string): string {
  return value.replace(/\D/g, '').slice(0, 8)
}

export function isValidSingaporeLocalPhone(value: string): boolean {
  return LOCAL_SINGAPORE_PHONE_PATTERN.test(value.trim())
}

export function toSingaporePhoneE164(value: string): string | null {
  const phoneLocal = value.trim()
  return phoneLocal ? `${SINGAPORE_PHONE_PREFIX}${phoneLocal}` : null
}
