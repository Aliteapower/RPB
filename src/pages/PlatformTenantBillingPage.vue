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
  durationCount: 1,
  currency: 'SGD',
  paymentNote: ''
})

interface BillingRow {
  productLine: PlatformProductLine
  subscription: TenantProductSubscription | null
}

const billingRows = computed<BillingRow[]>(() => productLines.value.map(productLine => ({
  productLine,
  subscription: subscriptions.value.find(subscription => subscription.appKey === productLine.appKey) ?? null
})))
const selectedProductLine = computed(() => productLines.value.find(productLine => productLine.appKey === form.appKey) ?? null)
const selectedSubscription = computed(() => subscriptions.value.find(subscription => subscription.appKey === form.appKey) ?? null)
const selectedPrice = computed(() => selectedProductLine.value?.prices.find(price => price.billingCycle === form.billingCycle) ?? null)
const selectedUnitPrice = computed(() => selectedPrice.value?.amount ?? 0)
const selectedCurrency = computed(() => selectedPrice.value?.currency ?? (form.currency.trim().toUpperCase() || 'SGD'))
const safeDurationCount = computed(() => Math.max(1, Number(form.durationCount) || 1))
const calculatedAmount = computed(() => selectedUnitPrice.value * safeDurationCount.value)
const durationUnitLabel = computed(() => form.billingCycle === 'yearly' ? '年' : '个月')
const primaryActionLabel = computed(() => {
  const subscription = selectedSubscription.value
  if (!subscription) {
    return '购买'
  }
  if (subscription.billingCycle === 'legacy_grant') {
    return '转付费'
  }
  if (subscription.status === 'cancelled') {
    return '重新开通'
  }
  if (subscription.status === 'suspended') {
    return '恢复并续费'
  }
  return '续费'
})

