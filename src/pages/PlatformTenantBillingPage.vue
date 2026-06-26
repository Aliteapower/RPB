<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'

import {
  PlatformBillingApiError,
  cancelProductSubscription,
  convertLegacyProductSubscription,
  listProductLines,
  listTenantProductSubscriptions,
  purchaseProductSubscription,
  renewProductSubscription,
  suspendProductSubscription
} from '../api/platformProductLineBillingApi'
import PlatformAdminNav from '../components/platform/PlatformAdminNav.vue'
import type {
  PlatformProductLine,
  ProductBillingCycle,
  TenantProductSubscription
} from '../types/platformProductLineBilling'
import { useAuthSessionStore } from '../stores/authSession'

const route = useRoute()
const auth = useAuthSessionStore()
const tenantId = computed(() => String(route.params.tenantId || ''))
const productLines = ref<PlatformProductLine[]>([])
const subscriptions = ref<TenantProductSubscription[]>([])
const loading = ref(false)
const saving = ref(false)
const errorText = ref('')
const savedText = ref('')
const form = reactive({
  appKey: 'reservation_queue',
  billingCycle: 'monthly' as ProductBillingCycle,
  currentPeriodStart: '',
  currentPeriodEnd: '',
  amount: 0,
  currency: 'SGD',
  paymentNote: ''
})

onMounted(() => {
  const now = new Date()
  form.currentPeriodStart = toInputValue(now)
  form.currentPeriodEnd = toInputValue(new Date(now.getTime() + 30 * 24 * 60 * 60 * 1000))
  void loadBilling()
})

async function loadBilling(): Promise<void> {
  loading.value = true
  errorText.value = ''
  try {
    const [lineResponse, subscriptionResponse] = await Promise.all([
      listProductLines(),
      listTenantProductSubscriptions(tenantId.value)
    ])
    productLines.value = lineResponse.productLines
    subscriptions.value = subscriptionResponse.subscriptions
    const firstLine = lineResponse.productLines.find(item => item.appKey === 'reservation_queue') ?? lineResponse.productLines[0]
    if (firstLine) {
      form.appKey = firstLine.appKey
    }
  } catch (error) {
    errorText.value = apiErrorText(error)
  } finally {
    loading.value = false
  }
}

async function purchaseSelectedProduct(): Promise<void> {
  await saveMutation(() => purchaseProductSubscription(tenantId.value, mutationPayload()))
}

async function renewSelectedProduct(subscription: TenantProductSubscription): Promise<void> {
  await saveMutation(() => renewProductSubscription(tenantId.value, subscription.id, mutationPayload(subscription.version)))
}

async function convertLegacyProduct(subscription: TenantProductSubscription): Promise<void> {
  if (form.billingCycle === 'manual') {
    savedText.value = ''
    errorText.value = '历史赠送只能转月付或年付'
    return
  }
  await saveMutation(() => convertLegacyProductSubscription(tenantId.value, subscription.id, mutationPayload(subscription.version)))
}

async function suspendSelectedProduct(subscription: TenantProductSubscription): Promise<void> {
  await saveMutation(() => suspendProductSubscription(tenantId.value, subscription.id, statusPayload(subscription.version, '手工暂停')))
}

async function cancelSelectedProduct(subscription: TenantProductSubscription): Promise<void> {
  await saveMutation(() => cancelProductSubscription(tenantId.value, subscription.id, statusPayload(subscription.version, '手工取消')))
}

async function saveMutation(action: () => Promise<unknown>): Promise<void> {
  if (saving.value) {
    return
  }
  saving.value = true
  errorText.value = ''
  savedText.value = ''
  try {
    await action()
    savedText.value = '已保存'
    await loadBilling()
  } catch (error) {
    errorText.value = apiErrorText(error)
  } finally {
    saving.value = false
  }
}

function mutationPayload(version?: number) {
  return {
    idempotencyKey: newIdempotencyKey(),
    appKey: form.appKey,
    billingCycle: form.billingCycle,
    currentPeriodStart: toIso(form.currentPeriodStart),
    currentPeriodEnd: form.billingCycle === 'manual' && !form.currentPeriodEnd ? null : toIso(form.currentPeriodEnd),
    amount: Number(form.amount) || 0,
    currency: form.currency.trim().toUpperCase() || 'SGD',
    paymentNote: form.paymentNote.trim() || null,
    version
  }
}

