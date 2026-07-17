<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'

import type {
  PlatformOperatingEntity,
  PlatformStore,
  PlatformTenantStoreAccessStore
} from '../../api/platformApi'
import PasswordInput from '../common/PasswordInput.vue'
import type {
  PlatformTenantAdminStoreAccessFormModel,
  PlatformOperatingEntityFormModel,
  PlatformStoreFormModel
} from './platformTenantUi'

const props = defineProps<{
  operatingEntities: PlatformOperatingEntity[]
  stores: PlatformStore[]
  adminStoreOptions: PlatformTenantStoreAccessStore[]
  adminStoreIds: string[]
  defaultAdminStoreId: string
  saving: boolean
}>()

const emit = defineEmits<{
  saveOperatingEntity: [form: PlatformOperatingEntityFormModel]
  saveStore: [form: PlatformStoreFormModel]
  deleteStore: [store: PlatformStore]
  deleteOperatingEntity: [entity: PlatformOperatingEntity]
  saveAdminStoreAccess: [form: PlatformTenantAdminStoreAccessFormModel]
}>()

const { t } = useI18n()

type StructurePanel = 'entities' | 'stores'

const entityForm = reactive<PlatformOperatingEntityFormModel>(emptyEntityForm())
const storeForm = reactive<PlatformStoreFormModel>(emptyStoreForm())
const adminAccessForm = reactive<PlatformTenantAdminStoreAccessFormModel>({
  adminStoreIds: [],
  defaultAdminStoreId: ''
})
const activePanel = ref<StructurePanel>('entities')
const entityFormOpen = ref(false)
const storeFormOpen = ref(false)
const selectedOperatingEntityId = ref('')

const activeOperatingEntities = computed(() => props.operatingEntities.filter(entity => entity.status === 'active'))
const entityById = computed(() => new Map(props.operatingEntities.map(entity => [entity.id, entity])))
const selectedOperatingEntity = computed(() =>
  props.operatingEntities.find(entity => entity.id === selectedOperatingEntityId.value)
)
const selectedOperatingEntityIsActive = computed(() => selectedOperatingEntity.value?.status === 'active')
const visibleStores = computed(() => {
  if (!selectedOperatingEntityId.value) {
    return props.stores
  }
  return props.stores.filter(store => store.operatingEntityId === selectedOperatingEntityId.value)
})
const visibleAdminStoreOptions = computed(() => {
  if (!selectedOperatingEntityId.value) {
    return []
  }
  return props.adminStoreOptions.filter(store => store.operatingEntityId === selectedOperatingEntityId.value)
})
const selectedAdminStoreOptions = computed(() => {
  const selected = new Set(adminAccessForm.adminStoreIds)
  return props.adminStoreOptions.filter(store => selected.has(store.storeId))
})
const currentEntitySelectedAdminStoreOptions = computed(() => {
  const selected = new Set(adminAccessForm.adminStoreIds)
  return visibleAdminStoreOptions.value.filter(store => selected.has(store.storeId))
})
const defaultAdminStoreOption = computed(() =>
  props.adminStoreOptions.find(store => store.storeId === adminAccessForm.defaultAdminStoreId)
)
const defaultStoreInSelectedEntity = computed(() =>
  currentEntitySelectedAdminStoreOptions.value.some(store => store.storeId === adminAccessForm.defaultAdminStoreId)
)
const structureGuideKey = computed(() => {
  if (props.operatingEntities.length === 0) {
    return 'platform.tenants.structure.guide.noEntities'
  }
  if (props.stores.length === 0) {
    return 'platform.tenants.structure.guide.noStores'
  }
  return ''
})
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
    if (entityForm.id && !props.operatingEntities.some(entity => entity.id === entityForm.id)) {
      entityFormOpen.value = false
      resetEntityForm()
    }
    if (
      selectedOperatingEntityId.value &&
      props.operatingEntities.some(entity => entity.id === selectedOperatingEntityId.value)
    ) {
      return
    }
    selectedOperatingEntityId.value = activeOperatingEntities.value[0]?.id || props.operatingEntities[0]?.id || ''
    if (!storeForm.operatingEntityId) {
      storeForm.operatingEntityId = selectedOperatingEntityId.value || activeOperatingEntities.value[0]?.id || ''
    }
  },
  { deep: true, immediate: true }
)

