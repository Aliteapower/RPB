import type {Product} from '../catalog/types'
export type {Product,Category} from '../catalog/types'
export interface Plan {id:string;nameZh:string;nameEn:string;kind:'stamp'|'count'|'period';productId:string;total:number|null;dailyLimit:number|null;validDays:number;stampTarget:number;repeatable:boolean;rewardDays:number;priceCents:number;taxMode:string;taxRate:string;product:Product;status:string;version:number}
export interface Holding {id:string;customerId:string;planId:string;kind:string;remaining:number|null;dailyLimit:number|null;progress:number;round:number;timezone:string;startsAt:string;expiresAt:string;status:string;version:number;snapshot:Plan;usedToday:number;available:number}
export interface CardRequest {id:string;kind:string;planId:string;cardId:string|null;customerId:string|null;quantity:number;status:string;expiresAt:string;entryId:string|null}
export interface CardEntry {id:string;cardId:string;customerId:string;kind:string;quantity:number;beforeTotal:number|null;afterTotal:number|null;businessDay:string;beforeDaily:number;afterDaily:number;source:string;originalId:string|null;reason:string;createdAt:string}
export interface Purchase {id:string;customerId:string;planId:string;cardId:string|null;amountCents:number;status:string;expiresAt:string;snapshot:Plan}
export interface QrCode {code:string;expiresAt:string}
export interface Customer {id:string;displayName:string;phone:string}
