<script setup lang="ts">
const props = withDefaults(
  defineProps<{
    customerName: string
    salutation: string
    disabled?: boolean
  }>(),
  {
    disabled: false
  }
)

const emit = defineEmits<{
  'update:customerName': [value: string]
  'update:salutation': [value: string]
}>()

const SALUTATION_OPTIONS = ['先生', '女士'] as const
const fieldId = `staff-guest-name-${Math.random().toString(36).slice(2)}`

function updateCustomerName(event: Event): void {
  emit('update:customerName', (event.target as HTMLInputElement).value)
}

function selectSalutation(value: string): void {
  emit('update:salutation', props.salutation === value ? '' : value)
}
</script>

<template>
  <section class="staff-guest-name-field">
    <label class="staff-guest-name-field__label" :for="fieldId">顾客姓名</label>

    <div class="staff-guest-name-field__row">
      <input
        :id="fieldId"
        :value="customerName"
        autocomplete="name"
        name="customerName"
        placeholder="姓名"
        type="text"
        :disabled="disabled"
        @input="updateCustomerName"
      />

      <div class="staff-guest-name-field__salutations" aria-label="顾客称呼">
        <button
          v-for="option in SALUTATION_OPTIONS"
          :key="option"
          type="button"
          :aria-pressed="salutation === option"
          :class="{ 'staff-guest-name-field__salutation--active': salutation === option }"
          :disabled="disabled"
          @click="selectSalutation(option)"
        >
          {{ option }}
        </button>
      </div>
    </div>
  </section>
</template>

<style scoped>
.staff-guest-name-field {
  display: grid;
  gap: 6px;
}

.staff-guest-name-field__label {
  color: #0f172a;
  font-size: 0.82rem;
  font-weight: 900;
}

.staff-guest-name-field__row {
  display: grid;
  gap: 8px;
  grid-template-columns: minmax(0, 1fr) auto;
}

.staff-guest-name-field input {
  background: #ffffff;
  border: 1px solid #d8e0eb;
  border-radius: 8px;
  color: #0f172a;
  min-height: 36px;
  outline: none;
  padding: 7px 12px;
  width: 100%;
}

.staff-guest-name-field__salutations {
  display: grid;
  gap: 6px;
  grid-template-columns: repeat(2, 52px);
}

.staff-guest-name-field__salutations button {
  background: #ffffff;
  border: 1px solid #d8e0eb;
  border-radius: 999px;
  color: #334155;
  font-size: 0.82rem;
  font-weight: 900;
  min-height: 36px;
  padding: 0 8px;
}

.staff-guest-name-field__salutations .staff-guest-name-field__salutation--active {
  background: #f97316;
  border-color: #f97316;
  color: #ffffff;
}

.staff-guest-name-field input:focus,
.staff-guest-name-field__salutations button:focus-visible {
  border-color: #f97316;
  box-shadow: 0 0 0 3px rgba(249, 115, 22, 0.16);
}

.staff-guest-name-field input:disabled,
.staff-guest-name-field__salutations button:disabled {
  cursor: not-allowed;
  opacity: 0.68;
}
</style>
