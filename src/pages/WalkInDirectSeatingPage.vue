<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'

import {
  seatWalkInDirectly,
  WalkInDirectSeatingApiError
} from '../api/walkInDirectSeatingApi'
import StaffGuestContactLookup from '../components/staff/StaffGuestContactLookup.vue'
import { isValidSingaporeLocalPhone, toSingaporePhoneE164 } from '../components/staff/staffGuestContact'
import TableResourcePicker from '../components/staff-table/TableResourcePicker.vue'
import { useStoreContextStore } from '../stores/storeContext'
import type {
  ApiErrorResponse,
  SeatWalkInDirectlyRequest,
  SeatWalkInDirectlyResponse
} from '../types/walkInDirectSeating'

const route = useRoute()
const storeContext = useStoreContextStore()

const form = reactive({
  partySize: 2,
  customerId: '',
  customerName: '',
  customerSalutation: '',
  phoneLocal: '',
  tableId: '',
  tableGroupId: '',
  overrideReasonCode: '',
  overrideNote: ''
})

const isSubmitting = ref(false)
const result = ref<SeatWalkInDirectlyResponse | null>(null)
const apiError = ref<ApiErrorResponse | null>(null)
const lastIdempotencyKey = ref('')

const storeId = computed(() => storeContext.resolveStoreId(route.params.storeId))
const hasSeatingId = computed(() => !!result.value?.seatingId)
const staffHomeRoute = computed(() => ({
  name: 'store-staff-home',
  params: {
    storeId: storeId.value
  }
}))
const cleaningRoute = computed(() => ({
  name: 'cleaning-complete',
  params: {
    storeId: storeId.value
  },
  query: {
    seatingId: result.value?.seatingId ?? ''
  }
}))

const canSubmit = computed(() => !isSubmitting.value && !!storeId.value)

watch(
  () => route.query,
  () => {
    applyRouteSelection()
  },
  { immediate: true }
)

