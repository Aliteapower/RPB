<script setup lang="ts">
import type { ProductLinePriceStatus, ProductLineStatus } from '../../../types/platformProductLineBilling'
import PlatformProductLinePriceForm from './PlatformProductLinePriceForm.vue'

type ProductLineEditorMode = 'create' | 'edit'

interface ProductLineForm {
  appKey: string
  productCode: string
  displayName: string
  status: ProductLineStatus
  defaultEntryRoute: string
  description: string
  sortOrder: number
}

interface ProductLinePriceFormState {
  monthlyAmount: number
  yearlyAmount: number
  currency: string
  monthlyStatus: ProductLinePriceStatus
  yearlyStatus: ProductLinePriceStatus
}

defineProps<{
  appKeyValid: boolean
  computedAppKey: string
  entryRouteOptions: ReadonlyArray<{
    value: string
    label: string
    description: string
  }>
  form: ProductLineForm
  loading: boolean
  mode: ProductLineEditorMode
  open: boolean
  priceForm: ProductLinePriceFormState
  priceSaving: boolean
  saving: boolean
}>()

const emit = defineEmits<{
  close: []
  save: []
  savePrices: []
}>()
</script>

<template>
  <Teleport to="body">
    <div
      v-if="open"
      class="drawer-backdrop"
      aria-label="关闭产品线编辑"
      @click.self="emit('close')"
    >
      <aside class="product-line-drawer" role="dialog" aria-modal="true" aria-labelledby="product-line-drawer-title">
        <header class="drawer-header">
          <div>
            <span>{{ mode === 'create' ? '新增产品线' : '产品线' }}</span>
            <h2 id="product-line-drawer-title">{{ mode === 'create' ? '登记产品线' : form.displayName }}</h2>
          </div>
          <button class="drawer-close-button" type="button" aria-label="关闭" @click="emit('close')">关闭</button>
        </header>

        <div class="drawer-body">
          <form class="edit-panel" @submit.prevent="emit('save')">
            <h3>产品线设置</h3>

            <label v-if="mode === 'create'">
              <span>产品线代码</span>
              <input v-model.trim="form.productCode" placeholder="例如 crm suite" required>
            </label>

            <label>
              <span>App Key</span>
              <input :value="mode === 'create' ? computedAppKey : form.appKey" disabled>
            </label>
            <p v-if="mode === 'create' && !appKeyValid" class="field-error">
              请输入英文开头的产品线代码，系统会生成 snake_case App Key。
            </p>

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
              <select v-model="form.defaultEntryRoute">
                <option
                  v-for="option in entryRouteOptions"
                  :key="option.value"
                  :value="option.value"
                >
                  {{ option.label }}
                </option>
              </select>
            </label>
            <p class="form-note">
              {{ entryRouteOptions.find(option => option.value === form.defaultEntryRoute)?.description }}
            </p>
            <label>
              <span>排序</span>
              <input v-model.number="form.sortOrder" type="number" min="0">
            </label>
            <label>
              <span>说明</span>
              <textarea v-model.trim="form.description" rows="4" />
            </label>
            <p class="form-note">
              {{ mode === 'create' ? '新增产品线默认建议先停用，后续业务开发完成后再启用给租户选择。' : '停用产品线会影响所有已购买该产品线的租户。' }}
            </p>
            <button class="primary-button" type="submit" :disabled="saving || loading || (mode === 'create' && !appKeyValid)">
              {{ saving ? '保存中' : (mode === 'create' ? '创建产品线' : '保存产品线') }}
            </button>
          </form>

          <PlatformProductLinePriceForm
            v-if="mode === 'edit'"
            :disabled="loading"
            :form="priceForm"
            :saving="priceSaving"
            @save="emit('savePrices')"
          />
        </div>
      </aside>
    </div>
  </Teleport>
</template>

<style scoped>
.drawer-backdrop {
  background: rgba(15, 23, 42, 0.28);
  inset: 0;
  position: fixed;
  z-index: 60;
}

.product-line-drawer {
  background: #ffffff;
  box-shadow: -16px 0 34px rgba(15, 23, 42, 0.18);
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  height: 100%;
  margin-left: auto;
  max-width: 100%;
  width: min(560px, 100%);
}

.drawer-header {
  align-items: center;
  border-bottom: 1px solid #e2e8f0;
  display: flex;
  gap: 12px;
  justify-content: space-between;
  padding: 18px 20px;
}

.drawer-header > div {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.drawer-header span,
.edit-panel label span,
.form-note {
  color: #64748b;
  font-size: 13px;
  font-weight: 700;
}

.drawer-header h2,
.edit-panel h3 {
  margin: 0;
  color: #0f172a;
}

.drawer-header h2 {
  font-size: 20px;
}

.edit-panel h3 {
  font-size: 18px;
}

.drawer-body {
  display: grid;
  gap: 16px;
  overflow-y: auto;
  padding: 18px 20px 24px;
}

.edit-panel {
  display: grid;
  gap: 14px;
  border: 1px solid #dbe3ea;
  border-radius: 8px;
  background: #ffffff;
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

.field-error {
  border: 1px solid #fecaca;
  border-radius: 6px;
  background: #fff1f2;
  color: #991b1b;
  margin: 0;
  padding: 8px 10px;
}

.form-note {
  margin: 0;
}

.primary-button,
.drawer-close-button {
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

.drawer-close-button {
  border: 1px solid #cbd5e1;
  color: #334155;
  background: #f8fafc;
  flex: 0 0 auto;
}

.primary-button:disabled {
  opacity: 0.55;
  cursor: default;
}
</style>
