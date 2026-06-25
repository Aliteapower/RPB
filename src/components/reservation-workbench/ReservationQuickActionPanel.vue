<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink } from 'vue-router'

interface ReservationQuickAction {
  label: string
  symbol: string
  tone: string
  disabledTitle?: string
  disabled?: boolean
  routeName?: string
  includeBusinessDateQuery?: boolean
  action?: 'create-reservation'
}

const props = defineProps<{
  canCreateReservationForSelectedDate: boolean
  storeId: string
  selectedDate: string
}>()

const emit = defineEmits<{
  'open-create-reservation': []
}>()

const entries = computed<ReservationQuickAction[]>(() => [
  {
    label: '创建预约',
    action: 'create-reservation',
    disabled: !props.canCreateReservationForSelectedDate,
    disabledTitle: '过去日期不可创建预约',
    symbol: '约',
    tone: 'primary'
  },
  {
    label: '现场取号',
    routeName: 'walk-in-queue',
    symbol: '取',
    tone: 'plain'
  },
  {
    label: '预约转排队',
    routeName: 'reservation-arrived-to-queue',
    includeBusinessDateQuery: true,
    symbol: '排',
    tone: 'plain'
  }
])

function actionKey(entry: ReservationQuickAction): string {
  return entry.routeName ?? entry.action ?? entry.label
}

function routeFor(entry: ReservationQuickAction) {
  return {
    name: entry.routeName,
    params: {
      storeId: props.storeId
    },
    query: entry.includeBusinessDateQuery && props.selectedDate
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
          :to="routeFor(entry)"
        >
          <span class="reservation-actions__symbol" aria-hidden="true">{{ entry.symbol }}</span>
          <strong>{{ entry.label }}</strong>
        </RouterLink>

        <button
          v-else
          class="reservation-actions__entry"
          :class="`reservation-actions__entry--${entry.tone}`"
          :aria-disabled="entry.disabled ? 'true' : 'false'"
          :disabled="entry.disabled"
          :title="entry.disabled ? entry.disabledTitle : undefined"
          type="button"
          @click="triggerAction(entry)"
        >
          <span class="reservation-actions__symbol" aria-hidden="true">{{ entry.symbol }}</span>
          <strong>{{ entry.label }}</strong>
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
  gap: 10px;
  padding: 12px;
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
  gap: 8px;
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.reservation-actions__entry {
  align-items: center;
  background: #ffffff;
  border: 1px solid #dbe3ee;
  border-radius: 8px;
  color: #0f172a;
  display: grid;
  gap: 6px;
  justify-items: center;
  min-height: 76px;
  min-width: 0;
  padding: 10px 4px;
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
  font-size: 0.84rem;
  font-weight: 950;
  height: 30px;
  justify-content: center;
  width: 30px;
}

.reservation-actions__entry strong {
  font-size: 0.82rem;
  font-weight: 950;
  line-height: 1.15;
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
