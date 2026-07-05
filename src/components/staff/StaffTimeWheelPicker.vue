<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'

const props = withDefaults(
  defineProps<{
    label: string
    modelValue: string
    name: string
    disabled?: boolean
  }>(),
  {
    disabled: false
  }
)

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

const HOUR_OPTIONS = Array.from({ length: 24 }, (_, index) => index)
const MINUTE_OPTIONS = Array.from({ length: 60 }, (_, index) => index)

const picker = reactive(toPickerState(props.modelValue))
const isOpen = ref(false)
const { t } = useI18n()
const fieldId = `time-wheel-picker-${Math.random().toString(36).slice(2)}`
const panelId = `${fieldId}-panel`

const displayValue = computed(() => (isValidTime(props.modelValue) ? props.modelValue : '--:--'))

watch(
  () => props.modelValue,
  value => {
    if (isValidTime(value) && value !== toTimeValue()) {
      const next = toPickerState(value)
      picker.hour = next.hour
      picker.minute = next.minute
    }
  }
)

function togglePicker(): void {
  if (!props.disabled) {
    isOpen.value = !isOpen.value
  }
}

function closePicker(): void {
  if (!isValidTime(props.modelValue)) {
    emit('update:modelValue', toTimeValue())
  }

  isOpen.value = false
}

function onWheelChanged(): void {
  emit('update:modelValue', toTimeValue())
}

function toPickerState(value: string): { hour: number; minute: number } {
  if (isValidTime(value)) {
    const [hour, minute] = value.split(':').map(Number)
    return { hour, minute }
  }

  const now = new Date()
  return {
    hour: now.getHours(),
    minute: now.getMinutes()
  }
}

function toTimeValue(): string {
  return `${pad2(picker.hour)}:${pad2(picker.minute)}`
}

function isValidTime(value: string): boolean {
  return /^([01][0-9]|2[0-3]):[0-5][0-9]$/.test(value)
}

function pad2(value: number): string {
  return String(value).padStart(2, '0')
}
</script>

<template>
  <section class="time-wheel-picker">
    <label class="time-wheel-picker__label" :for="fieldId">{{ label }}</label>
    <input :name="name" type="hidden" :value="modelValue" />

    <button
      :id="fieldId"
      class="time-wheel-picker__field"
      type="button"
      :aria-controls="panelId"
      :aria-expanded="isOpen"
      :disabled="disabled"
      @click="togglePicker"
    >
      <span>{{ displayValue }}</span>
      <span aria-hidden="true">◷</span>
    </button>

    <section
      v-if="isOpen"
      :id="panelId"
      class="time-wheel-picker__panel"
      :aria-label="t('staffControls.timePicker.aria')"
    >
      <div class="time-wheel-picker__frame">
        <label class="time-wheel-picker__column">
          <span>{{ t('staffControls.timePicker.hour') }}</span>
          <select v-model.number="picker.hour" size="5" @change="onWheelChanged">
            <option v-for="hour in HOUR_OPTIONS" :key="hour" :value="hour">
              {{ pad2(hour) }}
            </option>
          </select>
        </label>

        <span class="time-wheel-picker__separator" aria-hidden="true">:</span>

        <label class="time-wheel-picker__column">
          <span>{{ t('staffControls.timePicker.minute') }}</span>
          <select v-model.number="picker.minute" size="5" @change="onWheelChanged">
            <option v-for="minute in MINUTE_OPTIONS" :key="minute" :value="minute">
              {{ pad2(minute) }}
            </option>
          </select>
        </label>
      </div>

      <button class="time-wheel-picker__done" type="button" @click="closePicker">
        {{ t('staffControls.timePicker.done') }}
      </button>
    </section>
  </section>
</template>

<style scoped>
.time-wheel-picker {
  display: grid;
  gap: 6px;
}

.time-wheel-picker__label,
.time-wheel-picker__column span {
  color: #0f172a;
  font-size: 0.82rem;
  font-weight: 900;
}

.time-wheel-picker__field {
  align-items: center;
  background: #ffffff;
  border: 1px solid #d8e0eb;
  border-radius: 8px;
  color: #0f172a;
  display: grid;
  font-size: 0.92rem;
  grid-template-columns: minmax(0, 1fr) auto;
  min-height: 36px;
  padding: 7px 12px;
  text-align: left;
  width: 100%;
}

.time-wheel-picker__field span:first-child {
  font-variant-numeric: tabular-nums;
}

.time-wheel-picker__panel {
  background: #ffffff;
  border: 1px solid #d8e0eb;
  border-radius: 10px;
  box-shadow: 0 12px 28px rgba(15, 23, 42, 0.12);
  display: grid;
  gap: 10px;
  padding: 10px;
}

.time-wheel-picker__frame {
  align-items: center;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  display: grid;
  gap: 8px;
  grid-template-columns: minmax(0, 1fr) auto minmax(0, 1fr);
  padding: 10px;
}

.time-wheel-picker__column {
  display: grid;
  gap: 6px;
  min-width: 0;
}

.time-wheel-picker__column span {
  color: #64748b;
  text-align: center;
}

.time-wheel-picker__column select {
  background: #f8fafc;
  border: 1px solid #d8e0eb;
  border-radius: 8px;
  color: #0f172a;
  font-size: 1.1rem;
  font-weight: 950;
  height: 128px;
  outline: none;
  overflow-y: auto;
  padding: 4px;
  text-align: center;
  width: 100%;
}

.time-wheel-picker__column option {
  font-variant-numeric: tabular-nums;
  padding: 6px 0;
}

.time-wheel-picker__separator {
  color: #f97316;
  font-size: 1.25rem;
  font-weight: 950;
  padding-top: 22px;
}

.time-wheel-picker__done {
  background: #f97316;
  border: 1px solid #f97316;
  border-radius: 999px;
  color: #ffffff;
  font-size: 0.86rem;
  font-weight: 950;
  min-height: 34px;
  padding: 0 14px;
}

.time-wheel-picker__field:focus-visible,
.time-wheel-picker__column select:focus-visible,
.time-wheel-picker__done:focus-visible {
  outline: 3px solid rgba(249, 115, 22, 0.28);
  outline-offset: 2px;
}
</style>
