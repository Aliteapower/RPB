export type Period = 'breakfast' | 'lunch' | 'dinner'
export interface Item { id: string; name: string; enabled: boolean }
export interface LedgerConfig { store_name: string; currency: string; base_amount: string; final_period: Period; periods: Item[]; payments: Item[]; expenses: Item[] }
export interface Branding { store_name: string; store_name_en: string; logo_data: string }
export interface Field { value: string; note: string; entered_at: string | null }
export interface Detail extends Field { id: string; category?: string; source_period?: string; source_version?: number }
export interface Basis { period: string; version: number }
export interface Totals { cash: string; noncash: string; expenses: string; withdrawn: string; revenue: string; handover: string }
export interface LedgerRecord { version: number; status: 'draft' | 'submitted'; cash: Record<string, Field>; coin: Field; payments: Record<string, Field>; expenses: Detail[]; withdrawals: Detail[]; withdrawal_basis: Basis[]; withdrawal_snapshot: Detail[]; accounting_version: number; base_amount: string; totals: Totals; saved_at?: string; submitted_at?: string; saved_by?: string; client_save_id?: string }
export interface LedgerDay { date: string; config: LedgerConfig; records: Partial<Record<Period, LedgerRecord>>; submitted_records: Partial<Record<Period, LedgerRecord>>; last_period: Period | null; locked_config: boolean }
export interface StoreDisplay { timezone: string; locale: string; dateFormat: string; timeFormat: string; currency: string }
export interface Settings { config: LedgerConfig; branding: Branding; version: number; display?: StoreDisplay }
export interface SaveRequest { version: number; client_save_id: string; action: 'draft' | 'submit'; cash: LedgerRecord['cash']; coin: Field; payments: LedgerRecord['payments']; expenses: Detail[]; withdrawals: Detail[]; config_snapshot: LedgerConfig; withdrawal_basis: Basis[] }
export interface AuditEntry { id: string; actor: string; saved_at: string; payload: LedgerRecord; action: string }