function statusPayload(version: number, fallbackNote: string) {
  return {
    idempotencyKey: newIdempotencyKey(),
    paymentNote: form.paymentNote.trim() || fallbackNote,
    version
  }
}

function newIdempotencyKey(): string {
  return `manual-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`
}

function billingCycleLabel(cycle: ProductBillingCycle): string {
  if (cycle === 'legacy_grant') {
    return '历史赠送 / 永久有效'
  }
  if (cycle === 'monthly') {
    return '月付'
  }
  if (cycle === 'yearly') {
    return '年付'
  }
  return '手工'
}

function statusLabel(subscription: TenantProductSubscription): string {
  if (subscription.effectiveStatus === 'expired') {
    return '已到期'
  }
  if (subscription.status === 'active') {
    return '生效中'
  }
  if (subscription.status === 'suspended') {
    return '已暂停'
  }
  return '已取消'
}

function toInputValue(value: Date): string {
  return value.toISOString().slice(0, 16)
}

function toIso(value: string): string | null {
  if (!value) {
    return null
  }
  return new Date(value).toISOString()
}

function apiErrorText(error: unknown): string {
  if (!(error instanceof PlatformBillingApiError)) {
    return '操作失败'
  }
  if (error.status === 401) {
    auth.clear()
    return '登录已失效'
  }
  if (error.response.error.code === 'FORBIDDEN') {
    return '没有计费管理权限'
  }
  if (error.response.error.code === 'SUBSCRIPTION_CONFLICT') {
    return '订阅状态冲突'
  }
  if (error.response.error.code === 'VERSION_CONFLICT') {
    return '订阅已被其他操作更新，请刷新后重试'
  }
  return '操作失败'
}
</script>

<template>
  <main class="platform-shell">
    <PlatformAdminNav />

    <section class="platform-workspace">
      <header class="page-heading">
        <div>
          <span>租户管理</span>
          <h1>订阅 / 计费</h1>
        </div>
        <button class="secondary-button" type="button" :disabled="loading" @click="loadBilling">
          刷新
        </button>
      </header>

      <p v-if="errorText" class="error-banner" role="alert">{{ errorText }}</p>
      <p v-if="savedText" class="success-banner">{{ savedText }}</p>

      <div class="workspace-grid">
        <section class="table-panel">
          <table class="data-table">
            <thead>
              <tr>
                <th>产品线</th>
                <th>计费周期</th>
                <th>状态</th>
                <th>有效期</th>
                <th>授权</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="loading">
                <td colspan="6" class="table-empty">加载中</td>
              </tr>
              <tr v-else-if="subscriptions.length === 0">
                <td colspan="6" class="table-empty">暂无订阅</td>
              </tr>
              <tr v-for="subscription in subscriptions" v-else :key="subscription.id">
                <td>{{ subscription.productLineName }}</td>
                <td>{{ billingCycleLabel(subscription.billingCycle) }}</td>
                <td>{{ statusLabel(subscription) }}</td>
                <td>
                  <span v-if="subscription.currentPeriodEnd">{{ new Date(subscription.currentPeriodEnd).toLocaleString() }}</span>
                  <span v-else>永久有效</span>
                </td>
                <td>{{ subscription.entitlementStatus }}</td>
                <td>
                  <div class="row-actions">
                    <button type="button" class="text-action" :disabled="saving" @click="renewSelectedProduct(subscription)">
                      续费
                    </button>
                    <button
                      v-if="subscription.billingCycle === 'legacy_grant'"
                      type="button"
                      class="text-action"
                      :disabled="saving"
                      @click="convertLegacyProduct(subscription)"
                    >
                      转付费
                    </button>
                    <button type="button" class="text-action" :disabled="saving" @click="suspendSelectedProduct(subscription)">
                      暂停
                    </button>
                    <button type="button" class="text-action danger" :disabled="saving" @click="cancelSelectedProduct(subscription)">
                      取消
                    </button>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </section>

        <form class="edit-panel" @submit.prevent="purchaseSelectedProduct">
          <h2>手工购买 / 续费</h2>
          <label>
            <span>产品线</span>
            <select v-model="form.appKey">
              <option v-for="productLine in productLines" :key="productLine.appKey" :value="productLine.appKey">
                {{ productLine.displayName }}
              </option>
            </select>
          </label>
          <label>
            <span>计费周期</span>
            <select v-model="form.billingCycle">
              <option value="monthly">月付</option>
              <option value="yearly">年付</option>
              <option value="manual">手工</option>
            </select>
          </label>
          <label>
            <span>开始时间</span>
            <input v-model="form.currentPeriodStart" type="datetime-local">
          </label>
          <label>
            <span>结束时间</span>
            <input v-model="form.currentPeriodEnd" type="datetime-local">
          </label>
          <label>
            <span>金额</span>
            <input v-model.number="form.amount" type="number" min="0" step="0.01">
          </label>
          <label>
            <span>币种</span>
            <input v-model.trim="form.currency" maxlength="3">
          </label>
          <label>
            <span>备注</span>
            <textarea v-model.trim="form.paymentNote" rows="4" />
          </label>
          <button class="primary-button" type="submit" :disabled="saving || loading">
            {{ saving ? '保存中' : '购买' }}
          </button>
        </form>
      </div>
    </section>
  </main>
