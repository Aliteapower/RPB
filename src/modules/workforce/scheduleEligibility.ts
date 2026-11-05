import {WorkforceApiError} from './api'

const messages:Record<string,readonly [string,string]>={
 WORKFORCE_SCHEDULE_DATE_OUT_OF_RANGE:['只能新增或调整门店当地今天至未来90天内的排班。','Create or adjust schedules from today through the next 90 days in store time.'],
 WORKFORCE_SCHEDULE_BEFORE_HIRE:['排班日期早于员工入职日期，请核对员工档案或选择其他日期。','The schedule date precedes the employee’s hire date. Review the profile or choose another date.'],
 WORKFORCE_SCHEDULE_EMPLOYEE_LEFT:['排班日期晚于员工离职日期，请核对员工档案或选择其他员工。','The schedule date is after the employee’s leaving date. Review the profile or choose another employee.'],
 WORKFORCE_SCHEDULE_EMPLOYEE_INACTIVE:['员工已停用或不在职，不能新增或恢复排班。','This employee is disabled or inactive and cannot receive a new or restored schedule.'],
 WORKFORCE_SCHEDULE_STORE_ASSIGNMENT:['员工在该营业日期不属于此门店，请核对门店任职生效日期。','The employee is not assigned to this store on that business date. Review the store assignment dates.'],
 WORKFORCE_SCHEDULE_SHIFT_DISABLED:['所选班次已停用，请选择启用的班次。','The selected shift is disabled. Choose an enabled shift.']
}
export function scheduleEligibilityError(error:unknown,english=false):string {
 return error instanceof WorkforceApiError?messages[error.code]?.[english?1:0]||'':''
}
export function scheduleEmployeeUnavailable(english=false):string {
 return english?'The selected employee cannot be scheduled on this business date. Choose an eligible employee or review their profile.':'所选员工在该营业日期不可排班，请选择其他员工或核对员工档案。'
}
export function scheduleNoEligibleEmployees(english=false):string {
 return english?'No employees are eligible on this business date. Review employment and store assignment dates.':'该营业日期没有可排班员工，请核对入离职及门店任职日期。'
}
