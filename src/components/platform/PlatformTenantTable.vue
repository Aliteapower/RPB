<script setup lang="ts">
import { useI18n } from 'vue-i18n'

import type { PlatformTenant, TenantStatus } from '../../api/platformApi'
import type { TenantStatusOption } from './platformTenantUi'

defineProps<{
  tenants: PlatformTenant[]
  loading: boolean
  saving: boolean
  statusOptions: TenantStatusOption[]
  billingOnly?: boolean
}>()

const emit = defineEmits<{
  edit: [tenant: PlatformTenant]
  structure: [tenant: PlatformTenant]
  billing: [tenant: PlatformTenant]
  delete: [tenant: PlatformTenant]
  restore: [tenant: PlatformTenant]
}>()

const { t } = useI18n()

function statusLabel(status: TenantStatus, statusOptions: TenantStatusOption[]): string {
  const labelKey = statusOptions.find(option => option.value === status)?.labelKey
  return labelKey ? t(labelKey) : status
}

function formatTenantUpdatedAt(value: string): string {
  return new Date(value).toLocaleString()
}
</script>

<template>
  <div class="tenant-table-wrap">
    <table class="tenant-table">
      <thead>
        <tr>
          <th>{{ $t('platform.tenants.table.columns.tenantCode') }}</th>
          <th>{{ $t('platform.tenants.table.columns.name') }}</th>
          <th>{{ $t('platform.tenants.table.columns.principal') }}</th>
          <th>{{ $t('platform.tenants.table.columns.phone') }}</th>
          <th>{{ $t('platform.tenants.table.columns.address') }}</th>
          <th>{{ $t('platform.tenants.table.columns.status') }}</th>
          <th>{{ $t('platform.tenants.table.columns.updatedAt') }}</th>
          <th>{{ $t('platform.tenants.table.columns.actions') }}</th>
        </tr>
      </thead>
      <tbody>
        <tr v-if="loading">
          <td colspan="8" class="table-empty">{{ $t('common.actions.loading') }}</td>
        </tr>
        <tr v-else-if="tenants.length === 0">
          <td colspan="8" class="table-empty">{{ $t('platform.tenants.table.empty') }}</td>
        </tr>
        <template v-else>
          <tr v-for="tenant in tenants" :key="tenant.id" :class="{ deleted: tenant.deleted }">
            <td>
              <strong>{{ tenant.tenantCode }}</strong>
            </td>
            <td>{{ tenant.displayName }}</td>
            <td>{{ tenant.principalName || '-' }}</td>
            <td>{{ tenant.contactPhone || '-' }}</td>
            <td class="address-cell">{{ tenant.address || '-' }}</td>
            <td>
              <span class="status-pill" :class="{ deleted: tenant.deleted }">
                {{ tenant.deleted ? $t('platform.tenants.status.deleted') : statusLabel(tenant.status, statusOptions) }}
              </span>
            </td>
            <td>{{ formatTenantUpdatedAt(tenant.updatedAt) }}</td>
            <td>
              <div class="row-actions">
                <button v-if="!billingOnly" type="button" class="text-action" @click="emit('edit', tenant)">
                  {{ $t('common.actions.edit') }}
                </button>
                <button v-if="!billingOnly" type="button" class="text-action" @click="emit('structure', tenant)">
                  {{ $t('platform.tenants.table.structure') }}
                </button>
                <button type="button" class="text-action" @click="emit('billing', tenant)">
                  {{ billingOnly ? $t('platform.tenants.table.billingFull') : $t('platform.tenants.table.billingShort') }}
                </button>
                <button
                  v-if="!billingOnly && tenant.deleted"
                  type="button"
                  class="text-action"
                  :disabled="saving"
                  @click="emit('restore', tenant)"
                >
                  {{ $t('common.actions.restore') }}
                </button>
                <button
                  v-else-if="!billingOnly"
                  type="button"
                  class="text-action danger"
                  :disabled="saving"
                  @click="emit('delete', tenant)"
                >
                  {{ $t('common.actions.delete') }}
                </button>
              </div>
            </td>
          </tr>
        </template>
      </tbody>
    </table>
  </div>

  <div class="tenant-card-list" :aria-label="$t('platform.tenants.list.title')">
    <p v-if="loading" class="table-empty tenant-card-empty">{{ $t('common.actions.loading') }}</p>
    <p v-else-if="tenants.length === 0" class="table-empty tenant-card-empty">{{ $t('platform.tenants.table.empty') }}</p>
    <article v-for="tenant in tenants" v-else :key="tenant.id" class="tenant-card" :class="{ deleted: tenant.deleted }">
      <header class="tenant-card__header">
        <div>
          <strong>{{ tenant.tenantCode }}</strong>
          <span>{{ tenant.displayName }}</span>
        </div>
        <span class="status-pill" :class="{ deleted: tenant.deleted }">
          {{ tenant.deleted ? $t('platform.tenants.status.deleted') : statusLabel(tenant.status, statusOptions) }}
        </span>
      </header>

      <dl class="tenant-card__facts">
        <div>
          <dt>{{ $t('platform.tenants.table.columns.principal') }}</dt>
          <dd>{{ tenant.principalName || '-' }}</dd>
        </div>
        <div>
          <dt>{{ $t('platform.tenants.table.columns.phone') }}</dt>
          <dd>{{ tenant.contactPhone || '-' }}</dd>
        </div>
        <div class="tenant-card__wide">
          <dt>{{ $t('platform.tenants.table.columns.address') }}</dt>
          <dd>{{ tenant.address || '-' }}</dd>
        </div>
      </dl>

      <p class="tenant-card__updated">
        <span>{{ $t('platform.tenants.table.columns.updatedAt') }}</span>
        <strong>{{ formatTenantUpdatedAt(tenant.updatedAt) }}</strong>
      </p>

      <div class="tenant-card__actions">
        <button
          v-if="!billingOnly"
          type="button"
          class="tenant-card__primary"
          @click="emit('structure', tenant)"
        >
          {{ $t('platform.tenants.table.structure') }}
        </button>
        <button
          v-if="billingOnly"
          type="button"
          class="tenant-card__primary"
          @click="emit('billing', tenant)"
        >
          {{ $t('platform.tenants.table.billingFull') }}
        </button>
        <button v-if="!billingOnly" type="button" class="text-action" @click="emit('edit', tenant)">
          {{ $t('common.actions.edit') }}
        </button>
        <button v-if="!billingOnly" type="button" class="text-action" @click="emit('billing', tenant)">
          {{ $t('platform.tenants.table.billingShort') }}
        </button>
        <button
          v-if="!billingOnly && tenant.deleted"
          type="button"
          class="text-action"
          :disabled="saving"
          @click="emit('restore', tenant)"
        >
          {{ $t('common.actions.restore') }}
        </button>
        <button
          v-else-if="!billingOnly"
          type="button"
          class="text-action danger"
          :disabled="saving"
          @click="emit('delete', tenant)"
        >
          {{ $t('common.actions.delete') }}
        </button>
      </div>
    </article>
  </div>
