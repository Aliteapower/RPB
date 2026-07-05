<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'

import {
  createPublicBooking,
  getCustomerMe,
  getPublicBookingContext,
  loginCustomerWithEmail,
  loginCustomerWithOAuth,
  requestCustomerEmailCode,
  PublicBookingApiError
} from '../api/publicBookingApi'
import type {
  CustomerAuthPrincipal,
  PublicBookingContextResponse,
  PublicBookingTimeSlot
} from '../types/publicBooking'
import { useGeneratedText } from '../i18n/generatedText'

const { gt } = useGeneratedText()

declare global {
  interface Window {
    google?: {
      accounts: {
        id: {
          initialize: (options: { client_id: string; callback: (response: { credential?: string }) => void }) => void
          prompt: () => void
        }
      }
    }
    FB?: {
      init: (options: { appId: string; cookie: boolean; xfbml: boolean; version: string }) => void
      login: (
        callback: (response: { authResponse?: Record<string, string | undefined> }) => void,
        options: { scope: string }
      ) => void
    }
    fbAsyncInit?: () => void
  }
}

type BookingStep = 1 | 2 | 3

const route = useRoute()
const loading = ref(false)
const submitting = ref(false)
const authBusy = ref(false)
const errorText = ref('')
const statusText = ref('')
const currentStep = ref<BookingStep>(1)
const context = ref<PublicBookingContextResponse | null>(null)
const minBookingDate = todayDate()
const selectedDate = ref(minBookingDate)
const selectedStartAt = ref('')
const selectedPeriodKey = ref('all')
const dateInputErrorText = ref('')
const customer = ref<CustomerAuthPrincipal | null>(null)

const authForm = reactive({
  email: '',
  code: '',
  displayName: '',
  devCode: ''
})

const bookingForm = reactive({
  partySize: 2,
  phoneE164: '',
  note: ''
})

const ALL_PERIOD_KEY = 'all'
const DEFAULT_MAX_ADVANCE_DAYS = 30
const storeId = computed(() => String(route.params.storeId || '').trim())
const maxBookingDate = computed(() => {
  const maxAdvanceDays = context.value?.settings.maxAdvanceDays ?? DEFAULT_MAX_ADVANCE_DAYS
  return addDays(minBookingDate, maxAdvanceDays)
})
const bookingDateWindowText = computed(() => (
  `${gt('generated.public-booking.042')}${formatDisplayDate(maxBookingDate.value)}${gt('generated.public-booking.043')}`
))
const selectableSlots = computed(() => (context.value?.timeSlots || []).filter((slot) => slot.selectable))
const slotsByPeriod = computed(() => {
  const groups = new Map<string, PublicBookingTimeSlot[]>()
  selectableSlots.value.forEach((slot) => {
    const key = slot.periodKey || 'default'
    groups.set(key, [...(groups.get(key) || []), slot])
  })
  return Array.from(groups.entries()).map(([periodKey, slots]) => ({
    periodKey,
    displayName: slots[0]?.displayName || periodKey,
    slots
  }))
})
const periodFilterOptions = computed(() => [
  {
    periodKey: ALL_PERIOD_KEY,
    displayName: gt('generated.public-booking.045'),
    count: selectableSlots.value.length
  },
  ...slotsByPeriod.value.map((group) => ({
    periodKey: group.periodKey,
    displayName: group.displayName,
    count: group.slots.length
  }))
])
const filteredSlots = computed(() => {
  if (selectedPeriodKey.value === ALL_PERIOD_KEY) {
    return selectableSlots.value
  }
  return selectableSlots.value.filter((slot) => (slot.periodKey || 'default') === selectedPeriodKey.value)
})
const selectedSlot = computed(() => selectableSlots.value.find((slot) => slot.startAt === selectedStartAt.value) || null)
const hasValidPartySize = computed(() => {
  const partySize = Number(bookingForm.partySize)
  return Number.isInteger(partySize) && partySize >= 1 && partySize <= 20
})
const canProceedToAuth = computed(() => (
  !!selectedSlot.value &&
  hasValidPartySize.value &&
  !loading.value &&
  !dateInputErrorText.value
))
const canProceedToContact = computed(() => !!customer.value && !authBusy.value)
const canSubmit = computed(() => (
  currentStep.value === 3 &&
  !!customer.value &&
  !!selectedSlot.value &&
  hasValidPartySize.value &&
  !!bookingForm.phoneE164.trim() &&
  !submitting.value &&
  !dateInputErrorText.value
))
const enabledAuthProviders = computed(() => context.value?.authProviders || [])
const emailAuthEnabled = computed(() => context.value?.emailAuthEnabled === true)
const hasConfiguredLoginMethod = computed(() => emailAuthEnabled.value || enabledAuthProviders.value.length > 0)
const whatsappContactUrl = computed(() => {
  const digits = (context.value?.store.whatsappBusinessPhoneE164 || '').replace(/\D/g, '')
  return digits ? `https://wa.me/${digits}` : ''
})

