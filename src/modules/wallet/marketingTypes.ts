import type { Channel } from './types'
export type DeductionPolicy = 'proportional' | 'principal_first'
export interface TopupOffer { id: string; version: number; principalCents: number; bonusCents: number; creditTotalCents: number; enabled?: boolean }
export interface OfferSet { offers: TopupOffer[]; campaignId: string | null; campaignVersion?: number; channels: Channel[]; deductionPolicy: DeductionPolicy; selfServiceEnabled?: boolean; pointsEnabled?: boolean }
export interface TopupQuote { quoteId: string; principalCents: number; bonusCents: number; creditTotalCents: number; channel: Channel; expiresAt: string }
export interface MarketingSettings { baseOffers: TopupOffer[]; deductionPolicy: DeductionPolicy; version: number; selfServiceEnabled: boolean; campaignsEnabled?: boolean; pointsEnabled: boolean; affectedStoreIds: string[] }
export interface Campaign { id: string; name: string; status: string; version: number; draftVersion: number; currentPublishedRevision?: number; startsAt: string; endsAt: string; priority: number; offers: TopupOffer[]; terms?: string }
export interface PointsAccount { accountId: string | null; balance: number; version: number; programId?: string; status?: string }
export interface PointsEntry { id: string; kind: string; quantity: number; beforeBalance: number; afterBalance: number; reason: string; note?: string; redemptionDescription?: string; externalReference?: string; createdAt: string; reversed?: boolean; reversalOf?: string; originalEntryId?: string }
export interface PointsResult { account: PointsAccount; entry: PointsEntry }
export function rows<T>(value: T[] | { items?: T[]; entries?: T[]; campaigns?: T[] }): T[] { return Array.isArray(value) ? value : value.items ?? value.entries ?? value.campaigns ?? [] }
export function normalizeOffers(value: OfferSet): OfferSet { return { ...value, offers: (value.offers ?? []).map(offer => ({ ...offer, id: offer.id ?? (offer as unknown as {offerId:string}).offerId, version: offer.version ?? (offer as unknown as {offerVersion:number}).offerVersion })) } }

export interface RefundPreview { amountCents:number;walletCents:number;cashPrincipalRefundCents:number;principalReturnCents:number;bonusReturnCents:number;bonusRevokeCents:number;accountId:string;accountVersion:number;mode:string }
export interface FundingLot { id:string;accountId:string;principal:number;bonus:number;reservedP:number;reservedG:number;version:number;policy:string;legacy:boolean }
