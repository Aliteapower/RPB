<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { QueueDisplayApiError, fetchQueueDisplayState } from '../api/queueDisplayApi'
import { useAuthSessionStore } from '../stores/authSession'
import type {
  QueueDisplayAdSlide,
  QueueDisplayStateResponse,
  QueueDisplayTextAdSlide
} from '../types/queueDisplay'

const POLL_INTERVAL_SECONDS = 3
const SLIDE_DURATION_SECONDS = 5
const ERROR_GRACE_MS = 15_000

const fallbackSlide: QueueDisplayTextAdSlide = {
  slideId: 'empty-placeholder',
  title: '广告文案待配置',
  subtitle: '叫号屏已连接',
  tagline: '请稍候'
}

const route = useRoute()
const router = useRouter()
const auth = useAuthSessionStore()

const state = ref<QueueDisplayStateResponse | null>(null)
const apiError = ref<string | null>(null)
const isInitialLoading = ref(true)
const isGraceExpired = ref(false)
const activeSlideIndex = ref(0)
const pollTimer = ref<number | null>(null)
const adTimer = ref<number | null>(null)
const errorGraceTimer = ref<number | null>(null)
const clockTimer = ref<number | null>(null)
const clockNow = ref(new Date())
const serverClockBase = ref<number | null>(null)
const clientClockBase = ref<number | null>(null)

const storeId = computed(() => String(route.params.storeId || ''))
const hasCurrentCall = computed(() => !!state.value?.currentCall)
const textSlides = computed<QueueDisplayTextAdSlide[]>(() => state.value?.ads.slides.filter(isTextAdSlide) ?? [])
const activeAdSlideCount = computed(() => textSlides.value.length)
const currentSlide = computed<QueueDisplayTextAdSlide>(() => {
  const slides = textSlides.value
  return slides.length ? slides[activeSlideIndex.value % slides.length] : fallbackSlide
})
const slideDurationMs = computed(() => Math.max(3, state.value?.ads.slideDurationSeconds ?? SLIDE_DURATION_SECONDS) * 1000)
const pollIntervalMs = computed(() => Math.max(2, state.value?.ads.statePollSeconds ?? POLL_INTERVAL_SECONDS) * 1000)
const waitingPreview = computed(() => state.value?.waiting.preview.slice(0, 4) ?? [])
const waitingCount = computed(() => state.value?.waiting.count ?? 0)
const storeDisplayName = computed(() => {
  const payload = state.value
  return payload?.storeDisplayName?.trim() || payload?.storeName?.trim() || '门店'
})
const displayTime = computed(() => {
  const timezone = state.value?.storeTime.timezone
  if (!timezone) {
    return state.value?.storeTime.timeText || '--:--'
  }

  try {
    const clockSource =
      serverClockBase.value === null || clientClockBase.value === null
        ? clockNow.value
        : new Date(serverClockBase.value + Math.max(0, clockNow.value.getTime() - clientClockBase.value))

    return new Intl.DateTimeFormat('zh-CN', {
      hour: '2-digit',
      minute: '2-digit',
      hour12: false,
      timeZone: timezone
    }).format(clockSource)
  } catch {
    return state.value?.storeTime.timeText || '--:--'
  }
})
const businessDate = computed(() => state.value?.storeTime.businessDate ?? '')
const showManageButton = computed(() => auth.isAuthenticated)
const showErrorScreen = computed(() => !!apiError.value && (!state.value || isGraceExpired.value))
const screenMode = computed<'loading' | 'calling' | 'advertising' | 'error'>(() => {
  if (isInitialLoading.value && !state.value) {
    return 'loading'
  }
  if (showErrorScreen.value) {
    return 'error'
  }
  return hasCurrentCall.value ? 'calling' : 'advertising'
})

