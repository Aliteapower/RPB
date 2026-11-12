import { computed, onBeforeUnmount, ref } from 'vue'
import { ledgerApi, LedgerApiError } from './api'
import { basis, blankRecord, clone, totals, withdrawalSnapshot } from './model'
import type { LedgerDay, Period, SaveRequest } from './types'
import { readLocal, removeLocal, storageKey, writeLocal, type LedgerOutbox } from './storage'

export function useLedgerEditor(storeId: string, initialDay: LedgerDay, period: Period, admin: boolean, ownerKey: string) {
  const api = ledgerApi(storeId)
  const day = ref(clone(initialDay))
  const record = ref(clone(initialDay.records[period] ?? blankRecord(initialDay.config)))
  record.value.withdrawal_snapshot = withdrawalSnapshot(initialDay, period)
  const dirty = ref(false), saving = ref(false), error = ref(''), status = ref('')
  const localKey = storageKey(ownerKey, storeId, initialDay.date, period)
  const recoveryBackup = ref(readLocal<ReturnType<typeof blankRecord>>(`${localKey}:recovery`))
  const locked = computed(() => !admin && !!day.value.submitted_records[period])
  const summary = computed(() => totals(record.value))
  let revision = 0, pendingRevision = 0, timer: ReturnType<typeof setTimeout> | undefined
  let pending: SaveRequest | undefined, operation: Promise<boolean> | undefined
  const cached = readLocal<LedgerOutbox>(localKey)
  if (cached?.dirty && cached.record?.cash && cached.config) {
    const sameVersion = cached.record.version === record.value.version
    const acknowledgedPending = !!cached.pending && cached.pending.client_save_id === record.value.client_save_id
    const sameSources = JSON.stringify(cached.record.withdrawal_snapshot ?? []) === JSON.stringify(withdrawalSnapshot(initialDay, period))
    if (!locked.value && sameSources && JSON.stringify(cached.config) === JSON.stringify(initialDay.config) && (sameVersion || acknowledgedPending)) {
      record.value = clone(cached.record); pending = cached.pending; revision = cached.revision; pendingRevision = cached.pendingRevision; dirty.value = true
      status.value = '已恢复本机未同步草稿，请核对后立即同步'
    } else { recoveryBackup.value = clone(cached.record); writeLocal(`${localKey}:recovery`, cached.record); error.value = '本机草稿与云端版本不同或本班已锁定，请核对下方输入备份' }
  }
  function persist() {
    if (!dirty.value && !pending) { removeLocal(localKey); return }
    if (!writeLocal(localKey, { record: record.value, config: day.value.config, pending, revision, pendingRevision, dirty: dirty.value } satisfies LedgerOutbox)) status.value = '本机备份不可用，请保持页面打开并立即同步'
  }
  function changed() { dirty.value = true; revision++; status.value = '尚未同步'; persist(); clearTimeout(timer); if (!error.value) timer = setTimeout(() => { void save('draft') }, 900) }
  async function perform(action: 'draft' | 'submit'): Promise<boolean> {
    if (locked.value) return !dirty.value
    if (!dirty.value && action === 'draft' && !pending) return true
    clearTimeout(timer); saving.value = true; error.value = ''
    // Retain the exact request on network failure, so retry cannot double-submit.
    if (!pending) { pendingRevision = revision; pending = clone({ version: record.value.version, client_save_id: crypto.randomUUID(), action,
      cash: record.value.cash, coin: record.value.coin, payments: record.value.payments, expenses: record.value.expenses,
      withdrawals: record.value.withdrawals, config_snapshot: day.value.config, withdrawal_basis: basis(day.value, period) }) }
    const savedRevision = pendingRevision
    persist()
    const request = pending
    try {
      const updated = await api.save(initialDay.date, period, request)
      const saved = updated.records[period]!
      day.value = updated; record.value.version = saved.version; record.value.saved_at = saved.saved_at; record.value.saved_by = saved.saved_by
      record.value.withdrawal_snapshot = saved.withdrawal_snapshot
      if (savedRevision === revision) { record.value = clone(saved); dirty.value = false }
      pending = undefined
      persist()
      status.value = request.action === 'submit' ? '已提交成功' : '草稿已自动保存云端'
      if (dirty.value) timer = setTimeout(() => { void save('draft') }, 900)
      return true
    } catch (e) {
      if (e instanceof LedgerApiError && e.status >= 400 && e.status < 500) pending = undefined
      persist()
      error.value = e instanceof Error ? e.message : '保存失败，输入仍保留'; return false
    }
    finally { saving.value = false }
  }
  async function save(action: 'draft' | 'submit'): Promise<boolean> {
    if (operation) { if (!await operation) return false; return save(action) }
    const pendingAction = pending?.action
    operation = perform(action)
    const result = await operation; operation = undefined
    if (result && (dirty.value || (action === 'submit' && pendingAction === 'draft'))) return save(action)
    return result
  }
  function beforeUnload(event: BeforeUnloadEvent) { if (dirty.value || saving.value) { event.preventDefault(); event.returnValue = '' } }
  async function reloadLatest() {
    if (operation) await operation
    clearTimeout(timer)
    try {
      const latest = await api.day(initialDay.date)
      recoveryBackup.value = clone(record.value)
      writeLocal(`${localKey}:recovery`, recoveryBackup.value)
      day.value = latest; record.value = clone(latest.records[period] ?? blankRecord(latest.config))
      record.value.withdrawal_snapshot = withdrawalSnapshot(latest, period)
      pending = undefined; dirty.value = false; error.value = ''; status.value = '已载入最新记录；原输入保留在下方备份中，请核对后再填写'
      persist()
      return true
    } catch (e) { error.value = e instanceof Error ? e.message : '重新载入失败'; return false }
  }
  window.addEventListener('beforeunload', beforeUnload)
  onBeforeUnmount(() => { clearTimeout(timer); window.removeEventListener('beforeunload', beforeUnload) })
  return { day, record, dirty, saving, error, status, locked, summary, changed, save, recoveryBackup, reloadLatest }
}
