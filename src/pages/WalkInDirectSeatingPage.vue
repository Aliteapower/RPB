<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import {
  seatWalkInDirectly,
  WalkInDirectSeatingApiError
} from '../api/walkInDirectSeatingApi'
import StaffBottomNav from '../components/staff/StaffBottomNav.vue'
import StaffGuestContactLookup from '../components/staff/StaffGuestContactLookup.vue'
import { isValidSingaporeLocalPhone, toSingaporePhoneE164 } from '../components/staff/staffGuestContact'
import StaffHomeTopBar from '../components/staff-home/StaffHomeTopBar.vue'
import StaffHomeWorkflowStrip from '../components/staff-home/StaffHomeWorkflowStrip.vue'
import { useCurrentClock } from '../components/staff-home/useCurrentClock'
import TableResourcePicker from '../components/staff-table/TableResourcePicker.vue'
import { useStoreContextStore } from '../stores/storeContext'
import type {
  ApiErrorResponse,
  SeatWalkInDirectlyRequest
} from '../types/walkInDirectSeating'
import { useGeneratedText } from '../i18n/generatedText'

const { gt } = useGeneratedText()

const route = useRoute()
const router = useRouter()
const storeContext = useStoreContextStore()
const { currentBusinessDate, currentTimeText } = useCurrentClock()

const form = reactive({
  partySize: 2,
  customerId: '',
  customerName: '',
  customerSalutation: '',
  phoneLocal: '',
  tableId: '',
  tableGroupId: '',
  temporaryTableIds: [] as string[]
})

const isSubmitting = ref(false)
const apiError = ref<ApiErrorResponse | null>(null)

const storeId = computed(() => storeContext.resolveStoreId(route.params.storeId))
const storeLabel = computed(() => formatStoreLabel(storeId.value))
const appStatusLabel = computed(() => (isSubmitting.value ? gt('generated.walk-in-direct-seating.014') : gt('generated.walk-in-direct-seating.015')))
const businessDate = computed(() => currentBusinessDate.value)
const tableResourceListRoute = computed(() => ({
  name: 'table-resource-list',
  params: {
    storeId: storeId.value || ''
  }
}))

const hasTableId = computed(() => !!form.tableId.trim())
const hasTableGroupId = computed(() => !!form.tableGroupId.trim())
const hasTemporaryTables = computed(() => form.temporaryTableIds.length > 0)
const selectedResourceCount = computed(
  () => Number(hasTableId.value) + Number(hasTableGroupId.value) + Number(hasTemporaryTables.value)
)
const hasValidResourceSelection = computed(
  () => selectedResourceCount.value <= 1 && form.temporaryTableIds.length !== 1
)
const canSubmit = computed(() => !isSubmitting.value && !!storeId.value && hasValidResourceSelection.value)

watch(
  () => route.query,
  () => {
    applyRouteSelection()
  },
  { immediate: true }
)

async function submitDirectSeating(): Promise<void> {
  apiError.value = validateForm()

  if (apiError.value || !storeId.value) {
    return
  }

  isSubmitting.value = true

  try {
    await seatWalkInDirectly(storeId.value, toRequest(), createIdempotencyKey())
    await router.push(tableResourceListRoute.value)
  } catch (error) {
    apiError.value =
      error instanceof WalkInDirectSeatingApiError
        ? error.response
        : createLocalError('REQUEST_FAILED', 'walkin.direct_seating.request_failed')
  } finally {
    isSubmitting.value = false
  }
}

function validateForm(): ApiErrorResponse | null {
  if (!Number.isInteger(form.partySize) || form.partySize <= 0) {
    return createLocalError('INVALID_PARTY_SIZE', 'walkin.direct_seating.invalid_party_size')
  }

  const phoneLocal = form.phoneLocal.trim()
  if (phoneLocal && !isValidSingaporeLocalPhone(phoneLocal)) {
    return createLocalError('INVALID_PHONE_E164', 'walkin.direct_seating.invalid_phone_e164')
  }

  if (selectedResourceCount.value > 1) {
    return createLocalError('SEATING_RESOURCE_INVALID', 'walkin.direct_seating.resource_invalid')
  }

  if (form.temporaryTableIds.length === 1) {
    return createLocalError(
      'TEMPORARY_TABLE_GROUP_MEMBER_REQUIRED',
      'walkin.direct_seating.temporary_table_group_member_required'
    )
  }

  return null
}

function selectTable(tableId: string): void {
  form.tableId = tableId
  form.tableGroupId = ''
  form.temporaryTableIds = []
}

