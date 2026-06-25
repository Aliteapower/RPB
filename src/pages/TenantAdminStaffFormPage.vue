<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import {
  createStaff,
  getStaff,
  TenantAdminApiError,
  updateStaff,
  type TenantAdminStaffMutation
} from '../api/tenantAdminApi'
import TenantAdminNav from '../components/tenant-admin/TenantAdminNav.vue'
import { useAuthSessionStore } from '../stores/authSession'

const route = useRoute()
const router = useRouter()
const auth = useAuthSessionStore()
const loading = ref(false)
const saving = ref(false)
const errorText = ref('')

const mode = computed<'create' | 'edit'>(() => (route.name === 'tenant-admin-staff-create' ? 'create' : 'edit'))
const pageTitle = computed(() => (mode.value === 'create' ? '新增员工' : '编辑员工'))
const storeId = computed(() => String(route.params.storeId || ''))
const staffId = computed(() => String(route.params.staffId || ''))

const form = reactive({
  employeeNo: '',
  name: '',
  phone: '',
  email: '',
  status: 'active' as TenantAdminStaffMutation['status'],
  password: ''
})

onMounted(() => {
  if (mode.value === 'edit') {
    void loadStaff()
  }
})

async function loadStaff(): Promise<void> {
  loading.value = true
  errorText.value = ''
  try {
    const response = await getStaff(storeId.value, staffId.value)
    Object.assign(form, {
      employeeNo: response.staff.employeeNo,
      name: response.staff.name,
      phone: response.staff.phone || '',
      email: response.staff.email || '',
      status: response.staff.status,
      password: ''
    })
  } catch (error) {
    errorText.value = apiErrorText(error)
  } finally {
    loading.value = false
  }
}

async function submitStaff(): Promise<void> {
  if (saving.value) {
    return
  }

  saving.value = true
  errorText.value = ''
  try {
    const payload = toPayload()
    if (mode.value === 'create') {
      await createStaff(storeId.value, payload)
    } else {
      await updateStaff(storeId.value, staffId.value, payload)
    }
    await router.push({ name: 'tenant-admin-staff', params: { storeId: storeId.value } })
  } catch (error) {
    errorText.value = apiErrorText(error)
  } finally {
    saving.value = false
  }
}

function toPayload(): TenantAdminStaffMutation {
  return {
    employeeNo: mode.value === 'create' ? form.employeeNo.trim() : undefined,
    name: form.name.trim(),
    phone: optionalValue(form.phone),
    email: optionalValue(form.email),
    status: form.status,
    password: mode.value === 'create' ? form.password.trim() : optionalValue(form.password)
  }
}

function optionalValue(value: string): string | null {
  const normalized = value.trim()
  return normalized ? normalized : null
}

function apiErrorText(error: unknown): string {
  if (!(error instanceof TenantAdminApiError)) {
    return '操作失败'
  }
  if (error.status === 401) {
    auth.clear()
    return '登录已失效'
  }
  if (error.response.error.code === 'STAFF_CODE_CONFLICT') {
    return '员工号已存在'
  }
  if (error.response.error.code === 'STAFF_NOT_FOUND') {
    return '员工不存在'
  }
  if (error.response.error.code === 'REQUEST_INVALID') {
    return '请检查必填项和 6 位密码'
  }
  if (error.response.error.code === 'STORE_SCOPE_MISMATCH') {
    return '没有该店面的后台权限'
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
          <span>员工管理</span>
          <h1>{{ pageTitle }}</h1>
        </div>
        <button type="button" class="secondary-button" @click="router.push({ name: 'tenant-admin-staff', params: { storeId } })">
          返回列表
        </button>
      </header>

      <p v-if="errorText" class="error-banner" role="alert">{{ errorText }}</p>
      <p v-if="loading" class="loading-line">加载中</p>

      <form v-else class="form-panel" @submit.prevent="submitStaff">
        <label>
          <span>员工号</span>
          <input v-model.trim="form.employeeNo" :readonly="mode === 'edit'" required />
        </label>
        <label>
          <span>姓名</span>
          <input v-model.trim="form.name" required />
        </label>
        <label>
          <span>电话</span>
          <input v-model.trim="form.phone" inputmode="tel" />
        </label>
        <label>
          <span>电邮</span>
          <input v-model.trim="form.email" type="email" />
        </label>
        <label>
          <span>状态</span>
          <select v-model="form.status">
            <option value="active">启用</option>
            <option value="disabled">停用</option>
            <option value="locked">锁定</option>
          </select>
        </label>
        <label>
          <span>{{ mode === 'create' ? '初始密码' : '修改密码' }}</span>
          <input v-model="form.password" type="password" maxlength="6" :required="mode === 'create'" />
          <small>密码为 6 位数字或英文字母</small>
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

.secondary-button,
.primary-button {
  min-height: 38px;
  border-radius: 6px;
  padding: 0 14px;
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

.primary-button:disabled {
  opacity: 0.6;
  cursor: default;
}

.error-banner,
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

.loading-line {
  border: 1px solid #dbe3ea;
  color: #475569;
  background: #ffffff;
}

.form-panel {
  width: min(100%, 760px);
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
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

input,
select {
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

input[readonly] {
  color: #64748b;
  background: #f8fafc;
}

small {
  color: #64748b;
  font-weight: 500;
}

.form-actions {
  grid-column: 1 / -1;
  display: flex;
  justify-content: flex-end;
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

  .page-heading,
  .form-panel {
    display: grid;
    grid-template-columns: 1fr;
  }

  .secondary-button,
  .primary-button {
    width: 100%;
  }
}
</style>
