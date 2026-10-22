<script setup lang="ts">
import { nextTick, onUnmounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import jsQR from 'jsqr'
const emit = defineEmits<{ code: [value: string] }>()
const { t } = useI18n()
const video = ref<HTMLVideoElement | null>(null), running = ref(false), error = ref(''), manual = ref('')
let stream: MediaStream | null = null, frame = 0, lastFrame = 0, generation = 0
const canvas = document.createElement('canvas')
const ctx = canvas.getContext('2d', { willReadFrequently: true })
function decode(source: CanvasImageSource, width: number, height: number): string | null {
  if (!ctx || !width || !height) return null
  const scale = Math.min(1, 960 / Math.max(width, height))
  canvas.width = Math.round(width * scale); canvas.height = Math.round(height * scale)
  ctx.drawImage(source, 0, 0, canvas.width, canvas.height)
  const pixels = ctx.getImageData(0, 0, canvas.width, canvas.height)
  return jsQR(pixels.data, pixels.width, pixels.height, { inversionAttempts: 'attemptBoth' })?.data ?? null
}
function stop() {
  generation++; running.value = false; cancelAnimationFrame(frame)
  stream?.getTracks().forEach(track => track.stop()); stream = null
  if (video.value) video.value.srcObject = null
}
function found(code: string) { stop(); error.value = ''; manual.value = ''; emit('code', code.trim()) }
async function start() {
  stop(); const own = generation; error.value = ''; running.value = true
  try {
    const candidate = await navigator.mediaDevices.getUserMedia({ video: { facingMode: { ideal: 'environment' } }, audio: false })
    if (own !== generation) { candidate.getTracks().forEach(track => track.stop()); return }
    stream = candidate; await nextTick()
    if (!video.value || own !== generation) { stop(); return }
    video.value.srcObject = stream; await video.value.play()
    const tick = (time: number) => {
      if (!running.value || !video.value) return
      if (time - lastFrame > 180 && video.value.readyState >= 2) {
        lastFrame = time
        const result = decode(video.value, video.value.videoWidth, video.value.videoHeight)
        if (result) { found(result); return }
      }
      frame = requestAnimationFrame(tick)
    }
    frame = requestAnimationFrame(tick)
  } catch { stop(); error.value = t('wallet.cameraError') }
}
async function image(event: Event) {
  stop(); error.value = ''
  const input = event.target as HTMLInputElement, file = input.files?.[0]
  if (!file) return
  let url = ''
  try {
    if (file.size > 10 * 1024 * 1024) throw new Error()
    url = URL.createObjectURL(file)
    const picture = new Image(); picture.src = url; await picture.decode()
    const result = decode(picture, picture.width, picture.height)
    if (!result) throw new Error()
    found(result)
  } catch { error.value = t('wallet.invalidCode') }
  finally { if (url) URL.revokeObjectURL(url); input.value = '' }
}
onUnmounted(stop)
</script>
<template>
  <section class="wallet-scanner">
    <div class="wallet-actions"><button type="button" @click="start">{{ t('wallet.scan') }}</button><button v-if="running" type="button" @click="stop">{{ t('wallet.stopCamera') }}</button></div>
    <video v-if="running" ref="video" playsinline muted :aria-label="t('wallet.scan')" />
    <label>{{ t('wallet.scanImage') }}<input type="file" accept="image/*" @change="image"></label>
    <form class="wallet-actions" @submit.prevent="manual && found(manual)"><input v-model="manual" :placeholder="t('wallet.manualCode')" :aria-label="t('wallet.manualCode')" autocomplete="off"><button :disabled="!manual.trim()">{{ t('wallet.useCode') }}</button></form>
    <p v-if="error" class="wallet-error" role="alert">{{ error }}</p>
  </section>
</template>
