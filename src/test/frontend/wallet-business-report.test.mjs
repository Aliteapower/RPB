import {test} from 'node:test'
import assert from 'node:assert/strict'
import {reportTable,reportCsv,reportDate,reportQuery,filterReportRows} from '../../modules/wallet/report/model.ts'

test('only actual activity produces columns, and zero-net activity is retained',()=>{
 const table=reportTable([{bucket:'2026-10-01',metric:'pointsGrant',channel:'',channelName:'',amount:0,count:2},{bucket:'2026-10-02',metric:'consumption',channel:'',channelName:'',amount:500,count:1}])
 assert.equal(table.columns.length,2);assert.equal(table.totals[0].count,1)
 assert.equal(table.totals.find(x=>x.metric==='pointsGrant').amount,0)
 assert.equal(table.totals.find(x=>x.metric==='pointsGrant').count,2)
 assert.deepEqual(table.buckets,['2026-10-01','2026-10-02'])
 assert.deepEqual(reportTable([]).columns,[])
})
test('period input normalizes month and year and uses store timezone',()=>{
 assert.equal(reportDate('month','2024-02'),'2024-02-01');assert.equal(reportDate('year','2024'),'2024-01-01')
 assert.equal(reportQuery('day','2026-10-25'),'/business-report?period=day&date=2026-10-25')
})
test('CSV distinguishes cents and point quantities and escapes custom channel formulas',()=>{
 const report={period:'month',startDate:'2026-10-01',endDate:'2026-10-31',timezone:'Asia/Singapore',currency:'SGD',rows:[{bucket:'2026-10-01',metric:'topupReceipt',channel:'other:one',channelName:'=A1',amount:500,count:1},{bucket:'2026-10-01',metric:'pointsGrant',channel:'',channelName:'',amount:12,count:1}]}
 const csv=reportCsv(report,(x)=>x.channelName||x.metric,'合计','日期')
 assert.match(csv,/'=A1/);assert.match(csv,/5\.00/);assert.match(csv,/"12"/);assert.doesNotMatch(csv,/0\.12/)
 assert.match(csv,/Asia\/Singapore/)
})
test('major categories keep top-up refunds out of top-up sales and export only selected activity',()=>{
 const rows=['consumption','walletPayment','consumptionReceipt','topupPrincipal','topupBonus','topupReceipt','topupRefund','consumptionRefund','bonusReversal','pointsGrant','pointsReversalIn'].map(metric=>({bucket:'2026-10-01',metric,channel:'',channelName:'',amount:100,count:1}))
 assert.deepEqual(filterReportRows(rows,'topup').map(x=>x.metric),['topupPrincipal','topupBonus','topupReceipt'])
 assert.deepEqual(filterReportRows(rows,'refund').map(x=>x.metric),['topupRefund','consumptionRefund','bonusReversal'])
 assert.deepEqual(filterReportRows(rows,'points').map(x=>x.metric),['pointsGrant','pointsReversalIn'])
 const csv=reportCsv({period:'day',rows:filterReportRows(rows,'consumption')},x=>x.metric,'total','date')
 assert.match(csv,/consumptionReceipt/);assert.doesNotMatch(csv,/topup|points|Refund/)
})
