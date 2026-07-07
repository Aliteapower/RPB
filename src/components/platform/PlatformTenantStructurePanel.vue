<script setup lang="ts">
import { computed, reactive, watch } from 'vue'
import { useI18n } from 'vue-i18n'

import type { PlatformOperatingEntity, PlatformStore } from '../../api/platformApi'
import type {
  PlatformOperatingEntityFormModel,
  PlatformStoreFormModel
} from './platformTenantUi'

const props = defineProps<{
  operatingEntities: PlatformOperatingEntity[]
  stores: PlatformStore[]
  saving: boolean
}>()

const emit = defineEmits<{
  saveOperatingEntity: [form: PlatformOperatingEntityFormModel]
  saveStore: [form: PlatformStoreFormModel]
}>()

const { t } = useI18n()

const entityForm = reactive<PlatformOperatingEntityFormModel>(emptyEntityForm())
const storeForm = reactive<PlatformStoreFormModel>(emptyStoreForm())

const activeOperatingEntities = computed(() => props.operatingEntities.filter(entity => entity.status === 'active'))
const entityById = computed(() => new Map(props.operatingEntities.map(entity => [entity.id, entity])))
const entityStatusOptions = [
  { value: 'active', labelKey: 'platform.tenants.structure.status.active' },
  { value: 'inactive', labelKey: 'platform.tenants.structure.status.inactive' }
] as const
const storeStatusOptions = [
  { value: 'created', labelKey: 'platform.tenants.structure.status.created' },
  { value: 'active', labelKey: 'platform.tenants.structure.status.active' },
  { value: 'inactive', labelKey: 'platform.tenants.structure.status.inactive' }
] as const

watch(
  () => props.operatingEntities,
  () => {
    if (!storeForm.operatingEntityId) {
      storeForm.operatingEntityId = activeOperatingEntities.value[0]?.id || ''
    }
  },
  { deep: true, immediate: true }
)

function emptyEntityForm(): PlatformOperatingEntityFormModel {
  return {
    id: '',
    entityCode: '',
    displayName: '',
    status: 'active',
    defaultLocale: 'zh-CN',
    contactPhone: '',
    address: '',
    principalName: ''
  }
}

function emptyStoreForm(): PlatformStoreFormModel {
  return {
    id: '',
    operatingEntityId: '',
    storeCode: '',
    storeName: '',
    status: 'active',
    timezone: 'Asia/Singapore',
    locale: 'zh-CN',
    dateFormat: 'DD-MM-YYYY',
    timeFormat: 'HH:mm',
    currency: 'SGD'
  }
}

function editEntity(entity: PlatformOperatingEntity): void {
  Object.assign(entityForm, {
    id: entity.id,
    entityCode: entity.entityCode,
    displayName: entity.displayName,
    status: entity.status,
    defaultLocale: entity.defaultLocale || 'zh-CN',
    contactPhone: entity.contactPhone || '',
    address: entity.address || '',
    principalName: entity.principalName || ''
  } satisfies PlatformOperatingEntityFormModel)
}

function resetEntityForm(): void {
  Object.assign(entityForm, emptyEntityForm())
}

function submitEntity(): void {
  emit('saveOperatingEntity', { ...entityForm })
}

function editStore(store: PlatformStore): void {
  Object.assign(storeForm, {
    id: store.id,
    operatingEntityId: store.operatingEntityId || activeOperatingEntities.value[0]?.id || '',
    storeCode: store.storeCode,
    storeName: store.storeName,
    status: store.status,
    timezone: store.timezone || 'Asia/Singapore',
    locale: store.locale || 'zh-CN',
    dateFormat: store.dateFormat || 'DD-MM-YYYY',
    timeFormat: store.timeFormat || 'HH:mm',
    currency: store.currency || 'SGD'
  } satisfies PlatformStoreFormModel)
}

function resetStoreForm(): void {
  Object.assign(storeForm, {
    ...emptyStoreForm(),
    operatingEntityId: activeOperatingEntities.value[0]?.id || ''
  } satisfies PlatformStoreFormModel)
}

function submitStore(): void {
  emit('saveStore', { ...storeForm })
}

function operatingEntityLabel(entity: PlatformOperatingEntity): string {
  return entity.displayName || entity.entityCode
}

function storeEntityName(store: PlatformStore): string {
  if (store.operatingEntityName) {
    return store.operatingEntityName
  }
  if (store.operatingEntityId) {
    return entityById.value.get(store.operatingEntityId)?.displayName || store.operatingEntityId.slice(0, 8)
  }
  return t('platform.tenants.structure.fields.unassigned')
}
</script>