function selectTableGroup(tableGroupId: string): void {
  form.tableGroupId = tableGroupId
  form.tableId = ''
  form.temporaryTableIds = []
}

function selectTemporaryTables(tableIds: string[]): void {
  form.temporaryTableIds = tableIds
  form.tableId = ''
  form.tableGroupId = ''
}

function updatePartySize(delta: number): void {
  form.partySize = Math.max(1, Math.min(99, form.partySize + delta))
}

function applyRouteSelection(): void {
  const tableId = asSingleValue(route.query.tableId)
  const tableGroupId = asSingleValue(route.query.tableGroupId)
  const temporaryTableIds = asValueList(route.query.temporaryTableIds)
  const partySize = Number(asSingleValue(route.query.partySize) ?? '')

  if (temporaryTableIds.length) {
    selectTemporaryTables(temporaryTableIds)
  } else if (tableId) {
    selectTable(tableId)
  } else if (tableGroupId) {
    selectTableGroup(tableGroupId)
  }

  if (Number.isInteger(partySize) && partySize > 0) {
    form.partySize = partySize
  }
}

function toRequest(): SeatWalkInDirectlyRequest {
  return {
    partySize: form.partySize,
    customerId: optionalValue(form.customerId),
    customerName: optionalValue(form.customerName),
    customerNickname: optionalValue(form.customerSalutation),
    phoneE164: toSingaporePhoneE164(form.phoneLocal),
    tableId: optionalValue(form.tableId),
    tableGroupId: optionalValue(form.tableGroupId),
    temporaryTableIds: form.temporaryTableIds.length ? form.temporaryTableIds : null
  }
}

function optionalValue(value: string): string | null {
  const trimmed = value.trim()
  return trimmed ? trimmed : null
}

function asSingleValue(value: unknown): string | null {
  const candidate = Array.isArray(value) ? value[0] : value

  return typeof candidate === 'string' && candidate.trim() ? candidate.trim() : null
}

function asValueList(value: unknown): string[] {
  const values = Array.isArray(value) ? value : typeof value === 'string' ? [value] : []
  const ids = values.flatMap(item => item.split(',')).map(item => item.trim()).filter(Boolean)

  return Array.from(new Set(ids))
}

function createIdempotencyKey(): string {
  const prefix = 'walkin:direct-seating'

  if (globalThis.crypto && 'randomUUID' in globalThis.crypto) {
    return `${prefix}:${globalThis.crypto.randomUUID()}`
  }

  return `${prefix}:${Date.now()}:${Math.random().toString(36).slice(2)}`
}

function createLocalError(code: string, messageKey: string): ApiErrorResponse {
  return {
    success: false,
    error: {
      code,
      messageKey,
      details: {}
    },
    idempotency: {
      status: 'failed'
    }
  }
}

function formatStoreLabel(value: string | undefined): string {
  if (!value) {
    return gt('generated.walk-in-direct-seating.016')
  }

  return `${gt('generated.walk-in-direct-seating.013')}${value.slice(0, 8)}`
}

</script>

<template>
  <main class="staff-workbench-shell walk-in-direct-workbench">
    <StaffHomeTopBar
      :app-status-label="appStatusLabel"
      :business-date="businessDate"
      :current-time-text="currentTimeText"
      :store-label="storeLabel"
    />

    <div class="walk-in-direct-workbench-body">
      <StaffHomeWorkflowStrip />

      <form class="direct-seating-form" @submit.prevent="submitDirectSeating">
        <header class="direct-seating-heading">
          <div>
            <p>{{ gt('generated.walk-in-direct-seating.001') }}</p>
            <h1>{{ gt('generated.walk-in-direct-seating.002') }}</h1>
          </div>
        </header>

        <section class="party-size-panel" :aria-label="gt('generated.walk-in-direct-seating.003')">
          <span>{{ gt('generated.walk-in-direct-seating.004') }}</span>
          <div class="party-size-stepper">
            <button type="button" :disabled="form.partySize <= 1" @click="updatePartySize(-1)">-</button>
            <input v-model.number="form.partySize" inputmode="numeric" min="1" name="partySize" type="number" />
            <button type="button" @click="updatePartySize(1)">+</button>
          </div>
        </section>

        <section class="guest-panel" :aria-label="gt('generated.walk-in-direct-seating.005')">
          <h2>{{ gt('generated.walk-in-direct-seating.006') }}</h2>
          <StaffGuestContactLookup
            :store-id="storeId || ''"
            v-model:customer-id="form.customerId"
            v-model:customer-name="form.customerName"
            v-model:salutation="form.customerSalutation"
            v-model:phone-local="form.phoneLocal"
            :disabled="isSubmitting"
          />
        </section>

        <section class="resource-panel" :aria-label="gt('generated.walk-in-direct-seating.007')">
          <TableResourcePicker
            :store-id="storeId"
            :party-size="null"
            :available-only="true"
            :business-date="businessDate"
            :selected-table-id="form.tableId"
            :selected-table-group-id="form.tableGroupId"
            :selected-temporary-table-ids="form.temporaryTableIds"
            temporary-selection-enabled
            @select-table="selectTable"
            @select-table-group="selectTableGroup"
            @select-temporary-tables="selectTemporaryTables"
          />
        </section>

        <section v-if="apiError" class="result-panel error-panel" aria-live="assertive">
          <h2>{{ gt('generated.walk-in-direct-seating.008') }}</h2>
          <p class="error-code">{{ gt('generated.walk-in-direct-seating.009') }}{{ apiError.error.code }}</p>
          <p class="message-key">{{ gt('generated.walk-in-direct-seating.010') }}{{ apiError.error.messageKey }}</p>
        </section>

        <button class="submit-button" :disabled="!canSubmit" type="submit">
          {{ isSubmitting ? gt('generated.walk-in-direct-seating.011') : gt('generated.walk-in-direct-seating.012') }}
        </button>
      </form>
    </div>

    <StaffBottomNav :store-id="storeId" active-tab="home" />
  </main>
