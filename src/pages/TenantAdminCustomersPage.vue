<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'

import {
  archiveCustomer,
  createCustomer,
  listCustomers,
  TenantAdminApiError,
  type TenantAdminCustomer,
  type TenantAdminCustomerMutation,
  type TenantAdminPage,
  updateCustomer
} from '../api/tenantAdminApi'
import ErpPagination from '../components/erp/ErpPagination.vue'
import ErpQueryToolbar from '../components/erp/ErpQueryToolbar.vue'
import TenantAdminNav from '../components/tenant-admin/TenantAdminNav.vue'
import { useGeneratedText } from '../i18n/generatedText'
import { useAuthSessionStore } from '../stores/authSession'

const { gt } = useGeneratedText()

const route = useRoute()
const auth = useAuthSessionStore()
const customers = ref<TenantAdminCustomer[]>([])
const loading = ref(false)
const saving = ref(false)
const archivingId = ref('')
const errorText = ref('')
const successText = ref('')
const keyword = ref('')
const limit = ref(20)
const offset = ref(0)
const page = ref<TenantAdminPage>({
  limit: 20,
  offset: 0,
  total: 0
})
const editorOpen = ref(false)
const editingCustomer = ref<TenantAdminCustomer | null>(null)
const form = reactive({
  displayName: '',
  nickname: '',
  phoneE164: '',
  email: ''
})

const storeId = computed(() => String(route.params.storeId || ''))
const hasDirtyQuery = computed(() => keyword.value.trim() !== '')
const hasPhoneCount = computed(() => customers.value.filter(item => Boolean(item.phoneE164)).length)
const hasEmailCount = computed(() => customers.value.filter(item => Boolean(item.email)).length)
const safePage = computed<TenantAdminPage>(() => page.value ?? {
  limit: limit.value,
  offset: offset.value,
  total: customers.value.length
})
const actionDisabled = computed(() => loading.value || saving.value || archivingId.value !== '')

onMounted(() => {
  void loadCustomers()
  window.addEventListener('focus', refreshVisibleCustomers)
  document.addEventListener('visibilitychange', refreshVisibleCustomers)
})

onUnmounted(() => {
  window.removeEventListener('focus', refreshVisibleCustomers)
  document.removeEventListener('visibilitychange', refreshVisibleCustomers)
})

async function loadCustomers(nextOffset = offset.value): Promise<void> {
  loading.value = true
  errorText.value = ''
  try {
    const response = await listCustomers(storeId.value, {
      keyword: keyword.value,
      limit: limit.value,
      offset: nextOffset
    })
    customers.value = response.customers
    page.value = response.page
    offset.value = response.page.offset
  } catch (error) {
    errorText.value = apiErrorText(error)
  } finally {
    loading.value = false
  }
}

function searchCustomers(): void {
  offset.value = 0
  void loadCustomers(0)
}

function resetFilters(): void {
  keyword.value = ''
  offset.value = 0
  void loadCustomers(0)
}

function refreshVisibleCustomers(): void {
  if (document.visibilityState !== 'visible' || editorOpen.value || actionDisabled.value) {
    return
  }
  void loadCustomers()
}

function openCreateEditor(): void {
  editingCustomer.value = null
  resetForm()
  errorText.value = ''
  successText.value = ''
  editorOpen.value = true
}

function openEditEditor(item: TenantAdminCustomer): void {
  editingCustomer.value = item
  form.displayName = item.displayName || ''
  form.nickname = item.nickname || ''
  form.phoneE164 = item.phoneE164 || ''
  form.email = item.email || ''
  errorText.value = ''
  successText.value = ''
  editorOpen.value = true
}

function closeEditor(): void {
  editorOpen.value = false
  editingCustomer.value = null
  resetForm()
}

async function submitCustomer(): Promise<void> {
  if (saving.value) {
    return
  }

  const payload = customerPayload()
  if (!payload.displayName && !payload.phoneE164 && !payload.email) {
    errorText.value = gt('generated.tenant-admin-customers.035')
    return
  }

  saving.value = true
  errorText.value = ''
  successText.value = ''
  const reloadOffset = editingCustomer.value ? offset.value : 0
  try {
    if (editingCustomer.value) {
      await updateCustomer(storeId.value, editingCustomer.value.id, payload)
    } else {
      await createCustomer(storeId.value, payload)
      offset.value = 0
    }
    closeEditor()
    await loadCustomers(reloadOffset)
    successText.value = gt('generated.tenant-admin-customers.038')
  } catch (error) {
    errorText.value = apiErrorText(error)
  } finally {
    saving.value = false
  }
}

