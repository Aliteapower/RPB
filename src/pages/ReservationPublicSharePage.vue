<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'

import {
  getReservationPublicShare,
  reservationPublicShareErrorMessage,
  ReservationPublicShareApiError
} from '../api/reservationPublicShareApi'
import { copyPlainText } from '../utils/plainTextClipboard'
import type { ReservationPublicShare } from '../types/reservationPublicShare'

const route = useRoute()
const share = ref<ReservationPublicShare | null>(null)
const isLoading = ref(false)
const errorText = ref('')
const shareStatusText = ref('')
const fallbackUrl = ref('')

const token = computed(() => String(route.params.token || '').trim())
const tableDisplay = computed(() => {
  if (!share.value || share.value.tablePending || !share.value.tableCode.trim()) {
    return '桌位待确认'
  }

  return share.value.tableCode
})
const pageUrl = computed(() => {
  if (typeof window === 'undefined') {
    return ''
  }

  return window.location.href
})

onMounted(() => {
  void loadShare()
})

watch(token, () => {
  void loadShare()
})

async function loadShare(): Promise<void> {
  if (!token.value) {
    share.value = null
    errorText.value = '预约信息不存在'
    return
  }

  isLoading.value = true
  errorText.value = ''
  shareStatusText.value = ''
  fallbackUrl.value = ''

  try {
    const response = await getReservationPublicShare(token.value)
    share.value = response.share
  } catch (error) {
    share.value = null
    errorText.value =
      error instanceof ReservationPublicShareApiError
        ? publicSharePageErrorText(error)
        : '预约信息读取失败'
  } finally {
    isLoading.value = false
  }
}

function publicSharePageErrorText(error: ReservationPublicShareApiError): string {
  if (error.response.error.code === 'TOKEN_EXPIRED' || error.response.error.code === 'TOKEN_REVOKED') {
    return '链接已失效'
  }

  return reservationPublicShareErrorMessage(error.response.error.code)
}

async function shareCurrentPage(): Promise<void> {
  if (!share.value || !pageUrl.value) {
    return
  }

  shareStatusText.value = ''
  fallbackUrl.value = ''

  if (navigator.share) {
    try {
      await navigator.share({
        title: share.value.shareTitle,
        text: share.value.shareSummary,
        url: pageUrl.value
      })
      shareStatusText.value = '已打开转发'
      return
    } catch (error) {
      if (error instanceof DOMException && error.name === 'AbortError') {
        return
      }
    }
  }

  if (await copyPlainText(pageUrl.value)) {
    shareStatusText.value = '链接已复制'
    return
  }

  fallbackUrl.value = pageUrl.value
  shareStatusText.value = '请手动复制链接'
}
</script>

<template>
  <main class="reservation-public-share">
    <section class="reservation-public-share__shell" aria-label="预约信息">
      <header class="reservation-public-share__header">
        <span>预约信息</span>
        <h1>{{ share?.storeName || '订位确认' }}</h1>
        <p v-if="share">{{ share.shareSummary }}</p>
      </header>

      <section v-if="isLoading" class="reservation-public-share__state" aria-live="polite">
        正在读取预约信息
      </section>

      <section v-else-if="errorText" class="reservation-public-share__state" role="alert">
        <strong>{{ errorText }}</strong>
        <span>请联系门店确认最新订位状态。</span>
      </section>

      <template v-else-if="share">
        <dl class="reservation-public-share__details">
          <div>
            <dt>预约编号</dt>
            <dd>{{ share.reservationNo }}</dd>
          </div>
          <div>
            <dt>日期</dt>
            <dd>{{ share.reservationDate }}</dd>
          </div>
          <div>
            <dt>时间</dt>
            <dd>{{ share.reservationTime }}</dd>
          </div>
          <div>
            <dt>人数</dt>
            <dd>{{ share.partySize }}人</dd>
          </div>
          <div>
            <dt>桌位</dt>
            <dd>{{ tableDisplay }}</dd>
          </div>
        </dl>

        <section class="reservation-public-share__store" aria-label="门店信息">
          <div v-if="share.arrivalNote">
            <span>到店提示</span>
            <p>{{ share.arrivalNote }}</p>
          </div>
          <div v-if="share.storeAddress">
            <span>地址</span>
            <p>{{ share.storeAddress }}</p>
          </div>
          <a v-if="share.googleMapUrl" :href="share.googleMapUrl" target="_blank" rel="noreferrer">
            打开地图
          </a>
          <a v-if="share.storePhone" :href="`tel:${share.storePhone}`">
            联系门店
          </a>
        </section>

        <footer class="reservation-public-share__actions">
          <button type="button" @click="shareCurrentPage">转发订位链接</button>
          <p v-if="shareStatusText" role="status">{{ shareStatusText }}</p>
          <textarea
            v-if="fallbackUrl"
            readonly
            :value="fallbackUrl"
            aria-label="订位分享链接"
          ></textarea>
        </footer>
      </template>
    </section>
  </main>
