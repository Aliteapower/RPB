<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'

import {
  PlatformBillingApiError,
  listProductLines,
  updateProductLine,
  updateProductLinePrices
} from '../api/platformProductLineBillingApi'
import PlatformAdminNav from '../components/platform/PlatformAdminNav.vue'
import type { PlatformProductLine, ProductLinePriceStatus, ProductLineStatus } from '../types/platformProductLineBilling'
import { useAuthSessionStore } from '../stores/authSession'

const auth = useAuthSessionStore()
const productLines = ref<PlatformProductLine[]>([])
const loading = ref(false)
const saving = ref(false)
const priceSaving = ref(false)
const errorText = ref('')
const savedText = ref('')
const form = reactive({
  appKey: 'reservation_queue',
  displayName: '预约排队叫号产线',
  status: 'active' as ProductLineStatus,
  defaultEntryRoute: '/stores/:storeId/staff',
  description: '预约、排队、叫号一体化产线',
  sortOrder: 10
})
const priceForm = reactive({
  monthlyAmount: 0,
  yearlyAmount: 0,
  currency: 'SGD',
  monthlyStatus: 'active' as ProductLinePriceStatus,
  yearlyStatus: 'active' as ProductLinePriceStatus,
  monthlyVersion: 0,
  yearlyVersion: 0
})

onMounted(() => {
  void loadProductLines()
})

async function loadProductLines(): Promise<void> {
  loading.value = true
  errorText.value = ''
  try {
    const response = await listProductLines()
    productLines.value = response.productLines
    selectProductLine(response.productLines.find(item => item.appKey === 'reservation_queue') ?? response.productLines[0])
  } catch (error) {
    errorText.value = apiErrorText(error)
  } finally {
    loading.value = false
  }
}

function selectProductLine(productLine?: PlatformProductLine): void {
  if (!productLine) {
    return
  }
  form.appKey = productLine.appKey
  form.displayName = productLine.displayName
  form.status = productLine.status
  form.defaultEntryRoute = productLine.defaultEntryRoute
  form.description = productLine.description ?? ''
  form.sortOrder = productLine.sortOrder
  const monthly = productLine.prices.find(price => price.billingCycle === 'monthly')
  const yearly = productLine.prices.find(price => price.billingCycle === 'yearly')
  priceForm.monthlyAmount = monthly?.amount ?? 0
  priceForm.yearlyAmount = yearly?.amount ?? 0
  priceForm.currency = monthly?.currency ?? yearly?.currency ?? 'SGD'
  priceForm.monthlyStatus = monthly?.status ?? 'active'
  priceForm.yearlyStatus = yearly?.status ?? 'active'
  priceForm.monthlyVersion = monthly?.version ?? 0
  priceForm.yearlyVersion = yearly?.version ?? 0
}

async function saveProductLine(): Promise<void> {
  if (saving.value) {
    return
  }
  saving.value = true
  errorText.value = ''
  savedText.value = ''
  try {
    const response = await updateProductLine(form.appKey, {
      displayName: form.displayName,
      status: form.status,
      description: form.description,
      sortOrder: form.sortOrder
    })
    const index = productLines.value.findIndex(item => item.appKey === response.productLine.appKey)
    if (index >= 0) {
      productLines.value[index] = response.productLine
    }
    savedText.value = '已保存'
  } catch (error) {
    errorText.value = apiErrorText(error)
  } finally {
    saving.value = false
  }
}