</template>

<style scoped>
.platform-shell {
  min-height: 100dvh;
  display: grid;
  grid-template-columns: 220px minmax(0, 1fr);
  background: #f3f6f8;
  color: #102033;
}

.platform-workspace {
  min-width: 0;
  padding: 22px;
}

.page-heading,
.workspace-grid {
  display: grid;
  gap: 16px;
}

.page-heading {
  grid-template-columns: 1fr auto;
  align-items: center;
  margin-bottom: 16px;
}

.page-heading span,
.edit-panel label span {
  color: #64748b;
  font-size: 13px;
  font-weight: 700;
}

.page-heading h1,
.edit-panel h2 {
  margin: 0;
  color: #0f172a;
}

.page-heading h1 {
  font-size: 24px;
}

.edit-panel h2 {
  font-size: 18px;
}

.workspace-grid {
  grid-template-columns: minmax(0, 1.55fr) minmax(320px, 0.85fr);
  align-items: start;
}

.table-panel,
.edit-panel {
  border: 1px solid #dbe3ea;
  border-radius: 8px;
  background: #ffffff;
}

.table-panel {
  overflow-x: auto;
}

.data-table {
  width: 100%;
  min-width: 980px;
  border-collapse: collapse;
}

.data-table th,
.data-table td {
  padding: 12px 14px;
  border-bottom: 1px solid #edf2f7;
  text-align: left;
  vertical-align: middle;
}

.data-table th {
  color: #64748b;
  background: #f8fafc;
  font-size: 13px;
}

.table-empty {
  color: #64748b;
  text-align: center;
}

.row-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.text-action {
  border: 0;
  padding: 0;
  color: #0f766e;
  background: transparent;
  font: inherit;
  font-weight: 800;
  cursor: pointer;
}

.text-action.danger {
  color: #b91c1c;
}

.text-action:disabled {
  opacity: 0.55;
  cursor: default;
}

.edit-panel {
  display: grid;
  gap: 14px;
  padding: 16px;
}

.edit-panel label {
  display: grid;
  gap: 6px;
}

.edit-panel input,
.edit-panel select,
.edit-panel textarea {
  min-height: 38px;
  border: 1px solid #cbd5e1;
  border-radius: 6px;
  padding: 8px 10px;
  font: inherit;
}

.primary-button,
.secondary-button {
  min-height: 36px;
  border-radius: 6px;
  padding: 0 14px;
  font: inherit;
  font-weight: 800;
  cursor: pointer;
}

.primary-button {
  border: 0;
  color: #ffffff;
  background: #0f766e;
}

.secondary-button {
  border: 1px solid #cbd5e1;
  color: #334155;
  background: #ffffff;
}

.primary-button:disabled,
.secondary-button:disabled {
  opacity: 0.55;
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

@media (max-width: 980px) {
  .platform-shell,
  .workspace-grid {
    grid-template-columns: 1fr;
  }
}
</style>
