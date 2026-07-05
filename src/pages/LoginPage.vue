<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { AuthApiError, createSliderCaptcha } from '../api/authApi'
import PasswordInput from '../components/common/PasswordInput.vue'
import { useAuthSessionStore } from '../stores/authSession'
import type { AuthUser, SliderCaptchaChallenge } from '../types/auth'

type LoginEntryId = 'platform-admin' | 'tenant-admin' | 'tenant-staff'

interface LoginEntry {
  id: LoginEntryId
  label: string
  title: string
  description: string
  accountLabel: string
  loginUsername: string
  presetPassword: string
  tenantCode?: string
  employeeUsername?: string
  targetHint: string
}

const loginEntries: LoginEntry[] = [
  {
    id: 'platform-admin',
    label: '平台',
    title: '平台后台',
    description: '创建租户、开通租户后台和平台级管理。',
    accountLabel: '平台账号',
    loginUsername: 'sysadmin',
    presetPassword: '393930',
    targetHint: '平台范围'
  },
  {
    id: 'tenant-admin',
    label: '租户',
    title: '租户后台',
    description: '设置店面、维护员工账号和授权范围。',
    accountLabel: '租户账号',
    loginUsername: '20000000',
    presetPassword: '393930',
    tenantCode: '20000000',
    targetHint: '租户 20000000'
  },
  {
    id: 'tenant-staff',
    label: '员工',
    title: '租户员工',
    description: '按租户代码进入已授权店面，可为多店切换预留。',
    accountLabel: '员工账号',
    loginUsername: '1000',
    presetPassword: '393930',
    tenantCode: '20000000',
    employeeUsername: '1000',
    targetHint: '租户 20000000 / 员工 1000'
  }
]

const route = useRoute()
const router = useRouter()
const auth = useAuthSessionStore()
const missingStoreScopeText = '账号未绑定门店，请联系平台管理员完成租户初始化'

const selectedEntryId = ref<LoginEntryId>('tenant-staff')
const username = ref('1000')
const employeeUsername = ref('1000')
const tenantCode = ref('20000000')
const password = ref('393930')
const captcha = ref<SliderCaptchaChallenge | null>(null)
const captchaX = ref(0)
const sliderCanvas = ref<HTMLElement | null>(null)
const canvasWidth = ref(320)
const loadingCaptcha = ref(false)
const submitting = ref(false)
const draggingSlider = ref(false)
const errorText = ref('')
const pendingStoreSelection = ref(false)
const selectedStoreId = ref('')
let sliderResizeObserver: ResizeObserver | null = null
let activeSliderPointerId: number | null = null
let sliderDragOffsetX = 0

const selectedEntry = computed(() => loginEntries.find(entry => entry.id === selectedEntryId.value) ?? loginEntries[0])
const isStaffEntry = computed(() => selectedEntry.value.id === 'tenant-staff')
const loginPayloadUsername = computed(() =>
  isStaffEntry.value ? employeeUsername.value.trim() : username.value.trim()
)
const authorizedStoreIds = computed(() => auth.user?.storeIds ?? [])
const canChooseStore = computed(() => pendingStoreSelection.value && authorizedStoreIds.value.length > 0)

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
  void refreshSlider().then(showRouteStoreScopeMessage)
})

onBeforeUnmount(() => {
  sliderResizeObserver?.disconnect()
})

function selectEntry(entry: LoginEntry): void {
  selectedEntryId.value = entry.id
  username.value = entry.loginUsername
  employeeUsername.value = entry.employeeUsername ?? entry.loginUsername
  tenantCode.value = entry.tenantCode ?? ''
  password.value = entry.presetPassword
  pendingStoreSelection.value = false
  selectedStoreId.value = ''
  errorText.value = ''
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
    errorText.value = '滑块校验加载失败'
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
    errorText.value = missingStoreScopeText
  }
}

async function submitLogin(): Promise<void> {
  if (!captcha.value || submitting.value || !loginPayloadUsername.value) {
    return
  }

  submitting.value = true
  errorText.value = ''
  pendingStoreSelection.value = false

  try {
    const user = await auth.loginWithPassword({
      username: loginPayloadUsername.value,
      password: password.value,
      captchaId: captcha.value.challengeId,
      captchaX: Math.round(captchaX.value)
    })
    await continueAfterLogin(user)
  } catch (error) {
    errorText.value = loginErrorText(error)
    await refreshSlider()
  } finally {
    submitting.value = false
  }
}

