<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'

import {
  getSettings,
  TenantAdminApiError,
  updateSettings,
  type TenantAdminSettings
} from '../api/tenantAdminApi'
import {
  getStoreReservationMealPeriods,
  ReservationMealPeriodApiError,
  updateStoreReservationMealPeriods
} from '../api/reservationMealPeriodApi'
import TenantAdminNav from '../components/tenant-admin/TenantAdminNav.vue'
import { useAuthSessionStore } from '../stores/authSession'
import type {
  ReservationMealPeriod,
  ReservationMealPeriodMutation
} from '../types/reservationMealPeriod'
import { useGeneratedText } from '../i18n/generatedText'

const { gt } = useGeneratedText()

const route = useRoute()
const auth = useAuthSessionStore()
const loading = ref(false)
const saving = ref(false)
const savingMealPeriods = ref(false)
const errorText = ref('')
const savedText = ref('')
const platformPeriods = ref<ReservationMealPeriodMutation[]>([])

const storeId = computed(() => String(route.params.storeId || ''))

const form = reactive<TenantAdminSettings>({
  storeName: '',
  timezone: 'Asia/Shanghai',
  locale: 'zh-CN',
  dateFormat: 'DD-MM-YYYY',
  timeFormat: 'HH:mm',
  currency: 'CNY',
  reservationHoldMinutes: 15,
  queueCallHoldMinutes: 3,
  expectedDiningMinutes: 90
})

const mealPeriodForm = reactive({
  usePlatformSeed: true,
  periods: [] as ReservationMealPeriodMutation[]
})

onMounted(() => {
  void loadSettings()
})

async function loadSettings(): Promise<void> {
  loading.value = true
  errorText.value = ''
  savedText.value = ''
  try {
    const [settingsResponse, mealPeriodResponse] = await Promise.all([
      getSettings(storeId.value),
      getStoreReservationMealPeriods(storeId.value)
    ])
    Object.assign(form, settingsResponse.settings)
    applyMealPeriodSettings(mealPeriodResponse.usePlatformSeed, mealPeriodResponse.platformPeriods, mealPeriodResponse.storePeriods)
  } catch (error) {
    errorText.value = apiErrorText(error)
  } finally {
    loading.value = false
  }
}

async function submitMealPeriods(): Promise<void> {
  if (savingMealPeriods.value) {
    return
  }

  savingMealPeriods.value = true
  errorText.value = ''
  savedText.value = ''
  try {
    const response = await updateStoreReservationMealPeriods(storeId.value, {
      usePlatformSeed: mealPeriodForm.usePlatformSeed,
      periods: mealPeriodForm.usePlatformSeed ? [] : mealPeriodForm.periods.map(toMutation)
    })
    applyMealPeriodSettings(response.usePlatformSeed, response.platformPeriods, response.storePeriods)
    savedText.value = gt('generated.tenant-admin-settings.038')
  } catch (error) {
    errorText.value = apiErrorText(error)
  } finally {
    savingMealPeriods.value = false
  }
}

function usePlatformSeed(): void {
  mealPeriodForm.usePlatformSeed = true
  mealPeriodForm.periods = platformPeriods.value.map(clonePeriod)
}

function copyPlatformSeed(): void {
  mealPeriodForm.usePlatformSeed = false
  mealPeriodForm.periods = platformPeriods.value.map(clonePeriod)
}

function addPeriod(): void {
  const index = mealPeriodForm.periods.length
  mealPeriodForm.usePlatformSeed = false
  mealPeriodForm.periods.push({
    periodKey: `period_${index + 1}`,
    displayName: gt('generated.tenant-admin-settings.039'),
    startLocalTime: '11:00',
    endLocalTime: '15:00',
    crossesNextDay: false,
    slotIntervalMinutes: 30,
    status: 'active',
    sortOrder: (index + 1) * 10
  })
}

function removePeriod(index: number): void {
  mealPeriodForm.usePlatformSeed = false
  mealPeriodForm.periods.splice(index, 1)
}

