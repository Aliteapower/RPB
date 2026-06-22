<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'

import { CleaningApiError, completeCleaning, startCleaning } from '../api/cleaningApi'
import { useStoreContextStore } from '../stores/storeContext'
import type {
  CleaningApiErrorResponse,
  CompleteCleaningResponse,
  StartCleaningResponse
} from '../types/cleaning'

type CleaningAction = 'start' | 'complete'

const route = useRoute()
const storeContext = useStoreContextStore()

const startForm = reactive({
  seatingId: asSingleValue(route.query.seatingId),
  reasonCode: '',
  note: ''
})

const completeForm = reactive({
  cleaningId: asSingleValue(route.query.cleaningId),
  reasonCode: '',
  note: ''
})

const activeAction = ref<CleaningAction | null>(null)
const startResult = ref<StartCleaningResponse | null>(null)
const completeResult = ref<CompleteCleaningResponse | null>(null)
const apiError = ref<CleaningApiErrorResponse | null>(null)
const lastIdempotencyKey = ref('')

const storeId = computed(() => storeContext.resolveStoreId(route.params.storeId))
const staffHomeRoute = computed(() => ({
  name: 'store-staff-home',
  params: {
    storeId: storeId.value
  }
}))
const isSubmittingStart = computed(() => activeAction.value === 'start')
const isSubmittingComplete = computed(() => activeAction.value === 'complete')
const canSubmit = computed(() => !!storeId.value && !activeAction.value)
const hasQuerySeatingId = computed(() => !!asSingleValue(route.query.seatingId))
const canCompleteImmediately = computed(() => !!completeForm.cleaningId.trim() && canSubmit.value)
const isClosedLoopComplete = computed(
  () =>
    completeResult.value?.cleaningStatus === 'released' &&
    completeResult.value?.tableStatus === 'available'
)

watch(
  () => route.query.seatingId,
  (value) => {
    const seatingId = asSingleValue(value)
    if (seatingId) {
      startForm.seatingId = seatingId
    }
  }
)

watch(
  () => route.query.cleaningId,
  (value) => {
    const cleaningId = asSingleValue(value)
    if (cleaningId) {
      completeForm.cleaningId = cleaningId
    }
  }
)

async function submitStartCleaning(): Promise<void> {
  apiError.value = validateStartForm()
  startResult.value = null
  completeResult.value = null

  if (apiError.value || !storeId.value) {
    return
  }

  const idempotencyKey = createIdempotencyKey('start-cleaning')
  lastIdempotencyKey.value = idempotencyKey
  activeAction.value = 'start'

  try {
    const response = await startCleaning(
      storeId.value,
      startForm.seatingId.trim(),
      {
        reasonCode: optionalValue(startForm.reasonCode),
        note: optionalValue(startForm.note)
      },
      idempotencyKey
    )
    startResult.value = response
    completeForm.cleaningId = response.cleaningId
  } catch (error) {
    apiError.value =
      error instanceof CleaningApiError
        ? error.response
        : createLocalError('REQUEST_FAILED', 'cleaning.request_failed')
  } finally {
    activeAction.value = null
  }
}

async function submitCompleteCleaning(): Promise<void> {
  apiError.value = validateCompleteForm()
  startResult.value = null
  completeResult.value = null

  if (apiError.value || !storeId.value) {
    return
  }

  const idempotencyKey = createIdempotencyKey('complete-cleaning')
  lastIdempotencyKey.value = idempotencyKey
  activeAction.value = 'complete'

  try {
    completeResult.value = await completeCleaning(
      storeId.value,
      completeForm.cleaningId.trim(),
      {
        reasonCode: optionalValue(completeForm.reasonCode),
        note: optionalValue(completeForm.note)
      },
      idempotencyKey
    )
  } catch (error) {
    apiError.value =
      error instanceof CleaningApiError
        ? error.response
        : createLocalError('REQUEST_FAILED', 'cleaning.request_failed')
  } finally {
    activeAction.value = null
  }
}

function validateStartForm(): CleaningApiErrorResponse | null {
  if (!startForm.seatingId.trim()) {
    return createLocalError('SEATING_NOT_FOUND', 'cleaning.seating_not_found')
  }

  return null
}

function validateCompleteForm(): CleaningApiErrorResponse | null {
  if (!completeForm.cleaningId.trim()) {
    return createLocalError('CLEANING_NOT_FOUND', 'cleaning.not_found')
  }

  return null
}

function optionalValue(value: string): string | null {
  const trimmed = value.trim()
  return trimmed ? trimmed : null
}

function createIdempotencyKey(action: CleaningAction | 'start-cleaning' | 'complete-cleaning'): string {
  const prefix = `cleaning:${action}`
  if ('randomUUID' in crypto) {
    return `${prefix}:${crypto.randomUUID()}`
  }

  return `${prefix}:${Date.now()}:${Math.random().toString(36).slice(2)}`
}

