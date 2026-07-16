<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'

import { AuthApiError, createSliderCaptcha } from '../api/authApi'
import PasswordInput from '../components/common/PasswordInput.vue'
import { useAuthSessionStore } from '../stores/authSession'
import type { AuthLoginEntry, AuthUser, SliderCaptchaChallenge } from '../types/auth'
import { resolveLoginHostContext } from '../utils/hostContext'

type LoginEntryId = 'platform-admin' | 'tenant-admin' | 'tenant-staff'

interface LoginEntry {
  id: LoginEntryId
  loginEntry: AuthLoginEntry
  labelKey: string
  titleKey: string
  descriptionKey: string
  accountLabelKey: string
  loginUsername: string
  tenantCode?: string
  employeeUsername?: string
  targetHintKey: string
}

interface RememberedLoginAccount {
  username: string
  tenantCode?: string
}

const loginEntries: LoginEntry[] = [
  {
    id: 'platform-admin',
    loginEntry: 'platform_admin',
    labelKey: 'login.entries.platformAdmin.tab',
    titleKey: 'login.entries.platformAdmin.title',
    descriptionKey: 'login.entries.platformAdmin.description',
    accountLabelKey: 'login.entries.platformAdmin.accountLabel',
    loginUsername: 'sysadmin',
    targetHintKey: 'login.entries.platformAdmin.targetHint'
  },
  {
    id: 'tenant-admin',
    loginEntry: 'tenant_admin',
    labelKey: 'login.entries.tenantAdmin.tab',
    titleKey: 'login.entries.tenantAdmin.title',
    descriptionKey: 'login.entries.tenantAdmin.description',
    accountLabelKey: 'login.entries.tenantAdmin.accountLabel',
    loginUsername: '20000000',
    tenantCode: '20000000',
    targetHintKey: 'login.entries.tenantAdmin.targetHint'
  },
  {
    id: 'tenant-staff',
    loginEntry: 'staff',
    labelKey: 'login.entries.tenantStaff.tab',
    titleKey: 'login.entries.tenantStaff.title',
    descriptionKey: 'login.entries.tenantStaff.description',
    accountLabelKey: 'login.entries.tenantStaff.accountLabel',
    loginUsername: '1000',
    tenantCode: '20000000',
    employeeUsername: '1000',
    targetHintKey: 'login.entries.tenantStaff.targetHint'
  }
]

const route = useRoute()
const router = useRouter()
const auth = useAuthSessionStore()
const { t } = useI18n()
const missingStoreScopeText = computed(() => t('login.errors.missingStoreScope'))
const hostContext = resolveLoginHostContext()
const initialEntry = loginEntries.find(entry => entry.id === initialLoginEntryId()) ?? loginEntries[0]

const selectedEntryId = ref<LoginEntryId>(initialEntry.id)
const username = ref(defaultLoginUsername(initialEntry))
const employeeUsername = ref(defaultEmployeeUsername(initialEntry))
const tenantCode = ref(hostContext.kind === 'tenant' ? hostContext.tenantCode : (initialEntry.tenantCode ?? '20000000'))
const password = ref('')
const captcha = ref<SliderCaptchaChallenge | null>(null)
const captchaX = ref(0)
const sliderCanvas = ref<HTMLElement | null>(null)
const canvasWidth = ref(320)
const loadingCaptcha = ref(false)
const submitting = ref(false)
const draggingSlider = ref(false)
const errorText = ref('')
const rememberAccount = ref(false)
let sliderResizeObserver: ResizeObserver | null = null
let activeSliderPointerId: number | null = null
let sliderDragOffsetX = 0

const availableLoginEntries = computed(() => {
  if (hostContext.kind === 'platform') {
    return loginEntries.filter(entry => entry.id === 'platform-admin')
  }
  if (hostContext.kind === 'tenant') {
    return loginEntries.filter(entry => entry.id === 'tenant-admin' || entry.id === 'tenant-staff')
  }
  return loginEntries
})
const selectedEntry = computed(() =>
  availableLoginEntries.value.find(entry => entry.id === selectedEntryId.value) ?? availableLoginEntries.value[0] ?? loginEntries[0]
)
const resolvedTenantCode = computed(() => (
  hostContext.kind === 'tenant' ? hostContext.tenantCode : tenantCode.value.trim()
))
const selectedEntryTargetParams = computed(() => ({
  tenantCode: resolvedTenantCode.value || selectedEntry.value.tenantCode || tenantCode.value,
  employeeUsername: selectedEntry.value.employeeUsername ?? employeeUsername.value
}))
const isStaffEntry = computed(() => selectedEntry.value.id === 'tenant-staff')
const entryTargetVisible = computed(() => hostContext.kind !== 'tenant')
const tenantCodeVisible = computed(() => isStaffEntry.value && hostContext.kind !== 'tenant')
const tenantCodeFieldVisible = computed(() => selectedEntry.value.id !== 'platform-admin' && hostContext.kind !== 'tenant')
const loginPayloadUsername = computed(() =>
  isStaffEntry.value ? employeeUsername.value.trim() : username.value.trim()
)

