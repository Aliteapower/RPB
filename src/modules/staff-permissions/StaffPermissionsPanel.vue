<script setup lang="ts">
import {computed, onUnmounted, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {loadStaffPermissions, saveStaffPermissions, StaffPermissionsError, type StaffPermissions} from './api'
import {togglePermission} from './permissionSelection.mjs'

const props = defineProps<{staffId: string; initialStoreId: string; stores: {storeId: string; label: string}[]}>()
const {locale} = useI18n(), zh = computed(() => locale.value.startsWith('zh'))
const tr = (cn: string, en: string) => zh.value ? cn : en
const selectedStore = ref(''), snapshot = ref<StaffPermissions | null>(null), selected = ref<string[]>([])
const loading = ref(false), saving = ref(false), error = ref(''), saved = ref(false)
let generation = 0
const choices = computed(() => snapshot.value?.groups.flatMap(group => group.permissions) ?? [])
const dirty = computed(() => snapshot.value !== null && JSON.stringify([...selected.value].sort()) !== JSON.stringify([...snapshot.value.permissions].sort()))
const busy = computed(() => loading.value || saving.value)
function message(e: unknown) {
  if (e instanceof StaffPermissionsError) {
    if (e.status === 409) return tr('权限已被其他管理员修改。你的选择已保留，请重新加载后核对并保存。', 'Another administrator changed these permissions. Your choices are retained; reload, review and save again.')
    if (e.status === 401) return tr('登录已失效，请重新登录。', 'Your session expired. Please sign in again.')
    if (e.status === 403) return tr('无权管理该员工或门店的权限。', 'You cannot manage permissions for this employee or store.')
    if (e.status === 404) return tr('员工不存在或未获此门店访问权。', 'Employee not found or not assigned to this store.')
  }
  return tr('权限加载或保存失败，请重试。', 'Could not load or save permissions. Please retry.')
}
async function load() {
  const current = ++generation
  snapshot.value = null; selected.value = []; saved.value = false; error.value = ''
  if (!selectedStore.value || !props.staffId) { loading.value = false; return }
  loading.value = true
  try {
    const result = await loadStaffPermissions(selectedStore.value, props.staffId)
    if (current !== generation) return
    snapshot.value = result; selected.value = [...result.permissions]
  } catch (e) { if (current === generation) error.value = message(e) }
  finally { if (current === generation) loading.value = false }
}
async function save() {
  if (busy.value || !snapshot.value) return
  const current = generation
  saving.value = true; error.value = ''; saved.value = false
  try {
    const result = await saveStaffPermissions(selectedStore.value, props.staffId, snapshot.value.version, selected.value)
    if (current !== generation) return
    snapshot.value = result; selected.value = [...result.permissions]; saved.value = true
  } catch (e) { if (current === generation) error.value = message(e) }
  finally { saving.value = false }
}
function toggle(code: string, event: Event) {
  selected.value = togglePermission(selected.value, code, (event.target as HTMLInputElement).checked, choices.value)
  saved.value = false
}
watch(() => [props.staffId, props.initialStoreId, props.stores.map(store => store.storeId).join(',')], () => {
  selectedStore.value = props.stores.some(store => store.storeId === props.initialStoreId) ? props.initialStoreId : props.stores[0]?.storeId ?? ''
  void load()
}, {immediate: true})
function changeStore(event: Event) { selectedStore.value = (event.target as HTMLSelectElement).value; void load() }
onUnmounted(() => { generation++ })
</script>

<template>
  <section class="staff-permissions" :aria-busy="busy">
    <header><h2>{{tr('产品线与模块授权', 'Product and module permissions')}}</h2><p>{{tr('按门店独立保存。操作权限会同时启用必要的查看权限；关闭查看会取消依赖它的操作。', 'Saved separately for each store. Operations require viewing access; removing viewing access removes dependent operations.')}}</p></header>
    <label class="permission-store">{{tr('授权门店', 'Store')}}<select :value="selectedStore" :disabled="busy || !stores.length || dirty" @change="changeStore"><option v-for="store in stores" :key="store.storeId" :value="store.storeId">{{store.label}}</option></select></label>
    <p v-if="!stores.length">{{tr('请先保存员工的门店访问配置，再设置产品权限。', 'Save the employee’s store access before configuring product permissions.')}}</p>
    <p v-if="dirty" class="permission-hint">{{tr('当前有未保存选择，请保存或重新加载后切换门店。', 'Save or reload the current changes before switching stores.')}}</p>
    <p v-if="loading" role="status">{{tr('正在加载权限…', 'Loading permissions…')}}</p>
    <p v-if="error" class="permission-error" role="alert">{{error}}</p>
    <p v-if="saved" class="permission-success" role="status">{{tr('权限已保存，员工后续请求将按新权限校验。', 'Permissions saved. Subsequent employee requests use the new permissions.')}}</p>
    <template v-if="snapshot">
      <p class="permission-hint">{{snapshot.inherited ? tr('当前沿用账号原有权限。首次保存后，本门店将按以下选择授权。', 'Using the account’s existing permissions. Saving creates an explicit selection for this store.') : tr('当前使用本门店的独立授权。全部取消并保存会撤销以下所有模块权限。', 'Using explicit permissions for this store. Clearing all selections and saving revokes all permissions listed below.')}}</p>
      <fieldset v-for="group in snapshot.groups" :key="group.key" :disabled="busy">
        <legend>{{zh ? group.labelZh : group.labelEn}} <small v-if="!group.provisioned">{{tr('未开通', 'Not provisioned')}}</small><small v-else-if="!group.enabled">{{tr('当前不可开展新业务', 'New operations currently unavailable')}}</small></legend>
        <div class="permission-choices"><label v-for="choice in group.permissions" :key="choice.code" :class="{'permission-unavailable': !choice.grantable}"><input type="checkbox" :checked="selected.includes(choice.code)" :disabled="!choice.grantable && !selected.includes(choice.code)" @change="toggle(choice.code, $event)"><span>{{zh ? choice.labelZh : choice.labelEn}}<small v-if="!choice.grantable">{{tr('仅可撤销现有授权', 'Existing access can only be revoked')}}</small></span></label></div>
      </fieldset>
    </template>
    <footer><button type="button" :disabled="busy || !selectedStore" @click="load">{{tr('重新加载', 'Reload')}}</button><button type="button" class="permission-save" :disabled="busy || !snapshot || (!dirty && !snapshot.inherited)" @click="save">{{saving ? tr('保存中…', 'Saving…') : tr('保存本门店权限', 'Save store permissions')}}</button></footer>
  </section>
</template>

<style scoped>
.staff-permissions{margin-top:22px;padding:22px;border:1px solid #dce5ed;border-radius:16px;background:#fff;color:#102033}.staff-permissions h2{margin:0;font-size:19px}.staff-permissions header p,.permission-hint{color:#64748b;line-height:1.6}.permission-store{display:grid;gap:8px;max-width:440px;font-weight:600}.permission-store select{padding:10px;border:1px solid #cbd5e1;border-radius:8px;background:white;color:inherit}.staff-permissions fieldset{margin:18px 0;padding:16px;border:1px solid #dce5ed;border-radius:10px;min-width:0}.staff-permissions legend{font-weight:700}.staff-permissions legend small{margin-left:8px;font-weight:400;color:#64748b}.permission-choices{display:grid;grid-template-columns:repeat(auto-fit,minmax(220px,1fr));gap:12px}.permission-choices label{display:flex;align-items:flex-start;gap:9px;line-height:1.5}.permission-choices input{margin-top:4px}.permission-choices small{display:block;color:#64748b;font-size:12px}.permission-unavailable{color:#64748b}.permission-error{color:#b42318}.permission-success{color:#157347}.staff-permissions footer{display:flex;gap:12px;justify-content:flex-end;flex-wrap:wrap}.staff-permissions button{border:1px solid #cbd5e1;background:white;padding:10px 16px;border-radius:8px;color:#102033;cursor:pointer}.staff-permissions .permission-save{background:#0f766e;color:white;border-color:#0f766e}.staff-permissions button:disabled{opacity:.5;cursor:default}@media(max-width:600px){.staff-permissions{padding:16px}.permission-choices{grid-template-columns:1fr}.staff-permissions footer button{flex:1}}
</style>
