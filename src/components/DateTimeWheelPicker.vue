<script setup lang="ts">
import { computed, reactive, watch } from 'vue'

const props = withDefaults(
  defineProps<{
    label: string
    modelValue: string
    name: string
    yearSpan?: number
  }>(),
  {
    yearSpan: 5
  }
)

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

type DateTimePickerState = {
  year: number
  month: number
  day: number
  hour: number
  minute: number
}

const CURRENT_YEAR = new Date().getFullYear()
const MONTH_OPTIONS = Array.from({ length: 12 }, (_, index) => index + 1)
const HOUR_OPTIONS = Array.from({ length: 24 }, (_, index) => index)
const MINUTE_OPTIONS = Array.from({ length: 12 }, (_, index) => index * 5)

const picker = reactive(toPickerState(props.modelValue))
const yearOptions = computed(() => {
  const years = new Set(Array.from({ length: props.yearSpan }, (_, index) => CURRENT_YEAR + index))
  years.add(picker.year)
  return Array.from(years).sort((left, right) => left - right)
})
const dayOptions = computed(() => {
  return Array.from({ length: daysInMonth(picker.year, picker.month) }, (_, index) => index + 1)
})
const pickerDisplay = computed(() => {
  return `${pad2(picker.day)}-${pad2(picker.month)}-${picker.year} ${pad2(picker.hour)}:${pad2(picker.minute)}`
})

watch(
  () => props.modelValue,
  (value) => {
    if (value && value !== toDateTimeLocalValue(picker)) {
      setPickerFromValue(value)
    }
  }
)

function onPickerChanged(): void {
  syncPickerDate()
  emit('update:modelValue', toDateTimeLocalValue(picker))
}

function setPickerFromValue(value: string): void {
  const nextPicker = toPickerState(value)
  picker.year = nextPicker.year
  picker.month = nextPicker.month
  picker.day = nextPicker.day
  picker.hour = nextPicker.hour
  picker.minute = nextPicker.minute
  syncPickerDate()
}

function syncPickerDate(): void {
  const maxDay = daysInMonth(picker.year, picker.month)

  if (picker.day > maxDay) {
    picker.day = maxDay
  }
}

function toPickerState(value: string): DateTimePickerState {
  const date = new Date(value)
  const validDate = Number.isNaN(date.getTime()) ? new Date() : date

  return {
    year: validDate.getFullYear(),
    month: validDate.getMonth() + 1,
    day: validDate.getDate(),
    hour: validDate.getHours(),
    minute: roundToMinuteStep(validDate.getMinutes())
  }
}

function toDateTimeLocalValue(value: DateTimePickerState): string {
  return [
    `${value.year}-${pad2(value.month)}-${pad2(value.day)}`,
    `${pad2(value.hour)}:${pad2(value.minute)}`
  ].join('T')
}

function daysInMonth(year: number, month: number): number {
  return new Date(year, month, 0).getDate()
}

function roundToMinuteStep(minute: number): number {
  const rounded = Math.round(minute / 5) * 5
  return rounded === 60 ? 55 : rounded
}

function pad2(value: number): string {
  return String(value).padStart(2, '0')
}
</script>

<template>
  <section class="date-time-field" :aria-label="label">
    <div class="field-heading">
      <span>{{ label }}</span>
      <strong>{{ pickerDisplay }}</strong>
    </div>
    <input :name="name" type="hidden" :value="modelValue" />
    <div class="picker-row date-picker-row" :aria-label="`${label} ${$t('common.dateTime.date')}`">
      <label class="picker-column">
        <span>{{ $t('common.dateTime.year') }}</span>
        <select v-model.number="picker.year" @change="onPickerChanged">
          <option v-for="year in yearOptions" :key="year" :value="year">
            {{ year }}
          </option>
        </select>
      </label>
      <label class="picker-column">
        <span>{{ $t('common.dateTime.month') }}</span>
        <select v-model.number="picker.month" @change="onPickerChanged">
          <option v-for="month in MONTH_OPTIONS" :key="month" :value="month">
            {{ pad2(month) }}
          </option>
        </select>
      </label>
      <label class="picker-column">
        <span>{{ $t('common.dateTime.day') }}</span>
        <select v-model.number="picker.day" @change="onPickerChanged">
          <option v-for="day in dayOptions" :key="day" :value="day">
            {{ pad2(day) }}
          </option>
        </select>
      </label>
    </div>
    <div class="picker-row time-picker-row" :aria-label="`${label} ${$t('common.dateTime.time')}`">
      <label class="picker-column">
        <span>{{ $t('common.dateTime.hour') }}</span>
        <select v-model.number="picker.hour" @change="onPickerChanged">
          <option v-for="hour in HOUR_OPTIONS" :key="hour" :value="hour">
            {{ pad2(hour) }}
          </option>
        </select>
      </label>
      <label class="picker-column">
        <span>{{ $t('common.dateTime.minute') }}</span>
        <select v-model.number="picker.minute" @change="onPickerChanged">
          <option v-for="minute in MINUTE_OPTIONS" :key="minute" :value="minute">
            {{ pad2(minute) }}
          </option>
        </select>
      </label>
    </div>
  </section>
</template>

<style scoped>
.date-time-field {
  display: grid;
  gap: 10px;
}

.field-heading {
  display: grid;
  gap: 5px;
}

.field-heading span,
.picker-column span {
  color: #41516a;
  font-size: 0.78rem;
  font-weight: 800;
}

.field-heading strong {
  color: #14213d;
  font-size: 1.06rem;
  overflow-wrap: anywhere;
}

.picker-row {
  display: grid;
  gap: 8px;
}

.date-picker-row {
  grid-template-columns: 1.2fr 0.9fr 0.9fr;
}

.time-picker-row {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.picker-column {
  display: grid;
  gap: 5px;
  min-width: 0;
}

.picker-column select {
  appearance: none;
  background: #ffffff;
  border: 1px solid #c8d3e2;
  border-radius: 6px;
  color: #182536;
  font-size: 1.05rem;
  font-weight: 800;
  min-height: 50px;
  outline: none;
  padding: 10px 8px;
  text-align: center;
  width: 100%;
}

.picker-column select:focus {
  border-color: #2563eb;
  box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.14);
}
</style>
