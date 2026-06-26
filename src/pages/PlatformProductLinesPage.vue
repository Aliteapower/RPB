<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'

import {
  PlatformBillingApiError,
  createProductLine,
  listProductLines,
  updateProductLine,
  updateProductLinePrices
} from '../api/platformProductLineBillingApi'
import PlatformAdminNav from '../components/platform/PlatformAdminNav.vue'
import PlatformProductLineDrawer from '../components/platform/product-line/PlatformProductLineDrawer.vue'
import PlatformProductLineList from '../components/platform/product-line/PlatformProductLineList.vue'
import {
  defaultEntryRouteOptions,
  isProductLineAppKeyValid,
  normalizeProductLineAppKey
} from '../components/platform/product-line/productLineCatalog'
import { useAuthSessionStore } from '../stores/authSession'
import type { PlatformProductLine, ProductLinePriceStatus, ProductLineStatus } from '../types/platformProductLineBilling'

type ProductLineEditorMode = 'create' | 'edit'

const auth = useAuthSessionStore()
const productLines = ref<PlatformProductLine[]>([])
const loading = ref(false)
const saving = ref(false)
const priceSaving = ref(false)
const errorText = ref('')
const savedText = ref('')
const editDrawerOpen = ref(false)
const drawerMode = ref<ProductLineEditorMode>('edit')
const total = ref(0)
const page = ref(0)
const size = ref(10)
const filters = reactive({
  keyword: '',
  status: '' as ProductLineStatus | ''
})
const form = reactive({
  appKey: 'reservation_queue',
  productCode: 'reservation_queue',
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

const computedAppKey = computed(() => normalizeProductLineAppKey(form.productCode))
const appKeyValid = computed(() => drawerMode.value === 'edit' || isProductLineAppKeyValid(computedAppKey.value))

onMounted(() => {
  void loadProductLines()
})

async function loadProductLines(nextPage = page.value): Promise<void> {
  loading.value = true
  errorText.value = ''
  try {
    const response = await listProductLines({
      keyword: filters.keyword,
      status: filters.status,
      page: nextPage,
      size: size.value
    })
    const items = response.items ?? response.productLines
    productLines.value = items
    total.value = response.total ?? items.length
    page.value = response.page ?? nextPage
    size.value = response.size ?? size.value

    const selected = items.find(item => item.appKey === form.appKey)
      ?? items.find(item => item.appKey === 'reservation_queue')
      ?? items[0]
    if (selected && drawerMode.value === 'edit') {
      selectProductLine(selected)
    }
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
  form.productCode = productLine.appKey
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

function openProductLineDrawer(productLine: PlatformProductLine): void {
  drawerMode.value = 'edit'
  selectProductLine(productLine)
  editDrawerOpen.value = true
}

function openCreateProductLineDrawer(): void {
  drawerMode.value = 'create'
  form.appKey = ''
  form.productCode = ''
  form.displayName = ''
  form.status = 'disabled'
  form.defaultEntryRoute = ''
  form.description = ''
  form.sortOrder = nextSortOrder()
  resetPriceForm()
  editDrawerOpen.value = true
}

function closeProductLineDrawer(): void {
  editDrawerOpen.value = false
}

async function saveProductLine(): Promise<void> {
  if (saving.value) {
    return
  }
  saving.value = true
  errorText.value = ''
  savedText.value = ''
  try {
    const wasCreate = drawerMode.value === 'create'
    const response = wasCreate
      ? await createProductLine({
        appKey: computedAppKey.value,
        displayName: form.displayName,
        status: form.status,
        defaultEntryRoute: form.defaultEntryRoute,
        description: form.description,
        sortOrder: form.sortOrder
      })
      : await updateProductLine(form.appKey, {
        displayName: form.displayName,
        status: form.status,
        defaultEntryRoute: form.defaultEntryRoute,
        description: form.description,
        sortOrder: form.sortOrder
      })
    upsertProductLine(response.productLine)
    selectProductLine(response.productLine)
    drawerMode.value = 'edit'
    savedText.value = wasCreate ? '产品线已创建' : '已保存'
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
    upsertProductLine(response.productLine)
    selectProductLine(response.productLine)
    savedText.value = '定价已保存'
  } catch (error) {
    errorText.value = apiErrorText(error)
  } finally {
    priceSaving.value = false
  }
}

function searchProductLines(query: { keyword: string; status: ProductLineStatus | '' }): void {
  filters.keyword = query.keyword
  filters.status = query.status
  void loadProductLines(0)
}

function resetProductLineFilters(): void {
  filters.keyword = ''
  filters.status = ''
  void loadProductLines(0)
}

function upsertProductLine(productLine: PlatformProductLine): void {
  const index = productLines.value.findIndex(item => item.appKey === productLine.appKey)
  if (index >= 0) {
    productLines.value[index] = productLine
    return
  }
  productLines.value = [productLine, ...productLines.value]
  total.value += 1
}

function resetPriceForm(): void {
  priceForm.monthlyAmount = 0
  priceForm.yearlyAmount = 0
  priceForm.currency = 'SGD'
  priceForm.monthlyStatus = 'active'
  priceForm.yearlyStatus = 'active'
  priceForm.monthlyVersion = 0
  priceForm.yearlyVersion = 0
}

function nextSortOrder(): number {
  const maxSortOrder = productLines.value.reduce((max, productLine) => Math.max(max, productLine.sortOrder), 0)
  return maxSortOrder + 10
}

function apiErrorText(error: unknown): string {
  if (!(error instanceof PlatformBillingApiError)) {
    return '操作失败，请稍后重试'
  }
  if (error.status === 401) {
    auth.clear()
    return '登录已失效，请重新登录'
  }
  if (error.response.error.code === 'FORBIDDEN') {
    return '没有产品线管理权限'
  }
  if (error.response.error.code === 'PRODUCT_LINE_CONFLICT') {
    return '产品线 App Key 已存在，请换一个产品线代码'
  }
  if (error.response.error.code === 'REQUEST_INVALID') {
    return '产品线信息不完整，请检查名称、代码、状态和默认入口'
  }
  return '操作失败，请稍后重试'
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
        <button class="secondary-button" type="button" :disabled="loading" @click="loadProductLines()">
          刷新
        </button>
      </header>

      <p v-if="errorText" class="error-banner" role="alert">{{ errorText }}</p>
      <p v-if="savedText" class="success-banner">{{ savedText }}</p>

      <PlatformProductLineList
        :keyword="filters.keyword"
        :loading="loading"
        :page="page"
        :product-lines="productLines"
        :selected-app-key="form.appKey"
        :size="size"
        :status="filters.status"
        :total="total"
        @create="openCreateProductLineDrawer"
        @edit="openProductLineDrawer"
        @page="loadProductLines"
        @reset="resetProductLineFilters"
        @search="searchProductLines"
      />

      <PlatformProductLineDrawer
        :app-key-valid="appKeyValid"
        :computed-app-key="computedAppKey"
        :entry-route-options="defaultEntryRouteOptions"
        :form="form"
        :loading="loading"
        :mode="drawerMode"
        :open="editDrawerOpen"
        :price-form="priceForm"
        :price-saving="priceSaving"
        :saving="saving"
        @close="closeProductLineDrawer"
        @save="saveProductLine"
        @save-prices="saveProductLinePrices"
      />
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

.page-heading {
  display: grid;
  gap: 16px;
  grid-template-columns: 1fr auto;
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

.secondary-button {
  min-height: 36px;
  border: 1px solid #cbd5e1;
  border-radius: 6px;
  color: #334155;
  background: #ffffff;
  padding: 0 14px;
  font: inherit;
  font-weight: 800;
  cursor: pointer;
}

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
  .platform-shell {
    grid-template-columns: 1fr;
  }
}
</style>