const sliderMax = computed(() => {
  if (!captcha.value) {
    return 0
  }
  return Math.max(0, captcha.value.imageWidth - captcha.value.pieceSize)
})

const sliderScale = computed(() => {
  if (!captcha.value || captcha.value.imageWidth <= 0) {
    return 1
  }
  return canvasWidth.value / captcha.value.imageWidth
})

const pieceStyle = computed(() => ({
  width: `${(captcha.value?.pieceSize ?? 44) * sliderScale.value}px`,
  height: `${(captcha.value?.pieceSize ?? 44) * sliderScale.value}px`,
  transform: `translate(${captchaX.value * sliderScale.value}px, ${(captcha.value?.pieceY ?? 0) * sliderScale.value}px)`
}))

onMounted(() => {
  observeSliderCanvas()
  applyRememberedAccount(selectedEntry.value)
  void refreshSlider().then(showRouteStoreScopeMessage)
})

onBeforeUnmount(() => {
  sliderResizeObserver?.disconnect()
})

function selectEntry(entry: LoginEntry): void {
  selectedEntryId.value = entry.id
  username.value = defaultLoginUsername(entry)
  employeeUsername.value = defaultEmployeeUsername(entry)
  tenantCode.value = hostContext.kind === 'tenant' ? hostContext.tenantCode : (entry.tenantCode ?? '')
  password.value = ''
  errorText.value = ''
  applyRememberedAccount(entry)
}

async function refreshSlider(): Promise<void> {
  resetSliderDrag()
  loadingCaptcha.value = true
  errorText.value = ''
  try {
    const response = await createSliderCaptcha()
    captcha.value = response.challenge
    captchaX.value = 0
    await nextTick()
    updateCanvasWidth()
  } catch {
    errorText.value = t('login.errors.captchaLoadFailed')
  } finally {
    loadingCaptcha.value = false
  }
}

function observeSliderCanvas(): void {
  updateCanvasWidth()

  if (typeof ResizeObserver === 'undefined' || !sliderCanvas.value) {
    return
  }

  sliderResizeObserver = new ResizeObserver(updateCanvasWidth)
  sliderResizeObserver.observe(sliderCanvas.value)
}

function updateCanvasWidth(): void {
  canvasWidth.value = sliderCanvas.value?.clientWidth || captcha.value?.imageWidth || 320
}

function startSliderDrag(event: PointerEvent): void {
  if (!isSliderInteractive()) {
    return
  }

  const handle = event.currentTarget as HTMLElement
  sliderDragOffsetX = event.clientX - handle.getBoundingClientRect().left
  activeSliderPointerId = event.pointerId
  draggingSlider.value = true
  handle.setPointerCapture(event.pointerId)
  setCaptchaXFromClient(event.clientX)
  event.preventDefault()
}

function moveSliderDrag(event: PointerEvent): void {
  if (!draggingSlider.value || activeSliderPointerId !== event.pointerId) {
    return
  }

  setCaptchaXFromClient(event.clientX)
  event.preventDefault()
}

function stopSliderDrag(event: PointerEvent): void {
  if (activeSliderPointerId !== event.pointerId) {
    return
  }

  const handle = event.currentTarget as HTMLElement
  if (handle.hasPointerCapture(event.pointerId)) {
    handle.releasePointerCapture(event.pointerId)
  }
  resetSliderDrag()
  event.preventDefault()
}