async function archiveCustomerRecord(item: TenantAdminCustomer): Promise<void> {
  if (archivingId.value) {
    return
  }
  if (!window.confirm(gt('generated.tenant-admin-customers.036'))) {
    return
  }

  archivingId.value = item.id
  errorText.value = ''
  successText.value = ''
  try {
    await archiveCustomer(storeId.value, item.id)
    const nextOffset = customers.value.length === 1 && offset.value > 0
      ? Math.max(0, offset.value - limit.value)
      : offset.value
    await loadCustomers(nextOffset)
    successText.value = gt('generated.tenant-admin-customers.037')
  } catch (error) {
    errorText.value = apiErrorText(error)
  } finally {
    archivingId.value = ''
  }
}

function resetForm(): void {
  form.displayName = ''
  form.nickname = ''
  form.phoneE164 = ''
  form.email = ''
}

function customerPayload(): TenantAdminCustomerMutation {
  return {
    displayName: blankToNull(form.displayName),
    nickname: blankToNull(form.nickname),
    phoneE164: blankToNull(form.phoneE164),
    email: blankToNull(form.email)
  }
}

function blankToNull(value: string): string | null {
  const text = value.trim()
  return text === '' ? null : text
}

function displayValue(value: string | null): string {
  return value?.trim() ? value : '-'
}

function statusLabel(status: string): string {
  return status === 'active' ? gt('generated.tenant-admin-customers.019') : status
}

function apiErrorText(error: unknown): string {
  if (!(error instanceof TenantAdminApiError)) {
    return gt('generated.tenant-admin-customers.029')
  }
  if (error.status === 401) {
    auth.clear()
    return gt('generated.tenant-admin-customers.030')
  }
  if (error.response.error.code === 'STORE_SCOPE_MISMATCH') {
    return gt('generated.tenant-admin-customers.031')
  }
  if (error.response.error.code === 'FORBIDDEN') {
    return gt('generated.tenant-admin-customers.032')
  }
  if (error.response.error.code === 'CUSTOMER_PHONE_CONFLICT') {
    return gt('generated.tenant-admin-customers.033')
  }
  if (error.response.error.code === 'CUSTOMER_NOT_FOUND') {
    return gt('generated.tenant-admin-customers.034')
  }
  if (error.response.error.code === 'REQUEST_INVALID') {
    return gt('generated.tenant-admin-customers.035')
  }
  return gt('generated.tenant-admin-customers.029')
}
</script>

