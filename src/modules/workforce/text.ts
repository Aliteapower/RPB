import {useI18n} from 'vue-i18n'
import {WorkforceApiError} from './api'
const zh={title:'员工考勤与薪资',employees:'员工',companies:'发薪公司',administrators:'管理员',myself:'我的资料',
 back:'返回',refresh:'刷新',loading:'加载中…',empty:'暂无记录',save:'保存',saving:'保存中…',saved:'已保存',cancel:'取消',
 create:'新增员工',edit:'编辑资料',assignments:'工作门店',employeeNo:'员工编号',name:'姓名',phone:'电话',email:'邮箱',jobTitle:'岗位',
 hireDate:'入职日期',leaveDate:'离职日期',status:'状态',active:'在职',left:'已离职',company:'发薪公司',effectiveDate:'生效日期',mainStore:'主店',
 stores:'可工作门店',account:'登录账号',noAccount:'暂不设置账号',bindAccount:'绑定已有员工账号',createAccount:'创建仅打卡账号',username:'用户名',password:'六位初始密码',
 accountHint:'已有账号保留原密码和权限；工作门店须已在账号访问范围内。',clockHint:'仅开通本产品访问，不授予收款、钱包或预约权限。',
 registrationNo:'公司注册号',payDay:'每月工资支付日',enabled:'启用发薪配置',disabled:'已停用',enabledStatus:'已启用',
 administrator:'管理员账号',employeeManage:'允许维护员工与出勤',payroll:'工资管理',payrollOff:'关闭',payrollCompany:'允许管理工资的公司',
 grant:'设置授权',grantHint:'工资权限仅在指定公司有效，与员工管理权限分别授权。',
 partial:'仅可查看本店基础资料',missingProfile:'尚未关联员工档案，请联系管理员。',selfProfileMissing:'当前账号尚未关联本人员工档案。',noAccess:'无此功能权限或产品尚未开通，请联系管理员。',
 invalid:'资料不完整或格式不正确，请检查后重试。',conflict:'记录或账号已存在，或已被其他人修改。请刷新核对；新建请求结果不确定时，可保留当前表单重试。',
 failed:'请求未完成，请重试。',notFound:'记录不存在或不在当前管理范围。',select:'请选择',previous:'上一页',next:'下一页',
 assignmentHint:'变更工作门店当天生效；变更发薪公司时，生效日期须为今天。历史记录保留。',
 profileHint:'出勤为事实记录；工资以核准工时和已确认工资单为准。',none:'未设置',manage:'维护',day:'日',passwordHint:'六位数字或英文字母',
 companyDisabled:'发薪公司配置已停用，不能新增或调入员工。',newEmployeeHint:'先录入基本资料，保存后可单独维护工作门店。'}
const en:Record<keyof typeof zh,string>={title:'Attendance & payroll',employees:'Employees',companies:'Paying companies',administrators:'Administrators',myself:'My profile',
 back:'Back',refresh:'Refresh',loading:'Loading…',empty:'No records',save:'Save',saving:'Saving…',saved:'Saved',cancel:'Cancel',
 create:'Add employee',edit:'Edit profile',assignments:'Work locations',employeeNo:'Employee no.',name:'Name',phone:'Phone',email:'Email',jobTitle:'Job title',
 hireDate:'Start date',leaveDate:'Leaving date',status:'Status',active:'Active',left:'Left',company:'Paying company',effectiveDate:'Effective date',mainStore:'Home store',
 stores:'Work locations',account:'Login account',noAccount:'No login account yet',bindAccount:'Link existing staff account',createAccount:'Create attendance-only account',username:'Username',password:'Initial password',
 accountHint:'Existing passwords and permissions are retained. Work locations must be within the account’s existing store access.',clockHint:'Grants this product only, without payments, wallet or reservation access.',
 registrationNo:'Registration number',payDay:'Monthly payment day',enabled:'Enable payroll configuration',disabled:'Disabled',enabledStatus:'Enabled',
 administrator:'Administrator account',employeeManage:'Manage employees and attendance',payroll:'Payroll access',payrollOff:'Off',payrollCompany:'Allowed payroll company',
 grant:'Configure access',grantHint:'Payroll access is limited to the selected company and granted separately from employee management.',
 partial:'Basic information for this store only',missingProfile:'No employee profile is linked. Contact an administrator.',selfProfileMissing:'This account is not linked to an employee profile.',noAccess:'Access is unavailable or the product is not enabled. Contact an administrator.',
 invalid:'Check the required fields and formats.',conflict:'The record or account exists, or was changed by another user. Refresh to check. If creation is uncertain, retain this form to retry.',
 failed:'The request did not complete. Try again.',notFound:'Record not found within your scope.',select:'Select',previous:'Previous',next:'Next',
 assignmentHint:'Store changes take effect today. A company change must take effect today. History is retained.',profileHint:'Attendance records are factual. Payroll uses approved hours and confirmed payslips.',none:'Not set',manage:'Manage',day:'',passwordHint:'Six digits or English letters',companyDisabled:'This payroll configuration is disabled. Employees cannot be added or transferred into it.',newEmployeeHint:'Enter the profile first. Work locations can be maintained separately after saving.'}
export function useWorkforceText(){
 const {locale}=useI18n()
 const t=(key:keyof typeof zh)=>(locale.value.startsWith('en')?en:zh)[key]
 function error(e:unknown){if(e instanceof WorkforceApiError){if(e.code==='WORKFORCE_COMPANY_DISABLED')return t('companyDisabled');if(e.status===403||e.status===401)return t('noAccess');if(e.status===409)return t('conflict');if(e.status===404)return t('notFound');if(e.status===400)return t('invalid')}return t('failed')}
 return {t,error}
}
