import { translate } from '../i18n'

const previewVariableKeys = [
  'storeName',
  'reservationNo',
  'reservationCode',
  'reservationDate',
  'reservationTime',
  'reservedStartAt',
  'partySize',
  'tableCode',
  'holdMinutes',
  'contactName',
  'guestSalutation',
  'maskedPhone',
  'storeAddress',
  'googleMapUrl',
  'storePhone',
  'arrivalNote',
  'confirmInstruction',
  'cancelInstruction',
  'changeInstruction',
  'replyInstruction'
] as const

type ReservationShareTemplatePreviewVariableKey = (typeof previewVariableKeys)[number]

export const reservationShareTemplatePreviewVariables = buildDefaultPreviewVariables()

export interface ReservationShareTemplatePreviewSource {
  storeName?: string | null
  storeAddress?: string | null
  googleMapUrl?: string | null
  storePhone?: string | null
  arrivalNote?: string | null
}

const templateVariablePattern = /\{\{\s*([^{}]+?)\s*}}/g

export function buildReservationShareTemplatePreviewVariables(
  source: ReservationShareTemplatePreviewSource = {}
): Record<string, string> {
  return {
    ...buildDefaultPreviewVariables(),
    ...definedPreviewValues({
      storeName: source.storeName,
      storeAddress: source.storeAddress,
      googleMapUrl: source.googleMapUrl,
      storePhone: source.storePhone,
      arrivalNote: source.arrivalNote
    })
  }
}

export function renderReservationShareTemplatePreview(
  templateText: string,
  variables: Record<string, string> = buildDefaultPreviewVariables()
): string {
  return (templateText || '')
    .replace(templateVariablePattern, (token, variableName: string) => {
      const replacement = variables[variableName.trim()]
      return replacement === undefined ? token : replacement
    })
    .trimEnd()
}

function buildDefaultPreviewVariables(): Record<ReservationShareTemplatePreviewVariableKey, string> {
  return Object.fromEntries(
    previewVariableKeys.map((key) => [
      key,
      translate(`reservationShareTemplatePreview.variables.${key}`)
    ])
  ) as Record<ReservationShareTemplatePreviewVariableKey, string>
}

function definedPreviewValues(values: Record<string, string | null | undefined>): Record<string, string> {
  return Object.fromEntries(
    Object.entries(values)
      .map(([key, value]) => [key, typeof value === 'string' ? value.trim() : ''])
      .filter(([, value]) => value !== '')
  )
}
