<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import {
  getPaymentProofCandidates,
  PaymentApiError,
  scanPaymentProof
} from '../api/paymentApi'
import StaffBottomNav from '../components/staff/StaffBottomNav.vue'
import StaffHomeTopBar from '../components/staff-home/StaffHomeTopBar.vue'
import { useCurrentClock } from '../components/staff-home/useCurrentClock'
import { useGeneratedText } from '../i18n/generatedText'
import { useAuthSessionStore } from '../stores/authSession'
import { useStoreContextStore } from '../stores/storeContext'
import type { PaymentProofCandidate, PaymentProofScanResponse } from '../types/payment'
import { formatAppGateErrorMessage } from '../utils/appGateErrorMessages'

const terminalStorageKey = 'rpb.payment.quickPay.terminalCode'

const route = useRoute()
const router = useRouter()
const auth = useAuthSessionStore()
const storeContext = useStoreContextStore()
const { currentBusinessDate, currentTimeText } = useCurrentClock()
const { gt } = useGeneratedText()

const terminalCode = ref('T1')
const businessDate = ref('')
const candidates = ref<PaymentProofCandidate[]>([])
const loadingCandidates = ref(false)
const scanning = ref(false)
const scannerActive = ref(false)
const cameraStarting = ref(false)
const cameraError = ref('')
const errorText = ref('')
const selectedFile = ref<File | null>(null)
const previewUrl = ref('')
const scanResult = ref<PaymentProofScanResponse | null>(null)
const videoRef = ref<HTMLVideoElement | null>(null)
const photoInputRef = ref<HTMLInputElement | null>(null)
const albumInputRef = ref<HTMLInputElement | null>(null)
let candidateSequence = 0
let scannerStream: MediaStream | null = null
let scannerTimer: number | undefined
let liveScanInFlight = false

const storeId = computed(() => storeContext.resolveStoreId(route.params.storeId))
const storeLabel = computed(() => storeId.value ? gt('generated.payment-proof-review.001', { shortId: storeId.value.slice(0, 8) }) : gt('generated.payment-proof-review.002'))
const normalizedTerminalCode = computed(() => terminalCode.value.trim() || 'T1')
const displayedBusinessDate = computed(() => businessDate.value || currentBusinessDate.value)
const canScan = computed(() => Boolean(storeId.value && selectedFile.value && !scanning.value))
const matchedCandidate = computed(() => {
  const reference = scanResult.value?.paymentReference
  if (!reference) {
    return null
  }
  return candidates.value.find(candidate => candidate.paymentReference === reference) || null
})

onMounted(() => {
  try {
    terminalCode.value = window.localStorage.getItem(terminalStorageKey) || 'T1'
  } catch {
    terminalCode.value = 'T1'
  }
  businessDate.value = currentBusinessDate.value
  void loadCandidates()
})

onBeforeUnmount(() => {
  stopScanner()
  revokePreview()
})

watch(storeId, () => {
  scanResult.value = null
  void loadCandidates()
})

watch(terminalCode, value => {
  try {
    window.localStorage.setItem(terminalStorageKey, value.trim() || 'T1')
  } catch {
    // Keep the in-memory terminal code if local storage is unavailable.
  }
})

async function loadCandidates(): Promise<void> {
  const currentStoreId = storeId.value
  const sequence = ++candidateSequence
  if (!currentStoreId) {
    candidates.value = []
    return
  }
  loadingCandidates.value = true
  errorText.value = ''
  try {
    const response = await getPaymentProofCandidates(currentStoreId, {
      businessDate: businessDate.value,
      terminalCode: normalizedTerminalCode.value,
      limit: 80
    })
    if (sequence !== candidateSequence) {
      return
    }
    candidates.value = response.candidates
  } catch (error) {
    if (sequence === candidateSequence) {
      errorText.value = apiErrorText(error)
    }
  } finally {
    if (sequence === candidateSequence) {
      loadingCandidates.value = false
    }
  }
}

