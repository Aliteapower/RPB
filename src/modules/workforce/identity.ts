export type WorkforceRole='administrator'|'manager'|'employee'
/** Store managers may also carry tenant_admin; use the actual login roles, never feature grants. */
export function workforceRole(roles:readonly string[]):WorkforceRole {
 if(roles.includes('store_manager'))return 'manager'
 if(roles.includes('tenant_admin'))return 'administrator'
 return 'employee'
}
export function workforceRoleCaption(roles:readonly string[],locale:string){
 const role=workforceRole(roles)
 return locale.startsWith('en')?`Current role: ${{administrator:'Administrator',manager:'Manager',employee:'Employee'}[role]}`:`当前身份：${{administrator:'管理员',manager:'经理',employee:'员工'}[role]}`
}