function handleSliderKeydown(event: KeyboardEvent): void {
  if (!isSliderInteractive()) {
    return
  }

  const step = event.shiftKey ? 10 : 2
  let nextX: number | null = null
  if (event.key === 'ArrowLeft' || event.key === 'ArrowDown') {
    nextX = captchaX.value - step
  } else if (event.key === 'ArrowRight' || event.key === 'ArrowUp') {
    nextX = captchaX.value + step
  } else if (event.key === 'Home') {
    nextX = 0
  } else if (event.key === 'End') {
    nextX = sliderMax.value
  }

  if (nextX === null) {
    return
  }

  setCaptchaX(nextX)
  event.preventDefault()
}

function setCaptchaXFromClient(clientX: number): void {
  const canvasRect = sliderCanvas.value?.getBoundingClientRect()
  if (!canvasRect || sliderScale.value <= 0) {
    return
  }

  setCaptchaX((clientX - canvasRect.left - sliderDragOffsetX) / sliderScale.value)
}

function setCaptchaX(nextX: number): void {
  captchaX.value = Math.min(sliderMax.value, Math.max(0, Math.round(nextX)))
}

function resetSliderDrag(): void {
  draggingSlider.value = false
  activeSliderPointerId = null
  sliderDragOffsetX = 0
}

function isSliderInteractive(): boolean {
  return captcha.value !== null && !loadingCaptcha.value && !submitting.value
}

function showRouteStoreScopeMessage(): void {
  if (route.query.storeScope === 'missing') {
    errorText.value = missingStoreScopeText.value
  }
}

async function submitLogin(): Promise<void> {
  if (!captcha.value || submitting.value || !loginPayloadUsername.value) {
    return
  }

  submitting.value = true
  errorText.value = ''

  try {
    const loginSession = await auth.loginWithPassword({
      username: loginPayloadUsername.value,
      password: password.value,
      captchaId: captcha.value.challengeId,
      captchaX: Math.round(captchaX.value),
      loginEntry: authLoginEntry(selectedEntry.value),
      tenantCode: selectedEntry.value.id === 'platform-admin' ? null : (resolvedTenantCode.value || null)
    })
    persistRememberedAccount()
    await continueAfterLogin(loginSession.user, loginSession.entryStoreId)
  } catch (error) {
    errorText.value = loginErrorText(error)
    await refreshSlider()
  } finally {
    submitting.value = false
  }
}

async function continueAfterLogin(user: AuthUser, entryStoreId: string | null): Promise<void> {
  if (typeof route.query.redirect === 'string') {
    await router.replace(route.query.redirect)
    return
  }

  if (selectedEntry.value.id === 'platform-admin' && user.roles.includes('platform_admin')) {
    await router.replace(auth.platformHomeRoute)
    return
  }

  if (!auth.isPlatformAdmin && user.storeIds.length === 0) {
    await stopMissingStoreScopeLogin()
    return
  }

  const storeId = preferredLoginStoreId(user, entryStoreId)
  if (selectedEntry.value.id === 'tenant-admin' && auth.isTenantAdmin) {
    await router.replace(tenantAdminStoreRoute(storeId))
    return
  }

  if (isStaffEntry.value) {
    await router.replace(storeRoute(storeId))
    return
  }

  await router.replace(auth.defaultHomeRoute)
}

async function stopMissingStoreScopeLogin(): Promise<void> {
  await auth.logoutCurrentUser()
  await refreshSlider()
  errorText.value = missingStoreScopeText.value
}

function preferredLoginStoreId(user: AuthUser, entryStoreId: string | null): string {
  if (entryStoreId && user.storeIds.includes(entryStoreId)) {
    return entryStoreId
  }
  return user.defaultStoreId && user.storeIds.includes(user.defaultStoreId)
    ? user.defaultStoreId
    : (user.storeIds[0] ?? '')
}

function storeRoute(storeId: string): string {
  return `/stores/${storeId}/staff`
}

function tenantAdminStoreRoute(storeId: string): string {
  return `/stores/${storeId}/admin/profile`
}

function initialLoginEntryId(): LoginEntryId {
  if (hostContext.kind === 'platform') {
    return 'platform-admin'
  }
  if (hostContext.kind === 'tenant') {
    return 'tenant-admin'
  }
  return 'tenant-staff'
}

function defaultLoginUsername(entry: LoginEntry): string {
  return hostContext.kind === 'legacy' ? entry.loginUsername : ''
}

function defaultEmployeeUsername(entry: LoginEntry): string {
  return hostContext.kind === 'legacy' ? (entry.employeeUsername ?? entry.loginUsername) : ''
}

