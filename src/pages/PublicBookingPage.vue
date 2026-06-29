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

const route = useRoute()
const loading = ref(false)
const submitting = ref(false)
const authBusy = ref(false)
const errorText = ref('')
const statusText = ref('')
const context = ref<PublicBookingContextResponse | null>(null)
const selectedDate = ref(todayDate())
const selectedStartAt = ref('')
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

const storeId = computed(() => String(route.params.storeId || '').trim())
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
const selectedSlot = computed(() => selectableSlots.value.find((slot) => slot.startAt === selectedStartAt.value) || null)
const canSubmit = computed(() => !!customer.value && !!selectedSlot.value && !submitting.value)
const enabledAuthProviders = computed(() => context.value?.authProviders || [])

onMounted(() => {
  void loadCustomer()
  void loadContext()
})

watch(selectedDate, () => {
  selectedStartAt.value = ''
  void loadContext()
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
    errorText.value = '门店链接无效'
    return
  }
  loading.value = true
  errorText.value = ''
  statusText.value = ''
  try {
    context.value = await getPublicBookingContext(storeId.value, selectedDate.value)
    if (!context.value.settings.enabled) {
      errorText.value = '门店暂未开放公网预约'
    }
  } catch (error) {
    context.value = null
    errorText.value = publicBookingErrorText(error)
  } finally {
    loading.value = false
  }
}

async function sendEmailCode(): Promise<void> {
  if (authBusy.value || !authForm.email.trim()) {
    return
  }
  authBusy.value = true
  errorText.value = ''
  statusText.value = ''
  try {
    const response = await requestCustomerEmailCode(storeId.value, authForm.email)
    authForm.email = response.email
    authForm.devCode = response.devCode || ''
    statusText.value = response.devCode ? '验证码已生成' : '验证码已发送'
  } catch (error) {
    errorText.value = publicBookingErrorText(error)
  } finally {
    authBusy.value = false
  }
}

async function loginWithEmail(): Promise<void> {
  if (authBusy.value || !authForm.email.trim() || !authForm.code.trim()) {
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
    statusText.value = '已登录'
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
    errorText.value = '门店尚未开放该登录方式'
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
          errorText.value = '第三方授权已失效'
          return
        }
        void completeOAuthLogin('google', response.credential)
      }
    })
    window.google?.accounts.id.prompt()
  } catch {
    errorText.value = '第三方授权已失效'
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
        errorText.value = '第三方授权已失效'
        return
      }
      void completeOAuthLogin('facebook', token)
    }, { scope: 'email,public_profile' })
  } catch {
    errorText.value = '第三方授权已失效'
    authBusy.value = false
  }
}

async function completeOAuthLogin(provider: 'google' | 'facebook', token: string): Promise<void> {
  authBusy.value = true
  errorText.value = ''
  try {
    const response = await loginCustomerWithOAuth(storeId.value, provider, token)
    customer.value = response.principal
    statusText.value = '已登录'
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
    statusText.value = `预约成功，编号 ${response.reservationCode}`
  } catch (error) {
    errorText.value = publicBookingErrorText(error)
  } finally {
    submitting.value = false
  }
}

