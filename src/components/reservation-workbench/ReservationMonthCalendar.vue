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
  past: boolean
  reservationCount: number
}

const props = withDefaults(
  defineProps<{
    selectedDate: string
    calendarLabel?: string
    markedDates?: string[]
    minDate?: string
    reservationCounts?: Record<string, number>
    showCountInAria?: boolean
  }>(),
  {
    calendarLabel: '预约日历',
    markedDates: () => [],
    minDate: '',
    reservationCounts: () => ({}),
    showCountInAria: true
  }
)

const emit = defineEmits<{
  'update:selectedDate': [value: string]
  'visible-month-changed': [value: string]
}>()

const visibleMonth = ref(toMonthDate(props.selectedDate))
const markedDateSet = computed(() => new Set(props.markedDates))
const visibleMonthKey = computed(() => monthKey(visibleMonth.value))

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
    const reservationCount = props.reservationCounts[isoDate] ?? 0

    days.push({
      date,
      isoDate,
      dayNumber: date.getDate(),
      currentMonth: date.getMonth() === month,
      selected: props.selectedDate === isoDate,
      today: today === isoDate,
      marked: markedDateSet.value.has(isoDate) || reservationCount > 0,
      past: !!props.minDate && isoDate < props.minDate,
      reservationCount
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
      emitVisibleMonth()
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
  emitVisibleMonth()
}

function nextMonth(): void {
  visibleMonth.value = new Date(
    visibleMonth.value.getFullYear(),
    visibleMonth.value.getMonth() + 1,
    1
  )
  emitVisibleMonth()
}

function emitVisibleMonth(): void {
  emit('visible-month-changed', visibleMonthKey.value)
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

function monthKey(date: Date): string {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  return `${year}-${month}`
}

function formatDate(date: Date): string {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function reservationCountLabel(count: number): string {
  return count > 99 ? '99+' : String(count)
}

function dayAriaLabel(day: CalendarDay): string {
  const prefix = day.selected ? '已选择' : '选择'
  const reservationText = props.showCountInAria
    ? day.reservationCount > 0
      ? `，${day.reservationCount} 个预订`
      : '，暂无预订'
    : ''
  const dateLimitText = day.past ? '，不可创建新预约' : ''
  return `${prefix} ${day.isoDate}${reservationText}${dateLimitText}`
}
</script>

<template>
  <section class="reservation-calendar" :aria-label="calendarLabel">
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
          'is-past': day.past,
          'is-selected': day.selected,
          'is-today': day.today,
          'has-reservation': day.marked
        }"
        :aria-label="dayAriaLabel(day)"
        type="button"
        @click="selectDate(day)"
      >
        <span>{{ day.dayNumber }}</span>
        <span
          v-if="day.reservationCount > 0"
          class="reservation-calendar__reservation-count"
          aria-hidden="true"
        >
          {{ reservationCountLabel(day.reservationCount) }}
        </span>
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
  flex-direction: column;
  font-size: 0.88rem;
  font-weight: 850;
  height: 42px;
  justify-content: center;
  justify-self: center;
  position: relative;
  width: 42px;
}

.reservation-calendar__day > span:first-child {
  line-height: 1;
}

.reservation-calendar__day.is-muted {
  color: #cbd5e1;
}

.reservation-calendar__day.is-past {
  color: #94a3b8;
}

.reservation-calendar__day.is-past::after {
  background: #cbd5e1;
}

.reservation-calendar__day.is-today {
  border-color: #f97316;
}

.reservation-calendar__day.is-selected {
  background: #f97316;
  border-color: #f97316;
  color: #ffffff;
}

.reservation-calendar__reservation-count {
  align-items: center;
  background: #ef4444;
  border-radius: 999px;
  color: #ffffff;
  display: inline-flex;
  font-size: 0.55rem;
  font-weight: 950;
  height: 13px;
  justify-content: center;
  line-height: 1;
  min-width: 13px;
  padding: 0 3px;
  left: 50%;
  letter-spacing: 0;
  position: absolute;
  top: 25px;
  transform: translateX(-50%);
}

.reservation-calendar__day.is-selected .reservation-calendar__reservation-count {
  background: #ffffff;
  color: #ef4444;
}

.reservation-calendar__day:focus-visible {
  outline: 3px solid rgba(249, 115, 22, 0.28);
  outline-offset: 2px;
}
</style>