function authLoginEntry(entry: LoginEntry): AuthLoginEntry {
  return entry.loginEntry
}

function rememberAccountStorageKey(entryId = selectedEntryId.value): string {
  return `rpb.login.account.v1:${hostContext.storageScope}:${entryId}`
}

function applyRememberedAccount(entry: LoginEntry): void {
  const remembered = readRememberedAccount(entry.id)
  rememberAccount.value = remembered !== null
  if (!remembered) {
    return
  }
  if (entry.id === 'tenant-staff') {
    employeeUsername.value = remembered.username
  } else {
    username.value = remembered.username
  }
  if (hostContext.kind !== 'tenant' && remembered.tenantCode) {
    tenantCode.value = remembered.tenantCode
  }
}

function persistRememberedAccount(): void {
  const key = rememberAccountStorageKey()
  try {
    if (!rememberAccount.value) {
      window.localStorage.removeItem(key)
      return
    }
    const remembered: RememberedLoginAccount = {
      username: loginPayloadUsername.value,
      tenantCode: selectedEntry.value.id === 'platform-admin' ? undefined : (resolvedTenantCode.value || undefined)
    }
    window.localStorage.setItem(key, JSON.stringify(remembered))
  } catch {
    // Login should not fail when local storage is unavailable.
  }
}

function readRememberedAccount(entryId: LoginEntryId): RememberedLoginAccount | null {
  try {
    const raw = window.localStorage.getItem(rememberAccountStorageKey(entryId))
    if (!raw) {
      return null
    }
    const parsed = JSON.parse(raw) as Partial<RememberedLoginAccount>
    return typeof parsed.username === 'string' && parsed.username.trim()
      ? { username: parsed.username.trim(), tenantCode: parsed.tenantCode?.trim() }
      : null
  } catch {
    return null
  }
}

function loginErrorText(error: unknown): string {
  if (!(error instanceof AuthApiError)) {
    return t('login.errors.loginFailed')
  }

  const code = error.response.error.code
  if (code === 'CAPTCHA_MISMATCH' || code === 'CAPTCHA_EXPIRED' || code === 'CAPTCHA_REQUIRED') {
    return t('login.errors.captchaMismatch')
  }
  if (code === 'PASSWORD_POLICY_VIOLATION') {
    return t('login.passwordPolicy')
  }
  if (code === 'INVALID_CREDENTIALS') {
    return t('login.errors.invalidCredentials')
  }
  return t('login.errors.loginFailed')
}
</script>

