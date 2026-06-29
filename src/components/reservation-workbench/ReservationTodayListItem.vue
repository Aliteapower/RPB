<script setup lang="ts">
import { computed, ref } from 'vue'

import {
  getReservationShareInfo,
  ReservationShareInfoApiError
} from '../../api/reservationShareInfoApi'
import { shareLinkOrCopy } from '../../utils/reservationShareLauncher'
import type { ReservationTodayViewItem } from '../../types/reservationTodayView'
import type { ReservationShareInfo } from '../../types/reservationShareInfo'
import ReservationShareCopyPanel from './ReservationShareCopyPanel.vue'

const cancellableStatuses = new Set(['draft', 'confirmed'])
const noShowableStatuses = new Set(['confirmed'])

const statusLabels: Record<string, string> = {
  confirmed: '已预约',
  arrived: '已到店',
  seated: '已入桌',
  cancelled: '已取消',
  no_show: '爽约',
  completed: '已完成',
  draft: '草稿'
}

const queueStatusLabels: Record<string, string> = {
  waiting: '排队中',
  called: '已叫号',
  skipped: '已过号',
  rejoined: '已重回',
  seated: '已入座',
  cancelled: '排队已取消',
  expired: '排队已过期'
}

const props = defineProps<{
  canCancelReservation: boolean
  canNoShowReservation: boolean
  canRunCurrentDayActions: boolean
  item: ReservationTodayViewItem
  isCancelling?: boolean
  isCheckingIn?: boolean
  isNoShowing?: boolean
  isSeating?: boolean
  storeId: string
  storeTimezone: string
}>()

const emit = defineEmits<{
  'cancel-requested': [item: ReservationTodayViewItem]
  'check-in-requested': [item: ReservationTodayViewItem]
  'no-show-requested': [item: ReservationTodayViewItem]
  'seat-requested': [item: ReservationTodayViewItem]
}>()

const customerName = computed(() => {
  const values = [props.item.customerName, props.item.customerNickname].filter(Boolean)
  return values.length ? values.join(' / ') : props.item.reservationCode
})

const phoneDisplay = computed(() => optionalDisplay(props.item.phoneMasked))
const timeRange = computed(
  () => `${formatStoreTime(props.item.reservedStartAt)} - ${formatStoreTime(props.item.reservedEndAt)}`
)
const statusText = computed(() => statusLabels[props.item.status] ?? props.item.status)
const statusClass = computed(() => `status-${props.item.status.replace(/_/g, '-')}`)
const queueTicketStatus = computed(() => props.item.queueTicketStatus?.trim() ?? '')
const hasQueueTicket = computed(() => !!props.item.queueTicketId?.trim())
const showCheckIn = computed(() => props.item.status === 'confirmed')
const showDirectSeat = computed(() => props.item.status === 'arrived' && !hasQueueTicket.value)
const showQueueSeat = computed(
  () => props.item.status === 'arrived' && hasQueueTicket.value && queueTicketStatus.value === 'called'
)
const showSeat = computed(() => showDirectSeat.value || showQueueSeat.value)
const showNoShow = computed(() => noShowableStatuses.has(props.item.status))
const canCheckIn = computed(() => showCheckIn.value && props.canRunCurrentDayActions)
const canSeat = computed(() => showSeat.value && props.canRunCurrentDayActions)
const canNoShowAction = computed(
  () => showNoShow.value && props.canNoShowReservation && props.canRunCurrentDayActions
)
const canCancel = computed(
  () => props.canCancelReservation && cancellableStatuses.has(props.item.status)
)
const currentDayActionTitle = computed(() =>
  props.canRunCurrentDayActions ? undefined : '仅当日预约可以操作'
)
const seatActionText = computed(() => (showQueueSeat.value ? '排队入座' : '入桌'))
const seatLoadingText = computed(() => (showQueueSeat.value ? '跳转中' : '入桌中'))
const tableAssignmentText = computed(() => {
  const currentCode = props.item.currentResourceCode?.trim()

  if (currentCode) {
    const label = props.item.status === 'completed' ? '已完成' : '已入桌'
    return `${resourceLabel(props.item.currentResourceType)}：${currentCode}（${label}）`
  }

  const assignedCode = props.item.assignedResourceCode?.trim()

  if (assignedCode) {
    return `${resourceLabel(props.item.assignedResourceType)}：${assignedCode}（预约指定）`
  }

  return props.item.status === 'seated' ? '桌台：已入桌' : '桌台：未指定'
})
const queueAssignmentText = computed(() => {
  if (!props.item.queueTicketId) {
    return null
  }

  const status = props.item.queueTicketStatus?.trim()
  const statusText = status ? queueStatusLabels[status] ?? status : '已排队'
  const numberText = queueTicketDisplayText(props.item)

  return `${numberText} · ${statusText}`
})
const shareInfo = ref<ReservationShareInfo | null>(null)
const isLoadingShareInfo = ref(false)
const shareInfoErrorText = ref('')
const shareInfoShared = ref(false)
const shareInfoStatusText = ref('')
const shareInfoFallbackText = ref('')

