<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import {
  PlatformApiError,
  createTenant,
  getTenant,
  updateTenant,
  type PlatformTenantMutation
} from '../api/platformApi'
import PlatformAdminNav from '../components/platform/PlatformAdminNav.vue'
import PlatformTenantForm from '../components/platform/PlatformTenantForm.vue'
import type { PlatformTenantFormModel, TenantStatusOption } from '../components/platform/platformTenantUi'
import { useAuthSessionStore } from '../stores/authSession'

const route = useRoute()
const router = useRouter()
const auth = useAuthSessionStore()

const loading = ref(false)
const saving = ref(false)
const errorText = ref('')

const mode = computed<'create' | 'edit'>(() => (route.name === 'platform-tenant-create' ? 'create' : 'edit'))
const tenantId = computed(() => String(route.params.tenantId || ''))
const pageTitle = computed(() => (mode.value === 'create' ? '新增租户' : '编辑租户'))

const statusOptions: TenantStatusOption[] = [
  { value: 'created', label: '已创建' },
  { value: 'active', label: '启用' },
  { value: 'suspended', label: '停用' },
  { value: 'closed', label: '关闭' }
]

const form = reactive<PlatformTenantFormModel>({
  id: '',
  tenantCode: '',
  displayName: '',
  status: 'active',
  defaultLocale: 'zh-CN',
  contactPhone: '',
  address: '',
  principalName: '',
  initialPassword: '',
  password: ''
})

onMounted(() => {
  if (mode.value === 'edit') {
    void loadTenant()
  }
})

async function loadTenant(): Promise<void> {
  loading.value = true
  errorText.value = ''
  try {
    const response = await getTenant(tenantId.value)
    Object.assign(form, {
      id: response.tenant.id,
      tenantCode: response.tenant.tenantCode,
      displayName: response.tenant.displayName,
      status: response.tenant.status,
      defaultLocale: response.tenant.defaultLocale || 'zh-CN',
      contactPhone: response.tenant.contactPhone || '',
      address: response.tenant.address || '',
      principalName: response.tenant.principalName || '',
      initialPassword: '',
      password: ''
    } satisfies PlatformTenantFormModel)
  } catch (error) {
    errorText.value = apiErrorText(error)
  } finally {
    loading.value = false
  }
}

async function submitTenantForm(submittedForm: PlatformTenantFormModel): Promise<void> {
  if (saving.value) {
    return
  }

  saving.value = true
  errorText.value = ''
  try {
    const payload = toPayload(submittedForm)
    if (mode.value === 'create') {
      await createTenant(payload)
    } else {
      await updateTenant(submittedForm.id, payload)
    }
    await router.push({ name: 'platform-tenants' })
  } catch (error) {
    errorText.value = apiErrorText(error)
  } finally {
    saving.value = false
  }
}

function toPayload(submittedForm: PlatformTenantFormModel): PlatformTenantMutation {
  return {
    tenantCode: mode.value === 'create' ? submittedForm.tenantCode.trim() : undefined,
    displayName: submittedForm.displayName.trim(),
    status: submittedForm.status,
    defaultLocale: optionalValue(submittedForm.defaultLocale),
    contactPhone: optionalValue(submittedForm.contactPhone),
    address: optionalValue(submittedForm.address),
    principalName: optionalValue(submittedForm.principalName),
    initialPassword: mode.value === 'create' ? submittedForm.initialPassword.trim() : null,
    password: mode.value === 'edit' ? optionalValue(submittedForm.password) : null
  }
}

function optionalValue(value: string): string | null {
  const normalized = value.trim()
  return normalized ? normalized : null
}

function apiErrorText(error: unknown): string {
  if (!(error instanceof PlatformApiError)) {
    return '操作失败'
  }
  if (error.status === 401) {
    auth.clear()
    return '登录已失效'
  }
  if (error.response.error.code === 'FORBIDDEN') {
    return '没有平台后台权限'
  }
  if (error.response.error.code === 'TENANT_CODE_CONFLICT') {
    return '租户代码或管理员账号已存在'
  }
  if (error.response.error.code === 'TENANT_NOT_FOUND') {
    return '租户不存在'
  }
  if (error.response.error.code === 'REQUEST_INVALID') {
    return '请检查必填项和 6 位密码'
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
          <span>平台</span>
          <h1>{{ pageTitle }}</h1>
        </div>
        <button type="button" class="secondary-button" @click="router.push({ name: 'platform-tenants' })">
          返回列表
        </button>
      </header>

      <p v-if="errorText" class="error-banner" role="alert">{{ errorText }}</p>
      <p v-if="loading" class="loading-line">加载中</p>

      <PlatformTenantForm
        v-else
        :mode="mode"
        :form="form"
        :status-options="statusOptions"
        :saving="saving"
        @submit="submitTenantForm"
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

.secondary-button {
  min-height: 38px;
  border: 1px solid #cbd5e1;
  border-radius: 6px;
  padding: 0 14px;
  color: #0f172a;
  background: #ffffff;
  font: inherit;
  font-weight: 800;
  cursor: pointer;
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

@media (max-width: 980px) {
  .platform-shell {
    grid-template-columns: 1fr;
  }

}

@media (max-width: 620px) {
  .platform-workspace {
    padding: 14px;
  }

  .page-heading {
    grid-template-columns: 1fr;
  }

  .page-heading {
    display: grid;
  }
}
</style>
