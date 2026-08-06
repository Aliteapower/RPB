import type { PaymentIntentCreateResponse } from '../types/payment'
import { extractPayNowQrPayload } from './paymentQrPayloads'

export const PAYMENT_PRESENT_TTL_SECONDS = 120
export const MAX_PRESENT_PAYMENTS = 4

const CHANNEL_PREFIX = 'rpb-payment-present'
const ACTIVE_PREFIX = 'rpb.payment.present.active'
const RECENT_PREFIX = 'rpb.payment.present.recent'
const PRESET_PREFIX = 'rpb.payment.quickPay.presets'
const SETTINGS_PREFIX = 'rpb.payment.present.settings'
const DEFAULT_PRESET_AMOUNTS = ['5', '10', '20', '50', '100', '200']
const MAX_RECENT_ITEMS = 6
const DEFAULT_PRESENT_SETTINGS: PaymentPresentSettings = {
  maxPayments: 1,
  qrPerPayment: 1,
  primaryQr: 'sgqr'
}

export type PresentMaxPayments = 1 | 2 | 3 | 4
export type PresentQrPerPayment = 1
export type PresentPrimaryQr = 'sgqr'

export interface PaymentPresentPayload {
  storeId: string
  terminalCode: string
  sessionNo: string
  intentNo: string
  displayNumber: number
  businessDate: string
  status: string
  amount: string
  currency: string
  qrPayload: string
  createdAtMs: number
}

export interface PaymentPresentRecentItem {
  sessionNo: string
  intentNo: string
  displayNumber: number
  amount: string
  currency: string
  status: string
  createdAtMs: number
}

export interface PaymentPresentSettings {
  maxPayments: PresentMaxPayments
  qrPerPayment: PresentQrPerPayment
  primaryQr: PresentPrimaryQr
}

interface PaymentPresentBroadcastMessage {
  kind: 'payloads' | 'settings'
  payloads?: PaymentPresentPayload[]
  settings?: PaymentPresentSettings
}

export function paymentPresentChannelName(storeId: string, terminalCode: string): string {
  return `${CHANNEL_PREFIX}:${normalizeKeyPart(storeId)}:${normalizeKeyPart(terminalCode)}`
}

export function paymentPresentUrl(storeId: string, terminalCode: string): string {
  return `/stores/${encodeURIComponent(storeId)}/payments/present/${encodeURIComponent(normalizeTerminalCode(terminalCode))}`
}

export function normalizeTerminalCode(value: string | null | undefined): string {
  return String(value || '').trim() || 'T1'
}

export function buildPaymentPresentPayload(
  response: PaymentIntentCreateResponse,
  storeId: string,
  terminalCode: string
): PaymentPresentPayload {
  return {
    storeId,
    terminalCode: normalizeTerminalCode(terminalCode),
    sessionNo: response.session.sessionNo,
    intentNo: response.intent.intentNo,
    displayNumber: response.session.displayNumber,
    businessDate: response.session.businessDate,
    status: response.session.status || response.intent.status || 'pending',
    amount: response.intent.amount,
    currency: response.intent.currency,
    qrPayload: extractPayNowQrPayload(response.session.qrPayloadsJson),
    createdAtMs: Date.now()
  }
}

export function publishPaymentPresentPayload(payload: PaymentPresentPayload): void {
  const settings = readPaymentPresentSettings(payload.storeId, payload.terminalCode)
  const existing = readPaymentPresentPayloads(payload.storeId, payload.terminalCode)
    .filter(value => value.sessionNo !== payload.sessionNo && isPaymentPresentPayloadActive(value))
  const next = [payload, ...existing].slice(0, settings.maxPayments)
  safeSetJson(activeStorageKey(payload.storeId, payload.terminalCode), next)
  pushPaymentPresentRecent(payload)
  postPaymentPresentMessage(payload.storeId, payload.terminalCode, {
    kind: 'payloads',
    payloads: next
  })
}

export function subscribePaymentPresentPayloads(
  storeId: string,
  terminalCode: string,
  onPayloads: (payloads: PaymentPresentPayload[]) => void
): () => void {
  const normalizedTerminal = normalizeTerminalCode(terminalCode)
  const channelName = paymentPresentChannelName(storeId, normalizedTerminal)
  const storageKey = activeStorageKey(storeId, normalizedTerminal)
  let channel: BroadcastChannel | null = null

  if (typeof BroadcastChannel !== 'undefined') {
    channel = new BroadcastChannel(channelName)
    channel.onmessage = event => {
      const message = parseBroadcastMessage(event.data)
      if (!message || message.kind !== 'payloads') {
        return
      }
      onPayloads(parsePaymentPresentPayloads(message.payloads))
    }
  }

  const storageListener = (event: StorageEvent) => {
    if (event.key !== storageKey) {
      return
    }
    onPayloads(parsePaymentPresentPayloads(event.newValue))
  }

  window.addEventListener('storage', storageListener)

  return () => {
    window.removeEventListener('storage', storageListener)
    channel?.close()
  }
}

