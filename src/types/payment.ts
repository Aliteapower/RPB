export type PaymentMethod = 'paynow'
export type PaymentProfileStatus = 'active' | 'disabled'
export type PayNowType = 'mobile' | 'uen'
export type PaymentIntentSourceType = 'quick_pay' | 'reservation' | 'queue' | 'pos'
export type PaymentBusinessDayStatus = 'open' | 'closed' | 'not_open'

export interface PaymentProfile {
  id: string
  tenantId: string
  storeId: string
  method: PaymentMethod
  status: PaymentProfileStatus
  paynowType: PayNowType
  paynowMobile: string | null
  paynowUen: string | null
  merchantName: string
  currency: string
  configJson: string
  version: number
  createdAt: string
  updatedAt: string
}

export interface PaymentProfileMutation {
  method: PaymentMethod
  status: PaymentProfileStatus
  paynowType: PayNowType
  paynowMobile?: string | null
  paynowUen?: string | null
  merchantName: string
  currency: string
  configJson: string
  version?: number | null
}

export interface PaymentIntent {
  id: string
  tenantId: string
  storeId: string
  intentNo: string
  sourceType: PaymentIntentSourceType
  sourceId: string | null
  method: PaymentMethod
  amount: string
  currency: string
  paymentReference: string
  status: string
  terminalCode: string | null
  cashierName: string | null
  metadataJson: string
  version: number
  createdAt: string
  updatedAt: string
}

export interface PaymentSession {
  id: string
  tenantId: string
  storeId: string
  intentId: string
  sessionNo: string
  displayNumber: number
  businessDate: string
  status: string
  qrPayloadsJson: string
  expiresAt: string
  version: number
}

export interface PaymentProfileResponse {
  success: true
  profile: PaymentProfile
}

export interface PaymentProfileTestQr {
  method: PaymentMethod
  amount: string
  currency: string
  paymentReference: string
  qrPayload: string
}

export interface PaymentProfileTestQrResponse {
  success: true
  testQr: PaymentProfileTestQr
}

export interface PaymentIntentCreateRequest {
  idempotencyKey: string
  sourceType: PaymentIntentSourceType
  sourceId?: string | null
  method: PaymentMethod
  amount: string
  currency: string
  terminalCode?: string | null
  cashierName?: string | null
  requestedDisplayNumber?: number | null
  metadataJson?: string | null
}

export interface PaymentIntentCreateResponse {
  success: true
  replayed: boolean
  intent: PaymentIntent
  session: PaymentSession
  nextDisplayNumber: number
}

export interface PaymentManualConfirmRequest {
  idempotencyKey: string
  terminalCode?: string | null
}

export interface PaymentManualConfirmResponse {
  success: true
  replayed: boolean
  alreadyConfirmed: boolean
  intent: PaymentIntent
  session: PaymentSession
}

export interface PaymentBusinessDayResponse {
  success: true
  businessDate: string
  status: PaymentBusinessDayStatus
  openedAt: string | null
  closedAt: string | null
}

export interface QuickPayTerminalConfig {
  referencePrefix: string
  dailyStartNumber: number
  presetAmounts: string[]
}

export interface QuickPayTerminalConfigResponse {
  success: true
  terminalConfig: QuickPayTerminalConfig
}

export interface QuickPayRecord {
  intentId: string
  sessionId: string
  intentNo: string
  sessionNo: string
  displayNumber: number
  businessDate: string
  amount: string
  currency: string
  paymentReference: string
  intentStatus: string
  sessionStatus: string
  terminalCode: string | null
  cashierName: string | null
  createdAt: string
  expiresAt: string
}

export interface QuickPayRecordSummary {
  count: number
  pendingCount: number
  paidCount: number
  totalAmount: string
  paidAmount: string
  currency: string
}

export interface QuickPayRecordsQuery {
  businessDate?: string
  status?: string
  terminalCode?: string
  q?: string
  limit?: number
}

export interface QuickPayRecordsResponse {
  success: true
  records: QuickPayRecord[]
  summary: QuickPayRecordSummary
}

export interface PaymentProofCandidate {
  intentId: string
  sessionId: string
  intentNo: string
  sessionNo: string
  displayNumber: number
  businessDate: string
  amount: string
  currency: string
  paymentReference: string
  intentStatus: string
  sessionStatus: string
  terminalCode: string | null
  cashierName: string | null
  createdAt: string
  expiresAt: string
}