<template>
  <main class="login-shell">
    <section class="login-panel" :aria-label="t('login.shellAria')">
      <div class="login-heading">
        <p class="login-kicker">RPB</p>
        <h1>{{ t('login.heading') }}</h1>
      </div>

      <div v-if="availableLoginEntries.length > 1" class="entry-tabs" role="tablist" :aria-label="t('login.entryTabAria')">
        <button
          v-for="entry in availableLoginEntries"
          :key="entry.id"
          type="button"
          class="entry-tab"
          :class="{ 'entry-tab--active': entry.id === selectedEntryId }"
          :aria-selected="entry.id === selectedEntryId"
          role="tab"
          @click="selectEntry(entry)"
        >
          <span>{{ t(entry.labelKey) }}</span>
        </button>
      </div>

      <div class="entry-summary" :data-entry="selectedEntry.id">
        <p class="entry-title">{{ t(selectedEntry.titleKey) }}</p>
        <p class="entry-description">{{ t(selectedEntry.descriptionKey) }}</p>
        <p v-if="entryTargetVisible" class="entry-target">{{ t(selectedEntry.targetHintKey, selectedEntryTargetParams) }}</p>
      </div>

      <form class="login-form" @submit.prevent="submitLogin">
        <div v-if="isStaffEntry" class="field-grid" :class="{ 'field-grid--split': tenantCodeVisible }">
          <label v-if="tenantCodeVisible" class="login-field">
            <span>{{ t('login.fields.tenantCode') }}</span>
            <input v-model.trim="tenantCode" name="tenantCode" autocomplete="organization" inputmode="numeric" required />
          </label>

          <label class="login-field">
            <span>{{ t('login.fields.employeeUsername') }}</span>
            <input v-model.trim="employeeUsername" name="employeeUsername" autocomplete="username" inputmode="text" required />
          </label>
        </div>

        <div v-else-if="tenantCodeFieldVisible" class="field-grid field-grid--split">
          <label class="login-field">
            <span>{{ t('login.fields.tenantCode') }}</span>
            <input v-model.trim="tenantCode" name="tenantCode" autocomplete="organization" inputmode="numeric" required />
          </label>

          <label class="login-field">
            <span>{{ t(selectedEntry.accountLabelKey) }}</span>
            <input v-model.trim="username" name="username" autocomplete="username" inputmode="text" required />
          </label>
        </div>

        <label v-else class="login-field">
          <span>{{ t(selectedEntry.accountLabelKey) }}</span>
          <input v-model.trim="username" name="username" autocomplete="username" inputmode="text" required />
        </label>

        <label class="login-field">
          <span>{{ t('login.fields.password') }}</span>
          <PasswordInput
            v-model="password"
            name="password"
            autocomplete="current-password"
            maxlength="6"
            required
          />
          <small>{{ t('login.passwordPolicy') }}</small>
        </label>

        <label class="remember-row">
          <input v-model="rememberAccount" type="checkbox" />
          <span>{{ t('login.remember.account') }}</span>
        </label>

        <div v-if="isStaffEntry" class="store-preview">
          <span>{{ t('login.store.authorized') }}</span>
          <strong>{{ t('login.store.authorizedDescription') }}</strong>
        </div>

        <div class="slider-block">
          <div ref="sliderCanvas" class="slider-canvas" :aria-busy="loadingCaptcha">
            <img v-if="captcha" class="slider-bg" :src="captcha.backgroundImage" alt="" />
            <div
              v-if="captcha"
              class="slider-piece-handle"
              :class="{ 'slider-piece-handle--dragging': draggingSlider }"
              :style="pieceStyle"
              role="slider"
              :aria-label="t('login.captcha.aria')"
              aria-valuemin="0"
              :aria-valuemax="sliderMax"
              :aria-valuenow="Math.round(captchaX)"
              :aria-disabled="loadingCaptcha || submitting"
              :tabindex="loadingCaptcha || submitting ? -1 : 0"
              @pointerdown="startSliderDrag"
              @pointermove="moveSliderDrag"
              @pointerup="stopSliderDrag"
              @pointercancel="stopSliderDrag"
              @lostpointercapture="resetSliderDrag"
              @keydown="handleSliderKeydown"
            >
              <img class="slider-piece" :src="captcha.pieceImage" alt="" draggable="false" />
            </div>
            <button type="button" class="slider-refresh" :disabled="loadingCaptcha || submitting" @click="refreshSlider">
              {{ t('login.captcha.refresh') }}
            </button>
            <span v-if="!captcha" class="slider-empty">{{ t('login.captcha.loading') }}</span>
          </div>
        </div>

        <p v-if="errorText" class="login-error" role="alert">{{ errorText }}</p>

        <button class="login-submit" type="submit" :disabled="!captcha || submitting || !loginPayloadUsername">
          {{ submitting ? t('common.actions.loggingIn') : t('common.actions.login') }}
        </button>
      </form>
    </section>
  </main>
</template>

<style scoped>
.login-shell {
  min-height: 100dvh;
  display: grid;
  place-items: center;
  padding: 24px;
  background:
    linear-gradient(135deg, rgba(10, 88, 100, 0.12), transparent 42%),
    linear-gradient(180deg, #f8fafc 0%, #edf4f2 100%);
}

.login-panel {
  width: min(100%, 430px);
  padding: 24px;
  border: 1px solid #d7e2e0;
  border-radius: 8px;
  background: #ffffff;
  box-shadow: 0 18px 50px rgba(15, 23, 42, 0.12);
}

.login-heading {
  display: grid;
  gap: 6px;
  margin-bottom: 18px;
}

.login-kicker {
  margin: 0;
  color: #0f766e;
  font-size: 13px;
  font-weight: 700;
}

.login-heading h1 {
  margin: 0;
  color: #0f172a;
  font-size: 26px;
  line-height: 1.2;
}

.entry-tabs {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(0, 1fr));
  gap: 6px;
  padding: 4px;
  margin-bottom: 12px;
  border: 1px solid #dbe3ee;
  border-radius: 8px;
  background: #f8fafc;
}

.entry-tab {
  min-height: 38px;
  border: 0;
  border-radius: 6px;
  color: #475569;
  background: transparent;
  font: inherit;
  font-weight: 700;
  cursor: pointer;
}