function requestCancel(): void {
  emit('cancel-requested', props.item)
}

function requestNoShow(): void {
  if (!canNoShowAction.value) {
    return
  }

  emit('no-show-requested', props.item)
}

function requestCheckIn(): void {
  if (!canCheckIn.value) {
    return
  }

  emit('check-in-requested', props.item)
}

function requestSeat(): void {
  if (!canSeat.value) {
    return
  }

  emit('seat-requested', props.item)
}

async function shareReservationLink(): Promise<void> {
  if (!shareInfo.value) {
    await loadReservationShareInfo()
  }

  const info = shareInfo.value
  const url = info ? reservationShareUrl(info) : ''
  if (!info || !url) {
    shareInfoErrorText.value = '暂无可转发链接'
    return
  }

  shareInfoErrorText.value = ''
  shareInfoShared.value = false
  shareInfoStatusText.value = ''
  shareInfoFallbackText.value = ''

  const result = await shareLinkOrCopy({
    title: info.shareTitle,
    text: info.shareSummary || info.shareText,
    url
  })

  if (result === 'cancelled') {
    return
  }

  if (result === 'copied' || result === 'native-share') {
    shareInfoShared.value = true
    shareInfoStatusText.value = result === 'copied' ? '链接已复制' : '已打开转发'
    return
  }

  shareInfoFallbackText.value = url
  shareInfoErrorText.value = '当前浏览器限制转发，请手动复制下方链接'
}

async function loadReservationShareInfo(): Promise<void> {
  if (!props.storeId || isLoadingShareInfo.value) {
    return
  }

  isLoadingShareInfo.value = true
  shareInfoErrorText.value = ''
  shareInfoShared.value = false
  shareInfoStatusText.value = ''
  shareInfoFallbackText.value = ''

  try {
    const response = await getReservationShareInfo(props.storeId, props.item.reservationId)
    shareInfo.value = response.shareInfo
  } catch (error) {
    shareInfo.value = null
    shareInfoErrorText.value =
      error instanceof ReservationShareInfoApiError
        ? '订位信息读取失败'
        : '订位信息读取失败'
  } finally {
    isLoadingShareInfo.value = false
  }
}

function formatStoreTime(value: string): string {
  const date = new Date(value)

  if (Number.isNaN(date.getTime())) {
    return value
  }

  const parts = new Intl.DateTimeFormat('zh-CN', {
    timeZone: props.storeTimezone,
    hour: '2-digit',
    minute: '2-digit',
    hour12: false
  }).formatToParts(date)

  const part = (type: string) => parts.find(item => item.type === type)?.value ?? ''
  return `${part('hour')}:${part('minute')}`
}

function optionalDisplay(value: string | null | undefined): string {
  return value?.trim() ? value : '未填写'
}

function queueTicketDisplayText(item: ReservationTodayViewItem): string {
  const displayNumber =
    item.queueTicketDisplayNumber?.trim() ||
    (typeof item.queueTicketNumber === 'number' ? String(item.queueTicketNumber) : '')

  return displayNumber ? `#${displayNumber}` : '排队票'
}

function reservationShareUrl(info: ReservationShareInfo): string {
  const path = info.sharePath?.trim() || (info.shareToken ? `/reservation-share/${info.shareToken}` : '')
  return path ? new URL(path, window.location.origin).toString() : ''
}

function resourceLabel(type: string | null | undefined): string {
  if (type === 'table_group') {
    return '桌组'
  }

  if (type === 'dining_table') {
    return '桌号'
  }

  return '桌台'
}
</script>

