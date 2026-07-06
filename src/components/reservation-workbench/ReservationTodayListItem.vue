<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'

import {
  getReservationShareInfo,
  recordReservationShareIntent,
  ReservationShareInfoApiError
} from '../../api/reservationShareInfoApi'
import { reservationWechatShareText } from '../../utils/reservationChannelSharePayload'
import { shareLinkOrCopy } from '../../utils/reservationShareLauncher'
import { copyPlainText } from '../../utils/plainTextClipboard'
import type { ReservationShareIntentChannel } from '../../types/reservationShareInfo'
import type { ReservationTodayViewItem } from '../../types/reservationTodayView'
import type { ReservationShareInfo } from '../../types/reservationShareInfo'
import ReservationShareCopyPanel from './ReservationShareCopyPanel.vue'

const cancellableStatuses = new Set(['draft', 'confirmed'])
const noShowableStatuses = new Set(['confirmed'])

const statusLabelKeys: Record<string, string> = {
  confirmed: 'reservationWorkbench.statuses.confirmed',
  arrived: 'reservationWorkbench.statuses.arrived',
  seated: 'reservationWorkbench.statuses.seated',
  cancelled: 'reservationWorkbench.statuses.cancelled',
  no_show: 'reservationWorkbench.statuses.noShow',
  completed: 'reservationWorkbench.statuses.completed',
  draft: 'reservationWorkbench.statuses.draft'
}

const queueStatusLabelKeys: Record<string, string> = {
  waiting: 'reservationWorkbench.queueStatuses.waiting',
  called: 'reservationWorkbench.queueStatuses.called',
  skipped: 'reservationWorkbench.queueStatuses.skipped',
  rejoined: 'reservationWorkbench.queueStatuses.rejoined',
  seated: 'reservationWorkbench.queueStatuses.seated',
  cancelled: 'reservationWorkbench.queueStatuses.cancelled',
  expired: 'reservationWorkbench.queueStatuses.expired'
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

const { t, locale } = useI18n()
const activeLocale = computed(() => String(locale.value || 'zh-CN'))
const customerName = computed(() => {
  const values = [props.item.customerName, props.item.customerNickname].filter(Boolean)
  return values.length ? values.join(' / ') : props.item.reservationCode
})

const phoneDisplay = computed(() => optionalDisplay(props.item.phoneMasked))
const timeRange = computed(
  () => `${formatStoreTime(props.item.reservedStartAt)} - ${formatStoreTime(props.item.reservedEndAt)}`
)
const statusText = computed(() => {
  const labelKey = statusLabelKeys[props.item.status]
  return labelKey ? t(labelKey) : props.item.status
})
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
  props.canRunCurrentDayActions ? undefined : t('reservationWorkbench.item.currentDayOnly')
)
const seatActionText = computed(() => (
  showQueueSeat.value ? t('reservationWorkbench.item.seatFromQueue') : t('reservationWorkbench.item.seatDirect')
))
const seatLoadingText = computed(() => (
  showQueueSeat.value ? t('reservationWorkbench.item.jumping') : t('reservationWorkbench.item.seating')
))
const tableAssignmentText = computed(() => {
  const currentCode = props.item.currentResourceCode?.trim()

  if (currentCode) {
    const label = props.item.status === 'completed'
      ? t('reservationWorkbench.item.completed')
      : t('reservationWorkbench.item.seated')
    return t('reservationWorkbench.item.tableAssigned', {
      resource: resourceLabel(props.item.currentResourceType),
      code: currentCode,
      label
    })
  }

  const assignedCode = props.item.assignedResourceCode?.trim()

  if (assignedCode) {
    return t('reservationWorkbench.item.tableAssigned', {
      resource: resourceLabel(props.item.assignedResourceType),
      code: assignedCode,
      label: t('reservationWorkbench.item.reservationAssigned')
    })
  }

  return props.item.status === 'seated'
    ? t('reservationWorkbench.item.tableSeated')
    : t('reservationWorkbench.item.tableUnassigned')
})
const queueAssignmentText = computed(() => {
  if (!props.item.queueTicketId) {
    return null
  }

  const status = props.item.queueTicketStatus?.trim()
  const labelKey = status ? queueStatusLabelKeys[status] : ''
  const statusText = labelKey
    ? t(labelKey)
    : (status || t('reservationWorkbench.queueStatuses.queued'))
  const numberText = queueTicketDisplayText(props.item)

  return `${numberText} · ${statusText}`
})
const shareInfo = ref<ReservationShareInfo | null>(null)
const isLoadingShareInfo = ref(false)
const shareInfoErrorText = ref('')
const shareInfoShared = ref(false)
const shareInfoStatusText = ref('')
const shareInfoFallbackText = ref('')

watch(activeLocale, () => {
  shareInfo.value = null
  resetShareFeedback()
})

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
  await systemShareReservationLink()
}

async function systemShareReservationLink(): Promise<void> {
  const info = await ensureShareInfo()
  const url = info ? reservationShareUrl(info) : ''
  if (!info || !url) {
    shareInfoErrorText.value = t('reservationWorkbench.share.noForwardLink')
    return
  }

  resetShareFeedback()

  const result = await shareLinkOrCopy({
    title: info.shareTitle,
    text: info.shareSummary || info.shareText,
    url
  })

  if (result === 'cancelled') {
    return
  }

  if (result === 'copied' || result === 'native-share') {
    await recordShareIntent(result === 'copied' ? 'copy_link' : 'system_share')
    shareInfoShared.value = true
    shareInfoStatusText.value = result === 'copied'
      ? t('reservationWorkbench.share.copied')
      : t('reservationWorkbench.share.nativeOpened')
    return
  }

  shareInfoFallbackText.value = url
  shareInfoErrorText.value = t('reservationWorkbench.share.manualShare')
}

