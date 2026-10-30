import type {RouteLocationNormalizedLoaded,RouteLocationRaw} from 'vue-router'
import type {Home} from './types'
import type {PayrollHomeData} from './payrollTypes'

type Caption=readonly [string,string]
type Access='employee'|'headOffice'|'payroll'|'change'
interface ManagementSection {path:string;label:Caption;access:Access;routes:Record<string,Caption|null>}
// Explicit route names keep operation pages and employee calendar views out of management.
const sections:ManagementSection[]=[
 {path:'pay-change-requests',label:['工资变更申请','Pay change requests'],access:'change',routes:{'workforce-pay-change-requests':null}},
 {path:'attendance-rules',label:['休息与考勤规则','Break and attendance rules'],access:'employee',routes:{'workforce-attendance-rules':null}},
 {path:'review-explanations',label:['判定说明','Decision explanations'],access:'employee',routes:{'workforce-review-explanations':null}},
 {path:'clock-site',label:['门店打卡定位点','Store clock location'],access:'employee',routes:{'workforce-clock-site':null}},
 {path:'statutory-policies',label:['CPF／SDL／CDAC规则','CPF / SDL / CDAC rules'],access:'headOffice',routes:{'workforce-statutory-policies':null}},
 {path:'document-expiries',label:['证件到期','Document expiry'],access:'employee',routes:{'workforce-document-expiries':null}},
 {path:'document-types',label:['证件类型','Document types'],access:'headOffice',routes:{'workforce-document-types':null}},
 {path:'positions',label:['岗位管理','Positions'],access:'headOffice',routes:{'workforce-positions':null}},
 {path:'companies',label:['公司管理','Companies'],access:'headOffice',routes:{'workforce-companies':null,'workforce-company':['编辑公司','Edit company']}},
 {path:'administrators',label:['权限管理','Administrator permissions'],access:'headOffice',routes:{'workforce-administrators':null,'workforce-administrator':['编辑权限','Edit permissions']}},
 {path:'shifts',label:['班次管理','Shift management'],access:'employee',routes:{'workforce-shifts':null,'workforce-shift-new':['新增班次','New shift'],'workforce-shift':['编辑班次','Edit shift']}},
 {path:'calendar',label:['考勤日历','Attendance calendar'],access:'employee',routes:{'workforce-calendar':null,'workforce-calendar-new':['新增日历事项','New calendar event'],'workforce-calendar-edit':['编辑日历事项','Edit calendar event']}},
 {path:'public-holidays',label:['年度公共假期','Annual public holidays'],access:'employee',routes:{'workforce-public-holidays':null}},
 {path:'leave-types',label:['假期类型','Leave types'],access:'employee',routes:{'workforce-leave-types':null,'workforce-leave-type-new':['新增假期类型','New leave type'],'workforce-leave-type':['编辑假期类型','Edit leave type']}},
 {path:'salary-templates',label:['薪资模板','Salary templates'],access:'payroll',routes:{'workforce-salary-templates':null,'workforce-salary-template-new':['新增薪资模板','New salary template'],'workforce-salary-template':['编辑薪资模板','Edit salary template']}},
 {path:'pay-profiles',label:['员工薪资','Employee pay profiles'],access:'payroll',routes:{'workforce-pay-profiles':null,'workforce-pay-profile-new':['新增员工薪资','New employee pay profile'],'workforce-pay-profile':['编辑员工薪资','Edit employee pay profile']}}
]
export interface ManagementCrumb {label:string;to?:RouteLocationRaw}
export function managementNavigation(route:Pick<RouteLocationNormalizedLoaded,'name'|'params'|'query'>,home:Pick<Home,'employeeManage'|'headOffice'>|null,payrollHome:Pick<PayrollHomeData,'companies'>|null,locale:string){
 const name=typeof route.name==='string'?route.name:'',section=sections.find(s=>Object.hasOwn(s.routes,name))
 if(!home||!section)return null
 const requested=typeof route.query.companyId==='string'?route.query.companyId:''
 const companyId=section.access==='payroll'||section.access==='change'?(requested||payrollHome?.companies[0]?.id||''):requested
 if(section.access==='change'){
  if(!home.employeeManage&&!payrollHome?.companies.some(c=>c.id===companyId))return null
 }else if(section.access==='payroll'){
  if(!companyId||!payrollHome?.companies.some(c=>c.id===companyId))return null
 }else if(!home.employeeManage||(section.access==='headOffice'&&!home.headOffice))return null
 const english=locale.startsWith('en'),caption=(c:Caption)=>c[english?1:0]
 const base=`/stores/${encodeURIComponent(String(route.params.storeId))}/workforce`,query=companyId?{companyId}:{}
 const backTo={path:base+'/management',query}
 const action=section.routes[name],crumbs:ManagementCrumb[]=[{label:english?'Management':'管理',to:backTo},{label:caption(section.label),...(action?{to:{path:base+'/'+section.path,query}}:{})}]
 if(action)crumbs.push({label:caption(action)})
 return {backTo,backLabel:english?'← Back to management':'← 返回管理',ariaLabel:english?'Breadcrumb':'当前位置',crumbs}
}

