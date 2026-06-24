<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink } from 'vue-router'

interface ReservationQuickAction {
  label: string
  subtitle: string
  symbol: string
  tone: string
  disabled?: boolean
  routeName?: string
  action?: 'create-reservation' | 'show-confirmed-reservations' | 'show-arrived-reservations'
}

const props = defineProps<{
  canCreateReservationForSelectedDate: boolean
  storeId: string
  selectedDate: string
}>()

const emit = defineEmits<{
  'open-create-reservation': []
  'show-confirmed-reservations': []
  'show-arrived-reservations': []
}>()

const entries = computed<ReservationQuickAction[]>(() => [
  {
    label: '预约到店',
    subtitle: '确认预约客人已到店',
    action: 'show-confirmed-reservations',
    symbol: '到',
    tone: 'primary'
  },
  {
    label: '创建预约',
    subtitle: props.canCreateReservationForSelectedDate ? '登记新的门店预约' : '过去日期不可创建预约',
    action: 'create-reservation',
    disabled: !props.canCreateReservationForSelectedDate,
    symbol: '约',
    tone: 'plain'
  },
  {
    label: '预约排队',
    subtitle: '已到店预约进入排队',
    routeName: 'reservation-arrived-to-queue',
    symbol: '排',
    tone: 'plain'
  },
  {
    label: '预约入座',
    subtitle: '为已到店预约安排桌台',
    action: 'show-arrived-reservations',
    symbol: '入',
    tone: 'plain'
  }
])

function actionKey(entry: ReservationQuickAction): string {
  return entry.routeName ?? entry.action ?? entry.label
}

function routeFor(routeName: string) {
  return {
    name: routeName,
    params: {
      storeId: props.storeId
    },
    query: props.selectedDate
      ? {
          businessDate: props.selectedDate
        }
      : undefined
  }
}

function triggerAction(entry: ReservationQuickAction): void {
  if (entry.disabled) {
    return
  }

  if (entry.action === 'create-reservation') {
    emit('open-create-reservation')
  }

  if (entry.action === 'show-confirmed-reservations') {
    emit('show-confirmed-reservations')
  }

  if (entry.action === 'show-arrived-reservations') {
    emit('show-arrived-reservations')
  }
}
</script>

<template>
  <section class="reservation-actions" aria-label="预约管理">
    <header>
      <span></span>
      <h2>预约管理</h2>
    </header>

    <div class="reservation-actions__grid">
      <template v-for="entry in entries" :key="actionKey(entry)">
        <RouterLink
          v-if="entry.routeName"
          class="reservation-actions__entry"
          :class="`reservation-actions__entry--${entry.tone}`"
          :to="routeFor(entry.routeName)"
        >
          <span class="reservation-actions__symbol" aria-hidden="true">{{ entry.symbol }}</span>
          <strong>{{ entry.label }}</strong>
          <small>{{ entry.subtitle }}</small>
        </RouterLink>

        <button
          v-else
          class="reservation-actions__entry"
          :class="`reservation-actions__entry--${entry.tone}`"
          :aria-disabled="entry.disabled ? 'true' : 'false'"
          :disabled="entry.disabled"
          :title="entry.disabled ? entry.subtitle : undefined"
          type="button"
          @click="triggerAction(entry)"
        >
          <span class="reservation-actions__symbol" aria-hidden="true">{{ entry.symbol }}</span>
          <strong>{{ entry.label }}</strong>
          <small>{{ entry.subtitle }}</small>
        </button>
      </template>
    </div>
  </section>
</template>

<style scoped>
.reservation-actions {
  background: #ffffff;
  border: 1px solid #dbe3ee;
  border-radius: 8px;
  box-shadow: 0 8px 22px rgba(15, 23, 42, 0.06);
  display: grid;
  gap: 12px;
  padding: 14px;
}

.reservation-actions header {
  align-items: center;
  display: flex;
  gap: 8px;
}

.reservation-actions header span {
  background: #f97316;
  border-radius: 999px;
  height: 8px;
  width: 8px;
}

.reservation-actions h2 {
  color: #14213d;
  font-size: 1rem;
  letter-spacing: 0;
  margin: 0;
}

.reservation-actions__grid {
  display: grid;
  gap: 10px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.reservation-actions__entry {
  align-items: center;
  background: #ffffff;
  border: 1px solid #dbe3ee;
  border-radius: 8px;
  color: #0f172a;
  display: grid;
  gap: 5px;
  justify-items: center;
  min-height: 100px;
  min-width: 0;
  padding: 12px 8px;
  text-align: center;
  text-decoration: none;
}

.reservation-actions__entry--primary {
  background: #fff7ed;
  border-color: #fb923c;
}

.reservation-actions__symbol {
  align-items: center;
  background: #ffedd5;
  border-radius: 999px;
  color: #f97316;
  display: inline-flex;
  font-size: 0.9rem;
  font-weight: 950;
  height: 34px;
  justify-content: center;
  width: 34px;
}

.reservation-actions__entry strong {
  font-size: 0.92rem;
  font-weight: 950;
}

.reservation-actions__entry small {
  color: #315f91;
  font-size: 0.74rem;
  font-weight: 800;
  line-height: 1.3;
}

.reservation-actions__entry:focus-visible {
  outline: 3px solid rgba(249, 115, 22, 0.28);
  outline-offset: 2px;
}

.reservation-actions__entry:disabled {
  background: #f8fafc;
  border-color: #dbe3ee;
  color: #94a3b8;
  cursor: not-allowed;
}

.reservation-actions__entry:disabled .reservation-actions__symbol {
  background: #e2e8f0;
  color: #94a3b8;
}
</style>
