<script setup lang="ts">
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
  billing: [tenant: PlatformTenant]
  delete: [tenant: PlatformTenant]
  restore: [tenant: PlatformTenant]
}>()

function statusLabel(status: TenantStatus, statusOptions: TenantStatusOption[]): string {
  return statusOptions.find(option => option.value === status)?.label ?? status
}
</script>

<template>
  <div class="tenant-table-wrap">
    <table class="tenant-table">
      <thead>
        <tr>
          <th>租户代码</th>
          <th>名称</th>
          <th>负责人</th>
          <th>电话</th>
          <th>地址</th>
          <th>状态</th>
          <th>更新时间</th>
          <th>操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-if="loading">
          <td colspan="8" class="table-empty">加载中</td>
        </tr>
        <tr v-else-if="tenants.length === 0">
          <td colspan="8" class="table-empty">暂无租户</td>
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
                {{ tenant.deleted ? '已删除' : statusLabel(tenant.status, statusOptions) }}
              </span>
            </td>
            <td>{{ new Date(tenant.updatedAt).toLocaleString() }}</td>
            <td>
              <div class="row-actions">
                <button v-if="!billingOnly" type="button" class="text-action" @click="emit('edit', tenant)">编辑</button>
                <button type="button" class="text-action" @click="emit('billing', tenant)">
                  {{ billingOnly ? '订阅/计费' : '计费' }}
                </button>
                <button
                  v-if="!billingOnly && tenant.deleted"
                  type="button"
                  class="text-action"
                  :disabled="saving"
                  @click="emit('restore', tenant)"
                >
                  恢复
                </button>
                <button
                  v-else-if="!billingOnly"
                  type="button"
                  class="text-action danger"
                  :disabled="saving"
                  @click="emit('delete', tenant)"
                >
                  删除
                </button>
              </div>
            </td>
          </tr>
        </template>
      </tbody>
    </table>
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
</style>