async function openWhatsApp(): Promise<void> {
  const info = await ensureShareInfo()
  if (!info?.canOpenWhatsAppLink || !info.whatsappLink) {
    shareInfoErrorText.value = info?.customerPhoneAvailable
      ? t('reservationWorkbench.share.phoneUnavailable')
      : t('reservationWorkbench.share.phoneMissing')
    return
  }

  resetShareFeedback()
  await recordShareIntent('whatsapp')
  shareInfoShared.value = true
  shareInfoStatusText.value = t('reservationWorkbench.share.whatsappOpening', {
    sender: info.senderLabel || t('reservationWorkbench.share.defaultSender')
  })
  window.location.href = info.whatsappLink
}

async function openWechat(): Promise<void> {
  const info = await ensureShareInfo()
  const url = info ? reservationShareUrl(info) : ''
  const text = info ? reservationWechatShareText(info, url) : ''
  if (!info || !text) {
    shareInfoErrorText.value = t('reservationWorkbench.share.noText')
    return
  }

  resetShareFeedback()
  if (!(await copyPlainText(text))) {
    shareInfoFallbackText.value = text
    shareInfoErrorText.value = t('reservationWorkbench.share.manualTextCopy')
    return
  }
  await recordShareIntent('wechat')
  shareInfoShared.value = true
  shareInfoStatusText.value = t('reservationWorkbench.share.wechatOpening')
  if (info.wechatLink) {
    window.location.href = info.wechatLink
  }
}

async function copyReservationShareLink(): Promise<void> {
  const info = await ensureShareInfo()
  const url = info ? reservationShareUrl(info) : ''
  if (!info || !url) {
    shareInfoErrorText.value = t('reservationWorkbench.share.noCopyLink')
    return
  }

  resetShareFeedback()
  if (await copyPlainText(url)) {
    await recordShareIntent('copy_link')
    shareInfoShared.value = true
    shareInfoStatusText.value = t('reservationWorkbench.share.copied')
    return
  }

  shareInfoFallbackText.value = url
  shareInfoErrorText.value = t('reservationWorkbench.share.manualCopyLink')
}

async function ensureShareInfo(): Promise<ReservationShareInfo | null> {
  if (!shareInfo.value) {
    await loadReservationShareInfo()
  }

  return shareInfo.value
}

async function recordShareIntent(channel: ReservationShareIntentChannel): Promise<void> {
  try {
    await recordReservationShareIntent(props.storeId, props.item.reservationId, channel, activeLocale.value)
  } catch {
    // Sending should remain possible even when audit recording is temporarily unavailable.
  }
}

function resetShareFeedback(): void {
  shareInfoErrorText.value = ''
  shareInfoShared.value = false
  shareInfoStatusText.value = ''
  shareInfoFallbackText.value = ''
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
    const response = await getReservationShareInfo(props.storeId, props.item.reservationId, activeLocale.value)
    shareInfo.value = response.shareInfo
  } catch (error) {
    shareInfo.value = null
    shareInfoErrorText.value = error instanceof ReservationShareInfoApiError
      ? t('reservationWorkbench.share.loadFailed')
      : t('reservationWorkbench.share.loadFailed')
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
  return value?.trim() ? value : t('reservationWorkbench.item.unset')
}

function queueTicketDisplayText(item: ReservationTodayViewItem): string {
  const displayNumber =
    item.queueTicketDisplayNumber?.trim() ||
    (typeof item.queueTicketNumber === 'number' ? String(item.queueTicketNumber) : '')

  return displayNumber ? `#${displayNumber}` : t('reservationWorkbench.item.queueTicket')
}

function reservationShareUrl(info: ReservationShareInfo): string {
  const path = info.sharePath?.trim() || (info.shareToken ? `/reservation-share/${info.shareToken}` : '')
  return path ? new URL(path, window.location.origin).toString() : ''
}

function resourceLabel(type: string | null | undefined): string {
  if (type === 'table_group') {
    return t('reservationWorkbench.item.tableGroup')
  }

  if (type === 'dining_table') {
    return t('reservationWorkbench.item.diningTable')
  }

  return t('reservationWorkbench.item.tableResource')
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
      <p>{{ $t('reservationWorkbench.item.partySizeSummary', { count: item.partySize, table: tableAssignmentText }) }}</p>
      <p v-if="queueAssignmentText">{{ queueAssignmentText }}</p>
    </div>

    <div class="reservation-today-list-item__actions" :aria-label="$t('reservationWorkbench.item.actionsAria')">
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
        @whatsapp-requested="openWhatsApp"
        @wechat-requested="openWechat"
        @system-share-requested="shareReservationLink"
        @copy-requested="copyReservationShareLink"
      />

      <button
        v-if="showCheckIn"
        class="reservation-today-list-item__action reservation-today-list-item__action--primary"
        :disabled="!canCheckIn || isCheckingIn"
        :title="currentDayActionTitle"
        type="button"
        @click="requestCheckIn"
      >
        {{ isCheckingIn ? $t('reservationWorkbench.item.checkingIn') : $t('reservationWorkbench.item.checkIn') }}
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
        {{ isNoShowing ? $t('reservationWorkbench.item.noShowing') : $t('reservationWorkbench.item.noShow') }}
      </button>

      <button
        v-if="canCancel"
        class="reservation-today-list-item__action reservation-today-list-item__action--danger"
        :disabled="isCancelling"
        :title="$t('reservationWorkbench.item.cancelTitle')"
        type="button"
        @click="requestCancel"
      >
        {{ isCancelling ? $t('reservationWorkbench.item.cancelling') : $t('common.actions.cancel') }}
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