async function loadState(): Promise<void> {
  if (!storeId.value) {
    return
  }

  try {
    const nextState = await fetchQueueDisplayState(storeId.value)
    state.value = nextState
    syncServerClock(nextState.serverNow)
    apiError.value = null
    isGraceExpired.value = false
    clearErrorGraceTimer()
    constrainSlideIndex()
  } catch (error) {
    apiError.value = error instanceof QueueDisplayApiError ? error.response.error.messageKey : 'queue.display.load_failed'
    scheduleErrorGrace()
  } finally {
    isInitialLoading.value = false
  }
}

function syncServerClock(serverNow: string): void {
  const parsedServerNow = Date.parse(serverNow)
  if (Number.isFinite(parsedServerNow)) {
    serverClockBase.value = parsedServerNow
    clientClockBase.value = Date.now()
  }
}

function constrainSlideIndex(): void {
  const slideCount = activeAdSlideCount.value
  if (slideCount > 0 && activeSlideIndex.value >= slideCount) {
    activeSlideIndex.value = activeSlideIndex.value % slideCount
  }
}

function isTextAdSlide(slide: QueueDisplayAdSlide): slide is QueueDisplayTextAdSlide {
  return (
    typeof slide.slideId === 'string' &&
    typeof slide.title === 'string' &&
    typeof slide.subtitle === 'string' &&
    typeof slide.tagline === 'string'
  )
}

function startPolling(): void {
  stopPolling()
  pollTimer.value = window.setInterval(() => {
    void loadState()
  }, pollIntervalMs.value)
}

function stopPolling(): void {
  if (pollTimer.value !== null) {
    window.clearInterval(pollTimer.value)
    pollTimer.value = null
  }
}

function startAdRotation(): void {
  stopAdRotation()
  if (hasCurrentCall.value || activeAdSlideCount.value <= 1) {
    return
  }
  adTimer.value = window.setTimeout(() => {
    activeSlideIndex.value = (activeSlideIndex.value + 1) % activeAdSlideCount.value
    startAdRotation()
  }, slideDurationMs.value)
}

function stopAdRotation(): void {
  if (adTimer.value !== null) {
    window.clearTimeout(adTimer.value)
    adTimer.value = null
  }
}

function scheduleErrorGrace(): void {
  if (!state.value) {
    isGraceExpired.value = true
    return
  }

  clearErrorGraceTimer()
  errorGraceTimer.value = window.setTimeout(() => {
    if (apiError.value) {
      isGraceExpired.value = true
    }
  }, ERROR_GRACE_MS)
}

function clearErrorGraceTimer(): void {
  if (errorGraceTimer.value !== null) {
    window.clearTimeout(errorGraceTimer.value)
    errorGraceTimer.value = null
  }
}

function startClock(): void {
  clockTimer.value = window.setInterval(() => {
    clockNow.value = new Date()
  }, 1000)
}

function stopClock(): void {
  if (clockTimer.value !== null) {
    window.clearInterval(clockTimer.value)
    clockTimer.value = null
  }
}

function returnToManagement(): void {
  void router.push(auth.defaultHomeRoute)
}

watch(
  () => storeId.value,
  () => {
    state.value = null
    apiError.value = null
    isInitialLoading.value = true
    isGraceExpired.value = false
    activeSlideIndex.value = 0
    clearErrorGraceTimer()
    void loadState().then(startPolling)
  }
)

watch(pollIntervalMs, startPolling)

watch([hasCurrentCall, () => activeAdSlideCount.value, slideDurationMs], () => {
  constrainSlideIndex()
  startAdRotation()
})

onMounted(() => {
  startClock()
  void loadState().then(startPolling)
})

onBeforeUnmount(() => {
  stopPolling()
  stopAdRotation()
  stopClock()
  clearErrorGraceTimer()
})
</script>