<template>
  <main class="tenant-shell">
    <TenantAdminNav />

    <section class="tenant-workspace">
      <header class="page-heading">
        <div>
          <span>{{ gt('generated.tenant-admin-customers.001') }}</span>
          <h1>{{ gt('generated.tenant-admin-customers.002') }}</h1>
        </div>
      </header>

      <ErpQueryToolbar
        v-model:keyword="keyword"
        :placeholder="gt('generated.tenant-admin-customers.003')"
        :loading="actionDisabled"
        :has-dirty-query="hasDirtyQuery"
        @search="searchCustomers"
        @reset="resetFilters"
        @refresh="loadCustomers()"
      >
        <template #filters>
          <div class="summary-strip">
            <strong>{{ gt('generated.tenant-admin-customers.004') }} {{ safePage.total }}</strong>
            <span>{{ gt('generated.tenant-admin-customers.005') }} {{ hasPhoneCount }}</span>
            <span>{{ gt('generated.tenant-admin-customers.006') }} {{ hasEmailCount }}</span>
          </div>
        </template>
        <template #actions>
          <button class="primary-button" type="button" :disabled="actionDisabled" @click="openCreateEditor">
            {{ gt('generated.tenant-admin-customers.007') }}
          </button>
        </template>
      </ErpQueryToolbar>

      <p v-if="errorText" class="error-banner" role="alert">{{ errorText }}</p>
      <p v-if="successText" class="success-banner" role="status">{{ successText }}</p>

      <div class="content-layout" :class="{ 'with-editor': editorOpen }">
        <div class="list-panel">
          <div class="erp-table-wrap">
            <table>
              <thead>
                <tr>
                  <th>{{ gt('generated.tenant-admin-customers.008') }}</th>
                  <th>{{ gt('generated.tenant-admin-customers.009') }}</th>
                  <th>{{ gt('generated.tenant-admin-customers.010') }}</th>
                  <th>{{ gt('generated.tenant-admin-customers.011') }}</th>
                  <th>{{ gt('generated.tenant-admin-customers.012') }}</th>
                  <th>{{ gt('generated.tenant-admin-customers.013') }}</th>
                  <th>{{ gt('generated.tenant-admin-customers.014') }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-if="loading">
                  <td colspan="7" class="empty-cell">{{ gt('generated.tenant-admin-customers.015') }}</td>
                </tr>
                <tr v-else-if="customers.length === 0">
                  <td colspan="7" class="empty-cell">{{ gt('generated.tenant-admin-customers.016') }}</td>
                </tr>
                <tr v-for="item in customers" v-else :key="item.id">
                  <td><strong>{{ item.customerCode }}</strong></td>
                  <td>{{ displayValue(item.displayName) }}</td>
                  <td>{{ displayValue(item.nickname) }}</td>
                  <td>{{ displayValue(item.phoneE164) }}</td>
                  <td>{{ displayValue(item.email) }}</td>
                  <td>
                    <span class="status-pill" :class="{ muted: item.status !== 'active' }">
                      {{ statusLabel(item.status) }}
                    </span>
                  </td>
                  <td>
                    <div class="row-actions">
                      <button type="button" class="link-button" :disabled="actionDisabled" @click="openEditEditor(item)">
                        {{ gt('generated.tenant-admin-customers.017') }}
                      </button>
                      <button
                        type="button"
                        class="danger-link-button"
                        :disabled="actionDisabled"
                        @click="archiveCustomerRecord(item)"
                      >
                        {{ archivingId === item.id ? gt('generated.tenant-admin-customers.026') : gt('generated.tenant-admin-customers.018') }}
                      </button>
                    </div>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>

          <ErpPagination
            :limit="safePage.limit"
            :offset="safePage.offset"
            :total="safePage.total"
            :loading="actionDisabled"
            @change="loadCustomers"
          />
        </div>

        <aside v-if="editorOpen" class="editor-panel" :aria-label="gt('generated.tenant-admin-customers.020')">
          <form class="customer-form" @submit.prevent="submitCustomer">
            <header class="editor-heading">
              <span>{{ editingCustomer ? gt('generated.tenant-admin-customers.022') : gt('generated.tenant-admin-customers.021') }}</span>
              <button type="button" class="icon-close-button" :disabled="saving" @click="closeEditor">
                ×
              </button>
            </header>

            <label>
              <span>{{ gt('generated.tenant-admin-customers.009') }} <small>{{ gt('generated.tenant-admin-customers.023') }}</small></span>
              <input v-model.trim="form.displayName" name="displayName" autocomplete="name" />
            </label>

            <label>
              <span>{{ gt('generated.tenant-admin-customers.010') }} <small>{{ gt('generated.tenant-admin-customers.023') }}</small></span>
              <select v-model="form.nickname" name="nickname">
                <option value="">{{ gt('generated.tenant-admin-customers.023') }}</option>
                <option value="先生">{{ gt('generated.tenant-admin-customers.024') }}</option>
                <option value="女士">{{ gt('generated.tenant-admin-customers.025') }}</option>
              </select>
            </label>

            <label>
              <span>{{ gt('generated.tenant-admin-customers.011') }} <small>{{ gt('generated.tenant-admin-customers.023') }}</small></span>
              <input v-model.trim="form.phoneE164" name="phoneE164" type="tel" inputmode="tel" placeholder="+6591234567" />
            </label>

            <label>
              <span>{{ gt('generated.tenant-admin-customers.012') }} <small>{{ gt('generated.tenant-admin-customers.023') }}</small></span>
              <input v-model.trim="form.email" name="email" type="email" autocomplete="email" />
            </label>

            <div class="form-actions">
              <button class="secondary-button" type="button" :disabled="saving" @click="closeEditor">
                {{ gt('generated.tenant-admin-customers.028') }}
              </button>
              <button class="primary-button" type="submit" :disabled="saving">
                {{ saving ? gt('generated.tenant-admin-customers.026') : gt('generated.tenant-admin-customers.027') }}
              </button>
            </div>
          </form>
        </aside>
      </div>
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

.summary-strip {
  min-height: 36px;
  display: flex;
  gap: 12px;
  align-items: center;
  padding: 0 12px;
  border: 1px solid #cfd9e4;
  border-radius: 8px;
  background: #ffffff;
  color: #475569;
  font-size: 13px;
}

.summary-strip strong {
  color: #0f172a;
}

.content-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 14px;
  align-items: start;
}

