<script setup lang="ts">
import { computed } from 'vue'

import type { ReservationTodayViewItem } from '../../types/reservationTodayView'

const actionRoutes = {
  checkIn: {
    label: '到店',
    routeName: 'reservation-check-in'
  },
  directSeating: {
    label: '入桌',
    routeName: 'reservation-arrived-direct-seating'
  }
} as const

const cancellableStatuses = new Set(['draft', 'confirmed'])

const statusLabels: Record<string, string> = {
  confirmed: '待确认',
  arrived: '已到店',
  seated: '已入桌',
  cancelled: '已取消',
  no_show: '爽约',
  completed: '已完成',
  draft: '草稿'
}

const props = defineProps<{
  canCancelReservation: boolean
  item: ReservationTodayViewItem
  isCancelling?: boolean
  storeId: string
  storeTimezone: string
}>()

const emit = defineEmits<{
  'cancel-requested': [item: ReservationTodayViewItem]
}>()

const customerName = computed(() => {
  const values = [props.item.customerName, props.item.customerNickname].filter(Boolean)
  return values.length ? values.join(' / ') : props.item.reservationCode
})

const phoneDisplay = computed(() => optionalDisplay(props.item.phoneMasked))
const timeRange = computed(
  () => `${formatStoreDateTime(props.item.reservedStartAt)} - ${formatStoreDateTime(props.item.reservedEndAt)}`
)
const statusText = computed(() => statusLabels[props.item.status] ?? props.item.status)
const statusClass = computed(() => `status-${props.item.status.replace(/_/g, '-')}`)
const canCheckIn = computed(() => props.item.status === 'confirmed')
const canSeat = computed(() => props.item.status === 'arrived')
const canCancel = computed(
  () => props.canCancelReservation && cancellableStatuses.has(props.item.status)
)

function checkInRoute() {
  return {
    name: actionRoutes.checkIn.routeName,
    params: {
      storeId: props.storeId
    },
    query: {
      reservationId: props.item.reservationId
    }
  }
}

function directSeatingRoute() {
  return {
    name: actionRoutes.directSeating.routeName,
    params: {
      storeId: props.storeId
    },
    query: {
      reservationId: props.item.reservationId
    }
  }
}

function requestCancel(): void {
  emit('cancel-requested', props.item)
}

function formatStoreDateTime(value: string): string {
  const date = new Date(value)

  if (Number.isNaN(date.getTime())) {
    return value
  }

  const parts = new Intl.DateTimeFormat('zh-CN', {
    timeZone: props.storeTimezone,
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false
  }).formatToParts(date)

  const part = (type: string) => parts.find(item => item.type === type)?.value ?? ''
  return `${part('month')}-${part('day')} ${part('hour')}:${part('minute')}`
}

function optionalDisplay(value: string | null | undefined): string {
  return value?.trim() ? value : '未填写'
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
      <p>{{ item.partySize }}人 · 桌号：未分配</p>
    </div>

    <div class="reservation-today-list-item__actions" aria-label="预约操作">
      <span class="reservation-today-list-item__status" :class="statusClass">
        {{ statusText }}
      </span>

      <RouterLink
        v-if="canCheckIn"
        class="reservation-today-list-item__action reservation-today-list-item__action--primary"
        :to="checkInRoute()"
      >
        {{ actionRoutes.checkIn.label }}
      </RouterLink>

      <RouterLink
        v-if="canSeat"
        class="reservation-today-list-item__action reservation-today-list-item__action--primary"
        :to="directSeatingRoute()"
      >
        {{ actionRoutes.directSeating.label }}
      </RouterLink>

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
  max-width: 164px;
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
  background: #f97316;
  color: #ffffff;
}

.reservation-today-list-item__action--danger {
  background: #ef4444;
  border: 0;
  color: #ffffff;
}

.reservation-today-list-item__action--danger:disabled {
  background: #fca5a5;
  cursor: progress;
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