onMounted(() => {
  void loadCustomer()
  void loadContext()
})

watch(selectedDate, () => {
  const clampedDate = clampBookingDate(selectedDate.value)
  if (clampedDate !== selectedDate.value) {
    selectedDate.value = clampedDate
    return
  }
  dateInputErrorText.value = ''
  selectedStartAt.value = ''
  selectedPeriodKey.value = ALL_PERIOD_KEY
  void loadContext()
})

watch(maxBookingDate, () => {
  const clampedDate = clampBookingDate(selectedDate.value)
  if (clampedDate !== selectedDate.value) {
    selectedDate.value = clampedDate
  }
})

watch(slotsByPeriod, () => {
  if (selectedPeriodKey.value === ALL_PERIOD_KEY) {
    return
  }
  if (!slotsByPeriod.value.some((group) => group.periodKey === selectedPeriodKey.value)) {
    selectedPeriodKey.value = ALL_PERIOD_KEY
    selectedStartAt.value = ''
  }
})

async function loadCustomer(): Promise<void> {
  try {
    const response = await getCustomerMe()
    customer.value = response.principal
  } catch {
    customer.value = null
  }
}

async function loadContext(): Promise<void> {
  if (!storeId.value) {
    errorText.value = gt('generated.public-booking.046')
    return
  }
  loading.value = true
  errorText.value = ''
  statusText.value = ''
  try {
    context.value = await getPublicBookingContext(storeId.value, selectedDate.value)
    if (!context.value.settings.enabled) {
      errorText.value = gt('generated.public-booking.047')
    }
  } catch (error) {
    context.value = null
    errorText.value = publicBookingErrorText(error)
  } finally {
    loading.value = false
  }
}

async function sendEmailCode(): Promise<void> {
  if (authBusy.value || !emailAuthEnabled.value || !authForm.email.trim()) {
    return
  }
  authBusy.value = true
  errorText.value = ''
  statusText.value = ''
  try {
    const response = await requestCustomerEmailCode(storeId.value, authForm.email)
    authForm.email = response.email
    authForm.devCode = response.devCode || ''
    statusText.value = response.devCode ? gt('generated.public-booking.048') : gt('generated.public-booking.049')
  } catch (error) {
    errorText.value = publicBookingErrorText(error)
  } finally {
    authBusy.value = false
  }
}

async function loginWithEmail(): Promise<void> {
  if (authBusy.value || !emailAuthEnabled.value || !authForm.email.trim() || !authForm.code.trim()) {
    return
  }
  authBusy.value = true
  errorText.value = ''
  try {
    const response = await loginCustomerWithEmail(
      storeId.value,
      authForm.email,
      authForm.code,
      authForm.displayName
    )
    customer.value = response.principal
    statusText.value = gt('generated.public-booking.050')
    currentStep.value = 3
  } catch (error) {
    errorText.value = publicBookingErrorText(error)
  } finally {
    authBusy.value = false
  }
}

async function startOAuthLogin(provider: 'google' | 'facebook'): Promise<void> {
  if (authBusy.value) {
    return
  }
  const clientId = providerClientId(provider)
  if (!clientId) {
    errorText.value = gt('generated.public-booking.051')
    return
  }
  if (provider === 'google') {
    await startGoogleLogin(clientId)
  } else {
    await startFacebookLogin(clientId)
  }
}