<template>
  <article class="reservation-today-list-item">
    <div class="reservation-today-list-item__body">
      <div class="reservation-today-list-item__main">
        <strong>{{ customerName }}</strong>
        <span>{{ phoneDisplay }}</span>
      </div>
      <p>{{ timeRange }}</p>
      <p>{{ item.partySize }}人 · {{ tableAssignmentText }}</p>
      <p v-if="queueAssignmentText">{{ queueAssignmentText }}</p>
    </div>

    <div class="reservation-today-list-item__actions" aria-label="预约操作">
      <span class="reservation-today-list-item__status" :class="statusClass">
        {{ statusText }}
      </span>

      <ReservationShareCopyPanel
        :share-info="shareInfo"
        :loading="isLoadingShareInfo"
        :shared="shareInfoShared"
        :status-text="shareInfoStatusText"
        :error-text="shareInfoErrorText"
        :fallback-text="shareInfoFallbackText"
        button-text="转发订位链接"
        @share-requested="shareReservationLink"
      />

      <button
        v-if="showCheckIn"
        class="reservation-today-list-item__action reservation-today-list-item__action--primary"
        :disabled="!canCheckIn || isCheckingIn"
        :title="currentDayActionTitle"
        type="button"
        @click="requestCheckIn"
      >
        {{ isCheckingIn ? '到店中' : '到店' }}
      </button>

      <button
        v-if="showSeat"
        class="reservation-today-list-item__action reservation-today-list-item__action--primary"
        :disabled="!canSeat || isSeating"
        :title="currentDayActionTitle"
        type="button"
        @click="requestSeat"
      >
        {{ isSeating ? seatLoadingText : seatActionText }}
      </button>

      <button
        v-if="showNoShow && canNoShowReservation"
        class="reservation-today-list-item__action reservation-today-list-item__action--danger"
        :disabled="!canNoShowAction || isNoShowing"
        :title="currentDayActionTitle"
        type="button"
        @click="requestNoShow"
      >
        {{ isNoShowing ? '爽约中' : '爽约' }}
      </button>

      <button
        v-if="canCancel"
        class="reservation-today-list-item__action reservation-today-list-item__action--danger"
        :disabled="isCancelling"
        title="取消预约"
        type="button"
        @click="requestCancel"
      >
        {{ isCancelling ? '取消中' : '取消' }}
      </button>
    </div>
  </article>
</template>

<style scoped>
.reservation-today-list-item {
  align-items: center;
  border-bottom: 1px solid #edf2f7;
  display: grid;
  gap: 10px;
  grid-template-columns: minmax(0, 1fr) auto;
  padding: 13px 0;
}

.reservation-today-list-item:first-child {
  padding-top: 4px;
}

.reservation-today-list-item:last-child {
  border-bottom: 0;
  padding-bottom: 4px;
}

.reservation-today-list-item__body {
  display: grid;
  gap: 3px;
  min-width: 0;
}

.reservation-today-list-item__main {
  align-items: baseline;
  display: flex;
  gap: 6px;
  min-width: 0;
}

.reservation-today-list-item__main strong {
  color: #14213d;
  font-size: 1rem;
  font-weight: 950;
  overflow-wrap: anywhere;
}

.reservation-today-list-item__main span,
.reservation-today-list-item__body p {
  color: #315f91;
  font-size: 0.78rem;
  font-weight: 800;
  margin: 0;
}

.reservation-today-list-item__body p:first-of-type {
  color: #475569;
}

.reservation-today-list-item__actions {
  align-items: center;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  justify-content: flex-end;
  max-width: 214px;
}

.reservation-today-list-item__actions :deep(.reservation-share-copy) {
  max-width: 190px;
}

.reservation-today-list-item__actions :deep(.reservation-share-copy__fallback) {
  width: 190px;
}

.reservation-today-list-item__status,
.reservation-today-list-item__action {
  align-items: center;
  border-radius: 999px;
  display: inline-flex;
  font-size: 0.72rem;
  font-weight: 950;
  justify-content: center;
  min-height: 26px;
  padding: 0 9px;
  text-decoration: none;
  white-space: nowrap;
}

.reservation-today-list-item__status {
  background: #eaf2ff;
  color: #315f91;
}

.status-confirmed {
  background: #fff4de;
  color: #f97316;
}

.status-arrived {
  background: #fff0e4;
  color: #c2410c;
}

.status-seated {
  background: #eef8f2;
  color: #176b4d;
}

.status-cancelled,
.status-no-show,
.status-completed {
  background: #f1f5f9;
  color: #475569;
}

.reservation-today-list-item__action--primary {
  border: 1px solid #f97316;
  background: #f97316;
  color: #ffffff;
}

.reservation-today-list-item__action--secondary {
  background: #eff6ff;
  border: 1px solid #bfdbfe;
  color: #1d4ed8;
}

.reservation-today-list-item__action--danger {
  background: #ef4444;
  border: 0;
  color: #ffffff;
}

.reservation-today-list-item__action:disabled,
.reservation-today-list-item__action--danger:disabled {
  background: #e2e8f0;
  border-color: #e2e8f0;
  color: #94a3b8;
  cursor: not-allowed;
}

a:focus-visible,
button:focus-visible {
  outline: 3px solid rgba(249, 115, 22, 0.28);
  outline-offset: 2px;
}

@media (max-width: 420px) {
  .reservation-today-list-item {
    align-items: start;
    grid-template-columns: minmax(0, 1fr);
  }

  .reservation-today-list-item__actions {
    justify-content: flex-start;
    max-width: none;
  }
}
</style>
