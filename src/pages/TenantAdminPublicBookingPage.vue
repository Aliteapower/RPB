<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'

import {
  deleteTenantAdminPublicBookingAvailabilityRule,
  getTenantAdminCustomerEmailSettings,
  getTenantAdminCustomerOAuthProviderSettings,
  getTenantAdminPublicBookingAvailabilityRules,
  getTenantAdminPublicBookingSettings,
  updateTenantAdminCustomerEmailSettings,
  updateTenantAdminCustomerOAuthProviderSettings,
  updateTenantAdminPublicBookingAvailabilityRule,
  updateTenantAdminPublicBookingSettings,
  PublicBookingApiError
} from '../api/publicBookingApi'
import {
  getStoreReservationMealPeriods,
  ReservationMealPeriodApiError
} from '../api/reservationMealPeriodApi'
import {
  getTenantProfile
} from '../api/tenantAdminApi'
import DownloadableQrCode from '../components/common/DownloadableQrCode.vue'
import PasswordInput from '../components/common/PasswordInput.vue'
import TenantAdminNav from '../components/tenant-admin/TenantAdminNav.vue'
import type { ReservationMealPeriod } from '../types/reservationMealPeriod'
import type {
  TenantAdminCustomerEmailSettingsMutation,
  TenantAdminCustomerOAuthProviderSettingsMutation,
  TenantAdminPublicBookingAvailabilityRule,
  TenantAdminPublicBookingAvailabilityRuleMutation,
  TenantAdminPublicBookingSettingsMutation
} from '../types/publicBooking'
import { useGeneratedText } from '../i18n/generatedText'
import { publicBookingUrlForTenant } from '../utils/hostContext'

const { gt } = useGeneratedText()

const route = useRoute()
const loading = ref(false)
const saving = ref(false)
const savingEmail = ref(false)
const savingGoogle = ref(false)
const savingFacebook = ref(false)
const savingRule = ref(false)
const errorText = ref('')
const savedText = ref('')
const emailSecretConfigured = ref(false)
const googleSecretConfigured = ref(false)
const facebookSecretConfigured = ref(false)
const tenantLogoUrl = ref('')
const tenantCode = ref('')
const mealPeriods = ref<ReservationMealPeriod[]>([])
const availabilityRules = ref<TenantAdminPublicBookingAvailabilityRule[]>([])

type PublicBookingPanel = 'settings' | 'email' | 'google' | 'facebook' | 'rules'

const storeId = computed(() => String(route.params.storeId || ''))
const activePanel = ref<PublicBookingPanel>('settings')

const form = reactive<TenantAdminPublicBookingSettingsMutation>({
  enabled: false,
  requireCustomerLogin: true,
  defaultQuotaMode: 'percentage',
  defaultQuotaPercent: 100,
  defaultTableCount: null,
  defaultGuestCount: null,
  minLeadMinutes: 60,
  maxAdvanceDays: 30
})

const ruleForm = reactive<TenantAdminPublicBookingAvailabilityRuleMutation>({
  ruleType: 'weekly',
  businessDate: todayInputValue(),
  dayOfWeek: null,
  periodKey: '',
  quotaMode: 'closed',
  quotaPercent: null,
  tableCount: 4,
  guestCount: null
})
const selectedWeekdays = ref<number[]>([])

const weekdayOptions = [
  { value: 1, label: gt('generated.tenant-admin-public-booking.114') },
  { value: 2, label: gt('generated.tenant-admin-public-booking.115') },
  { value: 3, label: gt('generated.tenant-admin-public-booking.116') },
  { value: 4, label: gt('generated.tenant-admin-public-booking.117') },
  { value: 5, label: gt('generated.tenant-admin-public-booking.118') },
  { value: 6, label: gt('generated.tenant-admin-public-booking.119') },
  { value: 7, label: gt('generated.tenant-admin-public-booking.120') }
]

const emailForm = reactive<TenantAdminCustomerEmailSettingsMutation>({
  enabled: false,
  fromEmail: '',
  fromName: '',
  smtpHost: '',
  smtpPort: 587,
  smtpUsername: '',
  smtpPassword: '',
  smtpStartTls: true
})

const googleForm = reactive<TenantAdminCustomerOAuthProviderSettingsMutation>({
  enabled: false,
  clientId: '',
  clientSecret: ''
})

const facebookForm = reactive<TenantAdminCustomerOAuthProviderSettingsMutation>({
  enabled: false,
  clientId: '',
  clientSecret: ''
})

const publicBookingUrl = computed(() => publicBookingUrlForTenant(tenantCode.value, storeId.value))
const publicBookingQrFileName = computed(() => `public-booking-${tenantCode.value || storeId.value}.png`)
const activeMealPeriods = computed(() => mealPeriods.value.filter(period => period.status === 'active'))
const emailSmtpCredentialComplete = computed(() => (
  !hasText(emailForm.smtpUsername) ||
  emailSecretConfigured.value ||
  hasText(emailForm.smtpPassword)
))
const emailSettingsComplete = computed(() => (
  hasText(emailForm.fromEmail) &&
  hasText(emailForm.smtpHost) &&
  Number(emailForm.smtpPort) > 0 &&
  emailSmtpCredentialComplete.value
))
const googleSettingsComplete = computed(() => hasText(googleForm.clientId))
const facebookSettingsComplete = computed(() => (
  hasText(facebookForm.clientId) &&
  (facebookSecretConfigured.value || hasText(facebookForm.clientSecret))
))
const emailLoginReady = computed(() => emailForm.enabled && emailSettingsComplete.value)
const googleLoginReady = computed(() => googleForm.enabled && googleSettingsComplete.value)
const facebookLoginReady = computed(() => facebookForm.enabled && facebookSettingsComplete.value)
const canSaveEmail = computed(() => !savingEmail.value && (!emailForm.enabled || emailSettingsComplete.value))
const canSaveGoogle = computed(() => !savingGoogle.value && (!googleForm.enabled || googleSettingsComplete.value))
const canSaveFacebook = computed(() => !savingFacebook.value && (!facebookForm.enabled || facebookSettingsComplete.value))

onMounted(() => {
  void loadSettings()
})

