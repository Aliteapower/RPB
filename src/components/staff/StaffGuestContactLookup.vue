<script setup lang="ts">
import { ref, watch } from 'vue'

import { lookupCustomerByPhone } from '../../api/customerPhoneLookupApi'
import StaffGuestNameField from './StaffGuestNameField.vue'
import StaffSingaporePhoneField from './StaffSingaporePhoneField.vue'
import { isValidSingaporeLocalPhone, toSingaporePhoneE164 } from './staffGuestContact'

const props = withDefaults(
  defineProps<{
    storeId: string
    customerId: string
    customerName: string
    salutation: string
    phoneLocal: string
    disabled?: boolean
  }>(),
  {
    disabled: false
  }
)

const emit = defineEmits<{
  'update:customerId': [value: string]
  'update:customerName': [value: string]
  'update:salutation': [value: string]
  'update:phoneLocal': [value: string]
}>()

const isLookingUp = ref(false)
const lookupState = ref<'idle' | 'found' | 'not-found' | 'error'>('idle')
const lookupMessage = ref('')
let lookupSequence = 0

watch(
  () => [props.storeId, props.phoneLocal] as const,
  () => {
    void runPhoneLookup()
  }
)

function updateCustomerName(value: string): void {
  emit('update:customerName', value)
}

function updateSalutation(value: string): void {
  emit('update:salutation', value)
}

function updatePhoneLocal(value: string): void {
  emit('update:phoneLocal', value)
}

async function runPhoneLookup(): Promise<void> {
  const currentSequence = ++lookupSequence
  const phoneLocal = props.phoneLocal.trim()

  if (!props.storeId || !isValidSingaporeLocalPhone(phoneLocal)) {
    emit('update:customerId', '')
    clearLookupState()
    return
  }

  const phoneE164 = toSingaporePhoneE164(phoneLocal)
  if (!phoneE164) {
    emit('update:customerId', '')
    clearLookupState()
    return
  }

  isLookingUp.value = true

  try {
    const result = await lookupCustomerByPhone(props.storeId, phoneE164)
    if (currentSequence !== lookupSequence) {
      return
    }

    if (result.found && result.customer) {
      emit('update:customerId', result.customer.customerId)
      if (result.customer.displayName) {
        emit('update:customerName', result.customer.displayName)
      }
      if (result.customer.nickname) {
        emit('update:salutation', result.customer.nickname)
      }
      lookupState.value = 'found'
      lookupMessage.value = '已识别顾客'
      return
    }

    emit('update:customerId', '')
    lookupState.value = 'not-found'
    lookupMessage.value = '新手机号'
  } catch {
    if (currentSequence !== lookupSequence) {
      return
    }

    emit('update:customerId', '')
    lookupState.value = 'error'
    lookupMessage.value = '顾客识别失败'
  } finally {
    if (currentSequence === lookupSequence) {
      isLookingUp.value = false
    }
  }
}

function clearLookupState(): void {
  isLookingUp.value = false
  lookupState.value = 'idle'
  lookupMessage.value = ''
}
</script>

<template>
  <section class="staff-guest-contact-lookup customer-lookup">
    <StaffGuestNameField
      :customer-name="customerName"
      :salutation="salutation"
      :disabled="disabled"
      @update:customer-name="updateCustomerName"
      @update:salutation="updateSalutation"
    />

    <StaffSingaporePhoneField
      :model-value="phoneLocal"
      :disabled="disabled"
      @update:model-value="updatePhoneLocal"
    />

    <p
      v-if="lookupMessage"
      class="staff-guest-contact-lookup__status"
      :class="`staff-guest-contact-lookup__status--${lookupState}`"
      aria-live="polite"
    >
      <span>{{ isLookingUp ? '识别中...' : lookupMessage }}</span>
      <small v-if="customerId && lookupState === 'found'">{{ customerId }}</small>
    </p>
  </section>
</template>

<style scoped>
.staff-guest-contact-lookup {
  display: grid;
  gap: 11px;
}

.staff-guest-contact-lookup__status {
  align-items: center;
  border-radius: 8px;
  display: grid;
  gap: 2px;
  margin: 0;
  min-height: 34px;
  padding: 7px 10px;
}

.staff-guest-contact-lookup__status span {
  font-size: 0.82rem;
  font-weight: 900;
}

.staff-guest-contact-lookup__status small {
  color: inherit;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 0.7rem;
  overflow-wrap: anywhere;
  opacity: 0.78;
}

.staff-guest-contact-lookup__status--found {
  background: #ecfdf3;
  color: #166534;
}

.staff-guest-contact-lookup__status--not-found {
  background: #fff7ed;
  color: #9a3412;
}

.staff-guest-contact-lookup__status--error {
  background: #fff1f2;
  color: #be123c;
}
</style>
