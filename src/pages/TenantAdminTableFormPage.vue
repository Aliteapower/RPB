<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import {
  createTable,
  getTable,
  TenantAdminApiError,
  updateTable,
  type TenantAdminTableMutation
} from '../api/tenantAdminApi'
import TenantAdminNav from '../components/tenant-admin/TenantAdminNav.vue'
import { useAuthSessionStore } from '../stores/authSession'
import { useGeneratedText } from '../i18n/generatedText'

const { gt } = useGeneratedText()

const route = useRoute()
const router = useRouter()
const auth = useAuthSessionStore()
const loading = ref(false)
const saving = ref(false)
const errorText = ref('')

const mode = computed<'create' | 'edit'>(() => (route.name === 'tenant-admin-table-create' ? 'create' : 'edit'))
const pageTitle = computed(() => (mode.value === 'create' ? gt('generated.tenant-admin-table-form.014') : gt('generated.tenant-admin-table-form.015')))
const storeId = computed(() => String(route.params.storeId || ''))
const tableId = computed(() => String(route.params.tableId || ''))

interface TableFormState {
  areaName: string
  tableCode: string
  capacity: number
  enabled: boolean
  areaSortOrder: number | null
  tableSortOrder: number | null
}

type SortField = 'areaSortOrder' | 'tableSortOrder'

const form = reactive<TableFormState>({
  areaName: '',
  tableCode: '',
  capacity: 4,
  enabled: true,
  areaSortOrder: null,
  tableSortOrder: null
})

onMounted(() => {
  if (mode.value === 'edit') {
    void loadTable()
  }
})

async function loadTable(): Promise<void> {
  loading.value = true
  errorText.value = ''
  try {
    const response = await getTable(storeId.value, tableId.value)
    Object.assign(form, {
      areaName: response.table.areaName,
      tableCode: response.table.tableCode,
      capacity: response.table.capacity,
      enabled: response.table.enabled,
      areaSortOrder: response.table.areaSortOrder,
      tableSortOrder: response.table.tableSortOrder
    })
  } catch (error) {
    errorText.value = apiErrorText(error)
  } finally {
    loading.value = false
  }
}

async function submitTable(): Promise<void> {
  if (saving.value) {
    return
  }

  saving.value = true
  errorText.value = ''
  try {
    const payload = toPayload()
    if (mode.value === 'create') {
      await createTable(storeId.value, payload)
    } else {
      await updateTable(storeId.value, tableId.value, payload)
    }
    await router.push({ name: 'tenant-admin-tables', params: { storeId: storeId.value } })
  } catch (error) {
    errorText.value = apiErrorText(error)
  } finally {
    saving.value = false
  }
}

function toPayload(): TenantAdminTableMutation {
  const payload: TenantAdminTableMutation = {
    areaName: form.areaName.trim(),
    tableCode: form.tableCode.trim(),
    capacity: Number(form.capacity),
    enabled: form.enabled
  }
  const areaSortOrder = optionalSortOrder(form.areaSortOrder)
  const tableSortOrder = optionalSortOrder(form.tableSortOrder)
  if (areaSortOrder !== undefined) {
    payload.areaSortOrder = areaSortOrder
  }
  if (tableSortOrder !== undefined) {
    payload.tableSortOrder = tableSortOrder
  }
  return payload
}

function setSortOrder(field: SortField, event: Event): void {
  const value = (event.target as HTMLInputElement).value
  form[field] = value === '' ? null : Number(value)
}

function optionalSortOrder(value: number | null): number | undefined {
  return typeof value === 'number' && Number.isFinite(value) ? value : undefined
}

function apiErrorText(error: unknown): string {
  if (!(error instanceof TenantAdminApiError)) {
    return gt('generated.tenant-admin-table-form.016')
  }
  if (error.status === 401) {
    auth.clear()
    return gt('generated.tenant-admin-table-form.017')
  }
  if (error.response.error.code === 'TABLE_CODE_CONFLICT') {
    return gt('generated.tenant-admin-table-form.018')
  }
  if (error.response.error.code === 'TABLE_NOT_FOUND') {
    return gt('generated.tenant-admin-table-form.019')
  }
  if (error.response.error.code === 'TABLE_IN_USE') {
    return gt('generated.tenant-admin-table-form.020')
  }
  if (error.response.error.code === 'REQUEST_INVALID') {
    return gt('generated.tenant-admin-table-form.021')
  }
  if (error.response.error.code === 'STORE_SCOPE_MISMATCH') {
    return gt('generated.tenant-admin-table-form.022')
  }
  return gt('generated.tenant-admin-table-form.023')
}
</script>