watch(
  () => [props.adminStoreIds, props.defaultAdminStoreId, props.adminStoreOptions] as const,
  () => {
    adminAccessForm.adminStoreIds = [...props.adminStoreIds]
    adminAccessForm.defaultAdminStoreId = props.defaultAdminStoreId
    ensureDefaultAdminStore()
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
    currency: 'SGD',
    adminUsername: '',
    adminPassword: ''
  }
}

function editEntity(entity: PlatformOperatingEntity): void {
  activePanel.value = 'entities'
  selectOperatingEntity(entity.id)
  entityFormOpen.value = true
  Object.assign(entityForm, {
    id: entity.id,
    entityCode: entity.entityCode,
    displayName: entity.displayName,
    status: entity.status === 'archived' ? 'inactive' : entity.status,
    defaultLocale: entity.defaultLocale || 'zh-CN',
    contactPhone: entity.contactPhone || '',
    address: entity.address || '',
    principalName: entity.principalName || ''
  } satisfies PlatformOperatingEntityFormModel)
}

function resetEntityForm(): void {
  Object.assign(entityForm, emptyEntityForm())
}

function openEntityForm(): void {
  activePanel.value = 'entities'
  entityFormOpen.value = true
  resetEntityForm()
}

function closeEntityForm(): void {
  entityFormOpen.value = false
  resetEntityForm()
}

function submitEntity(): void {
  emit('saveOperatingEntity', { ...entityForm })
}

function editStore(store: PlatformStore): void {
  activePanel.value = 'stores'
  if (store.operatingEntityId) {
    selectOperatingEntity(store.operatingEntityId)
  }
  storeFormOpen.value = true
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
    currency: store.currency || 'SGD',
    adminUsername: '',
    adminPassword: ''
  } satisfies PlatformStoreFormModel)
}

function resetStoreForm(): void {
  Object.assign(storeForm, {
    ...emptyStoreForm(),
    operatingEntityId:
      selectedOperatingEntityIsActive.value
        ? selectedOperatingEntityId.value
        : activeOperatingEntities.value[0]?.id || ''
  } satisfies PlatformStoreFormModel)
}

function openStoreForm(): void {
  activePanel.value = 'stores'
  storeFormOpen.value = true
  resetStoreForm()
}

function closeStoreForm(): void {
  storeFormOpen.value = false
  resetStoreForm()
}

function submitStore(): void {
  emit('saveStore', { ...storeForm })
}

function deleteStore(store: PlatformStore): void {
  emit('deleteStore', store)
}

function entityHasCurrentStores(entityId: string): boolean {
  return props.stores.some(store => store.operatingEntityId === entityId)
}

function deleteOperatingEntity(entity: PlatformOperatingEntity): void {
  emit('deleteOperatingEntity', entity)
}

function toggleAdminStoreFromEvent(storeId: string, event: Event): void {
  const checked = (event.target as HTMLInputElement | null)?.checked === true
  const selected = new Set(adminAccessForm.adminStoreIds)
  if (checked) {
    selected.add(storeId)
  } else {
    selected.delete(storeId)
  }
  adminAccessForm.adminStoreIds = Array.from(selected)
  ensureDefaultAdminStore()
}

function ensureDefaultAdminStore(): void {
  const selected = new Set(adminAccessForm.adminStoreIds)
  const validSelectedStores = selectedAdminStoreOptions.value
  if (
    adminAccessForm.defaultAdminStoreId &&
    selected.has(adminAccessForm.defaultAdminStoreId) &&
    defaultAdminStoreOption.value
  ) {
    return
  }

  adminAccessForm.defaultAdminStoreId =
    currentEntitySelectedAdminStoreOptions.value[0]?.storeId ||
    validSelectedStores[0]?.storeId ||
    ''
}

