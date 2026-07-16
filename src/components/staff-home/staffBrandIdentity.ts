import type { AuthStoreAccess } from '../../types/auth'

export interface StaffBrandIdentity {
  displayName: string
  logoMediaUrl: string
  fallbackMark: string
}

export function resolveStaffBrandIdentity(
  store: AuthStoreAccess | undefined,
  fallbackLabel: string,
  genericLabel: string
): StaffBrandIdentity {
  const displayName = firstText(
    store?.shareDisplayName,
    store?.storeName,
    fallbackLabel,
    genericLabel
  )

  return {
    displayName,
    logoMediaUrl: clean(store?.tenantLogoMediaUrl),
    fallbackMark: Array.from(displayName)[0] || Array.from(genericLabel.trim())[0] || ''
  }
}

function firstText(...values: Array<string | null | undefined>): string {
  for (const value of values) {
    const normalized = clean(value)
    if (normalized) {
      return normalized
    }
  }
  return ''
}

function clean(value: string | null | undefined): string {
  return value?.trim() || ''
}