export function subscribePaymentPresentPayload(
  storeId: string,
  terminalCode: string,
  onPayload: (payload: PaymentPresentPayload | null) => void
): () => void {
  const normalizedTerminal = normalizeTerminalCode(terminalCode)
  const channelName = paymentPresentChannelName(storeId, normalizedTerminal)
  const storageKey = activeStorageKey(storeId, normalizedTerminal)
  let channel: BroadcastChannel | null = null

  if (typeof BroadcastChannel !== 'undefined') {
    channel = new BroadcastChannel(channelName)
    channel.onmessage = event => {
      const payloads = parsePaymentPresentPayloadsFromMessage(event.data)
      onPayload(payloads[0] || null)
    }
  }

  const storageListener = (event: StorageEvent) => {
    if (event.key !== storageKey) {
      return
    }
    onPayload(readPaymentPresentPayload(storeId, normalizedTerminal))
  }

  window.addEventListener('storage', storageListener)

  return () => {
    window.removeEventListener('storage', storageListener)
    channel?.close()
  }
}

export function readPaymentPresentPayload(storeId: string, terminalCode: string): PaymentPresentPayload | null {
  return readPaymentPresentPayloads(storeId, terminalCode)[0] || null
}

export function readPaymentPresentPayloads(storeId: string, terminalCode: string): PaymentPresentPayload[] {
  return parsePaymentPresentPayloads(safeGet(activeStorageKey(storeId, terminalCode)))
}

export function clearPaymentPresentPayload(storeId: string, terminalCode: string): void {
  clearPaymentPresentPayloads(storeId, terminalCode)
}

export function clearPaymentPresentPayloads(storeId: string, terminalCode: string): void {
  try {
    window.localStorage.removeItem(activeStorageKey(storeId, terminalCode))
  } catch {
    // Ignore storage restrictions in private browsing or locked-down kiosks.
  }
  postPaymentPresentMessage(storeId, terminalCode, {
    kind: 'payloads',
    payloads: []
  })
}

export function isPaymentPresentPayloadActive(payload: PaymentPresentPayload, nowMs = Date.now()): boolean {
  return nowMs - payload.createdAtMs < PAYMENT_PRESENT_TTL_SECONDS * 1000
}

export function paymentPresentSecondsRemaining(payload: PaymentPresentPayload, nowMs = Date.now()): number {
  const expiresAtMs = payload.createdAtMs + PAYMENT_PRESENT_TTL_SECONDS * 1000
  return Math.max(0, Math.ceil((expiresAtMs - nowMs) / 1000))
}

export function readPaymentPresentSettings(storeId: string, terminalCode: string): PaymentPresentSettings {
  const parsed = safeGet(settingsStorageKey(storeId, terminalCode))
  if (!parsed) {
    return { ...DEFAULT_PRESENT_SETTINGS }
  }
  return normalizePaymentPresentSettings(safeParseJson(parsed))
}

export function savePaymentPresentSettings(
  storeId: string,
  terminalCode: string,
  settings: Partial<PaymentPresentSettings>
): PaymentPresentSettings {
  const next = normalizePaymentPresentSettings(settings)
  safeSetJson(settingsStorageKey(storeId, terminalCode), next)
  const activePayloads = readPaymentPresentPayloads(storeId, terminalCode)
    .filter(payload => isPaymentPresentPayloadActive(payload))
    .slice(0, next.maxPayments)
  safeSetJson(activeStorageKey(storeId, terminalCode), activePayloads)
  return next
}

export function publishPaymentPresentSettings(
  storeId: string,
  terminalCode: string,
  settings: Partial<PaymentPresentSettings>
): PaymentPresentSettings {
  const next = savePaymentPresentSettings(storeId, terminalCode, settings)
  postPaymentPresentMessage(storeId, terminalCode, {
    kind: 'settings',
    settings: next
  })
  postPaymentPresentMessage(storeId, terminalCode, {
    kind: 'payloads',
    payloads: readPaymentPresentPayloads(storeId, terminalCode).slice(0, next.maxPayments)
  })
  return next
}