<template>
  <section id="tenant-structure" class="structure-panel" :aria-label="$t('platform.tenants.structure.title')">
    <div class="panel-heading">
      <div>
        <span>{{ $t('platform.tenants.structure.kicker') }}</span>
        <h2>{{ $t('platform.tenants.structure.title') }}</h2>
      </div>
    </div>

    <div class="structure-grid">
      <section class="structure-section" :aria-label="$t('platform.tenants.structure.operatingEntities.title')">
        <div class="section-heading">
          <h3>{{ $t('platform.tenants.structure.operatingEntities.title') }}</h3>
          <button class="secondary-button" type="button" @click="resetEntityForm">
            {{ $t('platform.tenants.structure.actions.newEntity') }}
          </button>
        </div>

        <div v-if="operatingEntities.length === 0" class="empty-line">
          {{ $t('platform.tenants.structure.operatingEntities.empty') }}
        </div>
        <div v-else class="data-list">
          <article v-for="entity in operatingEntities" :key="entity.id" class="data-row">
            <div>
              <strong>{{ operatingEntityLabel(entity) }}</strong>
              <small>{{ entity.entityCode }} · {{ $t(`platform.tenants.structure.status.${entity.status}`) }}</small>
            </div>
            <button class="text-button" type="button" @click="editEntity(entity)">
              {{ $t('common.actions.edit') }}
            </button>
          </article>
        </div>

        <form class="inline-form" @submit.prevent="submitEntity">
          <label>
            <span>{{ $t('platform.tenants.structure.fields.entityCode') }}</span>
            <input v-model.trim="entityForm.entityCode" required maxlength="64" autocomplete="off" />
          </label>
          <label>
            <span>{{ $t('platform.tenants.structure.fields.displayName') }}</span>
            <input v-model.trim="entityForm.displayName" required maxlength="120" autocomplete="off" />
          </label>
          <label>
            <span>{{ $t('platform.tenants.structure.fields.status') }}</span>
            <select v-model="entityForm.status">
              <option v-for="option in entityStatusOptions" :key="option.value" :value="option.value">
                {{ $t(option.labelKey) }}
              </option>
            </select>
          </label>
          <label>
            <span>{{ $t('platform.tenants.structure.fields.defaultLocale') }}</span>
            <input v-model.trim="entityForm.defaultLocale" maxlength="20" autocomplete="off" />
          </label>
          <label>
            <span>{{ $t('platform.tenants.structure.fields.principal') }}</span>
            <input v-model.trim="entityForm.principalName" maxlength="80" autocomplete="off" />
          </label>
          <label>
            <span>{{ $t('platform.tenants.structure.fields.phone') }}</span>
            <input v-model.trim="entityForm.contactPhone" maxlength="40" autocomplete="off" />
          </label>
          <label class="span-2">
            <span>{{ $t('platform.tenants.structure.fields.address') }}</span>
            <input v-model.trim="entityForm.address" maxlength="240" autocomplete="off" />
          </label>
          <div class="form-actions span-2">
            <button class="primary-button" type="submit" :disabled="saving">
              {{ saving ? $t('common.actions.saving') : $t('common.actions.save') }}
            </button>
          </div>
        </form>
      </section>

      <section class="structure-section" :aria-label="$t('platform.tenants.structure.stores.title')">
        <div class="section-heading">
          <h3>{{ $t('platform.tenants.structure.stores.title') }}</h3>
          <button class="secondary-button" type="button" @click="resetStoreForm">
            {{ $t('platform.tenants.structure.actions.newStore') }}
          </button>
        </div>

        <div v-if="stores.length === 0" class="empty-line">
          {{ $t('platform.tenants.structure.stores.empty') }}
        </div>
        <div v-else class="data-list">
          <article v-for="store in stores" :key="store.id" class="data-row">
            <div>
              <strong>{{ store.storeName }}</strong>
              <small>{{ store.storeCode }} · {{ storeEntityName(store) }} · {{ $t(`platform.tenants.structure.status.${store.status}`) }}</small>
            </div>
            <button class="text-button" type="button" @click="editStore(store)">
              {{ $t('common.actions.edit') }}
            </button>
          </article>
        </div>

        <form class="inline-form" @submit.prevent="submitStore">
          <label class="span-2">
            <span>{{ $t('platform.tenants.structure.fields.operatingEntity') }}</span>
            <select v-model="storeForm.operatingEntityId" required>
              <option value="" disabled>{{ $t('platform.tenants.structure.fields.chooseOperatingEntity') }}</option>
              <option v-for="entity in activeOperatingEntities" :key="entity.id" :value="entity.id">
                {{ operatingEntityLabel(entity) }}
              </option>
            </select>
          </label>
          <label>
            <span>{{ $t('platform.tenants.structure.fields.storeCode') }}</span>
            <input v-model.trim="storeForm.storeCode" required maxlength="64" autocomplete="off" />
          </label>
          <label>
            <span>{{ $t('platform.tenants.structure.fields.storeName') }}</span>
            <input v-model.trim="storeForm.storeName" required maxlength="120" autocomplete="off" />
          </label>
          <label>
            <span>{{ $t('platform.tenants.structure.fields.status') }}</span>
            <select v-model="storeForm.status">
              <option v-for="option in storeStatusOptions" :key="option.value" :value="option.value">
                {{ $t(option.labelKey) }}
              </option>
            </select>
          </label>
          <label>
            <span>{{ $t('platform.tenants.structure.fields.locale') }}</span>
            <input v-model.trim="storeForm.locale" maxlength="20" autocomplete="off" />
          </label>
          <label>
            <span>{{ $t('platform.tenants.structure.fields.timezone') }}</span>
            <input v-model.trim="storeForm.timezone" required maxlength="64" autocomplete="off" />
          </label>
          <label>
            <span>{{ $t('platform.tenants.structure.fields.currency') }}</span>
            <input v-model.trim="storeForm.currency" required maxlength="8" autocomplete="off" />
          </label>
          <label>
            <span>{{ $t('platform.tenants.structure.fields.dateFormat') }}</span>
            <input v-model.trim="storeForm.dateFormat" required maxlength="30" autocomplete="off" />
          </label>
          <label>
            <span>{{ $t('platform.tenants.structure.fields.timeFormat') }}</span>
            <input v-model.trim="storeForm.timeFormat" required maxlength="30" autocomplete="off" />
          </label>
          <div class="form-actions span-2">
            <button class="primary-button" type="submit" :disabled="saving || activeOperatingEntities.length === 0">
              {{ saving ? $t('common.actions.saving') : $t('common.actions.save') }}
            </button>
          </div>
        </form>
      </section>
    </div>
  </section>