<template>
  <main class="tenant-shell">
    <TenantAdminNav />

    <section class="tenant-workspace">
      <header class="page-heading">
        <div>
          <span>{{ gt('generated.tenant-admin-table-form.001') }}</span>
          <h1>{{ pageTitle }}</h1>
        </div>
        <button type="button" class="secondary-button" @click="router.push({ name: 'tenant-admin-tables', params: { storeId } })"> {{ gt('generated.tenant-admin-table-form.002') }} </button>
      </header>

      <p v-if="errorText" class="error-banner" role="alert">{{ errorText }}</p>
      <p v-if="loading" class="loading-line">{{ gt('generated.tenant-admin-table-form.003') }}</p>

      <form v-else class="form-panel" @submit.prevent="submitTable">
        <label>
          <span>{{ gt('generated.tenant-admin-table-form.004') }}</span>
          <input v-model.trim="form.areaName" required />
        </label>
        <label>
          <span>{{ gt('generated.tenant-admin-table-form.005') }}</span>
          <input v-model.trim="form.tableCode" required />
        </label>
        <label>
          <span>{{ gt('generated.tenant-admin-table-form.006') }}</span>
          <input v-model.number="form.capacity" type="number" min="1" max="999" required />
        </label>
        <label>
          <span>{{ gt('generated.tenant-admin-table-form.007') }}</span>
          <input
            :value="form.areaSortOrder ?? ''"
            type="number"
            min="0"
            :placeholder="gt('generated.tenant-admin-table-form.008')"
            @input="setSortOrder('areaSortOrder', $event)"
          />
        </label>
        <label>
          <span>{{ gt('generated.tenant-admin-table-form.009') }}</span>
          <input
            :value="form.tableSortOrder ?? ''"
            type="number"
            min="0"
            :placeholder="gt('generated.tenant-admin-table-form.010')"
            @input="setSortOrder('tableSortOrder', $event)"
          />
        </label>
        <label class="check-row">
          <input v-model="form.enabled" type="checkbox" />
          <span>{{ gt('generated.tenant-admin-table-form.011') }}</span>
        </label>
        <div class="form-actions">
          <button class="primary-button" type="submit" :disabled="saving">
            {{ saving ? gt('generated.tenant-admin-table-form.012') : gt('generated.tenant-admin-table-form.013') }}
          </button>
        </div>
      </form>
    </section>
  </main>
</template>

<style scoped>
.tenant-shell {
  min-height: 100dvh;
  display: grid;
  grid-template-columns: 220px minmax(0, 1fr);
  background: #f3f6f8;
  color: #102033;
}

.tenant-workspace {
  min-width: 0;
  padding: 22px;
}

.page-heading {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: center;
  margin-bottom: 16px;
}

.page-heading span {
  color: #64748b;
  font-size: 13px;
  font-weight: 700;
}

.page-heading h1 {
  margin: 0;
  color: #0f172a;
  font-size: 24px;
}

.secondary-button,
.primary-button {
  min-height: 38px;
  border-radius: 6px;
  padding: 0 14px;
  font: inherit;
  font-weight: 800;
  cursor: pointer;
}

.secondary-button {
  border: 1px solid #cbd5e1;
  color: #0f172a;
  background: #ffffff;
}

.primary-button {
  border: 0;
  color: #ffffff;
  background: #0f766e;
}

.primary-button:disabled {
  opacity: 0.6;
  cursor: default;
}

.error-banner,
.loading-line {
  margin: 0 0 12px;
  padding: 10px 12px;
  border-radius: 6px;
}

.error-banner {
  border: 1px solid #fecaca;
  color: #991b1b;
  background: #fff1f2;
}

.loading-line {
  border: 1px solid #dbe3ea;
  color: #475569;
  background: #ffffff;
}

.form-panel {
  width: min(100%, 640px);
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
  padding: 18px;
  border: 1px solid #dbe3ea;
  border-radius: 8px;
  background: #ffffff;
}

label {
  display: grid;
  gap: 7px;
  color: #334155;
  font-size: 14px;
  font-weight: 700;
}

input {
  width: 100%;
  min-height: 40px;
  box-sizing: border-box;
  border: 1px solid #cbd5e1;
  border-radius: 6px;
  padding: 9px 10px;
  color: #0f172a;
  background: #ffffff;
  font: inherit;
}

.check-row {
  grid-template-columns: auto 1fr;
  align-content: end;
  align-items: center;
  gap: 10px;
  min-height: 67px;
}

.check-row input {
  width: 18px;
  min-height: 18px;
  accent-color: #0f766e;
}

.form-actions {
  grid-column: 1 / -1;
  display: flex;
  justify-content: flex-end;
}

@media (max-width: 980px) {
  .tenant-shell {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 700px) {
  .tenant-workspace {
    padding: 14px;
  }

  .page-heading,
  .form-panel {
    display: grid;
    grid-template-columns: 1fr;
  }

  .secondary-button,
  .primary-button {
    width: 100%;
  }
}
</style>