</template>

<style scoped>
.reservation-public-share {
  background:
    linear-gradient(180deg, rgba(255, 247, 237, 0.92), rgba(240, 253, 250, 0.78)),
    #f8fafc;
  color: #0f172a;
  min-height: 100dvh;
  padding: 18px;
}

.reservation-public-share__shell {
  display: grid;
  gap: 14px;
  margin: 0 auto;
  max-width: 520px;
}

.reservation-public-share__header {
  background: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  display: grid;
  gap: 6px;
  padding: 20px;
}

.reservation-public-share__header span,
.reservation-public-share__store span,
.reservation-public-share__details dt {
  color: #64748b;
  font-size: 0.78rem;
  font-weight: 850;
}

.reservation-public-share__header h1 {
  color: #0f172a;
  font-size: 1.55rem;
  letter-spacing: 0;
  line-height: 1.22;
  margin: 0;
  overflow-wrap: anywhere;
}

.reservation-public-share__header p,
.reservation-public-share__state span,
.reservation-public-share__store p,
.reservation-public-share__actions p {
  color: #334155;
  font-size: 0.9rem;
  font-weight: 800;
  margin: 0;
  overflow-wrap: anywhere;
}

.reservation-public-share__state,
.reservation-public-share__details,
.reservation-public-share__store,
.reservation-public-share__actions {
  background: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 16px;
}

.reservation-public-share__state {
  display: grid;
  gap: 6px;
}

.reservation-public-share__state strong {
  color: #b42318;
  font-size: 1rem;
  font-weight: 950;
}

.reservation-public-share__details {
  display: grid;
  gap: 10px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  margin: 0;
}

.reservation-public-share__details div {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.reservation-public-share__details dd {
  color: #0f172a;
  font-size: 1rem;
  font-weight: 950;
  margin: 0;
  overflow-wrap: anywhere;
}

.reservation-public-share__store,
.reservation-public-share__actions {
  display: grid;
  gap: 10px;
}

.reservation-public-share__store div {
  display: grid;
  gap: 4px;
}

.reservation-public-share__store a,
.reservation-public-share__actions button {
  align-items: center;
  border-radius: 8px;
  display: inline-flex;
  font-size: 0.92rem;
  font-weight: 950;
  justify-content: center;
  min-height: 42px;
  text-decoration: none;
}

.reservation-public-share__store a {
  background: #f8fafc;
  border: 1px solid #cbd5e1;
  color: #0f766e;
}

.reservation-public-share__actions button {
  background: #f97316;
  border: 1px solid #f97316;
  color: #ffffff;
}

.reservation-public-share__actions textarea {
  border: 1px solid #cbd5e1;
  border-radius: 8px;
  color: #0f172a;
  min-height: 78px;
  padding: 10px;
  resize: vertical;
  width: 100%;
}

.reservation-public-share a:focus-visible,
.reservation-public-share button:focus-visible,
.reservation-public-share textarea:focus-visible {
  outline: 3px solid rgba(249, 115, 22, 0.26);
  outline-offset: 2px;
}

@media (max-width: 420px) {
  .reservation-public-share {
    padding: 12px;
  }

  .reservation-public-share__details {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