function publicBookingErrorText(error: unknown): string {
  if (!(error instanceof PublicBookingApiError)) {
    return '操作失败'
  }
  switch (error.response.error) {
    case 'login_required':
    case 'unauthenticated':
      return '请先邮箱登录'
    case 'booking_disabled':
      return '门店暂未开放公网预约'
    case 'invalid_booking_window':
      return '该时间暂不可预约'
    case 'reservation_rejected':
      return '该时段预约名额已满'
    case 'code_mismatch':
      return '验证码不正确'
    case 'code_expired':
      return '验证码已过期'
    case 'email_channel_not_configured':
      return '门店尚未配置邮件服务'
    case 'email_delivery_failed':
      return '验证码发送失败'
    case 'provider_not_configured':
      return '门店尚未开放该登录方式'
    case 'provider_token_invalid':
      return '第三方授权已失效'
    default:
      return '操作失败'
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
  return new Date().toISOString().slice(0, 10)
}
</script>

<template>
  <main class="public-booking">
    <section class="booking-shell">
      <header class="booking-heading">
        <span>Online Booking</span>
        <h1>{{ context?.store.storeName || '餐厅预约' }}</h1>
        <p v-if="context?.store.shareAddress">{{ context.store.shareAddress }}</p>
      </header>

      <p v-if="errorText" class="alert alert--error" role="alert">{{ errorText }}</p>
      <p v-if="statusText" class="alert alert--success" role="status">{{ statusText }}</p>

      <section class="booking-panel" aria-label="预约时间">
        <div class="panel-title">
          <span>1</span>
          <strong>选择日期与餐段</strong>
        </div>

        <label>
          <span>日期</span>
          <input v-model="selectedDate" type="date" />
        </label>

        <div v-if="loading" class="quiet-line">加载中</div>
        <div v-else class="slot-groups">
          <section v-for="group in slotsByPeriod" :key="group.periodKey" class="slot-group">
            <strong>{{ group.displayName }}</strong>
            <div class="slot-grid">
              <button
                v-for="slot in group.slots"
                :key="slot.startAt"
                type="button"
                :class="{ active: selectedStartAt === slot.startAt }"
                @click="selectedStartAt = slot.startAt"
              >
                {{ slot.localTime.slice(0, 5) }}
              </button>
            </div>
          </section>
          <p v-if="!selectableSlots.length" class="quiet-line">当天暂无可预约时段</p>
        </div>
      </section>

      <section class="booking-panel" aria-label="登录">
        <div class="panel-title">
          <span>2</span>
          <strong>邮箱登录</strong>
        </div>

        <div v-if="customer" class="customer-chip">
          <span>{{ customer.email }}</span>
          <strong>已登录</strong>
        </div>

        <template v-else>
          <div class="auth-grid">
            <label>
              <span>邮箱</span>
              <input v-model="authForm.email" autocomplete="email" type="email" />
            </label>
            <label>
              <span>姓名</span>
              <input v-model="authForm.displayName" autocomplete="name" />
            </label>
          </div>
          <div class="auth-actions">
            <button type="button" :disabled="authBusy" @click="sendEmailCode">发送验证码</button>
            <button
              v-if="providerClientId('google')"
              type="button"
              :disabled="authBusy"
              @click="startOAuthLogin('google')"
            >
              Google
            </button>
            <button
              v-if="providerClientId('facebook')"
              type="button"
              :disabled="authBusy"
              @click="startOAuthLogin('facebook')"
            >
              Facebook
            </button>
          </div>
          <label>
            <span>验证码</span>
            <input v-model="authForm.code" inputmode="numeric" />
          </label>
          <p v-if="authForm.devCode" class="quiet-line">验证码 {{ authForm.devCode }}</p>
          <button class="primary-button" type="button" :disabled="authBusy" @click="loginWithEmail">
            登录
          </button>
        </template>
      </section>

      <form class="booking-panel" aria-label="提交预约" @submit.prevent="submitBooking">
        <div class="panel-title">
          <span>3</span>
          <strong>填写资料</strong>
        </div>

        <div class="form-grid">
          <label>
            <span>人数</span>
            <input v-model.number="bookingForm.partySize" min="1" max="20" type="number" />
          </label>
          <label>
            <span>手机号</span>
            <input v-model="bookingForm.phoneE164" inputmode="tel" placeholder="+6591234567" />
          </label>
        </div>

        <label>
          <span>备注</span>
          <textarea v-model="bookingForm.note" rows="3"></textarea>
        </label>

        <button class="submit-button" type="submit" :disabled="!canSubmit">
          {{ submitting ? '提交中' : '提交预约' }}
        </button>
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

.slot-groups,
.slot-group,
.customer-chip {
  display: grid;
  gap: 10px;
}

.slot-group strong {
  color: #334155;
}

.slot-grid {
  display: grid;
  gap: 8px;
  grid-template-columns: repeat(auto-fit, minmax(82px, 1fr));
}

.slot-grid button,
.auth-actions button,
.primary-button,
.submit-button {
  border: 1px solid #cbd5e1;
  color: #334155;
  font-weight: 850;
  min-height: 40px;
}

.slot-grid button {
  background: #ffffff;
}

.slot-grid button.active {
  background: #0f766e;
  border-color: #0f766e;
  color: #ffffff;
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
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.auth-actions button,
.primary-button {
  background: #ffffff;
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
  .auth-actions {
    grid-template-columns: 1fr;
  }
}
</style>
