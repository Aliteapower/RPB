import test from 'node:test'
import assert from 'node:assert/strict'
import {validRule} from '../../modules/workforce/payrollUi.ts'

test('separately paid contract overtime requires a valid explicit multiplier even when automatic daily overtime is off',()=>{
 const rule={basis:'monthly',rate:'2600',dayHours:'8',weekHours:'44',overtimeEnabled:false,overtimeMultiplier:'1.5',items:[],generation:{mode:'contract',workingWeekdays:[1,2,3,4,5],contractTerms:{employmentProration:'working_days',unpaidLeaveTreatment:'deduct',overtimeTreatment:'separate'}}}
 assert.equal(validRule(rule),true)
 for(const value of ['0','-1','abc','11'])assert.equal(validRule({...rule,overtimeMultiplier:value}),false,value)
})