function openPanel(panel: PublicBookingPanel): void {
  activePanel.value = panel
  errorText.value = ''
  savedText.value = ''
}

async function loadSettings(): Promise<void> {
  loading.value = true
  errorText.value = ''
  savedText.value = ''
  try {
    const [settings, email, google, facebook, mealPeriodResponse, rulesResponse, tenantProfileResponse] = await Promise.all([
      getTenantAdminPublicBookingSettings(storeId.value),
      getTenantAdminCustomerEmailSettings(storeId.value),
      getTenantAdminCustomerOAuthProviderSettings(storeId.value, 'google'),
      getTenantAdminCustomerOAuthProviderSettings(storeId.value, 'facebook'),
      getStoreReservationMealPeriods(storeId.value),
      getTenantAdminPublicBookingAvailabilityRules(storeId.value),
      getTenantProfile(storeId.value).catch(() => null)
    ])
    Object.assign(form, {
      enabled: settings.enabled,
      requireCustomerLogin: settings.requireCustomerLogin,
      defaultQuotaMode: settings.defaultQuotaMode,
      defaultQuotaPercent: settings.defaultQuotaPercent,
      defaultTableCount: settings.defaultTableCount,
      defaultGuestCount: settings.defaultGuestCount,
      minLeadMinutes: settings.minLeadMinutes,
      maxAdvanceDays: settings.maxAdvanceDays
    })
    Object.assign(emailForm, {
      enabled: email.enabled,
      fromEmail: email.fromEmail || '',
      fromName: email.fromName || '',
      smtpHost: email.smtpHost || '',
      smtpPort: email.smtpPort,
      smtpUsername: email.smtpUsername || '',
      smtpPassword: '',
      smtpStartTls: email.smtpStartTls
    })
    emailSecretConfigured.value = email.secretConfigured
    Object.assign(googleForm, {
      enabled: google.enabled,
      clientId: google.clientId || '',
      clientSecret: ''
    })
    googleSecretConfigured.value = google.secretConfigured
    Object.assign(facebookForm, {
      enabled: facebook.enabled,
      clientId: facebook.clientId || '',
      clientSecret: ''
    })
    facebookSecretConfigured.value = facebook.secretConfigured
    tenantLogoUrl.value = tenantProfileResponse?.profile.logoMediaUrl || ''
    tenantCode.value = tenantProfileResponse?.profile.tenantCode || ''
    mealPeriods.value = mealPeriodResponse.effectivePeriods
    availabilityRules.value = [...rulesResponse.rules].sort(compareAvailabilityRules)
    hydrateWeeklySelectionFromSavedRules()
  } catch (error) {
    errorText.value = apiErrorText(error)
  } finally {
    loading.value = false
  }
}

async function saveEmailSettings(): Promise<void> {
  if (!canSaveEmail.value) {
    return
  }
  savingEmail.value = true
  errorText.value = ''
  savedText.value = ''
  try {
    const response = await updateTenantAdminCustomerEmailSettings(storeId.value, normalizedEmailSettings())
    emailSecretConfigured.value = response.secretConfigured
    emailForm.smtpPassword = ''
    savedText.value = gt('generated.tenant-admin-public-booking.121')
  } catch (error) {
    errorText.value = apiErrorText(error)
  } finally {
    savingEmail.value = false
  }
}

async function saveOAuthSettings(provider: 'google' | 'facebook'): Promise<void> {
  const busy = provider === 'google' ? savingGoogle : savingFacebook
  const canSave = provider === 'google' ? canSaveGoogle : canSaveFacebook
  if (!canSave.value) {
    return
  }
  busy.value = true
  errorText.value = ''
  savedText.value = ''
  try {
    const target = provider === 'google' ? googleForm : facebookForm
    const response = await updateTenantAdminCustomerOAuthProviderSettings(
      storeId.value,
      provider,
      normalizedOAuthSettings(target)
    )
    if (provider === 'google') {
      googleSecretConfigured.value = response.secretConfigured
      googleForm.clientSecret = ''
      savedText.value = gt('generated.tenant-admin-public-booking.122')
    } else {
      facebookSecretConfigured.value = response.secretConfigured
      facebookForm.clientSecret = ''
      savedText.value = gt('generated.tenant-admin-public-booking.123')
    }
  } catch (error) {
    errorText.value = apiErrorText(error)
  } finally {
    busy.value = false
  }
}

async function saveSettings(): Promise<void> {
  if (saving.value) {
    return
  }
  saving.value = true
  errorText.value = ''
  savedText.value = ''
  try {
    const response = await updateTenantAdminPublicBookingSettings(storeId.value, normalizedSettings())
    Object.assign(form, response)
    savedText.value = gt('generated.tenant-admin-public-booking.124')
  } catch (error) {
    errorText.value = apiErrorText(error)
  } finally {
    saving.value = false
  }
}

async function saveAvailabilityRule(): Promise<void> {
  if (savingRule.value) {
    return
  }
  savingRule.value = true
  errorText.value = ''
  savedText.value = ''
  try {
    if (ruleForm.ruleType === 'weekly') {
      const responses = await syncWeeklyAvailabilityRules()
      savedText.value = selectedWeekdays.value.length === 0
        ? gt('generated.tenant-admin-public-booking.125')
        : responses.length > 1 ? `${gt('generated.tenant-admin-public-booking.103')}${responses.length}${gt('generated.tenant-admin-public-booking.104')}` : gt('generated.tenant-admin-public-booking.126')
      return
    }
    const response = await updateTenantAdminPublicBookingAvailabilityRule(storeId.value, normalizedAvailabilityRule())
    availabilityRules.value = upsertAvailabilityRules(availabilityRules.value, [response])
    savedText.value = gt('generated.tenant-admin-public-booking.127')
  } catch (error) {
    errorText.value = apiErrorText(error)
  } finally {
    savingRule.value = false
  }
}

