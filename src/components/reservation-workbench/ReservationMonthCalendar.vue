<script setup lang="ts">
import { computed, ref, watch } from 'vue'

interface CalendarDay {
  date: Date
  isoDate: string
  dayNumber: number
  currentMonth: boolean
  selected: boolean
  today: boolean
  marked: boolean
}

const props = withDefaults(
  defineProps<{
    selectedDate: string
    markedDates?: string[]
  }>(),
  {
    markedDates: () => []
  }
)

const emit = defineEmits<{
  'update:selectedDate': [value: string]
}>()

const visibleMonth = ref(toMonthDate(props.selectedDate))
const markedDateSet = computed(() => new Set(props.markedDates))

const monthTitle = computed(() => {
  const year = visibleMonth.value.getFullYear()
  const month = visibleMonth.value.getMonth() + 1
  return `${year}年${month}月`
})

const monthDays = computed<CalendarDay[]>(() => {
  const year = visibleMonth.value.getFullYear()
  const month = visibleMonth.value.getMonth()
  const firstDay = new Date(year, month, 1)
  const today = formatDate(new Date())
  const days: CalendarDay[] = []

  for (let index = 0; index < 42; index += 1) {
    const date = new Date(year, month, 1 - firstDay.getDay() + index)
    const isoDate = formatDate(date)

    days.push({
      date,
      isoDate,
      dayNumber: date.getDate(),
      currentMonth: date.getMonth() === month,
      selected: props.selectedDate === isoDate,
      today: today === isoDate,
      marked: markedDateSet.value.has(isoDate)
    })
  }

  return days
})

watch(
  () => props.selectedDate,
  value => {
    const selectedMonth = toMonthDate(value)
    if (!sameMonth(selectedMonth, visibleMonth.value)) {
      visibleMonth.value = selectedMonth
    }
  }
)

function selectDate(day: CalendarDay): void {
  emit('update:selectedDate', day.isoDate)
}

function previousMonth(): void {
  visibleMonth.value = new Date(
    visibleMonth.value.getFullYear(),
    visibleMonth.value.getMonth() - 1,
    1
  )
}

function nextMonth(): void {
  visibleMonth.value = new Date(
    visibleMonth.value.getFullYear(),
    visibleMonth.value.getMonth() + 1,
    1
  )
}

function toMonthDate(value: string): Date {
  const date = parseDate(value) ?? new Date()
  return new Date(date.getFullYear(), date.getMonth(), 1)
}

function parseDate(value: string): Date | null {
  if (!value) {
    return null
  }

  const [year, month, day] = value.split('-').map(Number)
  if (!year || !month || !day) {
    return null
  }

  return new Date(year, month - 1, day)
}

function sameMonth(left: Date, right: Date): boolean {
  return left.getFullYear() === right.getFullYear() && left.getMonth() === right.getMonth()
}

function formatDate(date: Date): string {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}
</script>

<template>
  <section class="reservation-calendar" aria-label="预约日历">
    <header class="reservation-calendar__header">
      <button type="button" aria-label="上个月" @click="previousMonth">‹</button>
      <h2>{{ monthTitle }}</h2>
      <button type="button" aria-label="下个月" @click="nextMonth">›</button>
    </header>

    <div class="reservation-calendar__weekdays" aria-hidden="true">
      <span>日</span>
      <span>一</span>
      <span>二</span>
      <span>三</span>
      <span>四</span>
      <span>五</span>
      <span>六</span>
    </div>

    <div class="reservation-calendar__grid">
      <button
        v-for="day in monthDays"
        :key="day.isoDate"
        class="reservation-calendar__day"
        :class="{
          'is-muted': !day.currentMonth,
          'is-selected': day.selected,
          'is-today': day.today,
          'has-reservation': day.marked
        }"
        type="button"
        @click="selectDate(day)"
      >
        <span>{{ day.dayNumber }}</span>
      </button>
    </div>
  </section>
</template>

<style scoped>
.reservation-calendar {
  background: #ffffff;
  border: 1px solid #dbe3ee;
  border-radius: 8px;
  box-shadow: 0 8px 22px rgba(15, 23, 42, 0.06);
  display: grid;
  gap: 12px;
  padding: 14px 16px 18px;
}

.reservation-calendar__header {
  align-items: center;
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
}

.reservation-calendar__header h2 {
  color: #0f172a;
  font-size: 1.05rem;
  letter-spacing: 0;
  margin: 0;
  text-align: center;
}

.reservation-calendar__header button {
  align-items: center;
  background: #eef2f7;
  border: 0;
  border-radius: 999px;
  color: #0f172a;
  display: inline-flex;
  font-size: 1.6rem;
  font-weight: 900;
  height: 34px;
  justify-content: center;
  line-height: 1;
  width: 34px;
}

.reservation-calendar__weekdays,
.reservation-calendar__grid {
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
}

.reservation-calendar__weekdays span {
  color: #64748b;
  font-size: 0.78rem;
  font-weight: 900;
  min-height: 28px;
  text-align: center;
}

.reservation-calendar__day {
  align-items: center;
  background: transparent;
  border: 1px solid transparent;
  border-radius: 999px;
  color: #0f172a;
  display: inline-flex;
  font-size: 0.88rem;
  font-weight: 850;
  height: 42px;
  justify-content: center;
  justify-self: center;
  position: relative;
  width: 42px;
}

.reservation-calendar__day.is-muted {
  color: #cbd5e1;
}

.reservation-calendar__day.is-today {
  border-color: #f97316;
}

.reservation-calendar__day.is-selected {
  background: #f97316;
  border-color: #f97316;
  color: #ffffff;
}

.reservation-calendar__day.has-reservation::after {
  background: #f97316;
  border-radius: 999px;
  bottom: 5px;
  content: '';
  height: 4px;
  left: 50%;
  position: absolute;
  transform: translateX(-50%);
  width: 4px;
}

.reservation-calendar__day.is-selected.has-reservation::after {
  background: #ffffff;
}

.reservation-calendar__day:focus-visible {
  outline: 3px solid rgba(249, 115, 22, 0.28);
  outline-offset: 2px;
}
</style>
