export interface StoreTemporalContext {
  readonly timeZone: string
  readonly locale: string
}

export const DEFAULT_STORE_TEMPORAL_CONTEXT: StoreTemporalContext = Object.freeze({
  timeZone: 'Asia/Singapore',
  locale: 'zh-CN'
})

export function storeDateInput(
  value: Date = new Date(),
  context: StoreTemporalContext = DEFAULT_STORE_TEMPORAL_CONTEXT
): string {
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: context.timeZone,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit'
  }).formatToParts(value)

  return `${part(parts, 'year')}-${part(parts, 'month')}-${part(parts, 'day')}`
}

export function formatStoreTime(
  value: string | null | undefined,
  context: StoreTemporalContext = DEFAULT_STORE_TEMPORAL_CONTEXT
): string {
  return formatTimestamp(value, context, false)
}

export function formatStoreMonthDayTime(
  value: string | null | undefined,
  context: StoreTemporalContext = DEFAULT_STORE_TEMPORAL_CONTEXT
): string {
  return formatTimestamp(value, context, true)
}

function formatTimestamp(
  value: string | null | undefined,
  context: StoreTemporalContext,
  includeMonthDay: boolean
): string {
  if (!value) {
    return ''
  }

  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }

  const parts = new Intl.DateTimeFormat(context.locale, {
    timeZone: context.timeZone,
    ...(includeMonthDay ? { month: '2-digit' as const, day: '2-digit' as const } : {}),
    hour: '2-digit',
    minute: '2-digit',
    hour12: false
  }).formatToParts(date)
  const time = `${part(parts, 'hour')}:${part(parts, 'minute')}`

  return includeMonthDay
    ? `${part(parts, 'month')}-${part(parts, 'day')} ${time}`
    : time
}

function part(parts: Intl.DateTimeFormatPart[], type: Intl.DateTimeFormatPartTypes): string {
  return parts.find(item => item.type === type)?.value ?? ''
}
