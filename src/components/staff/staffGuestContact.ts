import {
  isValidCountryLocalPhone,
  sanitizeCountryLocalPhone,
  toCountryPhoneE164
} from '../../utils/countryPhone'

export const SINGAPORE_PHONE_PREFIX = '+65'
export const LOCAL_SINGAPORE_PHONE_PATTERN = /^[0-9]{8}$/

export function sanitizeSingaporeLocalPhone(value: string): string {
  return sanitizeCountryLocalPhone(value, 'SG')
}

export function isValidSingaporeLocalPhone(value: string): boolean {
  return isValidCountryLocalPhone(value, 'SG')
}

export function toSingaporePhoneE164(value: string): string | null {
  return toCountryPhoneE164(value, 'SG')
}