async function syncWeeklyAvailabilityRules(): Promise<TenantAdminPublicBookingAvailabilityRule[]> {
  const staleWeeklyRules = availabilityRules.value.filter(rule => (
    sameWeeklyRuleEditGroup(rule) &&
    rule.dayOfWeek != null &&
    !selectedWeekdays.value.includes(rule.dayOfWeek)
  ))
  await Promise.all(staleWeeklyRules.map(rule => (
    rule.id ? deleteTenantAdminPublicBookingAvailabilityRule(storeId.value, rule.id) : Promise.resolve()
  )))
  const responses = await saveWeeklyAvailabilityRules()
  availabilityRules.value = upsertAvailabilityRules(
    availabilityRules.value.filter(rule => (
      !staleWeeklyRules.some(staleRule => sameAvailabilityRuleTarget(staleRule, rule))
    )),
    responses
  )
  return responses
}

async function saveWeeklyAvailabilityRules(): Promise<TenantAdminPublicBookingAvailabilityRule[]> {
  return Promise.all(selectedWeekdays.value.map(dayOfWeek => (
    updateTenantAdminPublicBookingAvailabilityRule(storeId.value, normalizedAvailabilityRule(dayOfWeek))
  )))
}

async function copyPublicUrl(): Promise<void> {
  errorText.value = ''
  savedText.value = ''
  if (await copyText(publicBookingUrl.value)) {
    savedText.value = gt('generated.tenant-admin-public-booking.128')
    return
  }
  errorText.value = `${gt('generated.tenant-admin-public-booking.105')}${publicBookingUrl.value}`
}

async function copyText(text: string): Promise<boolean> {
  if (navigator.clipboard?.writeText) {
    try {
      await navigator.clipboard.writeText(text)
      return true
    } catch {
      // Fall back for HTTP deployments where Clipboard API may be blocked.
    }
  }
  return fallbackCopyText(text)
}

function fallbackCopyText(text: string): boolean {
  const textArea = document.createElement('textarea')
  textArea.value = text
  textArea.setAttribute('readonly', '')
  textArea.style.left = '0'
  textArea.style.opacity = '0'
  textArea.style.position = 'fixed'
  textArea.style.top = '0'
  document.body.appendChild(textArea)
  textArea.focus()
  textArea.select()
  try {
    return document.execCommand('copy')
  } catch {
    return false
  } finally {
    document.body.removeChild(textArea)
  }
}

function normalizedSettings(): TenantAdminPublicBookingSettingsMutation {
  return {
    ...form,
    defaultQuotaPercent: form.defaultQuotaMode === 'percentage' ? Number(form.defaultQuotaPercent ?? 100) : null,
    defaultTableCount: form.defaultQuotaMode === 'table_count' ? Number(form.defaultTableCount ?? 0) : null,
    defaultGuestCount: form.defaultQuotaMode === 'guest_count' ? Number(form.defaultGuestCount ?? 0) : null,
    minLeadMinutes: Number(form.minLeadMinutes),
    maxAdvanceDays: Number(form.maxAdvanceDays)
  }
}

function normalizedAvailabilityRule(dayOfWeek?: number): TenantAdminPublicBookingAvailabilityRuleMutation {
  return {
    ruleType: ruleForm.ruleType,
    businessDate: ruleForm.ruleType === 'date_exception' ? ruleForm.businessDate || todayInputValue() : null,
    dayOfWeek: ruleForm.ruleType === 'weekly' && dayOfWeek != null ? Number(dayOfWeek) : null,
    periodKey: nullableText(ruleForm.periodKey || ''),
    quotaMode: ruleForm.quotaMode,
    quotaPercent: ruleForm.quotaMode === 'percentage' ? Number(ruleForm.quotaPercent ?? 20) : null,
    tableCount: ruleForm.quotaMode === 'table_count' ? Number(ruleForm.tableCount ?? 0) : null,
    guestCount: ruleForm.quotaMode === 'guest_count' ? Number(ruleForm.guestCount ?? 0) : null
  }
}

function normalizedEmailSettings(): TenantAdminCustomerEmailSettingsMutation {
  return {
    enabled: emailForm.enabled,
    fromEmail: nullableText(emailForm.fromEmail || ''),
    fromName: nullableText(emailForm.fromName || ''),
    smtpHost: nullableText(emailForm.smtpHost || ''),
    smtpPort: Number(emailForm.smtpPort || 587),
    smtpUsername: nullableText(emailForm.smtpUsername || ''),
    smtpPassword: nullableText(emailForm.smtpPassword || ''),
    smtpStartTls: emailForm.smtpStartTls
  }
}

function normalizedOAuthSettings(
  source: TenantAdminCustomerOAuthProviderSettingsMutation
): TenantAdminCustomerOAuthProviderSettingsMutation {
  return {
    enabled: source.enabled,
    clientId: nullableText(source.clientId || ''),
    clientSecret: nullableText(source.clientSecret || '')
  }
}

function nullableText(value: string): string | null {
  const trimmed = value.trim()
  return trimmed ? trimmed : null
}

function hasText(value: string | null | undefined): boolean {
  return !!value && !!value.trim()
}

function hydrateWeeklySelectionFromSavedRules(): void {
  if (ruleForm.ruleType !== 'weekly') {
    return
  }
  const firstWeeklyRule = availabilityRules.value.find(rule => (
    rule.ruleType === 'weekly' &&
    rule.dayOfWeek != null
  ))
  if (!firstWeeklyRule) {
    selectedWeekdays.value = []
    return
  }
  Object.assign(ruleForm, {
    periodKey: firstWeeklyRule.periodKey || '',
    quotaMode: firstWeeklyRule.quotaMode,
    quotaPercent: firstWeeklyRule.quotaPercent,
    tableCount: firstWeeklyRule.tableCount,
    guestCount: firstWeeklyRule.guestCount
  })
  selectedWeekdays.value = availabilityRules.value
    .filter(sameWeeklyRuleEditGroup)
    .map(rule => rule.dayOfWeek)
    .filter((dayOfWeek): dayOfWeek is number => dayOfWeek != null)
    .sort((left, right) => left - right)
}

function mealPeriodOptionLabel(period: ReservationMealPeriod): string {
  const startTime = period.startLocalTime.slice(0, 5)
  const endTime = period.endLocalTime.slice(0, 5)
  const nextDay = period.crossesNextDay ? gt('generated.tenant-admin-public-booking.129') : ''
  return `${period.displayName} (${period.periodKey}, ${startTime}-${endTime}${nextDay})`
}

