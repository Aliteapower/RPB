import type { PaymentCode, Bill } from './types'
export interface PersonalConsent { configured:boolean;policyVersion:number;limitCents:number|null;consentedLimitCents:number|null;paymentEnabled:boolean;requiresConsent:boolean;ruleVersion:number }
export interface PersonalCode extends Omit<PaymentCode,'type'> {type:'customer_personal';paymentEnabled:boolean;limitCents:number|null;consentedLimitCents:number|null;policyVersion:number}
export interface PersonalCodeStatus {state:'active'|'expired'|'used'|'confirmation_required';bill?:Bill|null}
