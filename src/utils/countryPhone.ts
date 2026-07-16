export type PhoneCountryCode = 'SG' | 'CN'

export interface CountryPhoneConfig {
  countryCode: PhoneCountryCode
  callingCode: string
  nationalLength: number
  placeholder: string
}

export const DEFAULT_PHONE_COUNTRY_CODE: PhoneCountryCode = 'SG'

export const COUNTRY_PHONE_CONFIGS: Record<PhoneCountryCode, CountryPhoneConfig> = {
  SG: {
    countryCode: 'SG',
    callingCode: '+65',
    nationalLength: 8,
    placeholder: '91234567'
  },
  CN: {
    countryCode: 'CN',
    callingCode: '+86',
    nationalLength: 11,
    placeholder: '13800138000'
  }
}

export function phoneCountryConfig(countryCode: PhoneCountryCode = DEFAULT_PHONE_COUNTRY_CODE): CountryPhoneConfig {
  return COUNTRY_PHONE_CONFIGS[countryCode]
}

export function sanitizeCountryLocalPhone(
  value: string,
  countryCode: PhoneCountryCode = DEFAULT_PHONE_COUNTRY_CODE
): string {
  return digitsOnly(value).slice(0, phoneCountryConfig(countryCode).nationalLength)
}

export function isValidCountryLocalPhone(
  value: string,
  countryCode: PhoneCountryCode = DEFAULT_PHONE_COUNTRY_CODE
): boolean {
  const localPhone = value.trim()
  return /^\d+$/.test(localPhone) && localPhone.length === phoneCountryConfig(countryCode).nationalLength
}

export function toCountryPhoneE164(
  value: string,
  countryCode: PhoneCountryCode = DEFAULT_PHONE_COUNTRY_CODE
): string | null {
  const localPhone = sanitizeCountryLocalPhone(value, countryCode)
  return localPhone ? `${phoneCountryConfig(countryCode).callingCode}${localPhone}` : null
}

export function storedPhoneToCountryLocal(
  value: string | null | undefined,
  countryCode: PhoneCountryCode = DEFAULT_PHONE_COUNTRY_CODE
): string {
  const rawValue = String(value || '').trim()
  if (!rawValue) {
    return ''
  }

  const config = phoneCountryConfig(countryCode)
  const digits = digitsOnly(rawValue)
  const callingDigits = digitsOnly(config.callingCode)

  if (rawValue.startsWith(config.callingCode) || (rawValue.startsWith('+') && digits.startsWith(callingDigits))) {
    return digits.slice(callingDigits.length, callingDigits.length + config.nationalLength)
  }

  if (!rawValue.startsWith('+') && digits.length <= config.nationalLength) {
    return sanitizeCountryLocalPhone(rawValue, countryCode)
  }

  return ''
}

export function isLegacyCountryPhone(
  value: string | null | undefined,
  countryCode: PhoneCountryCode = DEFAULT_PHONE_COUNTRY_CODE
): boolean {
  const rawValue = String(value || '').trim()
  return Boolean(rawValue) && !storedPhoneToCountryLocal(rawValue, countryCode)
}

function digitsOnly(value: string): string {
  return value.replace(/\D/g, '')
}