<template>
  <main class="queue-display-terminal" :class="`queue-display-terminal--${screenMode}`">
    <header class="terminal-header">
      <div class="terminal-brand" aria-label="门店叫号屏">
        <span class="brand-dot"></span>
        <div>
          <strong>{{ storeDisplayName }}</strong>
          <span>叫号屏</span>
        </div>
      </div>

      <div class="terminal-time" aria-live="polite">
        <strong>{{ displayTime }}</strong>
        <span>{{ businessDate }}</span>
      </div>

      <button v-if="showManageButton" class="terminal-manage" type="button" @click="returnToManagement">
        返回管理
      </button>
      <div v-else class="terminal-manage-placeholder" aria-hidden="true"></div>
    </header>

    <div v-if="apiError && !showErrorScreen" class="terminal-offline-badge" role="status">
      连接恢复中
    </div>

    <section v-if="screenMode === 'loading'" class="screen-state screen-loading" aria-live="polite">
      <div class="loading-mark"></div>
      <p>正在连接叫号屏</p>
    </section>

    <section v-else-if="screenMode === 'error'" class="screen-state screen-error" role="alert">
      <p class="state-kicker">服务暂不可用</p>
      <h1>叫号屏连接中断</h1>
      <p>请稍后重试</p>
    </section>

    <section v-else-if="hasCurrentCall" class="screen-calling" aria-live="assertive">
      <div class="calling-main">
        <p class="calling-label">当前叫号</p>
        <strong class="calling-number">{{ state?.currentCall?.displayNumber }}</strong>
        <div class="calling-meta">
          <p class="calling-name">{{ state?.currentCall?.customerDisplayName || '顾客' }}</p>
          <span class="calling-group">{{ state?.currentCall?.partySizeGroup }}</span>
        </div>
      </div>

      <aside class="waiting-panel" aria-label="等待队列">
        <p class="waiting-count">等待 {{ waitingCount }} 人</p>
        <ul v-if="waitingPreview.length" class="waiting-list">
          <li v-for="item in waitingPreview" :key="item.displayNumber">
            <strong>{{ item.displayNumber }}</strong>
            <span>{{ item.customerDisplayName || '顾客' }}</span>
            <em>{{ item.partySizeGroup }}</em>
          </li>
        </ul>
        <p v-else class="waiting-empty">暂无等待</p>
      </aside>
    </section>

    <section v-else class="screen-ad" aria-live="polite">
      <div class="ad-icon" aria-hidden="true">食</div>
      <p class="ad-kicker">欢迎等候</p>
      <h1>{{ currentSlide.title }}</h1>
      <p class="ad-subtitle">{{ currentSlide.subtitle }}</p>
      <p class="ad-tagline">{{ currentSlide.tagline }}</p>
      <div v-if="textSlides.length > 1" class="ad-dots" aria-hidden="true">
        <span
          v-for="(slide, index) in textSlides"
          :key="slide.slideId"
          :class="{ 'ad-dot--active': index === activeSlideIndex % textSlides.length }"
        ></span>
      </div>
    </section>
  </main>
</template>

