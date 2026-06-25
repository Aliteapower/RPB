<script setup lang="ts">
import { computed, ref, watch } from 'vue'

import ReservationMonthCalendar from '../reservation-workbench/ReservationMonthCalendar.vue'

const props = withDefaults(
  defineProps<{
    selectedDate: string
    todayDate: string
    calendarLabel?: string
    reservationCounts?: Record<string, number>
  }>(),
  {
    calendarLabel: '业务日期日历',
    reservationCounts: () => ({})
  }
)

const emit = defineEmits<{
  'update:selectedDate': [value: string]
  'visible-month-changed': [value: string]
}>()

const calendarOpen = ref(false)

const dateTone = computed(() => {
  if (props.selectedDate > props.todayDate) {
    return 'future'
  }

  if (props.selectedDate < props.todayDate) {
    return 'past'
  }

  return 'today'
})

const dateLabel = computed(() => {
  if (dateTone.value === 'future') {
    return `未来日期 ${props.selectedDate}`
  }

  if (dateTone.value === 'past') {
    return `过去日期 ${props.selectedDate}`
  }

  return `今日 ${props.selectedDate}`
})

const statusLabel = computed(() => {
  if (dateTone.value === 'future') {
    return '规划模式'
  }

  if (dateTone.value === 'past') {
    return '历史查看'
  }

  return '营业中'
})

const changeButtonLabel = computed(() => (dateTone.value === 'today' ? '切换日期' : '改日期'))

watch(
  () => props.selectedDate,
  () => {
    calendarOpen.value = false
  }
)

function toggleCalendar(): void {
  calendarOpen.value = !calendarOpen.value
}

function selectDate(value: string): void {
  emit('update:selectedDate', value)
  calendarOpen.value = false
}

function resetToToday(): void {
  emit('update:selectedDate', props.todayDate)
  calendarOpen.value = false
}
</script>

<template>
  <section class="business-date-switcher" :class="`business-date-switcher--${dateTone}`">
    <div class="business-date-switcher__bar" aria-label="业务日期">
      <div class="business-date-switcher__state">
        <span class="business-date-switcher__dot" aria-hidden="true"></span>
        <strong>{{ dateLabel }}</strong>
        <small>{{ statusLabel }}</small>
      </div>

      <div class="business-date-switcher__actions">
        <button
          v-if="dateTone !== 'today'"
          class="business-date-switcher__secondary"
          type="button"
          @click="resetToToday"
        >
          回到今日
        </button>
        <button
          class="business-date-switcher__primary"
          type="button"
          :aria-expanded="calendarOpen"
          @click="toggleCalendar"
        >
          {{ changeButtonLabel }}
        </button>
      </div>
    </div>

    <ReservationMonthCalendar
      v-if="calendarOpen"
      :selected-date="selectedDate"
      :calendar-label="calendarLabel"
      :min-date="todayDate"
      :reservation-counts="reservationCounts"
      @update:selected-date="selectDate"
      @visible-month-changed="emit('visible-month-changed', $event)"
    />
  </section>
</template>

<style scoped>
.business-date-switcher {
  background: #ffffff;
  border: 1px solid #dbe3ee;
  border-radius: 8px;
  display: grid;
  gap: 10px;
  padding: 10px;
}

.business-date-switcher__bar {
  align-items: center;
  display: grid;
  gap: 10px;
  grid-template-columns: minmax(0, 1fr) auto;
  min-height: 42px;
}

.business-date-switcher__state {
  align-items: center;
  display: flex;
  gap: 8px;
  min-width: 0;
}

.business-date-switcher__dot {
  background: #f97316;
  border-radius: 999px;
  flex: 0 0 auto;
  height: 8px;
  width: 8px;
}

.business-date-switcher__state strong {
  color: #0f172a;
  font-size: 0.92rem;
  font-weight: 950;
  overflow-wrap: anywhere;
}

.business-date-switcher__state small {
  background: #fff7ed;
  border-radius: 999px;
  color: #c2410c;
  flex: 0 0 auto;
  font-size: 0.72rem;
  font-weight: 950;
  padding: 4px 8px;
}

.business-date-switcher__actions {
  display: flex;
  gap: 8px;
}

.business-date-switcher__actions button {
  border-radius: 999px;
  font: inherit;
  font-size: 0.76rem;
  font-weight: 950;
  min-height: 34px;
  padding: 0 12px;
  white-space: nowrap;
}

.business-date-switcher__primary {
  background: #fff7ed;
  border: 1px solid #fdba74;
  color: #c2410c;
}

.business-date-switcher__secondary {
  background: #eff6ff;
  border: 1px solid #bfdbfe;
  color: #1d4ed8;
}

.business-date-switcher--future {
  border-color: #bfdbfe;
}

.business-date-switcher--future .business-date-switcher__dot {
  background: #2563eb;
}

.business-date-switcher--future .business-date-switcher__state small {
  background: #eff6ff;
  color: #1d4ed8;
}

.business-date-switcher--past {
  border-color: #cbd5e1;
}

.business-date-switcher--past .business-date-switcher__dot {
  background: #94a3b8;
}

.business-date-switcher--past .business-date-switcher__state small {
  background: #f1f5f9;
  color: #64748b;
}

.business-date-switcher :deep(.reservation-calendar) {
  box-shadow: none;
}

.business-date-switcher button:focus-visible {
  outline: 3px solid rgba(249, 115, 22, 0.28);
  outline-offset: 2px;
}

@media (max-width: 420px) {
  .business-date-switcher__bar {
    align-items: stretch;
    grid-template-columns: minmax(0, 1fr);
  }

  .business-date-switcher__actions {
    justify-content: flex-end;
  }
}
</style>