export function subscribePaymentPresentSettings(
  storeId: string,
  terminalCode: string,
  onSettings: (settings: PaymentPresentSettings) => void
): () => void {
  const normalizedTerminal = normalizeTerminalCode(terminalCode)
  const channelName = paymentPresentChannelName(storeId, normalizedTerminal)
  const storageKey = settingsStorageKey(storeId, normalizedTerminal)
  let channel: BroadcastChannel | null = null

  if (typeof BroadcastChannel !== 'undefined') {
    channel = new BroadcastChannel(channelName)
    channel.onmessage = event => {
      const message = parseBroadcastMessage(event.data)
      if (!message || message.kind !== 'settings') {
        return
      }
      onSettings(normalizePaymentPresentSettings(message.settings))
    }
  }

  const storageListener = (event: StorageEvent) => {
    if (event.key !== storageKey) {
      return
    }
    onSettings(readPaymentPresentSettings(storeId, normalizedTerminal))
  }

  window.addEventListener('storage', storageListener)

  return () => {
    window.removeEventListener('storage', storageListener)
    channel?.close()
  }
}

export function readPaymentPresentRecent(storeId: string, terminalCode: string): PaymentPresentRecentItem[] {
  const value = safeGet(recentStorageKey(storeId, terminalCode))
  if (!value) {
    return []
  }
  try {
    const parsed = JSON.parse(value)
    return Array.isArray(parsed) ? parsed.map(parseRecentItem).filter(isPresentRecentItem).slice(0, MAX_RECENT_ITEMS) : []
  } catch {
    return []
  }
}

export function pushPaymentPresentRecent(payload: PaymentPresentPayload): PaymentPresentRecentItem[] {
  const item: PaymentPresentRecentItem = {
    sessionNo: payload.sessionNo,
    intentNo: payload.intentNo,
    displayNumber: payload.displayNumber,
    amount: payload.amount,
    currency: payload.currency,
    status: payload.status,
    createdAtMs: payload.createdAtMs
  }
  const existing = readPaymentPresentRecent(payload.storeId, payload.terminalCode)
  const next = [item, ...existing.filter(value => value.sessionNo !== item.sessionNo)].slice(0, MAX_RECENT_ITEMS)
  safeSetJson(recentStorageKey(payload.storeId, payload.terminalCode), next)
  return next
}

export function readQuickPayPresetAmounts(storeId: string): string[] {
  const parsed = safeGet(presetStorageKey(storeId))
  if (!parsed) {
    return DEFAULT_PRESET_AMOUNTS
  }
  try {
    const values = normalizePresetAmounts(JSON.parse(parsed))
    return values.length ? values : DEFAULT_PRESET_AMOUNTS
  } catch {
    return DEFAULT_PRESET_AMOUNTS
  }
}

export function saveQuickPayPresetAmounts(storeId: string, values: string[]): string[] {
  const next = normalizePresetAmounts(values)
  safeSetJson(presetStorageKey(storeId), next)
  return next
}

export function parsePresetAmountText(value: string): string[] {
  return normalizePresetAmounts(String(value || '').split(/[,\s，]+/))
}

function parsePaymentPresentPayload(value: unknown): PaymentPresentPayload | null {
  const raw = typeof value === 'string' ? safeParseJson(value) : value
  if (!raw || typeof raw !== 'object') {
    return null
  }
  const source = raw as Record<string, unknown>
  const payload: PaymentPresentPayload = {
    storeId: String(source.storeId || ''),
    terminalCode: normalizeTerminalCode(String(source.terminalCode || '')),
    sessionNo: String(source.sessionNo || ''),
    intentNo: String(source.intentNo || ''),
    displayNumber: Number(source.displayNumber || 0),
    businessDate: String(source.businessDate || ''),
    status: String(source.status || 'pending'),
    amount: String(source.amount || ''),
    currency: String(source.currency || 'SGD'),
    qrPayload: String(source.qrPayload || ''),
    createdAtMs: Number(source.createdAtMs || 0)
  }
  if (!payload.storeId || !payload.sessionNo || !payload.qrPayload || !Number.isFinite(payload.createdAtMs)) {
    return null
  }
  return payload
}

function parsePaymentPresentPayloads(value: unknown): PaymentPresentPayload[] {
  const raw = typeof value === 'string' ? safeParseJson(value) : value
  if (Array.isArray(raw)) {
    return raw.map(parsePaymentPresentPayload).filter(isPresentPayload).slice(0, MAX_PRESENT_PAYMENTS)
  }
  const message = parseBroadcastMessage(raw)
  if (message?.kind === 'payloads') {
    return parsePaymentPresentPayloads(message.payloads)
  }
  const single = parsePaymentPresentPayload(raw)
  return single ? [single] : []
}