</template>

<style scoped>
.tenant-table-wrap {
  overflow-x: auto;
  border: 1px solid #dbe3ea;
  border-radius: 8px;
  background: #ffffff;
}

.tenant-table {
  width: 100%;
  min-width: 1080px;
  border-collapse: collapse;
}

.tenant-table th,
.tenant-table td {
  padding: 12px 14px;
  border-bottom: 1px solid #edf2f7;
  text-align: left;
  vertical-align: middle;
}

.tenant-table th {
  color: #64748b;
  background: #f8fafc;
  font-size: 13px;
}

.tenant-table tr.deleted {
  color: #64748b;
  background: #fafafa;
}

.address-cell {
  max-width: 260px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.table-empty {
  color: #64748b;
  text-align: center;
}

.status-pill {
  display: inline-flex;
  min-height: 26px;
  align-items: center;
  border-radius: 999px;
  padding: 0 10px;
  color: #166534;
  background: #dcfce7;
  font-size: 12px;
  font-weight: 800;
}

.status-pill.deleted {
  color: #991b1b;
  background: #fee2e2;
}

.row-actions {
  display: flex;
  gap: 12px;
  align-items: center;
}

.text-action {
  border: 0;
  padding: 0;
  color: #0f766e;
  background: transparent;
  font: inherit;
  font-weight: 800;
  cursor: pointer;
}

.text-action.danger {
  color: #b91c1c;
}

.text-action:disabled {
  opacity: 0.55;
  cursor: default;
}

.tenant-card-list {
  display: none;
}

.tenant-card-empty,
.tenant-card {
  border: 1px solid #dbe3ea;
  border-radius: 8px;
  background: #ffffff;
}

.tenant-card-empty {
  margin: 0;
  padding: 16px;
}

.tenant-card {
  display: grid;
  gap: 12px;
  padding: 14px;
}

.tenant-card.deleted {
  color: #64748b;
  background: #fafafa;
}

.tenant-card__header,
.tenant-card__actions {
  display: flex;
  align-items: center;
}

.tenant-card__header {
  justify-content: space-between;
  gap: 12px;
}

.tenant-card__header div {
  min-width: 0;
  display: grid;
  gap: 3px;
}

.tenant-card__header strong,
.tenant-card__header span,
.tenant-card__facts dd,
.tenant-card__updated strong {
  min-width: 0;
  overflow-wrap: anywhere;
}

.tenant-card__header strong {
  color: #0f172a;
  font-size: 16px;
}

.tenant-card__header div span {
  color: #334155;
  font-size: 14px;
  font-weight: 700;
}

.tenant-card__facts {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  margin: 0;
}

.tenant-card__facts div,
.tenant-card__updated {
  min-width: 0;
  display: grid;
  gap: 3px;
}

.tenant-card__wide {
  grid-column: 1 / -1;
}

.tenant-card__facts dt,
.tenant-card__updated span {
  color: #64748b;
  font-size: 12px;
  font-weight: 800;
}

.tenant-card__facts dd {
  margin: 0;
  color: #0f172a;
  font-size: 14px;
  font-weight: 700;
}

.tenant-card__updated {
  margin: 0;
}

.tenant-card__updated strong {
  color: #334155;
  font-size: 13px;
}

.tenant-card__actions {
  flex-wrap: wrap;
  gap: 10px 12px;
}

.tenant-card__primary {
  min-height: 34px;
  border: 0;
  border-radius: 6px;
  padding: 0 12px;
  color: #ffffff;
  background: #0f766e;
  font: inherit;
  font-size: 13px;
  font-weight: 800;
  cursor: pointer;
}

.tenant-card__primary:disabled {
  opacity: 0.55;
  cursor: default;
}

@media (max-width: 760px) {
  .tenant-table-wrap {
    display: none;
  }

  .tenant-card-list {
    display: grid;
    gap: 10px;
  }
}
</style>
