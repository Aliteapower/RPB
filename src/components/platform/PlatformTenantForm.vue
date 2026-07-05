<script setup lang="ts">
import { computed, reactive, watch } from 'vue'

import PasswordInput from '../common/PasswordInput.vue'
import type { PlatformTenantFormModel, TenantStatusOption } from './platformTenantUi'

const props = defineProps<{
  mode: 'create' | 'edit'
  form: PlatformTenantFormModel
  statusOptions: TenantStatusOption[]
  saving: boolean
}>()

const emit = defineEmits<{
  submit: [form: PlatformTenantFormModel]
  uploadLogo: [file: File]
  clearLogo: []
}>()

const localForm = reactive<PlatformTenantFormModel>({
  id: '',
  tenantCode: '',
  displayName: '',
  status: 'active',
  defaultLocale: 'zh-CN',
  contactPhone: '',
  address: '',
  principalName: '',
  logoMediaUrl: '',
  logoFile: null,
  initialPassword: '',
  password: ''
})

const passwordLabel = computed(() => (props.mode === 'create' ? '初始密码' : '修改密码'))
const passwordPlaceholder = computed(() => (props.mode === 'create' ? '6 位数字或英文字母' : '留空则不修改'))

watch(
  () => props.form,
  value => {
    Object.assign(localForm, value)
  },
  { deep: true, immediate: true }
)

function submitForm(): void {
  emit('submit', { ...localForm })
}

function handleLogoFileChange(event: Event): void {
  const input = event.target as HTMLInputElement | null
  localForm.logoFile = input?.files?.[0] ?? null
}

function submitLogo(): void {
  if (localForm.logoFile) {
    emit('uploadLogo', localForm.logoFile)
  }
}

function clearLogo(): void {
  localForm.logoFile = null
  emit('clearLogo')
}
</script>

<template>
  <form class="tenant-form" @submit.prevent="submitForm">
    <section class="form-section" aria-label="基础信息">
      <label>
        <span>租户代码</span>
        <input
          v-model.trim="localForm.tenantCode"
          required
          maxlength="64"
          autocomplete="off"
          :readonly="mode === 'edit'"
          :class="{ readonly: mode === 'edit' }"
        />
      </label>

      <label>
        <span>名称</span>
        <input v-model.trim="localForm.displayName" required maxlength="120" autocomplete="off" />
      </label>

      <label>
        <span>状态</span>
        <select v-model="localForm.status">
          <option v-for="option in statusOptions" :key="option.value" :value="option.value">
            {{ option.label }}
          </option>
        </select>
      </label>

      <label>
        <span>默认语言</span>
        <input v-model.trim="localForm.defaultLocale" maxlength="20" autocomplete="off" />
      </label>
    </section>

    <section class="form-section" aria-label="联系方式">
      <label>
        <span>负责人</span>
        <input v-model.trim="localForm.principalName" maxlength="80" autocomplete="off" />
      </label>

      <label>
        <span>电话</span>
        <input v-model.trim="localForm.contactPhone" maxlength="40" autocomplete="off" />
      </label>

      <label class="span-2">
        <span>地址</span>
        <input v-model.trim="localForm.address" maxlength="240" autocomplete="off" />
      </label>
    </section>

    <section class="form-section" aria-label="租户管理员账号">
      <label>
        <span>{{ passwordLabel }}</span>
        <PasswordInput
          v-if="mode === 'create'"
          v-model.trim="localForm.initialPassword"
          required
          maxlength="6"
          :placeholder="passwordPlaceholder"
          autocomplete="new-password"
        />
        <PasswordInput
          v-else
          v-model.trim="localForm.password"
          maxlength="6"
          :placeholder="passwordPlaceholder"
          autocomplete="new-password"
        />
      </label>
    </section>

    <section v-if="mode === 'edit'" class="form-section form-section--logo" aria-label="租户 LOGO">
      <div class="logo-preview" :class="{ empty: !localForm.logoMediaUrl }">
        <img v-if="localForm.logoMediaUrl" :src="localForm.logoMediaUrl" alt="租户 LOGO" />
        <span v-else>LOGO</span>
      </div>

      <div class="logo-fields">
        <label>
          <span>租户 LOGO</span>
          <input type="file" accept="image/jpeg,image/png,image/webp" aria-label="选择图片" @change="handleLogoFileChange" />
        </label>
        <div class="logo-actions">
          <button class="secondary-button" type="button" :disabled="!localForm.logoFile || saving" @click="submitLogo">
            上传 LOGO
          </button>
          <button class="secondary-button" type="button" :disabled="!localForm.logoMediaUrl || saving" @click="clearLogo">
            清空 LOGO
          </button>
        </div>
      </div>
    </section>

    <div class="form-actions">
      <button class="primary-button" type="submit" :disabled="saving">
        {{ saving ? '保存中' : '保存' }}
      </button>
    </div>
  </form>
</template>

<style scoped>
.tenant-form {
  display: grid;
  gap: 18px;
}

.form-section {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
  padding: 16px;
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

.span-2 {
  grid-column: span 2;
}

.form-section--logo {
  grid-template-columns: auto minmax(0, 1fr);
  align-items: center;
}

.logo-preview {
  width: 78px;
  height: 78px;
  display: grid;
  place-items: center;
  overflow: hidden;
  border: 1px solid #cbd5e1;
  border-radius: 8px;
  background: #f8fafc;
}

.logo-preview img {
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.logo-preview span {
  color: #64748b;
  font-size: 13px;
  font-weight: 800;
}

.logo-fields {
  display: grid;
  gap: 10px;
}

.logo-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

input,
select {
  min-height: 40px;
  border: 1px solid #cbd5e1;
  border-radius: 6px;
  padding: 8px 10px;
  color: #0f172a;
  background: #ffffff;
  font: inherit;
}

input.readonly {
  color: #475569;
  background: #f8fafc;
  cursor: default;
}

.form-actions {
  display: flex;
  justify-content: flex-end;
}

.primary-button {
  min-height: 38px;
  border: 0;
  border-radius: 6px;
  padding: 0 18px;
  color: #ffffff;
  background: #0f766e;
  font: inherit;
  font-weight: 800;
  cursor: pointer;
}

.secondary-button {
  min-height: 34px;
  border: 1px solid #cbd5e1;
  border-radius: 6px;
  padding: 0 12px;
  color: #1e3a5f;
  background: #ffffff;
  font: inherit;
  font-size: 13px;
  font-weight: 800;
  cursor: pointer;
}

.primary-button:disabled,
.secondary-button:disabled {
  opacity: 0.55;
  cursor: default;
}

@media (max-width: 720px) {
  .form-section {
    grid-template-columns: 1fr;
  }

  .span-2 {
    grid-column: auto;
  }

  .form-section--logo {
    grid-template-columns: 1fr;
  }
}
</style>
