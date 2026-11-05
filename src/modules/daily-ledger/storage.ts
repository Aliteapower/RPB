import type { LedgerRecord, LedgerConfig, SaveRequest } from './types'
export interface LedgerOutbox { record: LedgerRecord; config: LedgerConfig; pending?: SaveRequest; revision: number; pendingRevision: number; dirty: boolean }
export function storageKey(ownerKey: string, storeId: string, date: string, period: string) { return `rpb:daily-ledger:${encodeURIComponent(ownerKey)}:${storeId}:${date}:${period}` }
export function readLocal<T>(key: string): T | undefined { try { const raw = window.localStorage.getItem(key); return raw ? JSON.parse(raw) as T : undefined } catch { return undefined } }
export function writeLocal(key: string, value: unknown) { try { window.localStorage.setItem(key, JSON.stringify(value)); return true } catch { return false } }
export function removeLocal(key: string) { try { window.localStorage.removeItem(key) } catch { /* Cloud saving remains available when browser storage is unavailable. */ } }