.entry-tab--active {
  color: #ffffff;
  background: #0f766e;
}

.entry-summary {
  display: grid;
  gap: 4px;
  margin-bottom: 16px;
  padding: 12px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #fbfdff;
}

.entry-title,
.entry-description,
.entry-target {
  margin: 0;
}

.entry-title {
  color: #0f172a;
  font-weight: 800;
}

.entry-description {
  color: #475569;
  font-size: 13px;
  line-height: 1.5;
}

.entry-target {
  color: #0f766e;
  font-size: 12px;
  font-weight: 800;
}

.login-form {
  display: grid;
  gap: 16px;
}

.field-grid {
  display: grid;
  gap: 12px;
}

.field-grid--split {
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
}

.login-field {
  display: grid;
  gap: 7px;
  color: #334155;
  font-size: 14px;
  font-weight: 600;
}

.login-field input {
  width: 100%;
  min-height: 44px;
  box-sizing: border-box;
  border: 1px solid #cbd5e1;
  border-radius: 6px;
  padding: 10px 12px;
  color: #0f172a;
  background: #ffffff;
  font: inherit;
}

.login-field small {
  color: #64748b;
  font-size: 12px;
  font-weight: 500;
}

.remember-row {
  align-items: center;
  display: inline-flex;
  gap: 8px;
  color: #475569;
  font-size: 13px;
  font-weight: 700;
}

.remember-row input {
  margin: 0;
}

.store-preview {
  display: grid;
  gap: 4px;
  padding: 10px 12px;
  border: 1px solid #bfdbfe;
  border-radius: 8px;
  background: #eff6ff;
  color: #1e3a8a;
  font-size: 13px;
}

.store-preview span {
  font-weight: 800;
}

.store-preview strong {
  font-weight: 600;
}

.slider-block {
  display: grid;
}

.slider-canvas {
  position: relative;
  width: 100%;
  aspect-ratio: 2 / 1;
  overflow: hidden;
  border: 1px solid #dbe3ee;
  border-radius: 8px;
  background: #f8fafc;
}

.slider-bg {
  width: 100%;
  height: 100%;
  display: block;
  object-fit: cover;
}

.slider-piece-handle {
  position: absolute;
  top: 0;
  left: 0;
  display: grid;
  place-items: center;
  border: 0;
  padding: 0;
  background: transparent;
  cursor: grab;
  touch-action: none;
  outline: none;
  filter: drop-shadow(0 8px 14px rgba(15, 23, 42, 0.22));
}

.slider-piece-handle--dragging {
  cursor: grabbing;
}

.slider-piece-handle:focus-visible {
  border-radius: 8px;
  outline: 3px solid rgba(15, 118, 110, 0.35);
  outline-offset: 3px;
}

.slider-piece-handle[aria-disabled='true'] {
  cursor: default;
}

.slider-piece {
  width: 100%;
  height: 100%;
  display: block;
  object-fit: contain;
  user-select: none;
  pointer-events: none;
}

.slider-refresh {
  position: absolute;
  top: 10px;
  right: 10px;
  min-height: 32px;
  border: 1px solid rgba(15, 118, 110, 0.18);
  border-radius: 999px;
  padding: 0 12px;
  background: rgba(255, 255, 255, 0.88);
  color: #0f766e;
  box-shadow: 0 8px 18px rgba(15, 23, 42, 0.12);
  font-weight: 700;
  cursor: pointer;
}

.slider-refresh:disabled {
  color: #94a3b8;
  cursor: default;
}

.slider-empty {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  color: #64748b;
  font-size: 14px;
}

.login-error {
  margin: 0;
  padding: 10px 12px;
  border: 1px solid #fecaca;
  border-radius: 6px;
  color: #991b1b;
  background: #fff1f2;
  font-size: 14px;
}

.login-submit {
  min-height: 46px;
  border: 0;
  border-radius: 6px;
  color: #ffffff;
  background: #0f766e;
  font-size: 16px;
  font-weight: 800;
  cursor: pointer;
}

.login-submit:disabled {
  background: #94a3b8;
  cursor: default;
}

@media (max-width: 520px) {
  .login-shell {
    padding: 14px;
  }

  .login-panel {
    padding: 18px;
  }

  .field-grid--split {
    grid-template-columns: 1fr;
  }
}
</style>