.content-layout.with-editor {
  grid-template-columns: minmax(0, 1fr) minmax(300px, 360px);
}

.list-panel {
  min-width: 0;
}

.secondary-button,
.primary-button {
  min-height: 36px;
  padding: 0 14px;
  border-radius: 6px;
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

.secondary-button:disabled,
.primary-button:disabled,
.link-button:disabled,
.danger-link-button:disabled {
  opacity: 0.6;
  cursor: default;
}

.error-banner,
.success-banner {
  margin: 0 0 12px;
  padding: 10px 12px;
  border-radius: 6px;
}

.error-banner {
  border: 1px solid #fecaca;
  color: #991b1b;
  background: #fff1f2;
}

.success-banner {
  border: 1px solid #bbf7d0;
  color: #166534;
  background: #f0fdf4;
}

.erp-table-wrap {
  overflow-x: auto;
  border: 1px solid #dbe3ea;
  border-radius: 8px;
  background: #ffffff;
}

table {
  width: 100%;
  border-collapse: collapse;
  min-width: 860px;
}

th,
td {
  padding: 14px 12px;
  border-bottom: 1px solid #edf2f7;
  text-align: left;
}

th {
  color: #64748b;
  background: #f8fafc;
  font-size: 13px;
}

td {
  color: #102033;
}

tr:last-child td {
  border-bottom: 0;
}

.empty-cell {
  color: #64748b;
  text-align: center;
}

.status-pill {
  display: inline-flex;
  min-height: 26px;
  align-items: center;
  border-radius: 999px;
  padding: 0 9px;
  color: #047857;
  background: #dcfce7;
  font-size: 12px;
  font-weight: 800;
}

.status-pill.muted {
  color: #64748b;
  background: #e2e8f0;
}

.row-actions {
  display: inline-flex;
  gap: 10px;
  align-items: center;
}

.link-button,
.danger-link-button {
  border: 0;
  background: transparent;
  font: inherit;
  font-weight: 800;
  cursor: pointer;
}

.link-button {
  color: #0f766e;
}

.danger-link-button {
  color: #b91c1c;
}

.editor-panel {
  position: sticky;
  top: 16px;
  border: 1px solid #dbe3ea;
  border-radius: 8px;
  background: #ffffff;
}

.customer-form {
  display: grid;
  gap: 14px;
  padding: 16px;
}

.editor-heading {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  align-items: center;
}

.editor-heading span {
  color: #0f172a;
  font-size: 18px;
  font-weight: 800;
}

.icon-close-button {
  width: 32px;
  height: 32px;
  display: inline-grid;
  place-items: center;
  border: 1px solid #cbd5e1;
  border-radius: 6px;
  color: #334155;
  background: #ffffff;
  font: inherit;
  font-size: 20px;
  line-height: 1;
  cursor: pointer;
}

label {
  display: grid;
  gap: 6px;
  color: #334155;
  font-weight: 800;
}

label span {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  align-items: center;
}

label small {
  color: #64748b;
  font-size: 12px;
  font-weight: 700;
}

input,
select {
  width: 100%;
  min-height: 38px;
  border: 1px solid #cbd5e1;
  border-radius: 6px;
  padding: 8px 10px;
  color: #0f172a;
  background: #ffffff;
  font: inherit;
}

.form-actions {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  margin-top: 2px;
}

@media (max-width: 1100px) {
  .content-layout.with-editor {
    grid-template-columns: minmax(0, 1fr);
  }

  .editor-panel {
    position: static;
  }
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

  .summary-strip,
  .primary-button,
  .secondary-button {
    width: 100%;
  }

  .summary-strip {
    align-items: flex-start;
    flex-direction: column;
    gap: 4px;
    padding: 9px 12px;
  }

  .form-actions {
    grid-template-columns: 1fr;
  }
}
</style>