function upsertAvailabilityRules(
  rules: TenantAdminPublicBookingAvailabilityRule[],
  updatedRules: TenantAdminPublicBookingAvailabilityRule[]
): TenantAdminPublicBookingAvailabilityRule[] {
  return updatedRules.reduce(
    (nextRules, rule) => upsertAvailabilityRule(nextRules, rule),
    rules
  )
}

function upsertAvailabilityRule(
  rules: TenantAdminPublicBookingAvailabilityRule[],
  rule: TenantAdminPublicBookingAvailabilityRule
): TenantAdminPublicBookingAvailabilityRule[] {
  const nextRules = rules.filter(candidate => !sameAvailabilityRuleTarget(candidate, rule))
  nextRules.push(rule)
  return nextRules.sort(compareAvailabilityRules)
}

function sameAvailabilityRuleTarget(
  left: TenantAdminPublicBookingAvailabilityRule,
  right: TenantAdminPublicBookingAvailabilityRule
): boolean {
  return left.ruleType === right.ruleType &&
    left.businessDate === right.businessDate &&
    left.dayOfWeek === right.dayOfWeek &&
    (left.periodKey || '') === (right.periodKey || '')
}

function sameWeeklyRuleEditGroup(rule: TenantAdminPublicBookingAvailabilityRule): boolean {
  if (rule.ruleType !== 'weekly' || rule.dayOfWeek == null) {
    return false
  }
  const current = normalizedAvailabilityRule(rule.dayOfWeek)
  return sameNullableText(rule.periodKey, current.periodKey) &&
    rule.quotaMode === current.quotaMode &&
    (rule.quotaPercent ?? null) === (current.quotaPercent ?? null) &&
    (rule.tableCount ?? null) === (current.tableCount ?? null) &&
    (rule.guestCount ?? null) === (current.guestCount ?? null)
}

function compareAvailabilityRules(
  left: TenantAdminPublicBookingAvailabilityRule,
  right: TenantAdminPublicBookingAvailabilityRule
): number {
  return ruleTargetLabel(left).localeCompare(ruleTargetLabel(right), 'zh-Hans') ||
    rulePeriodLabel(left).localeCompare(rulePeriodLabel(right), 'zh-Hans')
}

function ruleTargetLabel(rule: TenantAdminPublicBookingAvailabilityRule): string {
  if (rule.ruleType === 'weekly') {
    return `${gt('generated.tenant-admin-public-booking.106')}${weekdayLabel(rule.dayOfWeek)}`
  }
  return `${gt('generated.tenant-admin-public-booking.107')}${rule.businessDate || gt('generated.tenant-admin-public-booking.108')}`
}

function rulePeriodLabel(rule: TenantAdminPublicBookingAvailabilityRule): string {
  if (!rule.periodKey) {
    return gt('generated.tenant-admin-public-booking.130')
  }
  const period = activeMealPeriods.value.find(candidate => candidate.periodKey === rule.periodKey)
  return period ? `${period.displayName} (${period.periodKey})` : rule.periodKey
}

function ruleModeLabel(rule: TenantAdminPublicBookingAvailabilityRule): string {
  if (rule.quotaMode === 'closed') {
    return gt('generated.tenant-admin-public-booking.131')
  }
  if (rule.quotaMode === 'percentage') {
    return `${gt('generated.tenant-admin-public-booking.109')}${rule.quotaPercent ?? 0}%`
  }
  if (rule.quotaMode === 'table_count') {
    return `${gt('generated.tenant-admin-public-booking.110')}${rule.tableCount ?? 0}${gt('generated.tenant-admin-public-booking.111')}`
  }
  return `${gt('generated.tenant-admin-public-booking.112')}${rule.guestCount ?? 0}${gt('generated.tenant-admin-public-booking.113')}`
}

function weekdayLabel(dayOfWeek: number | null): string {
  return weekdayOptions.find(option => option.value === dayOfWeek)?.label || gt('generated.tenant-admin-public-booking.132')
}

function sameNullableText(left: string | null, right: string | null): boolean {
  return (left || '') === (right || '')
}

function todayInputValue(): string {
  const date = new Date()
  const localDate = new Date(date.getTime() - date.getTimezoneOffset() * 60_000)
  return localDate.toISOString().slice(0, 10)
}

function apiErrorText(error: unknown): string {
  if (error instanceof ReservationMealPeriodApiError) {
    if (error.status === 401) {
      return gt('generated.tenant-admin-public-booking.133')
    }
    if (error.status === 403 || error.response.error.code === 'FORBIDDEN') {
      return gt('generated.tenant-admin-public-booking.134')
    }
    return gt('generated.tenant-admin-public-booking.135')
  }
  if (!(error instanceof PublicBookingApiError)) {
    return gt('generated.tenant-admin-public-booking.136')
  }
  if (error.status === 401) {
    return gt('generated.tenant-admin-public-booking.137')
  }
  if (error.status === 403) {
    return gt('generated.tenant-admin-public-booking.138')
  }
  return gt('generated.tenant-admin-public-booking.139')
}
</script>

