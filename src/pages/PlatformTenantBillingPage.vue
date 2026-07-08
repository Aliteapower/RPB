<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'

import {
  PlatformBillingApiError,
  cancelProductSubscription,
  convertLegacyProductSubscription,
  listProductLines,
  listTenantProductSubscriptions,
  purchaseProductSubscription,
  renewProductSubscriptionItem,
  suspendProductSubscription
} from '../api/platformProductLineBillingApi'
import { listTenantStores, type PlatformStore } from '../api/platformApi'
import PlatformAdminNav from '../components/platform/PlatformAdminNav.vue'
import type {
  PlatformProductLine,
  ProductBillingCycle,
  TenantProductSubscription,
  TenantProductSubscriptionItem
} from '../types/platformProductLineBilling'
import { useAuthSessionStore } from '../stores/authSession'

const route = useRoute()
const { t } = useI18n()
const auth = useAuthSessionStore()
const tenantId = computed(() => String(route.params.tenantId || ''))
const productLines = ref<PlatformProductLine[]>([])
const subscriptions = ref<TenantProductSubscription[]>([])
const tenantStores = ref<PlatformStore[]>([])
const loading = ref(false)
const saving = ref(false)
const errorText = ref('')
const savedText = ref('')
const selectedStoreItemId = ref<string | null>(null)
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
const activeBillableStoreCount = computed(() => tenantStores.value.filter(store => store.status === 'active').length)
const storeItemRows = computed(() => billingItemsForSubscription(selectedSubscription.value))
const selectedStoreItem = computed(() => {
  if (storeItemRows.value.length === 0) {
    return null
  }
  return storeItemRows.value.find(item => item.id === selectedStoreItemId.value) ?? storeItemRows.value[0]
})
const singleStoreRenewalMode = computed(() => (
  selectedSubscription.value !== null &&
  selectedSubscription.value.billingCycle !== 'legacy_grant'
))
const quoteStoreCount = computed(() => (singleStoreRenewalMode.value ? (selectedStoreItem.value ? 1 : 0) : activeBillableStoreCount.value))
const billingDisabled = computed(() => (singleStoreRenewalMode.value ? selectedStoreItem.value === null : activeBillableStoreCount.value <= 0))
const storeUnitAmount = computed(() => selectedUnitPrice.value * safeDurationCount.value)
const calculatedAmount = computed(() => storeUnitAmount.value * quoteStoreCount.value)
const durationUnitLabel = computed(() => (
  form.billingCycle === 'yearly'
    ? t('platform.billing.units.year')
    : t('platform.billing.units.month')
))
const primaryActionLabel = computed(() => {
  const subscription = selectedSubscription.value
  if (!subscription) {
    return t('platform.billing.actions.purchase')
  }
  if (subscription.billingCycle === 'legacy_grant') {
    return t('platform.billing.actions.convert')
  }
  if (subscription.status === 'cancelled') {
    return t('platform.billing.actions.reactivate')
  }
  if (subscription.status === 'suspended') {
    return t('platform.billing.actions.resumeRenew')
  }
  return t('platform.billing.actions.renew')
})

onMounted(() => {
  void loadBilling()
})

watch(() => form.appKey, () => {
  ensureSelectedStoreItem()
})

