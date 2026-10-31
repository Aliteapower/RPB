import {test} from 'node:test'
import assert from 'node:assert/strict'
import {planLeaveRange} from '../../../src/modules/workforce/leavePlan.ts'
const s={id:'S',employeeId:'E',workDate:'2026-10-29',startAt:'2026-10-29T03:00:00Z',endAt:'2026-10-29T15:00:00Z',timezone:'Asia/Singapore',status:'scheduled'}
test('range matches own scheduled days and skips rest, empty and cancelled',()=>{
 const shifts=[s,{...s,id:'cancel',workDate:'2026-10-30',status:'cancelled'},{...s,id:'rest',workDate:'2026-10-31'},{...s,id:'other',employeeId:'other',workDate:'2026-11-01'}]
 const rows=planLeaveRange('2026-10-29','2026-11-01',shifts,{items:[{date:'2026-10-31',kind:'store_rest',enabled:true}]},[],'E',true,s,s.startAt,s.endAt)
 assert.deepEqual(rows.map(r=>r.status),['ready','cancelled','rest','empty']);assert.equal(rows.reduce((sum,r)=>sum+r.seconds,0),43200)
})
test('partial days retain start offset and exact hours; short shifts block without clipping',()=>{
 const next={...s,id:'next',workDate:'2026-10-30',startAt:'2026-10-30T14:00:00Z',endAt:'2026-10-30T18:00:00Z'}
 const rows=planLeaveRange('2026-10-29','2026-10-30',[s,next],null,[],'E',false,s,'2026-10-29T04:00:00Z','2026-10-29T08:00:00Z')
 assert.equal(rows[0].seconds,14400);assert.equal(rows[1].status,'outside');assert.equal(rows[1].startAt,'2026-10-30T15:00:00.000Z');assert.equal(rows[1].endAt,'2026-10-30T19:00:00.000Z')
})
test('approved overlap or pending request blocks preview, public holiday work remains eligible',()=>{
 const rows=planLeaveRange('2026-10-29','2026-10-29',[s],{items:[{date:s.workDate,kind:'public_holiday',enabled:true}]},[{scheduleId:'S',status:'approved',startAt:s.startAt,endAt:s.endAt}],'E',true,s,s.startAt,s.endAt)
 assert.equal(rows[0].status,'conflict')
 assert.equal(planLeaveRange(s.workDate,s.workDate,[s],null,[],'E',true,s,s.startAt,s.endAt)[0].status,'ready')
})
import {adjustLeavePlan} from '../../../src/modules/workforce/leavePlan.ts'
test('daily edits recalculate exact time and exclusion removes every shift on the date without mutating source',()=>{
 const next={...s,id:'next',workDate:'2026-10-30',startAt:'2026-10-30T03:00:00Z',endAt:'2026-10-30T15:00:00Z'}
 const base=planLeaveRange(s.workDate,next.workDate,[s,next,{...next,id:'segment'}],null,[],'E',true,s,s.startAt,s.endAt)
 const edits={S:{start:'2026-10-29T12:00',end:'2026-10-29T14:30'}}
 const changed=adjustLeavePlan(base,edits,[],[])
 assert.equal(changed[0].seconds,9000);assert.equal(changed.reduce((n,r)=>n+r.seconds,0),95400)
 const excluded=adjustLeavePlan(base,edits,['2026-10-30'],[])
 assert.deepEqual(excluded.map(r=>r.status),['ready','excluded','excluded']);assert.equal(excluded.reduce((n,r)=>n+r.seconds,0),9000)
 assert.equal(base[0].seconds,43200);assert.equal(adjustLeavePlan(base,edits,[],[])[1].status,'ready')
})
test('daily edits revalidate bounds and leave conflicts, and exclusion never changes cancelled rows',()=>{
 const base=planLeaveRange(s.workDate,s.workDate,[s,{...s,id:'cancel',status:'cancelled'}],null,[],'E',true,s,s.startAt,s.endAt)
 assert.equal(adjustLeavePlan(base,{S:{start:'',end:''}},[],[])[0].status,'outside')
 assert.equal(adjustLeavePlan(base,{S:{start:'2026-10-29T10:00',end:'2026-10-29T12:00'}},[],[])[0].status,'outside')
 const leaves=[{scheduleId:'S',status:'approved',startAt:s.startAt,endAt:'2026-10-29T04:00:00Z'}]
 assert.equal(adjustLeavePlan(base,{},[],leaves)[0].status,'conflict')
 assert.equal(adjustLeavePlan(base,{S:{start:'2026-10-29T13:00',end:'2026-10-29T14:00'}},[],leaves)[0].status,'ready')
 assert.equal(adjustLeavePlan(base,{},[s.workDate],leaves)[1].status,'cancelled')
})
