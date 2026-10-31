/** Campaign inputs are wall-clock values in the store timezone. */
export function campaignLocalTime(value: string, timezone: string): string {
  const parts = new Intl.DateTimeFormat('en-CA', { timeZone: timezone, year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hourCycle: 'h23' }).formatToParts(new Date(value))
  const get = (name: Intl.DateTimeFormatPartTypes) => parts.find(p => p.type === name)!.value
  return `${get('year')}-${get('month')}-${get('day')}T${get('hour')}:${get('minute')}`
}
export function campaignInstant(value: string, timezone: string): string {
  const target = Date.parse(value + ':00Z')
  if (!Number.isFinite(target)) throw new Error('Invalid campaign time')
  let instant = target
  for (let i = 0; i < 3; i++) {
    const rendered = Date.parse(campaignLocalTime(new Date(instant).toISOString(), timezone) + ':00Z')
    instant += target - rendered
  }
  if (campaignLocalTime(new Date(instant).toISOString(), timezone) !== value) throw new Error('Campaign time does not exist in store timezone')
  return new Date(instant).toISOString()
}