function applyMealPeriodSettings(
  usePlatformSeedValue: boolean,
  nextPlatformPeriods: ReservationMealPeriod[],
  nextStorePeriods: ReservationMealPeriod[]
): void {
  platformPeriods.value = nextPlatformPeriods.map(toEditablePeriod)
  mealPeriodForm.usePlatformSeed = usePlatformSeedValue
  mealPeriodForm.periods = (usePlatformSeedValue ? nextPlatformPeriods : nextStorePeriods).map(toEditablePeriod)
}

function toEditablePeriod(period: ReservationMealPeriod): ReservationMealPeriodMutation {
  return {
    periodKey: period.periodKey,
    displayName: period.displayName,
    startLocalTime: normalizeTime(period.startLocalTime),
    endLocalTime: normalizeTime(period.endLocalTime),
    crossesNextDay: period.crossesNextDay,
    slotIntervalMinutes: period.slotIntervalMinutes,
    status: period.status,
    sortOrder: period.sortOrder,
    version: period.version
  }
}

function clonePeriod(period: ReservationMealPeriodMutation): ReservationMealPeriodMutation {
  return { ...period, startLocalTime: normalizeTime(period.startLocalTime), endLocalTime: normalizeTime(period.endLocalTime) }
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

async function submitSettings(): Promise<void> {
  if (saving.value) {
    return
  }

  saving.value = true
  errorText.value = ''
  savedText.value = ''
  try {
    const response = await updateSettings(storeId.value, {
      storeName: form.storeName.trim(),
      timezone: form.timezone.trim(),
      locale: form.locale.trim(),
      dateFormat: form.dateFormat.trim(),
      timeFormat: form.timeFormat.trim(),
      currency: form.currency.trim(),
      reservationHoldMinutes: Number(form.reservationHoldMinutes),
      queueCallHoldMinutes: Number(form.queueCallHoldMinutes),
      expectedDiningMinutes: Number(form.expectedDiningMinutes)
    })
    Object.assign(form, response.settings)
    savedText.value = gt('generated.tenant-admin-settings.040')
  } catch (error) {
    errorText.value = apiErrorText(error)
  } finally {
    saving.value = false
  }
}

function apiErrorText(error: unknown): string {
  if (error instanceof ReservationMealPeriodApiError) {
    if (error.status === 401) {
      auth.clear()
      return gt('generated.tenant-admin-settings.041')
    }
    if (error.response.error.code === 'REQUEST_INVALID') {
      return gt('generated.tenant-admin-settings.042')
    }
    if (error.response.error.code === 'FORBIDDEN') {
      return gt('generated.tenant-admin-settings.043')
    }
    return gt('generated.tenant-admin-settings.044')
  }
  if (!(error instanceof TenantAdminApiError)) {
    return gt('generated.tenant-admin-settings.045')
  }
  if (error.status === 401) {
    auth.clear()
    return gt('generated.tenant-admin-settings.046')
  }
  if (error.response.error.code === 'REQUEST_INVALID') {
    return gt('generated.tenant-admin-settings.047')
  }
  if (error.response.error.code === 'STORE_SCOPE_MISMATCH') {
    return gt('generated.tenant-admin-settings.048')
  }
  if (error.response.error.code === 'FORBIDDEN') {
    return gt('generated.tenant-admin-settings.049')
  }
  return gt('generated.tenant-admin-settings.050')
}

function normalizeTime(value: string): string {
  return value ? value.slice(0, 5) : ''
}
</script>

<template>
  <main class="tenant-shell">
    <TenantAdminNav />

    <section class="tenant-workspace">
      <header class="page-heading">
        <div>
          <span>{{ gt('generated.tenant-admin-settings.001') }}</span>
          <h1>{{ gt('generated.tenant-admin-settings.002') }}</h1>
        </div>
      </header>

      <p v-if="errorText" class="error-banner" role="alert">{{ errorText }}</p>
      <p v-if="savedText" class="success-banner" role="status">{{ savedText }}</p>
      <p v-if="loading" class="loading-line">{{ gt('generated.tenant-admin-settings.003') }}</p>

      <form v-else class="form-panel" @submit.prevent="submitSettings">
        <label>
          <span>{{ gt('generated.tenant-admin-settings.004') }}</span>
          <input v-model.trim="form.storeName" required />
        </label>
        <label>
          <span>{{ gt('generated.tenant-admin-settings.005') }}</span>
          <input v-model.trim="form.timezone" required />
        </label>
        <label>
          <span>{{ gt('generated.tenant-admin-settings.006') }}</span>
          <input v-model.trim="form.locale" required />
        </label>
        <label>
          <span>{{ gt('generated.tenant-admin-settings.007') }}</span>
          <input v-model.trim="form.currency" required />
        </label>
        <label>
          <span>{{ gt('generated.tenant-admin-settings.008') }}</span>
          <input v-model.trim="form.dateFormat" placeholder="DD-MM-YYYY" required />
        </label>
        <label>
          <span>{{ gt('generated.tenant-admin-settings.009') }}</span>
          <input v-model.trim="form.timeFormat" required />
        </label>
        <label>
          <span>{{ gt('generated.tenant-admin-settings.010') }}</span>
          <input v-model.number="form.reservationHoldMinutes" type="number" min="1" required />
        </label>
        <label>
          <span>{{ gt('generated.tenant-admin-settings.011') }}</span>
          <input v-model.number="form.queueCallHoldMinutes" type="number" min="1" required />
        </label>
        <label>
          <span>{{ gt('generated.tenant-admin-settings.012') }}</span>
          <input v-model.number="form.expectedDiningMinutes" type="number" min="1" required />
        </label>
        <div class="form-actions">
          <button class="primary-button" type="submit" :disabled="saving">
            {{ saving ? gt('generated.tenant-admin-settings.013') : gt('generated.tenant-admin-settings.014') }}
          </button>
        </div>
      </form>

      <form v-if="!loading" class="meal-period-panel" :aria-label="gt('generated.tenant-admin-settings.015')" @submit.prevent="submitMealPeriods">
        <div class="section-heading">
          <h2>{{ gt('generated.tenant-admin-settings.016') }}</h2>
          <div class="section-actions">
            <button class="secondary-button" type="button" @click="usePlatformSeed">{{ gt('generated.tenant-admin-settings.017') }}</button>
            <button class="secondary-button" type="button" @click="copyPlatformSeed">{{ gt('generated.tenant-admin-settings.018') }}</button>
            <button class="primary-button" type="submit" :disabled="savingMealPeriods">
              {{ savingMealPeriods ? gt('generated.tenant-admin-settings.019') : gt('generated.tenant-admin-settings.020') }}
            </button>
          </div>
        </div>

        <div class="mode-row">
          <label>
            <input v-model="mealPeriodForm.usePlatformSeed" :value="true" type="radio" />
            <span>{{ gt('generated.tenant-admin-settings.021') }}</span>
          </label>
          <label>
            <input v-model="mealPeriodForm.usePlatformSeed" :value="false" type="radio" />
            <span>{{ gt('generated.tenant-admin-settings.022') }}</span>
          </label>
        </div>

        <div class="period-grid" role="table" :aria-label="gt('generated.tenant-admin-settings.023')">
          <div class="period-grid__head" role="row">
            <span>{{ gt('generated.tenant-admin-settings.024') }}</span>
            <span>{{ gt('generated.tenant-admin-settings.025') }}</span>
            <span>{{ gt('generated.tenant-admin-settings.026') }}</span>
            <span>{{ gt('generated.tenant-admin-settings.027') }}</span>
            <span>{{ gt('generated.tenant-admin-settings.028') }}</span>
            <span>{{ gt('generated.tenant-admin-settings.029') }}</span>
            <span>{{ gt('generated.tenant-admin-settings.030') }}</span>
            <span>{{ gt('generated.tenant-admin-settings.031') }}</span>
            <span></span>
          </div>

          <div
            v-for="(period, index) in mealPeriodForm.periods"
            :key="`${period.periodKey}-${index}`"
            class="period-grid__row"
            role="row"
          >
            <input v-model.trim="period.periodKey" :disabled="mealPeriodForm.usePlatformSeed" required maxlength="64" />
            <input v-model.trim="period.displayName" :disabled="mealPeriodForm.usePlatformSeed" required maxlength="80" />
            <input v-model="period.startLocalTime" :disabled="mealPeriodForm.usePlatformSeed" required type="time" />
            <input v-model="period.endLocalTime" :disabled="mealPeriodForm.usePlatformSeed" required type="time" />
            <label class="switch-cell">
              <input v-model="period.crossesNextDay" :disabled="mealPeriodForm.usePlatformSeed" type="checkbox" />
              <span>{{ period.crossesNextDay ? gt('generated.tenant-admin-settings.032') : gt('generated.tenant-admin-settings.033') }}</span>
            </label>
            <input
              v-model.number="period.slotIntervalMinutes"
              :disabled="mealPeriodForm.usePlatformSeed"
              required
              min="5"
              max="240"
              step="5"
              type="number"
            />
            <select v-model="period.status" :disabled="mealPeriodForm.usePlatformSeed">
              <option value="active">{{ gt('generated.tenant-admin-settings.034') }}</option>
              <option value="disabled">{{ gt('generated.tenant-admin-settings.035') }}</option>
            </select>
            <input v-model.number="period.sortOrder" :disabled="mealPeriodForm.usePlatformSeed" required type="number" />
            <button
              class="link-button"
              type="button"
              :disabled="mealPeriodForm.usePlatformSeed"
              @click="removePeriod(index)"
            > {{ gt('generated.tenant-admin-settings.036') }} </button>
          </div>
        </div>

        <div class="form-actions">
          <button class="secondary-button" type="button" :disabled="mealPeriodForm.usePlatformSeed" @click="addPeriod"> {{ gt('generated.tenant-admin-settings.037') }} </button>
        </div>
      </form>
    </section>
  </main>
</template>

<style scoped>
.tenant-shell {
  min-height: 100dvh;
  display: grid;
  grid-template-columns: 220px minmax(0, 1fr);
  background: #f3f6f8;
  color: #102033;
}

.tenant-workspace {
  min-width: 0;
  padding: 22px;
}

.page-heading {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: center;
  margin-bottom: 16px;
}

.page-heading span {
  color: #64748b;
  font-size: 13px;
  font-weight: 700;
}

.page-heading h1 {
  margin: 0;
  color: #0f172a;
  font-size: 24px;
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

.form-panel {
  width: min(100%, 860px);
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
  padding: 18px;
  border: 1px solid #dbe3ea;
  border-radius: 8px;
  background: #ffffff;
}

.meal-period-panel {
  margin-top: 16px;
  display: grid;
  gap: 14px;
  padding: 18px;
  border: 1px solid #dbe3ea;
  border-radius: 8px;
  background: #ffffff;
}

.section-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.section-heading h2 {
  margin: 0;
  color: #0f172a;
  font-size: 17px;
}

.section-actions,
.mode-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.mode-row label {
  min-height: 36px;
  grid-auto-flow: column;
  align-items: center;
  justify-content: start;
  gap: 6px;
  padding: 0 10px;
  border: 1px solid #cbd5e1;
  border-radius: 6px;
}

label {
  display: grid;
  gap: 7px;
  color: #334155;
  font-size: 14px;
  font-weight: 700;
}

input,
select {
  width: 100%;
  min-height: 40px;
  box-sizing: border-box;
  border: 1px solid #cbd5e1;
  border-radius: 6px;
  padding: 9px 10px;
  color: #0f172a;
  background: #ffffff;
  font: inherit;
}

.mode-row input,
.switch-cell input {
  width: auto;
  min-height: auto;
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

.switch-cell {
  min-height: 40px;
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

.period-grid input:disabled,
.period-grid select:disabled,
.switch-cell:has(input:disabled) {
  color: #64748b;
  background: #f8fafc;
}

.form-actions {
  grid-column: 1 / -1;
  display: flex;
  justify-content: flex-end;
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
  padding: 0 14px;
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

.primary-button:disabled,
.secondary-button:disabled,
.link-button:disabled {
  opacity: 0.6;
  cursor: default;
}

@media (max-width: 980px) {
  .tenant-shell,
  .form-panel {
    grid-template-columns: 1fr;
  }

  .section-heading {
    align-items: stretch;
    flex-direction: column;
  }
}

@media (max-width: 700px) {
  .tenant-workspace {
    padding: 14px;
  }

  .primary-button,
  .secondary-button {
    width: 100%;
  }
}
</style>