<template>
  <main class="tenant-shell">
    <TenantAdminNav />

    <section class="tenant-workspace">
      <header class="page-heading">
        <div>
          <span>{{ gt('generated.tenant-admin-public-booking.001') }}</span>
          <h1>{{ gt('generated.tenant-admin-public-booking.002') }}</h1>
        </div>
      </header>

      <p v-if="errorText" class="error-banner" role="alert">{{ errorText }}</p>
      <p v-if="savedText" class="success-banner" role="status">{{ savedText }}</p>
      <p v-if="loading" class="loading-line">{{ gt('generated.tenant-admin-public-booking.003') }}</p>

      <section v-else class="settings-layout">
        <section class="public-entry-panel" :aria-label="gt('generated.tenant-admin-public-booking.004')">
          <div class="public-entry-panel__content">
            <span>{{ gt('generated.tenant-admin-public-booking.005') }}</span>
            <strong>{{ publicBookingUrl }}</strong>
            <p>{{ gt('generated.tenant-admin-public-booking.006') }}</p>
            <button type="button" :aria-label="gt('generated.tenant-admin-public-booking.007')" @click="copyPublicUrl">{{ gt('generated.tenant-admin-public-booking.008') }}</button>
          </div>
          <DownloadableQrCode
            :value="publicBookingUrl"
            :logo-url="tenantLogoUrl"
            :file-name="publicBookingQrFileName"
            :title="gt('generated.tenant-admin-public-booking.009')"
            :description="gt('generated.tenant-admin-public-booking.010')"
            :download-label="gt('generated.tenant-admin-public-booking.011')"
          />
        </section>

        <section class="settings-operation-list" :aria-label="gt('generated.tenant-admin-public-booking.012')">
          <h2 class="operation-list-title">{{ gt('generated.tenant-admin-public-booking.013') }}</h2>

          <article class="operation-card" :class="{ 'operation-card--active': activePanel === 'settings' }">
            <div>
              <span>{{ gt('generated.tenant-admin-public-booking.014') }}</span>
              <small>{{ gt('generated.tenant-admin-public-booking.015') }}</small>
            </div>
            <strong class="status-chip" :class="{ 'status-chip--ready': form.enabled }">
              {{ form.enabled ? gt('generated.tenant-admin-public-booking.016') : gt('generated.tenant-admin-public-booking.017') }}
            </strong>
            <button
              type="button"
              :aria-expanded="activePanel === 'settings'"
              @click="openPanel('settings')"
            > {{ gt('generated.tenant-admin-public-booking.018') }} </button>
          </article>

          <article class="operation-card" :class="{ 'operation-card--active': activePanel === 'email' }">
            <div>
              <span>{{ gt('generated.tenant-admin-public-booking.019') }}</span>
              <small>{{ gt('generated.tenant-admin-public-booking.020') }}</small>
            </div>
            <strong class="status-chip" :class="{ 'status-chip--ready': emailLoginReady }">
              {{ emailLoginReady ? gt('generated.tenant-admin-public-booking.021') : gt('generated.tenant-admin-public-booking.022') }}
            </strong>
            <button
              type="button"
              :aria-expanded="activePanel === 'email'"
              @click="openPanel('email')"
            > {{ gt('generated.tenant-admin-public-booking.023') }} </button>
          </article>

          <article class="operation-card" :class="{ 'operation-card--active': activePanel === 'google' }">
            <div>
              <span>{{ gt('generated.tenant-admin-public-booking.024') }}</span>
              <small>OAuth Client ID</small>
            </div>
            <strong class="status-chip" :class="{ 'status-chip--ready': googleLoginReady }">
              {{ googleLoginReady ? gt('generated.tenant-admin-public-booking.025') : gt('generated.tenant-admin-public-booking.026') }}
            </strong>
            <button
              type="button"
              :aria-expanded="activePanel === 'google'"
              @click="openPanel('google')"
            > {{ gt('generated.tenant-admin-public-booking.027') }} </button>
          </article>

          <article class="operation-card" :class="{ 'operation-card--active': activePanel === 'facebook' }">
            <div>
              <span>{{ gt('generated.tenant-admin-public-booking.028') }}</span>
              <small>App ID / App Secret</small>
            </div>
            <strong class="status-chip" :class="{ 'status-chip--ready': facebookLoginReady }">
              {{ facebookLoginReady ? gt('generated.tenant-admin-public-booking.029') : gt('generated.tenant-admin-public-booking.030') }}
            </strong>
            <button
              type="button"
              :aria-expanded="activePanel === 'facebook'"
              @click="openPanel('facebook')"
            > {{ gt('generated.tenant-admin-public-booking.031') }} </button>
          </article>

          <article class="operation-card" :class="{ 'operation-card--active': activePanel === 'rules' }">
            <div>
              <span>{{ gt('generated.tenant-admin-public-booking.032') }}</span>
              <small>{{ gt('generated.tenant-admin-public-booking.033') }}</small>
            </div>
            <strong class="status-chip status-chip--ready">{{ gt('generated.tenant-admin-public-booking.034') }}</strong>
            <button
              type="button"
              :aria-expanded="activePanel === 'rules'"
              @click="openPanel('rules')"
            > {{ gt('generated.tenant-admin-public-booking.035') }} </button>
          </article>
        </section>

        <form v-if="activePanel === 'settings'" class="form-panel" @submit.prevent="saveSettings">
          <section class="form-panel__wide switch-row">
            <label>
              <input v-model="form.enabled" type="checkbox" />
              <span>{{ gt('generated.tenant-admin-public-booking.036') }}</span>
            </label>
            <label>
              <input v-model="form.requireCustomerLogin" type="checkbox" />
              <span>{{ gt('generated.tenant-admin-public-booking.037') }}</span>
            </label>
          </section>

          <label>
            <span>{{ gt('generated.tenant-admin-public-booking.038') }}</span>
            <select v-model="form.defaultQuotaMode">
              <option value="percentage">{{ gt('generated.tenant-admin-public-booking.039') }}</option>
              <option value="table_count">{{ gt('generated.tenant-admin-public-booking.040') }}</option>
              <option value="guest_count">{{ gt('generated.tenant-admin-public-booking.041') }}</option>
            </select>
          </label>

          <label v-if="form.defaultQuotaMode === 'percentage'">
            <span>{{ gt('generated.tenant-admin-public-booking.042') }}</span>
            <input v-model.number="form.defaultQuotaPercent" min="0" max="100" type="number" />
          </label>

          <label v-if="form.defaultQuotaMode === 'table_count'">
            <span>{{ gt('generated.tenant-admin-public-booking.043') }}</span>
            <input v-model.number="form.defaultTableCount" min="0" type="number" />
          </label>

          <label v-if="form.defaultQuotaMode === 'guest_count'">
            <span>{{ gt('generated.tenant-admin-public-booking.044') }}</span>
            <input v-model.number="form.defaultGuestCount" min="0" type="number" />
          </label>

          <label>
            <span>{{ gt('generated.tenant-admin-public-booking.045') }}</span>
            <input v-model.number="form.minLeadMinutes" min="0" type="number" />
          </label>

          <label>
            <span>{{ gt('generated.tenant-admin-public-booking.046') }}</span>
            <input v-model.number="form.maxAdvanceDays" min="0" max="366" type="number" />
          </label>

          <div class="form-actions">
            <button class="primary-button" type="submit" :disabled="saving">
              {{ saving ? gt('generated.tenant-admin-public-booking.047') : gt('generated.tenant-admin-public-booking.048') }}
            </button>
          </div>
        </form>

        <form v-else-if="activePanel === 'email'" class="form-panel" @submit.prevent="saveEmailSettings">
          <div class="form-panel__wide panel-heading-row">
            <h2>{{ gt('generated.tenant-admin-public-booking.049') }}</h2>
            <strong class="status-chip" :class="{ 'status-chip--ready': emailLoginReady }">
              {{ emailLoginReady ? gt('generated.tenant-admin-public-booking.050') : gt('generated.tenant-admin-public-booking.051') }}
            </strong>
          </div>
          <p class="form-panel__wide panel-note">{{ gt('generated.tenant-admin-public-booking.052') }}</p>

          <section class="form-panel__wide switch-row">
            <label>
              <input v-model="emailForm.enabled" type="checkbox" />
              <span>{{ gt('generated.tenant-admin-public-booking.053') }}</span>
            </label>
            <span class="secret-hint">{{ emailSecretConfigured ? gt('generated.tenant-admin-public-booking.054') : gt('generated.tenant-admin-public-booking.055') }}</span>
          </section>
          <p v-if="emailForm.enabled && !emailSettingsComplete" class="form-panel__wide field-hint"> {{ gt('generated.tenant-admin-public-booking.056') }} </p>

          <label>
            <span>{{ gt('generated.tenant-admin-public-booking.057') }}</span>
            <input v-model="emailForm.fromEmail" autocomplete="off" type="email" />
          </label>

          <label>
            <span>{{ gt('generated.tenant-admin-public-booking.058') }}</span>
            <input v-model="emailForm.fromName" autocomplete="off" />
          </label>

          <label>
            <span>SMTP Host</span>
            <input v-model="emailForm.smtpHost" autocomplete="off" />
          </label>

          <label>
            <span>SMTP Port</span>
            <input v-model.number="emailForm.smtpPort" min="1" max="65535" type="number" />
          </label>

          <label>
            <span>{{ gt('generated.tenant-admin-public-booking.059') }}</span>
            <input v-model="emailForm.smtpUsername" autocomplete="off" />
          </label>

          <label>
            <span>{{ gt('generated.tenant-admin-public-booking.060') }}</span>
            <PasswordInput v-model="emailForm.smtpPassword" autocomplete="new-password" />
          </label>

          <section class="form-panel__wide switch-row">
            <label>
              <input v-model="emailForm.smtpStartTls" type="checkbox" />
              <span>StartTLS</span>
            </label>
          </section>

          <div class="form-actions">
            <button class="primary-button" type="submit" :disabled="!canSaveEmail">
              {{ savingEmail ? gt('generated.tenant-admin-public-booking.061') : gt('generated.tenant-admin-public-booking.062') }}
            </button>
          </div>
        </form>

        <form v-else-if="activePanel === 'google'" class="form-panel" @submit.prevent="saveOAuthSettings('google')">
          <div class="form-panel__wide panel-heading-row">
            <h2>{{ gt('generated.tenant-admin-public-booking.063') }}</h2>
            <strong class="status-chip" :class="{ 'status-chip--ready': googleLoginReady }">
              {{ googleLoginReady ? gt('generated.tenant-admin-public-booking.064') : gt('generated.tenant-admin-public-booking.065') }}
            </strong>
          </div>

          <section class="form-panel__wide switch-row">
            <label>
              <input v-model="googleForm.enabled" type="checkbox" />
              <span>{{ gt('generated.tenant-admin-public-booking.066') }}</span>
            </label>
          </section>
          <p v-if="googleForm.enabled && !googleSettingsComplete" class="form-panel__wide field-hint"> {{ gt('generated.tenant-admin-public-booking.067') }} </p>

          <label>
            <span>Client ID</span>
            <input v-model="googleForm.clientId" autocomplete="off" />
          </label>

          <div class="form-actions">
            <button class="primary-button" type="submit" :disabled="!canSaveGoogle">
              {{ savingGoogle ? gt('generated.tenant-admin-public-booking.068') : gt('generated.tenant-admin-public-booking.069') }}
            </button>
          </div>
        </form>

        <form v-else-if="activePanel === 'facebook'" class="form-panel" @submit.prevent="saveOAuthSettings('facebook')">
          <div class="form-panel__wide panel-heading-row">
            <h2>{{ gt('generated.tenant-admin-public-booking.070') }}</h2>
            <strong class="status-chip" :class="{ 'status-chip--ready': facebookLoginReady }">
              {{ facebookLoginReady ? gt('generated.tenant-admin-public-booking.071') : gt('generated.tenant-admin-public-booking.072') }}
            </strong>
          </div>

          <section class="form-panel__wide switch-row">
            <label>
              <input v-model="facebookForm.enabled" type="checkbox" />
              <span>{{ gt('generated.tenant-admin-public-booking.073') }}</span>
            </label>
            <span class="secret-hint">{{ facebookSecretConfigured ? gt('generated.tenant-admin-public-booking.074') : gt('generated.tenant-admin-public-booking.075') }}</span>
          </section>
          <p v-if="facebookForm.enabled && !facebookSettingsComplete" class="form-panel__wide field-hint"> {{ gt('generated.tenant-admin-public-booking.076') }} </p>

          <label>
            <span>App ID</span>
            <input v-model="facebookForm.clientId" autocomplete="off" />
          </label>

          <label>
            <span>App Secret</span>
            <PasswordInput v-model="facebookForm.clientSecret" autocomplete="new-password" />
          </label>

          <div class="form-actions">
            <button class="primary-button" type="submit" :disabled="!canSaveFacebook">
              {{ savingFacebook ? gt('generated.tenant-admin-public-booking.077') : gt('generated.tenant-admin-public-booking.078') }}
            </button>
          </div>
        </form>

        <form v-else-if="activePanel === 'rules'" class="form-panel" @submit.prevent="saveAvailabilityRule">
          <h2 class="form-panel__wide">{{ gt('generated.tenant-admin-public-booking.079') }}</h2>
          <p class="form-panel__wide panel-note"> {{ gt('generated.tenant-admin-public-booking.080') }} </p>

          <label>
            <span>{{ gt('generated.tenant-admin-public-booking.081') }}</span>
            <select v-model="ruleForm.ruleType">
              <option value="weekly">{{ gt('generated.tenant-admin-public-booking.082') }}</option>
              <option value="date_exception">{{ gt('generated.tenant-admin-public-booking.083') }}</option>
            </select>
          </label>

          <section v-if="ruleForm.ruleType === 'weekly'" class="weekday-field">
            <span>{{ gt('generated.tenant-admin-public-booking.084') }}</span>
            <div class="weekday-checkbox-grid" role="group" :aria-label="gt('generated.tenant-admin-public-booking.085')">
              <label
                v-for="weekday in weekdayOptions"
                :key="weekday.value"
                class="weekday-checkbox"
              >
                <input v-model="selectedWeekdays" type="checkbox" :value="weekday.value" />
                <span>{{ weekday.label }}</span>
              </label>
            </div>
          </section>

          <label v-if="ruleForm.ruleType === 'date_exception'">
            <span>{{ gt('generated.tenant-admin-public-booking.086') }}</span>
            <input v-model="ruleForm.businessDate" type="date" />
          </label>

          <label>
            <span>{{ gt('generated.tenant-admin-public-booking.087') }}</span>
            <select v-model="ruleForm.periodKey">
              <option value="">{{ gt('generated.tenant-admin-public-booking.088') }}</option>
              <option
                v-for="period in activeMealPeriods"
                :key="period.id"
                :value="period.periodKey"
              >
                {{ mealPeriodOptionLabel(period) }}
              </option>
            </select>
          </label>

          <p v-if="activeMealPeriods.length === 0" class="form-panel__wide field-hint"> {{ gt('generated.tenant-admin-public-booking.089') }} </p>

          <label>
            <span>{{ gt('generated.tenant-admin-public-booking.090') }}</span>
            <select v-model="ruleForm.quotaMode">
              <option value="percentage">{{ gt('generated.tenant-admin-public-booking.091') }}</option>
              <option value="table_count">{{ gt('generated.tenant-admin-public-booking.092') }}</option>
              <option value="guest_count">{{ gt('generated.tenant-admin-public-booking.093') }}</option>
              <option value="closed">{{ gt('generated.tenant-admin-public-booking.094') }}</option>
            </select>
          </label>

          <label v-if="ruleForm.quotaMode === 'percentage'">
            <span>{{ gt('generated.tenant-admin-public-booking.095') }}</span>
            <input v-model.number="ruleForm.quotaPercent" min="0" max="100" type="number" />
          </label>

          <label v-if="ruleForm.quotaMode === 'table_count'">
            <span>{{ gt('generated.tenant-admin-public-booking.096') }}</span>
            <input v-model.number="ruleForm.tableCount" min="0" type="number" />
          </label>

          <label v-if="ruleForm.quotaMode === 'guest_count'">
            <span>{{ gt('generated.tenant-admin-public-booking.097') }}</span>
            <input v-model.number="ruleForm.guestCount" min="0" type="number" />
          </label>

          <div class="form-actions">
            <button class="primary-button" type="submit" :disabled="savingRule">
              {{ savingRule ? gt('generated.tenant-admin-public-booking.098') : gt('generated.tenant-admin-public-booking.099') }}
            </button>
          </div>

          <section class="form-panel__wide rule-list" :aria-label="gt('generated.tenant-admin-public-booking.100')">
            <h3>{{ gt('generated.tenant-admin-public-booking.101') }}</h3>
            <p v-if="availabilityRules.length === 0" class="rule-list__empty">{{ gt('generated.tenant-admin-public-booking.102') }}</p>
            <article
              v-for="rule in availabilityRules"
              :key="`${rule.ruleType}-${rule.businessDate || rule.dayOfWeek}-${rule.periodKey || 'all'}`"
            >
              <strong>{{ ruleTargetLabel(rule) }}</strong>
              <span>{{ rulePeriodLabel(rule) }}</span>
              <em>{{ ruleModeLabel(rule) }}</em>
            </article>
          </section>
        </form>
      </section>
    </section>
  </main>
