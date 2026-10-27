<script setup lang="ts">
import CustomerActivationAction from '../modules/customers/CustomerActivationAction.vue'
import {customerAccessReturnTarget} from '../modules/customers/accessNavigation'
import {useCustomerAccessText} from '../modules/customers/accessText'
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import CustomerProfileFields from '../modules/customers/CustomerProfileFields.vue'
import CustomerRegistrationEntry from '../modules/customers/CustomerRegistrationEntry.vue'
import CustomerPrivacyRecords from '../modules/customers/CustomerPrivacyRecords.vue'
import CustomerActivationIssue from '../modules/customers/CustomerActivationIssue.vue'
import type {TenantAdminCustomer} from '../api/tenantAdminApi'
import ErpPagination from '../components/erp/ErpPagination.vue'
import ErpQueryToolbar from '../components/erp/ErpQueryToolbar.vue'
import TenantAdminNav from '../components/tenant-admin/TenantAdminNav.vue'
import { useGeneratedText } from '../i18n/generatedText'
import { customerManagementReturnTarget } from '../modules/wallet/customerManagementNavigation'
import { useCustomerManagement } from '../modules/customers/useCustomerManagement'
const {t:accessText}=useCustomerAccessText()
const {gt}=useGeneratedText(),{t}=useI18n(),route=useRoute()
const storeId=computed(()=>String(route.params.storeId||''))
const walletReturnTarget=computed(()=>customerManagementReturnTarget(storeId.value,route.query.from))
const {activationIssued,privacyAccepted,privacyNotice,noticeLoading,privacyCustomer,privacyRecords,privacyLoading,loadPrivacyNotice,openPrivacyRecords,canSubmit,canManage,customers,loading,saving,archivingId,errorText,successText,keyword,safePage,editorOpen,editingCustomer,form,actionDisabled,hasDirtyQuery,hasPhoneCount,hasEmailCount,loadCustomers,searchCustomers,resetFilters,openCreateEditor,openEditEditor,closeEditor,submitCustomer,archiveCustomerRecord}=useCustomerManagement(storeId,{keyword:typeof route.query.keyword==='string'?route.query.keyword:'',create:route.query.create==='1'})
const activationCustomer=ref<TenantAdminCustomer|null>(null)
watch(storeId,()=>activationCustomer.value=null)
watch([editorOpen,privacyCustomer,canManage],()=>{if(editorOpen.value||privacyCustomer.value||!canManage.value)activationCustomer.value=null})
function displayValue(value:string|null){return value?.trim()?value:'-'}
function statusLabel(status:string){return status==='active'?gt('generated.tenant-admin-customers.019'):status}
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
<RouterLink v-if="route.query.accessSource" :to="customerAccessReturnTarget(storeId,route.query.accessSource)">{{accessText('back')}}</RouterLink>
        <RouterLink v-else-if="walletReturnTarget" :to="walletReturnTarget">{{t(route.query.from==='wallet-recharge'?'wallet.operation.backRecharge':'wallet.operation.backWallet')}}</RouterLink>
      </header>

<p v-if="route.query.action==='activation'" class="shared-customer-note">{{accessText('activationHint')}}</p>
      <CustomerRegistrationEntry :store="storeId" /><p class="shared-customer-note">{{ t('customerAuth.sharedHint') }}</p>

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
                    <div class="row-actions"><CustomerActivationAction :customer="item" :disabled="actionDisabled" @select="activationCustomer=item" /><button type="button" class="link-button" :disabled="actionDisabled" @click="openPrivacyRecords(item)">{{gt('generated.tenant-admin-customers.040')}}</button>
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

        <CustomerActivationIssue v-if="activationCustomer && canManage" :key="activationCustomer.id" :store="storeId" :customer="activationCustomer" @close="activationCustomer=null" @issued="activationIssued" /><CustomerPrivacyRecords v-if="privacyCustomer" :customer="privacyCustomer" :records="privacyRecords" :loading="privacyLoading" @close="privacyCustomer=null" /><aside v-if="editorOpen" class="editor-panel" :aria-label="gt('generated.tenant-admin-customers.020')">
          <form class="customer-form" @submit.prevent="submitCustomer">
            <header class="editor-heading">
              <span>{{ editingCustomer ? gt('generated.tenant-admin-customers.022') : gt('generated.tenant-admin-customers.021') }}</span>
              <button type="button" class="icon-close-button" :disabled="saving" @click="closeEditor">
                ×
              </button>
            </header>

            <CustomerProfileFields :form="form" :disabled="saving" :show-privacy="!editingCustomer" v-model:privacy-accepted="privacyAccepted" :privacy-notice="privacyNotice" :notice-loading="noticeLoading" @reload-notice="loadPrivacyNotice" />

            <div class="form-actions">
              <button class="secondary-button" type="button" :disabled="saving" @click="closeEditor">
                {{ gt('generated.tenant-admin-customers.028') }}
              </button>
              <button class="primary-button" type="submit" :disabled="!canSubmit">
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
