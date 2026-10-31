export type TransactionSource = 'bill'|'topup'|'refund'|'points'|'money_reversal'
export interface Transaction {
 id:string; sourceType:TransactionSource; type:string; status:string; occurredAt:string; createdAt:string; postedAt:string|null;
 storeId:string; storeName:string; timeZone:string; currency:string; amountCents:number; quantity:number; direction:'in'|'out'|'none'; walletDeltaCents:number;
 principalCents:number; bonusCents:number; creditTotalCents:number; walletPaidCents:number; externalPaidCents:number; refundStatus:string;
 customerId:string|null; customerName:string|null; customerPhone:string|null; summary:string|null; actorName:string|null; originalSourceType:TransactionSource|null; originalId:string|null
}
export interface Capabilities {money:boolean;points:boolean;refunds:boolean;reversals:boolean}
export interface TransactionPage {items:Transaction[];nextCursor:string|null;totalCount:number;summary:Record<string,number>;stores:{id:string;name:string;timeZone:string}[];period:{mode:string;startDate:string;endDate:string;timeZone:string};capabilities:Capabilities;category:string}
export interface TransactionDetail {transaction:Transaction;related:Transaction[];events:{type?:string;kind?:string;occurredAt?:string;at?:string;summary?:string;label?:string}[]}