<style scoped>
.queue-display-terminal {
  --queue-display-bg: #0b0e1a;
  --queue-display-panel: rgba(255, 255, 255, 0.08);
  --queue-display-accent: #f97316;
  --queue-display-accent-strong: #fb923c;
  --queue-display-cyan: #38bdf8;
  --queue-display-text: #f8fafc;
  --queue-display-muted: #a7b0c2;

  min-height: 100vh;
  overflow: hidden;
  background:
    linear-gradient(90deg, rgba(255, 255, 255, 0.035) 1px, transparent 1px),
    linear-gradient(180deg, rgba(255, 255, 255, 0.03) 1px, transparent 1px),
    linear-gradient(135deg, #090b14 0%, var(--queue-display-bg) 56%, #111827 100%);
  background-size: 4rem 4rem, 4rem 4rem, auto;
  color: var(--queue-display-text);
  font-family: Inter, "Microsoft YaHei", "PingFang SC", system-ui, sans-serif;
}

.terminal-header {
  display: grid;
  grid-template-columns: minmax(14rem, 1fr) auto minmax(14rem, 1fr);
  align-items: center;
  gap: 1.5rem;
  padding: 1.5rem 2.25rem;
  min-height: 6rem;
}

.terminal-brand {
  display: flex;
  align-items: center;
  gap: 0.875rem;
  min-width: 0;
}

.brand-dot {
  width: 0.875rem;
  height: 0.875rem;
  border-radius: 999px;
  background: var(--queue-display-accent);
  box-shadow: 0 0 1.75rem rgba(249, 115, 22, 0.72);
}

.terminal-brand strong,
.terminal-brand span {
  display: block;
}

.terminal-brand strong {
  overflow: hidden;
  font-size: 1.25rem;
  font-weight: 750;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.terminal-brand span,
.terminal-time span {
  color: var(--queue-display-muted);
  font-size: 0.875rem;
}

.terminal-time {
  justify-self: center;
  text-align: center;
}

.terminal-time strong {
  display: block;
  font-size: 2rem;
  font-weight: 780;
  line-height: 1;
}

.terminal-manage {
  justify-self: end;
  min-width: 7.5rem;
  min-height: 2.75rem;
  border: 1px solid rgba(255, 255, 255, 0.18);
  border-radius: 0.5rem;
  background: rgba(255, 255, 255, 0.08);
  color: var(--queue-display-text);
  cursor: pointer;
  font: inherit;
  font-weight: 700;
}

.terminal-manage-placeholder {
  min-height: 2.75rem;
}

.terminal-offline-badge {
  position: absolute;
  top: 6.125rem;
  right: 2.25rem;
  z-index: 3;
  border: 1px solid rgba(251, 146, 60, 0.48);
  border-radius: 999px;
  padding: 0.45rem 0.85rem;
  background: rgba(249, 115, 22, 0.14);
  color: #fed7aa;
  font-weight: 700;
}

.screen-state,
.screen-ad,
.screen-calling {
  min-height: calc(100vh - 6rem);
}

.screen-state {
  display: grid;
  place-items: center;
  padding: 2rem;
  text-align: center;
}

.screen-loading {
  gap: 1.25rem;
  align-content: center;
  color: var(--queue-display-muted);
  font-size: 1.25rem;
  font-weight: 700;
}

.loading-mark {
  width: 5rem;
  height: 5rem;
  border: 0.35rem solid rgba(255, 255, 255, 0.16);
  border-top-color: var(--queue-display-accent);
  border-radius: 999px;
  animation: queue-display-spin 900ms linear infinite;
}

.state-kicker,
.ad-kicker,
.calling-label {
  margin: 0;
  color: var(--queue-display-accent-strong);
  font-size: 1.25rem;
  font-weight: 780;
}

.screen-error h1 {
  margin: 1rem 0 0;
  font-size: 3rem;
  line-height: 1.1;
}

.screen-calling {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(18rem, 24rem);
  gap: 2rem;
  align-items: center;
  padding: 2rem 4rem 4rem;
}

.calling-main {
  min-width: 0;
  text-align: center;
}

.calling-number {
  display: block;
  margin: 0.5rem auto;
  max-width: 100%;
  overflow-wrap: anywhere;
  background: linear-gradient(135deg, #ffffff 30%, #f97316 100%);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
  font-size: 9rem;
  font-weight: 900;
  line-height: 1;
  -webkit-text-fill-color: transparent;
}

.calling-meta {
  display: inline-flex;
  align-items: center;
  gap: 1rem;
  border: 1px solid rgba(255, 255, 255, 0.16);
  border-radius: 0.5rem;
  padding: 0.875rem 1.25rem;
  background: rgba(255, 255, 255, 0.08);
}

.calling-name {
  margin: 0;
  font-size: 2rem;
  font-weight: 780;
}

.calling-group {
  border-radius: 999px;
  padding: 0.45rem 0.85rem;
  background: rgba(249, 115, 22, 0.22);
  color: #fed7aa;
  font-weight: 800;
}

.waiting-panel {
  align-self: stretch;
  min-width: 0;
  border: 1px solid rgba(255, 255, 255, 0.14);
  border-radius: 0.5rem;
  padding: 1.5rem;
  background: var(--queue-display-panel);
}

.waiting-count {
  margin: 0 0 1.25rem;
  font-size: 1.5rem;
  font-weight: 800;
}

.waiting-list {
  display: grid;
  gap: 0.875rem;
  margin: 0;
  padding: 0;
  list-style: none;
}

.waiting-list li {
  display: grid;
  grid-template-columns: 5.5rem minmax(0, 1fr) auto;
  align-items: center;
  gap: 0.75rem;
  min-height: 4rem;
  border-radius: 0.5rem;
  padding: 0.75rem 0.875rem;
  background: rgba(255, 255, 255, 0.08);
}

.waiting-list strong {
  color: var(--queue-display-cyan);
  font-size: 1.25rem;
}

.waiting-list span {
  overflow: hidden;
  font-weight: 700;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.waiting-list em {
  border-radius: 999px;
  padding: 0.25rem 0.55rem;
  background: rgba(255, 255, 255, 0.12);
  color: var(--queue-display-muted);
  font-style: normal;
  font-weight: 700;
}

.waiting-empty {
  margin: 0;
  color: var(--queue-display-muted);
  font-size: 1.125rem;
}

.screen-ad {
  display: grid;
  place-items: center;
  padding: 2rem 3rem 4rem;
  text-align: center;
}

.screen-ad h1 {
  max-width: 60rem;
  margin: 0.75rem auto 0;
  overflow-wrap: anywhere;
  color: #ffffff;
  font-size: 5rem;
  font-weight: 900;
  line-height: 1.05;
}

.ad-icon {
  display: grid;
  place-items: center;
  width: 7rem;
  height: 7rem;
  margin: 0 auto 1.5rem;
  border: 1px solid rgba(249, 115, 22, 0.56);
  border-radius: 999px;
  background: rgba(249, 115, 22, 0.14);
  color: #fed7aa;
  font-size: 3.25rem;
  font-weight: 900;
}

.ad-subtitle {
  margin: 1.25rem 0 0;
  color: #e2e8f0;
  font-size: 2.25rem;
  font-weight: 800;
}

.ad-tagline {
  max-width: 54rem;
  margin: 1.25rem auto 0;
  color: var(--queue-display-muted);
  font-size: 1.5rem;
  font-weight: 650;
  line-height: 1.45;
}

.ad-dots {
  display: inline-flex;
  gap: 0.625rem;
  margin-top: 2.5rem;
}

.ad-dots span {
  width: 0.75rem;
  height: 0.75rem;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.22);
}

.ad-dots .ad-dot--active {
  width: 2.25rem;
  background: var(--queue-display-accent);
}

@keyframes queue-display-spin {
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 1100px) {
  .terminal-header {
    grid-template-columns: minmax(0, 1fr) auto;
    padding: 1.25rem 1.5rem;
  }

  .screen-calling {
    grid-template-columns: 1fr;
    padding: 1.5rem;
  }

  .calling-number {
    font-size: 7rem;
  }

  .screen-ad h1 {
    font-size: 4rem;
  }
}

@media (max-width: 760px) {
  .terminal-header {
    grid-template-columns: 1fr;
    gap: 0.875rem;
  }

  .terminal-time,
  .terminal-manage {
    justify-self: start;
  }

  .terminal-manage {
    width: 100%;
  }

  .calling-number {
    font-size: 5rem;
  }

  .waiting-list li {
    grid-template-columns: 1fr;
  }

  .screen-ad {
    padding: 2rem 1.25rem 3rem;
  }

  .screen-ad h1 {
    font-size: 3rem;
  }
}
</style>