function onFileSelected(event: Event): void {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0] || null
  input.value = ''
  selectedFile.value = file
  scanResult.value = null
  revokePreview()
  if (file) {
    previewUrl.value = URL.createObjectURL(file)
  }
}

function openPhotoCapture(): void {
  photoInputRef.value?.click()
}

function openAlbumPicker(): void {
  albumInputRef.value?.click()
}

async function submitScan(): Promise<void> {
  if (!storeId.value || !selectedFile.value || scanning.value) {
    return
  }
  await submitProofImage(selectedFile.value, false)
}

async function startScanner(): Promise<void> {
  if (scannerActive.value || cameraStarting.value || !storeId.value) {
    return
  }
  cameraStarting.value = true
  cameraError.value = ''
  errorText.value = ''
  scanResult.value = null
  try {
    const stream = await navigator.mediaDevices.getUserMedia({
      audio: false,
      video: {
        facingMode: { ideal: 'environment' },
        width: { ideal: 1280 },
        height: { ideal: 720 }
      }
    })
    scannerStream = stream
    scannerActive.value = true
    await nextTick()
    if (videoRef.value) {
      videoRef.value.srcObject = stream
      await videoRef.value.play()
    }
    scannerTimer = window.setInterval(() => {
      void scanFrame()
    }, 1800)
    void scanFrame()
  } catch {
    cameraError.value = gt('generated.payment-proof-review.035')
    stopScanner()
  } finally {
    cameraStarting.value = false
  }
}

function stopScanner(): void {
  if (scannerTimer !== undefined) {
    window.clearInterval(scannerTimer)
    scannerTimer = undefined
  }
  if (scannerStream) {
    scannerStream.getTracks().forEach(track => track.stop())
    scannerStream = null
  }
  if (videoRef.value) {
    videoRef.value.srcObject = null
  }
  scannerActive.value = false
}

async function scanFrame(): Promise<void> {
  if (!scannerActive.value || scanning.value || liveScanInFlight || !storeId.value) {
    return
  }
  liveScanInFlight = true
  try {
    const frame = await captureFrameBlob()
    if (frame) {
      await submitProofImage(new File([frame], 'payment-proof-frame.jpg', { type: 'image/jpeg' }), true)
    }
  } finally {
    liveScanInFlight = false
  }
}

async function captureFrameBlob(): Promise<Blob | null> {
  const video = videoRef.value
  if (!video || !video.videoWidth || !video.videoHeight) {
    return null
  }
  const sourceWidth = video.videoWidth
  const sourceHeight = video.videoHeight
  const cropWidth = Math.round(sourceWidth * 0.9)
  const cropHeight = Math.round(sourceHeight * 0.86)
  const sourceX = Math.round((sourceWidth - cropWidth) / 2)
  const sourceY = Math.round((sourceHeight - cropHeight) / 2)
  const maxWidth = 1280
  const scale = Math.min(1, maxWidth / cropWidth)
  const canvas = document.createElement('canvas')
  canvas.width = Math.max(1, Math.round(cropWidth * scale))
  canvas.height = Math.max(1, Math.round(cropHeight * scale))
  const context = canvas.getContext('2d')
  if (!context) {
    return null
  }
  context.drawImage(video, sourceX, sourceY, cropWidth, cropHeight, 0, 0, canvas.width, canvas.height)
  return new Promise(resolve => {
    canvas.toBlob(blob => resolve(blob), 'image/jpeg', 0.88)
  })
}

async function submitProofImage(image: File, fromCamera: boolean): Promise<void> {
  if (!storeId.value || scanning.value) {
    return
  }
  scanning.value = true
  errorText.value = ''
  if (!fromCamera) {
    scanResult.value = null
  }
  try {
    scanResult.value = await scanPaymentProof(storeId.value, {
      image,
      idempotencyKey: createIdempotencyKey(),
      businessDate: businessDate.value,
      terminalCode: normalizedTerminalCode.value
    })
    if (scanResult.value.outcome === 'auto_confirmed' || scanResult.value.outcome === 'already_confirmed') {
      stopScanner()
      await loadCandidates()
    } else if (fromCamera && scanResult.value.outcome === 'needs_review') {
      stopScanner()
    }
  } catch (error) {
    errorText.value = apiErrorText(error)
  } finally {
    scanning.value = false
  }
}