async function startGoogleLogin(clientId: string): Promise<void> {
  authBusy.value = true
  errorText.value = ''
  try {
    await loadScript('google-identity-services', 'https://accounts.google.com/gsi/client')
    window.google?.accounts.id.initialize({
      client_id: clientId,
      callback: (response) => {
        if (!response.credential) {
          errorText.value = gt('generated.public-booking.052')
          return
        }
        void completeOAuthLogin('google', response.credential)
      }
    })
    window.google?.accounts.id.prompt()
  } catch {
    errorText.value = gt('generated.public-booking.053')
  } finally {
    authBusy.value = false
  }
}

async function startFacebookLogin(appId: string): Promise<void> {
  authBusy.value = true
  errorText.value = ''
  try {
    await loadFacebookSdk(appId)
    window.FB?.login((response) => {
      const token = response.authResponse?.[`access${'Token'}`] || ''
      if (!token) {
        authBusy.value = false
        errorText.value = gt('generated.public-booking.054')
        return
      }
      void completeOAuthLogin('facebook', token)
    }, { scope: 'email,public_profile' })
  } catch {
    errorText.value = gt('generated.public-booking.055')
    authBusy.value = false
  }
}

async function completeOAuthLogin(provider: 'google' | 'facebook', token: string): Promise<void> {
  authBusy.value = true
  errorText.value = ''
  try {
    const response = await loginCustomerWithOAuth(storeId.value, provider, token)
    customer.value = response.principal
    statusText.value = gt('generated.public-booking.056')
    currentStep.value = 3
  } catch (error) {
    errorText.value = publicBookingErrorText(error)
  } finally {
    authBusy.value = false
  }
}

async function submitBooking(): Promise<void> {
  if (!canSubmit.value || !selectedSlot.value) {
    return
  }
  submitting.value = true
  errorText.value = ''
  statusText.value = ''
  try {
    const response = await createPublicBooking(
      storeId.value,
      `public-${crypto.randomUUID()}`,
      {
        partySize: Number(bookingForm.partySize),
        reservedStartAt: selectedSlot.value.startAt,
        businessDate: selectedDate.value,
        phoneE164: nullableText(bookingForm.phoneE164),
        note: nullableText(bookingForm.note)
      }
    )
    statusText.value = `${gt('generated.public-booking.044')}${response.reservationCode}`
  } catch (error) {
    errorText.value = publicBookingErrorText(error)
  } finally {
    submitting.value = false
  }
}

function selectPeriod(periodKey: string): void {
  selectedPeriodKey.value = periodKey
  if (periodKey === ALL_PERIOD_KEY || !selectedSlot.value) {
    return
  }
  if ((selectedSlot.value.periodKey || 'default') !== periodKey) {
    selectedStartAt.value = ''
  }
}

function goToAuthStep(): void {
  if (canProceedToAuth.value) {
    currentStep.value = 2
  }
}

function goToContactStep(): void {
  if (canProceedToContact.value) {
    currentStep.value = 3
  }
}

function goBackToTimeStep(): void {
  currentStep.value = 1
}

function goBackToAuthStep(): void {
  currentStep.value = 2
}

function publicBookingErrorText(error: unknown): string {
  if (!(error instanceof PublicBookingApiError)) {
    return gt('generated.public-booking.057')
  }
  switch (error.response.error) {
    case 'login_required':
    case 'unauthenticated':
      return gt('generated.public-booking.058')
    case 'booking_disabled':
      return gt('generated.public-booking.059')
    case 'invalid_booking_window':
      return gt('generated.public-booking.060')
    case 'reservation_rejected':
      return gt('generated.public-booking.061')
    case 'code_mismatch':
      return gt('generated.public-booking.062')
    case 'code_expired':
      return gt('generated.public-booking.063')
    case 'email_channel_not_configured':
      return gt('generated.public-booking.064')
    case 'email_delivery_failed':
      return gt('generated.public-booking.065')
    case 'provider_not_configured':
      return gt('generated.public-booking.066')
    case 'provider_token_invalid':
      return gt('generated.public-booking.067')
    default:
      return gt('generated.public-booking.068')
  }
}

