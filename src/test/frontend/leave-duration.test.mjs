import {test} from 'node:test'
import assert from 'node:assert/strict'
import {quickLeavePeriod} from '../../../src/modules/workforce/leaveDuration.ts'
const shift={startAt:'2026-10-29T03:00:00Z',endAt:'2026-10-29T15:00:00Z',timezone:'Asia/Singapore'}
test('quick leave hours use store time, current start and exact minutes',()=>{
 assert.equal(quickLeavePeriod(shift,'2026-10-29T11:00',1),'2026-10-29T12:00')
 assert.equal(quickLeavePeriod(shift,'2026-10-29T12:30',2),'2026-10-29T14:30')
 assert.equal(quickLeavePeriod(shift,'2026-10-29T11:00',4),'2026-10-29T15:00')
 assert.equal(quickLeavePeriod(shift,'2026-10-29T11:00',1.5),'2026-10-29T12:30')
 assert.equal(quickLeavePeriod(shift,'2026-10-29T11:00',1.15),'2026-10-29T12:09')
})
test('quick leave never exceeds or truncates shift, or accepts invalid hours',()=>{
 for(const hours of [0,-1,NaN,Infinity,0.001,13])assert.equal(quickLeavePeriod(shift,'2026-10-29T11:00',hours),null)
 assert.equal(quickLeavePeriod(shift,'2026-10-29T22:00',2),null)
 assert.equal(quickLeavePeriod(shift,'2026-10-29T10:00',1),null)
 assert.equal(quickLeavePeriod(shift,'invalid',1),null)
})
test('overnight shift resolves next-day end and exact boundary',()=>{
 const overnight={...shift,startAt:'2026-10-29T14:00:00Z',endAt:'2026-10-29T22:00:00Z'}
 assert.equal(quickLeavePeriod(overnight,'2026-10-29T22:00',4),'2026-10-30T02:00')
 assert.equal(quickLeavePeriod(overnight,'2026-10-30T04:00',2),'2026-10-30T06:00')
 assert.equal(quickLeavePeriod(overnight,'2026-10-30T04:00',4),null)
})