function closeReview(): void {
  window.close()
  void router.push({
    name: 'payment-quick-pay',
    params: {
      storeId: storeId.value
    }
  })
}

function outcomeLabel(outcome: string | null | undefined): string {
  if (outcome === 'auto_confirmed') {
    return gt('generated.payment-proof-review.017')
  }
  if (outcome === 'already_confirmed') {
    return gt('generated.payment-proof-review.039')
  }
  if (outcome === 'needs_review') {
    return gt('generated.payment-proof-review.018')
  }
  return gt('generated.payment-proof-review.019')
}

function outcomeClass(outcome: string | null | undefined): string {
  if (outcome === 'auto_confirmed' || outcome === 'already_confirmed') {
    return 'result-panel--good'
  }
  if (outcome === 'needs_review') {
    return 'result-panel--warn'
  }
  return 'result-panel--bad'
}

function checkClass(value: string | null | undefined): string {
  return value === 'match' ? 'check-pill--good' : 'check-pill--warn'
}

function money(amount: string | number | null | undefined, currency = 'SGD'): string {
  const value = Number(amount ?? 0)
  const safeValue = Number.isFinite(value) ? value : 0
  return `${currency || 'SGD'} ${safeValue.toFixed(2)}`
}

function createIdempotencyKey(): string {
  return `proof-review-${Date.now()}-${Math.random().toString(16).slice(2)}`
}

function revokePreview(): void {
  if (previewUrl.value) {
    URL.revokeObjectURL(previewUrl.value)
    previewUrl.value = ''
  }
}

function apiErrorText(error: unknown): string {
  if (!(error instanceof PaymentApiError)) {
    return gt('generated.payment-proof-review.020')
  }
  if (error.status === 401) {
    auth.clear()
    return gt('generated.payment-proof-review.021')
  }
  if (error.response.error.code === 'FORBIDDEN') {
    return gt('generated.payment-proof-review.022')
  }
  if (error.response.error.code === 'PERMISSION_DENIED' || error.response.error.messageKey === 'appgate.permission_denied') {
    return formatAppGateErrorMessage(error.response.error, gt('generated.payment-proof-review.022'))
  }
  if (error.response.error.code === 'PAYMENT_OCR_UNAVAILABLE') {
    return gt('generated.payment-proof-review.023')
  }
  if (error.response.error.code === 'PAYMENT_INTENT_STATE_CONFLICT') {
    return gt('generated.payment-proof-review.024')
  }
  if (error.response.error.code === 'IDEMPOTENCY_CONFLICT') {
    return gt('generated.payment-proof-review.025')
  }
  if (error.response.error.code === 'REQUEST_INVALID') {
    return gt('generated.payment-proof-review.026')
  }
  return gt('generated.payment-proof-review.020')
}
</script>

