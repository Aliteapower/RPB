<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'

import {
  PlatformBillingApiError,
  listProductLines,
  updateProductLine
} from '../api/platformProductLineBillingApi'
import PlatformAdminNav from '../components/platform/PlatformAdminNav.vue'
import type { PlatformProductLine, ProductLineStatus } from '../types/platformProductLineBilling'
import { useAuthSessionStore } from '../stores/authSession'

const auth = useAuthSessionStore()
const productLines = ref<PlatformProductLine[]>([])
const loading = ref(false)
const saving = ref(false)
const errorText = ref('')
const savedText = ref('')
const form = reactive({
  appKey: 'reservation_queue',
  displayName: '预约排队叫号产线',
  status: 'active' as ProductLineStatus,
  description: '预约、排队、叫号一体化产线',
  sortOrder: 10
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
  form.description = productLine.description ?? ''
  form.sortOrder = productLine.sortOrder
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
                <th>排序</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="loading">
                <td colspan="4" class="table-empty">加载中</td>
              </tr>
              <tr v-else-if="productLines.length === 0">
                <td colspan="4" class="table-empty">暂无产品线</td>
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
                <td>{{ productLine.sortOrder }}</td>
              </tr>
            </tbody>
          </table>
        </section>

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
            <span>排序</span>
            <input v-model.number="form.sortOrder" type="number" min="0">
          </label>
          <label>
            <span>说明</span>
            <textarea v-model.trim="form.description" rows="4" />
          </label>
          <p class="form-note">停用产品线会影响所有已购买该产品线的租户</p>
          <button class="primary-button" type="submit" :disabled="saving || loading">
            {{ saving ? '保存中' : '保存' }}
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

.form-note {
  margin: 0;
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