function createLocalError(code: string, messageKey: string): CleaningApiErrorResponse {
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

function asSingleValue(value: unknown): string {
  if (Array.isArray(value)) {
    return typeof value[0] === 'string' ? value[0] : ''
  }

  return typeof value === 'string' ? value : ''
}
</script>

<template>
  <main class="page-shell">
    <section class="page-header">
      <p class="eyebrow">门店员工</p>
      <h1>清台处理</h1>
      <p class="store-context">门店 {{ storeId || 'VITE_DEFAULT_STORE_ID' }}</p>
      <p v-if="hasQuerySeatingId" class="handoff-note">已从路由参数带入入座记录 ID</p>
      <RouterLink class="home-link" :to="staffHomeRoute">返回员工首页</RouterLink>
    </section>

    <section class="workflow-stack">
      <form class="cleaning-form" @submit.prevent="submitStartCleaning">
        <header>
          <p class="form-kicker">开始清台</p>
          <h2>入座记录 ID</h2>
        </header>

        <label class="primary-field">
          <span>入座记录 ID</span>
          <input
            v-model="startForm.seatingId"
            autocomplete="off"
            name="seatingId"
            required
            type="text"
          />
        </label>

        <details class="field-group">
          <summary>备注</summary>
          <label>
            <span>原因代码（可选）</span>
            <input v-model="startForm.reasonCode" name="startReasonCode" type="text" />
          </label>
          <label>
            <span>备注（可选）</span>
            <textarea v-model="startForm.note" name="startNote" rows="3" />
          </label>
        </details>

        <button class="submit-button" :disabled="!canSubmit" type="submit">
          {{ isSubmittingStart ? '提交中...' : '开始清台' }}
        </button>
      </form>

      <form class="cleaning-form" @submit.prevent="submitCompleteCleaning">
        <header>
          <p class="form-kicker">完成清台</p>
          <h2>清台记录 ID</h2>
        </header>

        <label class="primary-field">
          <span>清台记录 ID</span>
          <input
            v-model="completeForm.cleaningId"
            autocomplete="off"
            name="cleaningId"
            required
            type="text"
          />
        </label>

        <details class="field-group">
          <summary>备注</summary>
          <label>
            <span>原因代码（可选）</span>
            <input v-model="completeForm.reasonCode" name="completeReasonCode" type="text" />
          </label>
          <label>
            <span>备注（可选）</span>
            <textarea v-model="completeForm.note" name="completeNote" rows="3" />
          </label>
        </details>

        <button class="submit-button complete-button" :disabled="!canCompleteImmediately" type="submit">
          {{ isSubmittingComplete ? '提交中...' : '完成清台' }}
        </button>
      </form>
    </section>

    <section v-if="startResult" class="result-panel success-panel" aria-live="polite">
      <h2>清台操作成功</h2>
      <dl>
        <div>
          <dt>清台记录 ID</dt>
          <dd>{{ startResult.cleaningId }}</dd>
        </div>
        <div>
          <dt>入座记录 ID</dt>
          <dd>{{ startResult.seatingId }}</dd>
        </div>
        <div>
          <dt>资源</dt>
          <dd>{{ startResult.resource.type }} {{ startResult.resource.label || startResult.resource.id }}</dd>
        </div>
        <div>
          <dt>清台状态</dt>
          <dd>{{ startResult.cleaningStatus }}</dd>
        </div>
        <div>
          <dt>桌台状态</dt>
          <dd>{{ startResult.tableStatus }}</dd>
        </div>
        <div>
          <dt>事件</dt>
          <dd>{{ startResult.events.join(', ') }}</dd>
        </div>
        <div>
          <dt>幂等状态</dt>
          <dd>
            {{ startResult.idempotency.status }}
            <span v-if="startResult.idempotency.replayed">重放：true</span>
          </dd>
        </div>
      </dl>
      <p class="handoff-note">清台已开始，可继续完成清台</p>
    </section>

    <section v-if="completeResult" class="result-panel success-panel" aria-live="polite">
      <h2>{{ isClosedLoopComplete ? '桌位已释放' : '清台操作成功' }}</h2>
      <dl>
        <div>
          <dt>清台记录 ID</dt>
          <dd>{{ completeResult.cleaningId }}</dd>
        </div>
        <div>
          <dt>资源</dt>
          <dd>
            {{ completeResult.resource.type }}
            {{ completeResult.resource.label || completeResult.resource.id }}
          </dd>
        </div>
        <div>
          <dt>清台状态</dt>
          <dd>{{ completeResult.cleaningStatus }}</dd>
        </div>
        <div>
          <dt>桌台状态</dt>
          <dd>{{ completeResult.tableStatus }}</dd>
        </div>
        <div>
          <dt>事件</dt>
          <dd>{{ completeResult.events.join(', ') }}</dd>
        </div>
        <div>
          <dt>幂等状态</dt>
          <dd>
            {{ completeResult.idempotency.status }}
            <span v-if="completeResult.idempotency.replayed">重放：true</span>
          </dd>
        </div>
      </dl>
    </section>

    <section v-if="apiError" class="result-panel error-panel" aria-live="assertive">
      <h2>清台操作失败</h2>
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
  max-width: 680px;
  min-height: 100vh;
  padding: 20px 14px 32px;
}

.page-header {
  display: grid;
  gap: 4px;
}

.eyebrow,
.store-context,
.idempotency-key,
.form-kicker,
.handoff-note {
  color: #667085;
  font-size: 0.82rem;
  margin: 0;
}

.handoff-note {
  color: #176b4d;
  font-weight: 800;
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

.workflow-stack {
  display: grid;
  gap: 14px;
}

.cleaning-form,
.result-panel {
  background: #ffffff;
  border: 1px solid #d8e0eb;
  border-radius: 8px;
  box-shadow: 0 10px 32px rgba(20, 33, 61, 0.08);
}

.cleaning-form {
  display: grid;
  gap: 12px;
  padding: 14px;
}

.cleaning-form header {
  display: grid;
  gap: 3px;
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

.primary-field {
  background: #eef6f1;
  border: 1px solid #b8d8c4;
  border-radius: 8px;
  padding: 12px;
}

.primary-field input {
  background: #ffffff;
  font-weight: 700;
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

.complete-button {
  background: #315f91;
}

.submit-button:disabled {
  background: #94a3b8;
  cursor: not-allowed;
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