function parsePaymentPresentPayloadsFromMessage(value: unknown): PaymentPresentPayload[] {
  const message = parseBroadcastMessage(value)
  if (message?.kind === 'payloads') {
    return parsePaymentPresentPayloads(message.payloads)
  }
  return parsePaymentPresentPayloads(value)
}

function isPresentPayload(value: PaymentPresentPayload | null): value is PaymentPresentPayload {
  return value !== null
}

function parseRecentItem(value: unknown): PaymentPresentRecentItem | null {
  if (!value || typeof value !== 'object') {
    return null
  }
  const source = value as Record<string, unknown>
  const item: PaymentPresentRecentItem = {
    sessionNo: String(source.sessionNo || ''),
    intentNo: String(source.intentNo || ''),
    displayNumber: Number(source.displayNumber || 0),
    amount: String(source.amount || ''),
    currency: String(source.currency || 'SGD'),
    status: String(source.status || 'pending'),
    createdAtMs: Number(source.createdAtMs || 0)
  }
  return item.sessionNo ? item : null
}

function isPresentRecentItem(value: PaymentPresentRecentItem | null): value is PaymentPresentRecentItem {
  return value !== null
}

function normalizePaymentPresentSettings(value: unknown): PaymentPresentSettings {
  const source = value && typeof value === 'object' ? value as Record<string, unknown> : {}
  const maxPayments = Number(source.maxPayments || DEFAULT_PRESENT_SETTINGS.maxPayments)
  return {
    maxPayments: normalizeMaxPayments(maxPayments),
    qrPerPayment: 1,
    primaryQr: 'sgqr'
  }
}

function normalizeMaxPayments(value: number): PresentMaxPayments {
  if (value === 2 || value === 3 || value === 4) {
    return value
  }
  return 1
}

function normalizePresetAmounts(values: unknown): string[] {
  const source = Array.isArray(values) ? values : []
  const next = source
    .map(value => Number(String(value).trim()))
    .filter(value => Number.isFinite(value) && value > 0)
    .map(value => formatPresetAmount(value))
  return Array.from(new Set(next)).slice(0, 6)
}

function formatPresetAmount(value: number): string {
  return Number.isInteger(value) ? String(value) : value.toFixed(2)
}

function activeStorageKey(storeId: string, terminalCode: string): string {
  return `${ACTIVE_PREFIX}:${normalizeKeyPart(storeId)}:${normalizeKeyPart(terminalCode)}`
}

function recentStorageKey(storeId: string, terminalCode: string): string {
  return `${RECENT_PREFIX}:${normalizeKeyPart(storeId)}:${normalizeKeyPart(terminalCode)}`
}

function presetStorageKey(storeId: string): string {
  return `${PRESET_PREFIX}:${normalizeKeyPart(storeId)}`
}

function settingsStorageKey(storeId: string, terminalCode: string): string {
  return `${SETTINGS_PREFIX}:${normalizeKeyPart(storeId)}:${normalizeKeyPart(terminalCode)}`
}

function normalizeKeyPart(value: string): string {
  return encodeURIComponent(String(value || '').trim())
}

function postPaymentPresentMessage(storeId: string, terminalCode: string, message: PaymentPresentBroadcastMessage): void {
  if (typeof BroadcastChannel === 'undefined') {
    return
  }
  const channel = new BroadcastChannel(paymentPresentChannelName(storeId, terminalCode))
  try {
    channel.postMessage(message)
  } finally {
    channel.close()
  }
}

function parseBroadcastMessage(value: unknown): PaymentPresentBroadcastMessage | null {
  const raw = typeof value === 'string' ? safeParseJson(value) : value
  if (!raw || typeof raw !== 'object') {
    return null
  }
  const source = raw as Record<string, unknown>
  if (source.kind !== 'payloads' && source.kind !== 'settings') {
    return null
  }
  return {
    kind: source.kind,
    payloads: Array.isArray(source.payloads) ? parsePaymentPresentPayloads(source.payloads) : undefined,
    settings: source.settings ? normalizePaymentPresentSettings(source.settings) : undefined
  }
}

function safeGet(key: string): string | null {
  try {
    return window.localStorage.getItem(key)
  } catch {
    return null
  }
}

function safeSetJson(key: string, value: unknown): void {
  try {
    window.localStorage.setItem(key, JSON.stringify(value))
  } catch {
    // Ignore storage quota/security failures. Broadcast still covers active windows.
  }
}

function safeParseJson(value: string): unknown {
  try {
    return JSON.parse(value)
  } catch {
    return null
  }
}