<template>
  <main class="staff-workbench-shell review-shell">
    <StaffHomeTopBar
      :app-status-label="gt('generated.payment-proof-review.003')"
      :business-date="currentBusinessDate"
      :current-time-text="currentTimeText"
      :store-label="storeLabel"
    >
      <template #action>
        <button class="ghost-button" type="button" @click="closeReview">
          {{ gt('generated.payment-proof-review.004') }}
        </button>
      </template>
    </StaffHomeTopBar>

    <section class="review-body">
      <header class="review-title">
        <div>
          <span>{{ gt('generated.payment-proof-review.005') }}</span>
          <h1>{{ gt('generated.payment-proof-review.006') }}</h1>
        </div>
        <button class="ghost-button" type="button" :disabled="loadingCandidates" @click="loadCandidates">
          {{ loadingCandidates ? gt('generated.payment-proof-review.027') : gt('generated.payment-proof-review.007') }}
        </button>
      </header>

      <form class="filter-strip" @submit.prevent="loadCandidates">
        <label>
          <span>{{ gt('generated.payment-proof-review.008') }}</span>
          <input v-model="businessDate" type="date" />
        </label>
        <label>
          <span>{{ gt('generated.payment-proof-review.009') }}</span>
          <input v-model.trim="terminalCode" maxlength="32" />
        </label>
      </form>

      <p v-if="errorText" class="error-banner" role="alert">{{ errorText }}</p>

      <section class="scan-panel">
        <div class="camera-scanner" :class="{ 'camera-scanner--active': scannerActive }">
          <video v-show="scannerActive" ref="videoRef" autoplay muted playsinline></video>
          <div class="scanner-placeholder" v-show="!scannerActive">
            {{ gt('generated.payment-proof-review.031') }}
          </div>
          <div class="scan-frame">
            <span>{{ scannerActive ? gt('generated.payment-proof-review.032') : gt('generated.payment-proof-review.033') }}</span>
          </div>
        </div>

        <button class="primary-button" type="button" :disabled="cameraStarting || scanning" @click="scannerActive ? stopScanner() : startScanner()">
          {{ cameraStarting ? gt('generated.payment-proof-review.034') : scannerActive ? gt('generated.payment-proof-review.036') : gt('generated.payment-proof-review.031') }}
        </button>

        <p v-if="cameraError" class="empty-line">{{ cameraError }}</p>

        <div class="upload-actions">
          <button class="secondary-button" type="button" :disabled="scanning" @click="openPhotoCapture">
            {{ gt('generated.payment-proof-review.037') }}
          </button>
          <button class="secondary-button" type="button" :disabled="scanning" @click="openAlbumPicker">
            {{ gt('generated.payment-proof-review.038') }}
          </button>
        </div>

        <input
          ref="photoInputRef"
          accept="image/png,image/jpeg,image/webp"
          capture="environment"
          class="file-input"
          type="file"
          @change="onFileSelected"
        />
        <input
          ref="albumInputRef"
          accept="image/png,image/jpeg,image/webp"
          class="file-input"
          type="file"
          @change="onFileSelected"
        />

        <p class="selected-file">{{ selectedFile ? selectedFile.name : gt('generated.payment-proof-review.010') }}</p>

        <img v-if="previewUrl" class="preview-image" :src="previewUrl" alt="" />

        <button class="primary-button" type="button" :disabled="!canScan" @click="submitScan">
          {{ scanning ? gt('generated.payment-proof-review.011') : gt('generated.payment-proof-review.012') }}
        </button>
      </section>

      <section v-if="scanResult" class="result-panel" :class="outcomeClass(scanResult.outcome)" aria-live="polite">
        <header>
          <span>{{ outcomeLabel(scanResult.outcome) }}</span>
          <strong v-if="scanResult.paymentReference">{{ scanResult.paymentReference }}</strong>
        </header>

        <dl class="result-grid">
          <div>
            <dt>{{ gt('generated.payment-proof-review.013') }}</dt>
            <dd>{{ scanResult.ocr?.extractedReference || '-' }}</dd>
          </div>
          <div>
            <dt>{{ gt('generated.payment-proof-review.014') }}</dt>
            <dd>{{ money(scanResult.ocr?.extractedAmount, 'SGD') }}</dd>
          </div>
          <div>
            <dt>{{ gt('generated.payment-proof-review.015') }}</dt>
            <dd>{{ scanResult.expectedAmount ? money(scanResult.expectedAmount, 'SGD') : '-' }}</dd>
          </div>
          <div>
            <dt>{{ gt('generated.payment-proof-review.016') }}</dt>
            <dd>
              <span class="check-pill" :class="checkClass(scanResult.checks?.reference)">
                Ref {{ scanResult.checks?.reference || '-' }}
              </span>
              <span class="check-pill" :class="checkClass(scanResult.checks?.amount)">
                Amount {{ scanResult.checks?.amount || '-' }}
              </span>
            </dd>
          </div>
        </dl>

        <article v-if="matchedCandidate" class="matched-card">
          <strong>#{{ matchedCandidate.displayNumber }} · {{ money(matchedCandidate.amount, matchedCandidate.currency) }}</strong>
          <span>{{ matchedCandidate.paymentReference }}</span>
        </article>

        <button v-if="scanResult.outcome === 'auto_confirmed' || scanResult.outcome === 'already_confirmed'" class="primary-button" type="button" @click="closeReview">
          {{ gt('generated.payment-proof-review.028') }}
        </button>
      </section>

      <section class="candidate-section">
        <header>
          <h2>{{ gt('generated.payment-proof-review.029') }}</h2>
          <span>{{ candidates.length }}</span>
        </header>

        <p v-if="loadingCandidates" class="empty-line">{{ gt('generated.payment-proof-review.027') }}</p>
        <p v-else-if="!candidates.length" class="empty-line">{{ gt('generated.payment-proof-review.030') }}</p>

        <div v-else class="candidate-list">
          <article v-for="candidate in candidates" :key="candidate.sessionId" class="candidate-card">
            <strong>#{{ candidate.displayNumber }}</strong>
            <div>
              <span>{{ candidate.paymentReference }}</span>
              <small>{{ money(candidate.amount, candidate.currency) }} · {{ candidate.intentStatus }}</small>
            </div>
          </article>
        </div>
      </section>
    </section>

    <StaffBottomNav :store-id="storeId" active-tab="payment" />
  </main>
