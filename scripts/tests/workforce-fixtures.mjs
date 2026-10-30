// Current child-read contracts shared by older page regression harnesses.
export function payrollChildFixture(path) {
 if (/\/changes$/.test(path)) {
  const comparison=()=>({available:false,baselineMonth:null,baselineVersion:null,totalGrossDelta:null,totalNetDelta:null,totalEmployerCostDelta:null,lines:[]})
  return {batchId:'b1',batchVersion:1,status:'confirmed',sourceChanged:false,lastCalculation:comparison(),previousMonth:comparison(),pending:[],recalculated:0,reused:0}
 }
 if (path.includes('/approval-readiness?')) return {ready:true,total:0,current:0,pending:0,stores:[],workSeconds:0,paidLeaveSeconds:0,unpaidLeaveSeconds:0}
}
export function withPayrollChildren(api) {
 return {...api,get:async(path,...args)=>payrollChildFixture(path)??api.get(path,...args)}
}
