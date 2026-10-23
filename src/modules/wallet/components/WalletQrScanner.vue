<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, shallowRef, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { createWalletCamera, type CameraState } from '../scanning/cameraSession'
import { createQrDecoder } from '../scanning/qrDecoder'
import { normalizeWalletQr, type WalletQrPurpose } from '../scanning/walletQr'
import WalletIcon from './WalletIcon.vue'
const props = withDefaults(defineProps<{ purpose: WalletQrPurpose; autoStart?: boolean; disabled?: boolean }>(), { autoStart: false, disabled: false })
const emit = defineEmits<{ code: [value: string] }>()
const { t } = useI18n(), video = ref<HTMLVideoElement | null>(null), manual = ref(''), imageError = ref(''), readingImage = ref(false)
const state = shallowRef<CameraState>({ phase:'idle', issue:null, facing:'environment', torchAvailable:false, torch:false })
const decoder = createQrDecoder()
let sourceGeneration = 0
function normalize(value: string) { return normalizeWalletQr(value, props.purpose, window.location.origin) }
function found(value: string) {
  const code = normalize(value)
  if (!code || props.disabled) { imageError.value = t(`wallet.scanner.${props.purpose === 'bill' ? 'wrongBill' : 'wrongCustomer'}`); return }
  sourceGeneration++; camera.stop(); imageError.value = ''; manual.value = ''; emit('code', code)
}
const camera = createWalletCamera({
  getVideo: () => video.value, secure: window.isSecureContext, mediaDevices: navigator.mediaDevices,
  requestFrame: callback => window.requestAnimationFrame(callback), cancelFrame: handle => window.cancelAnimationFrame(handle),
  decode: decoder.video, accept: value => !!normalize(value), onCode: found, onChange: value => { state.value = value }
})
const live = computed(() => state.value.phase === 'opening' || state.value.phase === 'scanning')
const cameraError = computed(() => !state.value.issue ? '' : state.value.issue === 'wrongCode' ? t(`wallet.scanner.${props.purpose === 'bill' ? 'wrongBill' : 'wrongCustomer'}`) : t(`wallet.scanner.issues.${state.value.issue}`))
const status = computed(() => props.disabled ? t('wallet.scanner.processing') : t(`wallet.scanner.${state.value.phase === 'error' ? 'idle' : state.value.phase}`))
async function start() { if (props.disabled || readingImage.value) return; sourceGeneration++; imageError.value = ''; await camera.start() }
function stop() { sourceGeneration++; camera.stop() }
function pause() { sourceGeneration++; camera.pause() }
async function switchCamera() { sourceGeneration++; imageError.value = ''; await camera.switchFacing() }
async function image(event: Event) {
  const input = event.target as HTMLInputElement, file = input.files?.[0]
  if (!file || props.disabled || readingImage.value) return
  stop(); const own = sourceGeneration; imageError.value = ''; readingImage.value = true
  let url = ''
  try {
    if (file.size > 10 * 1024 * 1024) { imageError.value = t('wallet.scanner.imageLarge'); return }
    url = URL.createObjectURL(file)
    const picture = new Image(); picture.src = url; await picture.decode()
    if (own !== sourceGeneration) return
    const value = decoder.image(picture)
    if (!value || !normalize(value)) imageError.value = t('wallet.scanner.imageError')
    else found(value)
  } catch { if (own === sourceGeneration) imageError.value = t('wallet.scanner.imageError') }
  finally { if (url) URL.revokeObjectURL(url); input.value = ''; readingImage.value = false }
}
function visibility() { if (document.visibilityState === 'hidden') pause() }
watch(() => props.disabled, value => { if (value) stop() })
onMounted(() => {
  document.addEventListener('visibilitychange', visibility); window.addEventListener('pagehide', pause)
  if (props.autoStart && !props.disabled && document.visibilityState !== 'hidden') void start()
})
onUnmounted(() => { sourceGeneration++; camera.dispose(); document.removeEventListener('visibilitychange', visibility); window.removeEventListener('pagehide', pause) })
</script>
<template>
  <section class="wallet-scanner" :aria-label="t('wallet.scanner.title')" :aria-busy="disabled || readingImage">
    <div class="wallet-camera-preview" :class="{'is-live':live}">
      <video ref="video" v-show="live" autoplay playsinline webkit-playsinline muted disablepictureinpicture :aria-label="t('wallet.scan')" />
      <div v-if="state.phase!=='scanning'" class="wallet-camera-placeholder"><WalletIcon name="scan" :size="48" /><strong>{{ t('wallet.scanner.title') }}</strong></div>
      <div v-if="state.phase==='scanning'" class="wallet-camera-guide" aria-hidden="true" />
      <p class="wallet-camera-status" role="status" aria-live="polite">{{ readingImage ? t('wallet.loading') : status }}</p>
    </div>
    <p class="wallet-camera-hint">{{ t('wallet.scanner.aim') }}</p>
    <div class="wallet-camera-controls">
      <button v-if="!live" type="button" class="wallet-primary" :disabled="disabled || readingImage" @click="start">{{ t(state.phase==='idle'?'wallet.scan':'wallet.scanner.retry') }}</button>
      <button v-if="live" type="button" @click="stop">{{ t('wallet.stopCamera') }}</button>
      <button v-if="state.phase==='scanning'" type="button" @click="switchCamera">{{ t('wallet.scanner.switch') }}</button>
      <button v-if="state.phase==='scanning' && state.torchAvailable" type="button" :aria-pressed="state.torch" @click="camera.toggleTorch">{{ t(state.torch?'wallet.scanner.torchOff':'wallet.scanner.torchOn') }}</button>
    </div>
    <p v-if="cameraError || imageError" class="wallet-error" role="alert">{{ imageError || cameraError }}</p>
    <details class="wallet-scan-alternatives"><summary>{{ t('wallet.scanner.alternatives') }}</summary>
      <label class="wallet-camera-image">{{ t('wallet.scanImage') }}<input type="file" accept="image/*" :disabled="disabled || readingImage" @change="image"></label>
      <form class="wallet-scan-manual" @submit.prevent="found(manual)"><input v-model="manual" :placeholder="t(purpose==='identity'||purpose==='personal'?'wallet.marketing.pastePersonal':'wallet.manualCode')" :aria-label="t(purpose==='identity'||purpose==='personal'?'wallet.marketing.pastePersonal':'wallet.manualCode')" maxlength="4096" autocomplete="off" :disabled="disabled || readingImage"><button :disabled="disabled || readingImage || !manual.trim()">{{ t('wallet.useCode') }}</button></form>
    </details>
    <small>{{ t(purpose==='personal'?'wallet.personal.scanPrivacy':'wallet.scanner.privacy') }}</small>
  </section>
</template>
