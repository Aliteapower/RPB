export interface ReservationDateTimeSelection {
  businessDate: string
  time: string
}

const DEFAULT_FUTURE_TIME = '19:00'
const FUTURE_BUFFER_MS = 30 * 60 * 1000
const QUARTER_HOUR_MINUTES = 15

export function defaultFutureReservationDateTime(
  selectedDate: string,
  current: Date = new Date()
): ReservationDateTimeSelection {
  const normalizedDate = normalizeDateInput(selectedDate)
  const today = toDateInput(current)

  if (!normalizedDate || compareDateInput(normalizedDate, today) <= 0) {
    const future = roundUpToQuarterHour(new Date(current.getTime() + FUTURE_BUFFER_MS))
    return {
      businessDate: toDateInput(future),
      time: toTimeInput(future)
    }
  }

  return {
    businessDate: normalizedDate,
    time: DEFAULT_FUTURE_TIME
  }
}

export function isReservationStartInPast(
  businessDate: string,
  time: string,
  current: Date = new Date()
): boolean {
  const start = toLocalDateTime(businessDate, time)
  return start !== null && start.getTime() <= current.getTime()
}

export function roundUpToQuarterHour(date: Date): Date {
  const rounded = new Date(date)
  const minutes = rounded.getMinutes()
  const nextMinutes = Math.ceil(minutes / QUARTER_HOUR_MINUTES) * QUARTER_HOUR_MINUTES

  if (nextMinutes >= 60) {
    rounded.setHours(rounded.getHours() + 1, 0, 0, 0)
    return rounded
  }

  rounded.setMinutes(nextMinutes, 0, 0)
  return rounded
}

function toLocalDateTime(businessDate: string, time: string): Date | null {
  if (!normalizeDateInput(businessDate) || !isValidTimeInput(time)) {
    return null
  }

  const date = new Date(`${businessDate}T${time}:00`)
  return Number.isNaN(date.getTime()) ? null : date
}

function normalizeDateInput(value: string): string | null {
  const trimmed = value.trim()
  if (!/^\d{4}-\d{2}-\d{2}$/.test(trimmed)) {
    return null
  }

  const date = new Date(`${trimmed}T00:00:00`)
  if (Number.isNaN(date.getTime()) || toDateInput(date) !== trimmed) {
    return null
  }

  return trimmed
}

function compareDateInput(left: string, right: string): number {
  return startOfDateInput(left).getTime() - startOfDateInput(right).getTime()
}

function startOfDateInput(value: string): Date {
  return new Date(`${value}T00:00:00`)
}

function isValidTimeInput(value: string): boolean {
  return /^([01][0-9]|2[0-3]):[0-5][0-9]$/.test(value)
}

function toDateInput(date: Date): string {
  const year = date.getFullYear()
  const month = pad2(date.getMonth() + 1)
  const day = pad2(date.getDate())
  return `${year}-${month}-${day}`
}

function toTimeInput(date: Date): string {
  return `${pad2(date.getHours())}:${pad2(date.getMinutes())}`
}

function pad2(value: number): string {
  return String(value).padStart(2, '0')
}
