export type ReportPeriod = 'day' | 'month' | 'year'
export type ReportCategory = 'all' | 'consumption' | 'topup' | 'refund' | 'points'
export const reportCategories=['consumption','topup','refund','points'] as const
export interface ReportRow { bucket:string; metric:string; channel:string; channelName:string; amount:number; count:number }
export interface BusinessReport {period:ReportPeriod;startDate:string;endDate:string;timezone:string;currency:string;rows:ReportRow[]}
const order=['consumption','walletPayment','spentPrincipal','spentBonus','externalPayment','consumptionReceipt','topupPrincipal','topupBonus','topupCredit','topupReceipt','consumptionRefund','topupRefund','bonusReversal','pointsGrant','pointsDeduct','pointsRedeem','pointsReversalIn','pointsReversalOut']
export const rowKey=(row:ReportRow)=>JSON.stringify([row.metric,row.channel,row.channelName])
export const isPoints=(row:ReportRow)=>row.metric.startsWith('points')
export function reportCategory(row:ReportRow):Exclude<ReportCategory,'all'>{
 if(isPoints(row))return 'points'
 if(['consumptionRefund','topupRefund','bonusReversal'].includes(row.metric))return 'refund'
 return row.metric.startsWith('topup')?'topup':'consumption'
}
export const filterReportRows=(rows:ReportRow[],category:ReportCategory)=>rows.filter(row=>row.count>0&&(category==='all'||reportCategory(row)===category))
export const reportDate=(period:ReportPeriod,value:string)=>period==='year'?`${value}-01-01`:period==='month'?`${value}-01`:value
export const reportQuery=(period:ReportPeriod,value:string)=>`/business-report?period=${period}&date=${encodeURIComponent(reportDate(period,value))}`
export function reportTable(rows:ReportRow[]){
 const totals=new Map<string,ReportRow>(),cells=new Map<string,ReportRow>()
 for(const row of rows.filter(x=>x.count>0)){
  const key=rowKey(row),total=totals.get(key)
  totals.set(key,{...row,amount:(total?.amount??0)+row.amount,count:(total?.count??0)+row.count})
  cells.set(JSON.stringify([row.bucket,key]),row)
 }
 const columns=[...totals.values()].sort((a,b)=>order.indexOf(a.metric)-order.indexOf(b.metric)||a.channel.localeCompare(b.channel)||a.channelName.localeCompare(b.channelName))
 return {columns,totals:columns,buckets:[...new Set(rows.filter(x=>x.count>0).map(x=>x.bucket))].sort(),cell:(bucket:string,row:ReportRow)=>cells.get(JSON.stringify([bucket,rowKey(row)]))}
}
export function reportCsv(report:BusinessReport,label:(row:ReportRow)=>string,totalLabel:string,dateLabel:string){
 const table=reportTable(report.rows),value=(row:ReportRow|undefined,column:ReportRow)=>row?(isPoints(column)?String(row.amount):(row.amount/100).toFixed(2)):'0'
 const rows:unknown[][]=[['period',report.period,'start',report.startDate,'end',report.endDate,'timezone',report.timezone,'currency',report.currency],[dateLabel,...table.columns.map(label)],[totalLabel,...table.totals.map(c=>value(c,c))],...table.buckets.map(bucket=>[report.period==='year'?bucket.slice(0,7):bucket,...table.columns.map(c=>value(table.cell(bucket,c),c))])]
 const cell=(v:unknown)=>'"'+String(v??'').replace(/^[=+\-@\t\r\n]/,"'$&").replaceAll('"','""')+'"'
 return '\ufeff'+rows.map(row=>row.map(cell).join(',')).join('\r\n')+'\r\n'
}