</template>

<style scoped>
.structure-panel {
  display: grid;
  gap: 14px;
  margin-top: 18px;
}

.panel-heading,
.section-heading,
.data-row,
.form-actions {
  display: flex;
  align-items: center;
}

.panel-heading {
  justify-content: space-between;
}

.panel-heading span {
  color: #64748b;
  font-size: 13px;
  font-weight: 700;
}

.panel-heading h2 {
  margin: 0;
  color: #0f172a;
  font-size: 20px;
}

.structure-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.structure-section {
  min-width: 0;
  display: grid;
  align-content: start;
  gap: 14px;
  padding: 16px;
  border: 1px solid #dbe3ea;
  border-radius: 8px;
  background: #ffffff;
}

.section-heading {
  justify-content: space-between;
  gap: 10px;
}

.section-heading h3 {
  margin: 0;
  color: #0f172a;
  font-size: 16px;
}

.data-list {
  display: grid;
  gap: 8px;
}

.data-row {
  justify-content: space-between;
  gap: 10px;
  min-height: 54px;
  padding: 10px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #f8fafc;
}

.data-row div {
  min-width: 0;
  display: grid;
  gap: 2px;
}

.data-row strong,
.data-row small {
  min-width: 0;
  overflow-wrap: anywhere;
}

.data-row strong {
  color: #0f172a;
}

.data-row small,
.empty-line {
  color: #64748b;
  font-size: 13px;
  font-weight: 700;
}

.inline-form {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  padding-top: 2px;
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

.form-actions {
  justify-content: flex-end;
}

.primary-button,
.secondary-button,
.text-button {
  min-height: 36px;
  border-radius: 6px;
  font: inherit;
  font-weight: 800;
  cursor: pointer;
}

.primary-button {
  border: 0;
  padding: 0 18px;
  color: #ffffff;
  background: #0f766e;
}

.secondary-button {
  border: 1px solid #cbd5e1;
  padding: 0 12px;
  color: #0f172a;
  background: #ffffff;
}

.text-button {
  border: 0;
  padding: 0 6px;
  color: #0f766e;
  background: transparent;
}

.primary-button:disabled,
.secondary-button:disabled,
.text-button:disabled {
  opacity: 0.55;
  cursor: default;
}

@media (max-width: 1100px) {
  .structure-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .inline-form {
    grid-template-columns: 1fr;
  }

  .span-2 {
    grid-column: auto;
  }

  .data-row,
  .section-heading {
    align-items: stretch;
  }

  .section-heading {
    display: grid;
  }
}
</style>
