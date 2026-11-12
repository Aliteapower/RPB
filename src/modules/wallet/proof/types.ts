export interface ProofContext {sourceType:'topup'|'bill';sourceId:string;channel:string;systemReference:string;amountCents:number;expectedAccount:string;merchantName:string}
export interface WalletProof {id:string;context:ProofContext;bankReference:string|null;extracted:Record<string,unknown>;checks:Record<string,'match'|'missing'|'mismatch'>;confirmation:Record<string,unknown>;imageUrl:string}
