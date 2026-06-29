<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'

import {
  getPlatformReservationMealPeriodSeed,
  ReservationMealPeriodApiError,
  updatePlatformReservationMealPeriodSeed
} from '../api/reservationMealPeriodApi'
import PlatformAdminNav from '../components/platform/PlatformAdminNav.vue'
import { useAuthSessionStore } from '../stores/authSession'
import type {
  ReservationMealPeriodMutation
} from '../types/reservationMealPeriod'

const auth = useAuthSessionStore()
const loading = ref(false)
const saving = ref(false)
const errorText = ref('')
const savedText = ref('')
const form = reactive({
  periods: [] as ReservationMealPeriodMutation[]
})

onMounted(() => {
  void loadSeed()
})

async function loadSeed(): Promise<void> {
  loading.value = true
  errorText.value = ''
  savedText.value = ''
  try {
    const response = await getPlatformReservationMealPeriodSeed()
    form.periods = response.periods.map(period => ({
      periodKey: period.periodKey,
      displayName: period.displayName,
      startLocalTime: normalizeTime(period.startLocalTime),
      endLocalTime: normalizeTime(period.endLocalTime),
      crossesNextDay: period.crossesNextDay,
      slotIntervalMinutes: period.slotIntervalMinutes,
      status: period.status,
      sortOrder: period.sortOrder,
      version: period.version
    }))
  } catch (error) {
    errorText.value = apiErrorText(error)
  } finally {
    loading.value = false
  }
}

async function saveSeed(): Promise<void> {
  if (saving.value) {
    return
  }

  saving.value = true
  errorText.value = ''
  savedText.value = ''
  try {
    const response = await updatePlatformReservationMealPeriodSeed({
      periods: form.periods.map(toMutation)
    })
    form.periods = response.periods.map(period => ({
      periodKey: period.periodKey,
      displayName: period.displayName,
      startLocalTime: normalizeTime(period.startLocalTime),
      endLocalTime: normalizeTime(period.endLocalTime),
      crossesNextDay: period.crossesNextDay,
      slotIntervalMinutes: period.slotIntervalMinutes,
      status: period.status,
      sortOrder: period.sortOrder,
      version: period.version
    }))
    savedText.value = '已保存'
  } catch (error) {
    errorText.value = apiErrorText(error)
  } finally {
    saving.value = false
  }
}

function addPeriod(): void {
  const index = form.periods.length
  form.periods.push({
    periodKey: `period_${index + 1}`,
    displayName: '新餐段',
    startLocalTime: '11:00',
    endLocalTime: '15:00',
    crossesNextDay: false,
    slotIntervalMinutes: 30,
    status: 'active',
    sortOrder: (index + 1) * 10
  })
}

function removePeriod(index: number): void {
  form.periods.splice(index, 1)
}

function toMutation(period: ReservationMealPeriodMutation): ReservationMealPeriodMutation {
  return {
    periodKey: period.periodKey.trim(),
    displayName: period.displayName.trim(),
    startLocalTime: normalizeTime(period.startLocalTime),
    endLocalTime: normalizeTime(period.endLocalTime),
    crossesNextDay: Boolean(period.crossesNextDay),
    slotIntervalMinutes: Number(period.slotIntervalMinutes),
    status: period.status,
    sortOrder: Number(period.sortOrder),
    version: period.version
  }
}

function apiErrorText(error: unknown): string {
  if (!(error instanceof ReservationMealPeriodApiError)) {
    return '操作失败'
  }
  if (error.status === 401) {
    auth.clear()
    return '登录已失效'
  }
  if (error.response.error.code === 'FORBIDDEN') {
    return '没有平台后台权限'
  }
  if (error.response.error.code === 'REQUEST_INVALID') {
    return '请检查餐段名称、时间和间隔'
  }
  return '操作失败'
}

function normalizeTime(value: string): string {
  return value ? value.slice(0, 5) : ''
}
</script>