function nullableText(value: string): string | null {
  const trimmed = value.trim()
  return trimmed ? trimmed : null
}

function providerClientId(provider: 'google' | 'facebook'): string {
  return enabledAuthProviders.value.find((item) => item.provider === provider)?.clientId || ''
}

function loadScript(id: string, src: string): Promise<void> {
  if (document.getElementById(id)) {
    return Promise.resolve()
  }
  return new Promise((resolve, reject) => {
    const script = document.createElement('script')
    script.id = id
    script.src = src
    script.async = true
    script.defer = true
    script.onload = () => resolve()
    script.onerror = () => reject(new Error('script_load_failed'))
    document.head.appendChild(script)
  })
}

function loadFacebookSdk(appId: string): Promise<void> {
  if (window.FB) {
    window.FB.init({ appId, cookie: true, xfbml: false, version: 'v21.0' })
    return Promise.resolve()
  }
  return new Promise((resolve, reject) => {
    window.fbAsyncInit = () => {
      window.FB?.init({ appId, cookie: true, xfbml: false, version: 'v21.0' })
      resolve()
    }
    loadScript('facebook-jssdk', 'https://connect.facebook.net/en_US/sdk.js').catch(reject)
  })
}

function todayDate(): string {
  const now = new Date()
  return formatIsoDate(now)
}

function addDays(isoDate: string, days: number): string {
  const [year, month, day] = isoDate.split('-').map(Number)
  const date = new Date(year, month - 1, day)
  date.setDate(date.getDate() + Math.max(0, Number(days) || 0))
  return formatIsoDate(date)
}