async function loadBilling(): Promise<void> {
  loading.value = true
  errorText.value = ''
  try {
    const [lineResponse, subscriptionResponse, storesResponse] = await Promise.all([
      listProductLines(),
      listTenantProductSubscriptions(tenantId.value),
      listTenantStores(tenantId.value)
    ])
    productLines.value = lineResponse.productLines
    subscriptions.value = subscriptionResponse.subscriptions
    tenantStores.value = storesResponse.stores
    const firstLine = lineResponse.productLines.find(item => item.appKey === 'reservation_queue') ?? lineResponse.productLines[0]
    if (firstLine) {
      form.appKey = firstLine.appKey
    }
    ensureSelectedStoreItem()
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
  const item = selectedStoreItem.value
  if (!item) {
    savedText.value = ''
    errorText.value = t('platform.billing.storeItems.empty')
    return
  }
  await saveMutation(() => renewProductSubscriptionItem(
    tenantId.value,
    subscription.id,
    item.id,
    mutationPayload(item.version, subscription.appKey)
  ))
}

async function convertLegacyProduct(subscription: TenantProductSubscription): Promise<void> {
  if (form.billingCycle === 'manual') {
    savedText.value = ''
    errorText.value = t('platform.billing.errors.legacyConvertCycle')
    return
  }
  await saveMutation(() => convertLegacyProductSubscription(tenantId.value, subscription.id, mutationPayload(subscription.version, subscription.appKey)))
}

async function suspendSelectedProduct(subscription: TenantProductSubscription): Promise<void> {
  await saveMutation(() => suspendProductSubscription(
    tenantId.value,
    subscription.id,
    statusPayload(subscription.version, t('platform.billing.notes.manualSuspend'))
  ))
}

async function cancelSelectedProduct(subscription: TenantProductSubscription): Promise<void> {
  await saveMutation(() => cancelProductSubscription(
    tenantId.value,
    subscription.id,
    statusPayload(subscription.version, t('platform.billing.notes.manualCancel'))
  ))
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
    savedText.value = t('platform.billing.messages.saved')
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
  ensureSelectedStoreItem(row.subscription ?? null)
}

function selectStoreItem(item: TenantProductSubscriptionItem): void {
  selectedStoreItemId.value = item.id
}

function ensureSelectedStoreItem(subscription = selectedSubscription.value): void {
  const rows = billingItemsForSubscription(subscription)
  if (rows.length === 0) {
    selectedStoreItemId.value = null
    return
  }
  if (!rows.some(item => item.id === selectedStoreItemId.value)) {
    selectedStoreItemId.value = rows[0].id
  }
}

function billingCycleLabel(cycle: ProductBillingCycle): string {
  if (cycle === 'legacy_grant') {
    return t('platform.billing.cycles.legacyGrant')
  }
  if (cycle === 'monthly') {
    return t('platform.billing.cycles.monthly')
  }
  if (cycle === 'yearly') {
    return t('platform.billing.cycles.yearly')
  }
  return t('platform.billing.cycles.manual')
}

function statusLabel(subscription: TenantProductSubscription): string {
  if (subscription.effectiveStatus === 'expired') {
    return t('platform.billing.status.expired')
  }
  if (subscription.status === 'active') {
    return t('platform.billing.status.active')
  }
  if (subscription.status === 'suspended') {
    return t('platform.billing.status.suspended')
  }
  return t('platform.billing.status.cancelled')
}

function formatDateTime(value: string | null): string {
  if (!value) {
    return t('platform.billing.status.permanent')
  }
  return new Date(value).toLocaleString()
}

function formatAmount(value: number): string {
  return Number(value || 0).toFixed(2)
}

function storeItemName(item: TenantProductSubscriptionItem): string {
  const name = item.storeName || item.storeCode || item.storeId?.slice(0, 8) || '-'
  return item.operatingEntityName ? `${name} / ${item.operatingEntityName}` : name
}

function billingItemsForSubscription(subscription: TenantProductSubscription | null): TenantProductSubscription['items'] {
  return subscription ? subscription.items : []
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

function renewalActionLabel(subscription: TenantProductSubscription): string {
  if (canReactivate(subscription)) {
    return t('platform.billing.actions.reactivate')
  }
  if (subscription.status === 'suspended') {
    return t('platform.billing.actions.resumeRenew')
  }
  return t('platform.billing.actions.renew')
}

function apiErrorText(error: unknown): string {
  if (!(error instanceof PlatformBillingApiError)) {
    return t('platform.billing.errors.operationFailed')
  }
  if (error.status === 401) {
    auth.clear()
    return t('platform.billing.errors.sessionExpired')
  }
  if (error.response.error.code === 'FORBIDDEN') {
    return t('platform.billing.errors.forbidden')
  }
  if (error.response.error.code === 'SUBSCRIPTION_CONFLICT') {
    return t('platform.billing.errors.subscriptionConflict')
  }
  if (error.response.error.code === 'VERSION_CONFLICT') {
    return t('platform.billing.errors.versionConflict')
  }
  return t('platform.billing.errors.operationFailed')
}
</script>

<template>
  <main class="platform-shell">
    <PlatformAdminNav />

    <section class="platform-workspace">
      <header class="page-heading">
        <div>
          <span>{{ $t('platform.billing.page.kicker') }}</span>
          <h1>{{ $t('platform.billing.page.title') }}</h1>
        </div>
        <button class="secondary-button" type="button" :disabled="loading" @click="loadBilling">
          {{ $t('common.actions.refresh') }}
        </button>
      </header>

      <p v-if="errorText" class="error-banner" role="alert">{{ errorText }}</p>
      <p v-if="savedText" class="success-banner">{{ savedText }}</p>

      <div class="workspace-grid">
        <section class="table-panel">
          <table class="data-table">
            <thead>
              <tr>
                <th>{{ $t('platform.billing.table.columns.productLine') }}</th>
                <th>{{ $t('platform.billing.table.columns.billingCycle') }}</th>
                <th>{{ $t('platform.billing.table.columns.status') }}</th>
                <th>{{ $t('platform.billing.table.columns.periodStart') }}</th>
                <th>{{ $t('platform.billing.table.columns.periodEnd') }}</th>
                <th>{{ $t('platform.billing.table.columns.amount') }}</th>
                <th>{{ $t('platform.billing.table.columns.currency') }}</th>
                <th>{{ $t('platform.billing.table.columns.entitlement') }}</th>
                <th>{{ $t('platform.billing.table.columns.actions') }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="loading">
                <td colspan="9" class="table-empty">{{ $t('common.actions.loading') }}</td>
              </tr>
              <tr v-else-if="billingRows.length === 0">
                <td colspan="9" class="table-empty">{{ $t('platform.billing.table.empty') }}</td>
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
                <td>{{ row.subscription ? statusLabel(row.subscription) : $t('platform.billing.status.notOpened') }}</td>
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
                      :disabled="saving || billingDisabled"
                      @click="purchaseBillingRow(row)"
                    >
                      {{ $t('platform.billing.actions.open') }}
                    </button>
                    <button
                      v-if="row.subscription && canRenew(row.subscription)"
                      type="button"
                      class="text-action"
                      :disabled="saving || row.subscription.items.length === 0"
                      @click="renewBillingRow(row)"
                    >
                      {{ renewalActionLabel(row.subscription) }}
                    </button>
                    <button
                      v-if="row.subscription && canConvertLegacy(row.subscription)"
                      type="button"
                      class="text-action"
                      :disabled="saving || billingDisabled"
                      @click="convertLegacyBillingRow(row)"
                    >
                      {{ $t('platform.billing.actions.convert') }}
                    </button>
                    <button
                      v-if="row.subscription && canSuspend(row.subscription)"
                      type="button"
                      class="text-action"
                      :disabled="saving"
                      @click="suspendBillingRow(row)"
                    >
                      {{ $t('platform.billing.actions.suspend') }}
                    </button>
                    <button
                      v-if="row.subscription && canCancel(row.subscription)"
                      type="button"
                      class="text-action danger"
                      :disabled="saving"
                      @click="cancelBillingRow(row)"
                    >
                      {{ $t('platform.billing.actions.cancel') }}
                    </button>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </section>

        <form class="edit-panel" @submit.prevent="submitSelectedProductMutation">
          <h2>{{ $t('platform.billing.form.title') }}</h2>
          <label>
            <span>{{ $t('platform.billing.form.productLine') }}</span>
            <select v-model="form.appKey">
              <option v-for="productLine in productLines" :key="productLine.appKey" :value="productLine.appKey">
                {{ productLine.displayName }}
              </option>
            </select>
          </label>
          <label>
            <span>{{ $t('platform.billing.form.billingCycle') }}</span>
            <select v-model="form.billingCycle">
              <option value="monthly">{{ $t('platform.billing.cycles.monthly') }}</option>
              <option value="yearly">{{ $t('platform.billing.cycles.yearly') }}</option>
            </select>
          </label>
          <label>
            <span>{{ $t('platform.billing.form.duration') }}</span>
            <div class="duration-row">
              <input v-model.number="form.durationCount" type="number" min="1" :max="form.billingCycle === 'monthly' ? 120 : 10">
              <span>{{ durationUnitLabel }}</span>
            </div>
          </label>
          <div class="quote-summary">
            <span>{{ $t('platform.billing.form.unitPrice') }}</span>
            <strong>{{ formatAmount(selectedUnitPrice) }} {{ selectedCurrency }} / {{ durationUnitLabel }}</strong>
          </div>
          <div class="quote-summary">
            <span>{{ $t('platform.billing.form.storeCount') }}</span>
            <strong>{{ quoteStoreCount }}</strong>
          </div>
          <p v-if="billingDisabled" class="warning-text">{{ $t('platform.billing.form.noBillableStores') }}</p>
          <div class="quote-summary">
            <span>{{ $t('platform.billing.form.storeUnitAmount') }}</span>
            <strong>{{ formatAmount(storeUnitAmount) }} {{ selectedCurrency }}</strong>
          </div>
          <div class="quote-summary">
            <span>{{ $t('platform.billing.form.amount') }}</span>
            <strong>{{ formatAmount(calculatedAmount) }} {{ selectedCurrency }}</strong>
          </div>
          <label>
            <span>{{ $t('platform.billing.form.currency') }}</span>
            <input v-model.trim="form.currency" maxlength="3" :placeholder="selectedCurrency">
          </label>
          <label>
            <span>{{ $t('platform.billing.form.paymentNote') }}</span>
            <textarea v-model.trim="form.paymentNote" rows="4" />
          </label>
          <button class="primary-button" type="submit" :disabled="saving || loading || billingDisabled">
            {{ saving ? $t('common.actions.saving') : primaryActionLabel }}
          </button>

          <section class="store-items" :aria-label="$t('platform.billing.storeItems.title')">
            <h3>{{ $t('platform.billing.storeItems.title') }}</h3>
            <p v-if="storeItemRows.length === 0" class="muted-text">{{ $t('platform.billing.storeItems.empty') }}</p>
            <div v-else class="store-item-list">
              <button
                v-for="item in storeItemRows"
                :key="item.id"
                type="button"
                class="store-item-row"
                :class="{ selected: selectedStoreItem?.id === item.id }"
                @click="selectStoreItem(item)"
              >
                <div>
                  <strong>{{ storeItemName(item) }}</strong>
                  <small>{{ item.storeCode || '-' }} / {{ item.status }} / {{ formatDateTime(item.currentPeriodEnd) }}</small>
                </div>
                <span>{{ formatAmount(item.amount) }} {{ item.currency }}</span>
              </button>
            </div>
          </section>
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

.edit-panel h3 {
  margin: 0;
  color: #0f172a;
  font-size: 15px;
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

.warning-text {
  margin: 0;
  color: #92400e;
  font-size: 13px;
  font-weight: 700;
}

.store-items,
.store-item-list {
  display: grid;
  gap: 8px;
}

.store-item-row {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  width: 100%;
  padding: 10px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  color: inherit;
  background: #f8fafc;
  font: inherit;
  text-align: left;
  cursor: pointer;
}

.store-item-row.selected {
  border-color: #0f766e;
  background: #ecfdf5;
  box-shadow: inset 3px 0 0 #0f766e;
}

.store-item-row div {
  min-width: 0;
  display: grid;
  gap: 2px;
}

.store-item-row strong,
.store-item-row small {
  overflow-wrap: anywhere;
}

.store-item-row small {
  color: #64748b;
  font-size: 12px;
}

.store-item-row span {
  white-space: nowrap;
  color: #0f172a;
  font-weight: 800;
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