function saveAdminStoreAccess(): void {
  emit('saveAdminStoreAccess', {
    adminStoreIds: [...adminAccessForm.adminStoreIds],
    defaultAdminStoreId: adminAccessForm.defaultAdminStoreId
  })
}

function showPanel(panel: StructurePanel): void {
  activePanel.value = panel
}

function selectOperatingEntity(entityId: string): void {
  selectedOperatingEntityId.value = entityId
  if (storeFormOpen.value && !storeForm.id) {
    storeForm.operatingEntityId = selectedOperatingEntityIsActive.value
      ? entityId
      : activeOperatingEntities.value[0]?.id || ''
  }
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

function adminStoreDisplayName(store: PlatformTenantStoreAccessStore): string {
  const name = store.storeName || store.storeCode || store.storeId.slice(0, 8)
  return store.storeCode ? `${name} (${store.storeCode})` : name
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

    <div class="structure-summary" :aria-label="$t('platform.tenants.structure.summary.aria')">
      <div>
        <span>{{ $t('platform.tenants.structure.summary.operatingEntities') }}</span>
        <strong>{{ operatingEntities.length }}</strong>
      </div>
      <div>
        <span>{{ $t('platform.tenants.structure.summary.stores') }}</span>
        <strong>{{ stores.length }}</strong>
      </div>
      <div>
        <span>{{ $t('platform.tenants.structure.summary.selectedStores') }}</span>
        <strong>{{ visibleStores.length }}</strong>
      </div>
    </div>

    <div v-if="structureGuideKey" class="structure-guide">
      <p>{{ $t(structureGuideKey) }}</p>
      <button
        v-if="operatingEntities.length === 0"
        class="primary-button"
        type="button"
        @click="openEntityForm"
      >
        {{ $t('platform.tenants.structure.actions.newEntity') }}
      </button>
      <button
        v-else
        class="primary-button"
        type="button"
        :disabled="activeOperatingEntities.length === 0"
        @click="openStoreForm"
      >
        {{ $t('platform.tenants.structure.actions.newStore') }}
      </button>
    </div>

    <div class="structure-tabs" role="tablist" :aria-label="$t('platform.tenants.structure.tabs.aria')">
      <button type="button" :class="{ active: activePanel === 'entities' }" @click="showPanel('entities')">
        {{ $t('platform.tenants.structure.operatingEntities.title') }}
        <strong>{{ operatingEntities.length }}</strong>
      </button>
      <button type="button" :class="{ active: activePanel === 'stores' }" @click="showPanel('stores')">
        {{ $t('platform.tenants.structure.stores.title') }}
        <strong>{{ stores.length }}</strong>
      </button>
    </div>

    <div class="structure-grid">
      <section
        class="structure-section"
        :class="{ active: activePanel === 'entities' }"
        :aria-label="$t('platform.tenants.structure.operatingEntities.title')"
      >
        <div class="section-heading">
          <div>
            <h3>{{ $t('platform.tenants.structure.operatingEntities.title') }}</h3>
            <span>{{ $t('platform.tenants.structure.operatingEntities.hint') }}</span>
          </div>
          <button class="secondary-button" type="button" @click="openEntityForm">
            {{ $t('platform.tenants.structure.actions.newEntity') }}
          </button>
        </div>

        <div v-if="operatingEntities.length === 0" class="empty-state">
          <strong>{{ $t('platform.tenants.structure.operatingEntities.empty') }}</strong>
          <span>{{ $t('platform.tenants.structure.guide.noEntities') }}</span>
        </div>
        <div v-else class="data-list">
          <article v-for="entity in operatingEntities" :key="entity.id" class="data-row structure-entity-row">
            <button
              class="structure-entity-button"
              :class="{ 'structure-entity-button--active': entity.id === selectedOperatingEntityId }"
              type="button"
              @click="selectOperatingEntity(entity.id)"
            >
              <strong>{{ operatingEntityLabel(entity) }}</strong>
              <small>{{ entity.entityCode }} · {{ $t(`platform.tenants.structure.status.${entity.status}`) }}</small>
            </button>
            <div class="row-actions">
              <button class="text-button" type="button" :disabled="saving" @click="editEntity(entity)">
                {{ $t('common.actions.edit') }}
              </button>
              <button
                v-if="!entityHasCurrentStores(entity.id)"
                class="text-button danger"
                type="button"
                :disabled="saving"
                @click="deleteOperatingEntity(entity)"
              >
                {{ $t('platform.tenants.structure.actions.deleteEntity') }}
              </button>
            </div>
          </article>
        </div>

        <form v-if="entityFormOpen" class="inline-form" @submit.prevent="submitEntity">
          <div class="form-subheading span-2">
            <strong>
              {{ entityForm.id ? $t('platform.tenants.structure.formTitles.editEntity') : $t('platform.tenants.structure.actions.newEntity') }}
            </strong>
            <button class="text-button" type="button" @click="closeEntityForm">
              {{ $t('common.actions.cancel') }}
            </button>
          </div>
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
          <details class="advanced-fields span-2">
            <summary>{{ $t('platform.tenants.structure.fields.supplementalInfo') }}</summary>
            <div class="advanced-grid">
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
            </div>
          </details>
          <div class="form-actions span-2">
            <button class="primary-button" type="submit" :disabled="saving">
              {{ saving ? $t('common.actions.saving') : $t('common.actions.save') }}
            </button>
          </div>
        </form>
      </section>

      <section
        class="structure-section"
        :class="{ active: activePanel === 'stores' }"
        :aria-label="$t('platform.tenants.structure.stores.title')"
      >
        <div class="section-heading">
          <div>
            <h3>{{ $t('platform.tenants.structure.stores.title') }}</h3>
            <span>{{ $t('platform.tenants.structure.stores.hint') }}</span>
          </div>
          <button
            class="secondary-button"
            type="button"
            :disabled="!selectedOperatingEntityIsActive"
            @click="openStoreForm"
          >
            {{ $t('platform.tenants.structure.actions.newStore') }}
          </button>
        </div>

        <div v-if="visibleStores.length === 0" class="empty-state">
          <strong>{{ $t('platform.tenants.structure.stores.empty') }}</strong>
          <span>
            {{ activeOperatingEntities.length === 0
              ? $t('platform.tenants.structure.guide.noActiveEntities')
              : stores.length === 0
                ? $t('platform.tenants.structure.guide.noStores')
                : $t('platform.tenants.structure.guide.noStoresForEntity') }}
          </span>
        </div>
        <div v-else class="data-list">
          <article v-for="store in visibleStores" :key="store.id" class="data-row">
            <div>
              <strong>{{ store.storeName }}</strong>
              <small>{{ store.storeCode }} · {{ storeEntityName(store) }} · {{ $t(`platform.tenants.structure.status.${store.status}`) }}</small>
            </div>
            <div class="row-actions">
              <button class="text-button" type="button" :disabled="saving" @click="editStore(store)">
                {{ $t('common.actions.edit') }}
              </button>
              <button class="text-button danger" type="button" :disabled="saving" @click="deleteStore(store)">
                {{ $t('platform.tenants.structure.actions.deleteStore') }}
              </button>
            </div>
          </article>
        </div>

        <form class="admin-store-access-panel" @submit.prevent="saveAdminStoreAccess">
          <div class="form-subheading">
            <strong>{{ $t('platform.tenants.structure.adminStoreAccess.title') }}</strong>
          </div>
          <p v-if="visibleAdminStoreOptions.length === 0" class="helper-line">
            {{ $t('platform.tenants.structure.adminStoreAccess.empty') }}
          </p>
          <div v-else class="admin-store-grid">
            <label
              v-for="store in visibleAdminStoreOptions"
              :key="store.storeId"
              class="admin-store-option"
            >
              <input
                type="checkbox"
                :checked="adminAccessForm.adminStoreIds.includes(store.storeId)"
                @change="toggleAdminStoreFromEvent(store.storeId, $event)"
              />
              <span>
                <strong>{{ adminStoreDisplayName(store) }}</strong>
                <small>{{ store.locale || '-' }}</small>
              </span>
            </label>
          </div>
          <label>
            <span>{{ $t('platform.tenants.structure.adminStoreAccess.defaultStore') }}</span>
            <select
              v-model="adminAccessForm.defaultAdminStoreId"
              :disabled="selectedAdminStoreOptions.length === 0"
              required
            >
              <option
                v-if="defaultAdminStoreOption && !defaultStoreInSelectedEntity"
                :value="defaultAdminStoreOption.storeId"
              >
                {{ adminStoreDisplayName(defaultAdminStoreOption) }}
              </option>
              <option
                v-for="store in currentEntitySelectedAdminStoreOptions"
                :key="store.storeId"
                :value="store.storeId"
              >
                {{ adminStoreDisplayName(store) }}
              </option>
            </select>
          </label>
          <p
            v-if="adminAccessForm.defaultAdminStoreId && !defaultStoreInSelectedEntity"
            class="helper-line"
          >
            {{ $t('platform.tenants.structure.adminStoreAccess.defaultStoreElsewhere') }}
          </p>
          <div class="form-actions">
            <button
              class="primary-button"
              type="submit"
              :disabled="saving || selectedAdminStoreOptions.length === 0 || !adminAccessForm.defaultAdminStoreId"
            >
              {{ saving ? $t('common.actions.saving') : $t('platform.tenants.structure.adminStoreAccess.save') }}
            </button>
          </div>
        </form>

        <form v-if="storeFormOpen" class="inline-form" @submit.prevent="submitStore">
          <div class="form-subheading span-2">
            <strong>
              {{ storeForm.id ? $t('platform.tenants.structure.formTitles.editStore') : $t('platform.tenants.structure.actions.newStore') }}
            </strong>
            <button class="text-button" type="button" @click="closeStoreForm">
              {{ $t('common.actions.cancel') }}
            </button>
          </div>
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
          <label class="span-2">
            <span>{{ $t('platform.tenants.structure.fields.status') }}</span>
            <select v-model="storeForm.status">
              <option v-for="option in storeStatusOptions" :key="option.value" :value="option.value">
                {{ $t(option.labelKey) }}
              </option>
            </select>
          </label>
          <details class="advanced-fields span-2" open>
            <summary>{{ $t('platform.tenants.structure.fields.branchAdminAccount') }}</summary>
            <div class="advanced-grid">
              <label>
                <span>{{ $t('platform.tenants.structure.fields.branchAdminUsername') }}</span>
                <input
                  v-model.trim="storeForm.adminUsername"
                  :required="!storeForm.id"
                  maxlength="64"
                  autocomplete="off"
                />
              </label>
              <label>
                <span>{{ $t('platform.tenants.structure.fields.branchAdminPassword') }}</span>
                <PasswordInput
                  v-model.trim="storeForm.adminPassword"
                  :required="!storeForm.id"
                  maxlength="6"
                  :placeholder="$t('platform.tenants.form.passwordPlaceholder')"
                  autocomplete="new-password"
                />
              </label>
            </div>
          </details>
          <details class="advanced-fields span-2">
            <summary>{{ $t('platform.tenants.structure.fields.operationDefaults') }}</summary>
            <div class="advanced-grid">
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
            </div>
          </details>
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

.structure-summary {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.structure-summary div {
  min-width: 0;
  display: grid;
  gap: 3px;
  padding: 12px;
  border: 1px solid #dbe3ea;
  border-radius: 8px;
  background: #ffffff;
}

.structure-summary span {
  color: #64748b;
  font-size: 12px;
  font-weight: 800;
}

.structure-summary strong {
  color: #0f172a;
  font-size: 24px;
  line-height: 1.1;
}

.structure-guide {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
  padding: 12px;
  border: 1px solid #bfdbfe;
  border-radius: 8px;
  background: #eff6ff;
}

.structure-guide p {
  margin: 0;
  color: #1e3a5f;
  font-size: 14px;
  font-weight: 800;
}

.structure-tabs {
  display: none;
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

.section-heading div {
  min-width: 0;
  display: grid;
  gap: 3px;
}

.section-heading h3 {
  margin: 0;
  color: #0f172a;
  font-size: 16px;
}

.section-heading span,
.empty-state span {
  color: #64748b;
  font-size: 13px;
  font-weight: 700;
}

.empty-state {
  display: grid;
  gap: 4px;
  padding: 12px;
  border: 1px dashed #cbd5e1;
  border-radius: 8px;
  background: #f8fafc;
}

.empty-state strong {
  color: #0f172a;
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

.structure-entity-row {
  padding: 4px 10px 4px 4px;
}

.structure-entity-button {
  min-width: 0;
  flex: 1;
  display: grid;
  gap: 2px;
  min-height: 44px;
  border: 0;
  border-radius: 6px;
  padding: 6px 8px;
  color: inherit;
  background: transparent;
  font: inherit;
  text-align: left;
  cursor: pointer;
}

.structure-entity-button--active {
  background: #e6fffb;
  box-shadow: inset 3px 0 0 #0f766e;
}

.data-row div {
  min-width: 0;
  display: grid;
  gap: 2px;
}

.row-actions {
  display: flex;
  flex: 0 0 auto;
  gap: 8px;
  align-items: center;
  justify-content: flex-end;
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
.empty-line,
.helper-line {
  color: #64748b;
  font-size: 13px;
  font-weight: 700;
}

.helper-line {
  margin: 0;
}

.inline-form {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  padding-top: 2px;
}

.admin-store-access-panel {
  display: grid;
  gap: 12px;
  padding-top: 2px;
}

.admin-store-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.admin-store-option {
  min-height: 58px;
  grid-template-columns: auto minmax(0, 1fr);
  align-items: center;
  gap: 10px;
  padding: 10px;
  border: 1px solid #dbe3ea;
  border-radius: 8px;
  background: #f8fafc;
}

.admin-store-option input {
  width: 18px;
  height: 18px;
  min-height: 0;
  padding: 0;
}

.admin-store-option span {
  min-width: 0;
  display: grid;
  gap: 2px;
}

.admin-store-option strong {
  overflow-wrap: anywhere;
  color: #0f172a;
}

.admin-store-option small {
  color: #64748b;
  font-size: 12px;
}

.form-subheading {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  align-items: center;
  padding-top: 8px;
  border-top: 1px solid #edf2f7;
}

.form-subheading strong {
  color: #0f172a;
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

.advanced-fields {
  display: grid;
  gap: 10px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 10px 12px;
  background: #f8fafc;
}

.advanced-fields summary {
  color: #334155;
  font-size: 14px;
  font-weight: 800;
  cursor: pointer;
}

.advanced-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  padding-top: 10px;
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

.text-button.danger {
  color: #b91c1c;
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
  .structure-summary {
    gap: 8px;
  }

  .structure-summary div {
    padding: 10px;
  }

  .structure-summary strong {
    font-size: 20px;
  }

  .structure-guide {
    display: grid;
  }

  .structure-tabs {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 8px;
  }

  .structure-tabs button {
    min-width: 0;
    min-height: 38px;
    border: 1px solid #cbd5e1;
    border-radius: 6px;
    padding: 0 10px;
    color: #334155;
    background: #ffffff;
    font: inherit;
    font-size: 13px;
    font-weight: 800;
    cursor: pointer;
  }

  .structure-tabs button.active {
    color: #ffffff;
    border-color: #0f766e;
    background: #0f766e;
  }

  .structure-tabs strong {
    margin-left: 4px;
  }

  .structure-grid {
    display: block;
  }

  .structure-section {
    display: none;
  }

  .structure-section.active {
    display: grid;
  }

  .inline-form {
    grid-template-columns: 1fr;
  }

  .admin-store-grid {
    grid-template-columns: 1fr;
  }

  .advanced-grid {
    grid-template-columns: 1fr;
  }

  .span-2 {
    grid-column: auto;
  }

  .data-row,
  .section-heading {
    align-items: stretch;
  }

  .row-actions {
    justify-content: flex-start;
  }

  .section-heading {
    display: grid;
  }
}
</style>
