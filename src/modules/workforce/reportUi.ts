import type {ReportTotals,ReportField} from './reportTypes'
export const reportFields:ReportField[]=['gross','net','employeeCpf','employerCpf','sdl','employerCost','paid','remaining']
export const emptyExpected=():ReportTotals=>Object.fromEntries(reportFields.map(k=>[k,''])) as unknown as ReportTotals
export const validExpected=(values:ReportTotals)=>reportFields.every(k=>/^\d{1,12}(\.\d{1,2})?$/.test(values[k]))
export const reportQuery=(month:string,companies:string[])=>'month='+encodeURIComponent(month)+'&companyIds='+encodeURIComponent([...companies].sort().join(','))