</template>

<style scoped>
.review-shell {
  background: #f3f7f4;
  color: #102033;
  min-height: 100dvh;
}

.review-body {
  display: grid;
  gap: 10px;
  margin: 0 auto;
  max-width: 560px;
  padding: 12px 10px calc(92px + env(safe-area-inset-bottom));
}

.review-title,
.filter-strip,
.scan-panel,
.result-panel,
.candidate-section {
  background: #ffffff;
  border: 1px solid #d8e4df;
  border-radius: 8px;
  padding: 12px;
}

.review-title {
  align-items: center;
  display: flex;
  gap: 10px;
  justify-content: space-between;
}

.review-title h1,
.candidate-section h2 {
  color: #0f172a;
  font-size: 1.2rem;
  letter-spacing: 0;
  line-height: 1.18;
  margin: 0;
}

.review-title span,
label span,
dt,
.candidate-card small,
.candidate-section header span {
  color: #64748b;
  font-size: 0.74rem;
  font-weight: 850;
}

.ghost-button,
.primary-button {
  border-radius: 6px;
  cursor: pointer;
  font: inherit;
  font-size: 0.82rem;
  font-weight: 900;
  min-height: 38px;
  padding: 0 12px;
}

.ghost-button {
  background: #ffffff;
  border: 1px solid #cbd5e1;
  color: #0f172a;
}

.primary-button {
  background: #0f766e;
  border: 0;
  color: #ffffff;
  width: 100%;
}

button:disabled {
  cursor: default;
  opacity: 0.6;
}

.filter-strip {
  display: grid;
  gap: 10px;
  grid-template-columns: minmax(0, 1fr) minmax(120px, 180px);
}

label {
  display: grid;
  gap: 6px;
  min-width: 0;
}

input {
  border: 1px solid #cbd5e1;
  border-radius: 6px;
  box-sizing: border-box;
  color: #0f172a;
  font: inherit;
  min-height: 38px;
  padding: 8px 10px;
  width: 100%;
}

.error-banner,
.empty-line {
  border-radius: 6px;
  margin: 0;
  padding: 10px 12px;
}

.error-banner {
  background: #fff1f2;
  border: 1px solid #fecaca;
  color: #991b1b;
}

.empty-line {
  background: #f8fafc;
  border: 1px solid #dbe3ea;
  color: #475569;
}

.scan-panel {
  display: grid;
  gap: 10px;
}

.camera-scanner {
  aspect-ratio: 3 / 4;
  background: #0f172a;
  border: 1px solid #dbe3ea;
  border-radius: 8px;
  display: grid;
  overflow: hidden;
  position: relative;
  width: 100%;
}

.camera-scanner video,
.scanner-placeholder {
  grid-area: 1 / 1;
  height: 100%;
  object-fit: cover;
  width: 100%;
}

