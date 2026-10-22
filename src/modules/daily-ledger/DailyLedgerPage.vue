<script setup lang="ts">
import { computed, provide, ref, watch } from 'vue'
import { onBeforeRouteLeave, onBeforeRouteUpdate, useRoute } from 'vue-router'
import LedgerShell from './components/LedgerShell.vue'
import LedgerOverview from './components/LedgerOverview.vue'
import LedgerSubmittedPayments from './components/LedgerSubmittedPayments.vue'
import { useAuthSessionStore } from '../../stores/authSession'
import { ledgerApi } from './api'
import { businessDate } from './model'
import type { LedgerDay, Period, Settings } from './types'
import LedgerRecordEditor from './components/LedgerRecordEditor.vue'
import LedgerHistory from './components/LedgerHistory.vue'
import LedgerSettings from './components/LedgerSettings.vue'
import './ledger.css'
import { defaultDisplay, displayKey } from './format'
const route = useRoute(), auth = useAuthSessionStore()
const storeId = computed(() => String(route.params.storeId))
const settings = ref<Settings>(), day = ref<LedgerDay>(), date = ref(businessDate()), period = ref<Period>('dinner')
const tab = ref<'record' | 'history' | 'settings'>('record'), loading = ref(false), error = ref('')
const editor = ref<InstanceType<typeof LedgerRecordEditor>>()
const admin = computed(() => auth.user?.permissions.includes('daily_ledger.settings.manage') === true)
const writable = computed(() => auth.user?.permissions.includes('daily_ledger.write') === true)
const timezone = ref('Asia/Singapore')
provide(displayKey, computed(() => settings.value?.display ?? defaultDisplay))
let selectedDate = false
let generation = 0
async function flush() { return await editor.value?.flush() ?? true }
async function load() {
  const current = ++generation, targetStore = storeId.value, targetDate = date.value
  loading.value = true; error.value = ''; day.value = undefined
  try {
    await auth.ensureCurrentUser()
    const config = await ledgerApi(targetStore).settings()
    const currentDate = selectedDate ? targetDate : businessDate(config.display?.timezone ?? defaultDisplay.timezone)
    const result = await ledgerApi(targetStore).day(currentDate)
    if (current !== generation) return
    settings.value = config; day.value = result; date.value = currentDate; timezone.value = config.display?.timezone ?? defaultDisplay.timezone
    if (!result.config.periods.some(p => p.id === period.value && p.enabled)) period.value = result.config.final_period
  } catch (e) { if (current === generation) error.value = e instanceof Error ? e.message : '读取失败' }
  finally { if (current === generation) loading.value = false }
}
async function changeDate(value: string) { if (await flush()) { selectedDate = true; date.value = value; await load() } }
async function changePeriod(value: Period) { if (await flush()) { period.value = value; await load() } }
function offsetDate(offset: number) { const value = new Date(date.value + 'T12:00:00Z'); value.setUTCDate(value.getUTCDate() + offset); void changeDate(value.toISOString().slice(0, 10)) }
async function changeTab(value: typeof tab.value) { if (await flush()) { tab.value = value; if (value === 'record') await load() } }
onBeforeRouteLeave(flush); onBeforeRouteUpdate(flush)
watch(storeId, () => { selectedDate = false; settings.value = undefined; tab.value = 'record'; void load() }, { immediate: true })
</script>
<template>
  <div class="daily-ledger">
    <LedgerShell :branding="settings?.branding" :store-id="storeId" :tab="tab" :admin="admin" :currency="settings?.config.currency" @navigate="changeTab">
      <div v-if="error" role="alert" class="ledger-error">{{ error }} <button class="button secondary" @click="load">重新载入</button></div>
      <p v-if="loading" class="loading">正在读取门店账本…</p>
      <template v-if="!loading && day && settings">
        <section v-if="tab !== 'history'">
          <div class="page-heading"><div><div class="eyebrow">DAILY BUSINESS RECORD</div><h1>每日营业记录<span class="heading-dot">.</span></h1><p>把今天的每一笔，记清楚。</p></div><div class="date-picker"><button class="icon-button" aria-label="前一天" @click="offsetDate(-1)">‹</button><input :value="date" aria-label="营业日期" type="date" @change="changeDate(($event.target as HTMLInputElement).value)" /><button class="icon-button" aria-label="后一天" @click="offsetDate(1)">›</button><button class="text-button today-button" @click="changeDate(businessDate(timezone))">今天</button></div></div>
          <LedgerOverview :submitted="day.submitted_records[day.config.final_period]" :base="day.config.base_amount" />
          <LedgerSubmittedPayments :record="day.submitted_records[period]" :config="day.config" :period="period" />
          <div class="record-toolbar"><div class="period-tabs" role="tablist" aria-label="盘点时段"><button v-for="p in day.config.periods.filter(p => p.enabled)" :key="p.id" class="period-tab" :class="{ selected: period === p.id }" role="tab" :aria-selected="period === p.id" @click="changePeriod(p.id as Period)"><span>{{ p.id === 'breakfast' ? '◔' : p.id === 'lunch' ? '☀' : '☾' }}</span>{{ p.name }}<i v-if="day.records[p.id as Period]" :class="day.records[p.id as Period]?.status === 'submitted' ? 'tab-saved' : 'tab-draft'">{{ day.records[p.id as Period]?.status === 'submitted' ? '✓' : '草稿' }}</i></button></div><span class="toolbar-date">{{ new Intl.DateTimeFormat('zh-CN', { month: 'long', day: 'numeric', weekday: 'long', timeZone: 'UTC' }).format(new Date(date + 'T12:00:00Z')) }}<span class="tag subtle">累计盘点</span></span></div>
          <LedgerRecordEditor :key="`${storeId}-${date}-${period}`" ref="editor" :store-id="storeId" :initial-day="day" :period="period" :admin="admin" :writable="writable" :owner-key="`${auth.user?.tenantId}/${auth.user?.accountId}`" @saved="value => day = value" />
        </section>
        <LedgerHistory v-else :key="storeId" :store-id="storeId" :timezone="timezone" @open="async value => { tab = 'record'; await changeDate(value) }" />
        <LedgerSettings v-if="tab === 'settings'" :key="storeId" :store-id="storeId" :initial="settings" @saved="value => settings = value" @close="tab = 'record'" />
      </template>
    </LedgerShell>
  </div>
</template>