</template>

<style scoped>
.tenant-shell {
  background: #f3f6f8;
  color: #102033;
  display: grid;
  grid-template-columns: 220px minmax(0, 1fr);
  min-height: 100dvh;
}

.tenant-workspace {
  min-width: 0;
  padding: 22px;
}

.page-heading {
  align-items: center;
  display: flex;
  gap: 16px;
  justify-content: space-between;
  margin-bottom: 16px;
}

.page-heading span {
  color: #64748b;
  font-size: 13px;
  font-weight: 700;
}

.page-heading h1 {
  color: #0f172a;
  font-size: 24px;
  margin: 0;
}

.public-entry-panel__content button,
.form-actions button {
  border-radius: 6px;
  font: inherit;
  font-weight: 800;
  min-height: 38px;
  padding: 0 12px;
}

.public-entry-panel__content button {
  background: #ffffff;
  border: 1px solid #cbd5e1;
  color: #334155;
  justify-self: start;
}

.public-entry-panel {
  align-items: stretch;
  background: #ffffff;
  border: 1px solid #dbe3ea;
  border-radius: 8px;
  display: grid;
  gap: 16px;
  grid-template-columns: minmax(0, 1fr) 286px;
  padding: 14px;
}

.public-entry-panel__content {
  align-content: start;
  display: grid;
  gap: 10px;
  min-width: 0;
}