export interface PaymentProofCandidatesQuery {
  businessDate?: string
  terminalCode?: string
  limit?: number
}

export interface PaymentProofCandidatesResponse {
  success: true
  businessDate: string | null
  candidates: PaymentProofCandidate[]
}

export interface PaymentProofOcrResult {
  extractedReference: string | null
  extractedAmount: string | null
  extractedPaidAt: string | null
  bankCode: string | null
  successDetected: boolean
  confidence: string | null
}

export interface PaymentProofChecks {
  reference: string
  amount: string
}

export interface PaymentProofScanResponse {
  success: true
  outcome: 'auto_confirmed' | 'already_confirmed' | 'needs_review' | 'no_match'
  replayed: boolean
  intentId: string | null
  sessionId: string | null
  proofId: string | null
  verificationId: string | null
  paymentReference: string | null
  expectedAmount: string | null
  ocr: PaymentProofOcrResult | null
  checks: PaymentProofChecks | null
}

export interface PaymentProofScanRequest {
  image: File
  idempotencyKey: string
  businessDate?: string
  terminalCode?: string
}

export interface PaymentProofTemplate {
  id: string
  tenantId: string | null
  bankCode: string
  bankName: string
  locale: string
  templateName: string
  source: 'platform_seed' | 'tenant_custom' | 'tenant_override'
  status: 'draft' | 'active' | 'inactive'
  priority: number
  version: number
  layoutJson: string
  createdAt: string
  updatedAt: string
}

export interface PaymentProofTemplateMutation {
  bankCode: string
  bankName: string
  locale: string
  templateName: string
  status: 'draft' | 'active' | 'inactive'
  priority: number
  layoutJson: string
  version?: number | null
}

export interface PaymentProofTemplatesResponse {
  success: true
  templates: PaymentProofTemplate[]
}

export interface PaymentProofTemplateResponse {
  success: true
  template: PaymentProofTemplate
}

export interface PaymentProofTemplateTestScanResponse {
  success: true
  template: PaymentProofTemplate | null
  ocr: {
    extractedReference: string | null
    extractedAmount: string | null
    bankCode: string | null
    successDetected: boolean
    confidence: string | null
    rawText: string | null
  } | null
}

export interface PaymentProofTemplateContribution {
  id: string
  tenantId: string
  storeId: string | null
  sourceTemplateId: string | null
  platformTemplateId: string | null
  bankCode: string
  bankName: string
  locale: string
  templateName: string
  layoutJson: string
  sampleFileName: string | null
  sampleContentType: string | null
  sampleFileDigest: string | null
  sampleRawText: string | null
  sampleOcrReference: string | null
  sampleOcrAmount: string | null
  status: 'submitted' | 'accepted' | 'rejected' | 'withdrawn'
  reviewNote: string | null
  version: number
  createdAt: string
  updatedAt: string
  reviewedAt: string | null
}

export interface PaymentProofTemplateContributionMutation {
  sourceTemplateId?: string | null
  bankCode: string
  bankName: string
  locale: string
  templateName: string
  layoutJson: string
  sampleFileName?: string | null
  sampleContentType?: string | null
  sampleFileDigest?: string | null
  sampleRawText?: string | null
  sampleOcrReference?: string | null
  sampleOcrAmount?: string | null
}

export interface PaymentProofTemplateContributionReviewRequest {
  platformTemplateId?: string | null
  targetTemplateVersion?: number | null
  reviewNote?: string | null
  version: number
}

export interface PaymentProofTemplateContributionsResponse {
  success: true
  contributions: PaymentProofTemplateContribution[]
}

export type PaymentProofTemplateContributionResponse = PaymentProofTemplateContribution

export interface PaymentProofTemplateRuleSuggestionResponse {
  success: true
  bankCode: string
  bankName: string
  locale: string
  templateName: string
  suggestedLayoutJson: string
  ocr: PaymentProofTemplateTestScanResponse['ocr']
}

export interface PaymentApiErrorResponse {
  success: false
  error: {
    code: string
    messageKey: string
    details: Record<string, unknown>
  }
}