async function saveProductLinePrices(): Promise<void> {
  if (priceSaving.value) {
    return
  }
  priceSaving.value = true
  errorText.value = ''
  savedText.value = ''
  try {
    const currency = priceForm.currency.trim().toUpperCase() || 'SGD'
    const response = await updateProductLinePrices(form.appKey, {
      prices: [
        {
          billingCycle: 'monthly',
          amount: Number(priceForm.monthlyAmount) || 0,
          currency,
          status: priceForm.monthlyStatus,
          version: priceForm.monthlyVersion
        },
        {
          billingCycle: 'yearly',
          amount: Number(priceForm.yearlyAmount) || 0,
          currency,
          status: priceForm.yearlyStatus,
          version: priceForm.yearlyVersion
        }
      ]
    })
    const index = productLines.value.findIndex(item => item.appKey === response.productLine.appKey)
    if (index >= 0) {
      productLines.value[index] = response.productLine
    }
    selectProductLine(response.productLine)
    savedText.value = '定价已保存'
  } catch (error) {
    errorText.value = apiErrorText(error)
  } finally {
    priceSaving.value = false
  }
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
    return '没有产品线管理权限'
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
          <span>基础设置</span>
          <h1>产品线</h1>
        </div>
        <button class="secondary-button" type="button" :disabled="loading" @click="loadProductLines">
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
                <th>App Key</th>
                <th>状态</th>
                <th>默认入口</th>
                <th>排序</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="loading">
                <td colspan="6" class="table-empty">加载中</td>
              </tr>
              <tr v-else-if="productLines.length === 0">
                <td colspan="6" class="table-empty">暂无产品线</td>
              </tr>
              <tr
                v-for="productLine in productLines"
                v-else
                :key="productLine.appKey"
                :class="{ selected: productLine.appKey === form.appKey }"
                @click="selectProductLine(productLine)"
              >
                <td>{{ productLine.displayName }}</td>
                <td>{{ productLine.appKey }}</td>
                <td>{{ productLine.status === 'active' ? '启用' : '停用' }}</td>
                <td>{{ productLine.defaultEntryRoute }}</td>
                <td>{{ productLine.sortOrder }}</td>
                <td>
                  <button class="text-action" type="button" @click.stop="selectProductLine(productLine)">编辑</button>
                </td>
              </tr>
            </tbody>
          </table>
        </section>

        <div class="side-panels">
          <form class="edit-panel" @submit.prevent="saveProductLine">
            <h2>产品线设置</h2>
            <label>
              <span>App Key</span>
              <input v-model="form.appKey" disabled>
            </label>
            <label>
              <span>展示名称</span>
              <input v-model.trim="form.displayName" required>
            </label>
            <label>
              <span>状态</span>
              <select v-model="form.status">
                <option value="active">启用</option>
                <option value="disabled">停用</option>
              </select>
            </label>
            <label>
              <span>默认入口</span>
              <input v-model="form.defaultEntryRoute" disabled>
            </label>
            <label>
              <span>排序</span>
              <input v-model.number="form.sortOrder" type="number" min="0">
            </label>
            <label>
              <span>说明</span>
              <textarea v-model.trim="form.description" rows="4" />
            </label>
            <p class="form-note">停用产品线会影响所有已购买该产品线的租户</p>
            <button class="primary-button" type="submit" :disabled="saving || loading">
              {{ saving ? '保存中' : '保存产品线' }}
            </button>
          </form>

          <form class="edit-panel" @submit.prevent="saveProductLinePrices">
            <h2>定价</h2>
            <label>
              <span>月付价格</span>
              <input v-model.number="priceForm.monthlyAmount" type="number" min="0" step="0.01">
            </label>
            <label>
              <span>年付价格</span>
              <input v-model.number="priceForm.yearlyAmount" type="number" min="0" step="0.01">
            </label>
            <label>
              <span>币种</span>
              <input v-model.trim="priceForm.currency" maxlength="3">
            </label>
            <div class="price-status-grid">
              <label>
                <span>月付状态</span>
                <select v-model="priceForm.monthlyStatus">
                  <option value="active">启用</option>
                  <option value="disabled">停用</option>
                </select>
              </label>
              <label>
                <span>年付状态</span>
                <select v-model="priceForm.yearlyStatus">
                  <option value="active">启用</option>
                  <option value="disabled">停用</option>
                </select>
              </label>
            </div>
            <button class="primary-button" type="submit" :disabled="priceSaving || loading">
              {{ priceSaving ? '保存中' : '保存定价' }}
            </button>
          </form>
        </div>
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
.workspace-grid,
.side-panels {
  display: grid;
  gap: 16px;
}

.page-heading {
  grid-template-columns: 1fr auto;
  align-items: center;
  margin-bottom: 16px;
}

.page-heading span,
.edit-panel label span,
.form-note {
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
  grid-template-columns: minmax(0, 1.5fr) minmax(320px, 0.8fr);
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
  min-width: 720px;
  border-collapse: collapse;
}

.data-table th,
.data-table td {
  padding: 12px 14px;
  border-bottom: 1px solid #edf2f7;
  text-align: left;
}

.data-table th {
  color: #64748b;
  background: #f8fafc;
  font-size: 13px;
}

.data-table tr.selected {
  background: #ecfdf5;
}

.table-empty {
  color: #64748b;
  text-align: center;
}

.edit-panel {
  display: grid;
  gap: 14px;
  padding: 16px;
}

.side-panels {
  align-items: start;
}

.edit-panel label {
  display: grid;
  gap: 6px;
}

.price-status-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
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

.form-note {
  margin: 0;
}

.primary-button,
.secondary-button,
.text-action {
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

.text-action {
  border: 0;
  color: #0f766e;
  background: transparent;
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
