import { describe, expect, it } from 'vitest'

import {
  DEFAULT_STORE_TEMPORAL_CONTEXT,
  formatStoreMonthDayTime,
  formatStoreTime,
  storeDateInput
} from './storeTemporalContext'

describe('StoreTemporalContext', () => {
  const midnightBoundary = new Date('2026-07-16T16:30:00.000Z')

  it('derives the Singapore business date across the UTC midnight boundary', () => {
    expect(DEFAULT_STORE_TEMPORAL_CONTEXT).toEqual({
      timeZone: 'Asia/Singapore',
      locale: 'zh-CN'
    })
    expect(storeDateInput(midnightBoundary)).toBe('2026-07-17')
  })

  it('formats the same instant with the current Table and Queue output shapes', () => {
    const value = midnightBoundary.toISOString()

    expect(formatStoreTime(value)).toBe('00:30')
    expect(formatStoreMonthDayTime(value)).toBe('07-17 00:30')
  })

  it('keeps null and malformed input behaviour explicit', () => {
    expect(formatStoreTime(null)).toBe('')
    expect(formatStoreMonthDayTime(undefined)).toBe('')
    expect(formatStoreTime('not-a-date')).toBe('not-a-date')
    expect(formatStoreMonthDayTime('not-a-date')).toBe('not-a-date')
  })
})
