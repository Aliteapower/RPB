<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ledgerApi } from '../api'
import { clone, denominations, field, restoreInputs } from '../model'
import { useLedgerEditor } from '../useLedgerEditor'
import type { AuditEntry, LedgerDay, LedgerRecord, Period } from '../types'
import LedgerField from './LedgerField.vue'
import LedgerCashRow from './LedgerCashRow.vue'
import LedgerSummary from './LedgerSummary.vue'
import LedgerSaveActions from './LedgerSaveActions.vue'
import LedgerModal from './LedgerModal.vue'
import LedgerIcon from './LedgerIcon.vue'
import { storageKey, readLocal, writeLocal, removeLocal } from '../storage'
import { useLedgerFormat } from '../format'
const format = useLedgerFormat()
const props = defineProps<{ storeId: string; initialDay: LedgerDay; period: Period; admin: boolean; writable: boolean; ownerKey: string }>()
const { day, record, dirty, saving, error, status, locked, summary, changed, save, recoveryBackup, reloadLatest } = useLedgerEditor(props.storeId, props.initialDay, props.period, props.admin, props.ownerKey)
const emit = defineEmits<{ saved: [day: LedgerDay] }>()
watch(day, value => emit('saved', value))
const final = computed(() => props.period === day.value.config.final_period)
const submitted = computed(() => record.value.status === 'submitted' && !dirty.value && !changedSource.value)
const undoKey = storageKey(props.ownerKey, props.storeId, props.initialDay.date, props.period) + ':clear'
const confirmSubmit = ref(false), undo = ref(readLocal<LedgerRecord>(undoKey)), audit = ref<AuditEntry[]>([]), showAudit = ref(false)
const crypto = window.crypto
const changedSource = computed(() => props.period === day.value.config.final_period && day.value.submitted_records[props.period] && JSON.stringify(day.value.submitted_records[props.period]?.withdrawal_snapshot ?? []) !== JSON.stringify(record.value.withdrawal_snapshot))
function addExpense() { const category = day.value.config.expenses.find(p => p.enabled)?.id; if (category) { record.value.expenses.push({ ...field(), id: crypto.randomUUID(), category }); changed() } }
function clearDraft() { undo.value = clone(record.value); writeLocal(undoKey, undo.value); Object.values(record.value.cash).forEach(f => { f.value = ''; f.note = ''; f.entered_at = null }); Object.values(record.value.payments).forEach(f => { f.value = ''; f.note = ''; f.entered_at = null }); record.value.coin = field(); record.value.expenses = []; if (props.period !== day.value.config.final_period) record.value.withdrawals = []; changed() }
function restoreCleared() { if (undo.value) { record.value = restoreInputs(record.value, undo.value, props.period === day.value.config.final_period); undo.value = undefined; removeLocal(undoKey); changed() } }
async function loadAudit() { try { audit.value = await ledgerApi(props.storeId).audit(day.value.date, props.period); showAudit.value = true } catch (e) { error.value = String(e) } }
async function submit() { if (await save('submit')) confirmSubmit.value = false }
defineExpose({ flush: () => save('draft') })
</script>
<template>
  <div>
    <div class="draft-tools"><span>填写自动保存草稿</span><button class="text-button" :disabled="locked || !writable || saving || confirmSubmit" @click="clearDraft">清空本班草稿</button><button class="text-button" :disabled="!undo || locked || !writable || saving || confirmSubmit" @click="restoreCleared">恢复清空前草稿</button></div>
    <p v-if="locked" class="notice">本班已提交，员工不能修改，请联系管理员。</p>
    <p v-if="changedSource" class="notice">前班抽款已变更。历史报表仍保留上次提交数，需管理员核对后重新提交。</p>
    <div v-if="error" role="alert" class="ledger-error"><p>{{ error }}。当前输入仍保留；请核对后重试或重新载入。</p><button class="button secondary compact" :disabled="saving" @click="reloadLatest">载入最新记录并保留输入备份</button></div>
    <details v-if="recoveryBackup"><summary>重新载入前的输入备份（请核对后填写）</summary><pre>{{ recoveryBackup }}</pre></details>
    <div class="record-layout">
      <div>
        <fieldset class="record-inputs" :disabled="locked || !writable || confirmSubmit" aria-label="本班盘点录入">
          <section class="panel record-panel"><div class="panel-heading"><div><div class="eyebrow">CASH COUNT</div><h2>现金盘点 <span class="tag">{{ day.config.periods.find(p => p.id === period)?.name }}</span></h2></div><span class="muted small">数量填整数，金额自动计算</span></div><p class="calculation-note">盘点钱箱内全部现金，包含底钱；已抽走的大钞不要再次清点。系统自动从营业额及现金总交款中扣除底钱一次，已付开支不再从交款扣减。</p><p v-if="record.accounting_version < 5" class="notice cash-convention-note">这份旧记录原按不含底钱填写。请将底钱一并重新盘点后再提交；已提交日报保留原结果。</p>
            <div class="cash-table"><div class="table-head cash-row"><span>现金面额</span><span>数量 / 输入时间</span><span>金额</span><span>备注</span></div><LedgerCashRow v-for="d in denominations" :key="d" :denomination="d" :model="record.cash[d]!" @changed="changed" /><LedgerCashRow :model="record.coin" @changed="changed" /></div><div class="section-total"><span>盘点现金（含底钱）</span><strong>${{ summary.cash }}</strong></div>
          </section>
          <section class="panel record-panel"><div class="panel-heading"><div><div class="eyebrow">CASHLESS PAYMENTS</div><h2>非现金收入</h2></div><span class="muted small">当日累计金额 · 保留两位小数</span></div><div class="payment-grid"><LedgerField v-for="p in day.config.payments.filter(p => p.enabled)" :key="p.id" :label="p.name" :model="record.payments[p.id]!" @changed="changed" /></div><div class="section-total"><span>非现金合计</span><strong>${{ summary.noncash }}</strong></div></section>
          <section class="panel record-panel"><div class="panel-heading"><div><div class="eyebrow">EXPENSES</div><h2>已付现金开支 <span class="tag subtle">累计</span></h2></div><button class="button secondary compact" :disabled="!day.config.expenses.some(p => p.enabled)" @click="addExpense">＋ 添加开支</button></div>
            <div v-if="!record.expenses.length" class="empty-expenses"><span>—</span><p>暂未填写开支</p><small>采购、杂费等可在这里记录</small></div>
            <div v-for="(row, i) in record.expenses" :key="row.id" class="expense-row"><div><label>开支项目</label><select v-model="row.category" :aria-label="`开支项目 ${i + 1}`" @change="changed"><option v-for="p in day.config.expenses.filter(p => p.enabled)" :key="p.id" :value="p.id">{{ p.name }}</option></select></div><LedgerField label="开支金额" :model="row" @changed="changed" /><button class="icon-button" :aria-label="`移除第${i + 1}条开支`" @click="record.expenses.splice(i, 1); changed()">×</button></div><div class="section-total"><span>开支合计</span><strong>${{ summary.expenses }}</strong></div>
          </section>
          <section class="panel record-panel"><div class="panel-heading"><div><div class="eyebrow">CASH WITHDRAWALS</div><h2>{{ final ? '前班抽大钞' : '本班抽大钞' }} <span class="tag subtle">{{ final ? '自动汇总' : '本班新增' }}</span></h2></div><button v-if="!final" class="button secondary compact" @click="record.withdrawals.push({ ...field(), id: crypto.randomUUID() }); changed()">＋ 记录抽款</button></div>
            <p class="calculation-note">{{ final ? '最终班次自动汇总前班抽款，无需重复填写。' : '只记录本班实际新增抽走的金额，不要重复填写前班抽款。已保存金额将自动带入最终统计和现金总交款。' }}</p>
            <template v-if="!final"><div v-for="(row, i) in record.withdrawals" :key="row.id" class="expense-row withdrawal-row"><LedgerField label="本班新增抽款" :model="row" @changed="changed" /><button class="icon-button" :aria-label="`移除第${i + 1}条抽款`" @click="record.withdrawals.splice(i, 1); changed()">×</button></div><p v-if="!record.withdrawals.length" class="calculation-note">尚无抽款；只填本班新增抽款，不要填当日累计数。</p></template>
            <div v-else class="skimming-sources"><div v-for="row in record.withdrawal_snapshot" :key="`${row.source_period}-${row.id}`" class="skimming-source"><div><strong>{{ day.config.periods.find(p => p.id === row.source_period)?.name ?? row.source_period }}</strong><small>{{ row.note }}</small></div><b>${{ row.value }}</b></div><p v-if="!record.withdrawal_snapshot.length" class="muted small">尚无前班抽款</p></div><div class="section-total"><span>{{ final ? '前班抽款合计' : '本班抽大钞合计' }}</span><strong>${{ summary.withdrawn }}</strong></div>
          </section>
        </fieldset>
        <div class="record-footer"><span><LedgerIcon name="lock" />填写自动存草稿 · 最终提交需确认</span><button class="text-button" @click="loadAudit">查看修改记录 ↗</button></div>
      </div>
      <LedgerSummary :summary="summary" :record="record" :base="record.base_amount" :final="final" :status="saving ? '正在同步…' : status"><LedgerSaveActions :disabled="saving || locked || !writable || confirmSubmit" :submitted="submitted" :status="status" @save="save('draft')" @submit="confirmSubmit = true" /></LedgerSummary>
    </div>
    <LedgerSaveActions mobile :disabled="saving || locked || !writable || confirmSubmit" :submitted="submitted" :status="saving ? '正在同步…' : status" @save="save('draft')" @submit="confirmSubmit = true" />
    <LedgerModal v-if="confirmSubmit" title="确认最终提交" @close="confirmSubmit = false"><p class="notice">确认提交此餐段？员工提交后不能修改。请核对盘点金额。</p><div class="summary-lines"><div><span>营业额</span><strong>${{ summary.revenue }}</strong></div><div><span>现金总交款</span><strong>${{ summary.handover }}</strong></div></div><div class="modal-actions"><button class="button secondary" :disabled="saving" @click="confirmSubmit = false">继续核对</button><button class="button primary" :disabled="saving" @click="submit">确认提交</button></div></LedgerModal>
    <LedgerModal v-if="showAudit" title="修改记录" wide @close="showAudit = false"><div class="audit-list"><p v-if="!audit.length">暂无修改记录</p><details v-for="row in audit" :key="row.id"><summary>{{ format.time(row.saved_at) }} · {{ row.actor }} · {{ row.action }}</summary><pre>{{ row.payload }}</pre></details></div></LedgerModal>
  </div>
</template>