onMounted(() => {
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

async function submitSelectedProductMutation(): Promise<void> {
  const subscription = selectedSubscription.value
  if (!subscription) {
    await purchaseSelectedProduct()
    return
  }
  if (subscription.billingCycle === 'legacy_grant') {
    await convertLegacyProduct(subscription)
    return
  }
  await renewSelectedProduct(subscription)
}

async function purchaseBillingRow(row: BillingRow): Promise<void> {
  selectBillingRow(row)
  await purchaseSelectedProduct()
}

async function renewBillingRow(row: BillingRow): Promise<void> {
  if (!row.subscription) {
    return
  }
  selectBillingRow(row)
  await renewSelectedProduct(row.subscription)
}

async function convertLegacyBillingRow(row: BillingRow): Promise<void> {
  if (!row.subscription) {
    return
  }
  selectBillingRow(row)
  await convertLegacyProduct(row.subscription)
}

async function suspendBillingRow(row: BillingRow): Promise<void> {
  if (!row.subscription) {
    return
  }
  selectBillingRow(row)
  await suspendSelectedProduct(row.subscription)
}

async function cancelBillingRow(row: BillingRow): Promise<void> {
  if (!row.subscription) {
    return
  }
  selectBillingRow(row)
  await cancelSelectedProduct(row.subscription)
}

async function toggleProductLine(row: BillingRow, event: Event): Promise<void> {
  selectBillingRow(row)
  const checked = event.target instanceof HTMLInputElement && event.target.checked
  if (checked) {
    await submitSelectedProductMutation()
    return
  }
  if (row.subscription && canCancel(row.subscription)) {
    await cancelSelectedProduct(row.subscription)
  }
}

async function renewSelectedProduct(subscription: TenantProductSubscription): Promise<void> {
  await saveMutation(() => renewProductSubscription(tenantId.value, subscription.id, mutationPayload(subscription.version, subscription.appKey)))
}

async function convertLegacyProduct(subscription: TenantProductSubscription): Promise<void> {
  if (form.billingCycle === 'manual') {
    savedText.value = ''
    errorText.value = '历史赠送只能转月付或年付'
    return
  }
  await saveMutation(() => convertLegacyProductSubscription(tenantId.value, subscription.id, mutationPayload(subscription.version, subscription.appKey)))
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

function mutationPayload(version?: number, appKey = form.appKey) {
  return {
    idempotencyKey: newIdempotencyKey(),
    appKey,
    billingCycle: form.billingCycle,
    durationCount: safeDurationCount.value,
    currency: selectedCurrency.value,
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

function selectBillingRow(row: BillingRow): void {
  const changingProductLine = form.appKey !== row.productLine.appKey
  form.appKey = row.productLine.appKey
  if (changingProductLine && (row.subscription?.billingCycle === 'monthly' || row.subscription?.billingCycle === 'yearly')) {
    form.billingCycle = row.subscription.billingCycle
  }
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

function formatDateTime(value: string | null): string {
  if (!value) {
    return '永久有效'
  }
  return new Date(value).toLocaleString()
}

function formatAmount(value: number): string {
  return Number(value || 0).toFixed(2)
}

function isProductLineChecked(row: BillingRow): boolean {
  return row.subscription?.status === 'active' && row.subscription.effectiveStatus !== 'expired'
}

function canRenew(subscription: TenantProductSubscription): boolean {
  return subscription.billingCycle !== 'legacy_grant'
}

function canSuspend(subscription: TenantProductSubscription): boolean {
  return subscription.status === 'active'
}

function canCancel(subscription: TenantProductSubscription): boolean {
  return subscription.status !== 'cancelled'
}

function canConvertLegacy(subscription: TenantProductSubscription): boolean {
  return subscription.billingCycle === 'legacy_grant' && subscription.status === 'active'
}

function canReactivate(subscription: TenantProductSubscription): boolean {
  return subscription.status === 'cancelled'
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
                <th>有效期开始</th>
                <th>有效期结束</th>
                <th>金额</th>
                <th>币种</th>
                <th>授权</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="loading">
                <td colspan="9" class="table-empty">加载中</td>
              </tr>
              <tr v-else-if="billingRows.length === 0">
                <td colspan="9" class="table-empty">暂无产品线</td>
              </tr>
              <tr v-for="row in billingRows" v-else :key="row.productLine.appKey">
                <td>
                  <label class="product-line-check">
                    <input
                      type="checkbox"
                      :checked="isProductLineChecked(row)"
                      :disabled="saving"
                      @change="toggleProductLine(row, $event)"
                    >
                    <span>{{ row.productLine.displayName }}</span>
                  </label>
                </td>
                <td>{{ row.subscription ? billingCycleLabel(row.subscription.billingCycle) : '-' }}</td>
                <td>{{ row.subscription ? statusLabel(row.subscription) : '未开通' }}</td>
                <td>{{ row.subscription ? formatDateTime(row.subscription.currentPeriodStart) : '-' }}</td>
                <td>{{ row.subscription ? formatDateTime(row.subscription.currentPeriodEnd) : '-' }}</td>
                <td>{{ row.subscription ? formatAmount(row.subscription.amount) : '-' }}</td>
                <td>{{ row.subscription?.currency ?? row.productLine.prices[0]?.currency ?? '-' }}</td>
                <td>{{ row.subscription?.entitlementStatus ?? '-' }}</td>
                <td>
                  <div class="row-actions">
                    <button
                      v-if="!row.subscription"
                      type="button"
                      class="text-action"
                      :disabled="saving"
                      @click="purchaseBillingRow(row)"
                    >
                      开通
                    </button>
                    <button
                      v-if="row.subscription && canRenew(row.subscription)"
                      type="button"
                      class="text-action"
                      :disabled="saving"
                      @click="renewBillingRow(row)"
                    >
                      {{ canReactivate(row.subscription) ? '重新开通' : row.subscription.status === 'suspended' ? '恢复并续费' : '续费' }}
                    </button>
                    <button
                      v-if="row.subscription && canConvertLegacy(row.subscription)"
                      type="button"
                      class="text-action"
                      :disabled="saving"
                      @click="convertLegacyBillingRow(row)"
                    >
                      转付费
                    </button>
                    <button
                      v-if="row.subscription && canSuspend(row.subscription)"
                      type="button"
                      class="text-action"
                      :disabled="saving"
                      @click="suspendBillingRow(row)"
                    >
                      暂停
                    </button>
                    <button
                      v-if="row.subscription && canCancel(row.subscription)"
                      type="button"
                      class="text-action danger"
                      :disabled="saving"
                      @click="cancelBillingRow(row)"
                    >
                      取消
                    </button>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </section>

        <form class="edit-panel" @submit.prevent="submitSelectedProductMutation">
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
            </select>
          </label>
          <label>
            <span>购买数量</span>
            <div class="duration-row">
              <input v-model.number="form.durationCount" type="number" min="1" :max="form.billingCycle === 'monthly' ? 120 : 10">
              <span>{{ durationUnitLabel }}</span>
            </div>
          </label>
          <div class="quote-summary">
            <span>标准单价</span>
            <strong>{{ formatAmount(selectedUnitPrice) }} {{ selectedCurrency }} / {{ durationUnitLabel }}</strong>
          </div>
          <div class="quote-summary">
            <span>本次金额</span>
            <strong>{{ formatAmount(calculatedAmount) }} {{ selectedCurrency }}</strong>
          </div>
          <label>
            <span>币种</span>
            <input v-model.trim="form.currency" maxlength="3" :placeholder="selectedCurrency">
          </label>
          <label>
            <span>备注</span>
            <textarea v-model.trim="form.paymentNote" rows="4" />
          </label>
          <button class="primary-button" type="submit" :disabled="saving || loading">
            {{ saving ? '保存中' : primaryActionLabel }}
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

.product-line-check,
.duration-row,
.quote-summary {
  display: flex;
  gap: 8px;
  align-items: center;
}

.product-line-check input {
  width: 16px;
  height: 16px;
}

.duration-row input {
  flex: 1;
}

.duration-row span,
.quote-summary span {
  color: #64748b;
  font-size: 13px;
  font-weight: 700;
}

.quote-summary {
  justify-content: space-between;
  min-height: 34px;
}

.quote-summary strong {
  color: #0f172a;
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

.muted-text {
  color: #94a3b8;
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
