<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'

import {
  getSettings,
  TenantAdminApiError,
  updateSettings,
  type TenantAdminSettings
} from '../api/tenantAdminApi'
import TenantAdminNav from '../components/tenant-admin/TenantAdminNav.vue'
import { useAuthSessionStore } from '../stores/authSession'

const route = useRoute()
const auth = useAuthSessionStore()
const loading = ref(false)
const saving = ref(false)
const errorText = ref('')
const savedText = ref('')

const storeId = computed(() => String(route.params.storeId || ''))

const form = reactive<TenantAdminSettings>({
  storeName: '',
  timezone: 'Asia/Shanghai',
  locale: 'zh-CN',
  dateFormat: 'DD-MM-YYYY',
  timeFormat: 'HH:mm',
  currency: 'CNY',
  reservationHoldMinutes: 15,
  queueCallHoldMinutes: 3,
  expectedDiningMinutes: 90
})

onMounted(() => {
  void loadSettings()
})

async function loadSettings(): Promise<void> {
  loading.value = true
  errorText.value = ''
  savedText.value = ''
  try {
    const response = await getSettings(storeId.value)
    Object.assign(form, response.settings)
  } catch (error) {
    errorText.value = apiErrorText(error)
  } finally {
    loading.value = false
  }
}

async function submitSettings(): Promise<void> {
  if (saving.value) {
    return
  }

  saving.value = true
  errorText.value = ''
  savedText.value = ''
  try {
    const response = await updateSettings(storeId.value, {
      storeName: form.storeName.trim(),
      timezone: form.timezone.trim(),
      locale: form.locale.trim(),
      dateFormat: form.dateFormat.trim(),
      timeFormat: form.timeFormat.trim(),
      currency: form.currency.trim(),
      reservationHoldMinutes: Number(form.reservationHoldMinutes),
      queueCallHoldMinutes: Number(form.queueCallHoldMinutes),
      expectedDiningMinutes: Number(form.expectedDiningMinutes)
    })
    Object.assign(form, response.settings)
    savedText.value = '已保存'
  } catch (error) {
    errorText.value = apiErrorText(error)
  } finally {
    saving.value = false
  }
}

function apiErrorText(error: unknown): string {
  if (!(error instanceof TenantAdminApiError)) {
    return '操作失败'
  }
  if (error.status === 401) {
    auth.clear()
    return '登录已失效'
  }
  if (error.response.error.code === 'REQUEST_INVALID') {
    return '请检查店面名称、时间格式和分钟数'
  }
  if (error.response.error.code === 'STORE_SCOPE_MISMATCH') {
    return '没有该店面的后台权限'
  }
  if (error.response.error.code === 'FORBIDDEN') {
    return '没有租户后台权限'
  }
  return '操作失败'
}
</script>

<template>
  <main class="tenant-shell">
    <TenantAdminNav />

    <section class="tenant-workspace">
      <header class="page-heading">
        <div>
          <span>租户</span>
          <h1>基础设置</h1>
        </div>
      </header>

      <p v-if="errorText" class="error-banner" role="alert">{{ errorText }}</p>
      <p v-if="savedText" class="success-banner" role="status">{{ savedText }}</p>
      <p v-if="loading" class="loading-line">加载中</p>

      <form v-else class="form-panel" @submit.prevent="submitSettings">
        <label>
          <span>店面名称</span>
          <input v-model.trim="form.storeName" required />
        </label>
        <label>
          <span>时区</span>
          <input v-model.trim="form.timezone" required />
        </label>
        <label>
          <span>语言</span>
          <input v-model.trim="form.locale" required />
        </label>
        <label>
          <span>币种</span>
          <input v-model.trim="form.currency" required />
        </label>
        <label>
          <span>日期格式</span>
          <input v-model.trim="form.dateFormat" placeholder="DD-MM-YYYY" required />
        </label>
        <label>
          <span>时间格式</span>
          <input v-model.trim="form.timeFormat" required />
        </label>
        <label>
          <span>预约保留分钟</span>
          <input v-model.number="form.reservationHoldMinutes" type="number" min="1" required />
        </label>
        <label>
          <span>叫号保留分钟</span>
          <input v-model.number="form.queueCallHoldMinutes" type="number" min="1" required />
        </label>
        <label>
          <span>预计用餐分钟</span>
          <input v-model.number="form.expectedDiningMinutes" type="number" min="1" required />
        </label>
        <div class="form-actions">
          <button class="primary-button" type="submit" :disabled="saving">
            {{ saving ? '保存中' : '保存' }}
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

.error-banner,
.success-banner,
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

.success-banner {
  border: 1px solid #bbf7d0;
  color: #166534;
  background: #f0fdf4;
}

.loading-line {
  border: 1px solid #dbe3ea;
  color: #475569;
  background: #ffffff;
}

.form-panel {
  width: min(100%, 860px);
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
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

.form-actions {
  grid-column: 1 / -1;
  display: flex;
  justify-content: flex-end;
}

.primary-button {
  min-height: 38px;
  border: 0;
  border-radius: 6px;
  padding: 0 14px;
  color: #ffffff;
  background: #0f766e;
  font: inherit;
  font-weight: 800;
  cursor: pointer;
}

.primary-button:disabled {
  opacity: 0.6;
  cursor: default;
}

@media (max-width: 980px) {
  .tenant-shell,
  .form-panel {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 700px) {
  .tenant-workspace {
    padding: 14px;
  }

  .primary-button {
    width: 100%;
  }
}
</style>