function formatIsoDate(date: Date): string {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function formatDisplayDate(isoDate: string): string {
  const [year, month, day] = isoDate.split('-')
  return year && month && day ? `${day}-${month}-${year}` : ''
}

function clampBookingDate(isoDate: string): string {
  if (!isoDate) {
    return minBookingDate
  }
  if (isoDate < minBookingDate) {
    dateInputErrorText.value = gt('generated.public-booking.069')
    return minBookingDate
  }
  if (isoDate > maxBookingDate.value) {
    dateInputErrorText.value = gt('generated.public-booking.070')
    return maxBookingDate.value
  }
  return isoDate
}
</script>

<template>
  <main class="public-booking">
    <section class="booking-shell">
      <header class="booking-heading">
        <span>Online Booking</span>
        <h1>{{ context?.store.storeName || gt('generated.public-booking.001') }}</h1>
        <p v-if="context?.store.shareAddress">{{ context.store.shareAddress }}</p>
        <nav
          v-if="
            context?.store.googleMapUrl ||
            context?.store.shareContactPhone ||
            context?.store.shareEmail ||
            context?.store.whatsappBusinessPhoneE164
          "
          class="booking-contact-actions"
          :aria-label="gt('generated.public-booking.002')"
        >
          <a
            v-if="context.store.googleMapUrl"
            :href="context.store.googleMapUrl"
            target="_blank"
            rel="noopener noreferrer"
          > {{ gt('generated.public-booking.003') }} </a>
          <a v-if="context.store.shareContactPhone" :href="`tel:${context.store.shareContactPhone}`">{{ gt('generated.public-booking.004') }}</a>
          <a v-if="context.store.shareEmail" :href="`mailto:${context.store.shareEmail}`">{{ gt('generated.public-booking.005') }}</a>
          <a
            v-if="whatsappContactUrl"
            :href="whatsappContactUrl"
            target="_blank"
            rel="noopener noreferrer"
          >
            WhatsApp
          </a>
        </nav>
      </header>

      <p v-if="errorText" class="alert alert--error" role="alert">{{ errorText }}</p>
      <p v-if="statusText" class="alert alert--success" role="status">{{ statusText }}</p>

      <section v-if="currentStep === 1" class="booking-panel" :aria-label="gt('generated.public-booking.006')">
        <div class="panel-title">
          <span>1</span>
          <strong>{{ gt('generated.public-booking.007') }}</strong>
        </div>

        <label>
          <span>{{ gt('generated.public-booking.008') }}</span>
          <input
            v-model="selectedDate"
            :min="minBookingDate"
            :max="maxBookingDate"
            type="date"
          />
          <small class="date-window-hint">{{ bookingDateWindowText }}</small>
          <small v-if="dateInputErrorText" class="field-error">{{ dateInputErrorText }}</small>
        </label>

        <div class="booking-time-field">
          <span>{{ gt('generated.public-booking.009') }}</span>
          <div
            v-if="periodFilterOptions.length > 1"
            class="booking-period-tabs"
            role="group"
            :aria-label="gt('generated.public-booking.010')"
          >
            <button
              v-for="option in periodFilterOptions"
              :key="option.periodKey"
              class="booking-period-tab"
              :class="{ 'is-selected': selectedPeriodKey === option.periodKey }"
              type="button"
              :aria-pressed="selectedPeriodKey === option.periodKey"
              @click="selectPeriod(option.periodKey)"
            >
              <span>{{ option.displayName }}</span>
              <small>{{ option.count }}</small>
            </button>
          </div>

          <div v-if="loading" class="quiet-line">{{ gt('generated.public-booking.011') }}</div>
          <template v-else>
            <div class="booking-time-slots" role="listbox" :aria-label="gt('generated.public-booking.012')">
              <button
                v-for="slot in filteredSlots"
                :key="slot.startAt"
                type="button"
                class="booking-time-card"
                :class="{ 'is-selected': selectedStartAt === slot.startAt }"
                :aria-selected="selectedStartAt === slot.startAt"
                @click="selectedStartAt = slot.startAt"
              >
                <strong>{{ slot.localTime.slice(0, 5) }}</strong>
                <small>{{ slot.displayName }}{{ slot.nextDay ? gt('generated.public-booking.013') : '' }}</small>
              </button>
            </div>
            <p v-if="!filteredSlots.length" class="quiet-line">{{ gt('generated.public-booking.014') }}</p>
          </template>
        </div>

        <label>
          <span>{{ gt('generated.public-booking.015') }}</span>
          <input v-model.number="bookingForm.partySize" min="1" max="20" type="number" />
        </label>

        <button class="submit-button" type="button" :disabled="!canProceedToAuth" @click="goToAuthStep"> {{ gt('generated.public-booking.016') }} </button>
      </section>

      <section v-else-if="currentStep === 2" class="booking-panel" :aria-label="gt('generated.public-booking.017')">
        <div class="panel-title">
          <span>2</span>
          <strong>{{ gt('generated.public-booking.018') }}</strong>
        </div>

        <div v-if="customer" class="customer-chip">
          <span>{{ customer.email }}</span>
          <strong>{{ gt('generated.public-booking.019') }}</strong>
        </div>

        <template v-else>
          <p v-if="!hasConfiguredLoginMethod" class="quiet-line">{{ gt('generated.public-booking.020') }}</p>

          <section v-if="emailAuthEnabled" class="auth-method-panel" :aria-label="gt('generated.public-booking.021')">
            <strong>{{ gt('generated.public-booking.022') }}</strong>
            <p class="quiet-line">{{ gt('generated.public-booking.023') }}</p>
            <div class="auth-grid">
              <label>
                <span>{{ gt('generated.public-booking.024') }}</span>
                <input v-model="authForm.email" autocomplete="email" type="email" />
              </label>
              <label>
                <span>{{ gt('generated.public-booking.025') }}</span>
                <input v-model="authForm.displayName" autocomplete="name" />
              </label>
            </div>
            <button class="secondary-button" type="button" :disabled="authBusy" @click="sendEmailCode"> {{ gt('generated.public-booking.026') }} </button>
            <label>
              <span>{{ gt('generated.public-booking.027') }}</span>
              <input v-model="authForm.code" inputmode="numeric" />
            </label>
            <p v-if="authForm.devCode" class="quiet-line">{{ gt('generated.public-booking.028') }} {{ authForm.devCode }}</p>
            <button class="primary-button" type="button" :disabled="authBusy" @click="loginWithEmail"> {{ gt('generated.public-booking.029') }} </button>
          </section>

          <div v-if="enabledAuthProviders.length" class="auth-actions" :aria-label="gt('generated.public-booking.030')">
            <button
              v-if="providerClientId('google')"
              type="button"
              :disabled="authBusy"
              @click="startOAuthLogin('google')"
            > {{ gt('generated.public-booking.031') }} </button>
            <button
              v-if="providerClientId('facebook')"
              type="button"
              :disabled="authBusy"
              @click="startOAuthLogin('facebook')"
            > {{ gt('generated.public-booking.032') }} </button>
          </div>
        </template>

        <div class="step-actions">
          <button class="secondary-button" type="button" @click="goBackToTimeStep">{{ gt('generated.public-booking.033') }}</button>
          <button class="submit-button" type="button" :disabled="!canProceedToContact" @click="goToContactStep"> {{ gt('generated.public-booking.034') }} </button>
        </div>
      </section>

      <form v-else class="booking-panel" :aria-label="gt('generated.public-booking.035')" @submit.prevent="submitBooking">
        <div class="panel-title">
          <span>3</span>
          <strong>{{ gt('generated.public-booking.036') }}</strong>
        </div>

        <label>
          <span>{{ gt('generated.public-booking.037') }}</span>
          <input v-model="bookingForm.phoneE164" inputmode="tel" placeholder="+6591234567" />
        </label>

        <label>
          <span>{{ gt('generated.public-booking.038') }}</span>
          <textarea v-model="bookingForm.note" rows="3"></textarea>
        </label>

        <div class="step-actions">
          <button class="secondary-button" type="button" @click="goBackToAuthStep">{{ gt('generated.public-booking.039') }}</button>
          <button class="submit-button" type="submit" :disabled="!canSubmit">
            {{ submitting ? gt('generated.public-booking.040') : gt('generated.public-booking.041') }}
          </button>
        </div>
      </form>
    </section>
  </main>
</template>

<style scoped>
.public-booking {
  background: #f5f7f8;
  color: #102033;
  min-height: 100dvh;
  padding: 18px;
}

.booking-shell {
  display: grid;
  gap: 14px;
  margin: 0 auto;
  max-width: 680px;
}

.booking-heading {
  background: #ffffff;
  border: 1px solid #dbe3ea;
  border-radius: 8px;
  display: grid;
  gap: 6px;
  padding: 18px;
}

.booking-heading span,
.panel-title span,
label span,
.quiet-line {
  color: #64748b;
  font-size: 13px;
  font-weight: 800;
}

.booking-heading h1 {
  color: #0f172a;
  font-size: 24px;
  line-height: 1.18;
  margin: 0;
  overflow-wrap: anywhere;
}

.booking-heading p,
.alert {
  margin: 0;
}

.booking-contact-actions {
  display: grid;
  gap: 8px;
  grid-template-columns: repeat(auto-fit, minmax(120px, 1fr));
}

.booking-contact-actions a {
  align-items: center;
  background: #ffffff;
  border: 1px solid #99f6e4;
  border-radius: 6px;
  color: #0f766e;
  display: inline-flex;
  font-size: 13px;
  font-weight: 850;
  justify-content: center;
  min-height: 38px;
  padding: 0 10px;
  text-decoration: none;
}

.booking-panel,
.alert {
  background: #ffffff;
  border: 1px solid #dbe3ea;
  border-radius: 8px;
  padding: 16px;
}

.booking-panel {
  display: grid;
  gap: 14px;
}

.alert--error {
  background: #fff1f2;
  border-color: #fecaca;
  color: #991b1b;
}

.alert--success {
  background: #f0fdf4;
  border-color: #bbf7d0;
  color: #166534;
}

.panel-title {
  align-items: center;
  display: flex;
  gap: 10px;
}

.panel-title span {
  align-items: center;
  background: #0f766e;
  border-radius: 999px;
  color: #ffffff;
  display: inline-flex;
  height: 26px;
  justify-content: center;
  width: 26px;
}

.panel-title strong {
  color: #0f172a;
}

label {
  display: grid;
  gap: 7px;
}

input,
textarea,
button {
  border-radius: 6px;
  box-sizing: border-box;
  font: inherit;
}

input,
textarea {
  background: #ffffff;
  border: 1px solid #cbd5e1;
  color: #0f172a;
  min-height: 40px;
  padding: 9px 10px;
  width: 100%;
}

textarea {
  resize: vertical;
}

.field-error {
  color: #b91c1c;
  font-size: 12px;
  font-weight: 800;
}

.date-window-hint {
  color: #64748b;
  font-size: 12px;
  font-weight: 800;
}

.booking-time-field,
.auth-method-panel,
.customer-chip {
  display: grid;
  gap: 10px;
}

.booking-time-field > span {
  color: #64748b;
  font-size: 13px;
  font-weight: 800;
}

.booking-period-tabs {
  background: #f8fafc;
  border: 1px solid #dbe3ee;
  border-radius: 8px;
  display: grid;
  gap: 4px;
  grid-template-columns: repeat(auto-fit, minmax(72px, 1fr));
  padding: 4px;
}

.booking-period-tab {
  align-items: center;
  background: transparent;
  border: 1px solid transparent;
  border-radius: 6px;
  color: #334155;
  display: flex;
  gap: 5px;
  justify-content: center;
  min-height: 30px;
  min-width: 0;
  padding: 0 8px;
}

.booking-period-tab span {
  font-size: 13px;
  font-weight: 950;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.booking-period-tab small {
  color: #64748b;
  font-size: 12px;
  font-weight: 900;
}

.booking-period-tab.is-selected {
  background: #ffffff;
  border-color: #fdba74;
  box-shadow: 0 1px 3px rgba(15, 23, 42, 0.08);
  color: #c2410c;
}

.booking-period-tab.is-selected small {
  color: #ea580c;
}

.booking-time-slots {
  display: grid;
  gap: 8px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  max-height: 178px;
  overflow-y: auto;
}

.booking-time-card {
  align-content: center;
  background: #ffffff;
  border: 1px solid #d8e0eb;
  border-radius: 8px;
  color: #0f172a;
  display: grid;
  gap: 2px;
  min-height: 48px;
  padding: 6px 10px;
  text-align: left;
}

.booking-time-card strong,
.booking-time-card small {
  overflow-wrap: anywhere;
}

.booking-time-card strong {
  font-size: 15px;
  font-weight: 950;
}

.booking-time-card small {
  color: #64748b;
  font-size: 12px;
  font-weight: 850;
}

.booking-time-card.is-selected {
  background: #fff7ed;
  border-color: #f97316;
}

.booking-time-card.is-selected small {
  color: #c2410c;
}

.auth-actions button,
.primary-button,
.secondary-button,
.submit-button {
  border: 1px solid #cbd5e1;
  color: #334155;
  font-weight: 850;
  min-height: 40px;
}

.auth-grid,
.form-grid {
  display: grid;
  gap: 10px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.auth-actions {
  display: grid;
  gap: 8px;
  grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
}

.auth-method-panel {
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 12px;
}

.auth-method-panel > strong {
  color: #0f172a;
  font-size: 15px;
}

.auth-actions button,
.primary-button,
.secondary-button {
  background: #ffffff;
}

.step-actions {
  display: grid;
  gap: 8px;
  grid-template-columns: minmax(110px, 0.55fr) minmax(0, 1fr);
}

.customer-chip {
  background: #ecfdf5;
  border: 1px solid #99f6e4;
  border-radius: 8px;
  padding: 12px;
}

.customer-chip span {
  color: #0f766e;
  font-weight: 800;
}

.customer-chip strong {
  color: #0f766e;
}

.submit-button {
  background: #f97316;
  border-color: #f97316;
  color: #ffffff;
}

button:disabled {
  cursor: default;
  opacity: 0.55;
}

button:not(:disabled) {
  cursor: pointer;
}

@media (max-width: 560px) {
  .public-booking {
    padding: 12px;
  }

  .auth-grid,
  .form-grid,
  .auth-actions,
  .step-actions {
    grid-template-columns: 1fr;
  }
}
</style>
