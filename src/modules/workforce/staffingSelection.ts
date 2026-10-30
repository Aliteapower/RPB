import {ref} from 'vue'
import type {StaffingEmployee} from './bulkScheduleTypes'
import type {AdjustmentTarget} from './bulkAdjustmentTypes'
export function useStaffingSelection(from:string,to:string){
 const date=ref(from===to?from:''),selected=ref<AdjustmentTarget[]>([])
 const eligible=(row:StaffingEmployee)=>row.schedules?.length===1&&Number.isSafeInteger(row.schedules[0].version)&&(row.schedules[0].version||0)>0&&(!date.value||row.date===date.value)
 const checked=(row:StaffingEmployee)=>!!row.schedules?.some(slot=>selected.value.some(target=>target.scheduleId===slot.id))
 function clear(){selected.value=[]}
 function acceptPage(rows:StaffingEmployee[]){if(!date.value&&rows[0])date.value=rows[0].date}
 function toggle(row:StaffingEmployee,value:boolean){if(!eligible(row))return;if(value){if(!date.value)date.value=row.date;if(!checked(row)&&selected.value.length<500)selected.value.push({scheduleId:row.schedules![0].id,expectedVersion:row.schedules![0].version!})}else selected.value=selected.value.filter(target=>target.scheduleId!==row.schedules![0].id)}
 function selectPage(rows:StaffingEmployee[]){acceptPage(rows);rows.filter(eligible).forEach(row=>toggle(row,true))}
 function replace(rows:StaffingEmployee[]){clear();selectPage(rows)}
 return {date,selected,eligible,checked,clear,acceptPage,toggle,selectPage,replace}
}