async function continueAfterLogin(user: AuthUser): Promise<void> {
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

  if (selectedEntry.value.id === 'tenant-admin' && auth.isTenantAdmin) {
    await router.replace(auth.tenantAdminHomeRoute)
    return
  }

  if (isStaffEntry.value && user.storeIds.length > 1) {
    selectedStoreId.value = user.defaultStoreId || user.storeIds[0]
    pendingStoreSelection.value = true
    return
  }

  if (isStaffEntry.value && user.storeIds.length === 1) {
    await router.replace(storeRoute(user.storeIds[0]))
    return
  }

  await router.replace(auth.defaultHomeRoute)
}

async function stopMissingStoreScopeLogin(): Promise<void> {
  pendingStoreSelection.value = false
  selectedStoreId.value = ''
  await auth.logoutCurrentUser()
  await refreshSlider()
  errorText.value = missingStoreScopeText
}

async function selectStoreAndContinue(): Promise<void> {
  if (!selectedStoreId.value) {
    return
  }
  await router.replace(storeRoute(selectedStoreId.value))
}

function storeRoute(storeId: string): string {
  return `/stores/${storeId}/staff`
}

function loginErrorText(error: unknown): string {
  if (!(error instanceof AuthApiError)) {
    return '登录失败'
  }

  const code = error.response.error.code
  if (code === 'CAPTCHA_MISMATCH' || code === 'CAPTCHA_EXPIRED' || code === 'CAPTCHA_REQUIRED') {
    return '滑块校验未通过'
  }
  if (code === 'PASSWORD_POLICY_VIOLATION') {
    return '密码为 6 位数字或英文字母'
  }
  if (code === 'INVALID_CREDENTIALS') {
    return '账号或密码不正确'
  }
  return '登录失败'
}
</script>

<template>
  <main class="login-shell">
    <section class="login-panel" aria-label="后台登录">
      <div class="login-heading">
        <p class="login-kicker">RPB</p>
        <h1>后台入口</h1>
      </div>

      <div class="entry-tabs" role="tablist" aria-label="选择后台入口">
        <button
          v-for="entry in loginEntries"
          :key="entry.id"
          type="button"
          class="entry-tab"
          :class="{ 'entry-tab--active': entry.id === selectedEntryId }"
          :aria-selected="entry.id === selectedEntryId"
          role="tab"
          @click="selectEntry(entry)"
        >
          <span>{{ entry.label }}</span>
        </button>
      </div>

      <div class="entry-summary" :data-entry="selectedEntry.id">
        <p class="entry-title">{{ selectedEntry.title }}</p>
        <p class="entry-description">{{ selectedEntry.description }}</p>
        <p class="entry-target">{{ selectedEntry.targetHint }}</p>
      </div>

      <form class="login-form" @submit.prevent="submitLogin">
        <div v-if="isStaffEntry" class="field-grid field-grid--split">
          <label class="login-field">
            <span>租户代码</span>
            <input v-model.trim="tenantCode" name="tenantCode" autocomplete="organization" inputmode="numeric" required />
          </label>

          <label class="login-field">
            <span>员工账号</span>
            <input v-model.trim="employeeUsername" name="employeeUsername" autocomplete="username" inputmode="text" required />
          </label>
        </div>

        <label v-else class="login-field">
          <span>{{ selectedEntry.accountLabel }}</span>
          <input v-model.trim="username" name="username" autocomplete="username" inputmode="text" required />
        </label>

        <label class="login-field">
          <span>密码</span>
          <PasswordInput
            v-model="password"
            name="password"
            autocomplete="current-password"
            maxlength="6"
            required
          />
          <small>密码为 6 位数字或英文字母</small>
        </label>

        <div v-if="isStaffEntry" class="store-preview">
          <span>授权店面</span>
          <strong>登录后按授权进入，可在多店权限下切换店面</strong>
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
              aria-label="滑块校验"
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
              换一张
            </button>
            <span v-if="!captcha" class="slider-empty">加载中</span>
          </div>
        </div>

        <p v-if="errorText" class="login-error" role="alert">{{ errorText }}</p>

        <button class="login-submit" type="submit" :disabled="!captcha || submitting || !loginPayloadUsername">
          {{ submitting ? '登录中...' : '登录' }}
        </button>
      </form>

      <section v-if="canChooseStore" class="store-selection" aria-label="选择授权店面">
        <label class="login-field">
          <span>选择店面</span>
          <select v-model="selectedStoreId">
            <option v-for="storeId in authorizedStoreIds" :key="storeId" :value="storeId">
              {{ storeId }}
            </option>
          </select>
        </label>
        <button class="login-submit" type="button" @click="selectStoreAndContinue">进入店面</button>
      </section>
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
  grid-template-columns: repeat(3, minmax(0, 1fr));
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

.login-form,
.store-selection {
  display: grid;
  gap: 16px;
}

.store-selection {
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid #e2e8f0;
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

.login-field input,
.login-field select {
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