.public-entry-panel__content span {
  color: #0f766e;
  font-size: 13px;
  font-weight: 900;
}

.public-entry-panel__content strong {
  background: #f8fafc;
  border: 1px solid #dbe3ea;
  border-radius: 6px;
  color: #0f172a;
  font-size: 14px;
  font-weight: 850;
  line-height: 1.45;
  overflow-wrap: anywhere;
  padding: 10px 12px;
}

.public-entry-panel__content p {
  color: #64748b;
  font-size: 13px;
  font-weight: 750;
  margin: 0;
}

.error-banner,
.success-banner,
.loading-line {
  border-radius: 6px;
  margin: 0 0 12px;
  padding: 10px 12px;
}

.error-banner {
  background: #fff1f2;
  border: 1px solid #fecaca;
  color: #991b1b;
}

.success-banner {
  background: #f0fdf4;
  border: 1px solid #bbf7d0;
  color: #166534;
}

.loading-line {
  background: #ffffff;
  border: 1px solid #dbe3ea;
  color: #475569;
}

.settings-layout {
  display: grid;
  gap: 16px;
  max-width: 980px;
}

.settings-operation-list {
  background: #ffffff;
  border: 1px solid #dbe3ea;
  border-radius: 8px;
  display: grid;
  gap: 10px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  padding: 14px;
}

