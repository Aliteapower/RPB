import {moduleRequest as cardRequest} from '../api'
import {moduleRequest} from '../../shared-commerce/http'

export type VoucherKind = 'cash' | 'product'
export interface VoucherStore { id: string; name: string }
export interface Face {
  name: string
  kind: VoucherKind
  productId: string | null
  productName: string | null
  specification: string | null
  unit: string | null
  quantity: number | null
  valueCents: number
  currency: string
  stores: VoucherStore[]
  validFrom: string
  validThrough: string
  timezone: string
  rules: string
  image: string | null
}
export interface Summary {
  id: string
  activityCode: string
  state: 'draft' | 'active' | 'disabled' | 'archived'
  version: number
  total: number
  issued: number
  redeemed: number
  startsAt: string
  endsAt: string
  name: string
  kind: VoucherKind
}
export type Campaign = Omit<Summary, 'name' | 'kind'> & { face: Face; batchCode: string; redeemedAt: string | null; issueTotal: number | null; redemptionNumber: number | null }
export interface PublicView {
  status: 'open' | 'claimed' | 'used' | 'already_claimed' | 'upcoming' | 'disabled' | 'ended' | 'sold_out'
  face: Face
  code: string | null
  batchCode: string
}
export interface VoucherReportRow {
  id: string
  activityCode: string
  name: string
  kind: VoucherKind
  total: number
  issued: number
  redeemed: number
  expired: number
}
export interface VoucherReportPage {items: VoucherReportRow[]; hasNext: boolean}
export interface ScanResult { status: 'redeemed' | 'used' | 'expired' | 'too_early'; face: Face; batchCode: string; redeemedAt: string | null; issueTotal: number | null; redemptionNumber: number | null }

export function voucherStaff<T>(store: string, path: string, method = 'GET', body?: unknown) {
  return cardRequest<T>('/api/v1/stores/' + encodeURIComponent(store) + '/wallet/cards', path, method, body)
}

export function voucherPublic(id: string, receipt: string, claim = false) {
  return moduleRequest<PublicView>('/api/v1/public/voucher-campaigns/' + encodeURIComponent(id),
    claim ? '/claims' : '', claim ? 'POST' : 'GET', undefined,
    receipt ? { 'X-Voucher-Receipt': receipt } : {})
}

export function voucherValue(face: Face) {
  return new Intl.NumberFormat('zh-SG', {style:'currency', currency:face.currency}).format(face.valueCents / 100)
}

export function voucherRedemptionTime(value: string, timezone: string) {
  return new Intl.DateTimeFormat('zh-CN', {
    timeZone: timezone, year:'numeric', month:'2-digit', day:'2-digit',
    hour:'2-digit', minute:'2-digit', second:'2-digit', hourCycle:'h23'
  }).format(new Date(value))
}

export function voucherBenefit(face: Face) {
  return face.kind === 'cash' ? voucherValue(face) + ' 代金券'
    : face.productName + ' × ' + face.quantity + ' ' + face.unit
}