<template>
  <main class="platform-shell">
    <PlatformAdminNav />

    <section class="platform-workspace">
      <header class="page-heading">
        <div>
          <span>平台种子配置</span>
          <h1>预约餐段种子</h1>
        </div>
        <button class="primary-button" type="button" :disabled="saving || loading" @click="saveSeed">
          {{ saving ? '保存中' : '保存' }}
        </button>
      </header>

      <p v-if="errorText" class="error-banner" role="alert">{{ errorText }}</p>
      <p v-if="savedText" class="success-banner" role="status">{{ savedText }}</p>
      <p v-if="loading" class="loading-line">加载中</p>

      <form v-else class="period-editor" aria-label="平台预约餐段种子" @submit.prevent="saveSeed">
        <div class="editor-toolbar">
          <h2>平台餐段</h2>
          <button class="secondary-button" type="button" @click="addPeriod">新增餐段</button>
        </div>

        <div class="period-grid" role="table" aria-label="平台餐段列表">
          <div class="period-grid__head" role="row">
            <span>键</span>
            <span>名称</span>
            <span>开始</span>
            <span>结束</span>
            <span>跨日</span>
            <span>间隔</span>
            <span>状态</span>
            <span>排序</span>
            <span></span>
          </div>

          <div
            v-for="(period, index) in form.periods"
            :key="`${period.periodKey}-${index}`"
            class="period-grid__row"
            role="row"
          >
            <input v-model.trim="period.periodKey" required maxlength="64" autocomplete="off" />
            <input v-model.trim="period.displayName" required maxlength="80" autocomplete="off" />
            <input v-model="period.startLocalTime" required type="time" />
            <input v-model="period.endLocalTime" required type="time" />
            <label class="switch-cell">
              <input v-model="period.crossesNextDay" type="checkbox" />
              <span>{{ period.crossesNextDay ? '是' : '否' }}</span>
            </label>
            <input v-model.number="period.slotIntervalMinutes" required min="5" max="240" step="5" type="number" />
            <select v-model="period.status">
              <option value="active">启用</option>
              <option value="disabled">停用</option>
            </select>
            <input v-model.number="period.sortOrder" required type="number" />
            <button class="link-button" type="button" @click="removePeriod(index)">删除</button>
          </div>
        </div>
      </form>
    </section>
  </main>
</template>

<style scoped>
.platform-shell {
  min-height: 100dvh;
  display: grid;
  grid-template-columns: 220px minmax(0, 1fr);
  background: #f3f6f8;
  color: #102033;
}

.platform-workspace {
  min-width: 0;
  padding: 22px;
}

.page-heading,
.editor-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.page-heading {
  margin-bottom: 16px;
}

.page-heading span {
  color: #64748b;
  font-size: 13px;
  font-weight: 700;
}

.page-heading h1,
.editor-toolbar h2 {
  margin: 0;
  color: #0f172a;
}

.page-heading h1 {
  font-size: 24px;
}

.editor-toolbar h2 {
  font-size: 17px;
}

.error-banner,
.success-banner,
.loading-line {
  margin: 0 0 12px;
  padding: 10px 12px;
  border-radius: 6px;
}

.error-banner {
  border: 1px solid #fecaca;
  color: #991b1b;
  background: #fff1f2;
}

.success-banner {
  border: 1px solid #bbf7d0;
  color: #166534;
  background: #f0fdf4;
}

.loading-line {
  border: 1px solid #dbe3ea;
  color: #475569;
  background: #ffffff;
}

.period-editor {
  display: grid;
  gap: 14px;
  padding: 18px;
  border: 1px solid #dbe3ea;
  border-radius: 8px;
  background: #ffffff;
}

.period-grid {
  display: grid;
  gap: 8px;
  overflow-x: auto;
}

.period-grid__head,
.period-grid__row {
  display: grid;
  grid-template-columns: 1.15fr 1.15fr 112px 112px 78px 92px 96px 78px 70px;
  gap: 8px;
  min-width: 940px;
  align-items: center;
}

.period-grid__head {
  color: #64748b;
  font-size: 12px;
  font-weight: 800;
}

input,
select {
  width: 100%;
  min-height: 38px;
  box-sizing: border-box;
  border: 1px solid #cbd5e1;
  border-radius: 6px;
  padding: 8px 9px;
  color: #0f172a;
  background: #ffffff;
  font: inherit;
}

.switch-cell {
  min-height: 38px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  border: 1px solid #cbd5e1;
  border-radius: 6px;
  color: #334155;
  font-size: 13px;
  font-weight: 800;
}

.switch-cell input {
  width: auto;
  min-height: auto;
}

.primary-button,
.secondary-button,
.link-button {
  min-height: 38px;
  border-radius: 6px;
  font: inherit;
  font-weight: 800;
  cursor: pointer;
}

.primary-button {
  border: 0;
  padding: 0 16px;
  color: #ffffff;
  background: #0f766e;
}

.secondary-button {
  border: 1px solid #cbd5e1;
  padding: 0 12px;
  color: #334155;
  background: #ffffff;
}

.link-button {
  border: 0;
  padding: 0;
  color: #be123c;
  background: transparent;
}

button:disabled {
  opacity: 0.6;
  cursor: default;
}

@media (max-width: 980px) {
  .platform-shell {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 700px) {
  .platform-workspace {
    padding: 14px;
  }

  .page-heading {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