.operation-list-title {
  color: #0f172a;
  font-size: 17px;
  grid-column: 1 / -1;
  margin: 0 0 2px;
}

.operation-card {
  align-items: center;
  background: #f8fafc;
  border: 1px solid #dbe3ea;
  border-radius: 6px;
  display: grid;
  gap: 12px;
  grid-template-columns: minmax(0, 1fr) auto auto;
  min-height: 64px;
  padding: 12px;
}

.operation-card--active {
  background: #f0fdfa;
  border-color: #0f766e;
  box-shadow: inset 3px 0 0 #0f766e;
}

.operation-card div {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.operation-card span {
  color: #334155;
  font-size: 14px;
  font-weight: 850;
}

.operation-card small {
  color: #64748b;
  font-size: 12px;
  font-weight: 750;
}

.operation-card button {
  background: #ffffff;
  border: 1px solid #cbd5e1;
  border-radius: 6px;
  color: #334155;
  font: inherit;
  font-size: 13px;
  font-weight: 850;
  min-height: 34px;
  padding: 0 10px;
}

.operation-card--active button {
  background: #0f766e;
  border-color: #0f766e;
  color: #ffffff;
}

.form-panel {
  background: #ffffff;
  border: 1px solid #dbe3ea;
  border-radius: 8px;
  display: grid;
  gap: 16px;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  padding: 18px;
}

.form-panel__wide {
  grid-column: 1 / -1;
}

.panel-heading-row {
  align-items: center;
  display: flex;
  gap: 12px;
  justify-content: space-between;
}

.form-panel h2 {
  color: #0f172a;
  font-size: 18px;
  margin: 0;
}

.panel-note {
  color: #64748b;
  font-size: 13px;
  font-weight: 800;
  margin: -8px 0 0;
}

.field-hint {
  background: #fff7ed;
  border: 1px solid #fed7aa;
  border-radius: 6px;
  color: #9a3412;
  font-size: 13px;
  font-weight: 850;
  margin: -4px 0 0;
  padding: 9px 10px;
}

.rule-list {
  border-top: 1px solid #e2e8f0;
  display: grid;
  gap: 10px;
  margin-top: 2px;
  padding-top: 14px;
}

.rule-list h3 {
  color: #0f172a;
  font-size: 15px;
  margin: 0;
}

.rule-list article {
  align-items: center;
  background: #f8fafc;
  border: 1px solid #dbe3ea;
  border-radius: 6px;
  display: grid;
  gap: 8px;
  grid-template-columns: minmax(0, 1fr) minmax(160px, auto) auto;
  min-height: 48px;
  padding: 10px 12px;
}

.rule-list strong,
.rule-list span,
.rule-list em,
.rule-list__empty {
  color: #334155;
  font-size: 13px;
  font-style: normal;
  font-weight: 800;
}

.rule-list span {
  color: #64748b;
}

.rule-list em {
  color: #0f766e;
  justify-self: end;
}

.rule-list__empty {
  margin: 0;
}

.weekday-field {
  color: #334155;
  display: grid;
  font-size: 14px;
  font-weight: 700;
  gap: 7px;
}

.weekday-checkbox-grid {
  display: grid;
  gap: 8px;
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.weekday-checkbox {
  align-items: center;
  background: #f8fafc;
  border: 1px solid #dbe3ea;
  border-radius: 6px;
  display: inline-flex;
  gap: 8px;
  min-height: 40px;
  padding: 0 10px;
}

.weekday-checkbox span {
  color: #334155;
  font-size: 13px;
  font-weight: 850;
}

.switch-row {
  display: flex;
  flex-wrap: wrap;
  gap: 18px;
}

label {
  color: #334155;
  display: grid;
  font-size: 14px;
  font-weight: 700;
  gap: 7px;
}

.switch-row label {
  align-items: center;
  display: inline-flex;
  gap: 8px;
}

.secret-hint {
  align-items: center;
  background: #eef6ff;
  border: 1px solid #bfdbfe;
  border-radius: 6px;
  color: #1d4ed8;
  display: inline-flex;
  font-size: 13px;
  font-weight: 800;
  min-height: 30px;
  padding: 0 10px;
}

.status-chip {
  align-items: center;
  background: #fff7ed;
  border: 1px solid #fed7aa;
  border-radius: 999px;
  color: #c2410c;
  display: inline-flex;
  font-size: 12px;
  font-weight: 950;
  min-height: 28px;
  padding: 0 10px;
  white-space: nowrap;
}

.status-chip--ready {
  background: #ecfdf5;
  border-color: #99f6e4;
  color: #0f766e;
}

input,
select {
  background: #ffffff;
  border: 1px solid #cbd5e1;
  border-radius: 6px;
  box-sizing: border-box;
  color: #0f172a;
  font: inherit;
  min-height: 40px;
  padding: 9px 10px;
  width: 100%;
}

input[type='checkbox'] {
  min-height: auto;
  width: auto;
}

.form-actions {
  display: flex;
  grid-column: 1 / -1;
  justify-content: flex-end;
}

.primary-button {
  background: #0f766e;
  border: 1px solid #0f766e;
  color: #ffffff;
}

button:disabled {
  cursor: default;
  opacity: 0.6;
}

@media (max-width: 980px) {
  .tenant-shell,
  .form-panel,
  .public-entry-panel,
  .settings-operation-list {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 700px) {
  .tenant-workspace {
    padding: 14px;
  }

  .page-heading,
  .form-actions {
    align-items: stretch;
    display: grid;
  }

  .public-entry-panel__content button {
    justify-self: stretch;
  }

  .operation-card {
    grid-template-columns: 1fr;
  }

  .rule-list article {
    grid-template-columns: 1fr;
  }

  .weekday-checkbox-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
