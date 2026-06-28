export const reservationShareTemplatePreviewVariables: Record<string, string> = {
  storeName: '示例门店',
  reservationNo: 'R-EXAMPLE-0001',
  reservationDate: '15-07-2026 (星期三)',
  reservationTime: '19:30',
  partySize: '2',
  tableCode: 'A01',
  holdMinutes: '15',
  contactName: '示例顾客',
  guestSalutation: '先生',
  maskedPhone: '0000****',
  storeAddress: '示例地址 1 号',
  googleMapUrl: 'https://example.com/map',
  storePhone: '0000 0000',
  arrivalNote: '请提前 10 分钟到店',
  confirmInstruction: '回复确认可保留订位',
  cancelInstruction: '如需取消，请提前 2 小时联系我们',
  changeInstruction: '如需修改人数或时间，请致电门店',
  replyInstruction: '收到请回复确认'
}

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
    ...reservationShareTemplatePreviewVariables,
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
  variables: Record<string, string> = reservationShareTemplatePreviewVariables
): string {
  return (templateText || '')
    .replace(templateVariablePattern, (token, variableName: string) => {
      const replacement = variables[variableName.trim()]
      return replacement === undefined ? token : replacement
    })
    .trimEnd()
}

function definedPreviewValues(values: Record<string, string | null | undefined>): Record<string, string> {
  return Object.fromEntries(
    Object.entries(values)
      .map(([key, value]) => [key, typeof value === 'string' ? value.trim() : ''])
      .filter(([, value]) => value !== '')
  )
}