async function submitDirectSeating(): Promise<void> {
  apiError.value = validateForm()
  result.value = null

  if (apiError.value || !storeId.value) {
    return
  }

  const idempotencyKey = createIdempotencyKey()
  lastIdempotencyKey.value = idempotencyKey
  isSubmitting.value = true

  try {
    result.value = await seatWalkInDirectly(storeId.value, toRequest(), idempotencyKey)
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

  if (form.tableId.trim() && form.tableGroupId.trim()) {
    return createLocalError('SEATING_RESOURCE_INVALID', 'walkin.direct_seating.resource_invalid')
  }

  return null
}

function selectTable(tableId: string): void {
  form.tableId = tableId
  form.tableGroupId = ''
}

function selectTableGroup(tableGroupId: string): void {
  form.tableGroupId = tableGroupId
  form.tableId = ''
}

function applyRouteSelection(): void {
  const tableId = asSingleValue(route.query.tableId)
  const tableGroupId = asSingleValue(route.query.tableGroupId)
  const partySize = Number(asSingleValue(route.query.partySize) ?? '')

  if (tableId) {
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
    overrideReasonCode: optionalValue(form.overrideReasonCode),
    overrideNote: optionalValue(form.overrideNote)
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

function createIdempotencyKey(): string {
  if ('randomUUID' in crypto) {
    return crypto.randomUUID()
  }

  return `walkin-${Date.now()}-${Math.random().toString(36).slice(2)}`
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
</script>

<template>
  <main class="page-shell">
    <section class="page-header">
      <p class="eyebrow">门店员工</p>
      <h1>散客直接入座</h1>
      <p class="store-context">门店 {{ storeId || 'VITE_DEFAULT_STORE_ID' }}</p>
      <RouterLink class="home-link" :to="staffHomeRoute">返回员工首页</RouterLink>
    </section>

    <form class="direct-seating-form" @submit.prevent="submitDirectSeating">
      <label class="party-size-field">
        <span>人数</span>
        <input
          v-model.number="form.partySize"
          min="1"
          inputmode="numeric"
          name="partySize"
          required
          type="number"
        />
      </label>

      <details class="field-group">
        <summary>客户信息</summary>
        <StaffGuestContactLookup
          :store-id="storeId"
          v-model:customer-id="form.customerId"
          v-model:customer-name="form.customerName"
          v-model:salutation="form.customerSalutation"
          v-model:phone-local="form.phoneLocal"
          :disabled="isSubmitting"
        />
      </details>

      <TableResourcePicker
        :store-id="storeId"
        :party-size="form.partySize"
        :selected-table-id="form.tableId"
        :selected-table-group-id="form.tableGroupId"
        @select-table="selectTable"
        @select-table-group="selectTableGroup"
      />

      <details class="field-group">
        <summary>手动填写资源 ID</summary>
        <label>
          <span>桌台 ID</span>
          <input v-model="form.tableId" name="tableId" type="text" />
        </label>
        <label>
          <span>桌组 ID</span>
          <input v-model="form.tableGroupId" name="tableGroupId" type="text" />
        </label>
      </details>

      <details class="field-group">
        <summary>调整信息</summary>
        <label>
          <span>调整原因代码（可选）</span>
          <input v-model="form.overrideReasonCode" name="overrideReasonCode" type="text" />
        </label>
        <label>
          <span>调整说明（可选）</span>
          <textarea v-model="form.overrideNote" name="overrideNote" rows="3" />
        </label>
      </details>

      <button class="submit-button" :disabled="!canSubmit" type="submit">
        {{ isSubmitting ? '提交中...' : '确认入座' }}
      </button>
    </form>

    <section v-if="result" class="result-panel success-panel" aria-live="polite">
      <h2>散客入座成功</h2>
      <dl>
        <div>
          <dt>入座记录 ID</dt>
          <dd>{{ result.seatingId }}</dd>
        </div>
        <div>
          <dt>散客记录 ID</dt>
          <dd>{{ result.walkInId }}</dd>
        </div>
        <div>
          <dt>资源</dt>
          <dd>
            {{ result.resource.type }} {{ result.resource.label || result.resource.id }}
          </dd>
        </div>
        <div>
          <dt>桌台状态</dt>
          <dd>{{ result.status }}</dd>
        </div>
        <div>
          <dt>幂等状态</dt>
          <dd>
            {{ result.idempotency.status }}
            <span v-if="result.idempotency.replayed">重放：true</span>
          </dd>
        </div>
      </dl>
      <RouterLink v-if="hasSeatingId" class="next-link" :to="cleaningRoute">开始清台</RouterLink>
      <button v-else class="next-link disabled-next" disabled type="button">开始清台</button>
      <p v-if="!hasSeatingId" class="handoff-hint">
        缺少 seatingId，暂不能继续清台。
      </p>
    </section>

    <section v-if="apiError" class="result-panel error-panel" aria-live="assertive">
      <h2>入座失败</h2>
      <p class="error-code">错误代码：{{ apiError.error.code }}</p>
      <p class="message-key">消息键：{{ apiError.error.messageKey }}</p>
    </section>

    <p v-if="lastIdempotencyKey" class="idempotency-key">
      幂等键 {{ lastIdempotencyKey }}
    </p>
  </main>
</template>

<style scoped>
.page-shell {
  display: grid;
  gap: 16px;
  margin: 0 auto;
  max-width: 560px;
  min-height: 100vh;
  padding: 20px 14px 32px;
}

.page-header {
  display: grid;
  gap: 4px;
}

.eyebrow,
.store-context,
.idempotency-key {
  color: #667085;
  font-size: 0.82rem;
  margin: 0;
}

.home-link {
  color: #315f91;
  font-size: 0.86rem;
  font-weight: 800;
  justify-self: start;
  text-decoration: none;
}

h1,
h2 {
  color: #14213d;
  letter-spacing: 0;
  margin: 0;
}

h1 {
  font-size: 1.7rem;
  line-height: 1.15;
}

h2 {
  font-size: 1rem;
}

.direct-seating-form,
.result-panel {
  background: #ffffff;
  border: 1px solid #d8e0eb;
  border-radius: 8px;
  box-shadow: 0 10px 32px rgba(20, 33, 61, 0.08);
}

.direct-seating-form {
  display: grid;
  gap: 12px;
  padding: 14px;
}

label {
  display: grid;
  gap: 6px;
}

label span,
summary,
dt {
  color: #41516a;
  font-size: 0.86rem;
  font-weight: 700;
}

input,
textarea {
  background: #fbfcfe;
  border: 1px solid #c8d3e2;
  border-radius: 6px;
  color: #182536;
  min-height: 44px;
  outline: none;
  padding: 10px 11px;
  width: 100%;
}

input:focus,
textarea:focus {
  border-color: #2563eb;
  box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.14);
}

.party-size-field {
  background: #eaf2ff;
  border: 1px solid #b8cdf6;
  border-radius: 8px;
  padding: 12px;
}

.party-size-field input {
  background: #ffffff;
  font-size: 1.7rem;
  font-weight: 800;
  min-height: 56px;
}

.field-group {
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 10px 12px;
}

.field-group[open] {
  display: grid;
  gap: 12px;
}

summary {
  cursor: pointer;
  min-height: 32px;
}

.submit-button {
  background: #176b4d;
  border: 0;
  border-radius: 8px;
  color: #ffffff;
  font-weight: 800;
  min-height: 52px;
  padding: 0 16px;
}

.submit-button:disabled {
  background: #94a3b8;
  cursor: not-allowed;
}

.next-link {
  align-items: center;
  background: #315f91;
  border-radius: 8px;
  color: #ffffff;
  display: inline-flex;
  font-weight: 800;
  justify-content: center;
  min-height: 44px;
  padding: 0 14px;
  text-decoration: none;
}

.disabled-next {
  background: #94a3b8;
  cursor: not-allowed;
}

.handoff-hint {
  color: #b42318;
  font-size: 0.86rem;
  font-weight: 700;
  margin: 0;
}

.result-panel {
  display: grid;
  gap: 10px;
  padding: 14px;
}

.success-panel {
  border-color: #a7d7be;
}

.error-panel {
  border-color: #f4b8b8;
}

dl {
  display: grid;
  gap: 10px;
  margin: 0;
}

dt,
dd {
  margin: 0;
}

dd {
  color: #1d2736;
  overflow-wrap: anywhere;
}

.error-code {
  color: #b42318;
  font-weight: 800;
  margin: 0;
}

.message-key {
  color: #41516a;
  margin: 0;
  overflow-wrap: anywhere;
}

.idempotency-key {
  overflow-wrap: anywhere;
}

@media (min-width: 720px) {
  .page-shell {
    padding-top: 36px;
  }

  h1 {
    font-size: 2rem;
  }
}
</style>