</template>

<style scoped>
.walk-in-direct-workbench-body {
  display: grid;
  gap: 14px;
  padding: 12px 14px calc(86px + env(safe-area-inset-bottom));
}

.direct-seating-form,
.result-panel {
  background: #ffffff;
  border: 1px solid #dbe3ee;
  border-radius: 10px;
  box-shadow: 0 3px 12px rgba(15, 23, 42, 0.05);
  display: grid;
  gap: 12px;
  padding: 14px;
}

.direct-seating-heading {
  align-items: start;
  display: flex;
  gap: 10px;
  justify-content: space-between;
}

.direct-seating-heading p,
.party-size-panel > span {
  color: #64748b;
  font-size: 0.78rem;
  font-weight: 800;
  line-height: 1.25;
  margin: 0;
}

h1,
h2 {
  color: #0f172a;
  letter-spacing: 0;
  margin: 0;
}

h1 {
  font-size: 1.24rem;
  line-height: 1.15;
}

h2 {
  font-size: 0.94rem;
}

.party-size-panel,
.guest-panel,
.resource-panel {
  display: grid;
  gap: 10px;
}

.party-size-panel {
  background: #fff7ed;
  border: 1px solid #fed7aa;
  border-radius: 10px;
  padding: 12px;
}

.party-size-stepper {
  display: grid;
  gap: 8px;
  grid-template-columns: 48px minmax(0, 1fr) 48px;
}

.party-size-stepper button,
.submit-button {
  align-items: center;
  border-radius: 10px;
  display: inline-flex;
  font-weight: 900;
  justify-content: center;
  min-height: 46px;
  text-align: center;
  text-decoration: none;
}

.party-size-stepper button {
  background: #ffffff;
  border: 1px solid #fdba74;
  color: #c2410c;
  font-size: 1.2rem;
}

.party-size-stepper button:disabled {
  background: #f1f5f9;
  border-color: #cbd5e1;
  color: #94a3b8;
}

input {
  background: #ffffff;
  border: 1px solid #d8e0eb;
  border-radius: 8px;
  color: #0f172a;
  font-size: 1.2rem;
  font-weight: 950;
  min-height: 46px;
  outline: none;
  padding: 7px 12px;
  text-align: center;
  width: 100%;
}

input:focus {
  border-color: #f97316;
  box-shadow: 0 0 0 3px rgba(249, 115, 22, 0.16);
}

.guest-panel,
.resource-panel {
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  padding: 12px;
}

.submit-button {
  background: #176b4d;
  border: 0;
  color: #ffffff;
  padding: 0 16px;
}

.submit-button:disabled {
  background: #94a3b8;
  cursor: not-allowed;
}

.error-panel {
  border-color: #fecaca;
}

.error-code {
  color: #b42318;
  font-weight: 900;
  margin: 0;
}

.message-key {
  color: #475569;
  margin: 0;
  overflow-wrap: anywhere;
}

button:focus-visible,
input:focus-visible {
  outline: 3px solid rgba(249, 115, 22, 0.28);
  outline-offset: 2px;
}

@media (min-width: 720px) {
  .walk-in-direct-workbench-body {
    padding-top: 16px;
  }
}
</style>