.scanner-placeholder {
  align-items: center;
  color: #e2e8f0;
  display: flex;
  font-weight: 900;
  justify-content: center;
  padding: 18px;
  text-align: center;
}

.scan-frame {
  align-items: end;
  border: 3px solid rgba(20, 184, 166, 0.9);
  border-radius: 8px;
  display: flex;
  inset: 7% 5%;
  justify-content: center;
  pointer-events: none;
  position: absolute;
}

.scan-frame span {
  background: rgba(15, 23, 42, 0.78);
  border-radius: 6px 6px 0 0;
  color: #ffffff;
  font-size: 0.78rem;
  font-weight: 900;
  padding: 6px 10px;
}

.upload-actions {
  display: grid;
  gap: 8px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.secondary-button {
  align-items: center;
  background: #ffffff;
  border: 1px solid #94a3b8;
  border-radius: 8px;
  color: #0f172a;
  cursor: pointer;
  display: inline-flex;
  font: inherit;
  font-size: 0.82rem;
  font-weight: 900;
  justify-content: center;
  min-height: 76px;
  padding: 12px;
  text-align: center;
}

.file-input {
  height: 1px;
  opacity: 0;
  position: absolute;
  width: 1px;
}

.selected-file {
  background: #f8fafc;
  border: 1px dashed #cbd5e1;
  border-radius: 8px;
  color: #475569;
  font-size: 0.8rem;
  font-weight: 850;
  margin: 0;
  overflow-wrap: anywhere;
  padding: 10px 12px;
  text-align: center;
}

.preview-image {
  aspect-ratio: 9 / 16;
  border: 1px solid #dbe3ea;
  border-radius: 8px;
  object-fit: cover;
  width: 100%;
}

.result-panel {
  border-width: 2px;
  display: grid;
  gap: 10px;
}

.result-panel--good {
  border-color: #86efac;
}

.result-panel--warn {
  border-color: #fde68a;
}

.result-panel--bad {
  border-color: #fecaca;
}

.result-panel header {
  display: grid;
  gap: 4px;
}

.result-panel header span {
  color: #0f766e;
  font-size: 0.8rem;
  font-weight: 950;
}

.result-panel header strong {
  color: #0f172a;
  font-size: 1.05rem;
  overflow-wrap: anywhere;
}

.result-grid {
  display: grid;
  gap: 8px;
  margin: 0;
}

.result-grid div {
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  display: grid;
  gap: 5px;
  padding: 9px;
}

.result-grid dd {
  color: #0f172a;
  font-weight: 900;
  margin: 0;
  overflow-wrap: anywhere;
}

.check-pill {
  align-items: center;
  border-radius: 999px;
  display: inline-flex;
  font-size: 0.72rem;
  font-weight: 950;
  margin: 0 5px 5px 0;
  min-height: 24px;
  padding: 0 8px;
}

.check-pill--good {
  background: #dcfce7;
  color: #166534;
}

.check-pill--warn {
  background: #fef3c7;
  color: #92400e;
}

.matched-card,
.candidate-card {
  align-items: center;
  border: 1px solid #dbe3ea;
  border-radius: 8px;
  display: grid;
  gap: 10px;
  grid-template-columns: 56px minmax(0, 1fr);
  padding: 10px;
}

.matched-card {
  background: #f0fdf4;
  grid-template-columns: 1fr;
}

.matched-card span,
.candidate-card span {
  color: #0f172a;
  display: block;
  font-weight: 900;
  overflow-wrap: anywhere;
}

.candidate-section {
  display: grid;
  gap: 10px;
}

.candidate-section header {
  align-items: center;
  display: flex;
  justify-content: space-between;
}

.candidate-list {
  display: grid;
  gap: 8px;
}

.candidate-card > strong {
  color: #0f172a;
  font-size: 1.2rem;
  text-align: center;
}

@media (max-width: 420px) {
  .review-title,
  .filter-strip {
    grid-template-columns: 1fr;
  }

  .review-title {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
