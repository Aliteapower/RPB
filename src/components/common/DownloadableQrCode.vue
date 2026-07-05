<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'

import {
  qrCanvasToPngDataUrl,
  renderQrCodeToCanvas,
  safeQrDownloadFileName
} from '../../utils/qrCodeRenderer'

const props = withDefaults(defineProps<{
  value: string
  logoUrl?: string | null
  title?: string
  description?: string
  fileName?: string
  size?: number
  downloadLabel?: string
}>(), {
  title: '二维码',
  description: '',
  fileName: 'qr-code.png',
  size: 220,
  downloadLabel: '下载二维码'
})

const canvasRef = ref<HTMLCanvasElement | null>(null)
const renderError = ref('')
const rendering = ref(false)

const normalizedFileName = computed(() => safeQrDownloadFileName(props.fileName))
const canDownload = computed(() => !!props.value.trim() && !rendering.value && !renderError.value)

watch(
  () => [props.value, props.logoUrl, props.size],
  () => {
    void nextTick(renderQrCode)
  },
  { immediate: true }
)

async function renderQrCode(): Promise<void> {
  const canvas = canvasRef.value
  if (!canvas) {
    return
  }
  renderError.value = ''
  if (!props.value.trim()) {
    renderError.value = '二维码内容为空'
    return
  }
  rendering.value = true
  try {
    await renderQrCodeToCanvas(canvas, {
      value: props.value,
      logoUrl: props.logoUrl,
      size: props.size
    })
  } catch {
    renderError.value = '二维码生成失败'
  } finally {
    rendering.value = false
  }
}

function downloadQrCodePng(): void {
  const canvas = canvasRef.value
  if (!canvas || !canDownload.value) {
    return
  }
  const link = document.createElement('a')
  link.href = qrCanvasToPngDataUrl(canvas)
  link.download = normalizedFileName.value
  link.click()
}
</script>

<template>
  <section class="downloadable-qr-code" aria-label="二维码下载">
    <div class="downloadable-qr-code__header">
      <div>
        <h2>{{ title }}</h2>
        <p v-if="description">{{ description }}</p>
      </div>
    </div>

    <div class="downloadable-qr-code__canvas-frame">
      <canvas
        ref="canvasRef"
        :width="size"
        :height="size"
        role="img"
        :aria-label="title"
      ></canvas>
    </div>

    <p v-if="renderError" class="downloadable-qr-code__error" role="alert">{{ renderError }}</p>

    <button
      class="downloadable-qr-code__download"
      type="button"
      :disabled="!canDownload"
      @click="downloadQrCodePng"
    >
      {{ rendering ? '生成中' : downloadLabel }}
    </button>
  </section>
</template>

<style scoped>
.downloadable-qr-code {
  align-content: start;
  background: #ffffff;
  border: 1px solid #dbe3ea;
  border-radius: 8px;
  display: grid;
  gap: 12px;
  justify-items: center;
  padding: 14px;
}

.downloadable-qr-code__header {
  justify-self: stretch;
}

.downloadable-qr-code h2 {
  color: #0f172a;
  font-size: 16px;
  margin: 0;
}

.downloadable-qr-code p {
  color: #64748b;
  font-size: 12px;
  font-weight: 750;
  margin: 5px 0 0;
}

.downloadable-qr-code__canvas-frame {
  align-items: center;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  display: grid;
  justify-items: center;
  min-height: 244px;
  width: 100%;
}

.downloadable-qr-code canvas {
  display: block;
  height: auto;
  max-width: 100%;
}

.downloadable-qr-code__error {
  background: #fff1f2;
  border: 1px solid #fecaca;
  border-radius: 6px;
  color: #991b1b;
  justify-self: stretch;
  margin: 0;
  padding: 8px 10px;
}

.downloadable-qr-code__download {
  background: #0f766e;
  border: 1px solid #0f766e;
  border-radius: 6px;
  color: #ffffff;
  font: inherit;
  font-weight: 850;
  min-height: 38px;
  padding: 0 12px;
  width: 100%;
}

.downloadable-qr-code__download:disabled {
  cursor: default;
  opacity: 0.6;
}
</style>
